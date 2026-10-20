package com.jarvis.island.settings

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Complete settings catalog for JARVIS Dynamic Island:
 * Controls master power, plugin activations, geometry, camera clearance,
 * spring physics, theme styles, lock-screen visibility, and gesture behaviors.
 */
class JarvisIslandSettings {
    companion object {
        val instance = JarvisIslandSettings()
        const val PREFS_NAME = "jarvis_island_settings"
        const val ACTION_SETTINGS_CHANGED = "com.jarvis.island.SETTINGS_CHANGED"
        const val ACTION_TOGGLE_EXPAND = "com.jarvis.island.ACTION_TOGGLE_EXPAND"
        const val ACTION_EXPAND = "com.jarvis.island.ACTION_EXPAND"
        const val ACTION_COLLAPSE = "com.jarvis.island.ACTION_COLLAPSE"
        const val ACTION_TEST_BEAM = "com.jarvis.island.ACTION_TEST_BEAM"
        const val ACTION_TEST_MUSIC = "com.jarvis.island.ACTION_TEST_MUSIC"
    }

    // ── 0. Master & Plugins ──
    var isIslandEnabled by mutableStateOf(true)
    var pluginMedia by mutableStateOf(true)
    var pluginFileBeam by mutableStateOf(true)
    var pluginNotifications by mutableStateOf(true)
    var pluginBattery by mutableStateOf(true)
    var pluginClipboard by mutableStateOf(true)

    // ── 1. Position & Alignment ──
    var gravity by mutableStateOf("Center") // "Center", "Left", "Right"
    var positionY by mutableIntStateOf(10)
    var positionX by mutableIntStateOf(0)
    var cutoutClearance by mutableIntStateOf(44) // Center punch-hole empty gap

    // ── 2. Size & Geometry ──
    var pillWidth by mutableIntStateOf(176)
    var pillHeight by mutableIntStateOf(38)
    var expandedWidth by mutableIntStateOf(364)
    var expandedHeight by mutableIntStateOf(252)
    var cornerRadius by mutableIntStateOf(19)
    var expandedCornerRadius by mutableIntStateOf(28)

    // ── 3. Themes & Visuals ──
    var themeStyle by mutableStateOf("Obsidian Black") // "Obsidian Black", "Cyber Cyan", "Emerald Matrix", "Material Purple"
    var showBorders by mutableStateOf(true)
    var borderGlow by mutableStateOf(true)

    // ── 4. Behavior & Gestures ──
    var showOnLockScreen by mutableStateOf(true)
    var showInLandscape by mutableStateOf(false)
    var autoExpandOnTransfer by mutableStateOf(true)
    var autoCollapseDelaySec by mutableFloatStateOf(4.0f)
    var hapticFeedback by mutableStateOf(true)
    var longPressToExpand by mutableStateOf(true)

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isIslandEnabled = prefs.getBoolean("island_enabled", true)
        pluginMedia = prefs.getBoolean("plugin_media", true)
        pluginFileBeam = prefs.getBoolean("plugin_file_beam", true)
        pluginNotifications = prefs.getBoolean("plugin_notifications", true)
        pluginBattery = prefs.getBoolean("plugin_battery", true)
        pluginClipboard = prefs.getBoolean("plugin_clipboard", true)

        gravity = prefs.getString("gravity", "Center") ?: "Center"
        positionY = prefs.getInt("position_y", 10)
        positionX = prefs.getInt("position_x", 0)
        cutoutClearance = prefs.getInt("cutout_clearance", 44)

        pillWidth = prefs.getInt("pill_width", 176)
        pillHeight = prefs.getInt("pill_height", 38)
        expandedWidth = prefs.getInt("expanded_width", 364)
        expandedHeight = prefs.getInt("expanded_height", 252)
        cornerRadius = prefs.getInt("corner_radius", 19)
        expandedCornerRadius = prefs.getInt("expanded_corner_radius", 28)

        themeStyle = prefs.getString("theme_style", "Obsidian Black") ?: "Obsidian Black"
        showBorders = prefs.getBoolean("show_borders", true)
        borderGlow = prefs.getBoolean("border_glow", true)

        showOnLockScreen = prefs.getBoolean("show_on_lock_screen", true)
        showInLandscape = prefs.getBoolean("show_in_landscape", false)
        autoExpandOnTransfer = prefs.getBoolean("auto_expand_transfer", true)
        autoCollapseDelaySec = prefs.getFloat("auto_collapse_delay", 4.0f)
        hapticFeedback = prefs.getBoolean("haptic_feedback", true)
        longPressToExpand = prefs.getBoolean("long_press_expand", true)
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("island_enabled", isIslandEnabled)
            .putBoolean("plugin_media", pluginMedia)
            .putBoolean("plugin_file_beam", pluginFileBeam)
            .putBoolean("plugin_notifications", pluginNotifications)
            .putBoolean("plugin_battery", pluginBattery)
            .putBoolean("plugin_clipboard", pluginClipboard)
            .putString("gravity", gravity)
            .putInt("position_y", positionY)
            .putInt("position_x", positionX)
            .putInt("cutout_clearance", cutoutClearance)
            .putInt("pill_width", pillWidth)
            .putInt("pill_height", pillHeight)
            .putInt("expanded_width", expandedWidth)
            .putInt("expanded_height", expandedHeight)
            .putInt("corner_radius", cornerRadius)
            .putInt("expanded_corner_radius", expandedCornerRadius)
            .putString("theme_style", themeStyle)
            .putBoolean("show_borders", showBorders)
            .putBoolean("border_glow", borderGlow)
            .putBoolean("show_on_lock_screen", showOnLockScreen)
            .putBoolean("show_in_landscape", showInLandscape)
            .putBoolean("auto_expand_transfer", autoExpandOnTransfer)
            .putFloat("auto_collapse_delay", autoCollapseDelaySec)
            .putBoolean("haptic_feedback", hapticFeedback)
            .putBoolean("long_press_expand", longPressToExpand)
            .apply()

        // Broadcast change so running overlay service updates in real-time
        try {
            val intent = Intent(ACTION_SETTINGS_CHANGED).setPackage(context.packageName)
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun resetDefaults(context: Context) {
        isIslandEnabled = true
        pluginMedia = true
        pluginFileBeam = true
        pluginNotifications = true
        pluginBattery = true
        pluginClipboard = true
        gravity = "Center"
        positionY = 10
        positionX = 0
        cutoutClearance = 44
        pillWidth = 176
        pillHeight = 38
        expandedWidth = 364
        expandedHeight = 252
        cornerRadius = 19
        expandedCornerRadius = 28
        themeStyle = "Obsidian Black"
        showBorders = true
        borderGlow = true
        showOnLockScreen = true
        showInLandscape = false
        autoExpandOnTransfer = true
        autoCollapseDelaySec = 4.0f
        hapticFeedback = true
        longPressToExpand = true
        save(context)
    }
}
