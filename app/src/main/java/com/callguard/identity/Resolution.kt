package com.callguard.identity

import com.callguard.core.PhoneNumber

enum class Confidence { HIGH, MEDIUM, LOW, NONE }

enum class SourceKind(
    val rank: Int,
    val trust: String,
) {
    CNAP(1, "network"),
    LOCAL_CONTACT(2, "device"),
    GOOGLE_CONTACTS(3, "device"),
    OPERATOR_SERVICE(4, "network"),
    APP_PROFILE(5, "learned"),
    ON_DEVICE_CACHE(6, "learned"),
    BUNDLED_DATABASE(7, "bundled"),
    ONLINE_LOOKUP(8, "online"),
    NONE(99, "none"),
}

data class LookupContext(
    val cnapName: String? = null,
)

interface LookupSource {
    val kind: SourceKind

    fun isAvailable(): Boolean

    fun lookup(
        n: PhoneNumber,
        ctx: LookupContext,
    ): String?
}

data class ResolutionResult(
    val number: PhoneNumber,
    val name: String?,
    val source: SourceKind,
    val confidence: Confidence,
) {
    val isKnown get() = name != null
}

/** Confidence of a name from this source: network and device sources HIGH, learned and bundled MEDIUM, cache and online LOW. */
val SourceKind.confidence: Confidence
    get() =
        when (this) {
            SourceKind.CNAP, SourceKind.LOCAL_CONTACT, SourceKind.GOOGLE_CONTACTS -> Confidence.HIGH
            SourceKind.OPERATOR_SERVICE, SourceKind.APP_PROFILE, SourceKind.BUNDLED_DATABASE -> Confidence.MEDIUM
            SourceKind.ON_DEVICE_CACHE, SourceKind.ONLINE_LOOKUP -> Confidence.LOW
            SourceKind.NONE -> Confidence.NONE
        }

class ResolutionPolicy(
    sources: List<LookupSource>,
) {
    private val ordered = sources.sortedBy { it.kind.rank }

    /** Never throws: a source that fails or is unavailable is treated as having no name. */
    fun resolve(
        n: PhoneNumber,
        ctx: LookupContext = LookupContext(),
    ): ResolutionResult {
        for (source in ordered) {
            val name =
                try {
                    if (source.isAvailable()) source.lookup(n, ctx)?.trim()?.takeIf { it.isNotEmpty() } else null
                } catch (e: Exception) {
                    null
                }
            if (name != null) return ResolutionResult(n, name, source.kind, source.kind.confidence)
        }
        return ResolutionResult(n, null, SourceKind.NONE, Confidence.NONE)
    }
}
