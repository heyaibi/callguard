package com.callguard.messaging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.RemoteInput
import com.callguard.core.PhoneNumber
import kotlin.concurrent.thread

/**
 * Handles the Reply and Mark read actions on a text notification. A reply goes through [SmsProvider], so it lands
 * in the conversation like any other outgoing text.
 */
class MessageActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val e164 = intent.getStringExtra(EXTRA_NUMBER)?.takeIf { it.isNotBlank() } ?: return
        val number = PhoneNumber(e164)
        when (intent.action) {
            ACTION_REPLY -> {
                val reply =
                    RemoteInput
                        .getResultsFromIntent(intent)
                        ?.getCharSequence(KEY_REPLY)
                        ?.toString()
                        ?.trim()
                if (reply.isNullOrEmpty()) return
                val pending = goAsync()
                thread {
                    try {
                        val sent = runCatching { SmsProvider.send(context, number, reply) }.isSuccess
                        if (!sent) toast(context, "Reply not sent")
                        if (isDefaultSmsApp(context)) SmsProvider.markRead(context, number)
                        MessageNotifier.cancel(context, number)
                    } finally {
                        pending.finish()
                    }
                }
            }

            ACTION_MARK_READ -> {
                val pending = goAsync()
                thread {
                    try {
                        if (isDefaultSmsApp(context)) SmsProvider.markRead(context, number)
                        MessageNotifier.cancel(context, number)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private fun toast(
        context: Context,
        text: String,
    ) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        const val ACTION_REPLY = "com.callguard.action.REPLY"
        const val ACTION_MARK_READ = "com.callguard.action.MARK_READ"
        const val EXTRA_NUMBER = "com.callguard.extra.NUMBER"
        const val KEY_REPLY = "com.callguard.key.REPLY"

        /** An intent for [action] on the conversation with [e164]. The data URI keeps each sender's PendingIntent distinct. */
        fun intent(
            context: Context,
            action: String,
            e164: String,
        ): Intent =
            Intent(context, MessageActionReceiver::class.java)
                .setAction(action)
                .setData(Uri.fromParts("callguard", "$action:$e164", null))
                .putExtra(EXTRA_NUMBER, e164)
    }
}
