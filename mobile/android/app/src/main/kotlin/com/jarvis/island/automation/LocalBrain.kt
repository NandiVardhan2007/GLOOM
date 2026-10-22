package com.jarvis.island.automation

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.jarvis.island.bridge.JarvisLiveBridgeClient
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
        val lower = text.trim().lowercase().trim('.', '!', '?')
        if (lower.isEmpty()) return CommandRouter.Outcome(false, false, "")

        when {
            isTimeQuestion(lower) -> return reply(timeOfDay())
            isDateQuestion(lower) -> return reply(date())
            lower.startsWith("what day") -> return reply(dayOfWeek())
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
        return CommandRouter.Outcome(false, false, "")
    }

    private fun isTimeQuestion(lower: String): Boolean =
        lower == "what time is it" ||
            lower == "what's the time" ||
            lower == "the time" ||
            lower == "time" ||
            lower == "tell me the time" ||
            lower == "current time"

    private fun isDateQuestion(lower: String): Boolean =
        lower == "what's the date" ||
            lower == "what is the date" ||
            lower == "today's date" ||
            lower == "date" ||
            lower == "what's today" ||
            lower == "what is today"

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

    private fun battery(context: Context): String {
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
        val client = JarvisLiveBridgeClient.activeClient
        return if (client?.isConnected == true) {
            "I'm connected to your desktop at ${client.desktopIp ?: "its address"}."
        } else {
            "I'm not connected to the desktop. Local commands still work."
        }
    }

    private fun appLabel(packageName: String): String {
        val context = com.jarvis.island.service.JarvisIslandAccessibilityService.instance ?: return packageName
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            packageName.ifBlank { "This app" }
        }
    }

    private fun reply(text: String) = CommandRouter.Outcome(true, true, text)
}
