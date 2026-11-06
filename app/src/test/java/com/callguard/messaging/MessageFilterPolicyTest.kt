package com.callguard.messaging

import com.callguard.core.PhoneNumber
import com.callguard.identity.Confidence
import com.callguard.identity.ResolutionResult
import com.callguard.identity.SourceKind
import com.callguard.prefs.Settings
import com.callguard.spam.SpamAssessment
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageFilterPolicyTest {

    private val n = PhoneNumber("+919876543210")
    private val e164 = n.e164
    private val policy = MessageFilterPolicy()
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
    ): MessageOutcome = policy.decide(identity, assessment(spam), settings, inContacts)

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
}
