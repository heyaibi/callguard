package com.callguard.prefs

enum class UnknownCallerAction { SILENCE, BLOCK }

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
    val allowList: Set<String> = emptySet(), // e164
    val blockList: Set<String> = emptySet(), // e164
)
