package com.gloom.island.ui

import androidx.compose.ui.graphics.Color

/**
 * Layout categories for per-app notification presentation in the Dynamic Island.
 */
enum class NotificationLayoutKind {
    STANDARD,
    MESSAGING,
    SOCIAL,
    EMAIL
}

/**
 * Visual styling and capability descriptors for an individual application package.
 * Hex colors are stored as Longs so they can be unit-tested without Android framework classes.
 */
data class NotificationAppStyle(
    val packageName: String,
    val accentColorHex: Long,
    val badgeColorHex: Long = accentColorHex,
    val brandName: String,
    val layoutKind: NotificationLayoutKind = NotificationLayoutKind.STANDARD,
    val openActionLabel: String = "Open App",
    val replyActionLabel: String = "Reply",
    val supportsInlineReply: Boolean = true
)

/**
 * Compose Color accessors for [NotificationAppStyle].
 */
val NotificationAppStyle.accentColor: Color
    get() = Color(accentColorHex)

val NotificationAppStyle.badgeColor: Color
    get() = Color(badgeColorHex)

/**
 * Clean, extensible per-app styling and capability registry for GLOOM Island.
 * Maps application package names to tailored accent colors, branding, layouts,
 * and quick-action behaviors.
 */
object NotificationStyleRegistry {

    /**
     * Default Apple/Cupertino-style fallback for unknown or unregistered packages.
     */
    val DEFAULT_STYLE = NotificationAppStyle(
        packageName = "*",
        accentColorHex = 0xFF0A84FF,
        badgeColorHex = 0xFF007AFF,
        brandName = "Notification",
        layoutKind = NotificationLayoutKind.STANDARD,
        openActionLabel = "Open App",
        replyActionLabel = "Reply",
        supportsInlineReply = true
    )

    private val styles = mutableMapOf<String, NotificationAppStyle>()

    init {
        registerBuiltInStyles()
    }

    private fun registerBuiltInStyles() {
        // WhatsApp Messenger
        register(
            NotificationAppStyle(
                packageName = "com.whatsapp",
                accentColorHex = 0xFF25D366,
                badgeColorHex = 0xFF128C7E,
                brandName = "WhatsApp",
                layoutKind = NotificationLayoutKind.MESSAGING,
                openActionLabel = "Open WhatsApp",
                replyActionLabel = "Reply",
                supportsInlineReply = true
            )
        )

        // WhatsApp Business
        register(
            NotificationAppStyle(
                packageName = "com.whatsapp.w4b",
                accentColorHex = 0xFF25D366,
                badgeColorHex = 0xFF128C7E,
                brandName = "WhatsApp Business",
                layoutKind = NotificationLayoutKind.MESSAGING,
                openActionLabel = "Open WhatsApp",
                replyActionLabel = "Reply",
                supportsInlineReply = true
            )
        )

        // Instagram
        register(
            NotificationAppStyle(
                packageName = "com.instagram.android",
                accentColorHex = 0xFFE1306C,
                badgeColorHex = 0xFF833AB4,
                brandName = "Instagram",
                layoutKind = NotificationLayoutKind.SOCIAL,
                openActionLabel = "Open Instagram",
                replyActionLabel = "Reply",
                supportsInlineReply = true
            )
        )

        // Snapchat
        register(
            NotificationAppStyle(
                packageName = "com.snapchat.android",
                accentColorHex = 0xFFFFFC00,
                badgeColorHex = 0xFFE5E200,
                brandName = "Snapchat",
                layoutKind = NotificationLayoutKind.SOCIAL,
                openActionLabel = "Open Snapchat",
                replyActionLabel = "Reply",
                supportsInlineReply = true
            )
        )

        // Gmail
        register(
            NotificationAppStyle(
                packageName = "com.google.android.gm",
                accentColorHex = 0xFFEA4335,
                badgeColorHex = 0xFFC5221F,
                brandName = "Gmail",
                layoutKind = NotificationLayoutKind.EMAIL,
                openActionLabel = "Open Gmail",
                replyActionLabel = "Reply",
                supportsInlineReply = true
            )
        )

        // Telegram
        register(
            NotificationAppStyle(
                packageName = "org.telegram.messenger",
                accentColorHex = 0xFF2AABEE,
                badgeColorHex = 0xFF229ED9,
                brandName = "Telegram",
                layoutKind = NotificationLayoutKind.MESSAGING,
                openActionLabel = "Open Telegram",
                replyActionLabel = "Reply",
                supportsInlineReply = true
            )
        )
        register(
            NotificationAppStyle(
                packageName = "org.thunderdog.challegram",
                accentColorHex = 0xFF2AABEE,
                badgeColorHex = 0xFF229ED9,
                brandName = "Telegram X",
                layoutKind = NotificationLayoutKind.MESSAGING,
                openActionLabel = "Open Telegram",
                replyActionLabel = "Reply",
                supportsInlineReply = true
            )
        )
        register(
            NotificationAppStyle(
                packageName = "org.telegram.plus",
                accentColorHex = 0xFF2AABEE,
                badgeColorHex = 0xFF229ED9,
                brandName = "Telegram Plus",
                layoutKind = NotificationLayoutKind.MESSAGING,
                openActionLabel = "Open Telegram",
                replyActionLabel = "Reply",
                supportsInlineReply = true
            )
        )

        // SMS / MMS (Google Messages, Samsung, AOSP)
        register(
            NotificationAppStyle(
                packageName = "com.google.android.apps.messaging",
                accentColorHex = 0xFF1A73E8,
                badgeColorHex = 0xFF1557B0,
                brandName = "Messages",
                layoutKind = NotificationLayoutKind.MESSAGING,
                openActionLabel = "Open Messages",
                replyActionLabel = "Reply",
                supportsInlineReply = true
            )
        )
        register(
            NotificationAppStyle(
                packageName = "com.samsung.android.messaging",
                accentColorHex = 0xFF1A73E8,
                badgeColorHex = 0xFF1557B0,
                brandName = "Messages",
                layoutKind = NotificationLayoutKind.MESSAGING,
                openActionLabel = "Open Messages",
                replyActionLabel = "Reply",
                supportsInlineReply = true
            )
        )
        register(
            NotificationAppStyle(
                packageName = "com.android.mms",
                accentColorHex = 0xFF1A73E8,
                badgeColorHex = 0xFF1557B0,
                brandName = "Messages",
                layoutKind = NotificationLayoutKind.MESSAGING,
                openActionLabel = "Open Messages",
                replyActionLabel = "Reply",
                supportsInlineReply = true
            )
        )

        // Microsoft Outlook
        register(
            NotificationAppStyle(
                packageName = "com.microsoft.office.outlook",
                accentColorHex = 0xFF0078D4,
                badgeColorHex = 0xFF005A9E,
                brandName = "Outlook",
                layoutKind = NotificationLayoutKind.EMAIL,
                openActionLabel = "Open Outlook",
                replyActionLabel = "Reply",
                supportsInlineReply = true
            )
        )

        // Discord
        register(
            NotificationAppStyle(
                packageName = "com.discord",
                accentColorHex = 0xFF5865F2,
                badgeColorHex = 0xFF4752C4,
                brandName = "Discord",
                layoutKind = NotificationLayoutKind.MESSAGING,
                openActionLabel = "Open Discord",
                replyActionLabel = "Reply",
                supportsInlineReply = true
            )
        )

        // Slack
        register(
            NotificationAppStyle(
                packageName = "com.Slack",
                accentColorHex = 0xFFE01E5A,
                badgeColorHex = 0xFF4A154B,
                brandName = "Slack",
                layoutKind = NotificationLayoutKind.MESSAGING,
                openActionLabel = "Open Slack",
                replyActionLabel = "Reply",
                supportsInlineReply = true
            )
        )
    }

    /**
     * Registers a new or custom [NotificationAppStyle].
     */
    fun register(style: NotificationAppStyle) {
        styles[style.packageName] = style
    }

    /**
     * Looks up the style for a package name, returning [DEFAULT_STYLE] if not found.
     */
    fun getStyleForPackage(packageName: String?): NotificationAppStyle {
        if (packageName.isNullOrBlank()) return DEFAULT_STYLE
        val direct = styles[packageName]
        if (direct != null) return direct
        val trimmed = packageName.trim().lowercase()
        return styles[trimmed] ?: DEFAULT_STYLE
    }

    /**
     * Checks if a package has an explicit custom style registered.
     */
    fun isRegistered(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return styles.containsKey(packageName) || styles.containsKey(packageName.trim().lowercase())
    }

    /**
     * Resets registry to built-in defaults (useful in tests).
     */
    fun resetToDefaults() {
        styles.clear()
        registerBuiltInStyles()
    }

    /**
     * Clears all styles (useful in tests).
     */
    fun clear() {
        styles.clear()
    }
}
