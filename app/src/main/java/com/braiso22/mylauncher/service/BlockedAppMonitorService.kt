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
        val blockedApps = repository.blocked.value
        val unlockExpiries = repository.unlockExpiries.value
        val now = System.currentTimeMillis()

        // Clean up expired unlocks periodically
        repository.clearExpiredUnlocks()

        // Detect the current foreground app
        val foreground = ForegroundAppDetector.getForegroundPackage(applicationContext)
        Log.d(TAG, "Check: foreground=$foreground, blocked=$blockedApps, unlocks=$unlockExpiries")

        // If the foreground app is not blocked, nothing to do
        if (foreground == null || foreground !in blockedApps) {
            overlayLaunched = false
            return
        }

        // Foreground app IS blocked — check if it has a valid unlock
        val expiry = unlockExpiries[foreground]

        if (expiry == null || expiry == 0L) {
            // App was opened without going through the launcher unlock flow
            Log.d(TAG, "Blocked app $foreground in foreground WITHOUT unlock. Blocking.")
            if (!overlayLaunched) {
                overlayLaunched = true
                OverlayActivity.launch(applicationContext, OverlayActivity.Reason.NOT_FROM_LAUNCHER, foreground)
            }
            return
        }

        if (now >= expiry) {
            // Unlock has expired — time is up
            Log.d(TAG, "Time up for $foreground! Showing overlay.")
            if (!overlayLaunched) {
                overlayLaunched = true
                OverlayActivity.launch(applicationContext, OverlayActivity.Reason.TIME_UP, foreground)
            }
            return
        }

        // App is unlocked and time hasn't expired — allow usage
        Log.d(TAG, "App $foreground unlocked until $expiry (${(expiry - now) / 1000}s remaining)")
        overlayLaunched = false
    }
}
