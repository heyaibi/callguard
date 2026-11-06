package com.callguard.messaging

import com.callguard.core.PhoneNumber
import com.callguard.identity.Confidence
import com.callguard.identity.ResolutionResult
import com.callguard.identity.SourceKind
import com.callguard.prefs.SenderRing
import com.callguard.prefs.Settings
import com.callguard.spam.SpamAssessment
import com.callguard.spam.classify.SmsKind
import com.callguard.spam.classify.SmsVerdict
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageFilterPolicyTest {
    private val n = PhoneNumber("+919876543210")
    private val e164 = n.e164
    private val policy = MessageFilterPolicy()
    private val known = ResolutionResult(n, "Ravi", SourceKind.LOCAL_CONTACT, Confidence.HIGH)
    private val cnap = ResolutionResult(n, "RAVI KUMAR", SourceKind.CNAP, Confidence.HIGH)
    private val unknown = ResolutionResult(n, null, SourceKind.NONE, Confidence.NONE)
    private val dltBank = ResolutionResult(PhoneNumber("VM-HDFCBK-S"), null, SourceKind.NONE, Confidence.NONE)
    private val otherDltBank = ResolutionResult(PhoneNumber("AX-HDFCBK"), null, SourceKind.NONE, Confidence.NONE)

    /** Contacts only is off in the base so each test isolates one rule; contacts-only tests turn it on. */
    private val open = Settings(contactsOnly = false)

    /** A promotional text below the spam threshold. Promotions do not ring by default. */
    private val promo = SmsVerdict(0.1, SmsKind.PROMOTIONAL, emptyList())

    private fun assessment(isSpam: Boolean) = SpamAssessment(n, if (isSpam) 90 else 0, isSpam, "test")

    private fun text(
        kind: SmsKind,
        score: Double,
    ) = SmsVerdict(score, kind, emptyList())

    private fun decide(
        identity: ResolutionResult = unknown,
        spam: Boolean = false,
        verdict: SmsVerdict = promo,
        settings: Settings = open,
        inContacts: Boolean = false,
    ): MessageOutcome = policy.decide(identity, assessment(spam), verdict, settings, inContacts)

    @Test
    fun defaultsNotify() {
        assertEquals(MessageOutcome.NOTIFY, decide())
    }

    @Test
    fun blockListMarksBlockedEvenForContacts() {
        val d = decide(identity = known, inContacts = true, settings = open.copy(blockList = setOf(e164)))
        assertEquals(MessageOutcome.BLOCKED, d)
    }

    @Test
    fun blockListIsCheckedBeforeAllowList() {
        val d = decide(settings = open.copy(allowList = setOf(e164), blockList = setOf(e164)))
        assertEquals(MessageOutcome.BLOCKED, d)
    }

    @Test
    fun allowListAlwaysNotifiesEvenWhenSpamOrUnknownAndFiltered() {
        val settings = open.copy(allowList = setOf(e164), silenceMode = true, ignoreMode = true)
        assertEquals(MessageOutcome.NOTIFY, decide(spam = true, settings = settings))
    }

    @Test
    fun ignoreModeSendsUnknownSenderToQuietInbox() {
        assertEquals(MessageOutcome.QUIET_INBOX, decide(settings = open.copy(ignoreMode = true)))
    }

    @Test
    fun ignoreModeUsesPhonebookMembershipNotTheResolvedName() {
        val d = decide(identity = known, settings = open.copy(ignoreMode = true), inContacts = false)
        assertEquals(MessageOutcome.QUIET_INBOX, d)
    }

    @Test
    fun ignoreModeOutranksSilenceMode() {
        assertEquals(MessageOutcome.QUIET_INBOX, decide(settings = open.copy(ignoreMode = true, silenceMode = true)))
    }

    @Test
    fun silenceModeSilencesUnknownSender() {
        assertEquals(MessageOutcome.SILENCED, decide(settings = open.copy(silenceMode = true)))
    }

    @Test
    fun silenceModeDoesNotAffectPhonebookSenders() {
        assertEquals(MessageOutcome.NOTIFY, decide(inContacts = true, settings = open.copy(silenceMode = true)))
    }

    @Test
    fun spamFromPhonebookSenderGoesToQuietInbox() {
        assertEquals(MessageOutcome.QUIET_INBOX, decide(spam = true, inContacts = true))
    }

    @Test
    fun spamFromUnknownSenderInSilenceModeIsSilencedNotQuiet() {
        assertEquals(MessageOutcome.SILENCED, decide(spam = true, settings = open.copy(silenceMode = true)))
    }

    @Test
    fun spamWithoutFiltersGoesToQuietInbox() {
        assertEquals(MessageOutcome.QUIET_INBOX, decide(spam = true))
    }

    @Test
    fun contactsOnlyIsOnByDefaultAndBlocksUnknownSenders() {
        assertEquals(MessageOutcome.BLOCKED, decide(settings = Settings()))
    }

    @Test
    fun contactsOnlyBlocksNonContactEvenWithCnapName() {
        val d = decide(identity = cnap, settings = open.copy(contactsOnly = true))
        assertEquals(MessageOutcome.BLOCKED, d)
    }

    @Test
    fun contactsOnlyKeepsAllowListedNonContact() {
        val d = decide(identity = cnap, settings = open.copy(contactsOnly = true, allowList = setOf(e164)))
        assertEquals(MessageOutcome.NOTIFY, d)
    }

    @Test
    fun contactsOnlyLeavesContactsUnaffected() {
        val d = decide(identity = known, inContacts = true, settings = open.copy(contactsOnly = true))
        assertEquals(MessageOutcome.NOTIFY, d)
    }

    @Test
    fun otpFromDltSenderRingsWithSilenceModeOn() {
        val settings = open.copy(silenceMode = true)

        val d = decide(identity = dltBank, verdict = text(SmsKind.OTP, 0.2), settings = settings)

        assertEquals(MessageOutcome.NOTIFY, d)
    }

    @Test
    fun ringKindOverridesContactsOnlyForAStranger() {
        val settings = open.copy(contactsOnly = true)

        val d = decide(identity = dltBank, verdict = text(SmsKind.FINANCE, 0.2), settings = settings)

        assertEquals(MessageOutcome.NOTIFY, d)
    }

    @Test
    fun ringKindOverridesIgnoreMode() {
        val settings = open.copy(ignoreMode = true)

        val d = decide(verdict = text(SmsKind.GOVERNMENT, 0.2), settings = settings)

        assertEquals(MessageOutcome.NOTIFY, d)
    }

    @Test
    fun financeTextIsSilencedWhenFinanceIsSwitchedOff() {
        val settings = open.copy(silenceMode = true, ringKinds = Settings().ringKinds - SmsKind.FINANCE)

        val d = decide(identity = dltBank, verdict = text(SmsKind.FINANCE, 0.2), settings = settings)

        assertEquals(MessageOutcome.SILENCED, d)
    }

    @Test
    fun promotionalScoreAtThresholdGoesToQuietInbox() {
        assertEquals(MessageOutcome.QUIET_INBOX, decide(verdict = text(SmsKind.PROMOTIONAL, 0.6)))
    }

    @Test
    fun promotionalScoreJustBelowThresholdFallsThroughToSilenceMode() {
        val d = decide(verdict = text(SmsKind.PROMOTIONAL, 0.59), settings = open.copy(silenceMode = true))

        assertEquals(MessageOutcome.SILENCED, d)
    }

    @Test
    fun promotionalKindRingsWhenSwitchedOn() {
        val settings = open.copy(silenceMode = true, ringKinds = Settings().ringKinds + SmsKind.PROMOTIONAL)

        val d = decide(verdict = text(SmsKind.PROMOTIONAL, 0.2), settings = settings)

        assertEquals(MessageOutcome.NOTIFY, d)
    }

    @Test
    fun scamTextStaysQuietEvenWhenScamIsSwitchedOn() {
        val settings = open.copy(ringKinds = Settings().ringKinds + SmsKind.SCAM)

        val d = decide(verdict = text(SmsKind.SCAM, 0.7), settings = settings)

        assertEquals(MessageOutcome.QUIET_INBOX, d)
    }

    @Test
    fun personalTextFromAStrangerRingsWithDefaultSettings() {
        val d = decide(verdict = text(SmsKind.PERSONAL, 0.1), settings = Settings())

        assertEquals(MessageOutcome.NOTIFY, d)
    }

    @Test
    fun reputationSpamNumberDoesNotRingForAKind() {
        val d = decide(spam = true, verdict = text(SmsKind.OTP, 0.2), settings = Settings(contactsOnly = false))

        assertEquals(MessageOutcome.QUIET_INBOX, d)
    }

    @Test
    fun contactIgnoresClassifierScore() {
        val d = decide(identity = known, inContacts = true, verdict = text(SmsKind.PROMOTIONAL, 0.9))

        assertEquals(MessageOutcome.NOTIFY, d)
    }

    @Test
    fun muteOverrideSilencesAContact() {
        val settings = open.copy(senderRing = mapOf(e164 to SenderRing.MUTE))

        val d = decide(identity = known, inContacts = true, settings = settings)

        assertEquals(MessageOutcome.SILENCED, d)
    }

    @Test
    fun ringOverrideNotifiesAStrangerWithContactsOnly() {
        val settings = open.copy(contactsOnly = true, senderRing = mapOf(e164 to SenderRing.RING))

        assertEquals(MessageOutcome.NOTIFY, decide(settings = settings))
    }

    @Test
    fun blockListBeatsRingOverride() {
        val settings = open.copy(blockList = setOf(e164), senderRing = mapOf(e164 to SenderRing.RING))

        assertEquals(MessageOutcome.BLOCKED, decide(settings = settings))
    }

    @Test
    fun allowListBeatsMuteOverride() {
        val settings = open.copy(allowList = setOf(e164), senderRing = mapOf(e164 to SenderRing.MUTE))

        assertEquals(MessageOutcome.NOTIFY, decide(settings = settings))
    }

    @Test
    fun muteOverrideBeatsRingKind() {
        val settings = open.copy(senderRing = mapOf(e164 to SenderRing.MUTE))

        val d = decide(verdict = text(SmsKind.OTP, 0.2), settings = settings)

        assertEquals(MessageOutcome.SILENCED, d)
    }

    @Test
    fun ringOverrideBeatsSpamScore() {
        val settings = open.copy(senderRing = mapOf(e164 to SenderRing.RING))

        val d = decide(verdict = text(SmsKind.PROMOTIONAL, 0.9), settings = settings)

        assertEquals(MessageOutcome.NOTIFY, d)
    }

    @Test
    fun dltOverrideMatchesEveryBrandVariant() {
        val settings = open.copy(senderRing = mapOf("HDFCBK" to SenderRing.MUTE))

        assertEquals(MessageOutcome.SILENCED, decide(identity = dltBank, settings = settings))
        assertEquals(MessageOutcome.SILENCED, decide(identity = otherDltBank, settings = settings))
    }
}
