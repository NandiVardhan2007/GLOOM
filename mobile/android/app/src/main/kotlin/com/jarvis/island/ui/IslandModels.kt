package com.jarvis.island.ui

import android.graphics.Bitmap

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
    val packageName: String = ""
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
