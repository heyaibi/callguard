package com.callguard.calllog

import com.callguard.data.CallLogEntry
import com.callguard.data.Direction
import com.callguard.identity.SourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RecentCallsTest {
    private val t0 = 1_700_000_000_000L

    private fun guard(
        number: String,
        at: Long,
        outcome: String = "ALLOWED",
        name: String? = null,
        direction: Direction = Direction.INCOMING,
    ) = CallLogEntry(
        id = at,
        number = number,
        name = name,
        source = SourceKind.NONE,
        direction = direction,
        outcome = outcome,
        timestamp = at,
    )

    @Test
    fun systemCallJoinsGuardRecordWithinTheWindow() {
        val system = listOf(SystemCall("98765 43210", "Amelia", CallKind.MISSED, t0, 0))
        val rows = mergeRecentCalls(system, listOf(guard("+919876543210", t0 + 20_000, outcome = "SILENCED")))

        assertEquals(1, rows.size)
        assertEquals("+919876543210", rows[0].number)
        assertEquals("Amelia", rows[0].name)
        assertEquals(CallKind.MISSED, rows[0].kind)
        assertEquals("SILENCED", rows[0].guard?.outcome)
    }

    @Test
    fun guardRecordOutsideTheWindowStaysItsOwnRow() {
        val system = listOf(SystemCall("+919876543210", null, CallKind.INCOMING, t0, 30))
        val rows = mergeRecentCalls(system, listOf(guard("+919876543210", t0 + 61_000)))

        assertEquals(2, rows.size)
        assertNotNull(rows[0].guard)
        assertNull(rows[1].guard)
        assertEquals(30L, rows[1].durationSec)
    }

    @Test
    fun eachGuardRecordJoinsAtMostOneSystemCall() {
        val system =
            listOf(
                SystemCall("+919876543210", null, CallKind.INCOMING, t0, 0),
                SystemCall("+919876543210", null, CallKind.INCOMING, t0 + 5_000, 0),
            )
        val rows = mergeRecentCalls(system, listOf(guard("+919876543210", t0 + 1_000)))

        assertEquals(2, rows.size)
        assertEquals(1, rows.count { it.guard != null })
    }

    @Test
    fun unmatchedBlockedRecordBecomesABlockedRow() {
        val rows = mergeRecentCalls(emptyList(), listOf(guard("+919000000001", t0, outcome = "BLOCKED")))

        assertEquals(1, rows.size)
        assertEquals(CallKind.BLOCKED, rows.single().kind)
        assertNotNull(rows.single().guard)
    }

    @Test
    fun rowsAreNewestFirst() {
        val system =
            listOf(
                SystemCall("+919000000001", null, CallKind.OUTGOING, t0, 0),
                SystemCall("+919000000002", null, CallKind.OUTGOING, t0 + 500_000, 0),
            )
        val rows = mergeRecentCalls(system, listOf(guard("+919000000003", t0 + 900_000)))

        assertEquals(listOf(t0 + 900_000, t0 + 500_000, t0), rows.map { it.timestamp })
    }
}
