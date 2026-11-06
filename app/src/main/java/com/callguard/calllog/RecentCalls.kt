package com.callguard.calllog

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import com.callguard.core.PhoneNumbers
import com.callguard.data.CallLogEntry
import com.callguard.data.Direction
import kotlin.math.abs

/** How a call ended, as the phone's call log names it. */
enum class CallKind { INCOMING, OUTGOING, MISSED, REJECTED, BLOCKED }

/** A row of the platform call log (CallLog.Calls). */
data class SystemCall(
    val number: String,
    val cachedName: String?,
    val kind: CallKind,
    val timestamp: Long,
    val durationSec: Long,
)

/**
 * One recents row: a system call, a CallGuard record the system did not log, or both joined.
 * [guard] carries CallGuard's outcome (ALLOWED, SILENCED, BLOCKED) and source for the tag and chip.
 */
data class RecentCall(
    val number: String,
    val name: String?,
    val timestamp: Long,
    val kind: CallKind,
    val durationSec: Long,
    val guard: CallLogEntry?,
)

/** A system call and a CallGuard record count as the same call when they match on number and start within this window. */
const val SAME_CALL_WINDOW_MILLIS = 60_000L

fun hasReadCallLog(context: Context): Boolean = context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

/**
 * Joins system calls with CallGuard records and returns them newest first. Each system call takes the first
 * unused CallGuard record with the same number that starts within [SAME_CALL_WINDOW_MILLIS]. CallGuard records
 * with no system counterpart (for example calls the phone never logged) become rows of their own.
 */
fun mergeRecentCalls(
    system: List<SystemCall>,
    guard: List<CallLogEntry>,
): List<RecentCall> {
    val unmatched = guard.toMutableList()
    val rows = ArrayList<RecentCall>(system.size + guard.size)
    for (call in system.sortedByDescending { it.timestamp }) {
        val key = canonical(call.number)
        val index =
            unmatched.indexOfFirst {
                canonical(it.number) == key && abs(it.timestamp - call.timestamp) <= SAME_CALL_WINDOW_MILLIS
            }
        val match = if (index >= 0) unmatched.removeAt(index) else null
        rows +=
            RecentCall(
                number = key,
                name = call.cachedName?.takeIf { it.isNotBlank() } ?: match?.name,
                timestamp = call.timestamp,
                kind = call.kind,
                durationSec = call.durationSec,
                guard = match,
            )
    }
    for (entry in unmatched) {
        rows +=
            RecentCall(
                number = canonical(entry.number),
                name = entry.name,
                timestamp = entry.timestamp,
                kind = entry.guardKind(),
                durationSec = 0,
                guard = entry,
            )
    }
    return rows.sortedByDescending { it.timestamp }
}

/**
 * The platform call log, newest first, at most [limit] rows. Empty without READ_CALL_LOG.
 * Call it off the main thread.
 */
fun readSystemCalls(
    context: Context,
    limit: Int = 300,
): List<SystemCall> {
    if (!hasReadCallLog(context)) return emptyList()
    return try {
        context.contentResolver
            .query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                ),
                null,
                null,
                "${CallLog.Calls.DATE} DESC",
            )?.use { c ->
                val out = ArrayList<SystemCall>()
                while (out.size < limit && c.moveToNext()) {
                    val number = c.getString(0)?.takeIf { it.isNotBlank() } ?: continue
                    out +=
                        SystemCall(
                            number = number,
                            cachedName = c.getString(1),
                            kind = platformKind(c.getInt(2)),
                            timestamp = c.getLong(3),
                            durationSec = c.getLong(4),
                        )
                }
                out
            }.orEmpty()
    } catch (e: Exception) {
        emptyList()
    }
}

/** The number as E.164 when it normalizes, otherwise as logged (private, unknown and short codes stay as they are). */
private fun canonical(raw: String): String = PhoneNumbers.normalize(raw)?.e164 ?: raw

private fun CallLogEntry.guardKind(): CallKind =
    when {
        outcome == "BLOCKED" -> CallKind.BLOCKED
        direction == Direction.OUTGOING || direction == Direction.APP -> CallKind.OUTGOING
        else -> CallKind.INCOMING
    }

/** Maps CallLog.Calls.TYPE. Voicemail and calls answered elsewhere were received calls. */
private fun platformKind(type: Int): CallKind =
    when (type) {
        CallLog.Calls.OUTGOING_TYPE -> CallKind.OUTGOING
        CallLog.Calls.MISSED_TYPE -> CallKind.MISSED
        CallLog.Calls.REJECTED_TYPE -> CallKind.REJECTED
        CallLog.Calls.BLOCKED_TYPE -> CallKind.BLOCKED
        else -> CallKind.INCOMING
    }
