package com.jarvis.island.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvis.island.service.JarvisIslandAccessibilityService
import com.jarvis.island.voice.AppLauncher

/**
 * Screen reading and hands-on automation for the accessibility service.
 *
 * The model is the one both reference projects converged on: read the screen
 * as a compact numbered outline, then act on an element number instead of
 * guessing pixel coordinates. Coordinates are only used when a node has no
 * bounds to act on.
 */
object ScreenAutomation {

    private const val TAG = "JarvisAutomation"
    private const val MAX_ELEMENTS = 220
    private const val MAX_DEPTH = 40
    private const val CACHE_MS = 25_000L

    data class Element(
        val eid: Int,
        val className: String,
        val label: String,
        val viewId: String,
        val bounds: Rect,
        val clickable: Boolean,
        val editable: Boolean,
        val scrollable: Boolean,
        val longClickable: Boolean,
        val node: AccessibilityNodeInfo,
        val actionTarget: AccessibilityNodeInfo?,
    )

    data class TypeResult(val success: Boolean, val via: String, val detail: String)

    /**
     * A cheap fingerprint of the current screen: package, element count and the
     * first labels with their positions. Two equal signatures mean the outline
     * did not change, which is how post-action verification detects a tap that
     * was accepted but did nothing.
     */
    data class Signature(
        val packageName: String,
        val elementCount: Int,
        val actionableCount: Int,
        val labelsHash: Int,
    )

    private val lock = Any()
    private var cache: List<Element> = emptyList()
    private var cachedAt = 0L
    private var cachedPackage = ""

    private fun service(): AccessibilityService? = JarvisIslandAccessibilityService.instance

    fun isReady(): Boolean = service() != null

    fun currentPackage(): String {
        val svc = service() ?: return ""
        return runCatching { svc.rootInActiveWindow?.packageName?.toString().orEmpty() }.getOrDefault("")
    }

    fun refresh(): List<Element> {
        val svc = service() ?: return emptyList()
        val result = ArrayList<Element>(MAX_ELEMENTS)
        val seen = HashSet<String>()
        var pkg = ""

        val roots = ArrayList<AccessibilityNodeInfo>()
        val activeRoot = runCatching { svc.rootInActiveWindow }.getOrNull()
        activeRoot?.let { roots.add(it) }
        runCatching { svc.windows }.getOrNull()?.forEach { window ->
            runCatching { window.root }.getOrNull()?.let { root ->
                if (roots.none { it == root }) roots.add(root)
            }
        }

        // Our own Voice panel and overlay sit above the target app. Act on the
        // app underneath first, and only fall back to our own window when the
        // user is actually working inside JARVIS.
        val ordered = roots.filter { root ->
            runCatching { root.packageName?.toString() != svc.packageName }.getOrDefault(true)
        } + roots.filter { root ->
            runCatching { root.packageName?.toString() == svc.packageName }.getOrDefault(false)
        }

        for (root in ordered) {
            val rootPackage = runCatching { root.packageName?.toString().orEmpty() }.getOrDefault("")
            if (rootPackage.isNotEmpty()) pkg = rootPackage
            val before = result.size
            walk(root, 0, result, seen, null)
            if (result.size > before) break
        }

        synchronized(lock) {
            cache = result
            cachedAt = SystemClock.uptimeMillis()
            cachedPackage = pkg
        }
        return result
    }

    private fun walk(
        node: AccessibilityNodeInfo,
        depth: Int,
        out: ArrayList<Element>,
        seen: HashSet<String>,
        inheritedAction: AccessibilityNodeInfo?,
    ) {
        if (depth > MAX_DEPTH || out.size >= MAX_ELEMENTS) return
        val bounds = Rect()
        runCatching { node.getBoundsInScreen(bounds) }
        val hasBounds = !bounds.isEmpty
        val text = runCatching { node.text?.toString()?.trim().orEmpty() }.getOrDefault("")
        val desc = runCatching { node.contentDescription?.toString()?.trim().orEmpty() }.getOrDefault("")
        val viewId = runCatching { node.viewIdResourceName.orEmpty() }.getOrDefault("")
        val className = runCatching { node.className?.toString().orEmpty() }.getOrDefault("")
        val clickable = runCatching { node.isClickable }.getOrDefault(false)
        val editable = runCatching { node.isEditable }.getOrDefault(false)
        val scrollable = runCatching { node.isScrollable }.getOrDefault(false)
        val longClickable = runCatching { node.isLongClickable }.getOrDefault(false)
        val actionable = clickable || editable || longClickable

        val nextAction = if (actionable) node else inheritedAction
        val label = when {
            text.isNotEmpty() -> text
            desc.isNotEmpty() -> desc
            viewId.isNotEmpty() -> "#" + viewId.substringAfterLast('/')
            else -> ""
        }
        val interesting = label.isNotEmpty() || actionable || scrollable
        if (interesting && hasBounds) {
            val signature = "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}|$label|$className"
            if (seen.add(signature)) {
                out.add(
                    Element(
                        eid = out.size,
                        className = shortClass(className),
                        label = oneLine(label),
                        viewId = viewId,
                        bounds = Rect(bounds),
                        clickable = clickable,
                        editable = editable,
                        scrollable = scrollable,
                        longClickable = longClickable,
                        node = node,
                        actionTarget = nextAction,
                    )
                )
            }
        }

        val childCount = runCatching { node.childCount }.getOrDefault(0)
        for (index in 0 until childCount) {
            if (out.size >= MAX_ELEMENTS) break
            val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
            walk(child, depth + 1, out, seen, nextAction)
        }
    }

    fun snapshot(): List<Element> {
        val current = synchronized(lock) { cache }
        if (current.isEmpty() || SystemClock.uptimeMillis() - cachedAt > CACHE_MS) return refresh()
        return current
    }

    fun element(eid: Int): Element? = snapshot().firstOrNull { it.eid == eid }

    fun signature(): Signature {
        val elements = refresh()
        val labels = buildString {
            for (element in elements.take(28)) {
                append(element.label)
                append('@')
                append(element.bounds.left)
                append(',')
                append(element.bounds.top)
                append('|')
            }
        }
        return Signature(
            packageName = currentPackage(),
            elementCount = elements.size,
            actionableCount = elements.count { it.clickable || it.editable || it.scrollable },
            labelsHash = labels.hashCode(),
        )
    }

    fun describe(limit: Int = 100): String {
        val elements = refresh()
        if (elements.isEmpty()) return "package=${currentPackage()}\n(no readable elements)"
        val width = screenWidth().coerceAtLeast(1)
        val height = screenHeight().coerceAtLeast(1)
        val lines = ArrayList<String>(minOf(elements.size, limit) + 1)
        lines.add("package=${currentPackage()}")
        for (element in elements.take(limit)) {
            val centreX = element.bounds.centerX() * 1000 / width
            val centreY = element.bounds.centerY() * 1000 / height
            val mark = if (element.clickable || element.editable) "*" else ""
            val flags = buildString {
                if (element.editable) append("E")
                if (element.scrollable) append("S")
            }.let { if (it.isEmpty()) "" else "{$it}" }
            lines.add("e${element.eid}$mark$flags|${element.className}|${element.label}|[$centreX,$centreY]")
        }
        return lines.joinToString("\n")
    }

    fun tap(eid: Int): Boolean {
        val element = element(eid) ?: return false
        val cx = element.bounds.centerX().toFloat()
        val cy = element.bounds.centerY().toFloat()

        // Physical touch gesture takes priority for universal compatibility across
        // native views, Jetpack Compose, Flutter, and WebViews.
        if (cx > 0f && cy > 0f) {
            val tapped = tapPoint(cx, cy)
            if (tapped) {
                invalidate()
                return true
            }
        }

        val target = element.actionTarget ?: element.node
        val clicked = runCatching { target.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)
        invalidate()
        return clicked
    }

    fun longPress(eid: Int, durationMs: Long = 650L): Boolean {
        val element = element(eid) ?: return false
        val target = element.actionTarget
        if (target != null && runCatching {
                target.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            }.getOrDefault(false)
        ) {
            invalidate()
            return true
        }
        val path = Path().apply { moveTo(element.bounds.centerX().toFloat(), element.bounds.centerY().toFloat()) }
        val ok = dispatch(path, durationMs.coerceIn(400L, 2000L))
        invalidate()
        return ok
    }

    fun tapLabel(
        labels: List<String>,
        contains: Boolean = true,
        editable: Boolean? = null,
    ): Element? {
        val wanted = labels.map { it.lowercase().trim() }.filter { it.isNotEmpty() }
        if (wanted.isEmpty()) return null
        val elements = snapshot().ifEmpty { refresh() }
        var best: Element? = null
        var bestScore = 0
        for (element in elements) {
            if (editable != null && element.editable != editable) continue
            val label = element.label.lowercase()
            if (label.isEmpty()) continue
            var score = 0
            for (want in wanted) {
                score = maxOf(
                    score,
                    when {
                        label == want -> 100
                        label.startsWith(want) -> 80
                        contains && label.contains(want) -> 60
                        element.viewId.lowercase().contains(want) -> 35
                        else -> 0
                    }
                )
            }
            if (score == 0) continue
            if (element.actionTarget != null) score += 20
            if (element.editable) score += 15
            if (element.bounds.width() > 0 && element.bounds.height() > 0) score += 5
            if (score > bestScore) {
                bestScore = score
                best = element
            }
        }
        return best
    }

    fun clickLabel(labels: List<String>, contains: Boolean = true): Boolean {
        val element = tapLabel(labels, contains) ?: return false
        return tap(element.eid)
    }

    fun typeText(text: String): TypeResult {
        val svc = service() ?: return TypeResult(false, "none", "Accessibility is not running")

        val imeResult = A11yIme.replace(svc, text)
        if (imeResult != null) {
            return if (imeResult == text) {
                TypeResult(true, A11yIme.VIA, "Typed ${text.length} characters")
            } else {
                TypeResult(false, A11yIme.VIA, "Field holds ${imeResult.length} of ${text.length} characters")
            }
        }

        val focused = focusedEditable(svc)
        if (focused != null) {
            val bundle = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val accepted = runCatching {
                focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
            }.getOrDefault(false)
            val readBack = runCatching { focused.text?.toString().orEmpty() }.getOrDefault("")
            if (accepted && (readBack == text || readBack.isEmpty())) {
                invalidate()
                return TypeResult(true, "set_text", "Typed ${text.length} characters")
            }
        }

        val clipboardTarget = focusedEditable(svc) ?: findEditableNode(svc)
        if (clipboardTarget != null) {
            val clipboard = svc.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("JARVIS", text))
                val pasted = runCatching {
                    clipboardTarget.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                }.getOrDefault(false)
                if (pasted) {
                    invalidate()
                    return TypeResult(true, "paste", "Pasted ${text.length} characters")
                }
            }
        }
        return TypeResult(false, "none", "No focused input field could accept text")
    }

    fun submit(): Boolean {
        val svc = service() ?: return false
        if (A11yIme.submit(svc)) return true
        val focused = focusedEditable(svc)
        if (focused != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val action = AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id
            if (runCatching { focused.performAction(action) }.getOrDefault(false)) {
                invalidate()
                return true
            }
        }
        return clickLabel(listOf("send", "submit", "search", "go", "done", "arrow"), contains = true)
    }

    fun scroll(direction: String, times: Int = 1): Int {
        val action = when (direction.lowercase()) {
            "up", "back", "backward" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            else -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }
        var performed = 0
        repeat(times.coerceIn(1, 10)) {
            val target = findScrollableNode()
            val ok = target != null &&
                runCatching { target.performAction(action) }.getOrDefault(false)
            if (ok) {
                performed += 1
                SystemClock.sleep(280)
            } else {
                if (swipe(direction)) {
                    performed += 1
                    SystemClock.sleep(220)
                }
            }
            invalidate()
        }
        return performed
    }

    fun swipe(direction: String): Boolean {
        val width = screenWidth().toFloat()
        val height = screenHeight().toFloat()
        val (startX, startY, endX, endY) = when (direction.lowercase()) {
            "up" -> floatArrayOf(width * 0.5f, height * 0.7f, width * 0.5f, height * 0.3f)
            "down" -> floatArrayOf(width * 0.5f, height * 0.3f, width * 0.5f, height * 0.72f)
            "left" -> floatArrayOf(width * 0.8f, height * 0.5f, width * 0.2f, height * 0.5f)
            else -> floatArrayOf(width * 0.2f, height * 0.5f, width * 0.8f, height * 0.5f)
        }
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val ok = dispatch(path, 280L)
        invalidate()
        return ok
    }

    fun tapPoint(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        return dispatch(path, 60L)
    }

    fun global(action: String): Boolean {
        val svc = service() ?: return false
        val code = when (action.lowercase()) {
            "back" -> AccessibilityService.GLOBAL_ACTION_BACK
            "home" -> AccessibilityService.GLOBAL_ACTION_HOME
            "recents", "recent" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            "notifications", "shade" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings", "settings" -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            "lock" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
            } else return false
            else -> return false
        }
        return runCatching { svc.performGlobalAction(code) }.getOrDefault(false)
    }

    fun launch(context: Context, query: String): String? = AppLauncher.launch(context, query)

    fun waitForText(text: String, timeoutMs: Long = 2500L): Element? {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            val found = tapLabel(listOf(text), contains = true)
            if (found != null) return found
            SystemClock.sleep(180)
            refresh()
        }
        return null
    }

    fun findEditableNode(svc: AccessibilityService? = service()): AccessibilityNodeInfo? {
        val active = svc ?: return null
        val root = runCatching { active.rootInActiveWindow }.getOrNull() ?: return null
        return findEditable(root, 0)
    }

    private fun focusedEditable(svc: AccessibilityService): AccessibilityNodeInfo? {
        val root = runCatching { svc.rootInActiveWindow }.getOrNull() ?: return null
        val focused = runCatching {
            root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        }.getOrNull()
        if (focused != null && focused.isEditable) return focused
        return findEditableNode(svc)
    }

    private fun findEditable(node: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
        if (depth > MAX_DEPTH) return null
        if (runCatching { node.isEditable }.getOrDefault(false)) return node
        val count = runCatching { node.childCount }.getOrDefault(0)
        for (index in 0 until count) {
            val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
            findEditable(child, depth + 1)?.let { return it }
        }
        return null
    }

    private fun findScrollableNode(): AccessibilityNodeInfo? {
        val svc = service() ?: return null
        val root = runCatching { svc.rootInActiveWindow }.getOrNull() ?: return null
        return findScrollable(root, 0)
    }

    private fun findScrollable(node: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
        if (depth > MAX_DEPTH) return null
        if (runCatching { node.isScrollable }.getOrDefault(false)) return node
        val count = runCatching { node.childCount }.getOrDefault(0)
        for (index in 0 until count) {
            val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
            findScrollable(child, depth + 1)?.let { return it }
        }
        return null
    }

    private fun dispatch(path: Path, durationMs: Long): Boolean {
        val svc = service() ?: return false
        return try {
            val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs.coerceAtLeast(1L))
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            svc.dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Log.w(TAG, "Gesture dispatch failed: ${e.message}")
            false
        }
    }

    private fun invalidate() {
        synchronized(lock) {
            cachedAt = 0L
        }
    }

    fun screenWidth(): Int =
        service()?.resources?.displayMetrics?.widthPixels ?: 1080

    fun screenHeight(): Int =
        service()?.resources?.displayMetrics?.heightPixels ?: 2400

    private fun shortClass(name: String): String = name.substringAfterLast('.')

    private fun oneLine(value: String): String =
        value.replace('\n', ' ').replace('\r', ' ').replace('|', '/').trim().take(120)
}
