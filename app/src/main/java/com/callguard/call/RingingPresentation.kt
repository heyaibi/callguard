package com.callguard.call

import com.callguard.data.Direction

/**
 * How the notification of a ringing call is presented: its channel, and whether it may take the full-screen intent.
 * Only the high-importance channel pops up over other apps as a heads-up.
 */
data class RingingPresentation(
    val channelId: String,
    val fullScreen: Boolean,
) {
    val headsUp: Boolean get() = channelId == CallNotifier.CHANNEL_INCOMING
}

/**
 * The notification of a ringing call. Pure: the caller reads the platform state and passes it in. Returns null when the
 * call is not ringing, because its notification is cancelled instead.
 *
 * - Screening silenced the call: the silenced channel, no full-screen intent. It never pops up or takes the screen.
 * - A CallGuard screen is in front and the screen is on: the quiet channel. The call stays in the shade, and nothing
 *   pops up over the screen that already shows it.
 * - Otherwise: the high-importance channel. It pops up as a heads-up over other apps. The full-screen intent opens the
 *   in-call screen when the screen is off or locked, where the platform allows it.
 */
fun ringingPresentation(
    phase: CallPhase,
    silenced: Boolean,
    uiVisible: Boolean,
    screenOn: Boolean,
    fullScreenAllowed: Boolean,
): RingingPresentation? =
    when {
        phase != CallPhase.RINGING -> null
        silenced -> RingingPresentation(CallNotifier.CHANNEL_SILENCED, fullScreen = false)
        uiVisible && screenOn -> RingingPresentation(CallNotifier.CHANNEL_INCOMING_QUIET, fullScreen = false)
        else -> RingingPresentation(CallNotifier.CHANNEL_INCOMING, fullScreen = fullScreenAllowed)
    }

/**
 * Whether a new call starts the in-call screen itself. A placed call always does. A ringing call does when a CallGuard
 * screen is in front (the call comes up with no heads-up) or when the screen is off (as before; the full-screen intent
 * covers it too). Over another app with the screen on, a ringing call only raises its heads-up, and tapping that opens
 * the screen. A silenced call never does.
 */
fun startsInCallUi(
    direction: Direction,
    phase: CallPhase,
    silenced: Boolean,
    uiVisible: Boolean,
    screenOn: Boolean,
): Boolean = direction == Direction.OUTGOING || (phase == CallPhase.RINGING && !silenced && (uiVisible || !screenOn))
