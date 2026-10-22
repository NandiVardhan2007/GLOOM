package com.jarvis.island.automation

import android.os.SystemClock

/**
 * Post-action verification.
 *
 * A tap that returned `true` only means Android accepted the gesture, not that
 * the app did anything. The screen outline is captured before and after the
 * action, and an action that changed nothing is retried once. Controls whose
 * whole job is toggling state (play, like, mute) are never retried, because a
 * second tap would undo the first.
 */
object ActionVerifier {

    data class Outcome(
        val success: Boolean,
        val changed: Boolean,
        val message: String,
    )

    private val statefulLabels = listOf(
        "play", "pause", "mute", "unmute", "like", "unlike", "favorite",
        "follow", "unfollow", "toggle", "switch", "check", "select", "star"
    )

    fun tap(eid: Int, verify: Boolean = true): Outcome {
        val element = ScreenAutomation.element(eid)
            ?: return Outcome(false, false, "I couldn't find that element on the screen.")
        val before = if (verify) ScreenAutomation.signature() else null
        val accepted = ScreenAutomation.tap(eid)
        if (!accepted) {
            return Outcome(false, false, "I couldn't tap ${element.label.ifBlank { "that" }}.")
        }
        if (!verify || before == null) {
            return Outcome(true, false, "Tapped ${element.label.ifBlank { "it" }}.")
        }

        val changed = waitForChange(before, 1_200L)
        if (changed) {
            return Outcome(true, true, "Tapped ${element.label.ifBlank { "it" }}.")
        }
        if (isStateful(element.label)) {
            // A toggle often changes only pixels (a filled heart, a pause bar).
            return Outcome(true, false, "Tapped ${element.label.ifBlank { "it" }}.")
        }

        // The first tap was accepted but nothing moved. Try the point once more.
        val retried = ScreenAutomation.tapPoint(
            element.bounds.centerX().toFloat(),
            element.bounds.centerY().toFloat()
        )
        val changedAfterRetry = retried && waitForChange(before, 1_400L)
        return when {
            changedAfterRetry -> Outcome(true, true, "Tapped ${element.label.ifBlank { "it" }}.")
            else -> Outcome(
                true,
                false,
                "I tapped ${element.label.ifBlank { "it" }}, but the screen did not change."
            )
        }
    }

    fun tapPoint(x: Float, y: Float, verify: Boolean = true): Outcome {
        val before = if (verify) ScreenAutomation.signature() else null
        val accepted = ScreenAutomation.tapPoint(x, y)
        if (!accepted) return Outcome(false, false, "I couldn't tap there.")
        if (!verify || before == null) return Outcome(true, false, "Tapped.")
        val changed = waitForChange(before, 1_200L)
        return Outcome(true, changed, if (changed) "Tapped." else "The screen did not change.")
    }

    /** Taps a 0-1000 screen fraction, the coordinate space the outline uses. */
    fun tapFraction(x: Int, y: Int, verify: Boolean = true): Outcome {
        val before = if (verify) ScreenAutomation.signature() else null
        val accepted = ScreenSnapshot.tapFraction(x, y)
        if (!accepted) return Outcome(false, false, "I couldn't tap there.")
        if (!verify || before == null) return Outcome(true, false, "Tapped.")
        val changed = waitForChange(before, 1_200L)
        return Outcome(true, changed, if (changed) "Tapped." else "The screen did not change.")
    }

    fun waitForChange(before: ScreenAutomation.Signature, timeoutMs: Long): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(160)
            val now = ScreenAutomation.signature()
            if (now != before) return true
        }
        return false
    }

    private fun isStateful(label: String): Boolean {
        val lower = label.lowercase().trim()
        if (lower.isEmpty()) return false
        return statefulLabels.any { lower == it || lower.startsWith("$it ") || lower.endsWith(" $it") }
    }

    /** Human-readable summary of an outline-to-outline difference. */
    fun describeChange(
        before: ScreenAutomation.Signature,
        after: ScreenAutomation.Signature,
    ): String = when {
        before.packageName != after.packageName -> "The screen moved to another app."
        before.labelsHash != after.labelsHash -> "The screen content changed."
        before.elementCount != after.elementCount -> "The screen changed."
        else -> "No visible change."
    }
}
