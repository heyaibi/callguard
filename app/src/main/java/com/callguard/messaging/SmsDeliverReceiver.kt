package com.callguard.messaging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

/**
 * Receives texts while CallGuard is the default SMS app. The default app must store each text itself,
 * so [MessageHandler.handle] writes it to the provider.
 */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val parts = smsParts(intent) ?: return
        val pending = goAsync()
        thread {
            try {
                for (sms in joinParts(parts)) {
                    MessageHandler.handle(context, sms.address, sms.body, sms.timestamp)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
