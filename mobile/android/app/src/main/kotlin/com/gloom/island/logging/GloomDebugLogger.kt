package com.gloom.island.logging

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Centralized on-device diagnostics and execution logger.
 *
 * Records user requests, AI thoughts, responses, executed actions, screen outlines,
 * and outcomes (success/failed/stalled) to both an in-memory ring and persistent JSONL storage
 * for easy inspection, debugging, and continuous improvement.
 */
object GloomDebugLogger {

    private const val TAG = "GloomDebugLogger"
    private const val MAX_IN_MEMORY = 80
    private const val LOG_FILE_NAME = "gloom_execution_trace.jsonl"

    data class ExecutionLogEntry(
        val id: String = java.util.UUID.randomUUID().toString().take(8),
        val timestamp: Long = System.currentTimeMillis(),
        val formattedTime: String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(timestamp)),
        val userRequest: String,
        val channel: String = "Voice",
        val handler: String,
        val aiThought: String = "",
        val aiResponse: String = "",
        val stepsExecuted: List<String> = emptyList(),
        val screenOutlineSnippet: String = "",
        val isSuccess: Boolean,
        val errorReason: String = "",
        val diffSummary: String = ""
    )

    private val inMemoryLogs = ArrayDeque<ExecutionLogEntry>(MAX_IN_MEMORY)
    private var isInitialized = false

    @Synchronized
    fun logEvent(
        context: Context,
        userRequest: String,
        channel: String = "Voice",
        handler: String,
        aiThought: String = "",
        aiResponse: String = "",
        stepsExecuted: List<String> = emptyList(),
        screenOutline: String = "",
        isSuccess: Boolean,
        errorReason: String = "",
        diffSummary: String = ""
    ) {
        val entry = ExecutionLogEntry(
            userRequest = userRequest,
            channel = channel,
            handler = handler,
            aiThought = aiThought,
            aiResponse = aiResponse,
            stepsExecuted = stepsExecuted,
            screenOutlineSnippet = screenOutline.take(500),
            isSuccess = isSuccess,
            errorReason = errorReason,
            diffSummary = diffSummary
        )

        if (inMemoryLogs.size >= MAX_IN_MEMORY) {
            inMemoryLogs.removeFirst()
        }
        inMemoryLogs.addLast(entry)

        try {
            val logDir = File(context.getExternalFilesDir(null), "logs").apply { mkdirs() }
            val logFile = File(logDir, LOG_FILE_NAME)
            val json = JSONObject().apply {
                put("id", entry.id)
                put("time", entry.formattedTime)
                put("timestamp", entry.timestamp)
                put("request", entry.userRequest)
                put("channel", entry.channel)
                put("handler", entry.handler)
                put("thought", entry.aiThought)
                put("response", entry.aiResponse)
                put("success", entry.isSuccess)
                put("error", entry.errorReason)
                put("diff", entry.diffSummary)
                put("steps", JSONArray(entry.stepsExecuted))
                put("outline", entry.screenOutlineSnippet)
            }
            logFile.appendText(json.toString() + "\n", Charsets.UTF_8)
            Log.i(TAG, "Logged execution: [$handler] ${if (isSuccess) "SUCCESS" else "FAIL"} - $userRequest")
        } catch (e: Exception) {
            Log.w(TAG, "Failed writing execution trace: ${e.message}")
        }
    }

    @Synchronized
    fun getRecentLogs(context: Context): List<ExecutionLogEntry> {
        if (!isInitialized) {
            loadFromDisk(context)
            isInitialized = true
        }
        return inMemoryLogs.toList().reversed()
    }

    @Synchronized
    fun clearLogs(context: Context) {
        inMemoryLogs.clear()
        try {
            val logDir = File(context.getExternalFilesDir(null), "logs")
            val logFile = File(logDir, LOG_FILE_NAME)
            if (logFile.exists()) logFile.delete()
        } catch (_: Exception) {}
    }

    fun getLogFile(context: Context): File {
        val logDir = File(context.getExternalFilesDir(null), "logs").apply { mkdirs() }
        return File(logDir, LOG_FILE_NAME)
    }

    private fun loadFromDisk(context: Context) {
        try {
            val logDir = File(context.getExternalFilesDir(null), "logs")
            val logFile = File(logDir, LOG_FILE_NAME)
            if (!logFile.exists()) return

            val lines = logFile.readLines(Charsets.UTF_8).takeLast(MAX_IN_MEMORY)
            for (line in lines) {
                if (line.isBlank()) continue
                val obj = JSONObject(line)
                val stepsList = mutableListOf<String>()
                val sArr = obj.optJSONArray("steps")
                if (sArr != null) {
                    for (i in 0 until sArr.length()) stepsList.add(sArr.getString(i))
                }
                inMemoryLogs.add(
                    ExecutionLogEntry(
                        id = obj.optString("id"),
                        timestamp = obj.optLong("timestamp"),
                        formattedTime = obj.optString("time"),
                        userRequest = obj.optString("request"),
                        channel = obj.optString("channel", "Voice"),
                        handler = obj.optString("handler"),
                        aiThought = obj.optString("thought"),
                        aiResponse = obj.optString("response"),
                        stepsExecuted = stepsList,
                        screenOutlineSnippet = obj.optString("outline"),
                        isSuccess = obj.optBoolean("success"),
                        errorReason = obj.optString("error"),
                        diffSummary = obj.optString("diff")
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed loading logs from disk: ${e.message}")
        }
    }
}
