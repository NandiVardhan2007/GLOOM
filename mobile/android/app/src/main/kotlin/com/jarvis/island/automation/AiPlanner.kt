package com.jarvis.island.automation

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.jarvis.island.ai.AiRouter
import com.jarvis.island.service.JarvisIslandAccessibilityService
import com.jarvis.island.settings.JarvisIslandSettings
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Reactive observe-plan-act loop over the accessibility outline, inspired by kuafuai/andee.
 *
 * Rather than blind batching of multiple actions, JARVIS observes the screen outline at each step,
 * asks the AI for the single immediate next action, executes it with physical gesture fidelity,
 * surfaces live feedback on the Dynamic Island, verifies the outcome, and repeats until the user's
 * goal is achieved.
 */
object AiPlanner {

    private const val TAG = "JarvisAiPlanner"
    private const val MAX_STEPS = 8
    private const val MAX_OUTLINE_ELEMENTS = 70

    private val ACTION_VERBS = listOf(
        "send", "open", "launch", "start", "tap", "click", "press", "type", "write",
        "scroll", "swipe", "search", "find", "like", "comment", "follow", "share",
        "message", "call", "play", "pause", "next", "skip", "back", "home",
        "enable", "turn on", "turn off", "toggle", "choose", "select"
    )

    private val BLOCKED_WORDS = listOf(
        "delete account", "factory reset", "uninstall", "purchase", "buy now",
        "transfer money", "pay ", "erase all", "format"
    )

    fun looksLikeAction(text: String): Boolean {
        val lower = text.trim().lowercase()
        if (lower.endsWith("?")) return false
        val questionStarts = listOf(
            "what", "how", "why", "who", "when", "where", "which",
            "explain", "tell me", "do you", "is it", "are there"
        )
        if (questionStarts.any { lower.startsWith(it) }) return false
        return ACTION_VERBS.any { verb ->
            lower.startsWith("$verb ") ||
                lower.startsWith("can you $verb ") ||
                lower.startsWith("could you $verb ") ||
                lower.startsWith("please $verb ")
        }
    }

    fun run(context: Context, request: String): CommandRouter.Outcome {
        if (!AiRouter.canReachModel(context)) {
            return CommandRouter.Outcome(false, false, "")
        }
        if (!ScreenAutomation.isReady()) {
            return CommandRouter.Outcome(
                true,
                false,
                "Enable JARVIS accessibility so I can control the screen."
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

        for (step in 1..MAX_STEPS) {
            val outline = ScreenAutomation.describe(MAX_OUTLINE_ELEMENTS)
            val grid = gridWhenTreeIsEmpty()
            val stepPlan = requestStep(context, request, history, outline, grid, step) ?: break

            val thought = stepPlan.optString("thought").trim()
            val say = stepPlan.optString("say").trim()
            val isDone = stepPlan.optBoolean("done", false)
            val actionName = stepPlan.optString("action").lowercase().trim()

            if (say.isNotEmpty()) {
                lastMessage = say
                JarvisIslandAccessibilityService.instance?.showLocalNotification("JARVIS Agent", say)
            }

            if (isDone || actionName == "done" || actionName.isEmpty()) {
                val completionMsg = if (say.isNotEmpty()) say else "Done."
                JarvisIslandAccessibilityService.instance?.showLocalNotification("JARVIS Agent", completionMsg)
                return CommandRouter.Outcome(true, true, completionMsg)
            }

            Log.i(TAG, "Step $step [$actionName]: thought=$thought, say=$say")

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
                        verify = JarvisIslandSettings.instance.verifyActionsEnabled
                    )
                    if (!result.success) {
                        return failure("I couldn't tap ${element.label.ifBlank { "element $eid" }}.")
                    }
                    history.add("Step $step: Tapped '${element.label.ifBlank { "eid $eid" }}'")
                    SystemClock.sleep(600)
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
                    ActionVerifier.waitForChange(before, 1_200L)
                    frame.bitmap.recycle()
                    history.add("Step $step: Tapped grid cell $cell")
                    SystemClock.sleep(600)
                }
                "tap_point" -> {
                    val x = stepPlan.optInt("x", -1)
                    val y = stepPlan.optInt("y", -1)
                    if (x < 0 || y < 0) return failure("No point specified to tap.")
                    val result = ActionVerifier.tapFraction(x, y)
                    if (!result.success) return failure("I couldn't tap that location.")
                    history.add("Step $step: Tapped point ($x, $y)")
                    SystemClock.sleep(600)
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
                    SystemClock.sleep(500)
                }
                "type", "write" -> {
                    val text = stepPlan.optString("text")
                    val eid = stepPlan.optInt("eid", -1)
                    if (eid >= 0) {
                        ScreenAutomation.tap(eid)
                        SystemClock.sleep(250)
                    }
                    val result = ScreenAutomation.typeText(text)
                    if (!result.success) return failure(result.detail)
                    history.add("Step $step: Typed '$text'")
                    SystemClock.sleep(400)
                }
                "submit", "send" -> {
                    if (!ScreenAutomation.submit()) return failure("No send or submit button found.")
                    history.add("Step $step: Submitted input")
                    SystemClock.sleep(600)
                }
                "scroll" -> {
                    val dir = stepPlan.optString("direction", "down")
                    val times = stepPlan.optInt("times", 1)
                    if (ScreenAutomation.scroll(dir, times) == 0) return failure("This screen does not scroll.")
                    history.add("Step $step: Scrolled $dir")
                    SystemClock.sleep(500)
                }
                "swipe" -> {
                    val dir = stepPlan.optString("direction", "up")
                    if (!ScreenAutomation.swipe(dir)) return failure("Couldn't swipe.")
                    history.add("Step $step: Swiped $dir")
                    SystemClock.sleep(450)
                }
                "launch", "open" -> {
                    val app = stepPlan.optString("app", stepPlan.optString("text", "")).trim()
                    if (app.isEmpty() || ScreenAutomation.launch(context, app) == null) {
                        return failure("Couldn't open $app.")
                    }
                    history.add("Step $step: Opened $app")
                    SystemClock.sleep(1600)
                }
                "global", "back", "home", "recents" -> {
                    val target = if (actionName in listOf("back", "home", "recents")) actionName
                    else stepPlan.optString("name", "back")
                    if (!ScreenAutomation.global(target)) return failure("Couldn't perform $target.")
                    history.add("Step $step: Performed $target")
                    SystemClock.sleep(600)
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
            SystemClock.sleep(300)
        }

        if (history.isEmpty()) return CommandRouter.Outcome(false, false, "")
        val pkg = ScreenAutomation.currentPackage()
        val finalMsg = if (lastMessage.isNotEmpty()) lastMessage else "Done. $pkg is on screen."
        JarvisIslandAccessibilityService.instance?.showLocalNotification("JARVIS Agent", finalMsg)
        return CommandRouter.Outcome(true, true, finalMsg)
    }

    private fun failure(message: String): CommandRouter.Outcome {
        JarvisIslandAccessibilityService.instance?.showLocalNotification("JARVIS Agent", message)
        return CommandRouter.Outcome(true, false, message)
    }

    private fun gridWhenTreeIsEmpty(): String? {
        if (!ScreenSnapshot.isSupported()) return null
        val elements = ScreenAutomation.snapshot()
        if (elements.size > 5) return null
        return runCatching { ScreenSnapshot.describeScreen() }.getOrNull()
    }

    private fun requestStep(
        context: Context,
        request: String,
        history: List<String>,
        outline: String,
        grid: String?,
        stepNumber: Int,
    ): JSONObject? {
        val system = """
            You are JARVIS, an autonomous mobile agent controlling an Android phone through precise accessibility actions.
            You will be given:
            1. User goal
            2. Action history (actions you executed in earlier steps)
            3. Current screen outline (e<eid>*|class|label|[x,y])

            Decide the EXACT NEXT SINGLE ACTION to progress toward the user's goal.
            Reply with ONE JSON object and no other text:
            {
              "thought": "Reasoning for this specific step",
              "say": "Short status to show user on Dynamic Island (e.g. 'Tapping Wi-Fi')",
              "action": "tap", // tap, type, submit, scroll, swipe, launch, back, home, recents, wait, done
              "eid": 12,       // element id from outline (for tap, type, long_press)
              "text": "query", // text to type (for type action)
              "direction": "down", // up, down, left, right (for scroll, swipe)
              "app": "Settings",   // app name to launch (for launch action)
              "done": false        // set true when user's goal is fully achieved!
            }

            Rules:
            - Output ONLY the immediate next step. Never attempt to batch multiple actions.
            - Use ONLY eids that appear in the outline.
            - If the goal is satisfied, return {"thought":"Goal complete","say":"Done!","action":"done","done":true}.
            - Never perform destructive or financial actions.
        """.trimIndent()

        val user = buildString {
            append("Goal: ").append(request).append('\n')
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
        if (reply == null) {
            Log.w(TAG, "AI planner step $stepNumber failed: ${error ?: "unknown"}")
            return null
        }
        return parseJson(reply!!)
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
