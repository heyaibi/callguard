package com.callguard.screening

import com.callguard.data.Direction
import com.callguard.prefs.Settings

/** Time the service has to answer one call. The platform allows a few seconds; this leaves a safe margin. */
const val SCREENING_DEADLINE_MILLIS = 2_500L

/** The flags of `CallScreeningService.CallResponse`, as plain values so the mapping can be unit tested. */
data class CallResponseSpec(
    val disallowCall: Boolean = false,
    val rejectCall: Boolean = false,
    val skipCallLog: Boolean = false,
    val skipNotification: Boolean = false,
    val silenceCall: Boolean = false,
)

/** BLOCK rejects the call and hides its notification; SILENCE only mutes it; ALLOW keeps the platform defaults. */
fun ScreeningDecision.responseSpec(): CallResponseSpec =
    when (this) {
        ScreeningDecision.BLOCK -> CallResponseSpec(disallowCall = true, rejectCall = true, skipNotification = true)
        ScreeningDecision.SILENCE -> CallResponseSpec(silenceCall = true)
        ScreeningDecision.ALLOW -> CallResponseSpec()
    }

/** Outcome stored in the call log for this decision. */
fun ScreeningDecision.logOutcome(): String =
    when (this) {
        ScreeningDecision.ALLOW -> "ALLOWED"
        ScreeningDecision.SILENCE -> "SILENCED"
        ScreeningDecision.BLOCK -> "BLOCKED"
    }

/**
 * The decision sent for one call.
 * Outgoing calls are always allowed. [screened] is the full screening result, or null when the deadline passed first;
 * without it only the block list applies, so a block-listed number is still rejected in time.
 */
fun answerFor(
    direction: Direction,
    e164: String?,
    settings: Settings,
    screened: ScreeningDecision?,
): ScreeningDecision =
    when {
        direction == Direction.OUTGOING -> ScreeningDecision.ALLOW
        screened != null -> screened
        e164 != null && e164 in settings.blockList -> ScreeningDecision.BLOCK
        else -> ScreeningDecision.ALLOW
    }

/**
 * Sends at most one answer per call. The worker's full decision and the deadline fallback both go through [offer];
 * the first one wins and the later one is dropped, so the platform never receives two answers.
 */
class CallAnswer(
    private val send: (ScreeningDecision) -> Unit,
) {
    private val lock = Any()
    private var sent: ScreeningDecision? = null

    /** Sends [decision] unless an answer already went out. Returns the answer that went out for this call. */
    fun offer(decision: ScreeningDecision): ScreeningDecision =
        synchronized(lock) {
            sent ?: decision.also {
                sent = it
                send(it)
            }
        }
}

/** Caller name delivered with the call (CNAP). It is trusted only when the network presents it as allowed. */
fun trustedCnapName(
    name: String?,
    presentation: Int,
    allowedPresentation: Int,
): String? = name?.trim()?.takeIf { presentation == allowedPresentation && it.isNotEmpty() }
