package com.callguard.messaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageDeliveryTest {

    @Test
    fun notifyOutcomeSounds() {
        assertEquals(AlertMode.SOUND, alertModeFor(MessageOutcome.NOTIFY))
    }

    @Test
    fun silencedOutcomePostsSilently() {
        assertEquals(AlertMode.SILENT, alertModeFor(MessageOutcome.SILENCED))
    }

    @Test
    fun quietAndBlockedOutcomesPostNothing() {
        assertEquals(AlertMode.NONE, alertModeFor(MessageOutcome.QUIET_INBOX))
        assertEquals(AlertMode.NONE, alertModeFor(MessageOutcome.BLOCKED))
    }

    @Test
    fun onlyQuietAndBlockedArriveRead() {
        assertFalse(isReadOnArrival(MessageOutcome.NOTIFY))
        assertFalse(isReadOnArrival(MessageOutcome.SILENCED))
        assertTrue(isReadOnArrival(MessageOutcome.QUIET_INBOX))
        assertTrue(isReadOnArrival(MessageOutcome.BLOCKED))
    }

    @Test
    fun joinsConsecutivePartsFromOneSender() {
        val joined = joinParts(
            listOf(
                SmsPart("+919876543210", "Your OTP ", 100L),
                SmsPart("+919876543210", "is 1234", 101L),
            ),
        )
        assertEquals(listOf(InboundSms("+919876543210", "Your OTP is 1234", 100L)), joined)
    }

    @Test
    fun keepsSeparateSendersApart() {
        val joined = joinParts(listOf(SmsPart("A", "one", 1L), SmsPart("B", "two", 2L)))
        assertEquals(listOf(InboundSms("A", "one", 1L), InboundSms("B", "two", 2L)), joined)
    }

    @Test
    fun doesNotMergeNonConsecutiveRunsFromOneSender() {
        val joined = joinParts(
            listOf(SmsPart("A", "first", 1L), SmsPart("B", "middle", 2L), SmsPart("A", "second", 3L)),
        )
        assertEquals(3, joined.size)
    }

    @Test
    fun emptyInputGivesNoMessages() {
        assertEquals(emptyList<InboundSms>(), joinParts(emptyList()))
    }
}
