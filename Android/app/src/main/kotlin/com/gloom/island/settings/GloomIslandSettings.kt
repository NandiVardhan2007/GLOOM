package com.gloom.island.settings

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Complete settings catalog for GLOOM Dynamic Island:
 * Controls master power, plugin activations, geometry (default top 2px),
 * camera clearance, theme styles, lock-screen visibility, and gesture behaviors.
 */
class GloomIslandSettings {
    companion object {
        val instance = GloomIslandSettings()
        const val PREFS_NAME = "gloom_island_settings"
        const val ACTION_SETTINGS_CHANGED = "com.gloom.island.SETTINGS_CHANGED"
        const val ACTION_TOGGLE_EXPAND = "com.gloom.island.ACTION_TOGGLE_EXPAND"
        const val ACTION_EXPAND = "com.gloom.island.ACTION_EXPAND"
        const val ACTION_COLLAPSE = "com.gloom.island.ACTION_COLLAPSE"
        const val ACTION_TEST_BEAM = "com.gloom.island.ACTION_TEST_BEAM"
        const val ACTION_TEST_MUSIC = "com.gloom.island.ACTION_TEST_MUSIC"
        const val ACTION_MEDIA_PLAY_PAUSE = "com.gloom.island.ACTION_MEDIA_PLAY_PAUSE"
        const val ACTION_MEDIA_NEXT = "com.gloom.island.ACTION_MEDIA_NEXT"
        const val ACTION_MEDIA_PREV = "com.gloom.island.ACTION_MEDIA_PREV"

        // Apple Live Activity geometry (HIG, iOS 393 x 852 class):
        // rest 126 x 37, compact 180 x 37 (camera band 125.34),
        // expanded 400 x 180 with 36 dp corners.
        const val DEFAULT_GRAVITY = "Center"
        const val DEFAULT_POSITION_Y = 2  // Hug the status bar
        const val DEFAULT_POSITION_X = 0  // Dead-center
        const val DEFAULT_CUTOUT_CLEARANCE = 125
        const val DEFAULT_PILL_WIDTH = 180
        const val DEFAULT_PILL_HEIGHT = 37
        const val DEFAULT_EXPANDED_WIDTH = 400
        const val DEFAULT_EXPANDED_HEIGHT = 180
        const val DEFAULT_CORNER_RADIUS = 18
        const val DEFAULT_EXPANDED_CORNER_RADIUS = 36
        const val DEFAULT_ISLAND_STYLE = "Notch" // "Notch" (Desktop Notch) or "Pill" (Floating Pill)
        const val DEFAULT_THEME = "Obsidian Black"
        const val DEFAULT_SHOW_BORDERS = true
        const val DEFAULT_BORDER_GLOW = true
        const val DEFAULT_ACCENT_COLOR = "#0A84FF"
        const val DEFAULT_FONT_FAMILY = "System"
        const val DEFAULT_BLUR_TRANSPARENCY = 0.92f
        const val DEFAULT_ICON_STYLE = "SF Rounded"
        const val DEFAULT_DISPLAY_THEME = "amoled"
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
        const val DEFAULT_AUTOMATION_SAFETY = true
        const val DEFAULT_VERIFY_ACTIONS = true
        const val DEFAULT_NOTIFICATION_TRIAGE = true
        const val DEFAULT_OTP_COPY = true
        const val DEFAULT_AI_BRAIN = "auto"
        const val DEFAULT_PLANNER_PROFILE = "flash"
        const val DEFAULT_REEL_MIN_DWELL_MS = 350

        // Lock & Pattern Calibration (bypasses in-display optical fingerprint sensors)
        const val DEFAULT_SWIPE_STRATEGY = "Left"
        const val DEFAULT_SWIPE_START_X = 0.22f
        const val DEFAULT_SWIPE_START_Y = 0.82f
        const val DEFAULT_SWIPE_END_X = 0.22f
        const val DEFAULT_SWIPE_END_Y = 0.25f
        const val DEFAULT_PATTERN_GRID_WIDTH = 0.80f
        const val DEFAULT_PATTERN_CENTER_Y = 0.67f

        const val DEFAULT_SHOW_IDLE_STATUS = true

        // Apple Fluid & Hardware Enhancements
        const val DEFAULT_CAMERA_HALO = false
        const val DEFAULT_DUAL_LIVE_ACTIVITIES = true
        const val DEFAULT_NOTIFICATION_SWIPE_DISMISS = true
        const val DEFAULT_LAUNCH_ASSISTANT_ON_APP_OPEN = false
        const val DEFAULT_LOCK_SCREEN_SIRI_UNLOCK = true
        const val DEFAULT_LOCK_SCREEN_AUTO_POP_SIRI = true
    }

    // ── 0. Master & Plugins ──
    var isIslandEnabled by mutableStateOf(true)
    var launchAssistantOnAppOpen by mutableStateOf(DEFAULT_LAUNCH_ASSISTANT_ON_APP_OPEN)
    var lockScreenSiriUnlock by mutableStateOf(DEFAULT_LOCK_SCREEN_SIRI_UNLOCK)
    var lockScreenAutoPopSiri by mutableStateOf(DEFAULT_LOCK_SCREEN_AUTO_POP_SIRI)
    var pluginMedia by mutableStateOf(true)
    var pluginFileBeam by mutableStateOf(true)
    var pluginNotifications by mutableStateOf(true)
    var pluginBattery by mutableStateOf(true)
    var pluginClipboard by mutableStateOf(true)
    var filterSilentNotifications by mutableStateOf(DEFAULT_FILTER_SILENT_NOTIFICATIONS)
    var reelCounterEnabled by mutableStateOf(DEFAULT_REEL_COUNTER)
    var automationSafetyEnabled by mutableStateOf(DEFAULT_AUTOMATION_SAFETY)
    var verifyActionsEnabled by mutableStateOf(DEFAULT_VERIFY_ACTIONS)
    var notificationTriageEnabled by mutableStateOf(DEFAULT_NOTIFICATION_TRIAGE)
    var otpCopyEnabled by mutableStateOf(DEFAULT_OTP_COPY)
    var aiBrain by mutableStateOf(DEFAULT_AI_BRAIN)
    var plannerProfile by mutableStateOf(DEFAULT_PLANNER_PROFILE)
    var reelMinDwellMs by mutableIntStateOf(DEFAULT_REEL_MIN_DWELL_MS)
    var blockedPackages by mutableStateOf<Set<String>>(emptySet())
    var showIdleStatus by mutableStateOf(DEFAULT_SHOW_IDLE_STATUS)
    var cameraHaloEnabled by mutableStateOf(DEFAULT_CAMERA_HALO)
    var dualLiveActivitiesEnabled by mutableStateOf(DEFAULT_DUAL_LIVE_ACTIVITIES)
    var notificationSwipeDismissEnabled by mutableStateOf(DEFAULT_NOTIFICATION_SWIPE_DISMISS)

    // ── 0a. Pattern & Swipe Calibration ──
    var swipeStrategy by mutableStateOf(DEFAULT_SWIPE_STRATEGY)
    var swipeStartXRatio by mutableFloatStateOf(DEFAULT_SWIPE_START_X)
    var swipeStartYRatio by mutableFloatStateOf(DEFAULT_SWIPE_START_Y)
    var swipeEndXRatio by mutableFloatStateOf(DEFAULT_SWIPE_END_X)
    var swipeEndYRatio by mutableFloatStateOf(DEFAULT_SWIPE_END_Y)
    var patternGridWidthRatio by mutableFloatStateOf(DEFAULT_PATTERN_GRID_WIDTH)
    var patternCenterYRatio by mutableFloatStateOf(DEFAULT_PATTERN_CENTER_Y)

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
    var islandStyle by mutableStateOf(DEFAULT_ISLAND_STYLE)

    // ── 3. Themes & Visuals ──
    var themeStyle by mutableStateOf(DEFAULT_THEME)
    var showBorders by mutableStateOf(DEFAULT_SHOW_BORDERS)
    var borderGlow by mutableStateOf(DEFAULT_BORDER_GLOW)
    var accentColorHex by mutableStateOf(DEFAULT_ACCENT_COLOR)
    var fontFamilyChoice by mutableStateOf(DEFAULT_FONT_FAMILY)
    var blurTransparency by mutableFloatStateOf(DEFAULT_BLUR_TRANSPARENCY)
    var iconStyle by mutableStateOf(DEFAULT_ICON_STYLE)
    var displayThemeMode by mutableStateOf(DEFAULT_DISPLAY_THEME)

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
        automationSafetyEnabled = prefs.getBoolean("automation_safety", DEFAULT_AUTOMATION_SAFETY)
        verifyActionsEnabled = prefs.getBoolean("verify_actions", DEFAULT_VERIFY_ACTIONS)
        notificationTriageEnabled = prefs.getBoolean("notification_triage", DEFAULT_NOTIFICATION_TRIAGE)
        otpCopyEnabled = prefs.getBoolean("otp_copy", DEFAULT_OTP_COPY)
        aiBrain = prefs.getString("ai_brain", DEFAULT_AI_BRAIN) ?: DEFAULT_AI_BRAIN
        plannerProfile = prefs.getString("planner_profile", DEFAULT_PLANNER_PROFILE) ?: DEFAULT_PLANNER_PROFILE
        reelMinDwellMs = prefs.getInt("reel_min_dwell_ms", DEFAULT_REEL_MIN_DWELL_MS)
        blockedPackages = prefs.getStringSet("blocked_packages", emptySet())?.toSet() ?: emptySet()

        swipeStrategy = prefs.getString("swipe_strategy", DEFAULT_SWIPE_STRATEGY) ?: DEFAULT_SWIPE_STRATEGY
        swipeStartXRatio = prefs.getFloat("swipe_start_x", DEFAULT_SWIPE_START_X)
        swipeStartYRatio = prefs.getFloat("swipe_start_y", DEFAULT_SWIPE_START_Y)
        swipeEndXRatio = prefs.getFloat("swipe_end_x", DEFAULT_SWIPE_END_X)
        swipeEndYRatio = prefs.getFloat("swipe_end_y", DEFAULT_SWIPE_END_Y)
        patternGridWidthRatio = prefs.getFloat("pattern_grid_width", DEFAULT_PATTERN_GRID_WIDTH)
        patternCenterYRatio = prefs.getFloat("pattern_center_y", DEFAULT_PATTERN_CENTER_Y)

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
        islandStyle = prefs.getString("island_style", DEFAULT_ISLAND_STYLE) ?: DEFAULT_ISLAND_STYLE

        themeStyle = prefs.getString("theme_style", DEFAULT_THEME) ?: DEFAULT_THEME
        showBorders = prefs.getBoolean("show_borders", DEFAULT_SHOW_BORDERS)
        borderGlow = prefs.getBoolean("border_glow", DEFAULT_BORDER_GLOW)
        accentColorHex = prefs.getString("accent_color_hex", DEFAULT_ACCENT_COLOR) ?: DEFAULT_ACCENT_COLOR
        fontFamilyChoice = prefs.getString("font_family_choice", DEFAULT_FONT_FAMILY) ?: DEFAULT_FONT_FAMILY
        blurTransparency = prefs.getFloat("blur_transparency", DEFAULT_BLUR_TRANSPARENCY)
        iconStyle = prefs.getString("icon_style", DEFAULT_ICON_STYLE) ?: DEFAULT_ICON_STYLE
        displayThemeMode = prefs.getString("display_theme_mode", DEFAULT_DISPLAY_THEME) ?: DEFAULT_DISPLAY_THEME

        showOnLockScreen = prefs.getBoolean("show_on_lock_screen", DEFAULT_SHOW_ON_LOCK_SCREEN)
        showInLandscape = prefs.getBoolean("show_in_landscape", DEFAULT_SHOW_IN_LANDSCAPE)
        autoExpandOnTransfer = prefs.getBoolean("auto_expand_transfer", DEFAULT_AUTO_EXPAND_TRANSFER)
        autoExpandOnNotification = prefs.getBoolean("auto_expand_notification", DEFAULT_AUTO_EXPAND_NOTIFICATION)
        autoCollapseDelaySec = prefs.getFloat("auto_collapse_delay", DEFAULT_AUTO_COLLAPSE_DELAY)
        hapticFeedback = prefs.getBoolean("haptic_feedback", DEFAULT_HAPTIC_FEEDBACK)
        longPressToExpand = prefs.getBoolean("long_press_expand", DEFAULT_LONG_PRESS_EXPAND)
        reducedMotion = prefs.getBoolean("reduced_motion", DEFAULT_REDUCED_MOTION)
        highContrast = prefs.getBoolean("high_contrast", DEFAULT_HIGH_CONTRAST)

        showIdleStatus = prefs.getBoolean("show_idle_status", DEFAULT_SHOW_IDLE_STATUS)
        cameraHaloEnabled = prefs.getBoolean("camera_halo_enabled", DEFAULT_CAMERA_HALO)
        dualLiveActivitiesEnabled = prefs.getBoolean("dual_live_activities_enabled", DEFAULT_DUAL_LIVE_ACTIVITIES)
        notificationSwipeDismissEnabled = prefs.getBoolean("notification_swipe_dismiss_enabled", DEFAULT_NOTIFICATION_SWIPE_DISMISS)
        launchAssistantOnAppOpen = prefs.getBoolean("launch_assistant_on_app_open", DEFAULT_LAUNCH_ASSISTANT_ON_APP_OPEN)
        lockScreenSiriUnlock = prefs.getBoolean("lock_screen_siri_unlock", DEFAULT_LOCK_SCREEN_SIRI_UNLOCK)
        lockScreenAutoPopSiri = prefs.getBoolean("lock_screen_auto_pop_siri", DEFAULT_LOCK_SCREEN_AUTO_POP_SIRI)
    }

    /**
     * One-time move from the pre-Apple geometry (184 x 38 / 380 x 260 / 32 dp
     * corners) or previous 230dp default to the compact 180dp HIG Live Activity dimensions.
     * User-customized geometry is left alone.
     */
    private fun migrateLegacyGeometry(prefs: android.content.SharedPreferences) {
        val editor = prefs.edit()
        val currentPillWidth = prefs.getInt("pill_width", -1)
        val currentExpW = prefs.getInt("expanded_width", -1)
        if (currentExpW == 371 || currentExpW == 380) {
            editor.putInt("expanded_width", DEFAULT_EXPANDED_WIDTH)
        }
        val currentExpH = prefs.getInt("expanded_height", -1)
        if (currentExpH == 160 || currentExpH == 260) {
            editor.putInt("expanded_height", DEFAULT_EXPANDED_HEIGHT)
        }
        val currentExpR = prefs.getInt("expanded_corner_radius", -1)
        if (currentExpR == 44 || currentExpR == 32) {
            editor.putInt("expanded_corner_radius", DEFAULT_EXPANDED_CORNER_RADIUS)
        }
        if (currentPillWidth == 230 || currentPillWidth == 184) {
            editor.putInt("pill_width", DEFAULT_PILL_WIDTH)
        }
        if (prefs.getBoolean("apple_island_v2", false)) {
            editor.apply()
            return
        }
        val legacy = currentPillWidth == 184 &&
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
            .putBoolean("automation_safety", automationSafetyEnabled)
            .putBoolean("verify_actions", verifyActionsEnabled)
            .putBoolean("notification_triage", notificationTriageEnabled)
            .putBoolean("otp_copy", otpCopyEnabled)
            .putString("ai_brain", aiBrain)
            .putString("planner_profile", plannerProfile)
            .putInt("reel_min_dwell_ms", reelMinDwellMs)
            .putStringSet("blocked_packages", blockedPackages)
            .putString("swipe_strategy", swipeStrategy)
            .putFloat("swipe_start_x", swipeStartXRatio)
            .putFloat("swipe_start_y", swipeStartYRatio)
            .putFloat("swipe_end_x", swipeEndXRatio)
            .putFloat("swipe_end_y", swipeEndYRatio)
            .putFloat("pattern_grid_width", patternGridWidthRatio)
            .putFloat("pattern_center_y", patternCenterYRatio)
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
            .putString("island_style", islandStyle)
            .putString("theme_style", themeStyle)
            .putBoolean("show_borders", showBorders)
            .putBoolean("border_glow", borderGlow)
            .putString("accent_color_hex", accentColorHex)
            .putString("font_family_choice", fontFamilyChoice)
            .putFloat("blur_transparency", blurTransparency)
            .putString("icon_style", iconStyle)
            .putString("display_theme_mode", displayThemeMode)
            .putBoolean("show_on_lock_screen", showOnLockScreen)
            .putBoolean("show_in_landscape", showInLandscape)
            .putBoolean("auto_expand_transfer", autoExpandOnTransfer)
            .putBoolean("auto_expand_notification", autoExpandOnNotification)
            .putFloat("auto_collapse_delay", autoCollapseDelaySec)
            .putBoolean("haptic_feedback", hapticFeedback)
            .putBoolean("long_press_expand", longPressToExpand)
            .putBoolean("reduced_motion", reducedMotion)
            .putBoolean("high_contrast", highContrast)
            .putBoolean("show_idle_status", showIdleStatus)
            .putBoolean("camera_halo_enabled", cameraHaloEnabled)
            .putBoolean("dual_live_activities_enabled", dualLiveActivitiesEnabled)
            .putBoolean("notification_swipe_dismiss_enabled", notificationSwipeDismissEnabled)
            .putBoolean("launch_assistant_on_app_open", launchAssistantOnAppOpen)
            .putBoolean("lock_screen_siri_unlock", lockScreenSiriUnlock)
            .putBoolean("lock_screen_auto_pop_siri", lockScreenAutoPopSiri)
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
        islandStyle = DEFAULT_ISLAND_STYLE
        save(context)
    }

    fun resetThemes(context: Context) {
        themeStyle = DEFAULT_THEME
        showBorders = DEFAULT_SHOW_BORDERS
        borderGlow = DEFAULT_BORDER_GLOW
        accentColorHex = DEFAULT_ACCENT_COLOR
        fontFamilyChoice = DEFAULT_FONT_FAMILY
        blurTransparency = DEFAULT_BLUR_TRANSPARENCY
        iconStyle = DEFAULT_ICON_STYLE
        displayThemeMode = DEFAULT_DISPLAY_THEME
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
        automationSafetyEnabled = DEFAULT_AUTOMATION_SAFETY
        verifyActionsEnabled = DEFAULT_VERIFY_ACTIONS
        notificationTriageEnabled = DEFAULT_NOTIFICATION_TRIAGE
        otpCopyEnabled = DEFAULT_OTP_COPY
        aiBrain = DEFAULT_AI_BRAIN
        reelMinDwellMs = DEFAULT_REEL_MIN_DWELL_MS
        blockedPackages = emptySet()
        save(context)
    }

    /** True when the island is allowed to show alerts from [packageName]. */
    fun isPackageAllowed(packageName: String): Boolean = packageName !in blockedPackages

    fun setPackageAllowed(context: Context, packageName: String, allowed: Boolean) {
        blockedPackages = if (allowed) {
            blockedPackages - packageName
        } else {
            blockedPackages + packageName
        }
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
        automationSafetyEnabled = DEFAULT_AUTOMATION_SAFETY
        verifyActionsEnabled = DEFAULT_VERIFY_ACTIONS
        notificationTriageEnabled = DEFAULT_NOTIFICATION_TRIAGE
        otpCopyEnabled = DEFAULT_OTP_COPY
        aiBrain = DEFAULT_AI_BRAIN
        reelMinDwellMs = DEFAULT_REEL_MIN_DWELL_MS
        blockedPackages = emptySet()
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
        islandStyle = DEFAULT_ISLAND_STYLE
        save(context)
    }
}
