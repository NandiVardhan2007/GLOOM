package com.jarvis.island.automation

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.jarvis.island.bridge.JarvisLiveBridgeClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A small observe-plan-act loop over the accessibility outline.
 *
 * This is the part both reference projects converge on: give the model a
 * numbered list of the elements on screen, let it choose one, execute, look
 * again. It is used only for requests the deterministic router cannot answer,
 * and every action is bounded and reversible except typing and sending.
 */
object AiPlanner {

    private const val TAG = "JarvisAiPlanner"
    private const val MAX_ROUNDS = 2
    private const val MAX_ACTIONS = 5
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
        val client = JarvisLiveBridgeClient.activeClient
            ?: return CommandRouter.Outcome(false, false, "")
        if (!ScreenAutomation.isReady()) {
            return CommandRouter.Outcome(
                true,
                false,
                "Enable JARVIS accessibility so I can control the screen."
            )
        }
        if (BLOCKED_WORDS.any { request.lowercase().contains(it) }) {
            return CommandRouter.Outcome(true, false, "I won't do that from voice.")
        }

        var lastMessage = ""
        for (round in 0 until MAX_ROUNDS) {
            val outline = ScreenAutomation.describe(MAX_OUTLINE_ELEMENTS)
            val plan = requestPlan(client, request, outline, round) ?: break
            val actions = plan.optJSONArray("actions") ?: JSONArray()
            val say = plan.optString("say").trim()
            if (say.isNotEmpty()) lastMessage = say
            if (actions.length() == 0) {
                return if (say.isNotEmpty()) {
                    CommandRouter.Outcome(true, true, say)
                } else {
                    CommandRouter.Outcome(false, false, "")
                }
            }

            var changedScreen = false
            var tapStreak = 0
            var lastTap = -1
            for (index in 0 until actions.length().coerceAtMost(MAX_ACTIONS)) {
                val action = actions.optJSONObject(index) ?: continue
                val name = action.optString("action").lowercase()
                Log.i(TAG, "Action $name: $action")
                when (name) {
                    "tap", "click" -> {
                        val eid = action.optInt("eid", -1)
                        if (eid < 0) return failure("The plan named no element to tap.")
                        if (eid == lastTap) tapStreak += 1 else tapStreak = 0
                        if (tapStreak >= 2) {
                            return failure("I stopped because the same control kept being tapped.")
                        }
                        lastTap = eid
                        if (!ScreenAutomation.tap(eid)) {
                            return failure("I couldn't tap element $eid; the screen may have changed.")
                        }
                        changedScreen = true
                        SystemClock.sleep(650)
                    }
                    "long_press", "hold" -> {
                        val eid = action.optInt("eid", -1)
                        if (eid < 0 || !ScreenAutomation.longPress(eid)) {
                            return failure("I couldn't long-press that element.")
                        }
                        changedScreen = true
                        SystemClock.sleep(500)
                    }
                    "type", "write" -> {
                        val value = action.optString("text")
                        val result = ScreenAutomation.typeText(value)
                        if (!result.success) return failure(result.detail)
                        SystemClock.sleep(300)
                    }
                    "submit", "send" -> {
                        if (!ScreenAutomation.submit()) {
                            return failure("The field has no Send action.")
                        }
                        changedScreen = true
                        SystemClock.sleep(600)
                    }
                    "scroll" -> {
                        val direction = action.optString("direction", "down")
                        val times = action.optInt("times", 1)
                        if (ScreenAutomation.scroll(direction, times) == 0) {
                            return failure("This screen does not scroll.")
                        }
                        changedScreen = true
                        SystemClock.sleep(500)
                    }
                    "swipe" -> {
                        val direction = action.optString("direction", "up")
                        if (!ScreenAutomation.swipe(direction)) {
                            return failure("I couldn't swipe.")
                        }
                        changedScreen = true
                        SystemClock.sleep(450)
                    }
                    "global", "back", "home" -> {
                        val target = if (name == "back" || name == "home") name else {
                            action.optString("name", action.optString("target", "back"))
                        }
                        if (!ScreenAutomation.global(target)) {
                            return failure("I couldn't perform $target.")
                        }
                        changedScreen = true
                        SystemClock.sleep(700)
                    }
                    "launch", "open" -> {
                        val app = action.optString("app", action.optString("text", "")).trim()
                        if (app.isEmpty() || ScreenAutomation.launch(context, app) == null) {
                            return failure("I couldn't open $app.")
                        }
                        changedScreen = true
                        SystemClock.sleep(1_800)
                    }
                    "wait" -> {
                        val ms = action.optInt("ms", 600).coerceIn(100, 3_000)
                        SystemClock.sleep(ms.toLong())
                    }
                    else -> Log.w(TAG, "Ignoring unknown action $name")
                }
            }

            if (changedScreen) {
                ScreenAutomation.refresh()
                SystemClock.sleep(400)
            } else {
                break
            }
        }

        // The final screen is the evidence. If the model gave a line, say it.
        val packageName = ScreenAutomation.currentPackage()
        return CommandRouter.Outcome(
            true,
            true,
            if (lastMessage.isNotEmpty()) lastMessage
            else "Done. $packageName is on screen."
        )
    }

    private fun failure(message: String): CommandRouter.Outcome =
        CommandRouter.Outcome(true, false, message)

    private fun requestPlan(
        client: JarvisLiveBridgeClient,
        request: String,
        outline: String,
        round: Int,
    ): JSONObject? {
        val system = """
            You control an Android phone through accessibility actions.
            Reply with ONE JSON object and no other text:
            {"actions":[{"action":"tap","eid":12},{"action":"type","text":"hello"},{"action":"submit"}],"say":"short status"}
            Allowed actions:
            tap/click {eid}, long_press {eid}, type {text}, submit, scroll {direction:up|down,times},
            swipe {direction:up|down|left|right}, global {name:back|home|recents|notifications},
            launch {app}, wait {ms}.
            Rules:
            - Use only eids that appear in the outline. Never invent one.
            - At most 5 actions. The device executes them in order.
            - The outline line is: e<number>[*][{E,S}]|<class>|<label>|[x,y] in 0-1000 coordinates.
              * means tappable, E means text field, S means scrollable.
            - If the request is already satisfied, return {"actions":[],"say":"..."}.
            - If more steps are needed after this batch, say so; you will be shown the new screen.
            - Never perform destructive or financial actions.
        """.trimIndent()
        val user = buildString {
            append("Request: ").append(request).append('\n')
            if (round > 0) append("This is round ").append(round + 1).append("; continue from the current screen.\n")
            append("Screen outline:\n").append(outline)
        }
        val latch = CountDownLatch(1)
        var reply: String? = null
        var error: String? = null
        client.requestAi(listOf("system" to system, "user" to user)) { answer, failure ->
            reply = answer
            error = failure
            latch.countDown()
        }
        if (!latch.await(45, TimeUnit.SECONDS)) {
            Log.w(TAG, "AI planner timed out")
            return null
        }
        if (reply == null) {
            Log.w(TAG, "AI planner failed: ${error ?: "unknown"}")
            return null
        }
        return parseJson(reply!!)
    }

    private fun parseJson(raw: String): JSONObject? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            JSONObject(raw.substring(start, end + 1))
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse planner reply", e)
            null
        }
    }
}
