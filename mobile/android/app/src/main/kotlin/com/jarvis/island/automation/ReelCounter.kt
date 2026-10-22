package com.jarvis.island.automation

import android.content.Context
import android.content.Intent
import java.time.LocalDate

/** Daily and lifetime Instagram Reels counter, persisted on the device. */
object ReelCounter {

    const val ACTION_REEL_COUNT_CHANGED = "com.jarvis.island.REEL_COUNT_CHANGED"
    private const val PREFS = "jarvis_reel_stats"
    private const val KEY_DAY = "day"
    private const val KEY_TODAY = "today"
    private const val KEY_TOTAL = "total"

    @Synchronized
    fun record(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        val storedDay = prefs.getString(KEY_DAY, "") ?: ""
        val current = if (storedDay == today) prefs.getInt(KEY_TODAY, 0) else 0
        val next = current + 1
        prefs.edit()
            .putString(KEY_DAY, today)
            .putInt(KEY_TODAY, next)
            .putInt(KEY_TOTAL, prefs.getInt(KEY_TOTAL, 0) + 1)
            .apply()
        runCatching {
            context.sendBroadcast(Intent(ACTION_REEL_COUNT_CHANGED).setPackage(context.packageName))
        }
        return next
    }

    fun today(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        return if (prefs.getString(KEY_DAY, "") == today) prefs.getInt(KEY_TODAY, 0) else 0
    }

    fun total(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_TOTAL, 0)

    @Synchronized
    fun resetToday(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_DAY, LocalDate.now().toString())
            .putInt(KEY_TODAY, 0)
            .apply()
        runCatching {
            context.sendBroadcast(Intent(ACTION_REEL_COUNT_CHANGED).setPackage(context.packageName))
        }
    }
}
