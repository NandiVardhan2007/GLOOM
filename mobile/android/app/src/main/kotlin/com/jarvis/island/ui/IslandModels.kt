package com.jarvis.island.ui

import android.app.PendingIntent
import android.graphics.Bitmap

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
    val deviceName: String = "JARVIS Desktop",
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
    val largeIcon: Bitmap? = null,
    val contentIntent: PendingIntent? = null,
    val postTimeMs: Long = System.currentTimeMillis()
)

data class SystemToastEvent(
    val type: String = "", // "charging" | "bluetooth" | "ringer" | "clipboard"
    val title: String = "",
    val subtitle: String = "",
    val iconName: String = "",
    val timestampMs: Long = System.currentTimeMillis()
)
