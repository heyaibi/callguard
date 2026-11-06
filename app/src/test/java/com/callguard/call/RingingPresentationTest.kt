package com.callguard.call

import com.callguard.data.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RingingPresentationTest {

    private fun ringing(
        uiVisible: Boolean,
        screenOn: Boolean = true,
        silenced: Boolean = false,
        fullScreenAllowed: Boolean = true,
    ) = ringingPresentation(CallPhase.RINGING, silenced, uiVisible, screenOn, fullScreenAllowed)

    @Test
    fun overAnotherAppWithScreenOnPopsUpAsHeadsUpWithFullScreenIntent() {
        val p = ringing(uiVisible = false)!!
        assertEquals(CallNotifier.CHANNEL_INCOMING, p.channelId)
        assertTrue(p.headsUp)
        assertTrue(p.fullScreen)
    }

    @Test
    fun inFrontWithScreenOnStaysInTheShadeWithoutHeadsUp() {
        val p = ringing(uiVisible = true, screenOn = true)!!
        assertEquals(CallNotifier.CHANNEL_INCOMING_QUIET, p.channelId)
        assertFalse(p.headsUp)
        assertFalse(p.fullScreen)
    }

    @Test
    fun screenOffNeverCountsAsInFrontSoTheFullScreenIntentStillOpensTheCall() {
        val p = ringing(uiVisible = true, screenOn = false)!!
        assertEquals(CallNotifier.CHANNEL_INCOMING, p.channelId)
        assertTrue(p.fullScreen)
    }

    @Test
    fun screenOffOrLockedUsesTheHeadsUpChannelAndFullScreenIntent() {
        val p = ringing(uiVisible = false, screenOn = false)!!
        assertEquals(CallNotifier.CHANNEL_INCOMING, p.channelId)
        assertTrue(p.fullScreen)
    }

    @Test
    fun withoutFullScreenPermissionTheHeadsUpStillPopsUp() {
        val p = ringing(uiVisible = false, fullScreenAllowed = false)!!
        assertEquals(CallNotifier.CHANNEL_INCOMING, p.channelId)
        assertTrue(p.headsUp)
        assertFalse(p.fullScreen)
    }

    @Test
    fun silencedCallNeverPopsUpOrTakesTheScreen() {
        listOf(true, false).forEach { visible ->
            val p = ringing(uiVisible = visible, silenced = true)!!
            assertEquals(CallNotifier.CHANNEL_SILENCED, p.channelId)
            assertFalse(p.headsUp)
            assertFalse(p.fullScreen)
        }
    }

    @Test
    fun callsThatAreNotRingingHaveNoRingingPresentation() {
        listOf(CallPhase.DIALING, CallPhase.ACTIVE, CallPhase.HOLDING, CallPhase.DISCONNECTED).forEach { phase ->
            assertNull(phase.name, ringingPresentation(phase, silenced = false, uiVisible = false, screenOn = true, fullScreenAllowed = true))
        }
    }

    @Test
    fun placedCallAlwaysOpensTheInCallScreen() {
        assertTrue(startsInCallUi(Direction.OUTGOING, CallPhase.DIALING, silenced = false, uiVisible = false, screenOn = true))
    }

    @Test
    fun ringingCallOpensTheInCallScreenWhenCallGuardIsInFront() {
        assertTrue(startsInCallUi(Direction.INCOMING, CallPhase.RINGING, silenced = false, uiVisible = true, screenOn = true))
    }

    @Test
    fun ringingCallOverAnotherAppOnlyRaisesItsHeadsUp() {
        assertFalse(startsInCallUi(Direction.INCOMING, CallPhase.RINGING, silenced = false, uiVisible = false, screenOn = true))
    }

    @Test
    fun ringingCallOpensTheInCallScreenWhenTheScreenIsOff() {
        assertTrue(startsInCallUi(Direction.INCOMING, CallPhase.RINGING, silenced = false, uiVisible = false, screenOn = false))
    }

    @Test
    fun silencedCallNeverOpensTheInCallScreen() {
        assertFalse(startsInCallUi(Direction.INCOMING, CallPhase.RINGING, silenced = true, uiVisible = true, screenOn = true))
        assertFalse(startsInCallUi(Direction.INCOMING, CallPhase.RINGING, silenced = true, uiVisible = false, screenOn = false))
    }
}
