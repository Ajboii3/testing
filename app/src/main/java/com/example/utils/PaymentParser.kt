package com.example.utils

import android.util.Log
import com.example.data.PaymentEvent

object PaymentParser {
    private const val TAG = "PaymentParser"

    // Regex definitions
    private val amountRegex = "(?:₹|Rs\\.?|INR)\\s*([0-9,]+(?:\\.[0-9]{2})?)".toRegex(RegexOption.IGNORE_CASE)
    
    // Explicit full patterns for extraction
    private val patterns = listOf(
        // Matches "You received ₹500 from Ajay" or "Received ₹500 from Ramesh Kumar"
        "(?i)(?:you\\s+)?received\\s+(?:(?:₹|Rs\\.?|INR)\\s*([0-9,.]+))\\s+from\\s+([^.\n]+)".toRegex(),
        // Matches "₹100 received from Rahul"
        "(?i)(?:(?:₹|Rs\\.?|INR)\\s*([0-9,.]+))\\s+received\\s+from\\s+([^.\n]+)".toRegex(),
        // Matches "Payment of ₹15 received from Subhash"
        "(?i)payment\\s+of\\s*(?:(?:₹|Rs\\.?|INR)\\s*([0-9,.]+))\\s+received\\s+from\\s+([^.\n]+)".toRegex(),
        // Matches "Rs. 50 received" (Fallback pattern, no sender)
        "(?i)(?:(?:₹|Rs\\.?|INR)\\s*([0-9,.]+))\\s+received".toRegex(),
        // Matches "Received ₹50 via UPI" (Fallback pattern, no sender)
        "(?i)received\\s+(?:(?:₹|Rs\\.?|INR)\\s*([0-9,.]+))".toRegex()
    )

    fun parse(text: String, packageName: String): PaymentEvent? {
        Log.d(TAG, "Parsing text: \"$text\" from package: $packageName")

        // First, check if the notification is promotional, sent, failed, or pending
        val textLower = text.lowercase()
        val ignoreKeywords = listOf(
            "sent", "paid", "failed", "pending", "declined", "recalled", 
            "promotional", "cashback", "offer", "discount", "recharge", 
            "loan", "bill paid", "sent successfully"
        )
        // Ensure "received" prefix exists and does not contain ignore keywords, 
        // unless it explicitly says "received cashback" in which case we still ignore per user instructions (Ignore: Cashback).
        if (ignoreKeywords.any { textLower.contains(it) }) {
            Log.d(TAG, "Skipping notification: matching ignore keyword.")
            return null
        }

        // We also want to verify there's a reference to incoming cash (e.g. "received", "credited", "added") or currency symbols
        if (!textLower.contains("received") && !textLower.contains("credited") && !textLower.contains("added") && !textLower.contains("₹") && !textLower.contains("rs")) {
            Log.d(TAG, "Skipping notification: does not look like an incoming payment confirmation.")
            return null
        }

        var amount: Double? = null
        var senderName = "Anonymous"

        for (pattern in patterns) {
            val match = pattern.find(text) ?: continue
            val groups = match.groupValues
            if (groups.size >= 2) {
                // Group 1 is always the amount
                val amountStr = groups[1].replace(",", "")
                amount = amountStr.toDoubleOrNull()
                
                if (groups.size >= 3) {
                    // Group 2 is the sender name
                    val nameRaw = groups[2].trim()
                    if (nameRaw.isNotEmpty() && 
                        !nameRaw.contains("via", ignoreCase = true) && 
                        !nameRaw.contains("upi", ignoreCase = true) &&
                        !nameRaw.contains("bank", ignoreCase = true) &&
                        !nameRaw.contains("account", ignoreCase = true)) {
                        senderName = nameRaw
                    }
                }
                break // Found a matching layout!
            }
        }

        // Fallback: If amount couldn't be extracted from formal patterns, try generic amount extraction
        if (amount == null) {
            val genericAmountMatch = amountRegex.find(text)
            if (genericAmountMatch != null) {
                val amountStr = genericAmountMatch.groupValues[1].replace(",", "")
                amount = amountStr.toDoubleOrNull()
            }
        }

        if (amount == null) {
            Log.d(TAG, "Parsing aborted: No valid numerical amount could be extracted.")
            return null
        }

        // Map packagename to friendly UPI App Name
        val appFriendlyName = getUpiAppName(packageName)

        val event = PaymentEvent(
            senderName = cleanSenderName(senderName),
            amount = amount,
            appName = appFriendlyName,
            text = text
        )
        Log.d(TAG, "Successfully parsed payment alert event: $event")
        return event
    }

    private fun cleanSenderName(rawName: String): String {
        // Strip out trailing transaction IDs, upi handles, etc.
        var name = rawName
            .replace(Regex("(?i)\\bvia\\b.*"), "")
            .replace(Regex("(?i)\\busing\\b.*"), "")
            .replace(Regex("(?i)\\bon\\b.*"), "")
            .replace(Regex("[()#*%]"), "")
            .trim()
        
        if (name.lowercase() == "you" || name.isEmpty()) {
            name = "UPI Sender"
        }
        return name
    }

    private fun getUpiAppName(packageName: String): String {
        return when {
            packageName.contains("nbu.paisa", ignoreCase = true) -> "Google Pay"
            packageName.contains("phonepe", ignoreCase = true) -> "PhonePe"
            packageName.contains("paytm", ignoreCase = true) -> "Paytm"
            packageName.contains("npci.upiapp", ignoreCase = true) -> "BHIM"
            packageName.contains("amazon.mShop", ignoreCase = true) -> "Amazon Pay"
            else -> {
                val parts = packageName.split(".")
                if (parts.size >= 2) {
                    parts[parts.size - 1].replaceFirstChar { it.uppercase() }
                } else {
                    "UPI App"
                }
            }
        }
    }
}
