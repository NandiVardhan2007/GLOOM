package com.gloom.island.bridge

import android.content.Context
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.security.KeyFactory
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocketFactory
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * TLS identity and certificate pinning for the LAN bridge.
 *
 * The desktop is the pairing authority: during PIN confirmation it issues this
 * device a self-signed identity, which is stored here and used to serve the
 * companion HTTPS endpoints. Outbound connections trust exactly one pinned
 * desktop certificate and never fall back to cleartext.
 */
object BridgeTls {

    private const val TAG = "GloomBridgeTls"
    private const val PREFS = "gloom_bridge_tls"
    private const val KEY_DESKTOP_FINGERPRINT = "desktop_fingerprint"
    private const val KEY_IDENTITY_PASSWORD = "identity_password"
    private const val IDENTITY_FILE = "bridge_identity.p12"

    @Volatile
    private var cachedServerFactory: SSLServerSocketFactory? = null

    @Volatile
    private var cachedClientFingerprint: String? = null

    @Volatile
    private var cachedClientFactory: SSLSocketFactory? = null

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun normalizeFingerprint(raw: String?): String? {
        val clean = raw?.filterNot { it.isWhitespace() || it == ':' }?.lowercase() ?: return null
        if (clean.length != 64 || !clean.all { it in "0123456789abcdef" }) return null
        return clean
    }

    fun fingerprintOf(certificate: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Trust-on-first-use pin used by discovery. An existing pin is never replaced
     * here: a spoofed beacon must not be able to redirect the client to a new
     * certificate. Only PIN-confirmed pairing may rotate the pin.
     */
    fun pinDesktopFingerprint(context: Context, raw: String?): Boolean {
        val normalized = normalizeFingerprint(raw) ?: return false
        val existing = desktopFingerprint(context)
        if (existing == null) {
            prefs(context).edit().putString(KEY_DESKTOP_FINGERPRINT, normalized).apply()
            cachedClientFingerprint = null
            cachedClientFactory = null
            return true
        }
        return existing == normalized
    }

    /** Rotates the pin after an authenticated PIN pairing. */
    fun storeDesktopFingerprint(context: Context, raw: String?) {
        val normalized = normalizeFingerprint(raw) ?: return
        prefs(context).edit().putString(KEY_DESKTOP_FINGERPRINT, normalized).apply()
        cachedClientFingerprint = null
        cachedClientFactory = null
    }

    fun clearDesktopPin(context: Context) {
        prefs(context).edit().remove(KEY_DESKTOP_FINGERPRINT).apply()
        cachedClientFingerprint = null
        cachedClientFactory = null
    }

    fun desktopFingerprint(context: Context): String? =
        normalizeFingerprint(prefs(context).getString(KEY_DESKTOP_FINGERPRINT, null))

    fun storeClientIdentity(context: Context, certPem: String, keyPem: String): Boolean {
        return try {
            val certificate = parseCertificate(certPem)
            val privateKey = parsePrivateKey(keyPem)
            val password = ByteArray(24).also { SecureRandom().nextBytes(it) }
                .joinToString("") { "%02x".format(it) }

            val keyStore = KeyStore.getInstance("PKCS12")
            keyStore.load(null, password.toCharArray())
            keyStore.setKeyEntry(
                "bridge",
                privateKey,
                password.toCharArray(),
                arrayOf(certificate)
            )

            FileOutputStream(File(context.filesDir, IDENTITY_FILE)).use {
                keyStore.store(it, password.toCharArray())
            }
            prefs(context).edit().putString(KEY_IDENTITY_PASSWORD, password).apply()
            cachedServerFactory = null
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not store companion TLS identity: ${e.message}")
            false
        }
    }

    fun hasClientIdentity(context: Context): Boolean =
        File(context.filesDir, IDENTITY_FILE).exists()

    private fun parseCertificate(pem: String): X509Certificate {
        val factory = CertificateFactory.getInstance("X.509")
        return pem.byteInputStream(Charsets.UTF_8).use {
            factory.generateCertificate(it) as X509Certificate
        }
    }

    private fun parsePrivateKey(pem: String): java.security.PrivateKey {
        val der = Base64.decode(
            pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace(Regex("\\s"), ""),
            Base64.DEFAULT
        )
        return KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(der))
    }

    private fun loadIdentity(context: Context): KeyStore? {
        return try {
            val file = File(context.filesDir, IDENTITY_FILE)
            if (!file.exists()) return null
            val password = prefs(context).getString(KEY_IDENTITY_PASSWORD, null) ?: return null
            KeyStore.getInstance("PKCS12").apply {
                file.inputStream().use { load(it, password.toCharArray()) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not load companion TLS identity: ${e.message}")
            null
        }
    }

    fun serverSocketFactory(context: Context): SSLServerSocketFactory? {
        cachedServerFactory?.let { return it }
        val keyStore = loadIdentity(context) ?: return null
        return try {
            val password = prefs(context).getString(KEY_IDENTITY_PASSWORD, null) ?: return null
            val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            keyManagerFactory.init(keyStore, password.toCharArray())
            val contextTls = SSLContext.getInstance("TLS")
            contextTls.init(keyManagerFactory.keyManagers, null, SecureRandom())
            contextTls.serverSocketFactory.also { cachedServerFactory = it }
        } catch (e: Exception) {
            Log.w(TAG, "Could not build companion TLS server context: ${e.message}")
            null
        }
    }

    /** Client factory that trusts only the pinned desktop certificate. */
    fun pinnedClientSocketFactory(context: Context): SSLSocketFactory? {
        val fingerprint = desktopFingerprint(context) ?: return null
        if (fingerprint == cachedClientFingerprint) {
            cachedClientFactory?.let { return it }
        }
        val expected = fingerprint.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return try {
            val tlsContext = SSLContext.getInstance("TLS")
            tlsContext.init(null, arrayOf<TrustManager>(PinnedTrustManager(expected)), SecureRandom())
            tlsContext.socketFactory.also {
                cachedClientFingerprint = fingerprint
                cachedClientFactory = it
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not build pinned TLS client context: ${e.message}")
            null
        }
    }

    /**
     * Accept-any TLS context used solely for the unauthenticated `/api/status`
     * discovery probe, which never carries credentials or content.
     */
    fun permissiveClientSocketFactory(): SSLSocketFactory? {
        return try {
            val tlsContext = SSLContext.getInstance("TLS")
            tlsContext.init(null, arrayOf<TrustManager>(PermissiveTrustManager), SecureRandom())
            tlsContext.socketFactory
        } catch (_: Exception) {
            null
        }
    }

    private class PinnedTrustManager(private val expected: ByteArray) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val certificate = chain?.firstOrNull()
                ?: throw CertificateException("Desktop presented no certificate")
            val actual = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
            if (!MessageDigest.isEqual(actual, expected)) {
                throw CertificateException("Pinned desktop certificate mismatch")
            }
            certificate.checkValidity()
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private object PermissiveTrustManager : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
