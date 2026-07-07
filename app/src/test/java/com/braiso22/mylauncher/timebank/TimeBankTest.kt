package com.braiso22.mylauncher.timebank

import com.braiso22.mylauncher.domain.timebank.BlockedSession
import com.braiso22.mylauncher.domain.timebank.TimeBank
import com.braiso22.mylauncher.domain.timebank.TimeBankAnchor
import com.braiso22.mylauncher.domain.timebank.TimeBankPolicy
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class TimeBankTest {

    private val policy = TimeBankPolicy.DEFAULT
    private val t0: Instant = Instant.parse("2026-07-06T08:00:00Z")

    private fun at(minutes: Long): Instant = t0.plus(Duration.ofMinutes(minutes))
    private fun anchor(minutes: Long, balance: Duration) = TimeBankAnchor(at(minutes), balance)

    // --- reconcile ---------------------------------------------------------

    @Test
    fun `no sessions leaves the anchor untouched`() {
        val a = anchor(0, Duration.ofMinutes(10))
        assertEquals(a, TimeBank.reconcile(a, emptyList(), policy))
    }

    @Test
    fun `a session deposits for the gap then subtracts its duration`() {
        // 2h abstinence → deposit 7 min; session lasts 3 min → 4 min left.
        val start = at(120)
        val end = start.plus(Duration.ofMinutes(3))
        val result = TimeBank.reconcile(
            TimeBankAnchor.initial(t0),
            listOf(BlockedSession(start, end)),
            policy,
        )
        assertEquals(Duration.ofMinutes(4), result.balance)
        assertEquals(end, result.lastActivityEnd)
    }

    @Test
    fun `leaving early preserves the unspent balance`() {
        // 4h abstinence → 15 min deposited; only 2 min used → 13 min banked.
        val start = at(240)
        val end = start.plus(Duration.ofMinutes(2))
        val result = TimeBank.reconcile(TimeBankAnchor.initial(t0), listOf(BlockedSession(start, end)), policy)
        assertEquals(Duration.ofMinutes(13), result.balance)
    }

    @Test
    fun `overspending a session cannot drive the balance below zero`() {
        // 30 min abstinence → 1 min deposited; session lasts 10 min → clamped to 0.
        val start = at(30)
        val end = start.plus(Duration.ofMinutes(10))
        val result = TimeBank.reconcile(TimeBankAnchor.initial(t0), listOf(BlockedSession(start, end)), policy)
        assertEquals(Duration.ZERO, result.balance)
    }

    @Test
    fun `balance is capped at the policy maximum`() {
        // Start already near the cap; a 12h gap would add 60 min but the cap is 1h.
        val start = at(12 * 60)
        val end = start.plus(Duration.ofMinutes(1))
        val result = TimeBank.reconcile(
            anchor(0, Duration.ofMinutes(30)),
            listOf(BlockedSession(start, end)),
            policy,
        )
        assertEquals(Duration.ofHours(1).minusMinutes(1), result.balance)
    }

    @Test
    fun `consecutive sessions measure each gap from the previous session end`() {
        // First: 1h gap → 5 min, use 1 → 4 left, ends at 61.
        val s1 = BlockedSession(at(60), at(61))
        // Second: gap from 61 to 121 = 1h → +5 (capped ok) → 9, use 2 → 7 left.
        val s2 = BlockedSession(at(121), at(123))
        val result = TimeBank.reconcile(TimeBankAnchor.initial(t0), listOf(s1, s2), policy)
        assertEquals(Duration.ofMinutes(7), result.balance)
        assertEquals(at(123), result.lastActivityEnd)
    }

    @Test
    fun `reconcile is idempotent when re-run over already-processed sessions`() {
        val s1 = BlockedSession(at(60), at(61))
        val s2 = BlockedSession(at(121), at(123))
        val once = TimeBank.reconcile(TimeBankAnchor.initial(t0), listOf(s1, s2), policy)
        val twice = TimeBank.reconcile(once, listOf(s1, s2), policy)
        assertEquals(once, twice)
    }

    @Test
    fun `open sessions are ignored by reconcile`() {
        val open = BlockedSession(at(120), null)
        val a = anchor(0, Duration.ofMinutes(10))
        assertEquals(a, TimeBank.reconcile(a, listOf(open), policy))
    }

    // --- liveBalance -------------------------------------------------------

    @Test
    fun `live balance without an open session is the anchor balance`() {
        val a = anchor(0, Duration.ofMinutes(12))
        assertEquals(Duration.ofMinutes(12), TimeBank.liveBalance(a, null, at(500), policy))
    }

    @Test
    fun `live balance during an open session applies deposit minus elapsed`() {
        // 2h gap → +7 on top of 0; 3 min elapsed → 4 min remaining right now.
        val open = BlockedSession(at(120), null)
        val now = at(123)
        assertEquals(
            Duration.ofMinutes(4),
            TimeBank.liveBalance(TimeBankAnchor.initial(t0), open, now, policy),
        )
    }

    @Test
    fun `live balance never goes negative once the granted time is exhausted`() {
        val open = BlockedSession(at(30), null) // 30 min gap → only 1 min granted
        val now = at(45) // 15 min elapsed
        assertEquals(Duration.ZERO, TimeBank.liveBalance(TimeBankAnchor.initial(t0), open, now, policy))
    }

    // --- previewEarnings ---------------------------------------------------

    @Test
    fun `preview shows what a fresh unlock would add`() {
        val now = at(120) // 2h abstinence → 7 min
        assertEquals(Duration.ofMinutes(7), TimeBank.previewEarnings(TimeBankAnchor.initial(t0), now, policy))
    }

    @Test
    fun `preview is capped by remaining room under the max balance`() {
        // Balance already at 58 min; 12h gap would add 60 but only 2 min fit under the 1h cap.
        val a = anchor(0, Duration.ofHours(1).minusMinutes(2))
        val now = at(12 * 60)
        assertEquals(Duration.ofMinutes(2), TimeBank.previewEarnings(a, now, policy))
    }

    @Test
    fun `preview is zero below the first earning tier`() {
        val now = at(20) // 20 min abstinence
        assertEquals(Duration.ZERO, TimeBank.previewEarnings(TimeBankAnchor.initial(t0), now, policy))
    }
}
