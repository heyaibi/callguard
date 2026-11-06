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

/**
 * The Messages list filter: the kinds to show. An empty set shows every kind. The tabs do the spam split, so the
 * filter has no score part.
 */
data class ListFilter(
    val kinds: Set<SmsKind> = emptySet(),
) {
    /** True when the filter hides some conversations: at least one kind is picked. */
    val active: Boolean get() = kinds.isNotEmpty()

    /**
     * True when a conversation with [verdict] shows. A conversation with no verdict has no received text to sort.
     * It shows only when the filter is off.
     */
    fun matches(verdict: SmsVerdict?): Boolean = !active || (verdict != null && verdict.kind in kinds)

    /** The same filter with [kind] added to the picked kinds, or removed when it is already picked. */
    fun toggleKind(kind: SmsKind): ListFilter = copy(kinds = if (kind in kinds) kinds - kind else kinds + kind)
}

/**
 * The kind chips a tab shows: every kind that one of its [verdicts] has, plus every kind already [picked], so a picked
 * chip never vanishes. Kinds keep the [SmsKind] order.
 */
fun kindsShown(
    verdicts: List<SmsVerdict?>,
    picked: Set<SmsKind>,
): List<SmsKind> {
    val present = verdicts.mapNotNullTo(mutableSetOf()) { it?.kind } + picked
    return SmsKind.entries.filter { it in present }
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

/**
 * The faint line under a row's time. The inbox shows the kind only, such as `BANKS`. The spam tab adds the spam
 * score, such as `SCAM · 87`.
 */
fun verdictCaption(
    verdict: SmsVerdict,
    withScore: Boolean,
): String {
    if (!withScore) return kindLabel(verdict.kind)
    val score = (verdict.spamScore * SCORE_SCALE).roundToInt()
    return "${kindLabel(verdict.kind)} · $score"
}
