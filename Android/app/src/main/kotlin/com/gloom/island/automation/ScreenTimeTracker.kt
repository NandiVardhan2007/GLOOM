package com.gloom.island.automation

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import java.time.LocalDate

/**
 * Tracks daily foreground screen time per application using accessibility window state events.
 *
 * Runs locally on the phone with zero cloud transmission or battery drain.
 * Aggregates daily usage statistics for the Apple-styled Activity section on Home.
 */
object ScreenTimeTracker {

    const val ACTION_SCREEN_TIME_CHANGED = "com.gloom.island.SCREEN_TIME_CHANGED"
    private const val PREFS = "gloom_screentime_stats"
    private const val KEY_DAY = "day"
    private const val KEY_TOTAL_SECS = "total_secs"
    private const val PREFIX_DAY_TOTAL = "d_total_"
    private const val PREFIX_APP = "app_"
    private const val MAX_IDLE_GAP_SECS = 900L // 15 mins max single burst

    @Volatile private var currentPackage: String = ""
    @Volatile private var lastSwitchTime: Long = 0L

    data class AppUsage(
        val packageName: String,
        val appName: String,
        val totalMinutes: Long,
        val percentage: Float
    )

    @Synchronized
    fun onEvent(service: AccessibilityService, event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val newPkg = event.packageName?.toString().orEmpty().trim()
        if (newPkg.isEmpty() || newPkg == service.packageName) return

        val now = SystemClock.uptimeMillis()
        if (currentPackage.isNotEmpty() && lastSwitchTime > 0) {
            val deltaSecs = ((now - lastSwitchTime) / 1000L).coerceIn(1L, MAX_IDLE_GAP_SECS)
            if (deltaSecs >= 2L && !isSystemIgnored(currentPackage)) {
                recordSeconds(service, currentPackage, deltaSecs)
            }
        }
        currentPackage = newPkg
        lastSwitchTime = now
    }

    @Synchronized
    fun flush(context: Context) {
        val now = SystemClock.uptimeMillis()
        if (currentPackage.isNotEmpty() && lastSwitchTime > 0) {
            val deltaSecs = ((now - lastSwitchTime) / 1000L).coerceIn(1L, MAX_IDLE_GAP_SECS)
            if (deltaSecs >= 2L && !isSystemIgnored(currentPackage)) {
                recordSeconds(context, currentPackage, deltaSecs)
            }
            lastSwitchTime = now
        }
    }

    private fun recordSeconds(context: Context, pkg: String, seconds: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        val dayTotalKey = PREFIX_DAY_TOTAL + today
        val appKey = PREFIX_APP + today + "_" + pkg

        val currentAppSecs = prefs.getLong(appKey, 0L)
        val currentDayTotal = prefs.getLong(dayTotalKey, 0L)

        prefs.edit()
            .putString(KEY_DAY, today)
            .putLong(dayTotalKey, currentDayTotal + seconds)
            .putLong(appKey, currentAppSecs + seconds)
            .apply()

        runCatching {
            context.sendBroadcast(Intent(ACTION_SCREEN_TIME_CHANGED).setPackage(context.packageName))
        }
    }

    fun getTodayTotalMinutes(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        val totalSecs = prefs.getLong(PREFIX_DAY_TOTAL + today, 0L)
        return totalSecs / 60L
    }

    fun getTodayTopApps(context: Context, limit: Int = 4): List<AppUsage> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        val todayPrefix = PREFIX_APP + today + "_"
        val pm = context.packageManager

        val allEntries = prefs.all
        val appSecsMap = mutableMapOf<String, Long>()
        var totalSecs = 0L

        for ((key, value) in allEntries) {
            if (key.startsWith(todayPrefix) && value is Long) {
                val pkg = key.removePrefix(todayPrefix)
                if (value > 30L && !isSystemIgnored(pkg)) {
                    appSecsMap[pkg] = value
                    totalSecs += value
                }
            }
        }

        if (appSecsMap.isEmpty() || totalSecs <= 0L) return emptyList()

        return appSecsMap.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { (pkg, secs) ->
                val appName = runCatching {
                    val info = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(info).toString()
                }.getOrDefault(pkg.substringAfterLast('.'))

                val mins = secs / 60L
                val pct = (secs.toFloat() / totalSecs.toFloat()).coerceIn(0f, 1f)
                AppUsage(
                    packageName = pkg,
                    appName = appName,
                    totalMinutes = mins,
                    percentage = pct
                )
            }
    }

    fun formatDuration(minutes: Long): String {
        return if (minutes < 60) {
            "${minutes}m"
        } else {
            val hours = minutes / 60
            val rem = minutes % 60
            if (rem > 0) "${hours}h ${rem}m" else "${hours}h"
        }
    }

    private fun isSystemIgnored(pkg: String): Boolean {
        return pkg.contains("launcher", ignoreCase = true) ||
            pkg.contains("nexuslauncher", ignoreCase = true) ||
            pkg.contains("systemui", ignoreCase = true) ||
            pkg == "android" ||
            pkg.contains("keyguard", ignoreCase = true)
    }

    @Synchronized
    fun resetToday(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        val todayPrefix = PREFIX_APP + today + "_"
        val editor = prefs.edit()
        editor.remove(PREFIX_DAY_TOTAL + today)
        for (key in prefs.all.keys) {
            if (key.startsWith(todayPrefix)) {
                editor.remove(key)
            }
        }
        editor.apply()
        runCatching {
            context.sendBroadcast(Intent(ACTION_SCREEN_TIME_CHANGED).setPackage(context.packageName))
        }
    }
}
