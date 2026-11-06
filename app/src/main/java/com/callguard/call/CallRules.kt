package com.callguard.call

import android.app.NotificationManager
import android.media.AudioManager
import com.callguard.data.Direction

/** What the ringer does for one incoming call. */
data class RingPlan(val sound: Boolean, val vibrate: Boolean) {
    val any: Boolean get() = sound || vibrate
}

/**
 * The ringer output for an incoming call. Screening silence or Do Not Disturb turns off both sound and vibration.
 * Otherwise the ringer mode decides: silent mode does nothing, vibrate mode vibrates only, normal mode rings and vibrates.
 */
fun ringPlan(silenced: Boolean, dndSuppressed: Boolean, ringerMode: Int): RingPlan = when {
    silenced || dndSuppressed -> RingPlan(sound = false, vibrate = false)
    ringerMode == AudioManager.RINGER_MODE_SILENT -> RingPlan(sound = false, vibrate = false)
    ringerMode == AudioManager.RINGER_MODE_VIBRATE -> RingPlan(sound = false, vibrate = true)
    else -> RingPlan(sound = true, vibrate = true)
}

/** From this release the platform reports Do Not Disturb per call (EXTRA_IS_SUPPRESSED_BY_DO_NOT_DISTURB). */
private const val PLATFORM_DND_SIGNAL_SDK = 34

/**
 * Whether Do Not Disturb suppresses one call's ringing. From API 34 the platform's per-call flag decides. Before that,
 * any interruption filter other than ALL counts as Do Not Disturb.
 */
fun dndSuppresses(platformSaysSuppressed: Boolean, sdkInt: Int, interruptionFilter: Int): Boolean =
    if (sdkInt >= PLATFORM_DND_SIGNAL_SDK) platformSaysSuppressed
    else interruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL

/**
 * The outcome logged for a call CallGuard did not screen. Placed calls are OUTGOING. Incoming calls are ALLOWED when
 * answered and MISSED otherwise; a declined call counts as missed.
 */
fun unscreenedOutcome(direction: Direction, answered: Boolean): String = when {
    direction == Direction.OUTGOING -> "OUTGOING"
    answered -> "ALLOWED"
    else -> "MISSED"
}
