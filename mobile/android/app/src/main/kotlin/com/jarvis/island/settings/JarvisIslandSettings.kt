package com.jarvis.island.settings

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Persistent settings for JARVIS Dynamic Island overlay geometry,
 * punch-hole camera clearance, lock screen visibility, and beam behavior.
 */
class JarvisIslandSettings {
    companion object {
        val instance = JarvisIslandSettings()
        const val PREFS_NAME = "jarvis_island_settings"
        const val ACTION_SETTINGS_CHANGED = "com.jarvis.island.SETTINGS_CHANGED"
        const val ACTION_TOGGLE_EXPAND = "com.jarvis.island.ACTION_TOGGLE_EXPAND"
        const val ACTION_TEST_BEAM = "com.jarvis.island.ACTION_TEST_BEAM"
    }

    var positionY by mutableIntStateOf(10)
    var positionX by mutableIntStateOf(0)
    var pillWidth by mutableIntStateOf(172)
    var pillHeight by mutableIntStateOf(38)
    var cutoutClearance by mutableIntStateOf(40)
    var cornerRadius by mutableIntStateOf(19)
    var showOnLockScreen by mutableStateOf(true)
    var showInLandscape by mutableStateOf(false)
    var autoExpandOnTransfer by mutableStateOf(true)
    var autoCollapseDelaySec by mutableFloatStateOf(3.5f)

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        positionY = prefs.getInt("position_y", 10)
        positionX = prefs.getInt("position_x", 0)
        pillWidth = prefs.getInt("pill_width", 172)
        pillHeight = prefs.getInt("pill_height", 38)
        cutoutClearance = prefs.getInt("cutout_clearance", 40)
        cornerRadius = prefs.getInt("corner_radius", 19)
        showOnLockScreen = prefs.getBoolean("show_on_lock_screen", true)
        showInLandscape = prefs.getBoolean("show_in_landscape", false)
        autoExpandOnTransfer = prefs.getBoolean("auto_expand_transfer", true)
        autoCollapseDelaySec = prefs.getFloat("auto_collapse_delay", 3.5f)
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("position_y", positionY)
            .putInt("position_x", positionX)
            .putInt("pill_width", pillWidth)
            .putInt("pill_height", pillHeight)
            .putInt("cutout_clearance", cutoutClearance)
            .putInt("corner_radius", cornerRadius)
            .putBoolean("show_on_lock_screen", showOnLockScreen)
            .putBoolean("show_in_landscape", showInLandscape)
            .putBoolean("auto_expand_transfer", autoExpandOnTransfer)
            .putFloat("auto_collapse_delay", autoCollapseDelaySec)
            .apply()

        // Send broadcast so running overlay service updates in real-time
        try {
            val intent = Intent(ACTION_SETTINGS_CHANGED).setPackage(context.packageName)
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun resetDefaults(context: Context) {
        positionY = 10
        positionX = 0
        pillWidth = 172
        pillHeight = 38
        cutoutClearance = 40
        cornerRadius = 19
        showOnLockScreen = true
        showInLandscape = false
        autoExpandOnTransfer = true
        autoCollapseDelaySec = 3.5f
        save(context)
    }
}
