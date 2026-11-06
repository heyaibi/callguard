package com.callguard.call

import android.app.NotificationManager
import android.media.AudioManager
import com.callguard.data.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallRulesTest {

    private val normal = AudioManager.RINGER_MODE_NORMAL
    private val vibrate = AudioManager.RINGER_MODE_VIBRATE
    private val silent = AudioManager.RINGER_MODE_SILENT

    @Test
    fun normalModeRingsAndVibrates() {
        assertEquals(RingPlan(sound = true, vibrate = true), ringPlan(silenced = false, dndSuppressed = false, ringerMode = normal))
    }

    @Test
    fun vibrateModeVibratesWithoutSound() {
        assertEquals(RingPlan(sound = false, vibrate = true), ringPlan(silenced = false, dndSuppressed = false, ringerMode = vibrate))
    }

    @Test
    fun silentModeDoesNothing() {
        assertFalse(ringPlan(silenced = false, dndSuppressed = false, ringerMode = silent).any)
    }

    @Test
    fun screeningSilenceStopsSoundAndVibrationEvenInNormalMode() {
        assertEquals(RingPlan(sound = false, vibrate = false), ringPlan(silenced = true, dndSuppressed = false, ringerMode = normal))
    }

    @Test
    fun doNotDisturbStopsSoundAndVibrationEvenInNormalMode() {
        assertEquals(RingPlan(sound = false, vibrate = false), ringPlan(silenced = false, dndSuppressed = true, ringerMode = normal))
    }

    @Test
    fun fromApi34ThePlatformsPerCallDoNotDisturbFlagDecides() {
        assertTrue(dndSuppresses(platformSaysSuppressed = true, sdkInt = 36, interruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL))
        assertFalse(dndSuppresses(platformSaysSuppressed = false, sdkInt = 36, interruptionFilter = NotificationManager.INTERRUPTION_FILTER_NONE))
    }

    @Test
    fun beforeApi34AnyInterruptionFilterOtherThanAllCountsAsDoNotDisturb() {
        assertTrue(dndSuppresses(platformSaysSuppressed = false, sdkInt = 33, interruptionFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY))
        assertFalse(dndSuppresses(platformSaysSuppressed = false, sdkInt = 33, interruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL))
    }

    @Test
    fun placedCallIsLoggedAsOutgoingWhateverHappened() {
        assertEquals("OUTGOING", unscreenedOutcome(Direction.OUTGOING, answered = true))
        assertEquals("OUTGOING", unscreenedOutcome(Direction.OUTGOING, answered = false))
    }

    @Test
    fun answeredIncomingCallIsLoggedAsAllowed() {
        assertEquals("ALLOWED", unscreenedOutcome(Direction.INCOMING, answered = true))
    }

    @Test
    fun unansweredIncomingCallIsLoggedAsMissed() {
        assertEquals("MISSED", unscreenedOutcome(Direction.INCOMING, answered = false))
    }
}
