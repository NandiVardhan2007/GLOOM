package com.jarvis.island.ui

data class MediaTrackInfo(
    val title: String = "Starboy",
    val artist: String = "The Weeknd ft. Daft Punk",
    val isPlaying: Boolean = true,
    val progress: Float = 0.42f
)

data class ActiveTransferInfo(
    val fileName: String = "Project_Blueprint.zip",
    val fileSizeMb: Float = 48.2f,
    val progress: Float = 0.65f,
    val speedMbps: String = "108 MB/s",
    val isUpload: Boolean = true,
    val isActive: Boolean = false
)

data class BridgeDeviceInfo(
    val deviceName: String = "JARVIS Desktop (Windows 11)",
    val ipAddress: String = "192.168.1.4",
    val pingMs: Int = 12,
    val isConnected: Boolean = true
)
