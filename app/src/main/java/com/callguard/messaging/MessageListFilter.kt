package com.callguard.messaging

import com.callguard.spam.classify.ClassifierConfig
import com.callguard.spam.classify.SmsKind
import com.callguard.spam.classify.SmsVerdict
import kotlin.math.roundToInt

/** One received text as the phone reports it: the raw sender address and the full body. */
data class IncomingText(
    val sender: String,
    val body: String,
)

/**
 * The newest received text per sender, keyed by the e164 form of its address. Sent and outgoing texts never count.
 * The newest text has the latest date. A tie goes to the higher id.
 */
fun latestIncoming(rows: List<SmsRow>): Map<String, IncomingText> =
    rows
        .filter { it.type == SmsType.INBOX }
        .groupBy { addressToNumber(it.address).e164 }
        .mapValues { (_, texts) ->
            val newest = texts.maxWith(compareBy<SmsRow>({ it.date }, { it.id }))
            IncomingText(sender = newest.address, body = newest.body)
        }

/** Which side of the spam threshold the list keeps: any score, the spam side, or the not-spam side. */
enum class ScoreFilter { ANY, SPAM, NOT_SPAM }

/**
 * The Messages list filter. [kinds] lists the kinds to show. An empty set shows every kind. [score] keeps one side
 * of [ClassifierConfig.SPAM_THRESHOLD]. A conversation shows only when it matches both.
 */
data class ListFilter(
    val kinds: Set<SmsKind> = emptySet(),
    val score: ScoreFilter = ScoreFilter.ANY,
) {
    /** True when the filter hides some conversations: a kind is picked, or the score is not [ScoreFilter.ANY]. */
    val active: Boolean get() = kinds.isNotEmpty() || score != ScoreFilter.ANY

    /**
     * True when a conversation with [verdict] shows. A conversation with no verdict has no received text to score.
     * It shows only when the filter is off.
     */
    fun matches(verdict: SmsVerdict?): Boolean {
        if (verdict == null) return !active
        val kindMatches = kinds.isEmpty() || verdict.kind in kinds
        val scoreMatches =
            when (score) {
                ScoreFilter.ANY -> true
                ScoreFilter.SPAM -> isSpamVerdict(verdict)
                ScoreFilter.NOT_SPAM -> !isSpamVerdict(verdict)
            }
        return kindMatches && scoreMatches
    }

    /** The same filter with [kind] added to the picked kinds, or removed when it is already picked. */
    fun toggleKind(kind: SmsKind): ListFilter = copy(kinds = if (kind in kinds) kinds - kind else kinds + kind)
}

/** True when the verdict scores at or above [ClassifierConfig.SPAM_THRESHOLD]. */
fun isSpamVerdict(verdict: SmsVerdict): Boolean = verdict.spamScore >= ClassifierConfig.SPAM_THRESHOLD

/** The short caps label for a kind, as the filter bar shows it. */
fun kindLabel(kind: SmsKind): String =
    when (kind) {
        SmsKind.OTP -> "OTP"
        SmsKind.FINANCE -> "BANKS"
        SmsKind.GOVERNMENT -> "GOVERNMENT"
        SmsKind.TRANSACTIONAL -> "SERVICE"
        SmsKind.DELIVERY -> "DELIVERY"
        SmsKind.PERSONAL -> "PERSONAL"
        SmsKind.PROMOTIONAL -> "PROMOTIONS"
        SmsKind.SCAM -> "SCAM"
    }

/** Scales a spam score from 0 to 1 to the 0 to 100 that a row's caption shows. */
private const val SCORE_SCALE = 100

/** The faint line under a row's time: the kind and the spam score, such as `PROMOTIONS · 72`. */
fun verdictCaption(verdict: SmsVerdict): String {
    val score = (verdict.spamScore * SCORE_SCALE).roundToInt()
    return "${kindLabel(verdict.kind)} · $score"
}
