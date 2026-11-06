package com.callguard.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class FormatTest {

    private val utc = ZoneId.of("UTC")
    // 2026-11-06 is a Friday.
    private val now = Instant.parse("2026-11-06T12:00:00Z").toEpochMilli()

    @Test
    fun todayShowsTheClockTime() {
        val millis = Instant.parse("2026-11-06T10:05:00Z").toEpochMilli()
        assertEquals("10:05", listTimeLabel(millis, now, utc))
    }

    @Test
    fun yesterdayIsNamed() {
        val millis = Instant.parse("2026-11-05T23:00:00Z").toEpochMilli()
        assertEquals("YESTERDAY", listTimeLabel(millis, now, utc))
    }

    @Test
    fun withinTheLastWeekShowsTheWeekday() {
        val millis = Instant.parse("2026-11-03T09:00:00Z").toEpochMilli()
        assertEquals("TUE", listTimeLabel(millis, now, utc))
    }

    @Test
    fun olderTextsShowTheDate() {
        val millis = Instant.parse("2026-10-20T09:00:00Z").toEpochMilli()
        assertEquals("20 OCT", listTimeLabel(millis, now, utc))
    }
}
