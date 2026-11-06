package com.callguard.screening

import com.callguard.core.PhoneNumbers
import java.util.concurrent.ConcurrentHashMap

/**
 * The decision the screening service sent for each call, kept in memory by call key for the in-call service.
 * The screening service records a decision before it answers. The in-call service reads the record when the call
 * ends, which tells it whether CallGuard screened the call (so screening already logged it) and, when it reads it
 * while ringing, whether the call was silenced. Outgoing calls can be screened after the platform adds them, so the
 * record must outlive the call; the in-call service forgets it at the end. Entries expire after [TTL_MILLIS].
 */
object ScreeningVerdicts {
    /** The key for a call that has no usable number, such as a hidden caller. */
    const val PRIVATE_KEY = "PRIVATE"

    private const val TTL_MILLIS = 60 * 60_000L

    private class Verdict(val decision: ScreeningDecision, val recordedAt: Long)

    private val verdicts = ConcurrentHashMap<String, Verdict>()

    /** The key for a call's raw number: E.164 when it normalizes, the trimmed text otherwise, [PRIVATE_KEY] when hidden. */
    fun keyFor(raw: String?): String {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return PRIVATE_KEY
        return PhoneNumbers.normalize(text)?.e164 ?: text
    }

    fun record(key: String, decision: ScreeningDecision, now: Long = System.currentTimeMillis()) {
        verdicts[key] = Verdict(decision, now)
    }

    /** The decision recorded for [key] within the TTL, or null when there is none. */
    fun peek(key: String, now: Long = System.currentTimeMillis()): ScreeningDecision? {
        val verdict = verdicts[key] ?: return null
        if (now - verdict.recordedAt > TTL_MILLIS) {
            verdicts.remove(key, verdict)
            return null
        }
        return verdict.decision
    }

    fun forget(key: String) {
        verdicts.remove(key)
    }
}
