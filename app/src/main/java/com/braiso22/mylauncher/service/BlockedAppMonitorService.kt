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
        private const val CHECK_INTERVAL_MS = 5_000L

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
            "Monitor de apps bloqueadas",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Monitorea si una app bloqueada está en uso"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("MyLauncher activo")
            .setContentText("Monitoreando apps bloqueadas")
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

        Log.d(TAG, "Check: tracked=$trackedPkg, openedAt=$openedAt, blocked=$blockedApps, times=$blockTimes")

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

        val allowedMinutes = blockTimes[trackedPkg] ?: 5
        val allowedMs = allowedMinutes * 60_000L
        val elapsed = System.currentTimeMillis() - openedAt

        Log.d(TAG, "Elapsed: ${elapsed / 1000}s, allowed: ${allowedMs / 1000}s")

        if (elapsed < allowedMs) return

        // Time is up
        val foreground = ForegroundAppDetector.getForegroundPackage(applicationContext)
        Log.d(TAG, "Time up! Foreground: $foreground, tracked: $trackedPkg")

        if (foreground == trackedPkg) {
            if (!overlayLaunched) {
                Log.d(TAG, "Launching OverlayActivity for $trackedPkg")
                overlayLaunched = true
                OverlayActivity.launch(applicationContext)
            }
        } else {
            Log.d(TAG, "User left $trackedPkg (now on $foreground), clearing")
            repository.clearBlockedAppOpened()
            overlayLaunched = false
        }
    }
}



