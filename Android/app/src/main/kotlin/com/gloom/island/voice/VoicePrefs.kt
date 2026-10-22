package com.gloom.island.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

object VoicePrefs {
    private const val TAG = "VoicePrefs"
    private const val PREFS = "gloom_voice"
    private const val KEY_WAKE_ENABLED = "wake_word_enabled"
    private const val KEY_VOICE_GENDER = "voice_gender"
    private const val KEY_VOICE_PITCH = "voice_pitch"
    private const val KEY_VOICE_RATE = "voice_rate"

    const val GENDER_FEMALE = "female"
    const val GENDER_MALE = "male"

    // Default Siri-like acoustic tuning for female (crisp, high-definition formant clarity)
    const val DEFAULT_FEMALE_PITCH = 1.15f
    // Default masculine acoustic tuning for male (resonant, deeper register)
    const val DEFAULT_MALE_PITCH = 0.82f
    const val DEFAULT_SPEECH_RATE = 1.05f

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isWakeWordEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WAKE_ENABLED, false)

    fun setWakeWordEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_WAKE_ENABLED, enabled).apply()
    }

    fun getVoiceGender(context: Context): String =
        prefs(context).getString(KEY_VOICE_GENDER, GENDER_FEMALE) ?: GENDER_FEMALE

    fun setVoiceGender(context: Context, gender: String) {
        val editor = prefs(context).edit().putString(KEY_VOICE_GENDER, gender)
        // If pitch was uncustomized, apply matching gender default
        if (!prefs(context).contains(KEY_VOICE_PITCH)) {
            editor.putFloat(
                KEY_VOICE_PITCH,
                if (gender == GENDER_MALE) DEFAULT_MALE_PITCH else DEFAULT_FEMALE_PITCH
            )
        }
        editor.apply()
    }

    fun getVoicePitch(context: Context): Float {
        val gender = getVoiceGender(context)
        val defaultPitch = if (gender == GENDER_MALE) DEFAULT_MALE_PITCH else DEFAULT_FEMALE_PITCH
        return prefs(context).getFloat(KEY_VOICE_PITCH, defaultPitch)
    }

    fun setVoicePitch(context: Context, pitch: Float) {
        prefs(context).edit().putFloat(KEY_VOICE_PITCH, pitch.coerceIn(0.5f, 2.0f)).apply()
    }

    fun getVoiceRate(context: Context): Float =
        prefs(context).getFloat(KEY_VOICE_RATE, DEFAULT_SPEECH_RATE)

    fun setVoiceRate(context: Context, rate: Float) {
        prefs(context).edit().putFloat(KEY_VOICE_RATE, rate.coerceIn(0.5f, 2.0f)).apply()
    }

    /**
     * Applies the configured voice model, pitch, and speech rate to an active TextToSpeech engine.
     * Selects high-quality neural voices from the engine matching the requested gender.
     */
    fun applyToTts(tts: TextToSpeech, context: Context) {
        try {
            val gender = getVoiceGender(context)
            val pitch = getVoicePitch(context)
            val rate = getVoiceRate(context)

            tts.setPitch(pitch)
            tts.setSpeechRate(rate)

            val availableVoices = runCatching { tts.voices }.getOrNull()
            if (!availableVoices.isNullOrEmpty()) {
                val locale = Locale.US
                val matchingVoices = availableVoices.filter { v ->
                    v.locale.language == locale.language && !v.isNetworkConnectionRequired
                }

                // Look for neural or high quality voices matching gender
                val selectedVoice = if (gender == GENDER_MALE) {
                    matchingVoices.firstOrNull { v ->
                        val name = v.name.lowercase()
                        (name.contains("male") || name.contains("man") || name.contains("iol") || name.contains("-c-") || name.contains("-d-")) &&
                            !name.contains("female")
                    } ?: matchingVoices.firstOrNull { it.quality >= Voice.QUALITY_VERY_HIGH }
                } else {
                    // Female: Look for natural neural high-clarity voice
                    matchingVoices.firstOrNull { v ->
                        val name = v.name.lowercase()
                        (name.contains("female") || name.contains("woman") || name.contains("sfg") || name.contains("-a-") || name.contains("-f-"))
                    } ?: matchingVoices.firstOrNull { it.quality >= Voice.QUALITY_VERY_HIGH }
                }

                if (selectedVoice != null) {
                    tts.voice = selectedVoice
                    Log.i(TAG, "Configured TTS voice: ${selectedVoice.name} (pitch=$pitch, rate=$rate)")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed applying voice prefs: ${e.message}")
        }
    }
}
