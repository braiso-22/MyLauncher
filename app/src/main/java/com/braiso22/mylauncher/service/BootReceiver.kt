package com.braiso22.mylauncher.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Starts the BlockedAppMonitorService when the device boots up,
 * ensuring that blocked app monitoring is always active.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed, starting BlockedAppMonitorService")
            BlockedAppMonitorService.start(context)
        }
    }
}

