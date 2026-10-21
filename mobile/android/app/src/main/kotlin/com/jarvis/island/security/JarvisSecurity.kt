package com.jarvis.island.security

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Stores the JARVIS lock: a 3x3 pattern hash, an optional biometric unlock and
 * the app-lock preference. The pattern is never stored in clear text.
 */
object JarvisSecurity {

    private const val PREFS = "jarvis_security"
    private const val KEY_SALT = "pattern_salt"
    private const val KEY_HASH = "pattern_hash"
    private const val KEY_BIOMETRIC = "biometric_enabled"
    private const val KEY_APP_LOCK = "app_lock_enabled"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasPattern(context: Context): Boolean =
        prefs(context).getString(KEY_HASH, null) != null

    fun savePattern(context: Context, pattern: List<Int>) {
        if (pattern.size < 4) return
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hashPattern(pattern, salt)
        prefs(context).edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
    }

    fun verifyPattern(context: Context, pattern: List<Int>): Boolean {
        val prefs = prefs(context)
        val saltText = prefs.getString(KEY_SALT, null) ?: return false
        val hashText = prefs.getString(KEY_HASH, null) ?: return false
        return try {
            val salt = Base64.decode(saltText, Base64.NO_WRAP)
            val expected = Base64.decode(hashText, Base64.NO_WRAP)
            MessageDigest.isEqual(expected, hashPattern(pattern, salt))
        } catch (_: Exception) {
            false
        }
    }

    fun clearPattern(context: Context) {
        prefs(context).edit().remove(KEY_HASH).remove(KEY_SALT).apply()
    }

    fun isBiometricEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BIOMETRIC, false)

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BIOMETRIC, enabled).apply()
    }

    fun isAppLockEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_APP_LOCK, false)

    fun setAppLockEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_APP_LOCK, enabled).apply()
    }

    /** True when the app shell should show the lock screen on launch. */
    fun shouldLock(context: Context): Boolean =
        isAppLockEnabled(context) && (hasPattern(context) || isBiometricEnabled(context))

    private fun hashPattern(pattern: List<Int>, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(pattern.joinToString("-").toByteArray(Charsets.UTF_8))
        repeat(4096) {
            digest.update(digest.digest())
        }
        return digest.digest()
    }
}
