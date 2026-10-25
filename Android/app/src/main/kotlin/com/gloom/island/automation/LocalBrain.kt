package com.gloom.island.automation

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.gloom.island.bridge.GloomLiveBridgeClient
import java.time.Instant
import java.time.ZoneId

/**
 * The on-device brain.
 *
 * Everything here runs with no network, no desktop and no model: clock, battery,
 * the notebook, the self-check, and the current screen. The assistant asks this
 * first; a remote model is only used for requests nothing local can answer.
 */
object LocalBrain {

    fun answer(context: Context, text: String): CommandRouter.Outcome {
        val outcome = answerInternal(context, text)
        if (outcome.handled) {
            com.gloom.island.logging.GloomDebugLogger.logEvent(
                context = context,
                userRequest = text,
                handler = "LocalBrain",
                aiResponse = outcome.message,
                isSuccess = outcome.success
            )
        }
        return outcome
    }

    private fun answerInternal(context: Context, text: String): CommandRouter.Outcome {
        val lower = text.trim().lowercase().trim('.', '!', '?')
        if (lower.isEmpty()) return CommandRouter.Outcome(false, false, "")

        when {
            isTimeQuestion(lower) -> return reply(timeOfDay())
            isDateQuestion(lower) -> return reply(date())
            lower.startsWith("what day") -> return reply(dayOfWeek())
            isWifiQuery(lower) -> return reply(SystemTools.getWifiStatus(context))
            isPdfQuery(lower) -> return reply(listPdfs(context, lower))
            isVolumeQuery(lower) -> return reply(SystemTools.getVolumeStatus(context))
            lower.contains("battery") && (lower.contains("level") || lower.contains("charge") || lower.startsWith("how much")) ->
                return reply(battery(context))
            lower == "what can you do" || lower == "help" || lower == "what are your commands" ->
                return reply(capabilities())
            lower == "are you ready" || lower == "self check" || lower == "run a self check" ->
                return reply(AutomationSelfCheck.spokenSummary(context))
            lower.startsWith("what's on my screen") || lower.startsWith("what is on my screen") ||
                lower.startsWith("read the screen") || lower.startsWith("what's on screen") ->
                return reply(describeScreen())
            lower.contains("next reminder") || lower == "what are my reminders" ||
                lower == "my reminders" || lower == "read my reminders" ->
                return reply(reminders(context))
            lower == "read my notes" || lower == "my notes" || lower == "list my notes" ->
                return reply(notes(context))
            lower == "my scenes" || lower == "list my scenes" || lower == "what scenes do i have" ->
                return reply(scenes(context))
            lower.startsWith("am i connected") || lower.startsWith("are you connected") ||
                lower == "desktop status" -> return reply(desktopStatus())
            lower == "yes" || lower == "no" -> return CommandRouter.Outcome(false, false, "")
        }

        // Persistent Long-Term Memory query (MAG)
        val memoryReply = com.gloom.island.ai.AiMemory.queryMemory(context, lower)
        if (memoryReply != null) return reply(memoryReply)

        return CommandRouter.Outcome(false, false, "")
    }

    private fun isTimeQuestion(lower: String): Boolean =
        (lower.contains("time") && (
            lower.contains("what") || lower.contains("tell") || lower.contains("current") ||
            lower.contains("now") || lower.contains("is it") || lower == "time" || lower.startsWith("time")
        )) || lower.startsWith("what time") || lower.endsWith("the time") || lower == "clock"

    private fun isDateQuestion(lower: String): Boolean =
        lower.contains("date") || lower.startsWith("what's today") || lower.startsWith("what is today") ||
        lower == "today" || (lower.contains("day") && (lower.contains("what") || lower.contains("today")))

    private fun isWifiQuery(lower: String): Boolean =
        (lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("network")) &&
        (lower.contains("which") || lower.contains("what") || lower.contains("status") ||
         lower.contains("connect") || lower.contains("name") || lower.contains("am i"))

    private fun isPdfQuery(lower: String): Boolean =
        (lower.contains("pdf") || lower.contains("pdfs")) &&
        (lower.contains("list") || lower.contains("all") || lower.contains("available") ||
         lower.contains("show") || lower.contains("find") || lower.contains("search") || lower.contains("what"))

    private fun isVolumeQuery(lower: String): Boolean =
        lower.contains("volume") && (lower.contains("what") || lower.contains("status") || lower.contains("level") || lower.contains("check"))

    private fun listPdfs(context: Context, lower: String): String {
        val query = if (lower.contains("named ")) {
            lower.substringAfter("named ").trim()
        } else if (lower.contains("for ")) {
            lower.substringAfter("for ").trim()
        } else if (lower.contains("about ")) {
            lower.substringAfter("about ").trim()
        } else ""
        val files = SystemTools.listPdfFiles(context, query)
        if (files.isEmpty()) {
            return if (query.isNotBlank()) "No PDF files matching \"$query\" found." else "No PDF files found on your device."
        }
        val count = files.size
        val fileList = files.take(5).joinToString("\n") { "• ${it.name} (${it.sizeKb} KB)" }
        val suffix = if (count > 5) "\n...and ${count - 5} more." else ""
        return "Found $count PDF ${if (count == 1) "file" else "files"}:\n$fileList$suffix"
    }

    fun liveTimeAndDate(): String {
        return "${timeOfDay()} Today is ${date()}."
    }

    private fun timeOfDay(): String {
        val now = Instant.now().atZone(ZoneId.systemDefault())
        val hour = when {
            now.hour == 0 -> 12
            now.hour > 12 -> now.hour - 12
            else -> now.hour
        }
        val minute = now.minute.toString().padStart(2, '0')
        val suffix = if (now.hour < 12) "AM" else "PM"
        return "It's $hour:$minute $suffix."
    }

    private fun date(): String {
        val now = Instant.now().atZone(ZoneId.systemDefault())
        val month = now.month.name.lowercase().replaceFirstChar { it.uppercase() }
        return "It's $month ${now.dayOfMonth}, ${now.year}."
    }

    private fun dayOfWeek(): String {
        val now = Instant.now().atZone(ZoneId.systemDefault())
        return "It's ${now.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}."
    }

    fun battery(context: Context): String {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return "I can't read the battery right now."
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return "I can't read the battery right now."
        val percent = level * 100 / scale
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return if (charging) "The battery is at $percent percent and charging."
        else "The battery is at $percent percent."
    }

    private fun capabilities(): String =
        "I can open apps, tap and type on screen, scroll, send messages in WhatsApp, " +
            "call and text contacts, set alarms and calendar events, keep notes and reminders, " +
            "run saved scenes, and answer questions with your desktop. " +
            "Try \"remind me to call mom at seven\", or \"note that the wifi password is on the router\"."

    private fun describeScreen(): String {
        if (!ScreenAutomation.isReady()) {
            return "Accessibility is off, so I can't see the screen."
        }
        val elements = ScreenAutomation.refresh()
        val packageName = ScreenAutomation.currentPackage()
        if (elements.isEmpty()) return "I can't read this screen. It may not expose its content."
        val appLabel = appLabel(packageName)
        val labels = elements
            .map { it.label }
            .filter { it.isNotBlank() }
            .distinct()
            .take(5)
        return if (labels.isEmpty()) {
            "$appLabel is on screen."
        } else {
            "$appLabel is on screen, showing ${labels.joinToString(", ")}."
        }
    }

    private fun reminders(context: Context): String {
        val pending = Notebook.pendingReminders(context)
        if (pending.isEmpty()) return "You have no pending reminders."
        val next = pending.first()
        val count = pending.size
        return "You have $count ${if (count == 1) "reminder" else "reminders"}. " +
            "The next one is ${next.text} at ${TimeParser.format(next.triggerAtMs)}."
    }

    private fun notes(context: Context): String {
        val notes = Notebook.notes(context)
        if (notes.isEmpty()) return "You have no notes."
        val recent = notes.take(3).joinToString(". ") { it.body }
        return "You have ${notes.size} ${if (notes.size == 1) "note" else "notes"}. " +
            "The latest ${if (notes.size == 1) "one is" else "ones are"}: $recent"
    }

    private fun scenes(context: Context): String {
        val all = SceneStore.scenes(context)
        if (all.isEmpty()) return "You haven't saved any scenes yet."
        return "Your scenes are ${all.joinToString(", ") { it.name }}."
    }

    private fun desktopStatus(): String {
        val client = GloomLiveBridgeClient.activeClient
        return if (client?.isConnected == true) {
            "I'm connected to your desktop at ${client.desktopIp ?: "its address"}."
        } else {
            "I'm not connected to the desktop. Local commands still work."
        }
    }

    private fun appLabel(packageName: String): String {
        val context = com.gloom.island.service.GloomIslandAccessibilityService.instance ?: return packageName
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            packageName.ifBlank { "This app" }
        }
    }

    private fun reply(text: String) = CommandRouter.Outcome(true, true, text)
}
