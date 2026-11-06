package com.callguard.core

/** E.164 ("+919876543210") for numbers; raw text for alphanumeric and short-code senders ("VM-HDFCBK", "56767"). */
@JvmInline
value class PhoneNumber(val e164: String)

object PhoneNumbers {
    /**
     * Normalizes [raw] without libphonenumber. National rules exist for India ("IN") only; other regions
     * accept international forms ("+" or "00") only. Returns null for blank or unrecognized input.
     */
    fun normalize(raw: String, defaultRegion: String = "IN"): PhoneNumber? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        if (s.any(Char::isLetter)) return PhoneNumber(s.uppercase())
        val compact = s.filterNot { it.isWhitespace() || it == '-' || it == '(' || it == ')' }
        val intl = compact.startsWith('+')
        val digits = compact.removePrefix("+")
        if (digits.isEmpty() || !digits.all { it in '0'..'9' }) return null
        val india = defaultRegion.equals("IN", ignoreCase = true)
        val national = when {
            intl -> digits
            digits.startsWith("00") -> digits.drop(2)
            india && digits.length == 10 && digits[0] in '6'..'9' -> "91$digits"
            india && digits.length == 11 && (digits.startsWith("1800") || digits.startsWith("1860")) -> "91$digits"
            india && digits.length == 11 && digits[0] == '0' -> "91" + digits.drop(1)
            india && digits.length == 12 && digits.startsWith("91") -> digits
            digits.length in 3..6 -> return PhoneNumber(s)
            else -> return null
        }
        return if (national.length in 7..15) PhoneNumber("+$national") else null
    }

    /** "+91 98765 43210" for +91 numbers with 10 national digits; the E.164 form otherwise. */
    fun format(n: PhoneNumber): String {
        val e = n.e164
        return if (e.length == 13 && e.startsWith("+91")) "+91 ${e.substring(3, 8)} ${e.substring(8)}" else e
    }
}
