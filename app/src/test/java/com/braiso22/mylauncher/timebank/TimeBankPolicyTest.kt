package com.braiso22.mylauncher.timebank

import com.braiso22.mylauncher.domain.timebank.TimeBankPolicy
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class TimeBankPolicyTest {

    private val policy = TimeBankPolicy.DEFAULT

    @Test
    fun `below 30 minutes earns nothing`() {
        assertEquals(Duration.ZERO, policy.earnedFor(Duration.ofMinutes(0)))
        assertEquals(Duration.ZERO, policy.earnedFor(Duration.ofMinutes(29)))
    }

    @Test
    fun `30 minutes up to 1 hour earns 1 minute`() {
        assertEquals(Duration.ofMinutes(1), policy.earnedFor(Duration.ofMinutes(30)))
        assertEquals(Duration.ofMinutes(1), policy.earnedFor(Duration.ofMinutes(59)))
    }

    @Test
    fun `each documented tier maps to its reward`() {
        assertEquals(Duration.ofMinutes(5), policy.earnedFor(Duration.ofHours(1)))
        assertEquals(Duration.ofMinutes(7), policy.earnedFor(Duration.ofHours(2)))
        assertEquals(Duration.ofMinutes(15), policy.earnedFor(Duration.ofHours(4)))
        assertEquals(Duration.ofMinutes(25), policy.earnedFor(Duration.ofHours(6)))
        assertEquals(Duration.ofMinutes(40), policy.earnedFor(Duration.ofHours(8)))
        assertEquals(Duration.ofMinutes(50), policy.earnedFor(Duration.ofHours(10)))
        assertEquals(Duration.ofMinutes(60), policy.earnedFor(Duration.ofHours(12)))
    }

    @Test
    fun `abstinence beyond top tier stays at top reward`() {
        assertEquals(Duration.ofMinutes(60), policy.earnedFor(Duration.ofHours(48)))
    }

    @Test
    fun `reward uses the highest tier reached not an intermediate one`() {
        // 3h30m clears the 2h tier but not the 4h one → 7 min.
        assertEquals(Duration.ofMinutes(7), policy.earnedFor(Duration.ofHours(3).plusMinutes(30)))
    }
}
