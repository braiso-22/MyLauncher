package com.braiso22.mylauncher.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log

object ForegroundAppDetector {

    private const val TAG = "ForegroundAppDetector"

    /**
     * Returns the package name of the app currently in the foreground,
     * or null if it cannot be determined.
     * Requires PACKAGE_USAGE_STATS permission granted via Settings.
     */
    fun getForegroundPackage(context: Context): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        if (usm == null) {
            Log.w(TAG, "UsageStatsManager not available")
            return null
        }

        // Strategy 1: queryEvents with a reasonable window (last 60 seconds)
        // to find the most recent ACTIVITY_RESUMED event
        val now = System.currentTimeMillis()
        try {
            val events = usm.queryEvents(now - 60_000, now)
            var foregroundPackage: String? = null
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    foregroundPackage = event.packageName
                }
            }
            if (foregroundPackage != null) {
                Log.d(TAG, "Detected foreground via events: $foregroundPackage")
                return foregroundPackage
            }
        } catch (e: Exception) {
            Log.w(TAG, "queryEvents failed", e)
        }

        // Strategy 2: Fallback to queryUsageStats
        // Find the app with the most recent lastTimeUsed in the last 60 seconds
        try {
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 60_000,
                now,
            )
            if (!stats.isNullOrEmpty()) {
                val recent = stats
                    .filter { it.lastTimeUsed > 0 }
                    .maxByOrNull { it.lastTimeUsed }
                if (recent != null) {
                    Log.d(TAG, "Detected foreground via stats: ${recent.packageName}")
                    return recent.packageName
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "queryUsageStats failed", e)
        }

        Log.w(TAG, "Could not detect foreground app")
        return null
    }
}


