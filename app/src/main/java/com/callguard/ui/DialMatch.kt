package com.callguard.ui

/** The keypad digit that carries [c] (T9), or null for anything that is not a letter. */
fun t9Digit(c: Char): Char? =
    when (c.uppercaseChar()) {
        'A', 'B', 'C' -> '2'
        'D', 'E', 'F' -> '3'
        'G', 'H', 'I' -> '4'
        'J', 'K', 'L' -> '5'
        'M', 'N', 'O' -> '6'
        'P', 'Q', 'R', 'S' -> '7'
        'T', 'U', 'V' -> '8'
        'W', 'X', 'Y', 'Z' -> '9'
        else -> null
    }

/** The T9 digit string for the letters of [s]. Other characters are skipped. */
fun t9Code(s: String): String = s.mapNotNull(::t9Digit).joinToString("")

/**
 * True when the dialed digits spell the start of [name] on the keypad, from the first letter or from the first
 * letter of any later word. "727" matches "Amelia Parker" because "Parker" starts with 727.
 */
fun t9Matches(
    dialed: String,
    name: String,
): Boolean {
    if (dialed.isEmpty()) return false
    return name.split(' ', '-', '.').any { word -> t9Code(word).startsWith(dialed) }
}

/**
 * True when the dialed digits are the start of [e164] (with or without its +91 country code), or when they
 * spell the start of [name] in T9. A leading plus on the dialed text is ignored.
 */
fun dialMatches(
    dialed: String,
    name: String?,
    e164: String,
): Boolean {
    val typed = dialed.filter { it.isDigit() }
    if (typed.isEmpty()) return false
    val digits = e164.filter { it.isDigit() }
    val nationalStart = e164.startsWith("+91") && digits.drop(2).startsWith(typed)
    return digits.startsWith(typed) || nationalStart || (name != null && t9Matches(typed, name))
}
