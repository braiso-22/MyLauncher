package com.braiso22.mylauncher.domain.timebank

import java.time.Duration

/**
 * One step of the abstinence → earned-time curve: reaching [minAbstinence] of continuous
 * abstinence from blocked apps grants [earned] time to spend on them.
 */
data class EarningTier(val minAbstinence: Duration, val earned: Duration)

/**
 * The rules that convert continuous abstinence into spendable time.
 *
 * Pure domain type — no Android dependencies. [tiers] must be sorted ascending by
 * [EarningTier.minAbstinence]; [earnedFor] returns the reward of the highest tier whose
 * threshold is met, so gaps below the first threshold earn nothing.
 */
data class TimeBankPolicy(
    val tiers: List<EarningTier>,
    val maxBalance: Duration,
) {
    /** Time earned for a given continuous [abstinence] gap. Never above the top tier. */
    fun earnedFor(abstinence: Duration): Duration {
        var result = Duration.ZERO
        for (tier in tiers) {
            if (abstinence >= tier.minAbstinence) result = tier.earned else break
        }
        return result
    }

    companion object {
        /**
         * Curve agreed with the user (see ADR/FEATURE_ABSTINENCE_TIME_BANK.md):
         * < 30 min → 0, [30 min, 1 h) → 1 min, ≥ 1 h → 5, ≥ 2 h → 7, ≥ 4 h → 15,
         * ≥ 6 h → 25, ≥ 8 h → 40, ≥ 10 h → 50, ≥ 12 h → 60. Balance capped at 1 h.
         */
        val DEFAULT = TimeBankPolicy(
            tiers = listOf(
                EarningTier(Duration.ofMinutes(30), Duration.ofMinutes(1)),
                EarningTier(Duration.ofHours(1), Duration.ofMinutes(5)),
                EarningTier(Duration.ofHours(2), Duration.ofMinutes(7)),
                EarningTier(Duration.ofHours(4), Duration.ofMinutes(15)),
                EarningTier(Duration.ofHours(6), Duration.ofMinutes(25)),
                EarningTier(Duration.ofHours(8), Duration.ofMinutes(40)),
                EarningTier(Duration.ofHours(10), Duration.ofMinutes(50)),
                EarningTier(Duration.ofHours(12), Duration.ofMinutes(60)),
            ),
            maxBalance = Duration.ofHours(1),
        )
    }
}
