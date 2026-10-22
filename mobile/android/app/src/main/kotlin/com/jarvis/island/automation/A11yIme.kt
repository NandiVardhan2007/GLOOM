package com.jarvis.island.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.InputMethod
import android.os.Build
import android.os.SystemClock
import android.util.Log

/**
 * Types through the accessibility service's own input connection (API 33+).
 *
 * This is the same commitText path a keyboard uses, so target apps cannot
 * refuse it, and the user's own keyboard stays active. It is the only typing
 * channel that needs no ADB, no extra APK and no IME switch.
 *
 * The approach follows the design proven by kuafuai/andee (Apache-2.0); the
 * implementation here is our own and deliberately smaller.
 */
object A11yIme {

    const val VIA = "a11y_ime"
    private const val TAG = "JarvisA11yIme"
    private const val READ_BUDGET_MS = 700L
    private const val READ_POLL_MS = 60L
    private const val SESSION_WAIT_MS = 450L

    fun available(service: AccessibilityService): Boolean = connection(service) != null

    /** Replaces the focused editor's contents. Null means the channel is absent. */
    fun replace(service: AccessibilityService, text: String): String? {
        val conn = awaitConnection(service) ?: return null
        clear(conn)
        runCatching { conn.commitText(text, 1, null) }.onFailure {
            Log.w(TAG, "commitText failed", it)
            return null
        }
        var got = ""
        val deadline = SystemClock.uptimeMillis() + READ_BUDGET_MS
        do {
            Thread.sleep(READ_POLL_MS)
            got = read(conn) ?: return null
            if (got == text) break
        } while (SystemClock.uptimeMillis() < deadline)
        return got
    }

    /** Fires the focused editor's own IME action (Send / Search / Done / Next). */
    fun submit(service: AccessibilityService): Boolean {
        val conn = connection(service) ?: return false
        val info = runCatching { service.inputMethod?.currentInputEditorInfo }.getOrNull() ?: return false
        val action = if (info.actionId != 0) {
            info.actionId
        } else {
            info.imeOptions and android.view.inputmethod.EditorInfo.IME_MASK_ACTION
        }
        if (action == 0 || action == android.view.inputmethod.EditorInfo.IME_ACTION_NONE) return false
        return runCatching { conn.performEditorAction(action); true }.getOrDefault(false)
    }

    /**
     * True when the field sends only from its own button. Android answers
     * ACTION_IME_ENTER there while sending nothing, so callers should tap the
     * visible Send control instead.
     */
    fun sendsOnlyFromButton(service: AccessibilityService): Boolean {
        if (connection(service) == null) return false
        val info = runCatching { service.inputMethod?.currentInputEditorInfo }.getOrNull() ?: return false
        return info.actionId == 0 &&
            info.imeOptions and android.view.inputmethod.EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
    }

    private fun awaitConnection(service: AccessibilityService): InputMethod.AccessibilityInputConnection? {
        val deadline = SystemClock.uptimeMillis() + SESSION_WAIT_MS
        while (true) {
            connection(service)?.let { return it }
            if (SystemClock.uptimeMillis() >= deadline) return null
            Thread.sleep(50)
        }
    }

    private fun connection(service: AccessibilityService): InputMethod.AccessibilityInputConnection? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val inputMethod = runCatching { service.inputMethod }.getOrNull() ?: return null
        if (!runCatching { inputMethod.currentInputStarted }.getOrDefault(false)) return null
        return runCatching { inputMethod.currentInputConnection }.getOrNull()
    }

    private fun clear(conn: InputMethod.AccessibilityInputConnection) {
        val surrounding = runCatching { conn.getSurroundingText(MAX_READ, MAX_READ, 0) }.getOrNull() ?: return
        val text = surrounding.text?.toString().orEmpty()
        if (text.isEmpty()) return
        val start = surrounding.selectionStart
        val end = surrounding.selectionEnd
        runCatching {
            if (start >= 0 && end >= 0) {
                conn.deleteSurroundingText(start, text.length - end)
            } else {
                conn.setSelection(surrounding.offset, surrounding.offset + text.length)
            }
        }
    }

    private fun read(conn: InputMethod.AccessibilityInputConnection): String? {
        val surrounding = runCatching { conn.getSurroundingText(MAX_READ, MAX_READ, 0) }.getOrNull()
            ?: return null
        return surrounding.text?.toString().orEmpty()
    }

    private const val MAX_READ = 8192
}
