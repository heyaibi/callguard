package com.callguard.bridge

import com.callguard.core.PhoneNumber
import com.callguard.core.PhoneNumbers

/**
 * Pure parsing of WhatsApp notification text. No Android types, so it runs in plain JVM unit tests.
 * Every input may be null, because notifications often leave fields out.
 */
object BridgeParser {
    /** Same value as Notification.CATEGORY_CALL. */
    const val CATEGORY_CALL = "call"

    private val CALL_LINE = Regex("""incoming[\s ]+(voice|video)[\s ]+call""", RegexOption.IGNORE_CASE)
    private val CALL_ACTION = Regex("""\b(answer|accept|decline|reject|hang[\s ]*up)\b""", RegexOption.IGNORE_CASE)

    /** A run of digits that may start with "+" and contain spaces, dashes or brackets. */
    private val NUMBER_RUN = Regex("""\+?\d[\d\s\-()]{6,}\d""")

    /**
     * The "~ Name" profile prefix WhatsApp shows for a sender who is not saved. It must sit at the start of the
     * field, optionally after the number ("+91 98200 11223 ~Ramesh"), so "Hi ~ there" in a message is not a name.
     * The name stops at ":", "·" or any other punctuation.
     */
    private val PROFILE_NAME =
        Regex(
            """^[+\d\s\-()]*~[\s ]*(\p{L}[\p{L}\p{M}'’.\-]*(?: \p{L}[\p{L}\p{M}'’.\-]*)*)""",
        )

    private const val MAX_NAME_LENGTH = 40

    /** A call category, an "Incoming voice/video call" line, or a call-style action such as Answer or Decline. */
    fun isCall(
        category: String?,
        lines: List<String?>,
        actionTitles: List<String?> = emptyList(),
    ): Boolean =
        category == CATEGORY_CALL ||
            lines.any { it != null && CALL_LINE.containsMatchIn(it) } ||
            actionTitles.any { it != null && CALL_ACTION.containsMatchIn(it) }

    /** True when the call line says "video". */
    fun isVideoCall(lines: List<String?>): Boolean =
        lines.any {
            CALL_LINE
                .find(it.orEmpty())
                ?.groupValues
                ?.get(1)
                ?.equals("video", ignoreCase = true) == true
        }

    /** The phone number in a title or text. A saved contact's name has no number, so it gives null. */
    fun numberIn(raw: String?): PhoneNumber? {
        if (raw.isNullOrBlank()) return null
        for (token in NUMBER_RUN.findAll(raw)) {
            val n = PhoneNumbers.normalize(token.value) ?: continue
            if (isE164(n)) return n
        }
        return null
    }

    /** "~ Ramesh" becomes "Ramesh". Null when there is no "~" name or the name has no letters. */
    fun profileName(raw: String?): String? {
        val name =
            raw
                ?.let { PROFILE_NAME.find(it.trim()) }
                ?.groupValues
                ?.get(1)
                ?.trim() ?: return null
        return name.takeIf { it.any(Char::isLetter) && it.length <= MAX_NAME_LENGTH }
    }

    private fun isE164(n: PhoneNumber): Boolean = n.e164.startsWith("+") && n.e164.length in 8..16 && n.e164.drop(1).all { it in '0'..'9' }
}
