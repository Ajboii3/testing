package com.example.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.example.data.AlertStateHolder
import com.example.data.PaymentEvent
import com.example.data.SettingsManager
import com.example.service.AlertForwardWorker
import com.example.service.NotificationMonitorService
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val settingsManager = SettingsManager(context)

    // UI Input States for Settings Form
    var channelIdInput by mutableStateOf(settingsManager.channelId)
    var jwtTokenInput by mutableStateOf(settingsManager.jwtToken)
    var alertEndpointInput by mutableStateOf(settingsManager.alertEndpoint)
    var minAmountInput by mutableStateOf(settingsManager.minAmount.toString())
    var currencyInput by mutableStateOf(settingsManager.currency)
    var discordWebhookUrlInput by mutableStateOf(settingsManager.discordWebhookUrl)
    var discordEnabledInput by mutableStateOf(settingsManager.discordEnabled)

    // Observable status states
    var isNotificationAccessGranted by mutableStateOf(false)
    var isPostNotificationsGranted by mutableStateOf(false)
    var isBatteryOptIgnored by mutableStateOf(false)

    // Exposure of in-memory dynamic state lists
    val recentEvents: StateFlow<List<PaymentEvent>> = AlertStateHolder.recentEvents
    val isServiceActive: StateFlow<Boolean> = AlertStateHolder.isServiceActive
    val isListenerBound: StateFlow<Boolean> = AlertStateHolder.isListenerBound

    init {
        checkPermissions()
        // Sync state container with configuration value initially
        AlertStateHolder.setServiceActive(settingsManager.isServiceRunning)
    }

    fun checkPermissions() {
        isNotificationAccessGranted = checkNotificationAccess()
        isPostNotificationsGranted = checkPostNotificationsPermission()
        isBatteryOptIgnored = checkBatteryOptimizationsState()
    }

    private fun checkNotificationAccess(): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        if (!flat.isNullOrEmpty()) {
            val names = flat.split(":")
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null && cn.packageName == context.packageName) {
                    return true
                }
            }
        }
        return false
    }

    private fun checkPostNotificationsPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun checkBatteryOptimizationsState(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }

    fun saveSettings() {
        settingsManager.channelId = channelIdInput
        settingsManager.jwtToken = jwtTokenInput
        settingsManager.alertEndpoint = alertEndpointInput
        settingsManager.minAmount = minAmountInput.toDoubleOrNull() ?: 1.0
        settingsManager.currency = currencyInput
        settingsManager.discordWebhookUrl = discordWebhookUrlInput
        settingsManager.discordEnabled = discordEnabledInput
        Log.d("MainViewModel", "Settings Saved Successfully!")
    }

    fun toggleServiceState() {
        val isActive = isServiceActive.value
        val intent = Intent(context, NotificationMonitorService::class.java).apply {
            action = if (isActive) {
                NotificationMonitorService.ACTION_STOP_FOREGROUND
            } else {
                NotificationMonitorService.ACTION_START_FOREGROUND
            }
        }

        try {
            if (!isActive) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                AlertStateHolder.setServiceActive(true)
                settingsManager.isServiceRunning = true
            } else {
                context.startService(intent) // Triggers stop command inside startCommand
                AlertStateHolder.setServiceActive(false)
                settingsManager.isServiceRunning = false
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error starting/stopping monitor service", e)
        }
    }

    fun sendTestAlert(donor: String, sum: Double, systemApp: String) {
        viewModelScope.launch {
            // Create Simulation Event
            val textSimulation = "₹$sum received from $donor using $systemApp UPI"
            val event = PaymentEvent(
                senderName = donor,
                amount = sum,
                appName = systemApp,
                text = textSimulation
            )

            // 1. Show simulated alert on screen immediately
            AlertStateHolder.addEvent(event)

            // 2. Dispatch the simulated event directly to WorkManager for complete functional validation
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

            WorkManager.getInstance(context).enqueue(workRequest)
            Log.i("MainViewModel", "Dispatched test donation alert directly to WorkManager pipeline.")
        }
    }

    fun clearLogHistory() {
        AlertStateHolder.clearEvents()
    }
}
