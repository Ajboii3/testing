package com.example.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.DiscordEmbed
import com.example.data.DiscordField
import com.example.data.DiscordFooter
import com.example.data.DiscordWebhookRequest
import com.example.data.SettingsManager
import com.example.data.StreamElementsTipRequest
import com.example.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class AlertForwardWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sender = inputData.getString("sender") ?: "Anonymous"
        val amount = inputData.getDouble("amount", 0.0)
        val appName = inputData.getString("appName") ?: "UPI App"
        val text = inputData.getString("text") ?: ""
        val timestamp = inputData.getLong("timestamp", System.currentTimeMillis())

        val settingsManager = SettingsManager(applicationContext)
        val channelId = settingsManager.channelId
        val jwtToken = settingsManager.jwtToken

        var isStreamElementsSuccess = true
        var isDiscordSuccess = true

        // 1. Send to StreamElements if JWT and Channel ID are configured
        if (channelId.isNotEmpty() && jwtToken.isNotEmpty()) {
            if (amount >= settingsManager.minAmount) {
                try {
                    val bearerToken = if (jwtToken.startsWith("Bearer ", ignoreCase = true)) jwtToken else "Bearer $jwtToken"
                    val message = "Received ₹$amount from $sender via $appName [Notification Monitor]"
                    
                    val response = NetworkClient.streamElementsApi.sendTip(
                        channelId = channelId,
                        bearerToken = bearerToken,
                        request = StreamElementsTipRequest(
                            name = sender,
                            amount = amount,
                            message = message,
                            currency = settingsManager.currency
                        )
                    )
                    
                    if (!response.isSuccessful) {
                        val errorBody = response.errorBody()?.string() ?: ""
                        Log.e("AlertForwardWorker", "StreamElements tip failed with code: ${response.code()} - $errorBody")
                        isStreamElementsSuccess = false
                    } else {
                        Log.i("AlertForwardWorker", "Successfully sent tip to StreamElements!")
                    }
                } catch (e: Exception) {
                    Log.e("AlertForwardWorker", "StreamElements network exception", e)
                    isStreamElementsSuccess = false
                }
            } else {
                Log.i("AlertForwardWorker", "Amount ₹$amount is lower than minimum donation threshold ₹${settingsManager.minAmount}")
            }
        } else {
            Log.d("AlertForwardWorker", "StreamElements settings missing, skipping tip alert.")
        }

        // 2. Send to Discord Webhook
        val webhookUrl = settingsManager.discordWebhookUrl
        val discordEnabled = settingsManager.discordEnabled
        if (discordEnabled && webhookUrl.isNotEmpty()) {
            try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val formattedTime = dateFormat.format(Date(timestamp))

                val request = DiscordWebhookRequest(
                    embeds = listOf(
                        DiscordEmbed(
                            title = "🎉 New Donation Alert!",
                            fields = listOf(
                                DiscordField("Sender Name", sender, true),
                                DiscordField("Amount Received", "₹$amount", true),
                                DiscordField("Received Via", appName, true),
                                DiscordField("Notification Content", text, false)
                            ),
                            footer = DiscordFooter("Donation Alert Android • Low RAM Monitor"),
                            timestamp = formattedTime
                        )
                    )
                )

                val response = NetworkClient.discordApi.sendWebhook(webhookUrl, request)
                if (!response.isSuccessful) {
                    val errorBody = response.errorBody()?.string() ?: ""
                    Log.e("AlertForwardWorker", "Discord Webhook failed with code: ${response.code()} - $errorBody")
                    isDiscordSuccess = false
                } else {
                    Log.i("AlertForwardWorker", "Successfully sent webhook to Discord!")
                }
            } catch (e: Exception) {
                Log.e("AlertForwardWorker", "Discord network exception", e)
                isDiscordSuccess = false
            }
        } else {
            Log.d("AlertForwardWorker", "Discord webhook disabled or unconfigured, skipping Discord send.")
        }

        // Best-effort alert forward delivery: mark this task as completed successfully to prevent duplicate retries.
        // Failing integrations log exact response reasons inside the system logs above.
        Result.success()
    }
}
