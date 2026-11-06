package com.callguard.spam

import com.callguard.core.PhoneNumber
import com.callguard.data.ReputationEntry
import java.time.Instant
import java.time.ZoneId

enum class SpamSource { BUNDLED, LEARNED, USER, HEURISTIC, NONE }

data class SpamAssessment(val number: PhoneNumber, val score: Int, val isSpam: Boolean, val reason: String)

interface ReputationStore {
    fun get(n: PhoneNumber): ReputationEntry?
    fun put(entry: ReputationEntry)
    fun remove(n: PhoneNumber)
}

class SpamPolicy(
    private val reputation: ReputationStore,
    private val isBundledSpam: (PhoneNumber) -> Boolean,
    private val callsSince: (PhoneNumber, Long) -> Int,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    companion object {
        const val SPAM_THRESHOLD = 70
        private const val USER_SPAM_SCORE = 100
        private const val BUNDLED_SCORE = 90
        private const val FREQUENT_CALLS_WINDOW_MILLIS = 10 * 60_000L
        private const val FREQUENT_CALLS = 3
        private const val TELEMARKETING_PREFIX = "+91140"
    }

    fun assess(n: PhoneNumber, nowMillis: Long = System.currentTimeMillis()): SpamAssessment {
        reputation.get(n)?.takeIf { it.source == SpamSource.USER }?.let { mark ->
            return if (mark.isSpam) {
                SpamAssessment(n, USER_SPAM_SCORE, true, "user marked spam")
            } else {
                SpamAssessment(n, 0, false, "user marked not spam")
            }
        }
        if (isBundledSpam(n)) return SpamAssessment(n, BUNDLED_SCORE, true, "bundled spam list")

        val reasons = mutableListOf<String>()
        var score = 0
        if (callsSince(n, nowMillis - FREQUENT_CALLS_WINDOW_MILLIS) >= FREQUENT_CALLS) {
            score += 40
            reasons += "3+ calls in 10 min"
        }
        if (n.e164.startsWith(TELEMARKETING_PREFIX)) {
            score += 30
            reasons += "telemarketing number series"
        }
        if (isNight(nowMillis)) {
            score += 10
            reasons += "call between 22:00 and 07:00"
        }
        score = score.coerceAtMost(100)
        return SpamAssessment(
            number = n,
            score = score,
            isSpam = score >= SPAM_THRESHOLD,
            reason = reasons.joinToString("; ").ifEmpty { "no spam signals" },
        )
    }

    fun mark(n: PhoneNumber, spam: Boolean) {
        reputation.put(
            ReputationEntry(
                number = n.e164,
                score = if (spam) USER_SPAM_SCORE else 0,
                isSpam = spam,
                source = SpamSource.USER,
                updated = System.currentTimeMillis(),
            ),
        )
    }

    /** Removes only the user's own mark; learned entries stay. */
    fun clearMark(n: PhoneNumber) {
        if (reputation.get(n)?.source == SpamSource.USER) reputation.remove(n)
    }

    private fun isNight(millis: Long): Boolean {
        val hour = Instant.ofEpochMilli(millis).atZone(zone).hour
        return hour >= 22 || hour < 7
    }
}
