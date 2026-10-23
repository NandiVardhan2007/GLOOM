package com.gloom.island.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotificationStyleRegistryTest {

    @Before
    fun setUp() {
        NotificationStyleRegistry.resetToDefaults()
    }

    @After
    fun tearDown() {
        NotificationStyleRegistry.resetToDefaults()
    }

    @Test
    fun resolvesWhatsAppBuiltInStyle() {
        val style = NotificationStyleRegistry.getStyleForPackage("com.whatsapp")
        assertEquals("com.whatsapp", style.packageName)
        assertEquals("WhatsApp", style.brandName)
        assertEquals(0xFF25D366, style.accentColorHex)
        assertEquals(NotificationLayoutKind.MESSAGING, style.layoutKind)
        assertEquals("Open WhatsApp", style.openActionLabel)
        assertEquals("Reply", style.replyActionLabel)
        assertTrue(style.supportsInlineReply)
    }

    @Test
    fun resolvesWhatsAppBusinessBuiltInStyle() {
        val style = NotificationStyleRegistry.getStyleForPackage("com.whatsapp.w4b")
        assertEquals("com.whatsapp.w4b", style.packageName)
        assertEquals("WhatsApp Business", style.brandName)
        assertEquals(0xFF25D366, style.accentColorHex)
        assertEquals(NotificationLayoutKind.MESSAGING, style.layoutKind)
        assertEquals("Open WhatsApp", style.openActionLabel)
        assertTrue(style.supportsInlineReply)
    }

    @Test
    fun resolvesInstagramBuiltInStyle() {
        val style = NotificationStyleRegistry.getStyleForPackage("com.instagram.android")
        assertEquals("com.instagram.android", style.packageName)
        assertEquals("Instagram", style.brandName)
        assertEquals(0xFFE1306C, style.accentColorHex)
        assertEquals(NotificationLayoutKind.SOCIAL, style.layoutKind)
        assertEquals("Open Instagram", style.openActionLabel)
    }

    @Test
    fun resolvesSnapchatBuiltInStyle() {
        val style = NotificationStyleRegistry.getStyleForPackage("com.snapchat.android")
        assertEquals("com.snapchat.android", style.packageName)
        assertEquals("Snapchat", style.brandName)
        assertEquals(0xFFFFFC00, style.accentColorHex)
        assertEquals(NotificationLayoutKind.SOCIAL, style.layoutKind)
        assertEquals("Open Snapchat", style.openActionLabel)
    }

    @Test
    fun fallsBackToDefaultStyleForUnknownPackage() {
        val unknown = NotificationStyleRegistry.getStyleForPackage("com.some.unregistered.app")
        assertEquals(NotificationStyleRegistry.DEFAULT_STYLE, unknown)
        assertEquals(0xFF0A84FF, unknown.accentColorHex)
        assertEquals("Notification", unknown.brandName)
        assertEquals("Open App", unknown.openActionLabel)
        assertEquals(NotificationLayoutKind.STANDARD, unknown.layoutKind)
    }

    @Test
    fun fallsBackToDefaultStyleForNullOrBlank() {
        assertEquals(NotificationStyleRegistry.DEFAULT_STYLE, NotificationStyleRegistry.getStyleForPackage(null))
        assertEquals(NotificationStyleRegistry.DEFAULT_STYLE, NotificationStyleRegistry.getStyleForPackage(""))
        assertEquals(NotificationStyleRegistry.DEFAULT_STYLE, NotificationStyleRegistry.getStyleForPackage("   "))
    }

    @Test
    fun matchesCaseInsensitively() {
        val styleUpper = NotificationStyleRegistry.getStyleForPackage("COM.WHATSAPP")
        assertEquals("WhatsApp", styleUpper.brandName)

        val styleMixed = NotificationStyleRegistry.getStyleForPackage("Com.Instagram.Android")
        assertEquals("Instagram", styleMixed.brandName)
    }

    @Test
    fun registersCustomStyleSuccessfully() {
        val custom = NotificationAppStyle(
            packageName = "org.telegram.messenger",
            accentColorHex = 0xFF24A1DE,
            badgeColorHex = 0xFF0088CC,
            brandName = "Telegram",
            layoutKind = NotificationLayoutKind.MESSAGING,
            openActionLabel = "Open Telegram"
        )
        NotificationStyleRegistry.register(custom)

        assertTrue(NotificationStyleRegistry.isRegistered("org.telegram.messenger"))
        val resolved = NotificationStyleRegistry.getStyleForPackage("org.telegram.messenger")
        assertEquals("Telegram", resolved.brandName)
        assertEquals(0xFF24A1DE, resolved.accentColorHex)
        assertEquals(0xFF0088CC, resolved.badgeColorHex)
    }

    @Test
    fun isRegisteredReturnsFalseForUnknownOrBlank() {
        assertFalse(NotificationStyleRegistry.isRegistered("com.nonexistent.app"))
        assertFalse(NotificationStyleRegistry.isRegistered(null))
        assertFalse(NotificationStyleRegistry.isRegistered(""))
        assertTrue(NotificationStyleRegistry.isRegistered("com.whatsapp"))
    }

    @Test
    fun notificationIslandEventConversationFieldsDefaultCorrectly() {
        val event = NotificationIslandEvent(
            id = "test-1",
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            title = "Alice",
            text = "Hello there",
            conversationTitle = "Project Chat",
            isGroup = true
        )
        assertEquals("Project Chat", event.conversationTitle)
        assertTrue(event.isGroup)
        assertEquals("Alice", event.title)
    }
}
