package com.jarvis.island.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

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
    private const val KEY_ENCRYPTED_PATTERN = "pattern_encrypted"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "jarvis_pattern_key"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasPattern(context: Context): Boolean =
        prefs(context).getString(KEY_HASH, null) != null

    fun savePattern(context: Context, pattern: List<Int>) {
        if (pattern.size < 4) return
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hashPattern(pattern, salt)
        val editor = prefs(context).edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
        // A reversible encrypted copy is needed only for lock-screen gesture
        // replay; it never leaves the app and is protected by Android Keystore.
        encryptPattern(pattern)?.let { editor.putString(KEY_ENCRYPTED_PATTERN, it) }
        editor.apply()
    }

    /**
     * Returns the saved pattern so the accessibility service can draw it on the
     * device lock screen. Returns null when no encrypted copy exists.
     */
    fun loadPattern(context: Context): List<Int>? {
        val stored = prefs(context).getString(KEY_ENCRYPTED_PATTERN, null) ?: return null
        return try {
            val raw = Base64.decode(stored, Base64.NO_WRAP)
            if (raw.size <= 12) return null
            val iv = raw.copyOfRange(0, 12)
            val data = raw.copyOfRange(12, raw.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, patternKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
                .split(',')
                .mapNotNull { it.toIntOrNull() }
                .takeIf { it.size >= 4 }
        } catch (_: Exception) {
            null
        }
    }

    private fun patternKey(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun encryptPattern(pattern: List<Int>): String? {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, patternKey())
            val data = cipher.doFinal(pattern.joinToString(",").toByteArray(Charsets.UTF_8))
            Base64.encodeToString(cipher.iv + data, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
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
        prefs(context).edit()
            .remove(KEY_HASH)
            .remove(KEY_SALT)
            .remove(KEY_ENCRYPTED_PATTERN)
            .apply()
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
