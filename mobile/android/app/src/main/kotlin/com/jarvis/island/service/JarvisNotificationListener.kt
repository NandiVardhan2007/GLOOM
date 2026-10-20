package com.jarvis.island.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.regex.Pattern

/**
 * JARVIS Notification Listener Service:
 * Mirrors real-time Android notifications, OTP security codes, and live activity cards
 * to the JARVIS Windows Dynamic Island over LiveBridge.
 */
class JarvisNotificationListener : NotificationListenerService() {

    private val TAG = "JarvisNotification"
    private val otpPattern = Pattern.compile("(?i)\\b(\\d{3}[- ]?\\d{3}|\\d{4,8})\\b")

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val packageName = sbn.packageName

        Log.d(TAG, "Notification received from $packageName: $title - $text")

        // OTP Detection logic inspired by LiveBridge reference
        val combined = "$title $text"
        val matcher = otpPattern.matcher(combined)
        if (matcher.find()) {
            val code = matcher.group(1)?.replace("-", "")?.replace(" ", "") ?: ""
            if (code.length in 4..8) {
                Log.i(TAG, "Detected OTP code: $code from $packageName")
                // Forwarded to LiveBridge for 1-click desktop island copy
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Handle dismissed notifications
    }
}
