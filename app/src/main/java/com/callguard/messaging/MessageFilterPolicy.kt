package com.callguard.messaging

import com.callguard.identity.ResolutionResult
import com.callguard.prefs.SenderRing
import com.callguard.prefs.Settings
import com.callguard.spam.SpamAssessment
import com.callguard.spam.classify.ClassifierConfig
import com.callguard.spam.classify.SmsVerdict

enum class MessageOutcome { NOTIFY, SILENCED, QUIET_INBOX, BLOCKED }

class MessageFilterPolicy {
    /**
     * Rules are applied in order:
     * 1. Block list, then allow list.
     * 2. The sender's ring or mute setting. RING notifies and MUTE silences, even for a contact.
     * 3. A contact: notify, or the quiet inbox when the number reputation says spam.
     * 4. Not a contact, and a spam score of [ClassifierConfig.SPAM_THRESHOLD] or more: quiet inbox.
     * 5. Not a contact, a kind in [Settings.ringKinds], and not a number marked spam: notify.
     * 6. Not a contact: contacts only, then ignore mode, then silence mode, then the number reputation.
     * Callers pass settings from AppGraph.effectiveSettings(), which clears contactsOnly without READ_CONTACTS.
     */
    fun decide(
        identity: ResolutionResult,
        spam: SpamAssessment,
        verdict: SmsVerdict,
        settings: Settings,
        inContacts: Boolean,
    ): MessageOutcome {
        val e164 = identity.number.e164
        return when {
            e164 in settings.blockList -> {
                MessageOutcome.BLOCKED
            }

            e164 in settings.allowList -> {
                MessageOutcome.NOTIFY
            }

            else -> {
                when (settings.senderRing[senderKey(e164)]) {
                    SenderRing.RING -> MessageOutcome.NOTIFY
                    SenderRing.MUTE -> MessageOutcome.SILENCED
                    null -> unlistedOutcome(spam, verdict, settings, inContacts)
                }
            }
        }
    }

    private fun unlistedOutcome(
        spam: SpamAssessment,
        verdict: SmsVerdict,
        settings: Settings,
        inContacts: Boolean,
    ): MessageOutcome =
        when {
            inContacts -> if (spam.isSpam) MessageOutcome.QUIET_INBOX else MessageOutcome.NOTIFY
            verdict.spamScore >= ClassifierConfig.SPAM_THRESHOLD -> MessageOutcome.QUIET_INBOX
            !spam.isSpam && verdict.kind in settings.ringKinds -> MessageOutcome.NOTIFY
            settings.contactsOnly -> MessageOutcome.BLOCKED
            settings.ignoreMode -> MessageOutcome.QUIET_INBOX
            settings.silenceMode -> MessageOutcome.SILENCED
            spam.isSpam -> MessageOutcome.QUIET_INBOX
            else -> MessageOutcome.NOTIFY
        }
}
