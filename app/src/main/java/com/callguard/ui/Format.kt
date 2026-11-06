package com.callguard.ui

import com.callguard.core.PhoneNumber
import com.callguard.core.PhoneNumbers
import com.callguard.data.CallLogEntry
import com.callguard.identity.SourceKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Calls since local midnight. A call is identified when it carried a name. */
data class TodayStats(val total: Int, val identified: Int, val silenced: Int, val blocked: Int)

fun todayStats(calls: List<CallLogEntry>): TodayStats {
    val midnight = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val today = calls.filter { it.timestamp >= midnight }
    return TodayStats(
        total = today.size,
        identified = today.count { it.name != null },
        silenced = today.count { it.outcome == "SILENCED" },
        blocked = today.count { it.outcome == "BLOCKED" },
    )
}

/** "+91 98200 11223" for Indian numbers, E.164 otherwise. */
fun displayNumber(e164: String): String = PhoneNumbers.format(PhoneNumber(e164))

/** The number without its +91 country code, which is how it is typed on the keypad. */
fun nationalDigits(e164: String): String = e164.removePrefix("+91")

/** Up to two initials; "??" when there is no name. */
fun initialsOf(name: String?): String {
    val words = name.orEmpty().split(' ').filter { it.isNotBlank() }
    return if (words.isEmpty()) "??" else words.take(2).joinToString("") { it.first().toString() }
}

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

fun timeLabel(millis: Long): String = TIME.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private val DATE_SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val WEEKDAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)

/** Conversation-list time: the clock time today, YESTERDAY, a weekday within the last week, else the date. */
fun listTimeLabel(millis: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val at = Instant.ofEpochMilli(millis).atZone(zone)
    val days = ChronoUnit.DAYS.between(at.toLocalDate(), Instant.ofEpochMilli(now).atZone(zone).toLocalDate())
    return when (days) {
        0L -> TIME.format(at)
        1L -> "YESTERDAY"
        in 2L..6L -> WEEKDAY.format(at).uppercase()
        else -> DATE_SHORT.format(at).uppercase()
    }
}

/** TODAY, YESTERDAY, or the calendar date. */
fun dayLabel(millis: Long): String {
    val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (date) {
        today -> "TODAY"
        today.minusDays(1) -> "YESTERDAY"
        else -> DATE.format(date).uppercase()
    }
}

/** Short source name for chips, in the mockup vocabulary. */
fun SourceKind.label(): String = when (this) {
    SourceKind.CNAP -> "CNAP"
    SourceKind.LOCAL_CONTACT -> "CONTACT"
    SourceKind.GOOGLE_CONTACTS -> "GOOGLE"
    SourceKind.OPERATOR_SERVICE -> "OPERATOR"
    SourceKind.APP_PROFILE -> "APP"
    SourceKind.ON_DEVICE_CACHE -> "CACHE"
    SourceKind.BUNDLED_DATABASE -> "BUNDLED"
    SourceKind.ONLINE_LOOKUP -> "ONLINE"
    SourceKind.NONE -> "NO MATCH"
}
