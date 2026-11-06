package com.callguard.screening

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreeningVerdictsTest {

    @Test
    fun keyIsTheE164NumberWhenItNormalizes() {
        assertEquals("+919876543210", ScreeningVerdicts.keyFor("98765 43210"))
    }

    @Test
    fun keyIsTheTrimmedTextWhenItDoesNotNormalize() {
        assertEquals("5554002", ScreeningVerdicts.keyFor(" 5554002 "))
    }

    @Test
    fun hiddenNumbersShareThePrivateKey() {
        assertEquals(ScreeningVerdicts.PRIVATE_KEY, ScreeningVerdicts.keyFor(null))
        assertEquals(ScreeningVerdicts.PRIVATE_KEY, ScreeningVerdicts.keyFor("   "))
    }

    @Test
    fun recordedDecisionIsReturnedWithinTheTtl() {
        val key = "+919100000001"
        ScreeningVerdicts.record(key, ScreeningDecision.SILENCE, now = 1_000L)
        assertEquals(ScreeningDecision.SILENCE, ScreeningVerdicts.peek(key, now = 1_000L + 60_000L))
    }

    @Test
    fun decisionStillHoldsForALongCallAndExpiresAfterTheTtl() {
        val key = "+919100000002"
        ScreeningVerdicts.record(key, ScreeningDecision.ALLOW, now = 0L)
        assertEquals(ScreeningDecision.ALLOW, ScreeningVerdicts.peek(key, now = 10 * 60_000L))
        assertNull(ScreeningVerdicts.peek(key, now = 60 * 60_000L + 1L))
    }

    @Test
    fun forgetRemovesTheDecision() {
        val key = "+919100000003"
        ScreeningVerdicts.record(key, ScreeningDecision.BLOCK, now = 0L)
        ScreeningVerdicts.forget(key)
        assertNull(ScreeningVerdicts.peek(key, now = 0L))
    }

    @Test
    fun unknownKeyHasNoDecision() {
        assertNull(ScreeningVerdicts.peek("+919100000004", now = 0L))
    }
}
