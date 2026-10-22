package com.jarvis.island.automation

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.jarvis.island.bridge.JarvisLiveBridgeClient
import com.jarvis.island.service.JarvisIslandAccessibilityService

/**
 * On-device self-check.
 *
 * Automation that silently fails because a permission was revoked is worse than
 * automation that refuses. This runs the same checks the engine depends on and
 * reports each one before a command is attempted.
 */
object AutomationSelfCheck {

    enum class Fix {
        ACCESSIBILITY,
        NOTIFICATION_ACCESS,
        OVERLAY,
        CONTACTS,
        EXACT_ALARM,
        BATTERY,
        PAIR,
        NONE,
    }

    data class Check(
        val id: String,
        val title: String,
        val detail: String,
        val ok: Boolean,
        val required: Boolean,
        val fix: Fix,
    )

    fun run(context: Context): List<Check> {
        val checks = ArrayList<Check>(10)
        val accessibility = JarvisIslandAccessibilityService.isRunning
        checks.add(
            Check(
                id = "accessibility",
                title = "Accessibility service",
                detail = if (accessibility) "Connected. JARVIS can read and act on the screen."
                else "Off. Screen reading, taps and typing are unavailable.",
                ok = accessibility,
                required = true,
                fix = Fix.ACCESSIBILITY,
            )
        )
        checks.add(
            Check(
                id = "screenshot",
                title = "Screen snapshots",
                detail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    "Available. Used when an app hides its accessibility tree."
                } else {
                    "Needs Android 11 or newer. This device is on Android ${Build.VERSION.RELEASE}."
                },
                ok = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
                required = false,
                fix = Fix.NONE,
            )
        )
        checks.add(
            Check(
                id = "notifications",
                title = "Notification access",
                detail = if (hasNotificationAccess(context)) "Connected. Alerts and media can reach the island."
                else "Off. The island cannot mirror notifications or now playing.",
                ok = hasNotificationAccess(context),
                required = false,
                fix = Fix.NOTIFICATION_ACCESS,
            )
        )
        checks.add(
            Check(
                id = "overlay",
                title = "Overlay permission",
                detail = if (Settings.canDrawOverlays(context)) "Granted."
                else "Off. The island cannot draw over other apps.",
                ok = Settings.canDrawOverlays(context),
                required = false,
                fix = Fix.OVERLAY,
            )
        )
        checks.add(
            Check(
                id = "contacts",
                title = "Contacts",
                detail = if (hasPermission(context, Manifest.permission.READ_CONTACTS)) {
                    "Granted. \"Call mom\" and \"text Alex\" can find the number."
                } else {
                    "Not granted. Calls and texts work with spoken numbers only."
                },
                ok = hasPermission(context, Manifest.permission.READ_CONTACTS),
                required = false,
                fix = Fix.CONTACTS,
            )
        )
        checks.add(
            Check(
                id = "exact_alarm",
                title = "Exact reminders",
                detail = if (canScheduleExactAlarms(context)) "Allowed. Reminders fire on the minute."
                else "Restricted. Reminders may arrive a few minutes late.",
                ok = canScheduleExactAlarms(context),
                required = false,
                fix = Fix.EXACT_ALARM,
            )
        )
        checks.add(
            Check(
                id = "battery",
                title = "Battery optimisation",
                detail = if (isIgnoringBatteryOptimisations(context)) {
                    "JARVIS is exempt. Background automation keeps working."
                } else {
                    "The system may pause JARVIS in the background. Exempting it improves reliability."
                },
                ok = isIgnoringBatteryOptimisations(context),
                required = false,
                fix = Fix.BATTERY,
            )
        )
        val paired = JarvisLiveBridgeClient.activeClient?.isConnected == true
        checks.add(
            Check(
                id = "desktop",
                title = "Desktop AI",
                detail = if (paired) "Paired. Open-ended requests are answered by your PC."
                else "Not paired. Local commands, notes and reminders still work.",
                ok = paired,
                required = false,
                fix = Fix.PAIR,
            )
        )
        return checks
    }

    /** One honest sentence for voice, listing only what blocks automation. */
    fun spokenSummary(context: Context): String {
        val blocking = run(context).filter { it.required && !it.ok }
        return if (blocking.isEmpty()) {
            "Everything automation needs is ready."
        } else {
            blocking.joinToString(" ") { "${it.title} is off." } + " Open JARVIS in Settings to fix it."
        }
    }

    private fun hasNotificationAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return false
            return manager.isNotificationListenerAccessGranted(
                android.content.ComponentName(
                    context,
                    com.jarvis.island.service.JarvisNotificationListener::class.java
                )
            )
        }
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        return enabled != null && enabled.contains(context.packageName)
    }

    private fun hasPermission(context: Context, permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        return manager.canScheduleExactAlarms()
    }

    private fun isIgnoringBatteryOptimisations(context: Context): Boolean {
        val manager = context.getSystemService(PowerManager::class.java) ?: return false
        return manager.isIgnoringBatteryOptimizations(context.packageName)
    }
}
