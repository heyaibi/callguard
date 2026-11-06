package com.callguard.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.callguard.MainActivity
import com.callguard.core.PhoneNumber
import com.callguard.core.PhoneNumbers

/**
 * Posts text notifications: sound for NOTIFY, a silent one for SILENCED, nothing for the rest. Each notification
 * offers Reply (inline) and Mark read, and tapping it opens the conversation.
 */
object MessageNotifier {
    private const val CHANNEL_SOUND = "messages"
    private const val CHANNEL_SILENT = "messages_silent"
    private const val TAG_PREFIX = "sms:"
    private const val NOTIFICATION_ID = 0

    /** RemoteInput needs a mutable PendingIntent from Android 12 on. */
    private val MUTABLE_FLAG = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

    fun post(
        context: Context,
        number: PhoneNumber,
        name: String?,
        body: String,
        mode: AlertMode,
    ) {
        if (mode == AlertMode.NONE) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannels(context)

        val silent = mode == AlertMode.SILENT
        val open =
            PendingIntent.getActivity(
                context,
                number.e164.hashCode(),
                MainActivity.threadIntent(context, number.e164),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, if (silent) CHANNEL_SILENT else CHANNEL_SOUND)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(name ?: PhoneNumbers.format(number))
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(if (silent) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
                .setSilent(silent)
                .setContentIntent(open)
                .addAction(replyAction(context, number))
                .addAction(markReadAction(context, number))
                .setAutoCancel(true)
                .build()
        try {
            manager.notify(TAG_PREFIX + number.e164, NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS was revoked between the check and the post; the text is still logged.
        }
    }

    /** Removes the notification for [number]: its texts were read or answered. */
    fun cancel(
        context: Context,
        number: PhoneNumber,
    ) {
        NotificationManagerCompat.from(context).cancel(TAG_PREFIX + number.e164, NOTIFICATION_ID)
    }

    private fun replyAction(
        context: Context,
        number: PhoneNumber,
    ): NotificationCompat.Action {
        val input = RemoteInput.Builder(MessageActionReceiver.KEY_REPLY).setLabel("Reply").build()
        val pending =
            PendingIntent.getBroadcast(
                context,
                0,
                MessageActionReceiver.intent(context, MessageActionReceiver.ACTION_REPLY, number.e164),
                PendingIntent.FLAG_UPDATE_CURRENT or MUTABLE_FLAG,
            )
        return NotificationCompat.Action
            .Builder(android.R.drawable.ic_menu_send, "Reply", pending)
            .addRemoteInput(input)
            .build()
    }

    private fun markReadAction(
        context: Context,
        number: PhoneNumber,
    ): NotificationCompat.Action {
        val pending =
            PendingIntent.getBroadcast(
                context,
                0,
                MessageActionReceiver.intent(context, MessageActionReceiver.ACTION_MARK_READ, number.e164),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat.Action.Builder(android.R.drawable.checkbox_on_background, "Mark read", pending).build()
    }

    private fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SOUND, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Texts from contacts and allowed senders"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SILENT, "Silenced messages", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Texts from unknown senders while silence mode is on"
                setSound(null, null)
                enableVibration(false)
            },
        )
    }
}
