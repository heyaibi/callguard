package com.callguard.messaging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

/**
 * Receives texts while another app is the default SMS app. The system stores and alerts for those,
 * so this receiver only logs them. When CallGuard is the default, [SmsDeliverReceiver] handles the text instead.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (isDefaultSmsApp(context)) return
        val parts = smsParts(intent) ?: return
        val pending = goAsync()
        thread {
            try {
                for (sms in joinParts(parts)) {
                    MessageHandler.logOnly(context, sms.address, sms.body, sms.timestamp)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
