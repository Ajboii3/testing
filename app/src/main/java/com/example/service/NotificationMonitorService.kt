package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.example.MainActivity
import com.example.data.AlertStateHolder
import com.example.data.SettingsManager
import com.example.utils.PaymentParser
import java.util.concurrent.TimeUnit

class NotificationMonitorService : NotificationListenerService() {

    private lateinit var settingsManager: SettingsManager

    companion object {
        private const val TAG = "NotificationMonitor"
        private const val CHANNEL_ID = "donation_alert_foreground_channel"
        private const val NOTIFICATION_ID = 4821

        const val ACTION_START_FOREGROUND = "ACTION_START_FOREGROUND"
        const val ACTION_STOP_FOREGROUND = "ACTION_STOP_FOREGROUND"
    }

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(applicationContext)
        Log.d(TAG, "NotificationMonitorService Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand Action: $action")
        
        when (action) {
            ACTION_START_FOREGROUND -> {
                settingsManager.isServiceRunning = true
                AlertStateHolder.setServiceActive(true)
                startServiceInForeground()
            }
            ACTION_STOP_FOREGROUND -> {
                settingsManager.isServiceRunning = false
                AlertStateHolder.setServiceActive(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        }
        return START_STICKY
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected successfully")
        AlertStateHolder.setListenerBound(true)

        // Automatically restore foreground state if saved settings request it
        if (settingsManager.isServiceRunning) {
            Log.d(TAG, "Restoring active foreground monitor from configuration")
            AlertStateHolder.setServiceActive(true)
            startServiceInForeground()
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Notification listener disconnected")
        AlertStateHolder.setListenerBound(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        
        // Return early if service monitoring is toggled off by user
        if (!settingsManager.isServiceRunning) {
            return
        }

        val sbnNonNull = sbn ?: return
        val packageName = sbnNonNull.packageName ?: ""
        
        // Extract notification text context
        val extras = sbnNonNull.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        val fullText = when {
            bigText.isNotEmpty() -> bigText
            text.isNotEmpty() -> text
            else -> title
        }

        if (fullText.isEmpty()) return

        // Filter and Parse
        val event = PaymentParser.parse(fullText, packageName)
        if (event != null) {
            Log.i(TAG, "Valid UPI payment detected! Emitting: $event")
            
            // 1. Log to active UI memory pipeline
            AlertStateHolder.addEvent(event)

            // 2. Schedule async Alert Dispatcher via WorkManager (enables offline fallback-retry)
            val data = Data.Builder()
                .putString("sender", event.senderName)
                .putDouble("amount", event.amount)
                .putString("appName", event.appName)
                .putString("text", event.text)
                .putLong("timestamp", event.timestamp)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<AlertForwardWorker>()
                .setInputData(data)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(applicationContext).enqueue(workRequest)
        }
    }

    private fun startServiceInForeground() {
        createNotificationChannel()

        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Custom M3 styled persistent status notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("UPI Payment Monitor Active")
            .setContentText("Listening for incoming donation announcements in the background...")
            .setSmallIcon(android.R.drawable.stat_sys_phone_call) // Safe standard fallback icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        try {
            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "Service transitioned to Foreground successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed starting service in foreground", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Payment Monitor Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the donation alert listener alive to scan payment notifications."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
