package com.callguard.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.callguard.spam.classify.SmsKind
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun freshInstallLoadsTheDefaultSettings() {
        assertEquals(Settings(), SettingsStore(context).settings.value)
    }

    @Test
    fun ringKindsAndSenderRingsSurviveAReload() {
        val store = SettingsStore(context)
        store.update { it.copy(ringKinds = setOf(SmsKind.PROMOTIONAL)) }
        store.toggleSenderRing("HDFCBK", SenderRing.MUTE)
        store.toggleSenderRing("+919876501234", SenderRing.RING)

        val reloaded = SettingsStore(context).settings.value

        assertEquals(setOf(SmsKind.PROMOTIONAL), reloaded.ringKinds)
        assertEquals(mapOf("HDFCBK" to SenderRing.MUTE, "+919876501234" to SenderRing.RING), reloaded.senderRing)
    }

    @Test
    fun emptyRingKindsStayEmptyAfterAReload() {
        SettingsStore(context).update { it.copy(ringKinds = emptySet()) }

        val reloaded = SettingsStore(context).settings.value

        assertEquals(emptySet<SmsKind>(), reloaded.ringKinds)
    }

    @Test
    fun toggleSenderRingClearsTheSameRingAgain() {
        val store = SettingsStore(context)
        store.toggleSenderRing("HDFCBK", SenderRing.MUTE)

        store.toggleSenderRing("HDFCBK", SenderRing.MUTE)

        assertEquals(emptyMap<String, SenderRing>(), store.settings.value.senderRing)
    }

    @Test
    fun unknownStoredRingValuesAreSkipped() {
        context
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putStringSet("ringKinds", setOf("OTP", "NOT_A_KIND"))
            .putStringSet("senderRing", setOf("HDFCBK=MUTE", "AX-BAD=LOUD"))
            .commit()

        val settings = SettingsStore(context).settings.value

        assertEquals(setOf(SmsKind.OTP), settings.ringKinds)
        assertEquals(mapOf("HDFCBK" to SenderRing.MUTE), settings.senderRing)
    }
}
