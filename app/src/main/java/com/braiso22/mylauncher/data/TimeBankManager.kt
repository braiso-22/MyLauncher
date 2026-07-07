package com.braiso22.mylauncher.data

import android.content.Context
import android.util.Log
import com.braiso22.mylauncher.domain.AppRepository
import com.braiso22.mylauncher.domain.timebank.BlockedSession
import com.braiso22.mylauncher.domain.timebank.TimeBank
import com.braiso22.mylauncher.domain.timebank.TimeBankAnchor
import com.braiso22.mylauncher.domain.timebank.TimeBankPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration
import java.time.Instant

/** Snapshot of the time bank for the UI at a given moment. */
data class TimeBankSnapshot(
    val balance: Duration,
    /** What a fresh unlock right now would add to the balance (0 while inside a blocked app). */
    val earnableNow: Duration,
    /** Since when the user has been abstinent, or null while a blocked app is in the foreground. */
    val abstinentSince: Instant?,
)

/**
 * Impure shell that wires the pure [TimeBank] domain to Android: reads the blocked set from
 * [AppRepository], derives sessions from [BlockedSessionSource], reconciles the persisted
 * anchor via [TimeBankRepository], and exposes the result as a [TimeBankSnapshot].
 *
 * Reconciliation is on-demand ([refresh]) rather than a live loop — the anchor advances each
 * time so the UsageStats window queried stays short.
 */
class TimeBankManager private constructor(context: Context) {

    companion object {
        private const val TAG = "TimeBankManager"

        @Volatile
        private var INSTANCE: TimeBankManager? = null

        fun getInstance(context: Context): TimeBankManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: TimeBankManager(context.applicationContext).also { INSTANCE = it }
            }
    }

    private val appContext = context.applicationContext
    private val appRepository = AppRepository.getInstance(appContext)
    private val timeBankRepository = TimeBankRepository.getInstance(appContext)
    private val policy = TimeBankPolicy.DEFAULT

    private val _snapshot = MutableStateFlow(
        TimeBankSnapshot(Duration.ZERO, Duration.ZERO, null),
    )
    val snapshot: StateFlow<TimeBankSnapshot> = _snapshot.asStateFlow()

    /**
     * Reconciles closed blocked-app sessions into the persisted anchor and recomputes the
     * live snapshot. Call on launcher open and before an unlock. Safe to call repeatedly —
     * reconciliation is idempotent.
     */
    suspend fun refresh(now: Instant = Instant.now()): TimeBankSnapshot {
        val blockedPackages = appRepository.blocked.value
        val anchor = timeBankRepository.anchor.value ?: TimeBankAnchor.initial(now).also {
            timeBankRepository.save(it)
        }

        val sessions = BlockedSessionSource.sessionsSince(
            context = appContext,
            blockedPackages = blockedPackages,
            since = anchor.lastActivityEnd,
            now = now,
        )

        val reconciled = TimeBank.reconcile(anchor, sessions, policy)
        if (reconciled != anchor) timeBankRepository.save(reconciled)

        val openSession: BlockedSession? = sessions.lastOrNull { it.isOpen }
        val balance = TimeBank.liveBalance(reconciled, openSession, now, policy)
        val earnableNow =
            if (openSession == null) TimeBank.previewEarnings(reconciled, now, policy)
            else Duration.ZERO

        val snapshot = TimeBankSnapshot(
            balance = balance,
            earnableNow = earnableNow,
            abstinentSince = if (openSession == null) reconciled.lastActivityEnd else null,
        )
        Log.d(TAG, "refresh: $snapshot (sessions=${sessions.size}, anchor=$reconciled)")
        _snapshot.value = snapshot
        return snapshot
    }
}
