package com.callguard.messaging

import com.callguard.core.PhoneNumber
import com.callguard.data.MessageLogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ConversationModelTest {
    private fun row(
        id: Long,
        address: String,
        body: String,
        date: Long,
        type: Int = SmsType.INBOX,
        read: Boolean = true,
        thread: Long = 1,
    ) = SmsRow(id = id, threadId = thread, address = address, body = body, date = date, type = type, read = read)

    private fun log(
        id: Long,
        number: String,
        outcome: MessageOutcome,
        ts: Long,
        body: String = "body",
    ) = MessageLogEntry(id = id, number = number, name = null, body = body, outcome = outcome, timestamp = ts)

    @Test
    fun groupsRowsByNormalizedNumberAndSortsNewestFirst() {
        val conversations =
            groupConversations(
                listOf(
                    row(1, "9876543210", "hi", 100, read = false, thread = 10),
                    row(2, "+919876543210", "there", 200, type = SmsType.SENT, thread = 10),
                    row(3, "5554002", "other", 150, thread = 11),
                ),
            )
        assertEquals(listOf(PhoneNumber("+919876543210"), PhoneNumber("5554002")), conversations.map { it.number })
        val first = conversations.first()
        assertEquals("there", first.snippet)
        assertEquals(200L, first.timestamp)
        assertEquals(1, first.unread)
        assertEquals(setOf(10L), first.threadIds)
    }

    @Test
    fun unreadCountsOnlyUnreadReceivedTexts() {
        val rows =
            listOf(
                row(1, "A", "one", 1, read = false),
                row(2, "A", "two", 2, read = true),
                row(3, "A", "three", 3, type = SmsType.SENT, read = false),
                row(4, "A", "four", 4, type = SmsType.FAILED, read = false),
            )
        assertEquals(1, unreadCount(rows))
        assertEquals(1, groupConversations(rows).single().unread)
    }

    @Test
    fun snippetCollapsesWhitespaceAndTruncates() {
        assertEquals("hello world again", snippetOf("hello   world\n\nagain"))
        val long = snippetOf("a".repeat(100), max = 80)
        assertEquals(80, long.length)
        assertTrue(long.endsWith("…"))
    }

    @Test
    fun snippetForEmptyBodySaysSo() {
        assertEquals("(no text)", snippetOf("   "))
    }

    @Test
    fun smsCountOfShortGsmTextIsOnePart() {
        val count = smsCount("hello")
        assertEquals(5, count.units)
        assertEquals(1, count.parts)
        assertEquals(155, count.remaining)
        assertFalse(count.unicode)
    }

    @Test
    fun smsCountSplitsLongGsmTextIntoMultipartSegments() {
        assertEquals(1, smsCount("a".repeat(160)).parts)
        assertEquals(0, smsCount("a".repeat(160)).remaining)
        val two = smsCount("a".repeat(161))
        assertEquals(2, two.parts)
        assertEquals(145, two.remaining)
    }

    @Test
    fun smsCountChargesExtensionCharactersTwice() {
        val euro = smsCount("€")
        assertEquals(2, euro.units)
        assertEquals(158, euro.remaining)
        assertFalse(euro.unicode)
    }

    @Test
    fun smsCountUsesUcs2LimitsForNonGsmText() {
        val short = smsCount("नमस्ते")
        assertTrue(short.unicode)
        assertEquals(1, short.parts)
        assertEquals(64, short.remaining)
        val long = smsCount("नमस्ते".repeat(12))
        assertEquals(2, long.parts)
        assertEquals(62, long.remaining)
    }

    @Test
    fun deliveryStateFollowsProviderType() {
        assertEquals(DeliveryState.RECEIVED, deliveryStateOf(SmsType.INBOX))
        assertEquals(DeliveryState.SENT, deliveryStateOf(SmsType.SENT))
        assertEquals(DeliveryState.PENDING, deliveryStateOf(SmsType.OUTBOX))
        assertEquals(DeliveryState.PENDING, deliveryStateOf(SmsType.QUEUED))
        assertEquals(DeliveryState.FAILED, deliveryStateOf(SmsType.FAILED))
    }

    @Test
    fun threadRowsAddOneDayHeadingPerLocalDay() {
        val utc = ZoneId.of("UTC")
        val day1 = Instant.parse("2026-11-05T23:00:00Z").toEpochMilli()
        val day2a = Instant.parse("2026-11-06T01:00:00Z").toEpochMilli()
        val day2b = Instant.parse("2026-11-06T02:00:00Z").toEpochMilli()
        val messages =
            listOf(
                ThreadMessage(1, SmsType.INBOX, "a", day1, true),
                ThreadMessage(2, SmsType.INBOX, "b", day2a, true),
                ThreadMessage(3, SmsType.SENT, "c", day2b, true),
            )
        val rows = threadRows(messages, utc)
        assertEquals(5, rows.size)
        assertTrue(rows[0] is ThreadRow.Day)
        assertTrue(rows[1] is ThreadRow.Text)
        assertTrue(rows[2] is ThreadRow.Day)
        assertEquals(day2a, (rows[2] as ThreadRow.Day).millis)
        assertEquals(3, (rows[4] as ThreadRow.Text).message.id)
    }

    @Test
    fun quietRulesFollowTheListsAndTheLatestOutcome() {
        assertTrue(isQuietSender("+919000000003", MessageOutcome.NOTIFY, emptySet(), setOf("+919000000003")))
        assertFalse(isQuietSender("+919000000001", MessageOutcome.NOTIFY, emptySet(), emptySet()))
        assertTrue(isQuietSender("+919000000001", MessageOutcome.QUIET_INBOX, emptySet(), emptySet()))
        assertFalse(isQuietSender("+919000000002", MessageOutcome.BLOCKED, setOf("+919000000002"), emptySet()))
    }

    @Test
    fun splitRoutesConversationsByListsAndLog() {
        val conversations =
            listOf(
                Conversation(PhoneNumber("+919000000001"), "a", 300, 0, emptySet()),
                Conversation(PhoneNumber("+919000000002"), "b", 200, 2, emptySet()),
                Conversation(PhoneNumber("+919000000003"), "c", 100, 0, emptySet()),
            )
        val logs =
            listOf(
                log(1, "+919000000001", MessageOutcome.NOTIFY, 250),
                log(2, "+919000000002", MessageOutcome.QUIET_INBOX, 200),
                log(3, "+919000000004", MessageOutcome.QUIET_INBOX, 50, body = "held back"),
            )
        val split =
            splitConversations(
                conversations = conversations,
                logs = logs,
                allowList = setOf("+919000000002"),
                blockList = setOf("+919000000003"),
            )
        assertEquals(listOf("+919000000001", "+919000000002"), split.inbox.map { it.number.e164 })
        assertEquals(listOf("+919000000003", "+919000000004"), split.quiet.map { it.number.e164 })
        val logOnly = split.quiet.last()
        assertEquals("held back", logOnly.snippet)
        assertEquals(0, logOnly.unread)
    }

    @Test
    fun directAddressNeedsThreeDigitsAndNoLetters() {
        assertEquals(PhoneNumber("5554002"), directAddress("5554002"))
        assertEquals(PhoneNumber("+919876543210"), directAddress("+91 98765 43210"))
        assertNull(directAddress("12"))
        assertNull(directAddress("ab"))
        assertNull(directAddress("98765x3210"))
    }

    @Test
    fun matchesQueryIgnoresCaseAndBlankQueries() {
        assertTrue(matchesQuery("", "Alice"))
        assertTrue(matchesQuery("   ", null))
        assertTrue(matchesQuery("ali", "Alice"))
        assertTrue(matchesQuery("98", null, "+919876543210"))
        assertFalse(matchesQuery("zzz", "Alice", "+919876543210"))
    }

    @Test
    fun allowListBeatsASpamMark() {
        assertFalse(isQuietSender("+919000000001", null, setOf("+919000000001"), emptySet(), userMark = true))
    }

    @Test
    fun blockListBeatsANotSpamMark() {
        assertTrue(isQuietSender("+919000000002", null, emptySet(), setOf("+919000000002"), userMark = false))
    }

    @Test
    fun aSpamMarkMovesAnInboxSenderToTheQuietInbox() {
        val conversations = listOf(Conversation(PhoneNumber("+919000000003"), "Flat 50% off today", 100, 0, setOf(1L)))

        val split = splitConversations(conversations, emptyList(), emptySet(), emptySet(), userMark = { true })

        assertEquals(emptyList<String>(), split.inbox.map { it.number.e164 })
        assertEquals(listOf("+919000000003"), split.quiet.map { it.number.e164 })
    }

    @Test
    fun aNotSpamMarkMovesAQuietLogSenderToTheInbox() {
        val conversation = Conversation(PhoneNumber("+919000000004"), "Your OTP is 123456", 100, 0, setOf(2L))
        val logs = listOf(log(1, "+919000000004", MessageOutcome.QUIET_INBOX, 90))

        val split = splitConversations(listOf(conversation), logs, emptySet(), emptySet(), userMark = { false })

        assertEquals(listOf("+919000000004"), split.inbox.map { it.number.e164 })
        assertEquals(emptyList<String>(), split.quiet.map { it.number.e164 })
    }

    @Test
    fun aSpamMarkAppliesToALogOnlySender() {
        val logs = listOf(log(1, "+919000000005", MessageOutcome.NOTIFY, 90, body = "Flat 50% off today"))

        val unmarked = splitConversations(emptyList(), logs, emptySet(), emptySet())
        val marked = splitConversations(emptyList(), logs, emptySet(), emptySet(), userMark = { true })

        assertEquals(emptyList<String>(), unmarked.quiet.map { it.number.e164 })
        assertEquals(listOf("+919000000005"), marked.quiet.map { it.number.e164 })
        assertEquals("Flat 50% off today", marked.quiet.single().snippet)
    }

    @Test
    fun aSpamScoreMovesAnUnmarkedSenderToTheSpamTab() {
        val conversations = listOf(Conversation(PhoneNumber("+919000000006"), "You won a lottery", 100, 1, setOf(3L)))

        val split = splitConversations(conversations, emptyList(), emptySet(), emptySet(), spamScored = { true })

        assertEquals(listOf("+919000000006"), split.quiet.map { it.number.e164 })
    }

    @Test
    fun allowListAndANotSpamMarkBeatASpamScore() {
        val number = "+919000000007"

        assertFalse(isQuietSender(number, null, setOf(number), emptySet(), spamScored = true))
        assertFalse(isQuietSender(number, null, emptySet(), emptySet(), userMark = false, spamScored = true))
        assertTrue(isQuietSender(number, null, emptySet(), emptySet(), spamScored = true))
    }
}
