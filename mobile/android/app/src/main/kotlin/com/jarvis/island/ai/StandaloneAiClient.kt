package com.jarvis.island.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Direct HTTPS chat client for Groq and OpenRouter.
 *
 * Used only when the desktop bridge is unreachable and the user has saved a
 * key on this phone. Keys are read from [SecureKeyStore] per request and are
 * never placed in a build config, a log line, or a plain preference.
 */
object StandaloneAiClient {

    private const val TAG = "JarvisStandaloneAi"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 60_000
    private const val MAX_TOKENS = 800

    data class Provider(
        val id: String,
        val label: String,
        val chatUrl: String,
        val keyCheckUrl: String,
        val defaultModel: String,
        val modelChoices: List<String>,
        val keyHint: String,
    )

    val GROQ = Provider(
        id = SecureKeyStore.PROVIDER_GROQ,
        label = "Groq",
        chatUrl = "https://api.groq.com/openai/v1/chat/completions",
        keyCheckUrl = "https://api.groq.com/openai/v1/models",
        defaultModel = "openai/gpt-oss-120b",
        modelChoices = listOf("openai/gpt-oss-120b", "llama-3.3-70b-versatile", "llama-3.1-8b-instant"),
        keyHint = "gsk_..."
    )

    val OPENROUTER = Provider(
        id = SecureKeyStore.PROVIDER_OPENROUTER,
        label = "OpenRouter",
        chatUrl = "https://openrouter.ai/api/v1/chat/completions",
        keyCheckUrl = "https://openrouter.ai/api/v1/key",
        defaultModel = "anthropic/claude-haiku-5.5",
        modelChoices = listOf(
            "anthropic/claude-haiku-5.5",
            "openai/gpt-4o-mini",
            "google/gemini-2.5-flash"
        ),
        keyHint = "sk-or-v1-..."
    )

    val providers: List<Provider> = listOf(GROQ, OPENROUTER)

    fun provider(id: String): Provider = providers.firstOrNull { it.id == id } ?: GROQ

    fun modelFor(context: Context, provider: Provider): String =
        SecureKeyStore.model(context, provider.id) ?: provider.defaultModel

    private val rotation = ConcurrentHashMap<String, AtomicInteger>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private class Attempt(val reply: String?, val error: String?, val rotate: Boolean)

    /**
     * Tries Groq first, then OpenRouter. Within a provider, keys rotate on
     * auth and rate-limit failures so one exhausted key does not disable the
     * phone. [onResult] lands on the main thread.
     */
    fun request(
        context: Context,
        messages: List<Pair<String, String>>,
        onResult: (reply: String?, error: String?, providerId: String?) -> Unit
    ) {
        val appContext = context.applicationContext
        thread(isDaemon = true, name = "Jarvis-StandaloneAi") {
            var lastError: String? = null
            for (provider in providers) {
                val keys = SecureKeyStore.keys(appContext, provider.id)
                if (keys.isEmpty()) continue
                val model = modelFor(appContext, provider)
                val start = rotation[provider.id]?.get() ?: 0
                for (offset in keys.indices) {
                    val index = (start + offset) % keys.size
                    val attempt = attemptChat(provider, model, keys[index], messages)
                    if (attempt.reply != null) {
                        rotation.getOrPut(provider.id) { AtomicInteger(0) }.set(index)
                        mainHandler.post { onResult(attempt.reply, null, provider.id) }
                        return@thread
                    }
                    lastError = "${provider.label}: ${attempt.error ?: "request failed"}"
                    if (!attempt.rotate) break
                }
            }
            val message = lastError
                ?: "No cloud API key saved. Add one in Settings > AI & Brain."
            mainHandler.post { onResult(null, message, null) }
        }
    }

    /** Checks a key that is not yet saved. [onResult] lands on the main thread. */
    fun validateKey(providerId: String, key: String, onResult: (ok: Boolean, message: String) -> Unit) {
        val provider = provider(providerId)
        thread(isDaemon = true, name = "Jarvis-KeyCheck") {
            var connection: HttpURLConnection? = null
            val (ok, message) = try {
                val conn = (URL(provider.keyCheckUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer ${key.trim()}")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "JARVIS-Android")
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = 15_000
                }
                connection = conn
                when (val code = conn.responseCode) {
                    in 200..299 -> true to "Key verified"
                    401, 403 -> false to "Key rejected (HTTP $code)"
                    429 -> true to "Key accepted, currently rate limited"
                    else -> false to "Provider answered HTTP $code"
                }
            } catch (e: Exception) {
                Log.w(TAG, "Key check failed: ${e.javaClass.simpleName}")
                false to (e.message ?: "Could not reach ${provider.label}")
            } finally {
                runCatching { connection?.disconnect() }
            }
            mainHandler.post { onResult(ok, message) }
        }
    }

    private fun attemptChat(
        provider: Provider,
        model: String,
        key: String,
        messages: List<Pair<String, String>>,
    ): Attempt {
        var connection: HttpURLConnection? = null
        return try {
            val payload = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    messages.forEach { (role, content) ->
                        put(JSONObject().apply {
                            put("role", role)
                            put("content", content)
                        })
                    }
                })
                put("temperature", 0.4)
                put("max_tokens", MAX_TOKENS)
                put("stream", false)
            }.toString().toByteArray(Charsets.UTF_8)

            connection = (URL(provider.chatUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer ${key.trim()}")
                setRequestProperty("User-Agent", "JARVIS-Android")
                if (provider.id == SecureKeyStore.PROVIDER_OPENROUTER) {
                    setRequestProperty("HTTP-Referer", "https://github.com/NandiVardhan2007/JARVIS")
                    setRequestProperty("X-Title", "JARVIS")
                }
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
            }
            val conn = connection
            conn.outputStream.use { it.write(payload) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (code in 200..299) {
                val reply = runCatching {
                    JSONObject(body)
                        .optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content")
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                }.getOrNull()
                if (reply != null) {
                    Attempt(reply, null, false)
                } else {
                    Attempt(null, "The provider returned an empty reply.", true)
                }
            } else {
                Attempt(null, errorMessage(body, code), code in ROTATE_CODES)
            }
        } catch (e: Exception) {
            Log.w(TAG, "${provider.label} request failed: ${e.javaClass.simpleName}")
            Attempt(null, e.message ?: "Could not reach ${provider.label}", false)
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    private val ROTATE_CODES = setOf(401, 402, 403, 429)

    private fun errorMessage(body: String, code: Int): String {
        val parsed = runCatching {
            val json = JSONObject(body)
            json.optJSONObject("error")?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: json.optString("message").takeIf { it.isNotBlank() }
        }.getOrNull()
        return parsed ?: "Provider error (HTTP $code)"
    }
}
