package com.callguard.screening

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.callguard.MainActivity
import com.callguard.bridge.OverlayCard
import com.callguard.core.PhoneNumber
import com.callguard.identity.ResolutionResult
import com.callguard.spam.SpamAssessment
import com.callguard.ui.displayNumber
import com.callguard.ui.label

/** Channel for the caller ID card. Heads-up but silent: the ringtone already announces the call. */
const val CHANNEL_CALLER_ID = "caller_id"

/** Channel for notices about rejected calls. Low importance: no sound and no pop-up. */
const val CHANNEL_BLOCKED = "blocked"

/** Intent extra holding the E.164 number whose incoming-call screen MainActivity should open. */
const val EXTRA_INCOMING_NUMBER = "incoming_number"

/** The system removes the card after this long if nobody acts on it. */
private const val CARD_TIMEOUT_MILLIS = 30_000L

/** Title and text of the caller ID card. */
data class CardText(val title: String, val text: String)

/**
 * Card wording. The title is the name, or UNKNOWN. The text is the formatted number, the source and the confidence,
 * with the spam score appended when the number is likely spam.
 */
fun callerCardText(identity: ResolutionResult, spam: SpamAssessment): CardText {
    val parts = mutableListOf(displayNumber(identity.number.e164), identity.source.label())
    if (identity.isKnown) parts += identity.confidence.name
    if (spam.isSpam) parts += "LIKELY SPAM · ${spam.score}"
    return CardText(title = identity.name ?: "UNKNOWN", text = parts.joinToString(" · "))
}

/**
 * Posts the caller ID card for answered calls and a notice for rejected ones. Nothing is posted while notifications are off
 * for the app. Both channels are created before a post, which is harmless when they already exist.
 */
class CallerIdNotifier(private val context: Context) {

    fun showCard(number: PhoneNumber, identity: ResolutionResult, spam: SpamAssessment, decision: ScreeningDecision) {
        val text = callerCardText(identity, spam)
        val open = PendingIntent.getActivity(
            context,
            cardId(number),
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_INCOMING_NUMBER, number.e164)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val card = Notification.Builder(context, CHANNEL_CALLER_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(text.title)
            .setContentText(text.text)
            .setAutoCancel(true)
            .setTimeoutAfter(CARD_TIMEOUT_MILLIS)
            .setContentIntent(open)
            .build()
        post(cardId(number), card)
        // The overlay needs "display over other apps". Without it, OverlayCard would post a second notification instead.
        if (OverlayCard.canShow(context)) {
            OverlayCard.show(
                context,
                name = text.title,
                number = number.e164,
                subtitle = if (decision == ScreeningDecision.SILENCE) "Silenced call" else "Incoming call",
                spam = spam.isSpam,
            )
        }
    }

    fun showBlocked(number: PhoneNumber, name: String?) {
        val open = PendingIntent.getActivity(
            context,
            blockedId(number),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notice = Notification.Builder(context, CHANNEL_BLOCKED)
            .setSmallIcon(android.R.drawable.sym_call_missed)
            .setContentTitle("Blocked ${name ?: displayNumber(number.e164)}")
            .setContentText(if (name != null) "Call rejected · ${displayNumber(number.e164)}" else "Call rejected")
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        post(blockedId(number), notice)
    }

    private fun post(id: Int, notification: Notification) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannels(manager)
        if (manager.areNotificationsEnabled()) manager.notify(id, notification)
    }

    private fun ensureChannels(manager: NotificationManager) {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_CALLER_ID, "Caller ID", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Caller name card for incoming calls"
                setSound(null, null)
                enableVibration(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_BLOCKED, "Blocked calls", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Calls CallGuard rejected"
            },
        )
    }

    private fun cardId(number: PhoneNumber): Int = "card:${number.e164}".hashCode()

    private fun blockedId(number: PhoneNumber): Int = "blocked:${number.e164}".hashCode()
}
