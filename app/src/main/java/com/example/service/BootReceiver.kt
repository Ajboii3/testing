package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.SettingsManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("BootReceiver", "Device reboot completed. Checking saved service state...")
            val settings = SettingsManager(context.applicationContext)
            
            if (settings.isServiceRunning) {
                Log.w("BootReceiver", "Service was configured to run. Bootstrapping foreground listener...")
                val serviceIntent = Intent(context, NotificationMonitorService::class.java).apply {
                    action = NotificationMonitorService.ACTION_START_FOREGROUND
                }
                
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to relaunch monitor service on boot", e)
                }
            }
        }
    }
}
