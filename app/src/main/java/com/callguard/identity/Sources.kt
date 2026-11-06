package com.callguard.identity

import com.callguard.core.PhoneNumber
import com.callguard.data.BundledDatabase
import com.callguard.data.CacheRepository
import com.callguard.prefs.Settings
import kotlinx.coroutines.flow.StateFlow

internal const val APP_ORIGIN_PREFIX = "app:"

/** Name delivered by the network with the call. */
class CnapSource : LookupSource {
    override val kind = SourceKind.CNAP
    override fun isAvailable() = true
    override fun lookup(n: PhoneNumber, ctx: LookupContext) = ctx.cnapName
}

/** No operator API is wired up, so it is never available. */
class OperatorServiceSource : LookupSource {
    override val kind = SourceKind.OPERATOR_SERVICE
    override fun isAvailable() = false
    override fun lookup(n: PhoneNumber, ctx: LookupContext): String? = null
}

/** Names learned from a messaging app; only used while the bridge is enabled for that app. */
class AppProfileSource(private val cache: CacheRepository, private val settings: StateFlow<Settings>) : LookupSource {
    override val kind = SourceKind.APP_PROFILE
    override fun isAvailable() = true
    override fun lookup(n: PhoneNumber, ctx: LookupContext): String? {
        val entry = cache.get(n) ?: return null
        if (!entry.origin.startsWith(APP_ORIGIN_PREFIX)) return null
        val enabled = settings.value.bridgeApps[entry.origin.removePrefix(APP_ORIGIN_PREFIX)] == true
        return if (enabled) entry.name else null
    }
}

/** Names learned from calls and messages. App-origin entries are read only by [AppProfileSource]. */
class CacheSource(private val cache: CacheRepository) : LookupSource {
    override val kind = SourceKind.ON_DEVICE_CACHE
    override fun isAvailable() = true
    override fun lookup(n: PhoneNumber, ctx: LookupContext): String? =
        cache.get(n)?.takeUnless { it.origin.startsWith(APP_ORIGIN_PREFIX) }?.name
}

class BundledSource(private val bundled: BundledDatabase) : LookupSource {
    override val kind = SourceKind.BUNDLED_DATABASE
    override fun isAvailable() = true
    override fun lookup(n: PhoneNumber, ctx: LookupContext): String? = bundled.nameFor(n)
}

/** Opt-in remote lookup. There is no backend yet, so it never returns a name. */
class OnlineLookupSource(private val settings: StateFlow<Settings>) : LookupSource {
    override val kind = SourceKind.ONLINE_LOOKUP
    override fun isAvailable() = settings.value.onlineLookup
    override fun lookup(n: PhoneNumber, ctx: LookupContext): String? = null
}
