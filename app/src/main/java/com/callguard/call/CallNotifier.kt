package com.callguard.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * The notifications that stand for calls. A ringing call gets a CallStyle notification with Answer and Decline; its
 * full-screen intent opens [InCallActivity]. The call in progress is the foreground notification of
 * [CallForegroundService], because the platform accepts an ongoing CallStyle notification only from a foreground
 * service. Below API 31 the same actions are plain buttons. Main thread only.
 */
object CallNotifier {
    private const val TAG = "CallNotifier"

    /**
     * Ringing calls that pop up over other apps as a heads-up. High importance with no sound: CallGuard plays the
     * ringtone itself. Used only when no CallGuard screen is in front.
     */
    const val CHANNEL_INCOMING = "incoming_calls"

    /**
     * Ringing calls while a CallGuard screen is in front. The call stays in the shade, but nothing pops up over the
     * screen that already shows it. Default importance, with no sound or vibration.
     */
    const val CHANNEL_INCOMING_QUIET = "incoming_calls_silent"

    /** Calls screening silenced. Default importance, so they never pop up or ring. */
    const val CHANNEL_SILENCED = "silenced_calls"

    /** The call in progress. Low importance: it stays in the shade without alerts. */
    const val CHANNEL_ONGOING = "ongoing_calls"

    /** The notification of the call in progress. Only one call is in progress at a time in this notification. */
    const val ONGOING_ID = 7151

    /**
     * Posts a ringing call on the channel [ringingPresentation] picks, replacing its notification. A call that has been
     * answered or has ended loses its ringing notification.
     */
    fun show(context: Context, call: CallState, uiVisible: Boolean, screenOn: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val presentation = ringingPresentation(call.phase, call.silenced, uiVisible, screenOn, fullScreenAllowed(manager))
        if (presentation == null) {
            cancel(context, call.id)
            return
        }
        if (!manager.areNotificationsEnabled()) return
        runCatching {
            ensureChannels(manager)
            manager.notify(ringingId(call.id), ringing(context, call, presentation))
        }.onFailure { Log.w(TAG, "Could not post the ringing notification for call ${call.id}", it) }
    }

    fun cancel(context: Context, callId: Long) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ringingId(callId))
    }

    /** Creates the channels. Safe to call often; a foreground notification needs its channel to exist first. */
    fun createChannels(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.let { ensureChannels(it) }
    }

    /** Clears the in-progress notification when the foreground service is not running. */
    fun cancelOngoing(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ONGOING_ID)
    }

    /**
     * Posts the call in progress as a plain notification. Used only when the platform refuses to start the foreground
     * service, for example from the background.
     */
    fun postPlainOngoing(context: Context, call: CallState) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!manager.areNotificationsEnabled()) return
        runCatching {
            ensureChannels(manager)
            manager.notify(ONGOING_ID, ongoing(context, call, callStyle = false))
        }.onFailure { Log.w(TAG, "Could not post the call in progress", it) }
    }

    /** The notification of the call in progress. [callStyle] false gives the plain form with a hang-up button. */
    fun ongoing(context: Context, call: CallState, callStyle: Boolean): Notification {
        val builder = Notification.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setCategory(Notification.CATEGORY_CALL)
            .setContentIntent(openUi(context, call.id))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(call.connectTime ?: System.currentTimeMillis())
            .setUsesChronometer(call.phase == CallPhase.ACTIVE)
        val hangUp = actionIntent(context, CallActionReceiver.ACTION_HANG_UP, call.id)
        if (callStyle && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setStyle(Notification.CallStyle.forOngoingCall(person(call), hangUp))
        } else {
            builder.setContentTitle(titleOf(call))
                .setContentText(subtitleOf(call))
                .addAction(plainAction(context, "Hang up", hangUp))
        }
        return builder.build()
    }

    /** The foreground notification shown for the instant before the service learns that no call is left. */
    fun ended(context: Context): Notification = Notification.Builder(context, CHANNEL_ONGOING)
        .setSmallIcon(android.R.drawable.stat_sys_phone_call)
        .setContentTitle("Call ended")
        .setOngoing(true)
        .build()

    private fun ringing(context: Context, call: CallState, presentation: RingingPresentation): Notification {
        val builder = Notification.Builder(context, presentation.channelId)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setCategory(Notification.CATEGORY_CALL)
            .setContentIntent(openUi(context, call.id))
            .setOnlyAlertOnce(true)
        // Only the heads-up channel takes the full-screen intent. Android 14 and later can refuse full-screen intents.
        if (presentation.fullScreen) {
            builder.setFullScreenIntent(openUi(context, call.id), true)
        }
        val answer = actionIntent(context, CallActionReceiver.ACTION_ANSWER, call.id)
        val decline = actionIntent(context, CallActionReceiver.ACTION_DECLINE, call.id)
        // The platform accepts a CallStyle notification only with a full-screen intent or from a foreground service. A
        // ringing call without one (the quiet channel, or a silenced call) gets the plain form with the same two buttons.
        if (presentation.fullScreen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setStyle(Notification.CallStyle.forIncomingCall(person(call), decline, answer))
        } else {
            builder.setContentTitle(titleOf(call))
                .setContentText(subtitleOf(call))
                .addAction(plainAction(context, "Decline", decline))
                .addAction(plainAction(context, "Answer", answer))
        }
        return builder.build()
    }

    private fun person(call: CallState): Person =
        Person.Builder().setName(titleOf(call)).setImportant(true).build()

    /** The name when known, otherwise the number as it is shown on screen. */
    private fun titleOf(call: CallState): String = call.name ?: call.displayNumber

    private fun subtitleOf(call: CallState): String = when (call.phase) {
        CallPhase.RINGING -> if (call.silenced) "Silenced call" else "Incoming call"
        CallPhase.DIALING -> "Calling"
        CallPhase.HOLDING -> "On hold"
        CallPhase.ACTIVE -> "Ongoing call"
        CallPhase.DISCONNECTED -> "Call ended"
    }

    private fun fullScreenAllowed(manager: NotificationManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || manager.canUseFullScreenIntent()

    private fun openUi(context: Context, callId: Long): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode("open", callId),
        InCallActivity.intent(context),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun actionIntent(context: Context, action: String, callId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode(action, callId),
        Intent(context, CallActionReceiver::class.java)
            .setAction(action)
            .putExtra(CallActionReceiver.EXTRA_CALL_ID, callId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    @Suppress("DEPRECATION")
    private fun plainAction(context: Context, label: String, intent: PendingIntent): Notification.Action =
        Notification.Action.Builder(0, label, intent).build()

    private fun ensureChannels(manager: NotificationManager) {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_INCOMING, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Ringing calls. CallGuard plays the ringtone."
                setSound(null, null)
                enableVibration(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_INCOMING_QUIET, "Incoming calls in CallGuard", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Ringing calls while CallGuard's call screen is open. No heads-up, no sound."
                setSound(null, null)
                enableVibration(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SILENCED, "Silenced calls", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Calls that screening silenced. They do not ring."
                setSound(null, null)
                enableVibration(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, "Ongoing calls", NotificationManager.IMPORTANCE_LOW).apply {
                description = "A call in progress"
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private fun ringingId(callId: Long): Int = "call:$callId".hashCode()

    private fun requestCode(kind: String, callId: Long): Int = "$kind:$callId".hashCode()
}
