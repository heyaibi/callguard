package com.callguard.messaging

import com.callguard.identity.ResolutionResult
import com.callguard.prefs.Settings
import com.callguard.spam.SpamAssessment

enum class MessageOutcome { NOTIFY, SILENCED, QUIET_INBOX, BLOCKED }

class MessageFilterPolicy {
    /**
     * Rules are applied in order: block list, allow list, contacts only, unknown sender in ignore or silence mode, spam, else notify.
     * Callers pass settings from AppGraph.effectiveSettings(), which clears contactsOnly without READ_CONTACTS.
     */
    fun decide(
        identity: ResolutionResult,
        spam: SpamAssessment,
        settings: Settings,
        inContacts: Boolean,
    ): MessageOutcome {
        val e164 = identity.number.e164
        return when {
            e164 in settings.blockList -> MessageOutcome.BLOCKED
            e164 in settings.allowList -> MessageOutcome.NOTIFY
            settings.contactsOnly && !inContacts -> MessageOutcome.BLOCKED
            !inContacts && settings.ignoreMode -> MessageOutcome.QUIET_INBOX
            !inContacts && settings.silenceMode -> MessageOutcome.SILENCED
            spam.isSpam -> MessageOutcome.QUIET_INBOX
            else -> MessageOutcome.NOTIFY
        }
    }
}
