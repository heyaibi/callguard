package com.callguard.call

import android.telecom.Call
import android.telecom.CallAudioState
import com.callguard.data.Direction
import com.callguard.identity.Confidence
import com.callguard.identity.SourceKind

/** The phase the in-call screens show. Connection-setup platform states read as DIALING. */
enum class CallPhase { RINGING, DIALING, ACTIVE, HOLDING, DISCONNECTED }

/** Maps a platform call state to a phase. Only RINGING and SIMULATED_RINGING ring. */
fun phaseFor(platformState: Int): CallPhase =
    when (platformState) {
        Call.STATE_RINGING, Call.STATE_SIMULATED_RINGING -> CallPhase.RINGING
        Call.STATE_ACTIVE -> CallPhase.ACTIVE
        Call.STATE_HOLDING -> CallPhase.HOLDING
        Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED -> CallPhase.DISCONNECTED
        else -> CallPhase.DIALING
    }

/** Where the call's audio goes. */
enum class AudioRoute { EARPIECE, SPEAKER, BLUETOOTH, WIRED }

/** Maps a platform audio route to [AudioRoute]. Unknown or combined routes read as the earpiece. */
fun audioRouteFor(route: Int): AudioRoute =
    when (route) {
        CallAudioState.ROUTE_SPEAKER -> AudioRoute.SPEAKER
        CallAudioState.ROUTE_BLUETOOTH -> AudioRoute.BLUETOOTH
        CallAudioState.ROUTE_WIRED_HEADSET -> AudioRoute.WIRED
        else -> AudioRoute.EARPIECE
    }

/**
 * One call as the in-call screens and notifications show it. Published as an immutable snapshot by [CallManager].
 * [number] is E.164 when the number normalizes, the raw text otherwise, and "PRIVATE" when hidden ([hidden] is true).
 * [silenced] means screening asked for silence, so the call does not ring.
 */
data class CallState(
    val id: Long,
    val direction: Direction,
    val number: String,
    val displayNumber: String,
    val hidden: Boolean,
    val name: String?,
    val source: SourceKind,
    val confidence: Confidence,
    val spam: Boolean,
    val spamScore: Int,
    val phase: CallPhase,
    val connectTime: Long?,
    val silenced: Boolean,
    val muted: Boolean,
    val route: AudioRoute,
    val canHold: Boolean,
)
