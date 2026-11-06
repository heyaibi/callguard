package com.callguard.screening

import com.callguard.core.PhoneNumber
import com.callguard.identity.Confidence
import com.callguard.identity.ResolutionResult
import com.callguard.identity.SourceKind
import com.callguard.spam.SpamAssessment
import org.junit.Assert.assertEquals
import org.junit.Test

class CallerIdCardTest {

    private val n = PhoneNumber("+919876543210")
    private val clean = SpamAssessment(n, 0, false, "no spam signals")

    @Test
    fun knownCallerShowsNameAndNumberSourceAndConfidence() {
        val identity = ResolutionResult(n, "RAVI KUMAR", SourceKind.CNAP, Confidence.HIGH)
        val card = callerCardText(identity, clean)
        assertEquals("RAVI KUMAR", card.title)
        assertEquals("+91 98765 43210 · CNAP · HIGH", card.text)
    }

    @Test
    fun unknownCallerIsTitledUnknownAndHasNoConfidence() {
        val identity = ResolutionResult(n, null, SourceKind.NONE, Confidence.NONE)
        val card = callerCardText(identity, clean)
        assertEquals("UNKNOWN", card.title)
        assertEquals("+91 98765 43210 · NO MATCH", card.text)
    }

    @Test
    fun likelySpamAppendsTheScore() {
        val identity = ResolutionResult(n, null, SourceKind.NONE, Confidence.NONE)
        val card = callerCardText(identity, SpamAssessment(n, 90, true, "bundled spam list"))
        assertEquals("+91 98765 43210 · NO MATCH · LIKELY SPAM · 90", card.text)
    }
}
