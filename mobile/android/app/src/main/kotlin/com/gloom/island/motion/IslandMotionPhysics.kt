package com.gloom.island.motion

import android.animation.TimeInterpolator
import android.view.animation.PathInterpolator

/**
 * Apple & HarmonyOS Critically Damped Spring & Asymmetric Bezier Curves
 * for mobile camera cutout expansions.
 */
object IslandMotionPhysics {

    /**
     * Rapid acceleration with critically-damped deceleration.
     * Snaps the cutout pill from 120dp to 340dp in ~180ms with zero vibration.
     */
    val MorphInterpolator: TimeInterpolator = PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f)

    /**
     * Subtle spring swell when a notification or live activity arrives.
     */
    val SwellInterpolator: TimeInterpolator = PathInterpolator(0.34f, 1.56f, 0.64f, 1.0f)

    /**
     * Ultra-fast exit collapse curve (~120ms).
     */
    val CollapseInterpolator: TimeInterpolator = PathInterpolator(0.4f, 0.0f, 0.2f, 1.0f)

    const val DURATION_EXPAND_MS = 200L
    const val DURATION_COLLAPSE_MS = 140L
    const val DURATION_FADE_MS = 100L
}
