package com.jarvis.island.ai

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * JARVIS Cognitive Architecture: CAG (Cache-Augmented Generation) & MAG (Memory-Augmented Generation)
 * with a Self-Improving Feedback Loop.
 *
 * MAG (Memory-Augmented Generation):
 * - Entity Graph: Remembers user relationships, contact aliases (e.g. "Amma" -> "Saraswathi"),
 *   preferred apps, and personal facts.
 * - Procedural Knowledge: Remembers learned multi-step UI recipes for common tasks.
 * - Negative Guidance: Remembers UI paths or selectors that previously failed to prevent loops.
 *
 * CAG (Cache-Augmented Generation):
 * - Instant semantic cache for known intent-to-action graphs.
 * - Injects exact pre-computed execution templates directly into LLM prompt contexts,
 *   reducing latency and token consumption while guaranteeing high execution reliability.
 *
 * Self-Improving Feedback Loop:
 * - Records outcome of every autonomous execution.
 * - Bumps confidence on successful steps and logs corrections on stalled or aborted actions.
 */
object AiMemory {

    private const val TAG = "JarvisAiMemory"
    private const val MEMORY_FILE_NAME = "jarvis_ai_memory.json"
    private const val MAX_CORRECTIONS = 50
    private const val MAX_CACHE_ENTRIES = 100

    data class Entity(
        val key: String,
        val value: String,
        val category: String = "general", // "contact", "app_default", "personal", "general"
        var confidence: Float = 1.0f,
        var usageCount: Int = 1
    )

    data class LearnedRecipe(
        val intentPattern: String,
        val targetApp: String,
        val stepsSummary: List<String>,
        var successCount: Int = 1,
        var failureCount: Int = 0
    )

    data class ActionCorrection(
        val screenContext: String,
        val failedAction: String,
        val advice: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class CachedPlan(
        val intentHash: String,
        val planJson: String,
        val expiresAt: Long = System.currentTimeMillis() + 86400000L // 24 hours
    )

    private val entities = ConcurrentHashMap<String, Entity>()
    private val recipes = ConcurrentHashMap<String, LearnedRecipe>()
    private val corrections = mutableListOf<ActionCorrection>()
    private val semanticCache = ConcurrentHashMap<String, CachedPlan>()
    private var isLoaded = false

    // ── Persistence ──

    fun load(context: Context) {
        if (isLoaded) return
        synchronized(this) {
            if (isLoaded) return
            try {
                val file = File(context.filesDir, MEMORY_FILE_NAME)
                if (!file.exists()) {
                    initDefaults()
                    isLoaded = true
                    return
                }
                val raw = file.readText(Charsets.UTF_8)
                val json = JSONObject(raw)

                // 1. Entities
                val entitiesArr = json.optJSONArray("entities")
                if (entitiesArr != null) {
                    for (i in 0 until entitiesArr.length()) {
                        val obj = entitiesArr.getJSONObject(i)
                        val k = obj.optString("key").lowercase().trim()
                        val v = obj.optString("value")
                        val c = obj.optString("category", "general")
                        val conf = obj.optDouble("confidence", 1.0).toFloat()
                        val u = obj.optInt("usageCount", 1)
                        if (k.isNotEmpty() && v.isNotEmpty()) {
                            entities[k] = Entity(k, v, c, conf, u)
                        }
                    }
                }

                // 2. Recipes
                val recipesArr = json.optJSONArray("recipes")
                if (recipesArr != null) {
                    for (i in 0 until recipesArr.length()) {
                        val obj = recipesArr.getJSONObject(i)
                        val pat = obj.optString("pattern").lowercase().trim()
                        val app = obj.optString("targetApp")
                        val succ = obj.optInt("successCount", 1)
                        val fail = obj.optInt("failureCount", 0)
                        val steps = mutableListOf<String>()
                        val sArr = obj.optJSONArray("steps")
                        if (sArr != null) {
                            for (j in 0 until sArr.length()) {
                                steps.add(sArr.getString(j))
                            }
                        }
                        if (pat.isNotEmpty()) {
                            recipes[pat] = LearnedRecipe(pat, app, steps, succ, fail)
                        }
                    }
                }

                // 3. Corrections
                val corrArr = json.optJSONArray("corrections")
                if (corrArr != null) {
                    corrections.clear()
                    for (i in 0 until corrArr.length()) {
                        val obj = corrArr.getJSONObject(i)
                        corrections.add(
                            ActionCorrection(
                                screenContext = obj.optString("screenContext"),
                                failedAction = obj.optString("failedAction"),
                                advice = obj.optString("advice"),
                                timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                            )
                        )
                    }
                }

                // 4. Semantic Cache
                val cacheArr = json.optJSONArray("cache")
                if (cacheArr != null) {
                    val now = System.currentTimeMillis()
                    for (i in 0 until cacheArr.length()) {
                        val obj = cacheArr.getJSONObject(i)
                        val hash = obj.optString("hash")
                        val plan = obj.optString("plan")
                        val exp = obj.optLong("expiresAt", 0L)
                        if (exp > now && hash.isNotEmpty()) {
                            semanticCache[hash] = CachedPlan(hash, plan, exp)
                        }
                    }
                }

                initDefaults()
                isLoaded = true
                Log.i(TAG, "Loaded memory: ${entities.size} entities, ${recipes.size} recipes, ${corrections.size} corrections")
            } catch (e: Exception) {
                Log.w(TAG, "Error loading AI memory, re-initializing: ${e.message}")
                initDefaults()
                isLoaded = true
            }
        }
    }

    private fun initDefaults() {
        if (!entities.containsKey("music")) {
            entities["music"] = Entity("music", "Spotify", "app_default")
        }
        if (!entities.containsKey("browser")) {
            entities["browser"] = Entity("browser", "Chrome", "app_default")
        }
        if (!entities.containsKey("messaging")) {
            entities["messaging"] = Entity("messaging", "WhatsApp", "app_default")
        }
    }

    fun save(context: Context) {
        synchronized(this) {
            try {
                val json = JSONObject()

                val entArr = JSONArray()
                entities.values.forEach { e ->
                    entArr.put(
                        JSONObject().apply {
                            put("key", e.key)
                            put("value", e.value)
                            put("category", e.category)
                            put("confidence", e.confidence.toDouble())
                            put("usageCount", e.usageCount)
                        }
                    )
                }
                json.put("entities", entArr)

                val recArr = JSONArray()
                recipes.values.forEach { r ->
                    recArr.put(
                        JSONObject().apply {
                            put("pattern", r.intentPattern)
                            put("targetApp", r.targetApp)
                            put("successCount", r.successCount)
                            put("failureCount", r.failureCount)
                            val sArr = JSONArray()
                            r.stepsSummary.forEach { sArr.put(it) }
                            put("steps", sArr)
                        }
                    )
                }
                json.put("recipes", recArr)

                val corrArr = JSONArray()
                corrections.takeLast(MAX_CORRECTIONS).forEach { c ->
                    corrArr.put(
                        JSONObject().apply {
                            put("screenContext", c.screenContext)
                            put("failedAction", c.failedAction)
                            put("advice", c.advice)
                            put("timestamp", c.timestamp)
                        }
                    )
                }
                json.put("corrections", corrArr)

                val cacheArr = JSONArray()
                val now = System.currentTimeMillis()
                semanticCache.values.filter { it.expiresAt > now }.take(MAX_CACHE_ENTRIES).forEach { cp ->
                    cacheArr.put(
                        JSONObject().apply {
                            put("hash", cp.intentHash)
                            put("plan", cp.planJson)
                            put("expiresAt", cp.expiresAt)
                        }
                    )
                }
                json.put("cache", cacheArr)

                val file = File(context.filesDir, MEMORY_FILE_NAME)
                file.writeText(json.toString(2), Charsets.UTF_8)
            } catch (e: Exception) {
                Log.w(TAG, "Failed saving memory: ${e.message}")
            }
        }
    }

    // ── MAG: Memory-Augmented Generation ──

    private val synonymGroups = listOf(
        setOf("mom", "mother", "amma", "mummy", "mommy"),
        setOf("dad", "father", "nanna", "daddy", "appa"),
        setOf("bro", "brother", "anna", "tammudu"),
        setOf("sis", "sister", "akka", "chelli"),
        setOf("wife", "spouse", "partner"),
        setOf("husband", "spouse", "partner"),
        setOf("car", "vehicle", "automobile"),
        setOf("bike", "motorcycle", "scooter"),
        setOf("phone", "number", "mobile", "contact"),
        setOf("mail", "email", "email address"),
        setOf("address", "home", "location"),
        setOf("wifi", "network", "password")
    )

    /**
     * Resolves a spoken or typed name (e.g. "Amma", "Mom", "Bro") against known entities.
     */
    fun resolveEntity(query: String): String? {
        val q = query.trim().lowercase()
        entities[q]?.let { return it.value }

        // Synonym expansion
        val matchingGroup = synonymGroups.firstOrNull { it.contains(q) }
        if (matchingGroup != null) {
            for ((key, entity) in entities) {
                if (matchingGroup.contains(key)) return entity.value
            }
        }
        return null
    }

    /**
     * High-accuracy natural memory query: answers questions about stored facts instantly.
     */
    fun queryMemory(context: Context, query: String): String? {
        load(context)
        val clean = query.trim().lowercase().trim('.', '?', '!')

        // 1. List / show all memories
        if (clean in listOf(
                "what do you remember", "what is in your memory", "list my memories",
                "what memories do you have", "read my memory", "show my memories",
                "what do you know about me", "tell me my memories"
            )
        ) {
            val personalEntities = entities.values.filter { it.category != "app_default" }
            if (personalEntities.isEmpty()) {
                return "I don't have any saved memories yet. You can tell me things like \"Remember that my car number is 1234\"."
            }
            val summary = personalEntities.joinToString(", ") { "${it.key.replaceFirstChar { c -> c.uppercase() }} is ${it.value}" }
            return "Here is what I remember: $summary."
        }

        // 2. Natural question parsing
        val extractedTarget = when {
            clean.startsWith("what is my ") -> clean.removePrefix("what is my ")
            clean.startsWith("what's my ") -> clean.removePrefix("what's my ")
            clean.startsWith("who is my ") -> clean.removePrefix("who is my ")
            clean.startsWith("who's my ") -> clean.removePrefix("who's my ")
            clean.startsWith("tell me my ") -> clean.removePrefix("tell me my ")
            clean.startsWith("do you know my ") -> clean.removePrefix("do you know my ")
            clean.startsWith("where is my ") -> clean.removePrefix("where is my ")
            clean.startsWith("what is the ") -> clean.removePrefix("what is the ")
            clean.startsWith("what's the ") -> clean.removePrefix("what's the ")
            else -> null
        }?.trim()

        val searchTerms = if (extractedTarget != null) {
            listOf(extractedTarget) + extractedTarget.split(" ").filter { it.length > 2 }
        } else {
            clean.split(" ").filter { it.length > 2 }
        }

        // 3. Search direct, substring, and synonyms
        for (target in searchTerms) {
            // Direct
            entities[target]?.let {
                it.usageCount++
                save(context)
                return "Your ${it.key} is ${it.value}."
            }
            // Substring
            for ((key, entity) in entities) {
                if (key.contains(target) || target.contains(key)) {
                    entity.usageCount++
                    save(context)
                    return "Your ${entity.key} is ${entity.value}."
                }
            }
            // Synonyms
            val matchingSynonyms = synonymGroups.firstOrNull { group -> group.contains(target) }
            if (matchingSynonyms != null) {
                for ((key, entity) in entities) {
                    if (matchingSynonyms.any { syn -> key.contains(syn) }) {
                        entity.usageCount++
                        save(context)
                        return "Your ${entity.key} is ${entity.value}."
                    }
                }
            }
        }

        return null
    }

    /**
     * Extracts relevant persistent memory context for a given user request.
     * Injected into the AI System Prompt to ground the model with user preferences,
     * contact aliases, and successful recipes.
     */
    fun getRelevantContext(context: Context, request: String): String {
        load(context)
        val lowerReq = request.lowercase()
        val relevantEntities = mutableListOf<String>()
        val relevantRecipes = mutableListOf<String>()
        val relevantCorrections = mutableListOf<String>()

        // 1. Entity matches
        entities.forEach { (key, entity) ->
            if (lowerReq.contains(key) || key.contains(lowerReq)) {
                entity.usageCount++
                relevantEntities.add("- ${entity.key.replaceFirstChar { it.uppercase() }} -> \"${entity.value}\" (${entity.category})")
            }
        }

        // Fallback: If no direct entity matched, include all personal facts so LLM is fully grounded
        if (relevantEntities.isEmpty()) {
            entities.values.filter { it.category != "app_default" }.take(10).forEach { entity ->
                relevantEntities.add("- ${entity.key.replaceFirstChar { it.uppercase() }} -> \"${entity.value}\" (${entity.category})")
            }
        }

        // 2. Recipe matches
        recipes.forEach { (pattern, recipe) ->
            if (lowerReq.contains(pattern) || pattern.split(" ").all { lowerReq.contains(it) }) {
                relevantRecipes.add("- Task \"$pattern\": Use ${recipe.targetApp} -> Steps: ${recipe.stepsSummary.joinToString(" > ")}")
            }
        }

        // 3. Negative guidance / Corrections
        synchronized(corrections) {
            corrections.takeLast(3).forEach { corr ->
                if (lowerReq.contains(corr.screenContext) || corr.screenContext.isEmpty()) {
                    relevantCorrections.add("- Note: In ${corr.screenContext}: ${corr.advice}")
                }
            }
        }

        if (relevantEntities.isEmpty() && relevantRecipes.isEmpty() && relevantCorrections.isEmpty()) {
            return ""
        }

        return buildString {
            append("\n[JARVIS Long-Term Memory (MAG)]\n")
            if (relevantEntities.isNotEmpty()) {
                append("Known Relations & Preferences:\n")
                relevantEntities.forEach { append(it).append("\n") }
            }
            if (relevantRecipes.isNotEmpty()) {
                append("Learned UI Navigation Recipes:\n")
                relevantRecipes.forEach { append(it).append("\n") }
            }
            if (relevantCorrections.isNotEmpty()) {
                append("Past Execution Lessons:\n")
                relevantCorrections.forEach { append(it).append("\n") }
            }
            append("\n")
        }
    }

    // ── Natural User Learning ──

    /**
     * Learns user relations or facts from natural phrases:
     * - "remember that amma is Saraswathi"
     * - "my mom is Lakshmi"
     * - "remember that my car number is 1234"
     */
    fun learnFromUser(context: Context, speech: String): String? {
        load(context)
        val s = speech.trim().trim('.', '!', '?')
        val lower = s.lowercase()

        val rememberPattern = Regex("""^(?:remember that|save that|keep in mind that)\s+(.+?)\s+(?:is|means|equals|=)\s+(.+)$""", RegexOption.IGNORE_CASE)
        val myPattern = Regex("""^(?:my)\s+(.+?)\s+(?:is)\s+(.+)$""", RegexOption.IGNORE_CASE)

        val match = rememberPattern.matchEntire(s) ?: myPattern.matchEntire(s)
        if (match != null) {
            val key = match.groupValues[1].trim().lowercase().removePrefix("my ").trim()
            val value = match.groupValues[2].trim()
            val category = when {
                key in listOf("mom", "amma", "dad", "father", "mother", "bro", "brother", "sister", "wife", "husband") -> "contact"
                key.contains("number") || key.contains("address") || key.contains("id") || key.contains("car") -> "personal"
                else -> "general"
            }
            entities[key] = Entity(key, value, category, 1.0f, 1)
            save(context)
            Log.i(TAG, "Learned new entity: $key -> $value ($category)")
            return "I've remembered that ${key.replaceFirstChar { it.uppercase() }} is $value."
        }

        return null
    }

    /**
     * Explicitly records a key-value fact into user entity memory.
     */
    fun recordUserFact(context: Context, key: String, value: String, category: String = "general") {
        load(context)
        val k = key.lowercase().trim()
        entities[k] = Entity(k, value.trim(), category, 1.0f, 1)
        save(context)
        Log.i(TAG, "Recorded user fact: $k -> $value ($category)")
    }

    // ── CAG: Cache-Augmented Generation ──

    /**
     * Checks semantic cache for pre-compiled plans to avoid LLM latency.
     */
    fun getCachedPlan(request: String, screenSignature: String): JSONObject? {
        val hash = computeIntentHash(request, screenSignature)
        val entry = semanticCache[hash] ?: return null
        if (entry.expiresAt < System.currentTimeMillis()) {
            semanticCache.remove(hash)
            return null
        }
        return runCatching { JSONObject(entry.planJson) }.getOrNull()
    }

    /**
     * Caches a successful verified step or plan.
     */
    fun cacheStep(context: Context, request: String, screenSignature: String, action: JSONObject) {
        val hash = computeIntentHash(request, screenSignature)
        semanticCache[hash] = CachedPlan(hash, action.toString())
        if (semanticCache.size % 5 == 0) {
            save(context)
        }
    }

    private fun computeIntentHash(request: String, screenSignature: String): String {
        val normReq = request.lowercase().replace(Regex("""[^a-z0-9]"""), "")
        return "${normReq}_${screenSignature.hashCode()}"
    }

    // ── Self-Improving Feedback Loop ──

    /**
     * Records execution outcome: boosts confidence on success, logs negative advice on failure.
     */
    fun recordExecutionOutcome(
        context: Context,
        goal: String,
        steps: List<String>,
        success: Boolean,
        targetApp: String? = null,
        detail: String? = null
    ) {
        load(context)
        val normGoal = goal.trim().lowercase()

        if (success) {
            // Reinforce or learn recipe
            val existing = recipes[normGoal]
            if (existing != null) {
                existing.successCount++
            } else if (steps.isNotEmpty() && targetApp != null) {
                recipes[normGoal] = LearnedRecipe(
                    intentPattern = normGoal,
                    targetApp = targetApp,
                    stepsSummary = steps.takeLast(4).map { it.substringAfter(": ") },
                    successCount = 1,
                    failureCount = 0
                )
            }
            Log.i(TAG, "Execution succeeded for '$goal'. Reinforced memory.")
        } else {
            // Record failure correction
            val existing = recipes[normGoal]
            if (existing != null) {
                existing.failureCount++
            }
            if (!detail.isNullOrBlank()) {
                synchronized(corrections) {
                    if (corrections.size >= MAX_CORRECTIONS) {
                        corrections.removeAt(0)
                    }
                    corrections.add(
                        ActionCorrection(
                            screenContext = targetApp ?: "Screen",
                            failedAction = steps.lastOrNull() ?: "Action",
                            advice = detail
                        )
                    )
                }
            }
            Log.w(TAG, "Execution failed for '$goal': $detail. Stored correction guidance.")
        }

        save(context)
    }

    /**
     * Clears all learned memory (for user privacy / reset).
     */
    fun clearMemory(context: Context) {
        entities.clear()
        recipes.clear()
        corrections.clear()
        semanticCache.clear()
        initDefaults()
        save(context)
        Log.i(TAG, "AI Memory cleared to defaults.")
    }
}
