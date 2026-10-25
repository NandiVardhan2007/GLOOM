package com.gloom.island.root

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Privileged system bridge leveraging KernelSU / root when available on the device.
 * Operates systemlessly with zero overhead when root is absent.
 */
object RootBridge {

    private const val TAG = "GloomRootBridge"
    private const val WATCHDOG_PATH = "/data/adb/service.d/gloom_watchdog.sh"

    @Volatile
    private var cachedRootState: Boolean? = null

    @Volatile
    private var cachedKsuState: Boolean? = null

    data class ChargingMetrics(
        val watts: Float,
        val volts: Float,
        val amperes: Float,
        val tempC: Float,
        val isTurboPower: Boolean
    )

    /**
     * Test whether elevated root execution is permitted.
     * Caches 'true' once confirmed; rechecks if forceRefresh is true.
     */
    fun isRootAvailable(forceRefresh: Boolean = false): Boolean {
        if (!forceRefresh && cachedRootState == true) return true
        val available = try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val finished = process.waitFor(2, TimeUnit.SECONDS)
            if (finished && process.exitValue() == 0) {
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = reader.readLine() ?: ""
                output.contains("uid=0")
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
        cachedRootState = available
        return available
    }

    /**
     * Check if the device is specifically rooted via KernelSU.
     */
    fun isKernelSu(forceRefresh: Boolean = false): Boolean {
        if (!forceRefresh && cachedKsuState == true) return true
        val isKsu = try {
            val procVersion = File("/proc/version")
            if (procVersion.exists() && procVersion.readText().contains("kernelsu", ignoreCase = true)) {
                true
            } else {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "which ksud || ls /data/adb/ksud || ls /data/adb/ksu/bin/ksud"))
                p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0
            }
        } catch (_: Throwable) {
            false
        }
        cachedKsuState = isKsu
        return isKsu
    }

    /**
     * Instantly grants all runtime and special permissions to GLOOM via root:
     * - Draw over apps (SYSTEM_ALERT_WINDOW)
     * - Notification access (BIND_NOTIFICATION_LISTENER_SERVICE)
     * - Accessibility service auto-activation
     * - Battery optimization ignore (deviceidle whitelist)
     * - WRITE_SECURE_SETTINGS, DUMP, PACKAGE_USAGE_STATS
     */
    suspend fun autoGrantAllPermissions(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (!isRootAvailable(forceRefresh = true)) {
            return@withContext Pair(false, "Root / KernelSU access not granted to GLOOM.")
        }
        val commands = listOf(
            "cmd appops set com.gloom.island SYSTEM_ALERT_WINDOW allow",
            "cmd notification allow_listener com.gloom.island/com.gloom.island.service.GloomNotificationListener",
            "settings put secure enabled_accessibility_services com.gloom.island/com.gloom.island.service.GloomIslandAccessibilityService",
            "settings put secure accessibility_enabled 1",
            "settings put global heads_up_notifications_enabled 0",
            "dumpsys deviceidle whitelist +com.gloom.island",
            "pm grant com.gloom.island android.permission.WRITE_SECURE_SETTINGS",
            "pm grant com.gloom.island android.permission.PACKAGE_USAGE_STATS",
            "pm grant com.gloom.island android.permission.DUMP",
            "pm grant com.gloom.island android.permission.READ_LOGS",
            "pm grant com.gloom.island android.permission.READ_CONTACTS",
            "pm grant com.gloom.island android.permission.CALL_PHONE",
            "pm grant com.gloom.island android.permission.ANSWER_PHONE_CALLS",
            "pm grant com.gloom.island android.permission.READ_PHONE_STATE",
            "pm grant com.gloom.island android.permission.MODIFY_AUDIO_SETTINGS"
        )
        val script = commands.joinToString(" && ")
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", script))
            val finished = process.waitFor(6, TimeUnit.SECONDS)
            if (finished && process.exitValue() == 0) {
                applyImmortalOomPriority()
                Pair(true, "All permissions granted and OOM priority elevated to -1000!")
            } else {
                Pair(false, "Permission script exited with code ${process.exitValue()}")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed auto-grant permissions", e)
            Pair(false, "Error: ${e.message}")
        }
    }

    /**
     * Set OOM adjustment score to -1000, giving GLOOM the highest Linux priority
     * and preventing Android's Low Memory Killer from ever killing the overlay service.
     */
    fun applyImmortalOomPriority(pid: Int = android.os.Process.myPid()): Boolean {
        if (!isRootAvailable()) return false
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "echo -1000 > /proc/$pid/oom_score_adj"))
            p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to apply OOM priority", e)
            false
        }
    }

    /**
     * Checks if the persistent boot watchdog script is present in /data/adb/service.d/
     */
    fun isWatchdogInstalled(): Boolean {
        if (!isRootAvailable()) return false
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "test -f $WATCHDOG_PATH"))
            p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Installs or removes persistent KernelSU boot service script in /data/adb/service.d/
     * to keep GLOOM immortal across device reboots.
     */
    suspend fun installBootWatchdog(enable: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!isRootAvailable()) return@withContext false
        try {
            if (enable) {
                val script = "mkdir -p /data/adb/service.d && cat << 'EOF' > " + WATCHDOG_PATH + "\n" +
                    "#!/system/bin/sh\n" +
                    "sleep 15\n" +
                    "PID=$(pidof com.gloom.island)\n" +
                    "if [ -n \"\$PID\" ]; then\n" +
                    "    echo -1000 > /proc/\$PID/oom_score_adj\n" +
                    "fi\n" +
                    "settings put secure enabled_accessibility_services com.gloom.island/com.gloom.island.service.GloomIslandAccessibilityService\n" +
                    "settings put secure accessibility_enabled 1\n" +
                    "settings put global heads_up_notifications_enabled 0\n" +
                    "cmd notification allow_listener com.gloom.island/com.gloom.island.service.GloomNotificationListener\n" +
                    "dumpsys deviceidle whitelist +com.gloom.island\n" +
                    "EOF\nchmod 755 " + WATCHDOG_PATH
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", script))
                p.waitFor(3, TimeUnit.SECONDS) && p.exitValue() == 0
            } else {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "rm -f $WATCHDOG_PATH"))
                p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed watchdog toggle", e)
            false
        }
    }

    /**
     * Cleanly hide or restore status bar icons (clock, notifications) around the camera cutout
     * to prevent clashing with the Dynamic Island.
     */
    suspend fun setStatusBarIconsHidden(hide: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!isRootAvailable()) return@withContext false
        try {
            val cmd = if (hide) {
                "settings put secure icon_blacklist clock,notifications"
            } else {
                "settings delete secure icon_blacklist || settings put secure icon_blacklist \"\""
            }
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to toggle status bar icons", e)
            false
        }
    }

    /**
     * Suppress or restore Android's native heads-up popups (floating banners).
     * When suppressed (true), notifications ONLY appear in the GLOOM Dynamic Island.
     */
    suspend fun setNativeHeadsUpSuppressed(suppress: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!isRootAvailable()) return@withContext false
        val value = if (suppress) "0" else "1"
        try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put global heads_up_notifications_enabled $value"))
            p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to toggle native heads-up notifications", e)
            false
        }
    }

    fun isNativeHeadsUpSuppressed(): Boolean {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "settings get global heads_up_notifications_enabled"))
            p.waitFor(2, TimeUnit.SECONDS)
            val reader = BufferedReader(InputStreamReader(p.inputStream))
            reader.readLine()?.trim() == "0"
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Reads direct kernel battery telemetry from /sys/class/power_supply/battery/.
     * World-readable and executes with sub-millisecond latency.
     */
    fun getLiveChargingMetrics(): ChargingMetrics? {
        return try {
            val currentFile = File("/sys/class/power_supply/battery/current_now")
            val voltageFile = File("/sys/class/power_supply/battery/voltage_now")
            val tempFile = File("/sys/class/power_supply/battery/temp")

            if (!currentFile.exists() || !voltageFile.exists()) return null

            val currentMicroA = currentFile.readText().trim().toLongOrNull() ?: return null
            val voltageMicroV = voltageFile.readText().trim().toLongOrNull() ?: return null
            val tempTenths = if (tempFile.exists()) tempFile.readText().trim().toFloatOrNull() ?: 300f else 300f

            val volts = voltageMicroV / 1_000_000f
            val amps = Math.abs(currentMicroA) / 1_000_000f
            val watts = volts * amps
            val tempC = tempTenths / 10f

            ChargingMetrics(
                watts = watts,
                volts = volts,
                amperes = amps,
                tempC = tempC,
                isTurboPower = watts >= 25f
            )
        } catch (e: Throwable) {
            Log.w(TAG, "Could not read charging metrics", e)
            null
        }
    }

    /**
     * Reads direct CPU / SoC temperature from thermal zones.
     */
    fun getLiveSocTemp(): Float? {
        val candidates = listOf(
            "/sys/class/thermal/thermal_zone31/temp", // Snapdragon 7 Gen 3 cpuss-0
            "/sys/class/thermal/thermal_zone73/temp", // socd
            "/sys/class/thermal/thermal_zone0/temp"
        )
        for (path in candidates) {
            val file = File(path)
            if (file.exists()) {
                val raw = file.readText().trim().toFloatOrNull()
                if (raw != null && raw > 0) {
                    return if (raw > 1000f) raw / 1000f else raw
                }
            }
        }
        return null
    }

    /**
     * Hardware Reboot & Power Actions via Root
     */
    fun reboot(mode: String = ""): Boolean {
        if (!isRootAvailable()) return false
        val cmd = when (mode.lowercase()) {
            "soft", "userspace" -> "setprop ctl.restart zygote"
            "recovery" -> "reboot recovery"
            "bootloader", "fastboot" -> "reboot bootloader"
            "poweroff", "shutdown" -> "reboot -p"
            else -> "reboot"
        }
        return try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Hardware Radios & Controls via Root
     */
    suspend fun setAirplaneMode(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!isRootAvailable()) return@withContext false
        val state = if (enabled) "enable" else "disable"
        try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "cmd connectivity airplane-mode $state"))
            p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun setMobileData(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!isRootAvailable()) return@withContext false
        val state = if (enabled) "enable" else "disable"
        try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "svc data $state"))
            p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun setWifi(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!isRootAvailable()) return@withContext false
        val state = if (enabled) "enabled" else "disabled"
        try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "cmd wifi set-wifi-enabled $state"))
            p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun setBluetooth(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!isRootAvailable()) return@withContext false
        val state = if (enabled) "enable" else "disable"
        try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "cmd bluetooth_manager $state"))
            p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun forceStopPackage(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!isRootAvailable()) return@withContext false
        try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "am force-stop $packageName"))
            p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun cleanRam(): Boolean = withContext(Dispatchers.IO) {
        if (!isRootAvailable()) return@withContext false
        try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "sync && echo 3 > /proc/sys/vm/drop_caches"))
            p.waitFor(3, TimeUnit.SECONDS) && p.exitValue() == 0
        } catch (_: Throwable) {
            false
        }
    }

    fun lockScreen(): Boolean {
        if (!isRootAvailable()) return false
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "input keyevent 26"))
            p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Captures an instant frame buffer screenshot via su without prompting the user
     * for MediaProjection screen recording permissions.
     */
    suspend fun takeSilentScreenshot(): Bitmap? = withContext(Dispatchers.IO) {
        if (!isRootAvailable()) return@withContext null
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "screencap -p"))
            val buffer = ByteArrayOutputStream()
            process.inputStream.use { it.copyTo(buffer) }
            val finished = process.waitFor(3, TimeUnit.SECONDS)
            if (finished && process.exitValue() == 0 && buffer.size() > 0) {
                BitmapFactory.decodeStream(ByteArrayInputStream(buffer.toByteArray()))
            } else {
                null
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Silent screencap failed", e)
            null
        }
    }

    /**
     * Executes a shell command synchronously as root and returns the standard output.
     */
    fun runRootCommand(command: String): String {
        if (!isRootAvailable()) return ""
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor(3, TimeUnit.SECONDS)
            output.trim()
        } catch (e: Throwable) {
            Log.w(TAG, "Root command failed: $command", e)
            ""
        }
    }
}
