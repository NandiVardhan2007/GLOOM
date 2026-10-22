package com.gloom.island.voice

import android.content.Context

object VoicePrefs {
    private const val PREFS = "gloom_voice"
    private const val KEY_WAKE_ENABLED = "wake_word_enabled"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isWakeWordEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WAKE_ENABLED, false)

    fun setWakeWordEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_WAKE_ENABLED, enabled).apply()
    }
}
