package com.callguard.call

import android.telecom.Call
import android.telecom.CallAudioState
import org.junit.Assert.assertEquals
import org.junit.Test

class CallPhaseTest {

    @Test
    fun ringingStatesRing() {
        assertEquals(CallPhase.RINGING, phaseFor(Call.STATE_RINGING))
        assertEquals(CallPhase.RINGING, phaseFor(Call.STATE_SIMULATED_RINGING))
    }

    @Test
    fun connectionSetupReadsAsDialing() {
        val setup = listOf(
            Call.STATE_NEW,
            Call.STATE_DIALING,
            Call.STATE_CONNECTING,
            Call.STATE_SELECT_PHONE_ACCOUNT,
            Call.STATE_PULLING_CALL,
            Call.STATE_AUDIO_PROCESSING,
        )
        setup.forEach { assertEquals(it.toString(), CallPhase.DIALING, phaseFor(it)) }
    }

    @Test
    fun activeAndHoldingKeepTheirPhase() {
        assertEquals(CallPhase.ACTIVE, phaseFor(Call.STATE_ACTIVE))
        assertEquals(CallPhase.HOLDING, phaseFor(Call.STATE_HOLDING))
    }

    @Test
    fun disconnectingAndDisconnectedEndTheCall() {
        assertEquals(CallPhase.DISCONNECTED, phaseFor(Call.STATE_DISCONNECTING))
        assertEquals(CallPhase.DISCONNECTED, phaseFor(Call.STATE_DISCONNECTED))
    }

    @Test
    fun audioRoutesMapToTheScreenRoutes() {
        assertEquals(AudioRoute.SPEAKER, audioRouteFor(CallAudioState.ROUTE_SPEAKER))
        assertEquals(AudioRoute.BLUETOOTH, audioRouteFor(CallAudioState.ROUTE_BLUETOOTH))
        assertEquals(AudioRoute.WIRED, audioRouteFor(CallAudioState.ROUTE_WIRED_HEADSET))
        assertEquals(AudioRoute.EARPIECE, audioRouteFor(CallAudioState.ROUTE_EARPIECE))
        assertEquals(AudioRoute.EARPIECE, audioRouteFor(CallAudioState.ROUTE_WIRED_OR_EARPIECE))
    }
}
