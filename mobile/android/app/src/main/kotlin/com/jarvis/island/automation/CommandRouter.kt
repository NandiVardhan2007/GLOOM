package com.jarvis.island.automation

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.jarvis.island.settings.JarvisIslandSettings

/**
 * Turns a spoken or typed instruction into hands-on actions.
 *
 * Commands are matched locally first, because they are deterministic and cost
 * nothing. Anything unmatched is left to the assistant by returning
 * handled = false. Destructive and financial actions are refused before a
 * gesture is injected, and every tap is verified against the screen afterwards.
 */
object CommandRouter {

    data class Outcome(val handled: Boolean, val success: Boolean, val message: String)

    private const val TAG = "JarvisCommandRouter"

    private val typePattern = Regex("""^(?:type|write|input)\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val enterTextPattern = Regex("""^enter\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val scrollPattern = Regex(
        """^scroll\s+(up|down|left|right)(?:\s+(\d{1,2}))?(?:\s+times?)?$""",
        RegexOption.IGNORE_CASE
    )
    private val swipePattern = Regex("""^swipe\s+(up|down|left|right)$""", RegexOption.IGNORE_CASE)
    private val longPressPattern = Regex("""^(?:long[\s-]?press|hold)\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val clickPattern = Regex(
        """^(?:tap|click|press|select|choose)\s+(?:on\s+)?(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val openPattern = Regex("""^(?:open|launch|start|run)\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val searchPattern = Regex(
        """^(?:search|look up|google|find)\s+(?:for\s+)?(.+)$""",
        RegexOption.IGNORE_CASE
    )

    private val reminderPattern = Regex(
        """^(?:remind me|set (?:a )?reminder|reminder)\s+(?:to\s+|about\s+|that\s+)?(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val notePattern = Regex(
        """^(?:note|remember|take a note|save a note|make a note)\s+(?:that\s+|down\s+)?(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val sceneRunPattern = Regex(
        """^(?:run|start|do|play)\s+(?:my\s+)?scene\s+(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val sceneRunSuffixPattern = Regex(
        """^(?:run|start|do|play)\s+(?:my\s+)?(.+?)\s+scene$""",
        RegexOption.IGNORE_CASE
    )
    private val sceneCreatePattern = Regex(
        """^(?:create|make|add|save)\s+(?:a\s+)?scene\s+(?:called\s+|named\s+)?([^:;]+)[:;]\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val sceneDeletePattern = Regex(
        """^(?:delete|remove)\s+scene\s+(.+)$""",
        RegexOption.IGNORE_CASE
    )

    private val smsPattern = Regex(
        """^(?:text|sms)\s+(.+?)\s+(?:saying|with|about|that)\s+(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val smsToPattern = Regex(
        """^(?:send|text|sms)\s+(?:a\s+)?(?:text|sms)\s+to\s+(.+?)\s+(?:saying|with|that|about)\s+(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val whatsAppPattern = Regex(
        """^(?:send|text|message)\s+(?:a\s+)?(?:whatsapp\s+)?(?:message\s+)?to\s+(.+?)\s+(?:saying|with|about|that)\s+(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val whatsAppShortPattern = Regex(
        """^(?:send|text|message)\s+(.+?)\s+(?:saying|with|about|that)\s+(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val callPattern = Regex("""^(?:call|dial|phone)\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val alarmPattern = Regex(
        """^(?:set (?:an )?alarm|wake me(?: up)?)\s+(?:for\s+)?(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val calendarPattern = Regex(
        """^(?:add|create|schedule|put)\s+(?:an?\s+)?(?:event|meeting|appointment)\s+(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val emailPattern = Regex(
        """^(?:email|send an email to)\s+(\S+@\S+)(?:\s+(?:about|saying|with)?\s*(.*))?$""",
        RegexOption.IGNORE_CASE
    )

    private val keyboardCommands = setOf(
        "enter", "send", "submit", "press enter", "hit enter", "press send"
    )
    private val navigationCommands = setOf(
        "back", "go back", "press back", "go home", "home", "home screen",
        "recents", "recent apps"
    )
    private val notificationCommands = setOf(
        "notifications", "open notifications", "show notifications", "notification shade"
    )
    private val quickSettingsCommands = setOf(
        "quick settings", "open quick settings", "control center"
    )
    private val clearCommands = setOf("clear text", "delete text", "clear the field")
    private val cameraCommands = setOf(
        "take a photo", "take a picture", "open camera", "open the camera",
        "launch camera", "camera"
    )
    private val reelShortcuts = setOf(
        "next reel", "next video", "next post", "skip reel",
        "previous reel", "previous video", "previous post"
    )

    /**
     * True when [text] is a command this router will execute. The voice panel
     * uses this to decide whether to get out of the way before acting.
     */
    fun isCommand(text: String): Boolean {
        val command = text.trim().trim('.', '!', '?', ',', ';')
        if (command.isEmpty()) return false
        val lower = command.lowercase()
        if (isReelQuery(lower)) return true
        if (lower in keyboardCommands || lower in navigationCommands ||
            lower in notificationCommands || lower in quickSettingsCommands ||
            lower in clearCommands || lower in cameraCommands || lower in reelShortcuts
        ) {
            return true
        }
        if (lower.startsWith("like ")) return true
        return typePattern.matches(command) ||
            enterTextPattern.matches(command) ||
            scrollPattern.matches(command) ||
            swipePattern.matches(command) ||
            longPressPattern.matches(command) ||
            clickPattern.matches(command) ||
            searchPattern.matches(command) ||
            openPattern.matches(command) ||
            reminderPattern.matches(command) ||
            notePattern.matches(command) ||
            sceneRunPattern.matches(command) ||
            sceneRunSuffixPattern.matches(command) ||
            sceneCreatePattern.matches(command) ||
            sceneDeletePattern.matches(command) ||
            smsPattern.matches(command) ||
            smsToPattern.matches(command) ||
            whatsAppPattern.matches(command) ||
            whatsAppShortPattern.matches(command) ||
            callPattern.matches(command) ||
            alarmPattern.matches(command) ||
            calendarPattern.matches(command) ||
            emailPattern.matches(command)
    }

    private fun isReelQuery(lower: String): Boolean {
        val mentionsReels = lower.contains("reel")
        if (!mentionsReels) return false
        return lower.startsWith("how many") ||
            lower.contains("count") ||
            lower.contains("watched") ||
            lower.contains("reset") ||
            lower.contains("clear")
    }

    fun handle(context: Context, text: String): Outcome {
        val command = text.trim().trim('.', '!', '?', ',', ';')
        if (command.isEmpty()) return Outcome(false, false, "")
        val lower = command.lowercase()
        val settings = JarvisIslandSettings.instance

        // ── Reels counter ──
        if ((lower.contains("reel") || lower.contains("reels")) &&
            (lower.startsWith("how many") || lower.contains("count") || lower.contains("watched"))
        ) {
            if (lower.contains("reset") || lower.contains("clear")) {
                ReelCounter.resetToday(context)
                return Outcome(true, true, "Today's reel count is reset.")
            }
            val today = ReelCounter.today(context)
            val total = ReelCounter.total(context)
            return Outcome(
                true,
                true,
                "You watched $today ${if (today == 1) "reel" else "reels"} today, $total in total."
            )
        }
        if (lower.contains("reel") && (lower.contains("reset") || lower.contains("clear"))) {
            ReelCounter.resetToday(context)
            return Outcome(true, true, "Today's reel count is reset.")
        }

        // ── Notebook ──
        reminderPattern.matchEntire(command)?.let { match ->
            val body = unquote(match.groupValues[1])
            val atMs = TimeParser.parse(body)
                ?: return Outcome(
                    true,
                    false,
                    "When should I remind you? Try \"remind me to call mom at seven\"."
                )
            val task = TimeParser.stripTime(body).ifBlank { body }
            val reminder = Notebook.addReminder(context, task, atMs)
            val scheduled = ReminderScheduler.schedule(context, reminder)
            return if (scheduled) {
                Outcome(true, true, "Reminder set for ${TimeParser.format(atMs)}: $task.")
            } else {
                Outcome(true, false, "I saved the reminder but could not schedule it. Check exact alarm access.")
            }
        }
        notePattern.matchEntire(command)?.let { match ->
            val body = unquote(match.groupValues[1])
            val looksLikeReminder = TimeParser.parse(body) != null &&
                (body.startsWith("to ", ignoreCase = true) || body.startsWith("that i ", ignoreCase = true))
            if (looksLikeReminder) {
                val atMs = TimeParser.parse(body)!!
                val task = TimeParser.stripTime(body).removePrefix("to ").trim()
                val reminder = Notebook.addReminder(context, task, atMs)
                ReminderScheduler.schedule(context, reminder)
                return Outcome(true, true, "Reminder set for ${TimeParser.format(atMs)}: $task.")
            }
            if (body.isBlank()) return Outcome(true, false, "What should I remember?")
            Notebook.addNote(context, body)
            return Outcome(true, true, "Noted.")
        }

        // ── Scenes ──
        sceneCreatePattern.matchEntire(command)?.let { match ->
            val name = match.groupValues[1].trim()
            val steps = match.groupValues[2]
                .split(Regex("""\s*;\s*|\s+then\s+""", RegexOption.IGNORE_CASE))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val scene = SceneStore.save(context, name, steps)
                ?: return Outcome(true, false, "I couldn't save that scene.")
            return Outcome(
                true,
                true,
                "Saved ${scene.name} with ${scene.steps.size} ${if (scene.steps.size == 1) "step" else "steps"}."
            )
        }
        sceneDeletePattern.matchEntire(command)?.let { match ->
            val name = match.groupValues[1].trim()
            val scene = SceneStore.find(context, name)
                ?: return Outcome(true, false, "I couldn't find a scene called $name.")
            SceneStore.delete(context, scene.id)
            return Outcome(true, true, "Deleted the ${scene.name} scene.")
        }
        val sceneName = sceneRunPattern.matchEntire(command)?.groupValues?.get(1)
            ?: sceneRunSuffixPattern.matchEntire(command)?.groupValues?.get(1)
        if (sceneName != null) {
            val scene = SceneStore.find(context, sceneName.trim())
                ?: return Outcome(true, false, "I couldn't find a scene called ${sceneName.trim()}.")
            val result = SceneStore.run(context, scene)
            return Outcome(true, result.failedStep == null, result.message)
        }

        // ── Calls, texts, alarms, calendar, camera ──
        if (lower in cameraCommands) {
            val result = DeviceTools.openCamera(context)
            return Outcome(true, result.success, result.message)
        }
        callPattern.matchEntire(command)?.let { match ->
            val result = DeviceTools.call(context, match.groupValues[1].trim())
            return Outcome(true, result.success, result.message)
        }
        smsToPattern.matchEntire(command)?.let { match ->
            return sendText(context, match.groupValues[1].trim(), match.groupValues[2].trim())
        }
        smsPattern.matchEntire(command)?.let { match ->
            return sendText(context, match.groupValues[1].trim(), match.groupValues[2].trim())
        }
        alarmPattern.matchEntire(command)?.let { match ->
            val phrase = match.groupValues[1].trim()
            val atMs = TimeParser.parse(phrase)
                ?: return Outcome(true, false, "What time should the alarm be? Try \"set an alarm for 7 am\".")
            val zoned = java.time.Instant.ofEpochMilli(atMs)
                .atZone(java.time.ZoneId.systemDefault())
            return DeviceTools.setAlarm(context, zoned.hour, zoned.minute, "JARVIS")
                .let { Outcome(true, it.success, it.message) }
        }
        calendarPattern.matchEntire(command)?.let { match ->
            val phrase = match.groupValues[1].trim()
            val atMs = TimeParser.parse(phrase)
                ?: return Outcome(
                    true,
                    false,
                    "When is it? Try \"add an event lunch tomorrow at one\"."
                )
            val title = TimeParser.stripTime(phrase).ifBlank { "Event" }
            return DeviceTools.addCalendarEvent(context, title, atMs)
                .let { Outcome(true, it.success, it.message) }
        }
        emailPattern.matchEntire(command)?.let { match ->
            val address = match.groupValues[1].trim()
            val body = match.groupValues[2].trim()
            return DeviceTools.email(context, address, "From JARVIS", body)
                .let { Outcome(true, it.success, it.message) }
        }

        // ── WhatsApp message ──
        whatsAppPattern.matchEntire(command)?.let { match ->
            return sendWhatsApp(context, match.groupValues[1].trim(), match.groupValues[2].trim())
        }
        if (lower.startsWith("send ") || lower.startsWith("message ") || lower.startsWith("text ")) {
            if (!lower.contains("email") && !lower.contains("sms")) {
                whatsAppShortPattern.matchEntire(command)?.let { match ->
                    return sendWhatsApp(context, match.groupValues[1].trim(), match.groupValues[2].trim())
                }
            }
        }

        // ── Keyboard and navigation primitives ──
        if (lower in keyboardCommands) {
            if (!accessibilityReady()) return accessibilityRequired()
            val ok = ScreenAutomation.submit()
            return Outcome(true, ok, if (ok) "Sent." else "The field has no Send action.")
        }
        if (lower in navigationCommands) {
            if (!accessibilityReady()) return accessibilityRequired()
            val action = when {
                lower.contains("home") -> "home"
                lower.contains("recent") -> "recents"
                else -> "back"
            }
            val ok = ScreenAutomation.global(action)
            return Outcome(true, ok, if (ok) "Done." else "I couldn't do that.")
        }
        if (lower in notificationCommands) {
            if (!accessibilityReady()) return accessibilityRequired()
            val ok = ScreenAutomation.global("notifications")
            return Outcome(true, ok, if (ok) "Opened notifications." else "I couldn't open notifications.")
        }
        if (lower in quickSettingsCommands) {
            if (!accessibilityReady()) return accessibilityRequired()
            val ok = ScreenAutomation.global("quick_settings")
            return Outcome(true, ok, if (ok) "Opened quick settings." else "I couldn't open quick settings.")
        }
        if (lower in clearCommands) {
            if (!accessibilityReady()) return accessibilityRequired()
            val result = ScreenAutomation.typeText("")
            return Outcome(true, result.success, if (result.success) "Cleared." else result.detail)
        }

        typePattern.matchEntire(command)?.let { match ->
            if (!accessibilityReady()) return accessibilityRequired()
            val value = unquote(match.groupValues[1])
            val result = ScreenAutomation.typeText(value)
            return Outcome(
                true,
                result.success,
                if (result.success) "Typed." else result.detail
            )
        }
        enterTextPattern.matchEntire(command)?.let { match ->
            if (!accessibilityReady()) return accessibilityRequired()
            val value = unquote(match.groupValues[1])
            val result = ScreenAutomation.typeText(value)
            return Outcome(true, result.success, if (result.success) "Typed." else result.detail)
        }

        scrollPattern.matchEntire(command)?.let { match ->
            if (!accessibilityReady()) return accessibilityRequired()
            val direction = match.groupValues[1].lowercase()
            val times = match.groupValues[2].toIntOrNull() ?: 1
            if (direction == "left" || direction == "right") {
                val ok = ScreenAutomation.swipe(direction)
                return Outcome(true, ok, if (ok) "Swiped $direction." else "I couldn't swipe.")
            }
            val moved = ScreenAutomation.scroll(direction, times)
            return Outcome(
                true,
                moved > 0,
                if (moved > 0) "Scrolled ${if (moved == 1) "once" else "$moved times"}." else "This screen does not scroll."
            )
        }
        swipePattern.matchEntire(command)?.let { match ->
            if (!accessibilityReady()) return accessibilityRequired()
            val direction = match.groupValues[1].lowercase()
            val ok = ScreenAutomation.swipe(direction)
            return Outcome(true, ok, if (ok) "Swiped $direction." else "I couldn't swipe.")
        }

        longPressPattern.matchEntire(command)?.let { match ->
            if (!accessibilityReady()) return accessibilityRequired()
            val label = normalizeLabel(match.groupValues[1])
            val element = tapLabelSmart(label)
                ?: return Outcome(true, false, "I couldn't find \"$label\" on the screen.")
            SafetyGuard.assessElement(element.label, element.viewId).takeIf { !it.allowed }?.let {
                return Outcome(true, false, SafetyGuard.refusal(it))
            }
            val ok = ScreenAutomation.longPress(element.eid)
            return Outcome(true, ok, if (ok) "Held ${element.label}." else "I couldn't long-press that.")
        }

        clickPattern.matchEntire(command)?.let { match ->
            if (!accessibilityReady()) return accessibilityRequired()
            val label = normalizeLabel(match.groupValues[1])
            val element = tapLabelSmart(label)
                ?: return Outcome(true, false, "I couldn't find \"$label\" on the screen.")
            SafetyGuard.assessRequest(command).takeIf { !it.allowed }?.let {
                return Outcome(true, false, SafetyGuard.refusal(it))
            }
            SafetyGuard.assessElement(element.label, element.viewId).takeIf { !it.allowed }?.let {
                return Outcome(true, false, SafetyGuard.refusal(it))
            }
            val result = ActionVerifier.tap(
                element.eid,
                verify = settings.verifyActionsEnabled
            )
            return Outcome(result.success, result.success, result.message)
        }

        // ── Reels shortcuts ──
        if (lower in setOf("next reel", "next video", "next post", "skip reel")) {
            if (!accessibilityReady()) return accessibilityRequired()
            val ok = ScreenAutomation.swipe("up")
            return Outcome(true, ok, if (ok) "Next." else "I couldn't advance.")
        }
        if (lower in setOf("previous reel", "previous video", "previous post")) {
            if (!accessibilityReady()) return accessibilityRequired()
            val ok = ScreenAutomation.swipe("down")
            return Outcome(true, ok, if (ok) "Previous." else "I couldn't go back.")
        }
        if (lower.startsWith("like ") || lower == "like this" || lower == "double tap") {
            if (!accessibilityReady()) return accessibilityRequired()
            val ok = ScreenAutomation.clickLabel(listOf("like", "♥"), contains = true)
            return Outcome(true, ok, if (ok) "Liked." else "I couldn't find a Like button.")
        }

        searchPattern.matchEntire(command)?.let { match ->
            if (!accessibilityReady()) return accessibilityRequired()
            val query = match.groupValues[1].trim()
            return search(context, query)
        }

        openPattern.matchEntire(command)?.let { match ->
            val target = match.groupValues[1].trim()
            val label = ScreenAutomation.launch(context, target)
            return if (label != null) {
                Outcome(true, true, "Opening $label.")
            } else {
                Outcome(true, false, "I couldn't find $target.")
            }
        }

        return Outcome(false, false, "")
    }

    private fun sendText(context: Context, contact: String, message: String): Outcome {
        val result = DeviceTools.text(context, contact, message)
        return Outcome(true, result.success, result.message)
    }

    private fun search(context: Context, query: String): Outcome {
        ScreenAutomation.refresh()
        val field = ScreenAutomation.tapLabel(listOf("search", "search…", "search...", "search bar"), contains = true)
            ?: return Outcome(true, false, "I couldn't find a search field on this screen.")
        ActionVerifier.tap(field.eid, verify = JarvisIslandSettings.instance.verifyActionsEnabled)
        Thread.sleep(250)
        val typed = ScreenAutomation.typeText(query)
        if (!typed.success) {
            return Outcome(true, false, "I couldn't type the search: ${typed.detail}")
        }
        Thread.sleep(300)
        val submitted = ScreenAutomation.submit()
        return Outcome(
            true,
            true,
            if (submitted) "Searching for $query." else "I typed $query. Press Search to continue."
        )
    }

    /**
     * Best-effort WhatsApp flow: open the app, search the contact, open the
     * conversation, type the message and press Send. Every step reports
     * honestly when the screen did not offer what it needed.
     */
    private fun sendWhatsApp(context: Context, contact: String, message: String): Outcome {
        if (!accessibilityReady()) return accessibilityRequired()
        val app = when {
            isInstalled(context, "com.whatsapp") -> "whatsapp"
            isInstalled(context, "com.whatsapp.w4b") -> "whatsapp business"
            else -> return Outcome(true, false, "WhatsApp is not installed.")
        }
        val label = ScreenAutomation.launch(context, app)
            ?: return Outcome(true, false, "I couldn't open WhatsApp.")
        Log.i(TAG, "Sending WhatsApp message to $contact via $label")
        Thread.sleep(2_200)

        ScreenAutomation.refresh()
        if (!ScreenAutomation.clickLabel(listOf("search"), contains = true)) {
            return Outcome(true, false, "I couldn't find the WhatsApp search button.")
        }
        Thread.sleep(500)

        val typedContact = ScreenAutomation.typeText(contact)
        if (!typedContact.success) {
            return Outcome(true, false, "I couldn't search for $contact: ${typedContact.detail}")
        }
        Thread.sleep(1_500)

        ScreenAutomation.refresh()
        val result = ScreenAutomation.tapLabel(
            labels = listOf(contact),
            contains = true,
            editable = false
        ) ?: return Outcome(true, false, "I couldn't find $contact in the results.")
        ActionVerifier.tap(result.eid, verify = JarvisIslandSettings.instance.verifyActionsEnabled)
        Thread.sleep(1_600)

        ScreenAutomation.refresh()
        val field = ScreenAutomation.tapLabel(
            labels = listOf("message", "type a message", "message…", "message..."),
            contains = true,
            editable = true
        ) ?: ScreenAutomation.snapshot().firstOrNull { it.editable }
        if (field == null) {
            return Outcome(true, false, "I opened $contact but couldn't find the message box.")
        }
        ScreenAutomation.tap(field.eid)
        Thread.sleep(400)

        val typedMessage = ScreenAutomation.typeText(message)
        if (!typedMessage.success) {
            return Outcome(true, false, "I couldn't type the message: ${typedMessage.detail}")
        }
        Thread.sleep(350)
        val sent = ScreenAutomation.submit() ||
            ScreenAutomation.clickLabel(listOf("send"), contains = true)
        return if (sent) {
            Outcome(true, true, "Message sent to $contact.")
        } else {
            Outcome(true, false, "I typed the message but couldn't press Send.")
        }
    }

    private fun accessibilityReady(): Boolean = ScreenAutomation.isReady()

    private fun tapLabelSmart(label: String): ScreenAutomation.Element? {
        ScreenAutomation.tapLabel(listOf(label), contains = true)?.let { return it }
        val words = label.split(' ')
            .map { it.trim() }
            .filter { it.length >= 3 }
        for (word in words) {
            ScreenAutomation.tapLabel(listOf(word), contains = true)?.let { return it }
        }
        return null
    }

    private fun normalizeLabel(value: String): String {
        var label = value.trim()
        for (article in listOf("the ", "a ", "an ", "my ")) {
            if (label.startsWith(article, ignoreCase = true)) {
                label = label.substring(article.length)
            }
        }
        for (suffix in listOf(" button", " icon", " field", " box")) {
            if (label.endsWith(suffix, ignoreCase = true)) {
                label = label.dropLast(suffix.length)
            }
        }
        return label.trim()
    }

    private fun accessibilityRequired(): Outcome =
        Outcome(true, false, "Enable JARVIS accessibility so I can control the screen.")

    private fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun unquote(value: String): String {
        val trimmed = value.trim()
        return when {
            trimmed.length >= 2 && trimmed.startsWith('"') && trimmed.endsWith('"') ->
                trimmed.substring(1, trimmed.length - 1)
            trimmed.length >= 2 && trimmed.startsWith('\'') && trimmed.endsWith('\'') ->
                trimmed.substring(1, trimmed.length - 1)
            else -> trimmed
        }
    }
}
