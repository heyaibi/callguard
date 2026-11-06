package com.callguard.prefs

import android.content.Context
import com.callguard.core.PhoneNumber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsStore(
    context: Context,
) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    val settings: StateFlow<Settings> = state.asStateFlow()

    @Synchronized
    fun update(transform: (Settings) -> Settings) {
        val next = transform(state.value)
        save(next)
        state.value = next
    }

    fun allow(n: PhoneNumber) = update { it.copy(allowList = it.allowList + n.e164, blockList = it.blockList - n.e164) }

    fun block(n: PhoneNumber) = update { it.copy(blockList = it.blockList + n.e164, allowList = it.allowList - n.e164) }

    fun unlist(n: PhoneNumber) = update { it.copy(allowList = it.allowList - n.e164, blockList = it.blockList - n.e164) }

    @Synchronized
    fun reset() {
        prefs.edit().clear().apply()
        state.value = Settings()
    }

    private fun load(): Settings {
        val d = Settings()
        return Settings(
            contactsOnly = prefs.getBoolean(CONTACTS_ONLY, d.contactsOnly),
            silenceMode = prefs.getBoolean(SILENCE, d.silenceMode),
            ignoreMode = prefs.getBoolean(IGNORE, d.ignoreMode),
            unknownCallerAction =
                prefs
                    .getString(UNKNOWN_ACTION, null)
                    ?.let { runCatching { UnknownCallerAction.valueOf(it) }.getOrNull() } ?: d.unknownCallerAction,
            blockSpam = prefs.getBoolean(BLOCK_SPAM, d.blockSpam),
            showCardForUnknown = prefs.getBoolean(SHOW_CARD, d.showCardForUnknown),
            onlineLookup = prefs.getBoolean(ONLINE, d.onlineLookup),
            googleContacts = prefs.getBoolean(GOOGLE, d.googleContacts),
            bridgeApps =
                if (prefs.contains(BRIDGE)) {
                    prefs
                        .getStringSet(BRIDGE, null)
                        .orEmpty()
                        .associate { it.substringBefore('=') to it.substringAfter('=').toBoolean() }
                } else {
                    d.bridgeApps
                },
            allowList = prefs.getStringSet(ALLOW, null).orEmpty().toSet(),
            blockList = prefs.getStringSet(BLOCK, null).orEmpty().toSet(),
        )
    }

    private fun save(s: Settings) {
        prefs
            .edit()
            .putBoolean(CONTACTS_ONLY, s.contactsOnly)
            .putBoolean(SILENCE, s.silenceMode)
            .putBoolean(IGNORE, s.ignoreMode)
            .putString(UNKNOWN_ACTION, s.unknownCallerAction.name)
            .putBoolean(BLOCK_SPAM, s.blockSpam)
            .putBoolean(SHOW_CARD, s.showCardForUnknown)
            .putBoolean(ONLINE, s.onlineLookup)
            .putBoolean(GOOGLE, s.googleContacts)
            .putStringSet(BRIDGE, s.bridgeApps.map { "${it.key}=${it.value}" }.toSet())
            .putStringSet(ALLOW, s.allowList.toSet())
            .putStringSet(BLOCK, s.blockList.toSet())
            .apply()
    }

    private companion object {
        const val CONTACTS_ONLY = "contactsOnly"
        const val SILENCE = "silenceMode"
        const val IGNORE = "ignoreMode"
        const val UNKNOWN_ACTION = "unknownCallerAction"
        const val BLOCK_SPAM = "blockSpam"
        const val SHOW_CARD = "showCardForUnknown"
        const val ONLINE = "onlineLookup"
        const val GOOGLE = "googleContacts"
        const val BRIDGE = "bridgeApps"
        const val ALLOW = "allowList"
        const val BLOCK = "blockList"
    }
}
