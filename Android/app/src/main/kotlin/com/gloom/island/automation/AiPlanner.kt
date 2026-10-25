package com.gloom.island.automation

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.gloom.island.ai.AiMemory
import com.gloom.island.ai.AiRouter
import com.gloom.island.security.PiiMasker
import com.gloom.island.security.Vault
import com.gloom.island.service.GloomIslandAccessibilityService
import com.gloom.island.settings.GloomIslandSettings
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Reactive observe-plan-act loop over the accessibility outline, inspired by kuafuai/andee.
 *
 * Rather than blind batching of multiple actions, GLOOM observes the screen outline at each step,
 * asks the AI for the single immediate next action, executes it with physical gesture fidelity,
 * surfaces live feedback on the Dynamic Island, verifies the outcome, and repeats until the user's
 * goal is achieved.
 */
object AiPlanner {

    private const val TAG = "GloomAiPlanner"
    private const val MAX_STEPS = 8
    private const val MAX_OUTLINE_ELEMENTS = 70

    private val ACTION_VERBS = listOf(
        "send", "open", "launch", "start", "tap", "click", "press", "type", "write",
        "scroll", "swipe", "search", "find", "like", "comment", "follow", "share",
        "message", "call", "play", "pause", "next", "skip", "back", "home",
        "enable", "turn on", "turn off", "toggle", "choose", "select",
        "increase", "decrease", "raise", "lower", "set", "mute", "unmute",
        "list", "show", "tell", "check"
    )

    private val BLOCKED_WORDS = listOf(
        "delete account", "factory reset", "uninstall", "purchase", "buy now",
        "transfer money", "pay ", "erase all", "format"
    )

    fun looksLikeAction(text: String): Boolean {
        val cleaned = CommandRouter.cleanSpeech(text)
        val lower = cleaned.trim().lowercase()

        // Device volume, wifi, and pdf commands are always actionable
        if (CommandRouter.isVolumeCommand(lower) || CommandRouter.isWifiQuery(lower) || CommandRouter.isPdfQuery(lower)) {
            return true
        }

        if (lower.endsWith("?")) return false
        val questionStarts = listOf(
            "what", "how", "why", "who", "when", "where", "which",
            "explain", "tell me about", "do you know", "is it", "are there"
        )
        if (questionStarts.any { lower.startsWith(it) }) return false

        // App-specific direct action intents
        if (lower.contains("whatsapp") && (lower.contains("message") || lower.contains("send") || lower.contains("open"))) {
            return true
        }
        if (lower.contains("send a message") || lower.contains("send message") || lower.contains("text ")) {
            return true
        }

        return ACTION_VERBS.any { verb ->
            lower.startsWith("$verb ") ||
                lower.startsWith("can you $verb ") ||
                lower.startsWith("could you $verb ") ||
                lower.startsWith("please $verb ")
        }
    }

    fun run(context: Context, request: String): CommandRouter.Outcome {
        val canReachCloud = AiRouter.canReachModel(context)
        val isNeedleAvailable = com.gloom.island.ai.NeedleBridge.isReady(context)
        if (!canReachCloud && !isNeedleAvailable) {
            return CommandRouter.Outcome(false, false, "")
        }
        if (!ScreenAutomation.isReady()) {
            return CommandRouter.Outcome(
                true,
                false,
                "Enable GLOOM accessibility so I can control the screen."
            )
        }
        val safety = SafetyGuard.assessRequest(request)
        if (!safety.allowed) {
            return CommandRouter.Outcome(true, false, SafetyGuard.refusal(safety))
        }
        if (BLOCKED_WORDS.any { request.lowercase().contains(it) }) {
            return CommandRouter.Outcome(true, false, "I won't do that from voice.")
        }

        val history = ArrayList<String>()
        var lastMessage = ""
        var lastTap = -1
        var tapStreak = 0
        var stallStreak = 0
        var selfCorrectionHint: String? = null
        val isPro = GloomIslandSettings.instance.plannerProfile.equals("pro", ignoreCase = true)
        val maxSteps = if (isPro) 10 else 6

        fun failure(message: String): CommandRouter.Outcome {
            AutomationFrameRing.dumpFailureTrace(context, request, message)
            com.gloom.island.logging.GloomDebugLogger.logEvent(
                context = context,
                userRequest = request,
                handler = if (!canReachCloud) "Needle-3 Offline" else if (isPro) "AiPlanner Pro" else "AiPlanner Flash",
                stepsExecuted = history,
                isSuccess = false,
                errorReason = message
            )
            AiMemory.recordExecutionOutcome(
                context = context,
                goal = request,
                steps = history,
                success = false,
                targetApp = ScreenAutomation.currentPackage(),
                detail = message
            )
            GloomIslandAccessibilityService.instance?.showLocalNotification("GLOOM Agent", message)
            return CommandRouter.Outcome(true, false, message)
        }

        for (step in 1..maxSteps) {
            // Dynamic Dwell Timing: wait for loading spinners and network activity to settle
            SpinnerGuard.waitForScreenIdle(GloomIslandAccessibilityService.instance, 2500L)

            val elements = ScreenAutomation.snapshot()
            val outline = ScreenAutomation.describe(MAX_OUTLINE_ELEMENTS)
            val safeOutline = PiiMasker.maskText(outline)
            val grid = gridWhenTreeIsEmpty()
            val beforeSignature = ScreenAutomation.signature()
            val beforeVisual = VisualStateDiff.captureSignature(GloomIslandAccessibilityService.instance?.rootInActiveWindow)

            // 1. CAG (Cache-Augmented Generation): Check semantic plan cache
            val cachedStep = AiMemory.getCachedPlan(request, beforeSignature.toString())

            // 2. Needle-3 On-Device Evaluation (sub-20ms Cactus model)
            val needlePrediction = if (cachedStep == null && isNeedleAvailable) {
                com.gloom.island.ai.NeedleBridge.predict(context, request, elements)
            } else null

            // 3. Selection: Cache -> High-Confidence Needle-3 -> Cloud LLM -> Offline Needle-3 Fallback
            val stepPlan = cachedStep
                ?: (if (needlePrediction != null && needlePrediction.confidence >= 0.88f) needlePrediction.toPlanJson() else null)
                ?: if (canReachCloud) {
                    requestStep(
                        context = context,
                        request = request,
                        history = compressHistory(history),
                        outline = safeOutline,
                        grid = grid,
                        stepNumber = step,
                        isPro = isPro,
                        correctionHint = selfCorrectionHint
                    )
                } else {
                    needlePrediction?.toPlanJson()
                } ?: break

            if (cachedStep == null) {
                AiMemory.cacheStep(context, request, beforeSignature.toString(), stepPlan)
            }

            selfCorrectionHint = null // Consumed for this step

            val thought = stepPlan.optString("thought").trim()
            val say = stepPlan.optString("say").trim()
            val isDone = stepPlan.optBoolean("done", false)
            val actionName = stepPlan.optString("action").lowercase().trim()
            val checkpointReport = stepPlan.optString("checkpoint_report").trim()
            val confidence = stepPlan.optDouble("confidence", 1.0)

            if (say.isNotEmpty()) {
                lastMessage = say
                GloomIslandAccessibilityService.instance?.showLocalNotification("GLOOM Agent", say)
            }

            if (confidence < 0.4) {
                Log.w(TAG, "Low confidence step ($confidence) for $actionName: $thought")
            }

            if (isDone || actionName == "done" || actionName.isEmpty()) {
                val completionMsg = when {
                    checkpointReport.isNotEmpty() -> checkpointReport
                    say.isNotEmpty() -> say
                    else -> "Done."
                }
                com.gloom.island.logging.GloomDebugLogger.logEvent(
                    context = context,
                    userRequest = request,
                    handler = if (!canReachCloud) "Needle-3 Offline" else if (isPro) "AiPlanner Pro" else "AiPlanner Flash",
                    aiThought = thought,
                    aiResponse = completionMsg,
                    stepsExecuted = history,
                    isSuccess = true
                )
                AiMemory.recordExecutionOutcome(
                    context = context,
                    goal = request,
                    steps = history,
                    success = true,
                    targetApp = ScreenAutomation.currentPackage()
                )
                GloomIslandAccessibilityService.instance?.showLocalNotification("GLOOM Agent", completionMsg)
                return CommandRouter.Outcome(true, true, completionMsg)
            }

            Log.i(TAG, "Step $step [$actionName, pro=$isPro]: thought=$thought, say=$say")

            when (actionName) {
                "tap", "click" -> {
                    val eid = stepPlan.optInt("eid", -1)
                    if (eid < 0) return failure("I couldn't identify the element to tap.")
                    val element = ScreenAutomation.element(eid)
                        ?: return failure("Element $eid is no longer visible on screen.")
                    val verdict = SafetyGuard.assessElement(element.label, element.viewId)
                    if (!verdict.allowed) return failure(SafetyGuard.refusal(verdict))
                    if (eid == lastTap) tapStreak += 1 else tapStreak = 0
                    if (tapStreak >= 2) {
                        return failure("I stopped because the same control kept being tapped.")
                    }
                    lastTap = eid
                    val result = ActionVerifier.tap(
                        eid,
                        verify = GloomIslandSettings.instance.verifyActionsEnabled
                    )
                    if (!result.success) {
                        return failure("I couldn't tap ${element.label.ifBlank { "element $eid" }}.")
                    }
                    history.add("Step $step: Tapped '${element.label.ifBlank { "eid $eid" }}'")
                    if (isPro && !result.changed) {
                        selfCorrectionHint = "Notice: Tapping '${element.label}' did not change the screen. Consider another control or scroll."
                    }
                    SystemClock.sleep(if (isPro) 250 else 160)
                }
                "tap_grid", "tap_cell" -> {
                    val cell = stepPlan.optInt("cell", -1)
                    if (cell < 0) return failure("No grid cell specified.")
                    val frame = ScreenSnapshot.analyseBlocking()
                        ?: return failure("Couldn't capture screen to aim.")
                    val before = ScreenAutomation.signature()
                    if (!ScreenSnapshot.tapCell(frame, cell)) {
                        frame.bitmap.recycle()
                        return failure("Couldn't tap grid cell $cell.")
                    }
                    ActionVerifier.waitForChange(before, 800L)
                    frame.bitmap.recycle()
                    history.add("Step $step: Tapped grid cell $cell")
                    SystemClock.sleep(if (isPro) 250 else 160)
                }
                "tap_point" -> {
                    val x = stepPlan.optInt("x", -1)
                    val y = stepPlan.optInt("y", -1)
                    if (x < 0 || y < 0) return failure("No point specified to tap.")
                    val result = ActionVerifier.tapFraction(x, y)
                    if (!result.success) return failure("I couldn't tap that location.")
                    history.add("Step $step: Tapped point ($x, $y)")
                    SystemClock.sleep(if (isPro) 250 else 160)
                }
                "long_press", "hold" -> {
                    val eid = stepPlan.optInt("eid", -1)
                    if (eid < 0) return failure("No element specified for long-press.")
                    val element = ScreenAutomation.element(eid)
                        ?: return failure("Element $eid is no longer visible.")
                    val verdict = SafetyGuard.assessElement(element.label, element.viewId)
                    if (!verdict.allowed) return failure(SafetyGuard.refusal(verdict))
                    if (!ScreenAutomation.longPress(eid)) return failure("Couldn't long-press that element.")
                    history.add("Step $step: Long-pressed '${element.label}'")
                    SystemClock.sleep(if (isPro) 300 else 180)
                }
                "burst", "tap_burst" -> {
                    val eid = stepPlan.optInt("eid", -1)
                    val count = stepPlan.optInt("count", 2)
                    if (eid < 0) return failure("No element specified for tap burst.")
                    if (!ScreenAutomation.tapBurst(eid, count)) return failure("Couldn't burst tap element $eid.")
                    history.add("Step $step: Burst tapped eid $eid ($count times)")
                    SystemClock.sleep(if (isPro) 220 else 140)
                }
                "autofill", "fill_password" -> {
                    val query = stepPlan.optString("account", stepPlan.optString("service", request))
                    val entry = Vault.findMatching(context, query)
                    if (entry != null) {
                        val filled = Vault.autofillActiveScreen(context, entry)
                        if (filled) {
                            history.add("Step $step: Autofilled credentials for ${entry.title}")
                        } else {
                            return failure("Couldn't find login fields to autofill.")
                        }
                    } else {
                        return failure("No credentials found in Vault for '$query'.")
                    }
                    SystemClock.sleep(if (isPro) 300 else 180)
                }
                "type", "write" -> {
                    val text = stepPlan.optString("text")
                    val eid = stepPlan.optInt("eid", -1)
                    if (eid >= 0) {
                        ScreenAutomation.tap(eid)
                        SystemClock.sleep(if (isPro) 180 else 120)
                    }
                    val result = ScreenAutomation.typeTextUnique(text, clearFirst = true)
                    if (!result.success && result.via != "deduped") return failure(result.detail)
                    history.add("Step $step: Typed '$text'")
                    SystemClock.sleep(if (isPro) 220 else 140)
                }
                "submit", "send" -> {
                    if (!ScreenAutomation.submit()) return failure("No send or submit button found.")
                    history.add("Step $step: Submitted input")
                    SystemClock.sleep(if (isPro) 250 else 160)
                }
                "scroll" -> {
                    val dir = stepPlan.optString("direction", "down")
                    val times = stepPlan.optInt("times", 1)
                    if (ScreenAutomation.scroll(dir, times) == 0) return failure("This screen does not scroll.")
                    history.add("Step $step: Scrolled $dir")
                    SystemClock.sleep(if (isPro) 250 else 160)
                }
                "swipe" -> {
                    val dir = stepPlan.optString("direction", "up")
                    if (!ScreenAutomation.swipe(dir)) return failure("Couldn't swipe.")
                    history.add("Step $step: Swiped $dir")
                    SystemClock.sleep(if (isPro) 250 else 150)
                }
                "launch", "open" -> {
                    val app = stepPlan.optString("app", stepPlan.optString("text", "")).trim()
                    if (app.isEmpty() || ScreenAutomation.launch(context, app) == null) {
                        return failure("Couldn't open $app.")
                    }
                    history.add("Step $step: Opened $app")
                    // Dynamic launch polling instead of static 1600ms sleep
                    val deadline = SystemClock.uptimeMillis() + 1200L
                    while (SystemClock.uptimeMillis() < deadline) {
                        if (ScreenAutomation.currentPackage().contains(app, ignoreCase = true) ||
                            ScreenAutomation.snapshot().isNotEmpty()) {
                            break
                        }
                        SystemClock.sleep(60)
                    }
                    SystemClock.sleep(if (isPro) 200 else 120)
                }
                "global", "back", "home", "recents" -> {
                    val target = if (actionName in listOf("back", "home", "recents")) actionName
                    else stepPlan.optString("name", "back")
                    if (!ScreenAutomation.global(target)) return failure("Couldn't perform $target.")
                    history.add("Step $step: Performed $target")
                    SystemClock.sleep(if (isPro) 300 else 180)
                }
                "wait" -> {
                    val ms = stepPlan.optInt("ms", 600).coerceIn(100, 3000)
                    SystemClock.sleep(ms.toLong())
                    history.add("Step $step: Waited ${ms}ms")
                }
                else -> {
                    Log.w(TAG, "Unknown action $actionName")
                }
            }

            // Settle and observe updated UI hierarchy
            ScreenAutomation.refresh()
            SystemClock.sleep(if (isPro) 180 else 100)

            val afterVisual = VisualStateDiff.captureSignature(GloomIslandAccessibilityService.instance?.rootInActiveWindow)
            val diff = VisualStateDiff.compare(beforeVisual, afterVisual)
            AutomationFrameRing.recordFrame(
                step = step,
                action = actionName,
                targetApp = ScreenAutomation.currentPackage(),
                outlineSnippet = safeOutline,
                diffResult = diff.description,
                success = !diff.isStalled
            )

            if (diff.isStalled) {
                stallStreak++
                if (isPro) {
                    if (stallStreak == 1) {
                        selfCorrectionHint = "Notice: Action '$actionName' did not alter screen state (${diff.description}). Try an alternate element or gesture."
                    } else if (stallStreak >= 2) {
                        Log.w(TAG, "Consecutive stalls detected ($stallStreak). Backtracking trajectory...")
                        ScreenAutomation.global("back")
                        SystemClock.sleep(600)
                        ScreenAutomation.refresh()
                        selfCorrectionHint = "Trajectory Recovery: Multiple actions stalled. Navigated BACK to recover previous state. Re-evaluate screen."
                        stallStreak = 0
                    }
                }
            } else {
                stallStreak = 0
            }
        }

        if (history.isEmpty()) return CommandRouter.Outcome(false, false, "")
        val pkg = ScreenAutomation.currentPackage()
        val finalMsg = if (lastMessage.isNotEmpty()) lastMessage else "Done. $pkg is on screen."
        AiMemory.recordExecutionOutcome(
            context = context,
            goal = request,
            steps = history,
            success = true,
            targetApp = pkg
        )
        com.gloom.island.logging.GloomDebugLogger.logEvent(
            context = context,
            userRequest = request,
            handler = if (isPro) "AiPlanner Pro" else "AiPlanner Flash",
            aiResponse = finalMsg,
            stepsExecuted = history,
            isSuccess = true
        )
        GloomIslandAccessibilityService.instance?.showLocalNotification("GLOOM Agent", finalMsg)
        return CommandRouter.Outcome(true, true, finalMsg)
    }

    private fun failure(message: String): CommandRouter.Outcome {
        GloomIslandAccessibilityService.instance?.showLocalNotification("GLOOM Agent", message)
        return CommandRouter.Outcome(true, false, message)
    }

    private fun gridWhenTreeIsEmpty(): String? {
        if (!ScreenSnapshot.isSupported()) return null
        val elements = ScreenAutomation.snapshot()
        if (elements.size > 5) return null
        return runCatching { ScreenSnapshot.describeScreen() }.getOrNull()
    }

    private fun compressHistory(history: List<String>): List<String> {
        if (history.size <= 4) return history
        val compressedCount = history.size - 3
        val summary = "Steps 1..$compressedCount summary: " +
            history.take(compressedCount).joinToString("; ") { it.substringAfter(": ") }
        return listOf(summary) + history.takeLast(3)
    }

    private fun requestStep(
        context: Context,
        request: String,
        history: List<String>,
        outline: String,
        grid: String?,
        stepNumber: Int,
        isPro: Boolean = false,
        correctionHint: String? = null
    ): JSONObject? {
        val profileDesc = if (isPro) {
            "Profile: PRO (Deep multi-turn planning, screen-diff verification, self-correction, and final 'checkpoint_report' upon completion)."
        } else {
            "Profile: FLASH (Fast immediate reactive tool caller)."
        }
        val memoryContext = AiMemory.getRelevantContext(context, request)

        val system = """
            You are GLOOM, an autonomous mobile agent controlling an Android phone through precise accessibility actions.
            $profileDesc
            $memoryContext
            You will be given:
            1. User goal
            2. Action history (actions you executed in earlier steps)
            3. Current screen outline (e<eid>*|class|label|[x,y])

            Decide the EXACT NEXT SINGLE ACTION to progress toward the user's goal.
            Reply with ONE JSON object and no other text:
            {
              "thought": "Reasoning for this specific step",
              "confidence": 0.95,
              "say": "Short status to show user on Dynamic Island (e.g. 'Tapping Wi-Fi')",
              "action": "tap", // tap, type, submit, scroll, swipe, burst, autofill, launch, back, home, recents, wait, done
              "eid": 12,       // element id from outline (for tap, type, long_press, burst)
              "count": 2,      // tap count for burst (default 2)
              "text": "query", // text to type (for type action)
              "account": "name", // account or service name from Vault (for autofill action)
              "direction": "down", // up, down, left, right (for scroll, swipe)
              "app": "Settings",   // app name to launch (for launch action)
              "checkpoint_report": "Concise summary of accomplished task",
              "done": false        // set true when user's goal is fully achieved!
            }

            Rules:
            - Output ONLY the immediate next step. Never attempt to batch multiple actions.
            - Use ONLY eids that appear in the outline.
            - In messaging apps (WhatsApp, Telegram, Messages), NEVER tap the profile photo or avatar icon on the left (which opens the photo dialog); ALWAYS tap the contact name or conversation row to open the chat.
            - Use "burst" when rapid repeated taps are required (e.g., skip, heart, increment).
            - Use "autofill" with "account" (service or account query) when username/password fields are on screen.
            - If the goal is satisfied, return {"thought":"Goal complete","say":"Done!","action":"done","done":true}.
            - Never perform destructive or financial actions.
        """.trimIndent()

        val user = buildString {
            append("Goal: ").append(request).append('\n')
            if (correctionHint != null) {
                append("\n[Self-Correction Alert]\n").append(correctionHint).append("\n\n")
            }
            if (history.isNotEmpty()) {
                append("Actions executed so far:\n")
                history.forEach { append(" - ").append(it).append('\n') }
            }
            append("Current Step: ").append(stepNumber).append('\n')
            append("Screen outline:\n").append(outline).append('\n')
            if (grid != null) {
                append("Screenshot aim grid:\n").append(grid)
            }
        }

        val latch = CountDownLatch(1)
        var reply: String? = null
        var error: String? = null
        AiRouter.request(context, listOf("system" to system, "user" to user)) { answer, failure, _ ->
            reply = answer
            error = failure
            latch.countDown()
        }
        if (!latch.await(35, TimeUnit.SECONDS)) {
            Log.w(TAG, "AI planner timed out on step $stepNumber")
            return null
        }
        val currentReply = reply
        if (currentReply == null) {
            Log.w(TAG, "AI planner step $stepNumber failed: ${error ?: "unknown"}")
            return null
        }
        return parseJson(currentReply)
    }

    private fun parseJson(raw: String): JSONObject? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            val json = JSONObject(raw.substring(start, end + 1))
            // Support both {"action": "tap", ...} and legacy {"actions": [{...}]}
            if (json.has("actions") && !json.has("action")) {
                val actionsArr = json.optJSONArray("actions")
                if (actionsArr != null && actionsArr.length() > 0) {
                    val first = actionsArr.getJSONObject(0)
                    if (json.has("say") && !first.has("say")) {
                        first.put("say", json.optString("say"))
                    }
                    first
                } else {
                    json.put("action", "done")
                    json.put("done", true)
                    json
                }
            } else {
                json
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse planner reply: $raw", e)
            null
        }
    }
}
