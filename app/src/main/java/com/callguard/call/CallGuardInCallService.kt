package com.callguard.call

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

/**
 * The in-call service of the default dialer. Telecom binds it while calls exist and forwards every call to
 * [CallManager]. CallGuard draws the in-call UI itself ([InCallActivity]) and rings itself, as the manifest declares.
 */
class CallGuardInCallService : InCallService() {
    override fun onCreate() {
        super.onCreate()
        CallManager.attach(this)
    }

    override fun onDestroy() {
        CallManager.detach(this)
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.add(call)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.remove(call)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        CallManager.onAudioChanged(audioState)
    }

    override fun onSilenceRinger() {
        super.onSilenceRinger()
        CallManager.silenceRinger()
    }

    /** Telecom asks the UI to come forward, for example after the user taps a call elsewhere. */
    override fun onBringToForeground(showDialpad: Boolean) {
        super.onBringToForeground(showDialpad)
        InCallActivity.start(this)
    }
}
