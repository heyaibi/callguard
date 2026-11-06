package com.callguard.messaging

/** How a text reaches the user: a sound, a silent notification, or nothing in the shade. */
enum class AlertMode { SOUND, SILENT, NONE }

/** NOTIFY sounds, SILENCED posts silently, and quiet or blocked texts stay out of the shade. */
fun alertModeFor(outcome: MessageOutcome): AlertMode =
    when (outcome) {
        MessageOutcome.NOTIFY -> AlertMode.SOUND
        MessageOutcome.SILENCED -> AlertMode.SILENT
        MessageOutcome.QUIET_INBOX, MessageOutcome.BLOCKED -> AlertMode.NONE
    }

/** Quiet and blocked texts are stored already read, so they never show as unread. */
fun isReadOnArrival(outcome: MessageOutcome): Boolean = outcome == MessageOutcome.QUIET_INBOX || outcome == MessageOutcome.BLOCKED

/** One SMS part as the telephony framework reports it. */
data class SmsPart(
    val address: String,
    val body: String,
    val timestamp: Long,
)

/** One message after its parts are joined. */
data class InboundSms(
    val address: String,
    val body: String,
    val timestamp: Long,
)

/**
 * Joins consecutive parts from the same sender into one message, in arrival order.
 * The first part's timestamp stands for the message.
 */
fun joinParts(parts: List<SmsPart>): List<InboundSms> {
    val out = mutableListOf<InboundSms>()
    for (part in parts) {
        val last = out.lastOrNull()
        if (last != null && last.address == part.address) {
            out[out.lastIndex] = last.copy(body = last.body + part.body)
        } else {
            out += InboundSms(part.address, part.body, part.timestamp)
        }
    }
    return out
}
