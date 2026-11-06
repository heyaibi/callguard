package com.callguard.screening

import com.callguard.data.Direction
import com.callguard.prefs.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CallAnswerTest {

    private val number = "+919876500001"
    private val open = Settings(contactsOnly = false)
    private val blockListed = Settings(contactsOnly = false, blockList = setOf(number))

    @Test
    fun deadlineFallbackAllowsANumberThatIsNotBlocked() {
        assertEquals(ScreeningDecision.ALLOW, answerFor(Direction.INCOMING, number, open, screened = null))
    }

    @Test
    fun deadlineFallbackBlocksABlockListedNumber() {
        assertEquals(ScreeningDecision.BLOCK, answerFor(Direction.INCOMING, number, blockListed, screened = null))
    }

    @Test
    fun deadlineFallbackUsesOnlyTheBlockListSoContactsOnlyAndSpamDoNotApply() {
        val settings = Settings(contactsOnly = true, blockSpam = true)
        assertEquals(ScreeningDecision.ALLOW, answerFor(Direction.INCOMING, number, settings, screened = null))
    }

    @Test
    fun deadlineFallbackAllowsWhenThereIsNoUsableNumber() {
        assertEquals(ScreeningDecision.ALLOW, answerFor(Direction.INCOMING, null, blockListed, screened = null))
    }

    @Test
    fun outgoingCallIsAlwaysAllowedEvenWhenBlockListedOrScreenedAsBlock() {
        assertEquals(ScreeningDecision.ALLOW, answerFor(Direction.OUTGOING, number, blockListed, screened = ScreeningDecision.BLOCK))
        assertEquals(ScreeningDecision.ALLOW, answerFor(Direction.OUTGOING, number, blockListed, screened = null))
    }

    @Test
    fun fullScreeningResultIsUsedWhenItArrivesInTime() {
        assertEquals(ScreeningDecision.SILENCE, answerFor(Direction.INCOMING, number, open, screened = ScreeningDecision.SILENCE))
    }

    @Test
    fun screeningResultWinsWhenItFinishesBeforeTheDeadline() {
        val sent = mutableListOf<ScreeningDecision>()
        val answer = CallAnswer { sent += it }
        answer.offer(answerFor(Direction.INCOMING, number, open, screened = ScreeningDecision.SILENCE))
        answer.offer(answerFor(Direction.INCOMING, number, open, screened = null))
        assertEquals(listOf(ScreeningDecision.SILENCE), sent)
    }

    @Test
    fun lateScreeningResultIsDroppedWhenTheDeadlineAnswerWentOutFirst() {
        val sent = CopyOnWriteArrayList<ScreeningDecision>()
        val answer = CallAnswer { sent += it }
        val fallbackSent = CountDownLatch(1)
        val timer = Executors.newSingleThreadScheduledExecutor()
        val worker = Executors.newSingleThreadExecutor()
        try {
            timer.schedule(
                Runnable {
                    answer.offer(answerFor(Direction.INCOMING, number, open, screened = null))
                    fallbackSent.countDown()
                },
                10,
                TimeUnit.MILLISECONDS,
            )
            worker.execute {
                // Screening is slow: its result only arrives after the deadline answer went out.
                fallbackSent.await(5, TimeUnit.SECONDS)
                answer.offer(ScreeningDecision.BLOCK)
            }
            worker.shutdown()
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS))
        } finally {
            timer.shutdownNow()
            worker.shutdownNow()
        }
        assertEquals(listOf(ScreeningDecision.ALLOW), sent)
    }

    @Test
    fun blockRejectsTheCallAndHidesItsNotificationButKeepsTheLogEntry() {
        assertEquals(
            CallResponseSpec(disallowCall = true, rejectCall = true, skipCallLog = false, skipNotification = true),
            ScreeningDecision.BLOCK.responseSpec(),
        )
    }

    @Test
    fun silenceOnlyMutesTheCall() {
        assertEquals(CallResponseSpec(silenceCall = true), ScreeningDecision.SILENCE.responseSpec())
    }

    @Test
    fun allowKeepsThePlatformDefaults() {
        assertEquals(CallResponseSpec(), ScreeningDecision.ALLOW.responseSpec())
    }

    @Test
    fun logOutcomeUsesTheStoredWords() {
        assertEquals("ALLOWED", ScreeningDecision.ALLOW.logOutcome())
        assertEquals("SILENCED", ScreeningDecision.SILENCE.logOutcome())
        assertEquals("BLOCKED", ScreeningDecision.BLOCK.logOutcome())
    }

    @Test
    fun cnapNameIsTrustedOnlyWhenThePresentationIsAllowed() {
        // 1 is TelecomManager.PRESENTATION_ALLOWED; 2 is PRESENTATION_RESTRICTED.
        assertEquals("RAVI KUMAR", trustedCnapName(" RAVI KUMAR ", presentation = 1, allowedPresentation = 1))
        assertNull(trustedCnapName("RAVI KUMAR", presentation = 2, allowedPresentation = 1))
        assertNull(trustedCnapName("   ", presentation = 1, allowedPresentation = 1))
        assertNull(trustedCnapName(null, presentation = 1, allowedPresentation = 1))
    }
}
