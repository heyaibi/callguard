package com.callguard.call

import org.junit.Assert.assertEquals
import org.junit.Test

class CallFormatTest {
    @Test
    fun zeroAndSubSecondReadAsZero() {
        assertEquals("00:00", formatElapsed(0L))
        assertEquals("00:00", formatElapsed(999L))
    }

    @Test
    fun secondsPadToTwoDigits() {
        assertEquals("00:05", formatElapsed(5_000L))
    }

    @Test
    fun minutesAndSecondsRollOver() {
        assertEquals("01:01", formatElapsed(61_000L))
        assertEquals("59:59", formatElapsed(3_599_000L))
    }

    @Test
    fun anHourOrMoreShowsHours() {
        assertEquals("1:00:00", formatElapsed(3_600_000L))
        assertEquals("1:02:03", formatElapsed(3_723_000L))
    }

    @Test
    fun negativeElapsedReadsAsZero() {
        assertEquals("00:00", formatElapsed(-5_000L))
    }
}
