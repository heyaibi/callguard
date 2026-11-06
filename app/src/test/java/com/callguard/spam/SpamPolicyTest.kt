package com.callguard.spam

import com.callguard.core.PhoneNumber
import com.callguard.data.ReputationEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class SpamPolicyTest {

    private class FakeReputation : ReputationStore {
        val entries = mutableMapOf<String, ReputationEntry>()
        override fun get(n: PhoneNumber): ReputationEntry? = entries[n.e164]
        override fun put(entry: ReputationEntry) {
            entries[entry.number] = entry
        }
        override fun remove(n: PhoneNumber) {
            entries.remove(n.e164)
        }
    }

    private val zone = ZoneId.of("Asia/Kolkata")
    private val clean = PhoneNumber("+919876543210")
    private val telemarketer = PhoneNumber("+911404123456")
    private val reputation = FakeReputation()
    private val bundledSpam = mutableSetOf<String>()
    private val callTimes = mutableMapOf<String, MutableList<Long>>()
    private val policy = SpamPolicy(
        reputation = reputation,
        isBundledSpam = { bundledSpam.contains(it.e164) },
        callsSince = { n, since -> callTimes[n.e164].orEmpty().count { it >= since } },
        zone = zone,
    )
    private val midday = at(12, 0)

    private fun at(hour: Int, minute: Int): Long =
        ZonedDateTime.of(2026, 11, 6, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    /** Records [count] calls from [n] that happened [agoMillis] before [time]. */
    private fun callsBefore(n: PhoneNumber, time: Long, count: Int, agoMillis: Long = 60_000L) {
        callTimes.getOrPut(n.e164) { mutableListOf() }.addAll(List(count) { time - agoMillis })
    }

    @Test
    fun cleanNumberHasNoSignals() {
        val a = policy.assess(clean, midday)
        assertEquals(0, a.score)
        assertFalse(a.isSpam)
        assertEquals("no spam signals", a.reason)
    }

    @Test
    fun userSpamMarkScoresHundred() {
        policy.mark(clean, true)
        val a = policy.assess(clean, midday)
        assertEquals(100, a.score)
        assertTrue(a.isSpam)
        assertEquals("user marked spam", a.reason)
    }

    @Test
    fun userMarkIsStoredAsUserEntry() {
        policy.mark(clean, true)
        val stored = reputation.get(clean)
        assertNotNull(stored)
        assertEquals(clean.e164, stored!!.number)
        assertEquals(SpamSource.USER, stored.source)
        assertTrue(stored.isSpam)
        assertEquals(100, stored.score)
    }

    @Test
    fun userNotSpamMarkOverridesBundledAndHeuristics() {
        val night = at(23, 0)
        bundledSpam += telemarketer.e164
        callsBefore(telemarketer, night, 3)
        policy.mark(telemarketer, false)
        val a = policy.assess(telemarketer, night)
        assertEquals(0, a.score)
        assertFalse(a.isSpam)
        assertEquals("user marked not spam", a.reason)
    }

    @Test
    fun bundledSpamScoresNinety() {
        bundledSpam += clean.e164
        val a = policy.assess(clean, midday)
        assertEquals(90, a.score)
        assertTrue(a.isSpam)
        assertEquals("bundled spam list", a.reason)
    }

    @Test
    fun threeRecentCallsAddForty() {
        callsBefore(clean, midday, 3)
        val a = policy.assess(clean, midday)
        assertEquals(40, a.score)
        assertFalse(a.isSpam)
        assertEquals("3+ calls in 10 min", a.reason)
    }

    @Test
    fun twoRecentCallsAddNothing() {
        callsBefore(clean, midday, 2)
        assertEquals(0, policy.assess(clean, midday).score)
    }

    @Test
    fun callsOlderThanTenMinutesDoNotCount() {
        callsBefore(clean, midday, 3, agoMillis = 11 * 60_000L)
        assertEquals(0, policy.assess(clean, midday).score)
    }

    @Test
    fun callExactlyAtWindowStartCounts() {
        callsBefore(clean, midday, 3, agoMillis = 10 * 60_000L)
        assertEquals(40, policy.assess(clean, midday).score)
    }

    @Test
    fun telemarketingSeriesAddsThirty() {
        val a = policy.assess(telemarketer, midday)
        assertEquals(30, a.score)
        assertFalse(a.isSpam)
        assertEquals("telemarketing number series", a.reason)
    }

    @Test
    fun telemarketingSeriesWithFrequentCallsReachesThreshold() {
        callsBefore(telemarketer, midday, 3)
        val a = policy.assess(telemarketer, midday)
        assertEquals(70, a.score)
        assertTrue(a.isSpam)
    }

    @Test
    fun nightCallAddsTen() {
        val a = policy.assess(clean, at(23, 30))
        assertEquals(10, a.score)
        assertFalse(a.isSpam)
        assertEquals("call between 22:00 and 07:00", a.reason)
    }

    @Test
    fun nightWindowRunsFromTenPmUpToSevenAm() {
        assertEquals(0, policy.assess(clean, at(21, 59)).score)
        assertEquals(10, policy.assess(clean, at(22, 0)).score)
        assertEquals(10, policy.assess(clean, at(6, 59)).score)
        assertEquals(0, policy.assess(clean, at(7, 0)).score)
    }

    @Test
    fun allHeuristicsTogetherScoreEightyAndAreSpam() {
        val night = at(23, 0)
        callsBefore(telemarketer, night, 3)
        val a = policy.assess(telemarketer, night)
        assertEquals(80, a.score)
        assertTrue(a.isSpam)
        assertEquals("3+ calls in 10 min; telemarketing number series; call between 22:00 and 07:00", a.reason)
    }

    @Test
    fun spamThresholdIsSeventy() {
        assertEquals(70, SpamPolicy.SPAM_THRESHOLD)
    }

    @Test
    fun clearMarkRestoresHeuristicScore() {
        policy.mark(telemarketer, false)
        assertEquals(0, policy.assess(telemarketer, midday).score)
        policy.clearMark(telemarketer)
        assertEquals(30, policy.assess(telemarketer, midday).score)
        assertNull(reputation.get(telemarketer))
    }

    @Test
    fun clearMarkKeepsLearnedEntries() {
        reputation.put(ReputationEntry(clean.e164, 50, false, SpamSource.LEARNED, 1L))
        policy.clearMark(clean)
        assertNotNull(reputation.get(clean))
    }

    @Test
    fun assessDoesNotStoreADecision() {
        policy.assess(telemarketer, midday)
        assertNull(reputation.get(telemarketer))
    }
}
