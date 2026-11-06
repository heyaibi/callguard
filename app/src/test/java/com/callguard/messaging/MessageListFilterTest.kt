package com.callguard.messaging

import com.callguard.spam.classify.SmsKind
import com.callguard.spam.classify.SmsVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageListFilterTest {
    private fun row(
        id: Long,
        address: String,
        body: String,
        date: Long,
        type: Int = SmsType.INBOX,
    ) = SmsRow(id = id, threadId = 1, address = address, body = body, date = date, type = type, read = true)

    private fun verdict(
        score: Double,
        kind: SmsKind = SmsKind.PERSONAL,
    ) = SmsVerdict(spamScore = score, kind = kind, signals = emptyList())

    @Test
    fun latestIncomingIgnoresSentRows() {
        val rows =
            listOf(
                row(1, "+919876501234", "Your OTP is 123456", 100),
                row(2, "+919876501234", "Sent reply", 200, type = SmsType.SENT),
            )

        val latest = latestIncoming(rows)

        assertEquals(IncomingText("+919876501234", "Your OTP is 123456"), latest["+919876501234"])
    }

    @Test
    fun latestIncomingPicksTheNewestByDateThenId() {
        val rows =
            listOf(
                row(9, "+919876501234", "Old text", 100),
                row(1, "+919876501234", "Newer date, lower id", 200),
                row(4, "+919876501234", "Same date, higher id", 200),
            )

        val latest = latestIncoming(rows)

        assertEquals("Same date, higher id", latest.getValue("+919876501234").body)
    }

    @Test
    fun latestIncomingGroupsTwoSpellingsOfOneNumber() {
        val rows =
            listOf(
                row(1, "9876501234", "first", 100),
                row(2, "+91 98765 01234", "second", 200),
            )

        val latest = latestIncoming(rows)

        assertEquals(setOf("+919876501234"), latest.keys)
        assertEquals(IncomingText("+91 98765 01234", "second"), latest.getValue("+919876501234"))
    }

    @Test
    fun inactiveFilterShowsAConversationWithNoVerdict() {
        assertFalse(ListFilter().active)
        assertTrue(ListFilter().matches(null))
    }

    @Test
    fun activeFilterHidesAConversationWithNoVerdict() {
        assertFalse(ListFilter(score = ScoreFilter.SPAM).matches(null))
        assertFalse(ListFilter(kinds = setOf(SmsKind.OTP)).matches(null))
    }

    @Test
    fun kindSetShowsOnlyThePickedKinds() {
        val filter = ListFilter(kinds = setOf(SmsKind.OTP, SmsKind.FINANCE))

        assertTrue(filter.matches(verdict(0.1, SmsKind.OTP)))
        assertTrue(filter.matches(verdict(0.1, SmsKind.FINANCE)))
        assertFalse(filter.matches(verdict(0.1, SmsKind.PROMOTIONAL)))
    }

    @Test
    fun spamScoreAtTheThresholdCountsAsSpam() {
        val filter = ListFilter(score = ScoreFilter.SPAM)

        assertTrue(filter.matches(verdict(0.6)))
        assertFalse(filter.matches(verdict(0.59)))
    }

    @Test
    fun notSpamScoreIsBelowTheThreshold() {
        val filter = ListFilter(score = ScoreFilter.NOT_SPAM)

        assertTrue(filter.matches(verdict(0.59)))
        assertFalse(filter.matches(verdict(0.6)))
    }

    @Test
    fun kindAndScoreMustBothMatch() {
        val filter = ListFilter(kinds = setOf(SmsKind.PROMOTIONAL), score = ScoreFilter.SPAM)

        assertTrue(filter.matches(verdict(0.9, SmsKind.PROMOTIONAL)))
        assertFalse(filter.matches(verdict(0.9, SmsKind.OTP)))
        assertFalse(filter.matches(verdict(0.2, SmsKind.PROMOTIONAL)))
    }

    @Test
    fun toggleKindAddsAPickedKindAndRemovesItAgain() {
        val picked = ListFilter().toggleKind(SmsKind.OTP)

        assertEquals(setOf(SmsKind.OTP), picked.kinds)
        assertEquals(emptySet<SmsKind>(), picked.toggleKind(SmsKind.OTP).kinds)
    }

    @Test
    fun kindLabelNamesEveryKindInShortCaps() {
        val labels = SmsKind.entries.associateWith { kindLabel(it) }

        assertEquals(
            mapOf(
                SmsKind.OTP to "OTP",
                SmsKind.FINANCE to "BANKS",
                SmsKind.GOVERNMENT to "GOVERNMENT",
                SmsKind.TRANSACTIONAL to "SERVICE",
                SmsKind.DELIVERY to "DELIVERY",
                SmsKind.PERSONAL to "PERSONAL",
                SmsKind.PROMOTIONAL to "PROMOTIONS",
                SmsKind.SCAM to "SCAM",
            ),
            labels,
        )
    }

    @Test
    fun captionShowsTheKindAndTheRoundedScore() {
        assertEquals("PROMOTIONS · 72", verdictCaption(verdict(0.72, SmsKind.PROMOTIONAL)))
        assertEquals("OTP · 60", verdictCaption(verdict(0.6, SmsKind.OTP)))
    }
}
