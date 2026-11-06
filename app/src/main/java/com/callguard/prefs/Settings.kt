package com.callguard.prefs

import com.callguard.spam.classify.SmsKind

enum class UnknownCallerAction { SILENCE, BLOCK }

/** A per-sender override. RING notifies with sound. MUTE silences the text but keeps it in the inbox. */
enum class SenderRing { RING, MUTE }

data class Settings(
    val contactsOnly: Boolean = true,
    val silenceMode: Boolean = false,
    val ignoreMode: Boolean = false,
    val unknownCallerAction: UnknownCallerAction = UnknownCallerAction.SILENCE,
    val blockSpam: Boolean = false,
    val showCardForUnknown: Boolean = true,
    val onlineLookup: Boolean = false,
    val googleContacts: Boolean = false,
    val bridgeApps: Map<String, Boolean> = mapOf("com.whatsapp" to true, "com.whatsapp.w4b" to true),
    // Both lists hold e164 numbers.
    val allowList: Set<String> = emptySet(),
    val blockList: Set<String> = emptySet(),
    // Kinds of text that ring when the sender is not a contact. Contacts always ring. PERSONAL is off by default, so a
    // stranger's personal text follows the unknown-sender modes.
    val ringKinds: Set<SmsKind> =
        setOf(
            SmsKind.OTP,
            SmsKind.FINANCE,
            SmsKind.GOVERNMENT,
            SmsKind.TRANSACTIONAL,
            SmsKind.DELIVERY,
        ),
    // Keyed by senderKey(): the e164 form for a number, the brand header for a DLT sender.
    val senderRing: Map<String, SenderRing> = emptyMap(),
)
