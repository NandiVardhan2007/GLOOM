package com.jarvis.island.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Batch import and validator for keys.json or copied key bundles.
 *
 * Traverses JSON or line-separated keys, tests each key individually
 * against the provider's verification endpoint (0 token cost), and commits
 * only the verified working keys into EncryptedSharedPreferences via SecureKeyStore.
 */
object KeyBatchImporter {

    private const val TAG = "JarvisKeyImporter"

    data class ImportProgress(
        val totalFound: Int,
        val testedCount: Int,
        val currentProvider: String,
        val currentKeyMasked: String,
        val isFinished: Boolean,
        val summary: String? = null
    )

    data class ImportResult(
        val groqValid: Int,
        val groqTotal: Int,
        val openRouterValid: Int,
        val openRouterTotal: Int,
        val totalValid: Int
    )

    suspend fun importJson(
        context: Context,
        jsonString: String,
        onProgress: (ImportProgress) -> Unit
    ): ImportResult = withContext(Dispatchers.IO) {
        val (groqCandidates, openRouterCandidates) = extractCandidateKeys(jsonString)
        val allTasks = mutableListOf<Pair<String, String>>()
        groqCandidates.forEach { allTasks.add(SecureKeyStore.PROVIDER_GROQ to it) }
        openRouterCandidates.forEach { allTasks.add(SecureKeyStore.PROVIDER_OPENROUTER to it) }

        val total = allTasks.size
        var tested = 0
        val validGroq = mutableListOf<String>()
        val validOpenRouter = mutableListOf<String>()

        if (total == 0) {
            val emptyResult = ImportResult(0, 0, 0, 0, 0)
            onProgress(
                ImportProgress(
                    totalFound = 0,
                    testedCount = 0,
                    currentProvider = "",
                    currentKeyMasked = "",
                    isFinished = true,
                    summary = "No valid Groq or OpenRouter keys found in the input."
                )
            )
            return@withContext emptyResult
        }

        for ((providerId, key) in allTasks) {
            val masked = SecureKeyStore.mask(key)
            onProgress(
                ImportProgress(
                    totalFound = total,
                    testedCount = tested,
                    currentProvider = providerId.replaceFirstChar { it.uppercase() },
                    currentKeyMasked = masked,
                    isFinished = false
                )
            )

            val isValid = validateKeySync(providerId, key)
            if (isValid) {
                if (providerId == SecureKeyStore.PROVIDER_GROQ) {
                    validGroq.add(key)
                } else {
                    validOpenRouter.add(key)
                }
            }
            tested++
        }

        // Save valid keys into SecureKeyStore
        if (validGroq.isNotEmpty()) {
            SecureKeyStore.addKeys(context, SecureKeyStore.PROVIDER_GROQ, validGroq.joinToString("\n"))
        }
        if (validOpenRouter.isNotEmpty()) {
            SecureKeyStore.addKeys(context, SecureKeyStore.PROVIDER_OPENROUTER, validOpenRouter.joinToString("\n"))
        }

        val result = ImportResult(
            groqValid = validGroq.size,
            groqTotal = groqCandidates.size,
            openRouterValid = validOpenRouter.size,
            openRouterTotal = openRouterCandidates.size,
            totalValid = validGroq.size + validOpenRouter.size
        )

        val summary = "Tested $total keys: ${result.totalValid} working keys saved (${result.groqValid} Groq, ${result.openRouterValid} OpenRouter)."
        onProgress(
            ImportProgress(
                totalFound = total,
                testedCount = tested,
                currentProvider = "",
                currentKeyMasked = "",
                isFinished = true,
                summary = summary
            )
        )

        result
    }

    private fun validateKeySync(providerId: String, key: String): Boolean {
        val latch = CountDownLatch(1)
        var valid = false
        StandaloneAiClient.validateKey(providerId, key) { ok, _ ->
            valid = ok
            latch.countDown()
        }
        latch.await(12, TimeUnit.SECONDS)
        return valid
    }

    fun extractCandidateKeys(raw: String): Pair<Set<String>, Set<String>> {
        val groqKeys = mutableSetOf<String>()
        val openRouterKeys = mutableSetOf<String>()

        fun inspectString(str: String, hintProvider: String? = null) {
            val trimmed = str.trim().trim('"', '\'')
            if (trimmed.length < 15) return
            when {
                trimmed.startsWith("gsk_") -> groqKeys.add(trimmed)
                trimmed.startsWith("sk-or-v1-") -> openRouterKeys.add(trimmed)
                hintProvider == SecureKeyStore.PROVIDER_GROQ -> groqKeys.add(trimmed)
                hintProvider == SecureKeyStore.PROVIDER_OPENROUTER -> openRouterKeys.add(trimmed)
            }
        }

        fun traverse(element: Any?, hintProvider: String? = null) {
            when (element) {
                is JSONObject -> {
                    val keys = element.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val lowerK = k.lowercase()
                        val newHint = when {
                            lowerK.contains("groq") -> SecureKeyStore.PROVIDER_GROQ
                            lowerK.contains("openrouter") || lowerK.contains("open_router") -> SecureKeyStore.PROVIDER_OPENROUTER
                            else -> hintProvider
                        }
                        traverse(element.opt(k), newHint)
                    }
                }
                is JSONArray -> {
                    for (i in 0 until element.length()) {
                        traverse(element.opt(i), hintProvider)
                    }
                }
                is String -> {
                    inspectString(element, hintProvider)
                }
            }
        }

        try {
            val trimmed = raw.trim()
            if (trimmed.startsWith("{")) {
                traverse(JSONObject(trimmed))
            } else if (trimmed.startsWith("[")) {
                traverse(JSONArray(trimmed))
            } else {
                trimmed.lines().forEach { line -> inspectString(line) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "JSON parsing error, falling back to regex: ${e.message}")
            val gskRegex = Regex("""gsk_[A-Za-z0-9_-]{20,}""")
            val orRegex = Regex("""sk-or-v1-[A-Za-z0-9_-]{20,}""")
            gskRegex.findAll(raw).forEach { groqKeys.add(it.value) }
            orRegex.findAll(raw).forEach { openRouterKeys.add(it.value) }
        }

        return groqKeys to openRouterKeys
    }
}
