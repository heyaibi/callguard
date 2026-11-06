package com.callguard.screening

import com.callguard.core.PhoneNumber
import com.callguard.identity.Confidence
import com.callguard.identity.ResolutionResult
import com.callguard.identity.SourceKind
import com.callguard.prefs.Settings
import com.callguard.prefs.UnknownCallerAction
import com.callguard.spam.SpamAssessment
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreeningPolicyTest {

    private val n = PhoneNumber("+919876543210")
    private val e164 = n.e164
    private val policy = ScreeningPolicy()
    private val known = ResolutionResult(n, "Ravi", SourceKind.LOCAL_CONTACT, Confidence.HIGH)
    private val cnap = ResolutionResult(n, "RAVI KUMAR", SourceKind.CNAP, Confidence.HIGH)
    private val unknown = ResolutionResult(n, null, SourceKind.NONE, Confidence.NONE)

    /** Contacts only is off in the base so each test isolates one rule; contacts-only tests turn it on. */
    private val open = Settings(contactsOnly = false)

    private fun assessment(isSpam: Boolean) = SpamAssessment(n, if (isSpam) 90 else 0, isSpam, "test")

    private fun decide(
        identity: ResolutionResult = unknown,
        spam: Boolean = false,
        settings: Settings = open,
        inContacts: Boolean = false,
    ): ScreeningDecision = policy.decide(identity, assessment(spam), settings, inContacts)

    @Test
    fun defaultsAllowAnUnknownCaller() {
        assertEquals(ScreeningDecision.ALLOW, decide())
    }

    @Test
    fun blockListBlocksEvenKnownContacts() {
        val d = decide(identity = known, inContacts = true, settings = open.copy(blockList = setOf(e164)))
        assertEquals(ScreeningDecision.BLOCK, d)
    }

    @Test
    fun blockListIsCheckedBeforeAllowList() {
        val d = decide(settings = open.copy(allowList = setOf(e164), blockList = setOf(e164)))
        assertEquals(ScreeningDecision.BLOCK, d)
    }

    @Test
    fun allowListRingsEvenForSpam() {
        val d = decide(spam = true, settings = open.copy(allowList = setOf(e164), blockSpam = true))
        assertEquals(ScreeningDecision.ALLOW, d)
    }

    @Test
    fun allowListRingsUnknownCallerInSilenceMode() {
        val d = decide(settings = open.copy(allowList = setOf(e164), silenceMode = true, unknownCallerAction = UnknownCallerAction.BLOCK))
        assertEquals(ScreeningDecision.ALLOW, d)
    }

    @Test
    fun spamIsBlockedWhenBlockSpamIsOn() {
        assertEquals(ScreeningDecision.BLOCK, decide(spam = true, settings = open.copy(blockSpam = true)))
    }

    @Test
    fun spamIsOnlySilencedWhenBlockSpamIsOff() {
        assertEquals(ScreeningDecision.SILENCE, decide(spam = true))
    }

    @Test
    fun spamIsSilencedEvenForKnownContacts() {
        assertEquals(ScreeningDecision.SILENCE, decide(identity = known, spam = true, inContacts = true))
    }

    @Test
    fun blockSpamDoesNotAffectNonSpam() {
        assertEquals(ScreeningDecision.ALLOW, decide(settings = open.copy(blockSpam = true)))
    }

    @Test
    fun spamOutranksUnknownCallerBlockAction() {
        val d = decide(spam = true, settings = open.copy(silenceMode = true, unknownCallerAction = UnknownCallerAction.BLOCK))
        assertEquals(ScreeningDecision.SILENCE, d)
    }

    @Test
    fun silenceModeSilencesUnknownCallerByDefault() {
        assertEquals(ScreeningDecision.SILENCE, decide(settings = open.copy(silenceMode = true)))
    }

    @Test
    fun silenceModeBlocksUnknownCallerWhenConfiguredToBlock() {
        val d = decide(settings = open.copy(silenceMode = true, unknownCallerAction = UnknownCallerAction.BLOCK))
        assertEquals(ScreeningDecision.BLOCK, d)
    }

    @Test
    fun unknownCallerIsAllowedWhenSilenceModeIsOff() {
        val d = decide(settings = open.copy(silenceMode = false, unknownCallerAction = UnknownCallerAction.BLOCK))
        assertEquals(ScreeningDecision.ALLOW, d)
    }

    @Test
    fun silenceModeKeepsCallersWithAKnownName() {
        assertEquals(ScreeningDecision.ALLOW, decide(identity = known, settings = open.copy(silenceMode = true)))
    }

    @Test
    fun silenceModeKeepsPhonebookNumbers() {
        assertEquals(ScreeningDecision.ALLOW, decide(inContacts = true, settings = open.copy(silenceMode = true)))
    }

    @Test
    fun contactsOnlyIsOnByDefaultAndBlocksUnknownCallers() {
        assertEquals(ScreeningDecision.BLOCK, decide(settings = Settings()))
    }

    @Test
    fun contactsOnlyBlocksNonContactEvenWithCnapName() {
        val d = decide(identity = cnap, settings = open.copy(contactsOnly = true))
        assertEquals(ScreeningDecision.BLOCK, d)
    }

    @Test
    fun contactsOnlyKeepsAllowListedNonContact() {
        val d = decide(identity = cnap, settings = open.copy(contactsOnly = true, allowList = setOf(e164)))
        assertEquals(ScreeningDecision.ALLOW, d)
    }

    @Test
    fun contactsOnlyLeavesContactsUnaffected() {
        val d = decide(identity = known, inContacts = true, settings = open.copy(contactsOnly = true))
        assertEquals(ScreeningDecision.ALLOW, d)
    }
}
