package com.jarvis.island.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray

/**
 * On-device storage for cloud provider keys.
 *
 * Values live in [EncryptedSharedPreferences] behind an Android Keystore
 * AES-256-GCM master key. There is deliberately no plaintext fallback: if the
 * encrypted store cannot be opened the keys are treated as absent.
 */
object SecureKeyStore {

    const val PROVIDER_GROQ = "groq"
    const val PROVIDER_OPENROUTER = "openrouter"

    private const val TAG = "JarvisKeyStore"
    private const val FILE = "jarvis_secure_keys"
    private const val GROQ_KEYS = "keys_groq"
    private const val OPENROUTER_KEYS = "keys_openrouter"
    private const val GROQ_MODEL = "model_groq"
    private const val OPENROUTER_MODEL = "model_openrouter"

    @Volatile
    private var cached: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences? {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            return try {
                val masterKey = MasterKey.Builder(context.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context.applicationContext,
                    FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                ).also { cached = it }
            } catch (e: Exception) {
                // A corrupted keyset is unrecoverable; discard only the encrypted
                // store (never fall back to plaintext) and try once more.
                Log.w(TAG, "Encrypted key store could not be opened, recreating")
                runCatching {
                    context.applicationContext.deleteSharedPreferences(FILE)
                }
                runCatching {
                    val retried = MasterKey.Builder(context.applicationContext)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()
                    EncryptedSharedPreferences.create(
                        context.applicationContext,
                        FILE,
                        retried,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    ).also { cached = it }
                }.getOrNull()
            }
        }
    }

    private fun keyPreference(provider: String): String = when (provider) {
        PROVIDER_OPENROUTER -> OPENROUTER_KEYS
        else -> GROQ_KEYS
    }

    private fun modelPreference(provider: String): String = when (provider) {
        PROVIDER_OPENROUTER -> OPENROUTER_MODEL
        else -> GROQ_MODEL
    }

    fun keys(context: Context, provider: String): List<String> {
        val raw = prefs(context)?.getString(keyPreference(provider), null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length())
                .map { array.optString(it).trim() }
                .filter { it.isNotEmpty() }
        }.getOrDefault(emptyList())
    }

    /** Splits a pasted blob on commas, semicolons and newlines, then dedupes. */
    fun addKeys(context: Context, provider: String, raw: String): Int {
        val additions = raw
            .split('\n', '\r', ',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (additions.isEmpty()) return 0
        val merged = (keys(context, provider) + additions).distinct()
        val store = prefs(context) ?: return 0
        store.edit().putString(keyPreference(provider), JSONArray(merged).toString()).apply()
        return merged.size
    }

    fun clearKeys(context: Context, provider: String) {
        prefs(context)?.edit()?.remove(keyPreference(provider))?.apply()
    }

    fun removeKey(context: Context, provider: String, key: String) {
        val remaining = keys(context, provider) - key
        val store = prefs(context) ?: return
        store.edit().putString(keyPreference(provider), JSONArray(remaining).toString()).apply()
    }

    fun hasCloudKeys(context: Context): Boolean =
        keys(context, PROVIDER_GROQ).isNotEmpty() || keys(context, PROVIDER_OPENROUTER).isNotEmpty()

    fun model(context: Context, provider: String): String? =
        prefs(context)?.getString(modelPreference(provider), null)?.takeIf { it.isNotBlank() }

    fun setModel(context: Context, provider: String, model: String) {
        val store = prefs(context) ?: return
        if (model.isBlank()) {
            store.edit().remove(modelPreference(provider)).apply()
        } else {
            store.edit().putString(modelPreference(provider), model.trim()).apply()
        }
    }

    /** `gsk_••••••••7f3a` — enough to recognize a key, not enough to use it. */
    fun mask(key: String): String {
        if (key.length <= 8) return "••••••••"
        val prefix = key.take(4)
        val suffix = key.takeLast(4)
        return "$prefix••••••••$suffix"
    }
}
