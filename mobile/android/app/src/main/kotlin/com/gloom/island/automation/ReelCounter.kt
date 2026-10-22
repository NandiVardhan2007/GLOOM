package com.gloom.island.automation

import android.content.Context
import android.content.Intent
import java.time.LocalDate

/** Daily and lifetime Instagram Reels counter, persisted on the device. */
object ReelCounter {

    const val ACTION_REEL_COUNT_CHANGED = "com.gloom.island.REEL_COUNT_CHANGED"
    private const val PREFS = "gloom_reel_stats"
    private const val KEY_DAY = "day"
    private const val KEY_TODAY = "today"
    private const val KEY_TOTAL = "total"
    private const val DAY_PREFIX = "d_"

    @Synchronized
    fun record(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        val storedDay = prefs.getString(KEY_DAY, "") ?: ""
        val current = if (storedDay == today) prefs.getInt(KEY_TODAY, 0) else 0
        val next = current + 1
        val dayKey = DAY_PREFIX + today
        prefs.edit()
            .putString(KEY_DAY, today)
            .putInt(KEY_TODAY, next)
            .putInt(KEY_TOTAL, prefs.getInt(KEY_TOTAL, 0) + 1)
            .putInt(dayKey, prefs.getInt(dayKey, 0) + 1)
            .apply()
        prune(prefs, today)
        runCatching {
            context.sendBroadcast(Intent(ACTION_REEL_COUNT_CHANGED).setPackage(context.packageName))
        }
        return next
    }

    fun today(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        return if (prefs.getString(KEY_DAY, "") == today) prefs.getInt(KEY_TODAY, 0)
        else prefs.getInt(DAY_PREFIX + today, 0)
    }

    fun total(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_TOTAL, 0)

    /** The last seven days, oldest first, for the Home activity chart. */
    fun last7Days(context: Context): List<Pair<LocalDate, Int>> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now()
        return (6 downTo 0).map { offset ->
            val day = today.minusDays(offset.toLong())
            day to prefs.getInt(DAY_PREFIX + day.toString(), 0)
        }
    }

    @Synchronized
    fun resetToday(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        prefs.edit()
            .putString(KEY_DAY, today)
            .putInt(KEY_TODAY, 0)
            .putInt(DAY_PREFIX + today, 0)
            .apply()
        runCatching {
            context.sendBroadcast(Intent(ACTION_REEL_COUNT_CHANGED).setPackage(context.packageName))
        }
    }

    private fun prune(
        prefs: android.content.SharedPreferences,
        today: String,
    ) {
        val cutoff = LocalDate.parse(today).minusDays(30)
        val stale = prefs.all.keys.filter { key ->
            if (!key.startsWith(DAY_PREFIX)) return@filter false
            val value = key.removePrefix(DAY_PREFIX)
            runCatching { LocalDate.parse(value).isBefore(cutoff) }.getOrDefault(false)
        }
        if (stale.isEmpty()) return
        prefs.edit().apply {
            stale.forEach { remove(it) }
        }.apply()
    }
}
