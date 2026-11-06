package com.callguard.messaging

import android.content.Context
import com.callguard.CallGuardApp
import com.callguard.core.PhoneNumber
import com.callguard.data.MessageLogEntry

/** Decides what happens to a received text, then records it. */
object MessageHandler {
    /**
     * Default-SMS-app path. The text is stored in the SMS provider so nothing is lost, logged, and
     * notified according to its outcome.
     */
    fun handle(
        context: Context,
        sender: String,
        body: String,
        timestamp: Long,
    ) {
        val decision = decide(context, sender, body, timestamp)
        if (isDefaultSmsApp(context)) {
            SmsProvider.insertInbox(context, sender.trim(), body, timestamp, isReadOnArrival(decision.outcome))
        }
        record(context, decision, body, timestamp)
        MessageNotifier.post(context, decision.number, decision.name, body, alertModeFor(decision.outcome))
    }

    /**
     * Fallback path, used while another app is the default SMS app. The system stores and alerts for those
     * texts, so this only logs them.
     */
    fun logOnly(
        context: Context,
        sender: String,
        body: String,
        timestamp: Long,
    ) {
        record(context, decide(context, sender, body, timestamp), body, timestamp)
    }

    private class Decision(
        val number: PhoneNumber,
        val name: String?,
        val outcome: MessageOutcome,
    )

    /** Classifies the text and applies the filter policy. The SMS receivers call this from a background thread. */
    private fun decide(
        context: Context,
        sender: String,
        body: String,
        timestamp: Long,
    ): Decision {
        val graph = CallGuardApp.graph(context)
        val number = addressToNumber(sender)
        val identity = graph.resolutionPolicy.resolve(number)
        val spam = graph.spamPolicy.assess(number, timestamp)
        val outcome =
            graph.messageFilterPolicy.decide(
                identity = identity,
                spam = spam,
                verdict = graph.smsClassifier.classify(sender, body),
                settings = graph.effectiveSettings(),
                inContacts = graph.isInContacts(number),
            )
        return Decision(number, identity.name, outcome)
    }

    private fun record(
        context: Context,
        decision: Decision,
        body: String,
        timestamp: Long,
    ) {
        CallGuardApp.graph(context).repos.messages.add(
            MessageLogEntry(
                number = decision.number.e164,
                name = decision.name,
                body = body,
                outcome = decision.outcome,
                timestamp = timestamp,
            ),
        )
    }
}
