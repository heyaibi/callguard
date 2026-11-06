package com.callguard.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Runs the Answer, Decline and Hang up buttons of the call notifications. Not exported: only CallGuard's own
 * notifications send these actions. Runs on the main thread, where [CallManager] acts.
 */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val callId = intent.getLongExtra(EXTRA_CALL_ID, NO_CALL)
        if (callId == NO_CALL) return
        when (intent.action) {
            ACTION_ANSWER -> CallManager.answer(callId)
            ACTION_DECLINE -> CallManager.decline(callId)
            ACTION_HANG_UP -> CallManager.hangUp(callId)
        }
    }

    companion object {
        const val ACTION_ANSWER = "com.callguard.call.action.ANSWER"
        const val ACTION_DECLINE = "com.callguard.call.action.DECLINE"
        const val ACTION_HANG_UP = "com.callguard.call.action.HANG_UP"
        const val EXTRA_CALL_ID = "com.callguard.call.extra.CALL_ID"
        private const val NO_CALL = -1L
    }
}
