package com.braiso22.mylauncher.domain.timebank

import java.time.Duration
import java.time.Instant

/**
 * A period the user spent inside a blocked app, derived from UsageStats events.
 * [end] is null while the app is still in the foreground.
 */
data class BlockedSession(val start: Instant, val end: Instant?) {
    val isOpen: Boolean get() = end == null
}

/**
 * Checkpoint of the time bank: at [lastActivityEnd] (the end of the last processed
 * blocked session) the spendable [balance] was this value. Persisted so we only ever
 * scan the short UsageStats window since the checkpoint, never all of history.
 */
data class TimeBankAnchor(val lastActivityEnd: Instant, val balance: Duration) {
    companion object {
        fun initial(at: Instant): TimeBankAnchor = TimeBankAnchor(at, Duration.ZERO)
    }
}

/**
 * Pure core of the abstinence time bank. Every value is derived from the anchor plus the
 * blocked-app sessions since it — no live counters. All functions take the current time
 * as a parameter so they stay testable without Android.
 */
object TimeBank {

    /**
     * Folds every CLOSED blocked session that ended after the anchor into a new anchor.
     * At each session start it deposits the time earned for the preceding abstinence gap
     * (capped at [TimeBankPolicy.maxBalance]), then subtracts the session's real duration
     * so leftover time is preserved when the user leaves early.
     *
     * Idempotent: sessions ending at or before [anchor]'s checkpoint are ignored, so
     * re-running with overlapping UsageStats data yields the same result. Open sessions
     * are left untouched — they only affect the live balance, not the persisted anchor.
     */
    fun reconcile(
        anchor: TimeBankAnchor,
        sessions: List<BlockedSession>,
        policy: TimeBankPolicy,
    ): TimeBankAnchor {
        var current = anchor
        val closed = sessions
            .filter { it.end != null && it.end > anchor.lastActivityEnd }
            .sortedBy { it.start }
        for (session in closed) {
            val end = session.end!!
            // Clip to the checkpoint so a session straddling it is never double-counted.
            val start = maxOf(session.start, current.lastActivityEnd)
            val deposited = deposit(current.balance, current.lastActivityEnd, start, policy)
            val usage = Duration.between(start, end).coerceAtLeast(Duration.ZERO)
            val remaining = (deposited - usage).coerceAtLeast(Duration.ZERO)
            current = TimeBankAnchor(
                lastActivityEnd = maxOf(end, current.lastActivityEnd),
                balance = remaining,
            )
        }
        return current
    }

    /**
     * Spendable balance right now. With no [openSession] this is just the anchor balance;
     * while the user is inside a blocked app it applies that session's deposit and the
     * time already spent. Never negative, never above [TimeBankPolicy.maxBalance].
     */
    fun liveBalance(
        anchor: TimeBankAnchor,
        openSession: BlockedSession?,
        now: Instant,
        policy: TimeBankPolicy,
    ): Duration {
        if (openSession == null) return anchor.balance.coerceAtMost(policy.maxBalance)
        val start = maxOf(openSession.start, anchor.lastActivityEnd)
        val deposited = deposit(anchor.balance, anchor.lastActivityEnd, start, policy)
        val usage = Duration.between(start, now).coerceAtLeast(Duration.ZERO)
        return (deposited - usage).coerceAtLeast(Duration.ZERO)
    }

    /**
     * How much a fresh unlock right now would add to the balance — for the
     * "+X min if you unlock now" hint. Capped so the balance can't exceed the max.
     */
    fun previewEarnings(anchor: TimeBankAnchor, now: Instant, policy: TimeBankPolicy): Duration {
        val capped = deposit(anchor.balance, anchor.lastActivityEnd, now, policy)
        return (capped - anchor.balance).coerceAtLeast(Duration.ZERO)
    }

    /** Balance after depositing the reward for the abstinence gap [from]..[to], capped at max. */
    private fun deposit(
        balance: Duration,
        from: Instant,
        to: Instant,
        policy: TimeBankPolicy,
    ): Duration {
        val gap = Duration.between(from, to).coerceAtLeast(Duration.ZERO)
        return (balance + policy.earnedFor(gap)).coerceAtMost(policy.maxBalance)
    }
}
