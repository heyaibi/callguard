package com.callguard.messaging

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder

/**
 * Sends the reply for a "respond via message" request, such as a call-screening text. Required for the
 * default SMS role. Runs only when the system sends RESPOND_VIA_MESSAGE, which needs SEND_RESPOND_VIA_MESSAGE.
 */
class HeadlessSmsSendService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val target = intent?.data?.schemeSpecificPart?.let { Uri.decode(it.substringBefore('?')) }
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (!target.isNullOrBlank() && !text.isNullOrBlank()) {
            runCatching { SmsProvider.send(this, addressToNumber(target), text) }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
