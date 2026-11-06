package com.callguard.spam.classify

import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizerTest {
    @Test
    fun foldsLeetspeakInsideWords() {
        assertEquals("free loan", TextNormalizer.normalize("fr33 l0an").folded)
    }

    @Test
    fun leavesDigitOnlyTokensAlone() {
        assertEquals("pay 1500 now", TextNormalizer.normalize("pay 1500 now").folded)
    }

    @Test
    fun collapsesLongLetterRunsToTwo() {
        assertEquals("free", TextNormalizer.normalize("freeeeee").folded)
    }

    @Test
    fun keepsDigitsInFoldedTextAndMasksThemInMaskedText() {
        val sms = TextNormalizer.normalize("Your OTP is 482913.")
        assertEquals("your otp is 482913.", sms.folded)
        assertEquals("your otp is <num>.", sms.masked)
    }

    @Test
    fun masksUrlsAndAmountsAndKeepsUrlHosts() {
        val sms = TextNormalizer.normalize("Pay Rs 500 at bit.ly/ab12 now")
        assertEquals("pay <amt> at <url> now", sms.masked)
        assertEquals(listOf("bit.ly"), sms.urlHosts)
    }

    @Test
    fun foldsCompatibilityCharactersBeforeMatching() {
        assertEquals("otp", TextNormalizer.normalize("ＯＴＰ").folded)
    }
}
