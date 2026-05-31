package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class SettingsManager(private val context: Context) {

    private val sharedPrefs: SharedPreferences by lazy {
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

            EncryptedSharedPreferences.create(
                "donation_alert_secure_prefs",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("SettingsManager", "Error creating EncryptedSharedPreferences, falling back to standard prefs", e)
            context.getSharedPreferences("donation_alert_fallback_prefs", Context.MODE_PRIVATE)
        }
    }

    companion object {
        private const val KEY_CHANNEL_ID = "channel_id"
        private const val KEY_JWT_TOKEN = "jwt_token"
        private const val KEY_ALERT_ENDPOINT = "alert_endpoint"
        private const val KEY_MIN_AMOUNT = "min_amount"
        private const val KEY_CURRENCY = "currency"
        private const val KEY_DISCORD_WEBHOOK_URL = "discord_webhook_url"
        private const val KEY_DISCORD_ENABLED = "discord_enabled"
        private const val KEY_SERVICE_RUNNING = "service_running"
    }

    var channelId: String
        get() = sharedPrefs.getString(KEY_CHANNEL_ID, "") ?: ""
        set(value) = sharedPrefs.edit().putString(KEY_CHANNEL_ID, value.trim()).apply()

    var jwtToken: String
        get() = sharedPrefs.getString(KEY_JWT_TOKEN, "") ?: ""
        set(value) = sharedPrefs.edit().putString(KEY_JWT_TOKEN, value.trim()).apply()

    var alertEndpoint: String
        get() = sharedPrefs.getString(KEY_ALERT_ENDPOINT, "https://api.streamelements.com/v2/activities/alerts") ?: "https://api.streamelements.com/v2/activities/alerts"
        set(value) = sharedPrefs.edit().putString(KEY_ALERT_ENDPOINT, value.trim()).apply()

    var minAmount: Double
        get() {
            val str = sharedPrefs.getString(KEY_MIN_AMOUNT, "1.0") ?: "1.0"
            return str.toDoubleOrNull() ?: 1.0
        }
        set(value) = sharedPrefs.edit().putString(KEY_MIN_AMOUNT, value.toString()).apply()

    var currency: String
        get() = sharedPrefs.getString(KEY_CURRENCY, "INR") ?: "INR"
        set(value) = sharedPrefs.edit().putString(KEY_CURRENCY, value.trim().uppercase()).apply()

    var discordWebhookUrl: String
        get() = sharedPrefs.getString(KEY_DISCORD_WEBHOOK_URL, "") ?: ""
        set(value) = sharedPrefs.edit().putString(KEY_DISCORD_WEBHOOK_URL, value.trim()).apply()

    var discordEnabled: Boolean
        get() = sharedPrefs.getBoolean(KEY_DISCORD_ENABLED, false)
        set(value) = sharedPrefs.edit().putBoolean(KEY_DISCORD_ENABLED, value).apply()

    var isServiceRunning: Boolean
        get() = sharedPrefs.getBoolean(KEY_SERVICE_RUNNING, false)
        set(value) = sharedPrefs.edit().putBoolean(KEY_SERVICE_RUNNING, value).apply()

    fun clear() {
        sharedPrefs.edit().clear().apply()
    }
}
