package com.jarvis.island.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Apple Dynamic Island geometry, in dp, from the Human Interface Guidelines
 * "Live Activities" specifications (iOS 393 x 852 class).
 *
 * - island at rest            126 x 36.67
 * - compact leading/trailing  52.33 x 36.67 each
 * - camera band               125.34 x 36.67
 * - compact total             230 x 36.67
 * - expanded                  400 x 180, corner radius 36
 * - content margin            14, artwork 44
 *
 * Android's camera cutout is smaller than the TrueDepth system, so the
 * island keeps Apple's proportions and covers the hole in the camera band.
 */
object AppleIslandSpec {
    val restWidth = 126.dp
    val compactWidth = 230.dp
    val compactHeight = 36.67.dp
    val compactRadius = 18.335.dp

    val wing = 52.33.dp
    val cameraGap = 125.34.dp
    val compactInset = 11.dp
    val compactArtwork = 22.dp

    val expandedWidth = 400.dp
    val expandedHeight = 180.dp
    val expandedCorner = 36.dp
    val contentMargin = 14.dp
    val contentTop = 34.dp
    val artwork = 44.dp

    const val MIN_TOUCH_DP = 44
}

/** Island type ramp. The platform font is used, with SF-style tracking. */
object IslandType {
    val compactTitle = androidx.compose.ui.text.TextStyle(
        fontSize = 13.sp,
        lineHeight = 16.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        letterSpacing = (-0.08).sp,
    )
    val compactNumber = compactTitle.copy(
        fontFeatureSettings = "tnum"
    )
    val title = androidx.compose.ui.text.TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        letterSpacing = (-0.24).sp,
    )
    val subtitle = androidx.compose.ui.text.TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
        letterSpacing = (-0.08).sp,
    )
    val caption = androidx.compose.ui.text.TextStyle(
        fontSize = 11.sp,
        lineHeight = 13.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        letterSpacing = 0.06.sp,
    )
    val time = androidx.compose.ui.text.TextStyle(
        fontSize = 11.sp,
        lineHeight = 13.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        letterSpacing = 0.06.sp,
        fontFeatureSettings = "tnum",
    )
    val button = androidx.compose.ui.text.TextStyle(
        fontSize = 13.sp,
        lineHeight = 16.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        letterSpacing = (-0.08).sp,
    )
    val heroNumber = androidx.compose.ui.text.TextStyle(
        fontSize = 24.sp,
        lineHeight = 28.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        letterSpacing = (-0.4).sp,
        fontFeatureSettings = "tnum"
    )
}
