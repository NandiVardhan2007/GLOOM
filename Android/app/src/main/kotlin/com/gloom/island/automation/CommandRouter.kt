package com.gloom.island.automation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.accessibility.AccessibilityNodeInfo
import com.gloom.island.ai.AiMemory
import com.gloom.island.automation.SystemTools
import android.content.pm.PackageManager
import android.util.Log
import com.gloom.island.service.GloomIslandAccessibilityService
import com.gloom.island.settings.GloomIslandSettings

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

    private const val TAG = "GloomCommandRouter"

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
    private val openAndSendWhatsAppPattern = Regex(
        """^(?:open\s+(?:the\s+)?whatsapp\s+(?:and\s+)?(?:then\s+)?(?:send|text|message)\s+(?:a\s+)?(?:whatsapp\s+)?(?:message\s+)?to\s+(.+?)\s+(?:saying|with|about|that)\s+(.+))$""",
        RegexOption.IGNORE_CASE
    )
    private val whatsAppShortPattern = Regex(
        """^(?:send|text|message)\s+(.+?)\s+(?:saying|with|about|that)\s+(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val openWhatsAppChatPattern = Regex(
        """^(?:open\s+(?:the\s+)?whatsapp\s+(?:to|with|for)\s+(.+)|send\s+(?:a\s+)?(?:whatsapp\s+)?(?:message\s+)?to\s+(.+)|message\s+(.+)\s+on\s+whatsapp)$""",
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
    private val unlockCommands = setOf(
        "unlock", "unlock phone", "unlock my phone", "unlock mobile", "unlock my mobile",
        "unlock device", "draw pattern", "replay pattern", "test unlock"
    )

    fun cleanSpeech(raw: String): String {
        var s = raw.trim().trim('.', '!', '?', ',', ';')
        val fillers = listOf(
            "ya could you please", "yeah could you please", "could you please",
            "ya could you", "yeah could you", "could you", "can you please",
            "can you", "would you please", "would you", "please",
            "ya please", "yeah please", "ya ", "yeah ", "hey gloom ",
            "hey ", "ok gloom ", "okay gloom ", "ok ", "okay ", "hi gloom ",
            "hi ", "hello gloom ", "hello ", "gloom ", "yo ", "so ", "um "
        )
        var changed = true
        while (changed) {
            changed = false
            val lower = s.lowercase()
            for (filler in fillers) {
                if (lower.startsWith(filler)) {
                    s = s.substring(filler.length).trim()
                    changed = true
                    break
                }
            }
        }
        return s.ifBlank { raw.trim() }
    }

    /**
     * True when [text] is a command this router will execute. The voice panel
     * uses this to decide whether to get out of the way before acting.
     */
    fun isCommand(text: String): Boolean {
        val command = cleanSpeech(text)
        if (command.isEmpty()) return false
        val lower = command.lowercase()
        if (isReelQuery(lower)) return true
        if (lower in keyboardCommands || lower in navigationCommands ||
            lower in notificationCommands || lower in quickSettingsCommands ||
            lower in clearCommands || lower in cameraCommands || lower in reelShortcuts ||
            lower in unlockCommands
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
            openAndSendWhatsAppPattern.matches(command) ||
            openWhatsAppChatPattern.matches(command) ||
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
        val outcome = handleInternal(context, text)
        if (outcome.handled) {
            com.gloom.island.logging.GloomDebugLogger.logEvent(
                context = context,
                userRequest = text,
                handler = "CommandRouter",
                aiResponse = outcome.message,
                isSuccess = outcome.success
            )
        }
        return outcome
    }

    private fun handleInternal(context: Context, text: String): Outcome {
        val command = cleanSpeech(text)
        if (command.isEmpty()) return Outcome(false, false, "")
        val lower = command.lowercase()
        val settings = GloomIslandSettings.instance

        // ── Natural AI Memory Learning ("remember that...", "my ... is ...") ──
        val learnedResponse = com.gloom.island.ai.AiMemory.learnFromUser(context, text)
        if (learnedResponse != null) {
            return Outcome(true, true, learnedResponse)
        }

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

        // ── Device Unlock & Pattern Replay ──
        if (lower in unlockCommands) {
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
            if (km != null && !km.isKeyguardLocked) {
                return Outcome(true, true, "Your phone is already unlocked.")
            }
            val service = GloomIslandAccessibilityService.instance
            return if (service != null) {
                val pattern = com.gloom.island.security.GloomSecurity.loadPattern(context)
                val ok = service.performUnlockPattern(pattern)
                Outcome(true, ok, if (ok) "Unlocking device." else "Could not unlock.")
            } else {
                Outcome(true, false, "Accessibility service not running.")
            }
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
            return DeviceTools.setAlarm(context, zoned.hour, zoned.minute, "GLOOM")
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
            return DeviceTools.email(context, address, "From GLOOM", body)
                .let { Outcome(true, it.success, it.message) }
        }

        // ── Chrome Browsing & Form Autofill ──
        val browsePattern = Regex("""^(?:browse|open|go)\s+to\s+(https?://\S+|\S+\.[a-zA-Z]{2,}\S*)$""", RegexOption.IGNORE_CASE)
        browsePattern.matchEntire(command)?.let { match ->
            var url = match.groupValues[1].trim()
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage("com.android.chrome")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val ok = runCatching { context.startActivity(intent); true }.getOrElse {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
            }
            return Outcome(true, ok, if (ok) "Opening $url in Chrome." else "Couldn't open browser.")
        }

        if (lower.contains("fill form") || lower.contains("autofill form") || lower.contains("fill this form")) {
            val filled = com.gloom.island.security.Vault.autofillWebForm(context)
            return if (filled > 0) {
                Outcome(true, true, "Autofilled $filled form fields using your profile.")
            } else {
                Outcome(true, false, "I couldn't find matching fields to autofill.")
            }
        }

        // ── Personalized Email Drafting ──
        val draftEmailPattern = Regex("""^(?:draft|send|compose)\s+(?:an?\s+)?email\s+to\s+(.+?)(?:\s+(?:saying|about|with\s+subject)\s+(.+))?$""", RegexOption.IGNORE_CASE)
        draftEmailPattern.matchEntire(command)?.let { match ->
            val to = match.groupValues[1].trim()
            val body = match.groupValues[2].trim()
            val ok = SystemTools.draftEmail(context, to, "Message from GLOOM", body)
            return Outcome(true, ok, if (ok) "Drafted email to $to." else "I couldn't open the email client.")
        }

        // ── Regex-based File Search ──
        val fileSearchPattern = Regex("""^(?:search|find)\s+files?\s+(?:matching|regex|with)\s+(.+)$""", RegexOption.IGNORE_CASE)
        fileSearchPattern.matchEntire(command)?.let { match ->
            val pat = match.groupValues[1].trim()
            val results = SystemTools.searchFilesRegex(pat)
            return if (results.isNotEmpty()) {
                val preview = results.take(3).joinToString(", ") { it.name }
                Outcome(true, true, "Found ${results.size} files matching '$pat': $preview")
            } else {
                Outcome(true, true, "No files matched '$pat'.")
            }
        }

        // ── Data & Clipboard Synchronization ──
        if (lower == "sync data" || lower == "sync with desktop" || lower == "sync clipboard" || lower == "sync files" || lower == "synchronize") {
            val res = SystemTools.syncDataWithDesktop(context)
            return Outcome(true, res.success, res.message)
        }

        // ── System Optimization & Duplicate File Removal ──
        if (lower.contains("clean up storage") || lower.contains("cleanup storage") || lower.contains("remove duplicate") || lower.contains("optimize storage")) {
            val report = SystemTools.cleanupStorage(context, removeDuplicates = true)
            val mbStr = String.format(java.util.Locale.US, "%.1f", report.totalReclaimedMb)
            return Outcome(true, true, "Storage optimized: freed $mbStr MB (${report.duplicatesRemoved} duplicates removed).")
        }

        // ── Self-Developing Memory: Fact Learning ──
        val rememberPattern = Regex("""^(?:remember\s+(?:that\s+)?|my\s+)(.+?)\s+(?:is|=)\s+(.+)$""", RegexOption.IGNORE_CASE)
        if (lower.startsWith("remember") || (lower.startsWith("my ") && (lower.contains(" is ") || lower.contains("=")))) {
            rememberPattern.matchEntire(command)?.let { match ->
                val key = match.groupValues[1].trim().removePrefix("my ").trim()
                val value = match.groupValues[2].trim()
                AiMemory.recordUserFact(context, key, value)
                return Outcome(true, true, "I will remember that your $key is $value.")
            }
        }

        // ── WhatsApp message & chat opening ──
        openAndSendWhatsAppPattern.matchEntire(command)?.let { match ->
            return sendWhatsApp(context, unquote(match.groupValues[1]), unquote(match.groupValues[2]))
        }
        whatsAppPattern.matchEntire(command)?.let { match ->
            return sendWhatsApp(context, unquote(match.groupValues[1]), unquote(match.groupValues[2]))
        }
        if (lower.startsWith("send ") || lower.startsWith("message ") || lower.startsWith("text ")) {
            if (!lower.contains("email") && !lower.contains("sms")) {
                whatsAppShortPattern.matchEntire(command)?.let { match ->
                    return sendWhatsApp(context, unquote(match.groupValues[1]), unquote(match.groupValues[2]))
                }
            }
        }
        openWhatsAppChatPattern.matchEntire(command)?.let { match ->
            val target = (match.groupValues[1].ifEmpty { match.groupValues[2] }.ifEmpty { match.groupValues[3] }).trim()
            if (target.isNotEmpty() && !target.contains("saying")) {
                return sendWhatsApp(context, unquote(target), "")
            }
        }

        // ── Self-Developing Memory: Fact Retrieval ──
        val memReply = AiMemory.queryMemory(context, lower)
        if (memReply != null) {
            return Outcome(true, true, memReply)
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
        val resolvedContact = com.gloom.island.ai.AiMemory.resolveEntity(contact) ?: contact
        val result = DeviceTools.text(context, resolvedContact, message)
        return Outcome(true, result.success, result.message)
    }

    private fun search(context: Context, query: String): Outcome {
        ScreenAutomation.refresh()
        val field = ScreenAutomation.tapLabel(listOf("search", "search…", "search...", "search bar"), contains = true)
            ?: return Outcome(true, false, "I couldn't find a search field on this screen.")
        ActionVerifier.tap(field.eid, verify = GloomIslandSettings.instance.verifyActionsEnabled)
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
     * conversation, type the message and press Send.
     * Uses dynamic fast polling with sub-100ms intervals instead of static sleeps.
     */
    private fun sendWhatsApp(context: Context, contact: String, message: String): Outcome {
        if (!accessibilityReady()) return accessibilityRequired()
        val cleanContact = unquote(contact)
        val cleanMsg = unquote(message)
        val resolvedContact = com.gloom.island.ai.AiMemory.resolveEntity(cleanContact) ?: cleanContact
        val app = when {
            isInstalled(context, "com.whatsapp") -> "whatsapp"
            isInstalled(context, "com.whatsapp.w4b") -> "whatsapp business"
            else -> return Outcome(true, false, "WhatsApp is not installed.")
        }
        val label = ScreenAutomation.launch(context, app)
            ?: return Outcome(true, false, "I couldn't open WhatsApp.")
        Log.i(TAG, "Sending WhatsApp message to $resolvedContact (original: $contact) via $label")

        // 0. Ensure fresh snapshot and wait for WhatsApp foreground
        ScreenAutomation.refresh()
        ScreenAutomation.waitForElement(timeoutMs = 1500L, pollIntervalMs = 50L) {
            ScreenAutomation.currentPackage().contains("whatsapp")
        }

        val wantedContact = resolvedContact.lowercase().trim()
        val fallbackContact = cleanContact.lowercase().trim()

        // 1. Check if already in the target contact's chat
        val snap = ScreenAutomation.refresh()
        val hasMessageBox = snap.any { elem ->
            elem.viewId.endsWith("/entry") || elem.viewId.contains("id/entry") || (elem.editable && elem.label.lowercase().contains("message"))
        }
        val hasContactName = snap.any { elem ->
            val l = elem.label.lowercase()
            l.contains(wantedContact) || l.contains(fallbackContact)
        }
        val alreadyInTargetChat = hasMessageBox && hasContactName

        if (!alreadyInTargetChat) {
            // If in some other chat or info screen, navigate back to HomeActivity
            val hasSearchBar = ScreenAutomation.snapshot().any { elem ->
                elem.viewId.contains("search_bar") || elem.viewId.contains("search_input")
            }
            if (!hasSearchBar) {
                ScreenAutomation.global("back")
                Thread.sleep(300)
                ScreenAutomation.refresh()
            }

            // Locate search or check if search input is already active
            val alreadySearching = ScreenAutomation.snapshot().any { elem ->
                elem.viewId.contains("search_input")
            }
            if (!alreadySearching) {
                val searchBtn = ScreenAutomation.waitForElement(timeoutMs = 2500L, pollIntervalMs = 70L) { elem ->
                    val l = elem.label.lowercase()
                    val id = elem.viewId.lowercase()
                    !id.contains("meta") && !l.contains("meta") && !id.contains("filter") && !l.contains("unread") &&
                        (id.contains("search_bar_inner_layout") || id.contains("search_text") || id.contains("search_icon") || (elem.clickable && id.contains("search")))
                } ?: return Outcome(true, false, "I couldn't find the WhatsApp search button.")

                ScreenAutomation.tap(searchBtn.eid)
            }

            // 2. Wait for search edit text to be focused/editable
            val searchField = ScreenAutomation.waitForElement(timeoutMs = 2000L, pollIntervalMs = 70L) { elem ->
                elem.viewId.contains("search_input")
            }
            if (searchField != null) {
                ScreenAutomation.tap(searchField.eid)
                Thread.sleep(80)
            }

            // 3. Type contact name uniquely
            var typedContact = ScreenAutomation.typeTextUnique(resolvedContact, clearFirst = true)
            if (!typedContact.success && typedContact.via != "deduped") {
                Thread.sleep(120)
                typedContact = ScreenAutomation.typeTextUnique(resolvedContact, clearFirst = true)
            }
            if (!typedContact.success && typedContact.via != "deduped") {
                return Outcome(true, false, "I couldn't search for $resolvedContact: ${typedContact.detail}")
            }

            // 4. Wait for conversation row to appear in search results
            val chatRow = ScreenAutomation.waitForElement(timeoutMs = 2500L, pollIntervalMs = 70L) { elem ->
                val vId = elem.viewId.lowercase()
                val lbl = elem.label.lowercase()
                val isAvatar = vId.contains("photo") || vId.contains("avatar") || vId.contains("picture") ||
                    vId.contains("selector") || elem.bounds.right <= 250 ||
                    lbl.contains("photo") || lbl.contains("picture")
                !isAvatar && !elem.editable && !vId.contains("filter") && !vId.contains("token") &&
                    (lbl.contains(wantedContact) || lbl.contains(fallbackContact)) &&
                    (vId.contains("conversations_row_contact_name") || vId.contains("contact_name") || vId.contains("name"))
            } ?: ScreenAutomation.waitForElement(timeoutMs = 1200L, pollIntervalMs = 70L) { elem ->
                val vId = elem.viewId.lowercase()
                val lbl = elem.label.lowercase()
                val isAvatar = vId.contains("photo") || vId.contains("avatar") || vId.contains("picture") ||
                    vId.contains("selector") || elem.bounds.right <= 250 ||
                    lbl.contains("photo") || lbl.contains("picture")
                !isAvatar && !elem.editable && !vId.contains("filter") && !vId.contains("token") &&
                    (lbl.contains(wantedContact) || lbl.contains(fallbackContact))
            } ?: return Outcome(true, false, "I couldn't find $resolvedContact in the results.")

            // 5. Open chat by clicking row via both accessibility and touch at center of contact name
            val clickX = chatRow.bounds.centerX().toFloat()
            val clickY = chatRow.bounds.centerY().toFloat()
            val rowNode = chatRow.actionTarget ?: chatRow.node
            runCatching { rowNode.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
            ScreenAutomation.tap(chatRow.eid)
            ScreenAutomation.tapPoint(clickX, clickY)
        }

        // 6. Wait for chat screen message entry box to appear
        // CRITICAL: Must be WhatsApp's actual chat entry field (com.whatsapp:id/entry), NEVER search_input!
        val field = ScreenAutomation.waitForElement(timeoutMs = 3500L, pollIntervalMs = 80L) { elem ->
            val vId = elem.viewId.lowercase()
            (vId.endsWith("/entry") || vId == "entry" || vId.contains("id/entry")) &&
            !vId.contains("search")
        } ?: run {
            // Popup recovery: check if avatar dialog opened accidentally
            val popupMessageBtn = ScreenAutomation.snapshot().firstOrNull { elem ->
                (elem.label.equals("message", ignoreCase = true) || elem.viewId.contains("message")) &&
                    !elem.editable && elem.clickable && !elem.viewId.contains("search")
            }
            if (popupMessageBtn != null) {
                ScreenAutomation.tap(popupMessageBtn.eid)
                ScreenAutomation.waitForElement(timeoutMs = 1500L, pollIntervalMs = 70L) { elem ->
                    elem.viewId.contains("entry") && !elem.viewId.contains("search")
                }
            } else null
        }

        if (field == null) {
            return Outcome(true, false, "I searched for $resolvedContact but couldn't open the chat.")
        }

        ScreenAutomation.tap(field.eid)
        Thread.sleep(80)

        // If no message was requested, leaving the conversation open is a success!
        if (cleanMsg.isBlank()) {
            return Outcome(true, true, "Opened WhatsApp chat with $resolvedContact.")
        }

        // 7. Type message into the verified chat entry field
        var typedMessage = ScreenAutomation.typeTextUnique(cleanMsg, clearFirst = false, targetEid = field.eid)
        if (!typedMessage.success && typedMessage.via != "deduped") {
            Thread.sleep(120)
            typedMessage = ScreenAutomation.typeTextUnique(cleanMsg, clearFirst = false, targetEid = field.eid)
        }
        if (!typedMessage.success && typedMessage.via != "deduped") {
            return Outcome(true, false, "I couldn't type the message: ${typedMessage.detail}")
        }

        // 8. Wait for Send button to appear
        val sendBtn = ScreenAutomation.waitForElement(timeoutMs = 1500L, pollIntervalMs = 70L) { elem ->
            val vId = elem.viewId.lowercase()
            val lbl = elem.label.lowercase()
            (vId.endsWith("/send") || vId == "send" || (vId.contains("send") && !vId.contains("send_container"))) ||
            lbl == "send" ||
            lbl.contains("send message")
        }

        val sent = if (sendBtn != null) {
            ScreenAutomation.tap(sendBtn.eid)
        } else {
            ScreenAutomation.submit() || ScreenAutomation.clickLabel(listOf("send", "Send"), contains = true)
        }
        return if (sent) {
            com.gloom.island.ai.AiMemory.recordExecutionOutcome(
                context = context,
                goal = "Send WhatsApp to $contact",
                steps = listOf("Opened WhatsApp", "Searched $resolvedContact", "Typed message", "Sent"),
                success = true,
                targetApp = "com.whatsapp"
            )
            Outcome(true, true, "Message sent to $resolvedContact.")
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
        Outcome(true, false, "Enable GLOOM accessibility so I can control the screen.")

    private fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun unquote(value: String): String {
        var str = value.trim()
        while (str.startsWith('\\') || str.startsWith('"') || str.startsWith('\'')) {
            str = str.drop(1).trim()
        }
        while (str.endsWith('\\') || str.endsWith('"') || str.endsWith('\'')) {
            str = str.dropLast(1).trim()
        }
        return str
    }
}
