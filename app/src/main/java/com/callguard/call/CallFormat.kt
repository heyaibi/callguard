package com.callguard.call

import java.util.Locale

/**
 * Elapsed call time as the timer pill shows it: MM:SS, or H:MM:SS from one hour on. Negative input reads as zero.
 * Locale.ROOT keeps the digits ASCII on every device.
 */
fun formatElapsed(millis: Long): String {
    val total = (millis / 1000L).coerceAtLeast(0L)
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val seconds = total % 60L
    return if (hours > 0L) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}
