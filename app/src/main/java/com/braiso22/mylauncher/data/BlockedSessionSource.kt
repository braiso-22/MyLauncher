package com.braiso22.mylauncher.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import com.braiso22.mylauncher.domain.timebank.BlockedSession
import java.time.Instant

/**
 * Derives [BlockedSession]s from the system UsageStats event stream. A session spans any
 * continuous period in which *some* blocked app is in the foreground, so switching directly
 * between two blocked apps is one session (no abstinence in between). Requires the
 * PACKAGE_USAGE_STATS permission (already requested by MainActivity).
 */
object BlockedSessionSource {

    private const val TAG = "BlockedSessionSource"

    /**
     * Blocked-app sessions overlapping the window [since]..[now]. The last session has a
     * null end if a blocked app is still in the foreground at [now]. Returns an empty list
     * if usage data is unavailable.
     */
    fun sessionsSince(
        context: Context,
        blockedPackages: Set<String>,
        since: Instant,
        now: Instant,
    ): List<BlockedSession> {
        if (blockedPackages.isEmpty() || !since.isBefore(now)) return emptyList()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        if (usm == null) {
            Log.w(TAG, "UsageStatsManager not available")
            return emptyList()
        }

        return try {
            val events = usm.queryEvents(since.toEpochMilli(), now.toEpochMilli())
            val sessions = mutableListOf<BlockedSession>()
            var currentForeground: String? = null
            var sessionStart: Instant? = null
            val event = UsageEvents.Event()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val at = Instant.ofEpochMilli(event.timeStamp)
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        currentForeground = event.packageName
                        if (event.packageName in blockedPackages && sessionStart == null) {
                            sessionStart = at
                        }
                    }

                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        // Only a pause of the current blocked foreground app closes the session.
                        val start = sessionStart
                        if (start != null &&
                            event.packageName == currentForeground &&
                            event.packageName in blockedPackages
                        ) {
                            sessions += BlockedSession(start, at)
                            sessionStart = null
                        }
                        if (event.packageName == currentForeground) currentForeground = null
                    }
                }
            }

            // A blocked app still in the foreground at `now` → open session.
            sessionStart?.let { sessions += BlockedSession(it, null) }
            sessions
        } catch (e: Exception) {
            Log.w(TAG, "queryEvents failed", e)
            emptyList()
        }
    }
}
