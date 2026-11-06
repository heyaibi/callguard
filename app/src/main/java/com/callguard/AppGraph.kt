package com.callguard

import android.content.Context
import com.callguard.core.PhoneNumber
import com.callguard.core.PhoneNumbers
import com.callguard.data.BundledDatabase
import com.callguard.data.CallGuardDb
import com.callguard.data.Repositories
import com.callguard.identity.AppProfileSource
import com.callguard.identity.BundledSource
import com.callguard.identity.CacheSource
import com.callguard.identity.CnapSource
import com.callguard.identity.GoogleContactsSource
import com.callguard.identity.LocalContactSource
import com.callguard.identity.LookupContext
import com.callguard.identity.OnlineLookupSource
import com.callguard.identity.OperatorServiceSource
import com.callguard.identity.ResolutionPolicy
import com.callguard.identity.ResolutionResult
import com.callguard.messaging.MessageFilterPolicy
import com.callguard.prefs.Settings
import com.callguard.prefs.SettingsStore
import com.callguard.screening.ScreeningPolicy
import com.callguard.spam.SpamPolicy
import com.callguard.ui.screens.MessagesModel
import kotlinx.coroutines.flow.MutableStateFlow

/** Manual service locator. Built once by [CallGuardApp]. */
class AppGraph(
    context: Context,
) {
    val settingsStore = SettingsStore(context)
    val repos = Repositories(CallGuardDb(context))
    val bundled = BundledDatabase(context)
    private val localContacts = LocalContactSource(context)

    val resolutionPolicy =
        ResolutionPolicy(
            listOf(
                CnapSource(),
                localContacts,
                GoogleContactsSource(context, settingsStore.settings),
                OperatorServiceSource(),
                AppProfileSource(repos.cache, settingsStore.settings),
                CacheSource(repos.cache),
                BundledSource(bundled),
                OnlineLookupSource(settingsStore.settings),
            ),
        )
    val spamPolicy = SpamPolicy(repos.reputation, bundled::isSpam, repos.calls::countSince)
    val screeningPolicy = ScreeningPolicy()
    val messageFilterPolicy = MessageFilterPolicy()

    /**
     * The Messages list as last loaded, null until the first load. It outlives the screen, so a return shows it
     * at once.
     */
    internal val messagesModel = MutableStateFlow<MessagesModel?>(null)

    /** Null when [raw] is not a usable number (blank, private, or unrecognized). */
    fun resolve(
        raw: String,
        cnapName: String? = null,
    ): ResolutionResult? {
        val n = PhoneNumbers.normalize(raw) ?: return null
        return resolutionPolicy.resolve(n, LookupContext(cnapName))
    }

    fun isInContacts(n: PhoneNumber): Boolean = localContacts.contains(n)

    /** Contacts only can match nothing without READ_CONTACTS, so it stays off until the permission is granted. */
    fun contactsOnlyActive(): Boolean = settingsStore.settings.value.contactsOnly && localContacts.isAvailable()

    /** Settings to pass to the screening and messaging policies: contactsOnly is forced off when it cannot apply. */
    fun effectiveSettings(): Settings = settingsStore.settings.value.copy(contactsOnly = contactsOnlyActive())
}
