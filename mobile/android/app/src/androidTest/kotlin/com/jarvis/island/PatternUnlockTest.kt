package com.jarvis.island

import android.content.Context
import android.graphics.Point
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.jarvis.island.security.JarvisSecurity
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the real app with UIAutomator: opens Security, draws a 4-dot pattern
 * twice, and verifies the saved pattern through the same verifier used by the
 * lock screen.
 */
@RunWith(AndroidJUnit4::class)
class PatternUnlockTest {

    private lateinit var device: UiDevice
    private lateinit var context: Context

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        context = ApplicationProvider.getApplicationContext()
        JarvisSecurity.clearPattern(context)
        JarvisSecurity.setAppLockEnabled(context, false)
        device.executeShellCommand("pm grant com.jarvis.island android.permission.POST_NOTIFICATIONS")
        device.executeShellCommand("pm grant com.jarvis.island android.permission.RECORD_AUDIO")
        device.executeShellCommand("am start -n com.jarvis.island/.MainActivity")
        device.wait(Until.hasObject(By.text("Home")), 8_000)
        if (device.hasObject(By.text("Allow"))) {
            device.findObject(By.text("Allow"))?.click()
        }
        device.findObject(By.text("Home"))?.click()
        Thread.sleep(1_000)
        runCatching {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            device.takeScreenshot(java.io.File(dir, "setup-failure.png"))
        }
        assertTrue("Home should appear", device.wait(Until.hasObject(By.text("JARVIS")), 5_000))
    }

    @Test
    fun drawsAndSavesPatternWithUiAutomator() {
        // Open Settings -> App Lock -> Set Pattern.
        device.findObject(By.text("Settings"))?.click()
        if (!device.wait(Until.hasObject(By.text("App Lock")), 2_000)) {
            device.findObject(By.text("Settings"))?.click()
        }
        assertTrue("App Lock row should appear", device.wait(Until.hasObject(By.text("App Lock")), 5_000))
        device.findObject(By.text("App Lock"))?.click()
        assertTrue("Set Pattern button should appear", device.wait(Until.hasObject(By.text("Set Pattern")), 5_000))
        device.findObject(By.text("Set Pattern"))?.click()
        assertTrue("Pattern drawing screen should appear", device.wait(Until.hasObject(By.text("Draw a new pattern")), 5_000))

        val width = device.displayWidth
        val height = device.displayHeight
        fun dot(index: Int, bounds: android.graphics.Rect) = Point(
            (bounds.left + bounds.width() / 3f * (index % 3) + bounds.width() / 6f).toInt(),
            (bounds.top + bounds.height() / 3f * (index / 3) + bounds.height() / 6f).toInt()
        )

        val firstCanvas = device.wait(Until.findObject(By.desc("3 by 3 pattern lock")), 5_000)
        assertTrue("Pattern canvas should be visible", firstCanvas != null)
        val firstBounds = firstCanvas.visibleBounds

        // 0 -> 1 -> 4 -> 3
        device.swipe(
            arrayOf(
                dot(0, firstBounds),
                dot(1, firstBounds),
                dot(4, firstBounds),
                dot(3, firstBounds)
            ),
            16
        )

        assertTrue("Confirmation step should appear", device.wait(Until.hasObject(By.text("Draw it again to confirm")), 5_000))
        val secondCanvas = device.wait(Until.findObject(By.desc("3 by 3 pattern lock")), 5_000)
        assertTrue("Pattern canvas should still be visible", secondCanvas != null)
        val secondBounds = secondCanvas.visibleBounds
        device.swipe(
            arrayOf(
                dot(0, secondBounds),
                dot(1, secondBounds),
                dot(4, secondBounds),
                dot(3, secondBounds)
            ),
            16
        )

        assertTrue("Pattern saved message should appear", device.wait(Until.hasObject(By.text("Pattern saved.")), 5_000))
        assertTrue(JarvisSecurity.hasPattern(context))

        val pattern = listOf(0, 1, 4, 3)
        assertTrue("The saved pattern must verify", JarvisSecurity.verifyPattern(context, pattern))
        assertTrue("A different pattern must not verify", !JarvisSecurity.verifyPattern(context, listOf(0, 1, 2, 5)))
    }
}
