package com.jarvis.island.automation

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * OEM & Custom Hierarchy Normalizer (KuafuAI Andee architecture).
 *
 * Flattens obscured nodes and synthesizes actionable touch bounds for custom OEM
 * skins (MIUI/HyperOS, ColorOS, FunTouch) and self-drawn webviews/miniapps (WeChat).
 */
object OemTreeHelper {

    private const val TAG = "JarvisOemHelper"

    data class NormalizedNode(
        val node: AccessibilityNodeInfo,
        val effectiveBounds: Rect,
        val isActionable: Boolean,
        val syntheticLabel: String
    )

    /**
     * Inspects a node in custom OEM trees and synthesizes missing clickability
     * or actionable bounds if child views hold the actual labels/listeners.
     */
    fun normalize(node: AccessibilityNodeInfo?): NormalizedNode? {
        if (node == null) return null

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        val isDirectlyClickable = node.isClickable || node.isCheckable || node.isEditable
        var label = buildString {
            val t = node.text?.toString().orEmpty().trim()
            val d = node.contentDescription?.toString().orEmpty().trim()
            if (t.isNotEmpty()) append(t)
            if (d.isNotEmpty() && d != t) {
                if (isNotEmpty()) append(" ")
                append(d)
            }
        }

        var actionable = isDirectlyClickable

        // If a container has empty label but contains clickable or labeled leaf children, synthesize bounds
        if (!isDirectlyClickable && label.isEmpty() && node.childCount > 0) {
            val subLabel = findFirstDescriptiveChild(node, 0)
            if (subLabel.isNotEmpty()) {
                label = subLabel
                actionable = true
            }
        }

        return NormalizedNode(
            node = node,
            effectiveBounds = bounds,
            isActionable = actionable,
            syntheticLabel = label
        )
    }

    private fun findFirstDescriptiveChild(node: AccessibilityNodeInfo?, depth: Int): String {
        if (node == null || depth > 4) return ""
        for (i in 0 until node.childCount) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            val t = child.text?.toString().orEmpty().trim()
            val d = child.contentDescription?.toString().orEmpty().trim()
            if (t.isNotEmpty()) return t
            if (d.isNotEmpty()) return d
            val nested = findFirstDescriptiveChild(child, depth + 1)
            if (nested.isNotEmpty()) return nested
        }
        return ""
    }
}
