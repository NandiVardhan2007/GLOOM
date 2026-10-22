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
 * Controls master power, plugin activations, geometry (default top 2px),
 * camera clearance, theme styles, lock-screen visibility, and gesture behaviors.
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
        const val ACTION_MEDIA_PLAY_PAUSE = "com.jarvis.island.ACTION_MEDIA_PLAY_PAUSE"
        const val ACTION_MEDIA_NEXT = "com.jarvis.island.ACTION_MEDIA_NEXT"
        const val ACTION_MEDIA_PREV = "com.jarvis.island.ACTION_MEDIA_PREV"

        // Apple Live Activity geometry (HIG, iOS 393 x 852 class):
        // rest 126 x 37, compact 230 x 37 (camera band 125.34),
        // expanded 371 x 160 with 44 dp corners.
        const val DEFAULT_GRAVITY = "Center"
        const val DEFAULT_POSITION_Y = 2  // Hug the status bar
        const val DEFAULT_POSITION_X = 0  // Dead-center
        const val DEFAULT_CUTOUT_CLEARANCE = 125
        const val DEFAULT_PILL_WIDTH = 230
        const val DEFAULT_PILL_HEIGHT = 37
        const val DEFAULT_EXPANDED_WIDTH = 371
        const val DEFAULT_EXPANDED_HEIGHT = 160
        const val DEFAULT_CORNER_RADIUS = 18
        const val DEFAULT_EXPANDED_CORNER_RADIUS = 44
        const val DEFAULT_THEME = "Obsidian Black"
        const val DEFAULT_SHOW_BORDERS = true
        const val DEFAULT_BORDER_GLOW = true
        const val DEFAULT_SHOW_ON_LOCK_SCREEN = true
        const val DEFAULT_SHOW_IN_LANDSCAPE = false
        const val DEFAULT_AUTO_EXPAND_TRANSFER = true
        const val DEFAULT_AUTO_EXPAND_NOTIFICATION = true
        const val DEFAULT_AUTO_COLLAPSE_DELAY = 4.0f
        const val DEFAULT_HAPTIC_FEEDBACK = true
        const val DEFAULT_LONG_PRESS_EXPAND = true
        const val DEFAULT_REDUCED_MOTION = false
        const val DEFAULT_HIGH_CONTRAST = false
        const val DEFAULT_FILTER_SILENT_NOTIFICATIONS = true
        const val DEFAULT_REEL_COUNTER = true
    }

    // ── 0. Master & Plugins ──
    var isIslandEnabled by mutableStateOf(true)
    var pluginMedia by mutableStateOf(true)
    var pluginFileBeam by mutableStateOf(true)
    var pluginNotifications by mutableStateOf(true)
    var pluginBattery by mutableStateOf(true)
    var pluginClipboard by mutableStateOf(true)
    var filterSilentNotifications by mutableStateOf(DEFAULT_FILTER_SILENT_NOTIFICATIONS)
    var reelCounterEnabled by mutableStateOf(DEFAULT_REEL_COUNTER)

    // ── 0b. Accessibility & Motion ──
    var reducedMotion by mutableStateOf(DEFAULT_REDUCED_MOTION)
    var highContrast by mutableStateOf(DEFAULT_HIGH_CONTRAST)

    // ── 1. Position & Alignment (Top default 2px) ──
    var gravity by mutableStateOf(DEFAULT_GRAVITY)
    var positionY by mutableIntStateOf(DEFAULT_POSITION_Y)
    var positionX by mutableIntStateOf(DEFAULT_POSITION_X)
    var cutoutClearance by mutableIntStateOf(DEFAULT_CUTOUT_CLEARANCE)

    // ── 2. Size & Geometry ──
    var pillWidth by mutableIntStateOf(DEFAULT_PILL_WIDTH)
    var pillHeight by mutableIntStateOf(DEFAULT_PILL_HEIGHT)
    var expandedWidth by mutableIntStateOf(DEFAULT_EXPANDED_WIDTH)
    var expandedHeight by mutableIntStateOf(DEFAULT_EXPANDED_HEIGHT)
    var cornerRadius by mutableIntStateOf(DEFAULT_CORNER_RADIUS)
    var expandedCornerRadius by mutableIntStateOf(DEFAULT_EXPANDED_CORNER_RADIUS)

    // ── 3. Themes & Visuals ──
    var themeStyle by mutableStateOf(DEFAULT_THEME)
    var showBorders by mutableStateOf(DEFAULT_SHOW_BORDERS)
    var borderGlow by mutableStateOf(DEFAULT_BORDER_GLOW)

    // ── 4. Behavior & Gestures ──
    var showOnLockScreen by mutableStateOf(DEFAULT_SHOW_ON_LOCK_SCREEN)
    var showInLandscape by mutableStateOf(DEFAULT_SHOW_IN_LANDSCAPE)
    var autoExpandOnTransfer by mutableStateOf(DEFAULT_AUTO_EXPAND_TRANSFER)
    var autoExpandOnNotification by mutableStateOf(DEFAULT_AUTO_EXPAND_NOTIFICATION)
    var autoCollapseDelaySec by mutableFloatStateOf(DEFAULT_AUTO_COLLAPSE_DELAY)
    var hapticFeedback by mutableStateOf(DEFAULT_HAPTIC_FEEDBACK)
    var longPressToExpand by mutableStateOf(DEFAULT_LONG_PRESS_EXPAND)

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        migrateLegacyGeometry(prefs)
        isIslandEnabled = prefs.getBoolean("island_enabled", true)
        pluginMedia = prefs.getBoolean("plugin_media", true)
        pluginFileBeam = prefs.getBoolean("plugin_file_beam", true)
        pluginNotifications = prefs.getBoolean("plugin_notifications", true)
        pluginBattery = prefs.getBoolean("plugin_battery", true)
        pluginClipboard = prefs.getBoolean("plugin_clipboard", true)
        filterSilentNotifications = prefs.getBoolean(
            "filter_silent_notifications",
            DEFAULT_FILTER_SILENT_NOTIFICATIONS
        )
        reelCounterEnabled = prefs.getBoolean("reel_counter_enabled", DEFAULT_REEL_COUNTER)

        gravity = prefs.getString("gravity", DEFAULT_GRAVITY) ?: DEFAULT_GRAVITY
        positionY = prefs.getInt("position_y", DEFAULT_POSITION_Y)
        positionX = prefs.getInt("position_x", DEFAULT_POSITION_X)
        cutoutClearance = prefs.getInt("cutout_clearance", DEFAULT_CUTOUT_CLEARANCE)

        pillWidth = prefs.getInt("pill_width", DEFAULT_PILL_WIDTH)
        pillHeight = prefs.getInt("pill_height", DEFAULT_PILL_HEIGHT)
        expandedWidth = prefs.getInt("expanded_width", DEFAULT_EXPANDED_WIDTH)
        expandedHeight = prefs.getInt("expanded_height", DEFAULT_EXPANDED_HEIGHT)
        cornerRadius = prefs.getInt("corner_radius", DEFAULT_CORNER_RADIUS)
        expandedCornerRadius = prefs.getInt("expanded_corner_radius", DEFAULT_EXPANDED_CORNER_RADIUS)

        themeStyle = prefs.getString("theme_style", DEFAULT_THEME) ?: DEFAULT_THEME
        showBorders = prefs.getBoolean("show_borders", DEFAULT_SHOW_BORDERS)
        borderGlow = prefs.getBoolean("border_glow", DEFAULT_BORDER_GLOW)

        showOnLockScreen = prefs.getBoolean("show_on_lock_screen", DEFAULT_SHOW_ON_LOCK_SCREEN)
        showInLandscape = prefs.getBoolean("show_in_landscape", DEFAULT_SHOW_IN_LANDSCAPE)
        autoExpandOnTransfer = prefs.getBoolean("auto_expand_transfer", DEFAULT_AUTO_EXPAND_TRANSFER)
        autoExpandOnNotification = prefs.getBoolean("auto_expand_notification", DEFAULT_AUTO_EXPAND_NOTIFICATION)
        autoCollapseDelaySec = prefs.getFloat("auto_collapse_delay", DEFAULT_AUTO_COLLAPSE_DELAY)
        hapticFeedback = prefs.getBoolean("haptic_feedback", DEFAULT_HAPTIC_FEEDBACK)
        longPressToExpand = prefs.getBoolean("long_press_expand", DEFAULT_LONG_PRESS_EXPAND)
        reducedMotion = prefs.getBoolean("reduced_motion", DEFAULT_REDUCED_MOTION)
        highContrast = prefs.getBoolean("high_contrast", DEFAULT_HIGH_CONTRAST)
    }

    /**
     * One-time move from the pre-Apple geometry (184 x 38 / 380 x 260 / 32 dp
     * corners) to the HIG Live Activity dimensions. User-customized geometry
     * is left alone.
     */
    private fun migrateLegacyGeometry(prefs: android.content.SharedPreferences) {
        if (prefs.getBoolean("apple_island_v2", false)) return
        val editor = prefs.edit()
        val legacy = prefs.getInt("pill_width", -1) == 184 &&
            prefs.getInt("pill_height", -1) == 38 &&
            prefs.getInt("expanded_width", -1) == 380 &&
            prefs.getInt("expanded_height", -1) == 260
        if (legacy) {
            editor
                .putInt("pill_width", DEFAULT_PILL_WIDTH)
                .putInt("pill_height", DEFAULT_PILL_HEIGHT)
                .putInt("expanded_width", DEFAULT_EXPANDED_WIDTH)
                .putInt("expanded_height", DEFAULT_EXPANDED_HEIGHT)
                .putInt("corner_radius", DEFAULT_CORNER_RADIUS)
                .putInt("expanded_corner_radius", DEFAULT_EXPANDED_CORNER_RADIUS)
        }
        // The camera band changed meaning from a 32 dp punch-hole pad to Apple's
        // 125 dp TrueDepth band. Move the old default even if size was customized.
        if (prefs.getInt("cutout_clearance", -1) == 32) {
            editor.putInt("cutout_clearance", DEFAULT_CUTOUT_CLEARANCE)
        }
        editor.putBoolean("apple_island_v2", true).apply()
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
            .putBoolean("filter_silent_notifications", filterSilentNotifications)
            .putBoolean("reel_counter_enabled", reelCounterEnabled)
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
            .putBoolean("auto_expand_notification", autoExpandOnNotification)
            .putFloat("auto_collapse_delay", autoCollapseDelaySec)
            .putBoolean("haptic_feedback", hapticFeedback)
            .putBoolean("long_press_expand", longPressToExpand)
            .putBoolean("reduced_motion", reducedMotion)
            .putBoolean("high_contrast", highContrast)
            .apply()

        // Broadcast change so running overlay service updates in real-time
        try {
            val intent = Intent(ACTION_SETTINGS_CHANGED).setPackage(context.packageName)
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun resetPosition(context: Context) {
        gravity = DEFAULT_GRAVITY
        positionY = DEFAULT_POSITION_Y
        positionX = DEFAULT_POSITION_X
        cutoutClearance = DEFAULT_CUTOUT_CLEARANCE
        save(context)
    }

    fun resetSize(context: Context) {
        pillWidth = DEFAULT_PILL_WIDTH
        pillHeight = DEFAULT_PILL_HEIGHT
        expandedWidth = DEFAULT_EXPANDED_WIDTH
        expandedHeight = DEFAULT_EXPANDED_HEIGHT
        cornerRadius = DEFAULT_CORNER_RADIUS
        expandedCornerRadius = DEFAULT_EXPANDED_CORNER_RADIUS
        save(context)
    }

    fun resetThemes(context: Context) {
        themeStyle = DEFAULT_THEME
        showBorders = DEFAULT_SHOW_BORDERS
        borderGlow = DEFAULT_BORDER_GLOW
        save(context)
    }

    fun resetBehavior(context: Context) {
        showOnLockScreen = DEFAULT_SHOW_ON_LOCK_SCREEN
        showInLandscape = DEFAULT_SHOW_IN_LANDSCAPE
        autoExpandOnTransfer = DEFAULT_AUTO_EXPAND_TRANSFER
        autoExpandOnNotification = DEFAULT_AUTO_EXPAND_NOTIFICATION
        autoCollapseDelaySec = DEFAULT_AUTO_COLLAPSE_DELAY
        hapticFeedback = DEFAULT_HAPTIC_FEEDBACK
        longPressToExpand = DEFAULT_LONG_PRESS_EXPAND
        filterSilentNotifications = DEFAULT_FILTER_SILENT_NOTIFICATIONS
        reelCounterEnabled = DEFAULT_REEL_COUNTER
        save(context)
    }

    fun resetDefaults(context: Context) {
        isIslandEnabled = true
        pluginMedia = true
        pluginFileBeam = true
        pluginNotifications = true
        pluginBattery = true
        pluginClipboard = true
        filterSilentNotifications = DEFAULT_FILTER_SILENT_NOTIFICATIONS
        reelCounterEnabled = DEFAULT_REEL_COUNTER
        gravity = DEFAULT_GRAVITY
        positionY = DEFAULT_POSITION_Y
        positionX = DEFAULT_POSITION_X
        cutoutClearance = DEFAULT_CUTOUT_CLEARANCE
        pillWidth = DEFAULT_PILL_WIDTH
        pillHeight = DEFAULT_PILL_HEIGHT
        expandedWidth = DEFAULT_EXPANDED_WIDTH
        expandedHeight = DEFAULT_EXPANDED_HEIGHT
        cornerRadius = DEFAULT_CORNER_RADIUS
        expandedCornerRadius = DEFAULT_EXPANDED_CORNER_RADIUS
        themeStyle = DEFAULT_THEME
        showBorders = DEFAULT_SHOW_BORDERS
        borderGlow = DEFAULT_BORDER_GLOW
        showOnLockScreen = DEFAULT_SHOW_ON_LOCK_SCREEN
        showInLandscape = DEFAULT_SHOW_IN_LANDSCAPE
        autoExpandOnTransfer = DEFAULT_AUTO_EXPAND_TRANSFER
        autoExpandOnNotification = DEFAULT_AUTO_EXPAND_NOTIFICATION
        autoCollapseDelaySec = DEFAULT_AUTO_COLLAPSE_DELAY
        hapticFeedback = DEFAULT_HAPTIC_FEEDBACK
        longPressToExpand = DEFAULT_LONG_PRESS_EXPAND
        save(context)
    }

    fun applyMotoEdge50ProPreset(context: Context) {
        gravity = DEFAULT_GRAVITY
        positionY = DEFAULT_POSITION_Y
        positionX = DEFAULT_POSITION_X
        cutoutClearance = DEFAULT_CUTOUT_CLEARANCE
        pillWidth = DEFAULT_PILL_WIDTH
        pillHeight = DEFAULT_PILL_HEIGHT
        expandedWidth = DEFAULT_EXPANDED_WIDTH
        expandedHeight = DEFAULT_EXPANDED_HEIGHT
        cornerRadius = DEFAULT_CORNER_RADIUS
        expandedCornerRadius = DEFAULT_EXPANDED_CORNER_RADIUS
        save(context)
    }
}
