package com.callguard.messaging

import com.callguard.core.PhoneNumber
import com.callguard.data.MessageLogEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Telephony.Sms message types, copied here so this model stays free of Android classes. */
object SmsType {
    const val INBOX = 1
    const val SENT = 2
    const val DRAFT = 3
    const val OUTBOX = 4
    const val FAILED = 5
    const val QUEUED = 6

    /** Types a conversation shows. Drafts stay out. */
    val CONVERSATION = setOf(INBOX, SENT, OUTBOX, FAILED, QUEUED)
}

/** One SMS provider row, reduced to the columns the screens use. */
data class SmsRow(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int,
    val read: Boolean,
)

/** Every text with one sender number, as the conversation list shows it. */
data class Conversation(
    val number: PhoneNumber,
    val snippet: String,
    val timestamp: Long,
    val unread: Int,
    val threadIds: Set<Long>,
)

/** Where a text in a thread stands: received, or an outgoing text that is pending, sent or failed. */
enum class DeliveryState { RECEIVED, PENDING, SENT, FAILED }

fun deliveryStateOf(type: Int): DeliveryState = when (type) {
    SmsType.INBOX -> DeliveryState.RECEIVED
    SmsType.FAILED -> DeliveryState.FAILED
    SmsType.OUTBOX, SmsType.QUEUED, SmsType.DRAFT -> DeliveryState.PENDING
    else -> DeliveryState.SENT
}

/** One text in a thread. */
data class ThreadMessage(val id: Long, val type: Int, val body: String, val timestamp: Long, val read: Boolean) {
    val state: DeliveryState get() = deliveryStateOf(type)
    val isUnreadIncoming: Boolean get() = type == SmsType.INBOX && !read
}

/** A line of a thread: a day heading before the first text of each local day, or a text. */
sealed interface ThreadRow {
    data class Day(val millis: Long) : ThreadRow
    data class Text(val message: ThreadMessage) : ThreadRow
}

/** Groups provider rows into one conversation per sender, newest conversation first. Rows group by normalized address. */
fun groupConversations(rows: List<SmsRow>): List<Conversation> =
    rows.groupBy { addressToNumber(it.address) }
        .map { (number, texts) ->
            val newest = texts.maxWith(compareBy<SmsRow>({ it.date }, { it.id }))
            Conversation(
                number = number,
                snippet = snippetOf(newest.body),
                timestamp = newest.date,
                unread = unreadCount(texts),
                threadIds = texts.mapTo(mutableSetOf()) { it.threadId },
            )
        }
        .sortedByDescending { it.timestamp }

/** Received texts not yet read. Sent and outgoing texts never count. */
fun unreadCount(rows: List<SmsRow>): Int = rows.count { it.type == SmsType.INBOX && !it.read }

private val WHITESPACE = Regex("\\s+")

/** One line for a list: whitespace collapsed, cut to [max] characters with an ellipsis. */
fun snippetOf(body: String, max: Int = 80): String {
    val flat = body.replace(WHITESPACE, " ").trim()
    return when {
        flat.isEmpty() -> "(no text)"
        flat.length <= max -> flat
        else -> flat.take(max - 1).trimEnd() + "…"
    }
}

/** The newest message-log entry per sender number. */
fun latestLogEntries(logs: List<MessageLogEntry>): Map<String, MessageLogEntry> =
    logs.groupBy { it.number }.mapValues { (_, entries) ->
        entries.maxWith(compareBy<MessageLogEntry>({ it.timestamp }, { it.id }))
    }

/**
 * A sender is quiet when the user blocked it, or when its newest held-back text was quiet or blocked.
 * An allowed sender always stays in the inbox.
 */
fun isQuietSender(number: String, latest: MessageOutcome?, allowList: Set<String>, blockList: Set<String>): Boolean = when {
    number in allowList -> false
    number in blockList -> true
    else -> latest == MessageOutcome.QUIET_INBOX || latest == MessageOutcome.BLOCKED
}

data class SplitConversations(val inbox: List<Conversation>, val quiet: List<Conversation>)

/**
 * Splits conversations between the inbox and the quiet inbox. A quiet sender with logged texts but no provider
 * thread (CallGuard was not the default app then) still appears, with its logged text.
 */
fun splitConversations(
    conversations: List<Conversation>,
    logs: List<MessageLogEntry>,
    allowList: Set<String>,
    blockList: Set<String>,
): SplitConversations {
    val latest = latestLogEntries(logs)
    fun quiet(number: String) = isQuietSender(number, latest[number]?.outcome, allowList, blockList)

    val (quietThreads, inbox) = conversations.partition { quiet(it.number.e164) }
    val threaded = conversations.mapTo(mutableSetOf()) { it.number.e164 }
    val logOnly = latest.values
        .filter { it.number !in threaded && quiet(it.number) }
        .map { entry ->
            Conversation(
                number = PhoneNumber(entry.number),
                snippet = snippetOf(entry.body),
                timestamp = entry.timestamp,
                unread = 0,
                threadIds = emptySet(),
            )
        }
    return SplitConversations(inbox = inbox, quiet = (quietThreads + logOnly).sortedByDescending { it.timestamp })
}

/** Interleaves day headings into a thread sorted oldest first. */
fun threadRows(messages: List<ThreadMessage>, zone: ZoneId): List<ThreadRow> {
    val rows = ArrayList<ThreadRow>(messages.size * 2)
    var day: LocalDate? = null
    for (message in messages) {
        val date = Instant.ofEpochMilli(message.timestamp).atZone(zone).toLocalDate()
        if (date != day) {
            rows += ThreadRow.Day(message.timestamp)
            day = date
        }
        rows += ThreadRow.Text(message)
    }
    return rows
}

/** True when [query] is blank, or appears in one of [fields] ignoring case. */
fun matchesQuery(query: String, vararg fields: String?): Boolean {
    val q = query.trim()
    return q.isEmpty() || fields.any { it != null && it.contains(q, ignoreCase = true) }
}

/**
 * The number typed into a To field, or null until it has at least three digits and holds only digits and
 * phone punctuation. Short numbers such as 5554002 are kept as typed.
 */
fun directAddress(input: String): PhoneNumber? {
    val s = input.trim()
    if (s.count(Char::isDigit) < 3) return null
    if (s.any { !(it.isDigit() || it == '+' || it == ' ' || it == '-' || it == '(' || it == ')') }) return null
    return addressToNumber(s)
}

/** A draft measured in SMS parts. GSM 03.38 packs 160 characters into one part, UCS-2 packs 70. */
data class SmsCount(val units: Int, val parts: Int, val remaining: Int, val unicode: Boolean)

fun smsCount(text: String): SmsCount {
    val unicode = text.any { it !in GSM_BASIC && it !in GSM_EXTENSION }
    val units = if (unicode) text.length else text.sumOf { if (it in GSM_EXTENSION) 2 else 1 }
    val single = if (unicode) 70 else 160
    val multi = if (unicode) 67 else 153
    val parts = if (units <= single) 1 else (units + multi - 1) / multi
    val capacity = if (parts == 1) single else parts * multi
    return SmsCount(units = units, parts = parts, remaining = capacity - units, unicode = unicode)
}

/** The GSM 03.38 basic character set. */
private const val GSM_BASIC =
    "@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"

/** GSM characters that take two units: they are sent with an escape code. */
private const val GSM_EXTENSION = "^{}\\[~]|€"
