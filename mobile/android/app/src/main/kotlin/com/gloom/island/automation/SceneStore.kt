package com.gloom.island.automation

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Scenes: named sequences of on-device commands.
 *
 * A scene is a routine the user saved, such as "Morning" = open the calendar,
 * scroll down, tap Today. Steps run through the deterministic router only; the
 * model is never consulted mid-scene. The first failing step stops the scene,
 * because continuing after a miss can act on the wrong screen.
 */
object SceneStore {

    private const val PREFS = "gloom_scenes"
    private const val KEY = "scenes"
    private const val MAX_SCENES = 60
    private const val MAX_STEPS = 20

    data class Scene(
        val id: String,
        val name: String,
        val steps: List<String>,
        val createdAtMs: Long,
    )

    data class RunResult(
        val completed: Int,
        val total: Int,
        val failedStep: String?,
        val message: String,
    )

    fun scenes(context: Context): List<Scene> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.optJSONObject(index) ?: continue
                    val stepsArray = json.optJSONArray("steps") ?: JSONArray()
                    val steps = buildList {
                        for (step in 0 until stepsArray.length()) {
                            val value = stepsArray.optString(step).trim()
                            if (value.isNotEmpty()) add(value)
                        }
                    }
                    add(
                        Scene(
                            id = json.optString("id"),
                            name = json.optString("name"),
                            steps = steps,
                            createdAtMs = json.optLong("createdAtMs"),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
            .filter { it.id.isNotEmpty() && it.name.isNotBlank() && it.steps.isNotEmpty() }
            .sortedByDescending { it.createdAtMs }
    }

    fun find(context: Context, query: String): Scene? {
        val lower = query.trim().lowercase()
        if (lower.isEmpty()) return null
        val all = scenes(context)
        return all.firstOrNull { it.name.lowercase() == lower }
            ?: all.firstOrNull { it.name.lowercase().contains(lower) }
    }

    fun save(context: Context, name: String, steps: List<String>): Scene? {
        val trimmedName = name.trim()
        val cleanSteps = steps.map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_STEPS)
        if (trimmedName.isEmpty() || cleanSteps.isEmpty()) return null
        val scene = Scene(
            id = UUID.randomUUID().toString(),
            name = trimmedName,
            steps = cleanSteps,
            createdAtMs = System.currentTimeMillis(),
        )
        val updated = (listOf(scene) + scenes(context).filterNot {
            it.name.equals(trimmedName, ignoreCase = true)
        }).take(MAX_SCENES)
        write(context, updated)
        return scene
    }

    fun delete(context: Context, id: String) {
        write(context, scenes(context).filterNot { it.id == id })
    }

    fun run(context: Context, scene: Scene): RunResult {
        if (scene.steps.isEmpty()) {
            return RunResult(0, 0, null, "That scene has no steps.")
        }
        var completed = 0
        for (step in scene.steps) {
            if (step.startsWith("wait ", ignoreCase = true)) {
                val ms = step.drop(5).trim().toLongOrNull()?.coerceIn(100L, 5_000L) ?: 500L
                Thread.sleep(ms)
                completed += 1
                continue
            }
            val outcome = CommandRouter.handle(context, step)
            if (!outcome.handled || !outcome.success) {
                val reason = outcome.message.ifBlank { "I couldn't run \"$step\"." }
                return RunResult(
                    completed = completed,
                    total = scene.steps.size,
                    failedStep = step,
                    message = "${scene.name} stopped at \"$step\". $reason",
                )
            }
            completed += 1
            // Give the next screen a moment to settle before the next step.
            Thread.sleep(260)
        }
        return RunResult(
            completed = completed,
            total = scene.steps.size,
            failedStep = null,
            message = "Ran ${scene.name}: $completed ${if (completed == 1) "step" else "steps"}.",
        )
    }

    private fun write(context: Context, scenes: List<Scene>) {
        val array = JSONArray()
        scenes.take(MAX_SCENES).forEach { scene ->
            val steps = JSONArray()
            scene.steps.take(MAX_STEPS).forEach { steps.put(it) }
            array.put(
                JSONObject().apply {
                    put("id", scene.id)
                    put("name", scene.name)
                    put("steps", steps)
                    put("createdAtMs", scene.createdAtMs)
                }
            )
        }
        prefs(context).edit().putString(KEY, array.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
