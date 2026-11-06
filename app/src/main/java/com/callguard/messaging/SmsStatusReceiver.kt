package com.callguard.messaging

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

/**
 * Receives the result of each sent text part and updates the outbox row, so a thread shows the text as
 * pending, sent or failed.
 */
class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_SENT) return
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id < 0) return
        val success = resultCode == Activity.RESULT_OK
        val part = intent.getIntExtra(EXTRA_PART, 0)
        val parts = intent.getIntExtra(EXTRA_PARTS, 1)
        val pending = goAsync()
        thread {
            try {
                SmsProvider.onPartSent(context, id, success, lastPart = part >= parts - 1)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SENT = "com.callguard.action.SMS_SENT"
        const val EXTRA_ID = "com.callguard.extra.SMS_ID"
        const val EXTRA_PART = "com.callguard.extra.SMS_PART"
        const val EXTRA_PARTS = "com.callguard.extra.SMS_PARTS"
    }
}
