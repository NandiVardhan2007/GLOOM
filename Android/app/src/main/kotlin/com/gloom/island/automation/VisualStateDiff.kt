package com.gloom.island.automation

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Visual State Diffing & Perceptual Anchoring (Google Artemis architecture).
 *
 * Compares pre-action and post-action screen state to detect ghost taps,
 * infinite loading loops, and stalled transitions.
 */
object VisualStateDiff {

    private const val TAG = "GloomVisualDiff"

    data class ScreenSignature(
        val nodeCount: Int,
        val textHash: Int,
        val structuralHash: Int,
        val boundsSummary: List<Rect>,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class DiffResult(
        val isStalled: Boolean,
        val similarityRatio: Float,
        val changedNodes: Int,
        val description: String
    )

    /**
     * Extracts a fast, deterministic signature of the active window hierarchy.
     */
    fun captureSignature(root: AccessibilityNodeInfo?): ScreenSignature {
        if (root == null) return ScreenSignature(0, 0, 0, emptyList())

        var count = 0
        val textBuilder = StringBuilder()
        val structuralBuilder = StringBuilder()
        val boundsList = mutableListOf<Rect>()

        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 25) return
            count++

            val text = node.text?.toString().orEmpty()
            val desc = node.contentDescription?.toString().orEmpty()
            val id = node.viewIdResourceName.orEmpty()
            val cls = node.className?.toString().orEmpty()

            if (text.isNotEmpty()) textBuilder.append(text).append('|')
            if (desc.isNotEmpty()) textBuilder.append(desc).append('|')
            structuralBuilder.append(cls).append(':').append(id).append(';')

            if (node.isClickable || node.isEditable) {
                val r = Rect()
                node.getBoundsInScreen(r)
                if (boundsList.size < 50) boundsList.add(r)
            }

            for (i in 0 until node.childCount) {
                val child = runCatching { node.getChild(i) }.getOrNull()
                walk(child, depth + 1)
            }
        }

        walk(root, 0)

        return ScreenSignature(
            nodeCount = count,
            textHash = textBuilder.toString().hashCode(),
            structuralHash = structuralBuilder.toString().hashCode(),
            boundsSummary = boundsList
        )
    }

    /**
     * Compares two screen signatures to assess whether a gesture produced visible UI progress.
     */
    fun compare(before: ScreenSignature, after: ScreenSignature): DiffResult {
        if (before.nodeCount == 0 && after.nodeCount == 0) {
            return DiffResult(isStalled = true, similarityRatio = 1.0f, changedNodes = 0, "Empty screen before and after.")
        }

        val textChanged = before.textHash != after.textHash
        val structuralChanged = before.structuralHash != after.structuralHash
        val countDiff = kotlin.math.abs(before.nodeCount - after.nodeCount)

        var boundsMatching = 0
        val totalBounds = before.boundsSummary.size.coerceAtLeast(1)
        for (b1 in before.boundsSummary) {
            if (after.boundsSummary.any { it == b1 }) {
                boundsMatching++
            }
        }
        val spatialSimilarity = boundsMatching.toFloat() / totalBounds.toFloat()

        val isStalled = !textChanged && !structuralChanged && countDiff == 0 && spatialSimilarity > 0.95f
        val similarityRatio = if (isStalled) 1.0f else (spatialSimilarity * 0.7f + (if (!textChanged) 0.3f else 0f))

        val description = when {
            isStalled -> "Screen state remained static (0 changes detected — possible ghost tap or loading stall)."
            textChanged && !structuralChanged -> "Screen content updated (text changed, layout structure preserved)."
            structuralChanged -> "Screen layout transitioned (structure changed, $countDiff node count delta)."
            else -> "Minor visual changes detected (${(similarityRatio * 100).toInt()}% similarity)."
        }

        Log.d(TAG, "Visual diff: isStalled=$isStalled, similarity=$similarityRatio, textChanged=$textChanged")
        return DiffResult(isStalled, similarityRatio, countDiff, description)
    }
}
