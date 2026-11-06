package com.callguard.screening

import com.callguard.identity.ResolutionResult
import com.callguard.prefs.Settings
import com.callguard.prefs.UnknownCallerAction
import com.callguard.spam.SpamAssessment

enum class ScreeningDecision { ALLOW, SILENCE, BLOCK }

class ScreeningPolicy {
    /**
     * Rules are applied in order: block list, allow list, contacts only, spam, unknown caller in silence mode, else allow.
     * Callers pass settings from AppGraph.effectiveSettings(), which clears contactsOnly without READ_CONTACTS.
     */
    fun decide(identity: ResolutionResult, spam: SpamAssessment, settings: Settings, inContacts: Boolean): ScreeningDecision {
        val e164 = identity.number.e164
        return when {
            e164 in settings.blockList -> ScreeningDecision.BLOCK
            e164 in settings.allowList -> ScreeningDecision.ALLOW
            settings.contactsOnly && !inContacts -> ScreeningDecision.BLOCK
            spam.isSpam && settings.blockSpam -> ScreeningDecision.BLOCK
            spam.isSpam -> ScreeningDecision.SILENCE
            !inContacts && !identity.isKnown && settings.silenceMode -> when (settings.unknownCallerAction) {
                UnknownCallerAction.SILENCE -> ScreeningDecision.SILENCE
                UnknownCallerAction.BLOCK -> ScreeningDecision.BLOCK
            }
            else -> ScreeningDecision.ALLOW
        }
    }
}
