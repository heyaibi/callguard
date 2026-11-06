package com.callguard.identity

import com.callguard.core.PhoneNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolutionPolicyTest {

    private val n = PhoneNumber("+919876543210")

    private class FakeSource(
        override val kind: SourceKind,
        private val name: String? = null,
        private val available: Boolean = true,
        private val throwOnAvailable: Boolean = false,
        private val throwOnLookup: Boolean = false,
    ) : LookupSource {
        override fun isAvailable(): Boolean {
            if (throwOnAvailable) error("availability check failed")
            return available
        }

        override fun lookup(n: PhoneNumber, ctx: LookupContext): String? {
            if (throwOnLookup) error("lookup failed")
            return name
        }
    }

    @Test
    fun higherRankWinsRegardlessOfListOrder() {
        val policy = ResolutionPolicy(
            listOf(
                FakeSource(SourceKind.BUNDLED_DATABASE, "SBI Card"),
                FakeSource(SourceKind.LOCAL_CONTACT, "Ravi"),
                FakeSource(SourceKind.CNAP, "RAVI KUMAR"),
            ),
        )
        val r = policy.resolve(n)
        assertEquals("RAVI KUMAR", r.name)
        assertEquals(SourceKind.CNAP, r.source)
    }

    @Test
    fun fallsThroughToNextSourceWhenHigherRankHasNoName() {
        val policy = ResolutionPolicy(
            listOf(FakeSource(SourceKind.LOCAL_CONTACT), FakeSource(SourceKind.BUNDLED_DATABASE, "SBI Card")),
        )
        assertEquals(SourceKind.BUNDLED_DATABASE, policy.resolve(n).source)
    }

    @Test
    fun skipsUnavailableSourceEvenWhenItWouldHaveAName() {
        val policy = ResolutionPolicy(
            listOf(
                FakeSource(SourceKind.LOCAL_CONTACT, "Ravi", available = false),
                FakeSource(SourceKind.BUNDLED_DATABASE, "SBI Card"),
            ),
        )
        assertEquals(SourceKind.BUNDLED_DATABASE, policy.resolve(n).source)
    }

    @Test
    fun blankNameDoesNotWin() {
        val policy = ResolutionPolicy(
            listOf(FakeSource(SourceKind.LOCAL_CONTACT, "   "), FakeSource(SourceKind.BUNDLED_DATABASE, "SBI Card")),
        )
        val r = policy.resolve(n)
        assertEquals("SBI Card", r.name)
        assertEquals(SourceKind.BUNDLED_DATABASE, r.source)
    }

    @Test
    fun namesAreTrimmed() {
        val policy = ResolutionPolicy(listOf(FakeSource(SourceKind.LOCAL_CONTACT, "  Ravi  ")))
        assertEquals("Ravi", policy.resolve(n).name)
    }

    @Test
    fun unknownNumberIsAValidResultWithNoName() {
        val policy = ResolutionPolicy(listOf(FakeSource(SourceKind.LOCAL_CONTACT), FakeSource(SourceKind.ON_DEVICE_CACHE)))
        val r = policy.resolve(n)
        assertNull(r.name)
        assertFalse(r.isKnown)
        assertEquals(SourceKind.NONE, r.source)
        assertEquals(Confidence.NONE, r.confidence)
        assertEquals(n, r.number)
    }

    @Test
    fun emptySourceListIsUnknown() {
        val r = ResolutionPolicy(emptyList()).resolve(n)
        assertFalse(r.isKnown)
        assertEquals(SourceKind.NONE, r.source)
    }

    @Test
    fun knownResultIsKnown() {
        val r = ResolutionPolicy(listOf(FakeSource(SourceKind.BUNDLED_DATABASE, "SBI Card"))).resolve(n)
        assertTrue(r.isKnown)
    }

    @Test
    fun lookupExceptionCountsAsNoName() {
        val policy = ResolutionPolicy(
            listOf(
                FakeSource(SourceKind.LOCAL_CONTACT, "Ravi", throwOnLookup = true),
                FakeSource(SourceKind.BUNDLED_DATABASE, "SBI Card"),
            ),
        )
        assertEquals(SourceKind.BUNDLED_DATABASE, policy.resolve(n).source)
    }

    @Test
    fun availabilityExceptionCountsAsUnavailable() {
        val policy = ResolutionPolicy(
            listOf(
                FakeSource(SourceKind.LOCAL_CONTACT, "Ravi", throwOnAvailable = true),
                FakeSource(SourceKind.BUNDLED_DATABASE, "SBI Card"),
            ),
        )
        assertEquals(SourceKind.BUNDLED_DATABASE, policy.resolve(n).source)
    }

    @Test
    fun confidenceFollowsSourceKind() {
        val expected = mapOf(
            SourceKind.CNAP to Confidence.HIGH,
            SourceKind.LOCAL_CONTACT to Confidence.HIGH,
            SourceKind.GOOGLE_CONTACTS to Confidence.HIGH,
            SourceKind.OPERATOR_SERVICE to Confidence.MEDIUM,
            SourceKind.APP_PROFILE to Confidence.MEDIUM,
            SourceKind.BUNDLED_DATABASE to Confidence.MEDIUM,
            SourceKind.ON_DEVICE_CACHE to Confidence.LOW,
            SourceKind.ONLINE_LOOKUP to Confidence.LOW,
        )
        for ((kind, confidence) in expected) {
            val r = ResolutionPolicy(listOf(FakeSource(kind, "Name"))).resolve(n)
            assertEquals(kind.name, confidence, r.confidence)
        }
    }

    @Test
    fun cnapOnlyCountsWhenDeliveredWithTheCall() {
        val policy = ResolutionPolicy(listOf(CnapSource(), FakeSource(SourceKind.BUNDLED_DATABASE, "SBI Card")))
        assertEquals(SourceKind.BUNDLED_DATABASE, policy.resolve(n).source)
        val withCnap = policy.resolve(n, LookupContext(cnapName = "RAVI KUMAR"))
        assertEquals(SourceKind.CNAP, withCnap.source)
        assertEquals("RAVI KUMAR", withCnap.name)
    }

    @Test
    fun operatorServiceIsNeverAvailable() {
        assertFalse(OperatorServiceSource().isAvailable())
    }
}
