package com.callguard.data

import android.content.Context
import com.callguard.identity.SourceKind
import java.time.LocalDate
import java.time.ZoneId

/**
 * Debug-only sample names and calls so Home, Recents and Lookup are not empty on first run.
 * Written once; the flag lives in SharedPreferences, so clearing app data seeds again.
 */
object DemoData {
    private const val PREFS = "demo"
    private const val SEEDED = "seeded"
    private const val MINUTE = 60_000L

    fun seedOnce(
        context: Context,
        repos: Repositories,
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(SEEDED, false)) return
        val now = System.currentTimeMillis()
        val midnight =
            LocalDate
                .now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

        listOf(
            CacheEntry("+919820011223", "Priya Sharma", "call", now),
            CacheEntry("+918827014569", "Vikram Rao", "sms", now),
            CacheEntry("+919824406619", "Tara Shah", "call", now),
        ).forEach { repos.cache.put(it) }

        // Today's calls step back 25 minutes from now and never go before midnight.
        fun at(stepsBack: Int) = maxOf(now - stepsBack * 25 * MINUTE, midnight)
        listOf(
            call("+919820011223", "Priya Sharma", SourceKind.ON_DEVICE_CACHE, "ALLOWED", at(1)),
            call("+917011155390", null, SourceKind.NONE, "SILENCED", at(2)),
            call("+919988766501", "Rohan Mehta", SourceKind.CNAP, "ALLOWED", at(3)),
            call("+911404000111", null, SourceKind.NONE, "SILENCED", at(4)),
            call("+9118001234567", "SBI Card", SourceKind.BUNDLED_DATABASE, "ALLOWED", at(5)),
            call("+919447032190", "Arjun Nair", SourceKind.LOCAL_CONTACT, "BLOCKED", at(6)),
            call("+919003977145", "Anjali Iyer", SourceKind.LOCAL_CONTACT, "ALLOWED", midnight - 90 * MINUTE),
            call("+919811460027", "Kabir Khanna", SourceKind.ON_DEVICE_CACHE, "BLOCKED", midnight - 360 * MINUTE),
        ).forEach { repos.calls.add(it) }

        prefs.edit().putBoolean(SEEDED, true).apply()
    }

    private fun call(
        number: String,
        name: String?,
        source: SourceKind,
        outcome: String,
        at: Long,
    ) = CallLogEntry(
        number = number,
        name = name,
        source = source,
        direction = Direction.INCOMING,
        outcome = outcome,
        timestamp = at,
    )
}
