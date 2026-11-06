package com.callguard.spam.classify

import java.text.Normalizer

/**
 * Folded SMS text. [folded] keeps digits, so an OTP code stays visible. [masked] replaces digits, amounts and
 * URLs with `<num>`, `<amt>` and `<url>`. [urlHosts] lists the host of every URL, in lower case.
 */
internal class NormalizedSms(
    val folded: String,
    val masked: String,
    val urlHosts: List<String>,
)

/** Folds and masks SMS text. The rules and the token model both read its output. */
internal object TextNormalizer {
    private const val TLDS =
        "com|in|co|net|org|gov|edu|info|biz|io|ly|me|gl|gd|gy|cc|to|is|tk|ml|ga|cf|gq|pw|xyz|top|site|online|app|" +
            "link|click|live|shop|club|store|win|bid|loan|cash|work|today|vip|icu|ru|cn|us|uk|ai|tv|ws|mobi|pro|sbs"

    private val urlRe =
        Regex(
            """(?:https?://|www\.)\S+|(?<![\p{L}\p{N}@/.\-])(?:[a-z0-9\-]+\.)+(?:$TLDS)(?![\p{L}\p{N}])(?:/\S*)?""",
        )
    private val amountRe = Regex("""(?:₹|(?<![\p{L}\p{N}])(?:rs|inr)\.?)\s*\d[\d,]*(?:\.\d+)?""")
    private val wordRe = Regex("""[\p{L}\p{N}@$]+""")
    private val repeatRe = Regex("""(\p{L})\1{2,}""")
    private val digitsRe = Regex("""\d+""")
    private val spacesRe = Regex("""\s+""")
    private val leet =
        mapOf(
            '0' to 'o',
            '1' to 'i',
            '3' to 'e',
            '4' to 'a',
            '5' to 's',
            '7' to 't',
            '@' to 'a',
            '$' to 's',
        )

    /** Lower-cases, applies NFKC, reads leetspeak inside words, collapses long letter runs and masks. */
    fun normalize(raw: String): NormalizedSms {
        val lower = Normalizer.normalize(raw, Normalizer.Form.NFKC).lowercase()
        val hosts = urlRe.findAll(lower).map { hostOf(it.value) }.toList()
        val noUrls = urlRe.replace(lower, " <url> ")
        val noAmounts = amountRe.replace(noUrls, " <amt> ")
        val unleeted = wordRe.replace(noAmounts) { unleet(it.value) }
        val collapsed = repeatRe.replace(unleeted) { it.groupValues[1].repeat(2) }
        val folded = spacesRe.replace(collapsed, " ").trim()
        val masked = spacesRe.replace(digitsRe.replace(folded, "<num>"), " ").trim()
        return NormalizedSms(folded, masked, hosts)
    }

    private fun hostOf(url: String): String =
        url
            .substringAfter("//", url)
            .removePrefix("www.")
            .substringBefore('/')
            .substringBefore('?')

    /** Maps leet characters to letters, but only in a word that has at least one letter. */
    private fun unleet(word: String): String {
        if (word.none { it.isLetter() }) return word
        return buildString(word.length) { word.forEach { append(leet[it] ?: it) } }
    }
}
