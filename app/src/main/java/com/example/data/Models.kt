package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PaymentEvent(
    val senderName: String,
    val amount: Double,
    val currency: String = "INR",
    val appName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class StreamElementsTipRequest(
    val name: String,
    val amount: Double,
    val message: String,
    val currency: String = "INR"
)

@JsonClass(generateAdapter = true)
data class DiscordWebhookRequest(
    val embeds: List<DiscordEmbed>
)

@JsonClass(generateAdapter = true)
data class DiscordEmbed(
    val title: String,
    val color: Int = 3066993, // Premium Green Accent
    val fields: List<DiscordField>,
    val footer: DiscordFooter,
    val timestamp: String
)

@JsonClass(generateAdapter = true)
data class DiscordField(
    val name: String,
    val value: String,
    val inline: Boolean = false
)

@JsonClass(generateAdapter = true)
data class DiscordFooter(
    val text: String
)
