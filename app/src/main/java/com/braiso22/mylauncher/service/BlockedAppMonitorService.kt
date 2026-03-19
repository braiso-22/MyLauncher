package com.braiso22.mylauncher.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.braiso22.mylauncher.OverlayActivity
import com.braiso22.mylauncher.R
import com.braiso22.mylauncher.domain.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BlockedAppMonitorService : Service() {

    companion object {
        private const val TAG = "BlockedAppMonitor"
        private const val CHANNEL_ID = "blocked_app_monitor"
        private const val NOTIFICATION_ID = 1001
        private const val CHECK_INTERVAL_MS = 3_000L

        fun start(context: Context) {
            Log.d(TAG, "Starting service...")
            context.startForegroundService(
                Intent(context, BlockedAppMonitorService::class.java)
            )
        }

        fun stop(context: Context) {
            Log.d(TAG, "Stopping service...")
            context.stopService(
                Intent(context, BlockedAppMonitorService::class.java)
            )
        }
    }

    private lateinit var repository: AppRepository
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var overlayLaunched = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        repository = AppRepository.getInstance(applicationContext)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        startMonitorLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.monitor_service_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.monitor_service_desc)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    private fun startMonitorLoop() {
        scope.launch {
            delay(2_000) // Wait for DataStore to load
            Log.d(TAG, "Monitor loop started")
            while (true) {
                try {
                    check()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in monitor check", e)
                }
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    private fun check() {
        val trackedPkg = repository.blockedAppOpenedPkg.value
        val openedAt = repository.blockedAppOpenedAt.value
        val blockedApps = repository.blocked.value
        val blockTimes = repository.blockTimes.value

        // Always detect the current foreground app
        val foreground = ForegroundAppDetector.getForegroundPackage(applicationContext)
        Log.d(TAG, "Check: tracked=$trackedPkg, openedAt=$openedAt, foreground=$foreground, blocked=$blockedApps")

        // 1. If a blocked app is in the foreground but NOT tracked (not opened via launcher "Enter" flow),
        // show the overlay immediately with the NOT_FROM_LAUNCHER reason.
        if (foreground != null && foreground in blockedApps && (trackedPkg != foreground || openedAt == 0L)) {
            Log.d(TAG, "Detected blocked app $foreground in foreground WITHOUT launcher tracking. Blocking.")
            if (!overlayLaunched) {
                overlayLaunched = true
                OverlayActivity.launch(applicationContext, OverlayActivity.Reason.NOT_FROM_LAUNCHER)
            }
            return
        }

        // 2. If nothing is tracked, or the tracked app is no longer blocked, just reset and return
        if (trackedPkg == null || openedAt == 0L) {
            overlayLaunched = false
            return
        }

        if (trackedPkg !in blockedApps) {
            Log.d(TAG, "Tracked app $trackedPkg no longer blocked, clearing")
            repository.clearBlockedAppOpened()
            overlayLaunched = false
            return
        }

        // 3. Check if time is up for the tracked app
        val allowedMinutes = blockTimes[trackedPkg] ?: 5
        val allowedMs = allowedMinutes * 60_000L
        val elapsed = System.currentTimeMillis() - openedAt

        if (elapsed >= allowedMs) {
            Log.d(TAG, "Time up for $trackedPkg! Foreground: $foreground")
            if (foreground == trackedPkg) {
                if (!overlayLaunched) {
                    Log.d(TAG, "Launching OverlayActivity (TIME_UP) for $trackedPkg")
                    overlayLaunched = true
                    OverlayActivity.launch(applicationContext, OverlayActivity.Reason.TIME_UP)
                }
            } else {
                Log.d(TAG, "User left $trackedPkg (time was up), clearing")
                repository.clearBlockedAppOpened()
                overlayLaunched = false
            }
            return
        }

        // 4. If the user leaves the tracked app before time is up, clear tracking.
        // This forces them to go through the launcher again to re-enter.
        if (foreground != null && foreground != trackedPkg && foreground != packageName) {
            // packageName is our own app. We don't want to clear if we are in the launcher or overlay.
            // But we need to check if 'foreground' is a launcher activity or something.
            // Simplified: if it's not the tracked app and not our launcher, they left.
            Log.d(TAG, "User left $trackedPkg (switched to $foreground), clearing tracking")
            repository.clearBlockedAppOpened()
            overlayLaunched = false
        }
    }
}
