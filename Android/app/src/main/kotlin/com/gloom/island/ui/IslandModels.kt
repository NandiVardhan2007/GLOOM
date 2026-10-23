package com.gloom.island.ui

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle

data class NotificationReplyAction(
    val label: String = "Reply",
    val pendingIntent: PendingIntent,
    val remoteInput: RemoteInput,
    val resultKey: String
) {
    fun send(context: Context, replyText: String): Boolean {
        return try {
            val intent = Intent()
            val bundle = Bundle()
            bundle.putCharSequence(resultKey, replyText)
            RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                RemoteInput.setResultsSource(intent, RemoteInput.SOURCE_FREE_FORM_INPUT)
            }
            pendingIntent.send(context, 0, intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}

data class LyricLine(
    val timeMs: Long = 0L,
    val text: String = ""
)

data class MediaTrackInfo(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArt: Bitmap? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val progress: Float = 0f,
    val appName: String = "",
    val packageName: String = "",
    val lyrics: List<LyricLine> = emptyList()
)

data class ActiveTransferInfo(
    val fileName: String = "",
    val fileSizeMb: Float = 0f,
    val progress: Float = 0f,
    val speedMbps: String = "",
    val isUpload: Boolean = false,
    val isActive: Boolean = false
)

data class BridgeDeviceInfo(
    val deviceName: String = "GLOOM Desktop",
    val ipAddress: String = "192.168.1.4",
    val pingMs: Int = 12,
    val isConnected: Boolean = true
)

data class NotificationIslandEvent(
    val id: String = "",
    val packageName: String = "",
    val appName: String = "",
    val title: String = "",
    val text: String = "",
    val subText: String = "",
    val conversationTitle: String = "",
    val isGroup: Boolean = false,
    val largeIcon: Bitmap? = null,
    val contentIntent: PendingIntent? = null,
    val postTimeMs: Long = System.currentTimeMillis(),
    val otpCode: String? = null,
    val replyAction: NotificationReplyAction? = null
) {
    fun openApp(context: Context): Boolean {
        if (contentIntent != null) {
            try {
                contentIntent.send()
                return true
            } catch (_: Exception) {
                // Expired or canceled pending intent; fall through to launch intent
            }
        }
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                true
            } else false
        } catch (_: Exception) {
            false
        }
    }
}

data class SystemToastEvent(
    val type: String = "", // "charging" | "bluetooth" | "ringer" | "clipboard"
    val title: String = "",
    val subtitle: String = "",
    val iconName: String = "",
    val timestampMs: Long = System.currentTimeMillis()
)

data class LockIslandEvent(
    val isUnlocked: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis()
)
