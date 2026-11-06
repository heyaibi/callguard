package com.callguard.spam.classify

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.util.Locale
import kotlin.math.round

/**
 * Local evaluation on the owner's SMS export. It runs only when the CALLGUARD_SMS_JSON environment variable names
 * the export file. It writes report.md and labels.json into an eval folder beside the export's folder.
 * Nothing it writes belongs in the repository.
 */
class SmsClassifierEvalTest {
    /** One received message. [verdict] is the rules-only verdict. */
    private class Row(
        val id: String,
        val sender: String,
        val body: String,
        val senderKind: SenderKind,
        val masked: String,
        val hits: List<RuleHit>,
        val verdict: SmsVerdict,
    )

    @Test
    fun evaluatesReceivedSmsAndWritesReport() {
        val path = System.getenv(ENV_VAR)
        Assume.assumeTrue("set $ENV_VAR to run the SMS evaluation", !path.isNullOrBlank())
        val input = File(path.orEmpty())
        Assume.assumeTrue("$ENV_VAR does not name a file", input.isFile)
        val evalDir = File(input.absoluteFile.parentFile?.parentFile ?: input.absoluteFile, "eval").apply { mkdirs() }

        val all = JSONArray(input.readText())
        val received = (0 until all.length()).map { all.getJSONObject(it) }.filter { it.optString("type") == "1" }
        val rows = received.map { toRow(it) }
        val agreement = crossValidationAgreement(rows)

        File(evalDir, "report.md").writeText(buildReport(rows, all.length() - received.size, agreement))
        File(evalDir, "labels.json").writeText(buildLabels(rows).toString(2))

        val protectedHits = rows.filter { it.verdict.kind in PROTECTED_KINDS && isSpam(it.verdict.spamScore) }
        assertEquals("protected kinds at or above the spam threshold", 0L, protectedHits.size.toLong())
    }

    private fun toRow(obj: JSONObject): Row {
        val sender = obj.optString("address")
        val body = obj.optString("body")
        val sms = TextNormalizer.normalize(body)
        val info = SenderParser.parse(sender)
        val rules = Rules.evaluate(info, sms)
        val verdict = SmsClassifier().classify(sender, body)
        return Row(obj.optString("_id"), sender, body, info.kind, sms.masked, rules.hits, verdict)
    }

    /** Five folds. Each fold's token model learns the rules-only labels of the other four folds. */
    private fun crossValidationAgreement(rows: List<Row>): Int {
        var agree = 0
        for (fold in 0 until FOLDS) {
            val classifier = SmsClassifier()
            rows.forEachIndexed { i, row ->
                if (i % FOLDS != fold) classifier.learn(row.sender, row.body, isSpam(row.verdict.spamScore))
            }
            rows.forEachIndexed { i, row ->
                if (i % FOLDS == fold) {
                    val full = isSpam(classifier.classify(row.sender, row.body).spamScore)
                    if (full == isSpam(row.verdict.spamScore)) agree++
                }
            }
        }
        return agree
    }

    private fun buildReport(
        rows: List<Row>,
        sentCount: Int,
        agreement: Int,
    ): String {
        val out = StringBuilder()
        val total = rows.size
        out.appendLine("# SMS classifier evaluation (rules only)")
        out.appendLine()
        out.appendLine("Received messages: $total. Sent messages skipped: $sentCount.")
        out.appendLine()
        out.appendLine("## Counts per kind")
        for (kind in SmsKind.entries) {
            out.appendLine("- ${kind.name}: ${rows.count { it.verdict.kind == kind }}")
        }
        out.appendLine()
        out.appendLine("## Sender types")
        for (kind in SenderKind.entries) {
            out.appendLine("- ${kind.name}: ${rows.count { it.senderKind == kind }}")
        }
        out.appendLine()
        out.appendLine("## Score histogram")
        for (bin in 0 until BINS) {
            val low = bin.toDouble() / BINS
            val high = (bin + 1).toDouble() / BINS
            val count = rows.count { row -> bucketOf(row.verdict.spamScore) == bin }
            out.appendLine("- ${fmt(low)} to ${fmt(high)}: $count")
        }
        out.appendLine()
        out.appendLine("## Spam counts")
        val spamCount = rows.count { isSpam(it.verdict.spamScore) }
        val highCount = rows.count { it.verdict.spamScore >= HIGH_CONFIDENCE }
        out.appendLine("- At or above ${fmt(ClassifierConfig.SPAM_THRESHOLD)} (spam): $spamCount")
        out.appendLine("- High confidence, at or above ${fmt(HIGH_CONFIDENCE)}: $highCount")
        out.appendLine()
        val protectedHits = rows.filter { it.verdict.kind in PROTECTED_KINDS && isSpam(it.verdict.spamScore) }
        out.appendLine("## Protected-kind false positives (must be 0)")
        out.appendLine("- Count: ${protectedHits.size}")
        for (row in protectedHits) {
            out.appendLine("- ${row.id} ${row.verdict.kind} ${fmt(row.verdict.spamScore)} | ${excerpt(row)}")
        }
        out.appendLine()
        out.appendLine("## Rule hits (messages where each rule fired, rules only)")
        val ruleCounts = rows.flatMap { row -> row.hits.map { it.name } }.groupingBy { it }.eachCount()
        for ((name, count) in ruleCounts.entries.sortedByDescending { it.value }) {
            out.appendLine("- $name: $count")
        }
        out.appendLine()
        out.appendLine("## Cross-validation (5 folds, token model learns the rules-only labels)")
        out.appendLine("- Decisions that match the rules-only decision: $agreement of $total")
        out.appendLine("- Decisions that differ: ${total - agreement}")
        out.appendLine()
        out.appendLine("## Top $TOP_N by spam score (masked text, first $TEXT_CHARS characters)")
        out.appendLine("| id | kind | score | signals | text |")
        out.appendLine("| --- | --- | --- | --- | --- |")
        for (row in rows.sortedByDescending { it.verdict.spamScore }.take(TOP_N)) {
            val signals = row.verdict.signals.joinToString(", ") { it.name }
            val score = fmt(row.verdict.spamScore)
            out.appendLine("| ${row.id} | ${row.verdict.kind} | $score | $signals | ${excerpt(row)} |")
        }
        return out.toString()
    }

    private fun buildLabels(rows: List<Row>): JSONArray {
        val labels = JSONArray()
        for (row in rows) {
            val signals = JSONArray()
            for (signal in row.verdict.signals) {
                signals.put(JSONObject().put("name", signal.name).put("weight", signal.weight))
            }
            labels.put(
                JSONObject()
                    .put("_id", row.id)
                    .put("kind", row.verdict.kind.name)
                    .put("spamScore", round(row.verdict.spamScore * SCALE_4) / SCALE_4)
                    .put("signals", signals),
            )
        }
        return labels
    }

    private fun isSpam(score: Double): Boolean = score >= ClassifierConfig.SPAM_THRESHOLD

    private fun bucketOf(score: Double): Int = minOf((score * BINS).toInt(), BINS - 1)

    private fun fmt(value: Double): String = "%.3f".format(Locale.ROOT, value)

    private fun excerpt(row: Row): String = row.masked.take(TEXT_CHARS)

    private companion object {
        const val ENV_VAR = "CALLGUARD_SMS_JSON"
        const val FOLDS = 5
        const val BINS = 10
        const val TOP_N = 30
        const val TEXT_CHARS = 60
        const val HIGH_CONFIDENCE = 0.8
        const val SCALE_4 = 10000.0
        val PROTECTED_KINDS = setOf(SmsKind.OTP, SmsKind.FINANCE, SmsKind.GOVERNMENT, SmsKind.TRANSACTIONAL)
    }
}
