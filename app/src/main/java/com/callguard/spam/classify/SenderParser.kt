package com.callguard.spam.classify

/** Kind of sender address. */
internal enum class SenderKind { DLT, MOBILE, SHORT_CODE, OTHER }

/**
 * Parsed sender. For [SenderKind.DLT], [name] is the upper-case header name and [suffix] is T, S, P, G or null.
 */
internal data class SenderInfo(
    val kind: SenderKind,
    val name: String = "",
    val suffix: Char? = null,
)

/** Sorts a sender address into a DLT header, a 10-digit mobile number, a short code or other. */
internal object SenderParser {
    private val dltRe = Regex("""([A-Za-z]{2})-([A-Za-z0-9]{2,12})(?:-([TSPGtspg]))?""")
    private val mobileRe = Regex("""(?:\+?91|0)?[6-9]\d{9}""")
    private val shortCodeRe = Regex("""\d{3,6}""")
    private const val SEPARATORS = " +-()"
    private const val NAME_GROUP = 2
    private const val SUFFIX_GROUP = 3

    fun parse(sender: String): SenderInfo {
        val text = sender.trim()
        val dlt = dltRe.matchEntire(text)
        val numeric = text.all { it.isDigit() || it in SEPARATORS }
        val digits = text.filter { it.isDigit() || it == '+' }
        return when {
            dlt != null -> {
                SenderInfo(
                    SenderKind.DLT,
                    dlt.groupValues[NAME_GROUP].uppercase(),
                    dlt.groupValues[SUFFIX_GROUP].uppercase().firstOrNull(),
                )
            }

            numeric && mobileRe.matches(digits) -> {
                SenderInfo(SenderKind.MOBILE)
            }

            numeric && shortCodeRe.matches(digits) -> {
                SenderInfo(SenderKind.SHORT_CODE)
            }

            else -> {
                SenderInfo(SenderKind.OTHER)
            }
        }
    }
}
