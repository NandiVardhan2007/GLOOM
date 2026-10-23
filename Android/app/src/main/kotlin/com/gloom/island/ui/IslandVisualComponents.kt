package com.gloom.island.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.app.KeyguardManager
import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gloom.island.ui.Apple
import java.util.Locale

/** Colors for custom themes. The island surface itself is always black. */
data class IslandThemeColors(
    val background: Color,
    val cardBackground: Color,
    val primaryAccent: Color,
    val secondaryAccent: Color,
    val borderBrush: Brush
)

object IslandThemePalettes {
    private val hairline = SolidColor(Color(0x1FFFFFFF))

    fun getTheme(themeName: String): IslandThemeColors {
        return when (themeName) {
            "Cyber Cyan" -> IslandThemeColors(
                background = Color(0xFF030712),
                cardBackground = Color(0xFF0B132B),
                primaryAccent = Apple.systemBlue,
                secondaryAccent = Apple.systemTeal,
                borderBrush = hairline
            )
            "Emerald Matrix", "Neon Green" -> IslandThemeColors(
                background = Color(0xFF021008),
                cardBackground = Color(0xFF071911),
                primaryAccent = Apple.systemGreen,
                secondaryAccent = Apple.systemGreen,
                borderBrush = hairline
            )
            "Material Purple" -> IslandThemeColors(
                background = Color(0xFF0E0414),
                cardBackground = Color(0xFF1B0B26),
                primaryAccent = Apple.systemPurple,
                secondaryAccent = Apple.systemIndigo,
                borderBrush = hairline
            )
            else -> IslandThemeColors(
                background = Color.Black,
                cardBackground = Apple.secondaryGroupedBackground,
                primaryAccent = Apple.systemBlue,
                secondaryAccent = Apple.systemGreen,
                borderBrush = hairline
            )
        }
    }
}

enum class IslandCompactKind {
    MEDIA, TIMER, TRANSFER, NOTIFICATION, CHARGING, DROP, REEL_COUNTER, LOCK, IDLE
}

/** Small 4-bar equalizer in the SF Symbols waveform spirit. */
@Composable
fun AppleEqualizer(
    isPlaying: Boolean,
    color: Color = Apple.systemGreen,
    height: Dp = 14.dp,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "apple-eq")
    val finals = listOf(0.42f, 0.92f, 0.58f, 1f)
    val bars = finals.mapIndexed { index, factor ->
        transition.animateFloat(
            initialValue = 0.28f,
            targetValue = if (isPlaying) factor else 0.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = if (isPlaying) 820 + index * 120 else 180,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar-$index"
        )
    }

    Row(
        modifier = modifier.height(height),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        bars.forEach { value ->
            val barHeight = (height * value.value).coerceAtLeast(3.dp)
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(1.25.dp))
                    .background(color)
            )
        }
    }
}

/** 22dp artwork for the compact wings. */
@Composable
fun AppleCompactArtwork(track: MediaTrackInfo) {
    val size = AppleIslandSpec.compactArtwork
    val shape = RoundedCornerShape(6.5.dp)
    if (track.albumArt != null) {
        Image(
            bitmap = track.albumArt.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(shape)
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(Apple.systemPink, Color(0xFFB3123F))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            GloomIcon(GloomIcons.MusicNote, Color.White, size * 0.58f)
        }
    }
}

/**
 * Apple's compact presentation: leading and trailing elements on either side
 * of the camera band, with no padding between content and the camera.
 */
@Composable
fun AppleCompactContent(
    kind: IslandCompactKind,
    track: MediaTrackInfo,
    transfer: ActiveTransferInfo,
    notification: NotificationIslandEvent?,
    batteryLevel: Int,
    isCharging: Boolean,
    timerText: String,
    wingWidth: Dp,
    cameraGap: Dp,
    inset: Dp,
    reelCount: Int = 0,
    lockEvent: LockIslandEvent? = null,
    modifier: Modifier = Modifier
) {
    val contentTransition = (
        fadeIn(tween(160)) + scaleIn(initialScale = 0.94f, animationSpec = spring(1f, 320f))
        ).togetherWith(fadeOut(tween(100)))

    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(wingWidth)
                .fillMaxHeight(),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(modifier = Modifier.padding(start = inset)) {
                AnimatedContent(
                    targetState = kind,
                    transitionSpec = { contentTransition },
                    label = "compactLeading"
                ) { state ->
                    when (state) {
                        IslandCompactKind.MEDIA -> AppleCompactArtwork(track)
                        IslandCompactKind.TIMER -> CompactGlyphCircle(
                            icon = GloomIcons.Timer,
                            tint = Apple.systemOrange,
                            background = Apple.systemOrange.copy(alpha = 0.18f)
                        )
                        IslandCompactKind.TRANSFER -> CompactGlyphCircle(
                            icon = if (transfer.isUpload) GloomIcons.Beam else GloomIcons.DownloadDoc,
                            tint = Apple.systemBlue,
                            background = Apple.systemBlue.copy(alpha = 0.18f)
                        )
                        IslandCompactKind.NOTIFICATION -> {
                            val notifStyle = NotificationStyleRegistry.getStyleForPackage(notification?.packageName)
                            val notifAccent = notifStyle.accentColor
                            if (notification?.largeIcon != null) {
                                Image(
                                    bitmap = notification.largeIcon.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(AppleIslandSpec.compactArtwork)
                                        .clip(CircleShape)
                                        .border(0.5.dp, notifAccent.copy(alpha = 0.5f), CircleShape)
                                )
                            } else {
                                CompactGlyphCircle(
                                    icon = GloomIcons.Bell,
                                    tint = notifAccent,
                                    background = notifAccent.copy(alpha = 0.18f)
                                )
                            }
                        }
                        IslandCompactKind.CHARGING -> GloomIcon(
                            icon = GloomIcons.BoltFill,
                            tint = Apple.systemGreen,
                            size = 16.dp
                        )
                        IslandCompactKind.DROP -> CompactGlyphCircle(
                            icon = GloomIcons.Beam,
                            tint = Apple.systemBlue,
                            background = Apple.systemBlue.copy(alpha = 0.18f)
                        )
                        IslandCompactKind.REEL_COUNTER -> CompactGlyphCircle(
                            icon = GloomIcons.Instagram,
                            tint = Color.White,
                            background = Color(0xFFE1306C)
                        )
                        IslandCompactKind.LOCK -> {
                            val unlocked = lockEvent?.isUnlocked == true
                            AppleAnimatedLockIcon(
                                isUnlocked = unlocked,
                                size = 18.dp,
                                tint = if (unlocked) Apple.systemGreen else Color.White
                            )
                        }
                        IslandCompactKind.IDLE -> Spacer(Modifier.size(0.dp))
                    }
                }
            }
        }

        // TrueDepth camera band. Never place content here.
        Spacer(Modifier.width(cameraGap).height(1.dp))

        Box(
            modifier = Modifier
                .width(wingWidth)
                .fillMaxHeight(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(modifier = Modifier.padding(end = inset)) {
                AnimatedContent(
                    targetState = kind,
                    transitionSpec = { contentTransition },
                    label = "compactTrailing"
                ) { state ->
                    when (state) {
                        IslandCompactKind.MEDIA -> if (track.isPlaying) {
                            AppleEqualizer(isPlaying = true, height = 13.dp)
                        } else {
                            GloomIcon(GloomIcons.Waveform, Apple.secondaryLabel, 15.dp)
                        }
                        IslandCompactKind.TIMER -> Text(
                            text = timerText,
                            style = IslandType.compactNumber,
                            color = Apple.systemOrange,
                            maxLines = 1
                        )
                        IslandCompactKind.TRANSFER -> Text(
                            text = "${(transfer.progress * 100).toInt()}%",
                            style = IslandType.compactNumber,
                            color = Color.White,
                            maxLines = 1
                        )
                        IslandCompactKind.NOTIFICATION -> Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Apple.systemBlue)
                        )
                        IslandCompactKind.CHARGING -> Text(
                            text = "$batteryLevel%",
                            style = IslandType.compactNumber,
                            color = Apple.systemGreen,
                            maxLines = 1
                        )
                        IslandCompactKind.DROP -> Text(
                            text = "Beam",
                            style = IslandType.compactNumber.copy(fontSize = 12.sp),
                            color = Apple.systemBlue,
                            maxLines = 1
                        )
                        IslandCompactKind.REEL_COUNTER -> Text(
                            text = "$reelCount",
                            style = IslandType.compactNumber,
                            color = Color.White,
                            maxLines = 1
                        )
                        IslandCompactKind.LOCK -> {
                            val unlocked = lockEvent?.isUnlocked == true
                            Text(
                                text = if (unlocked) "Unlocked" else "Locked",
                                style = IslandType.compactNumber.copy(
                                    fontSize = 10.5.sp,
                                    letterSpacing = (-0.3).sp
                                ),
                                color = if (unlocked) Apple.systemGreen else Apple.secondaryLabel,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        IslandCompactKind.IDLE -> Spacer(Modifier.size(0.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun CompactGlyphCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    background: Color
) {
    Box(
        modifier = Modifier
            .size(AppleIslandSpec.compactArtwork)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        GloomIcon(icon, tint, 13.dp)
    }
}

/**
 * Apple's expanded music Live Activity, 400 x 180.
 */
@Composable
fun AppleMusicActivity(
    track: MediaTrackInfo,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: ((Float) -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = AppleIslandSpec.contentMargin,
                end = AppleIslandSpec.contentMargin,
                top = 32.dp,
                bottom = 8.dp
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val artworkShape = RoundedCornerShape(10.dp)
            if (track.albumArt != null) {
                Image(
                    bitmap = track.albumArt.asImageBitmap(),
                    contentDescription = "Album artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(AppleIslandSpec.artwork)
                        .clip(artworkShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(AppleIslandSpec.artwork)
                        .clip(artworkShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Apple.systemPink, Color(0xFFB3123F))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    GloomIcon(GloomIcons.MusicNote, Color.White, 18.dp)
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title.ifBlank { "Nothing Playing" },
                    style = IslandType.title,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist.ifBlank { "Start playback in any app" },
                    style = IslandType.subtitle,
                    color = Apple.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            AppleEqualizer(
                isPlaying = track.isPlaying,
                color = if (track.isPlaying) Apple.systemGreen else Apple.tertiaryLabel,
                height = 14.dp
            )
            ActivityCloseButton(onClose)
        }

        Spacer(Modifier.height(6.dp))

        AppleSeekBar(
            progress = track.progress,
            positionMs = track.positionMs,
            durationMs = track.durationMs,
            onSeek = onSeek
        )

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            ActivityTransportButton(
                label = "Previous track",
                onClick = onPrev
            ) {
                GloomIcon(GloomIcons.SkipBackFill, Color.White, 19.dp)
            }
            Spacer(Modifier.width(26.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onPlayPause
                    ),
                contentAlignment = Alignment.Center
            ) {
                GloomIcon(
                    icon = if (track.isPlaying) GloomIcons.PauseFill else GloomIcons.PlayFill,
                    tint = Color.Black,
                    size = 18.dp
                )
            }
            Spacer(Modifier.width(26.dp))
            ActivityTransportButton(
                label = "Next track",
                onClick = onNext
            ) {
                GloomIcon(GloomIcons.SkipForwardFill, Color.White, 19.dp)
            }
        }
    }
}

/**
 * Apple's expanded transfer Live Activity.
 */
@Composable
fun AppleTransferActivity(
    transfer: ActiveTransferInfo,
    accentColor: Color,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val done = transfer.progress >= 1f || transfer.speedMbps.equals("Completed", ignoreCase = true)
    val failed = transfer.speedMbps.startsWith("Failed", ignoreCase = true)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = AppleIslandSpec.contentMargin,
                end = AppleIslandSpec.contentMargin,
                top = 34.dp,
                bottom = 8.dp
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(AppleIslandSpec.artwork)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (failed) Apple.systemRed.copy(alpha = 0.18f)
                        else if (done) Apple.systemGreen.copy(alpha = 0.18f)
                        else Apple.systemBlue.copy(alpha = 0.18f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                GloomIcon(
                    icon = if (failed) GloomIcons.XCircleFill
                    else if (done) GloomIcons.CheckCircleFill
                    else if (transfer.isUpload) GloomIcons.Beam else GloomIcons.DownloadDoc,
                    tint = if (failed) Apple.systemRed else if (done) Apple.systemGreen else Apple.systemBlue,
                    size = 22.dp
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transfer.fileName.ifBlank { "Transfer" },
                    style = IslandType.title,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (failed) "Transfer failed"
                    else if (done) "Transfer complete"
                    else if (transfer.isUpload) "Sending to GLOOM PC" else "Receiving from GLOOM PC",
                    style = IslandType.subtitle,
                    color = if (failed) Apple.systemRed else Apple.secondaryLabel,
                    maxLines = 1
                )
            }

            Text(
                text = if (failed) "Failed" else if (done) "Complete" else transfer.speedMbps,
                style = IslandType.time,
                color = if (failed) Apple.systemRed else if (done) Apple.systemGreen else Apple.secondaryLabel,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x1FFFFFFF))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            ActivityCloseButton(onClose)
        }

        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0x24FFFFFF))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(transfer.progress.coerceIn(0.02f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (failed) Apple.systemRed else if (done) Apple.systemGreen else accentColor)
            )
        }

        Spacer(Modifier.height(7.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = String.format(
                    Locale.US,
                    "%.1f MB of %.1f MB",
                    transfer.fileSizeMb * transfer.progress,
                    transfer.fileSizeMb
                ),
                style = IslandType.time,
                color = Apple.secondaryLabel
            )
            Text(
                text = "${(transfer.progress * 100).toInt()}%",
                style = IslandType.time,
                color = if (done) Apple.systemGreen else Apple.secondaryLabel
            )
        }
    }
}

/**
 * Apple's expanded notification presentation with per-app styling and quick actions.
 */
@Composable
fun AppleNotificationActivity(
    event: NotificationIslandEvent,
    accentColor: Color,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val appStyle = remember(event.packageName) {
        NotificationStyleRegistry.getStyleForPackage(event.packageName)
    }
    val effectiveAccent = appStyle.accentColor
    var copied by remember(event.id) { mutableStateOf(false) }
    var isReplying by remember(event.id) { mutableStateOf(false) }
    var replyText by remember(event.id) { mutableStateOf("") }
    var replySent by remember(event.id) { mutableStateOf(false) }
    var replyError by remember(event.id) { mutableStateOf(false) }
    val otpCode = event.otpCode
    val replyAction = event.replyAction

    val isKeyguardLocked = remember {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        km?.isKeyguardLocked == true
    }

    fun copyCode() {
        val code = otpCode ?: return
        clipboard.setText(AnnotatedString(code))
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        copied = true
    }

    androidx.compose.runtime.LaunchedEffect(replySent) {
        if (replySent) {
            delay(900)
            onDismiss()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = AppleIslandSpec.contentMargin,
                end = AppleIslandSpec.contentMargin,
                top = 28.dp,
                bottom = 8.dp
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (event.largeIcon != null) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Image(
                        bitmap = event.largeIcon.asImageBitmap(),
                        contentDescription = "Sender avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(AppleIslandSpec.artwork)
                            .clip(CircleShape)
                            .border(1.dp, effectiveAccent.copy(alpha = 0.5f), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(effectiveAccent)
                            .border(1.dp, Color.Black, CircleShape)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(AppleIslandSpec.artwork)
                        .clip(CircleShape)
                        .background(effectiveAccent.copy(alpha = 0.18f))
                        .border(1.dp, effectiveAccent.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    GloomIcon(GloomIcons.Bell, effectiveAccent, 20.dp)
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = event.appName.ifBlank { appStyle.brandName }.uppercase(),
                        style = IslandType.caption,
                        color = effectiveAccent,
                        maxLines = 1
                    )
                    if (event.conversationTitle.isNotBlank() && event.conversationTitle != event.title) {
                        Text(
                            text = " • ${event.conversationTitle}",
                            style = IslandType.caption,
                            color = Apple.secondaryLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    text = event.title.ifBlank { "New message" },
                    style = IslandType.title,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ActivityCloseButton(onDismiss)
        }

        Spacer(Modifier.height(8.dp))

        if (otpCode != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x1FFFFFFF))
                    .clickable { copyCode() }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = otpCode,
                    style = IslandType.title.copy(letterSpacing = 3.sp),
                    color = Color.White
                )
                Text(
                    text = if (copied) "Copied" else "Copy",
                    style = IslandType.caption,
                    color = if (copied) Apple.systemGreen else effectiveAccent
                )
            }
        } else {
            Text(
                text = event.text,
                style = IslandType.subtitle.copy(lineHeight = 16.sp),
                color = Apple.secondaryLabel,
                maxLines = if (isReplying) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )
            if (event.subText.isNotBlank()) {
                Text(
                    text = event.subText,
                    style = IslandType.caption,
                    color = Apple.tertiaryLabel,
                    maxLines = 1
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (replySent) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x2EFFFFFF)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                GloomIcon(GloomIcons.CheckCircleFill, Apple.systemGreen, 16.dp)
                Spacer(Modifier.width(6.dp))
                Text("Reply Sent", style = IslandType.caption, color = Color.White)
            }
        } else if (replyError) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Apple.systemRed.copy(alpha = 0.25f))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GloomIcon(GloomIcons.XCircleFill, Apple.systemRed, 14.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Could not send reply", style = IslandType.caption, color = Color.White)
                }
                Text(
                    text = "Open App",
                    style = IslandType.caption,
                    color = effectiveAccent,
                    modifier = Modifier.clickable { onOpen() }
                )
            }
        } else if (isReplying && replyAction != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(Color(0x2EFFFFFF))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    textStyle = IslandType.subtitle.copy(color = Color.White),
                    cursorBrush = SolidColor(effectiveAccent),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        if (replyText.isEmpty()) {
                            Text(
                                text = "Reply to ${event.title.take(16)}...",
                                style = IslandType.caption,
                                color = Apple.tertiaryLabel
                            )
                        }
                        innerTextField()
                    }
                )
                if (replyText.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(effectiveAccent)
                            .clickable {
                                val ok = replyAction.send(context, replyText)
                                if (ok) {
                                    replySent = true
                                    replyError = false
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                } else {
                                    replyError = true
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        GloomIcon(GloomIcons.ChevronUp, Color.White, 16.dp)
                    }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (otpCode != null) {
                    AppleActivityButton(
                        text = if (copied) "Copied" else "Copy Code",
                        background = effectiveAccent,
                        foreground = Color.White,
                        modifier = Modifier.weight(1f),
                        onClick = { copyCode() }
                    )
                    AppleActivityButton(
                        text = appStyle.openActionLabel,
                        background = Color(0x24FFFFFF),
                        foreground = Color.White,
                        modifier = Modifier.weight(1f),
                        onClick = onOpen
                    )
                } else if (replyAction != null) {
                    if (isKeyguardLocked) {
                        AppleActivityButton(
                            text = "Unlock to Reply",
                            background = effectiveAccent.copy(alpha = 0.8f),
                            foreground = Color.White,
                            modifier = Modifier.weight(1.2f),
                            onClick = onOpen
                        )
                    } else {
                        AppleActivityButton(
                            text = appStyle.replyActionLabel,
                            background = effectiveAccent,
                            foreground = Color.White,
                            modifier = Modifier.weight(1.2f),
                            onClick = { isReplying = true }
                        )
                    }
                    AppleActivityButton(
                        text = appStyle.openActionLabel,
                        background = Color(0x24FFFFFF),
                        foreground = Color.White,
                        modifier = Modifier.weight(1.0f),
                        onClick = onOpen
                    )
                    AppleActivityButton(
                        text = "Dismiss",
                        background = Color(0x24FFFFFF),
                        foreground = Color.White,
                        modifier = Modifier.weight(0.8f),
                        onClick = onDismiss
                    )
                } else {
                    AppleActivityButton(
                        text = appStyle.openActionLabel,
                        background = effectiveAccent,
                        foreground = Color.White,
                        modifier = Modifier.weight(1.2f),
                        onClick = onOpen
                    )
                    AppleActivityButton(
                        text = "Dismiss",
                        background = Color(0x24FFFFFF),
                        foreground = Color.White,
                        modifier = Modifier.weight(0.8f),
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

/**
 * Expanded continuity card used when no Live Activity is active.
 */
@Composable
fun AppleContinuityActivity(
    device: BridgeDeviceInfo,
    batteryLevel: Int,
    isCharging: Boolean,
    accentColor: Color,
    secondaryColor: Color,
    onBeamFile: () -> Unit,
    onSyncClipboard: () -> Unit,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = AppleIslandSpec.contentMargin,
                end = AppleIslandSpec.contentMargin,
                top = 28.dp,
                bottom = 8.dp
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(AppleIslandSpec.artwork)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x18FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                GloomIcon(GloomIcons.Laptop, Color.White, 22.dp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (device.isConnected) "GLOOM Ecosystem" else "GLOOM Standby",
                    style = IslandType.title,
                    color = Color.White,
                    maxLines = 1
                )
                Text(
                    text = if (device.isConnected) "Live Continuity Active" else "Ready to Connect",
                    style = IslandType.subtitle,
                    color = Apple.secondaryLabel,
                    maxLines = 1
                )
            }
            ActivityCloseButton(onClose)
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0x10FFFFFF))
                .padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCharging) Apple.systemGreen
                            else if (device.isConnected) secondaryColor
                            else Apple.systemRed
                        )
                )
                Text(
                    text = if (isCharging) "Charging · $batteryLevel%"
                    else if (device.isConnected) "High-Speed Link" else "Local Wireless Standby",
                    style = IslandType.subtitle.copy(fontSize = 12.sp),
                    color = Apple.secondaryLabel,
                    maxLines = 1
                )
            }
            Text(
                text = if (device.isConnected) "Synced" else "Ready",
                style = IslandType.time,
                color = if (device.isConnected) Apple.systemGreen else Apple.tertiaryLabel
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ContinuityButton(
                icon = GloomIcons.Beam,
                label = "File Sharing",
                modifier = Modifier.weight(1f),
                onClick = onBeamFile
            )
            ContinuityButton(
                icon = GloomIcons.Clipboard,
                label = "Clipboard",
                modifier = Modifier.weight(1f),
                onClick = onSyncClipboard
            )
        }
    }
}

@Composable
private fun ContinuityButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x1AFFFFFF))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            GloomIcon(icon, Color.White, 14.dp)
            Text(
                text = label,
                style = IslandType.button.copy(fontSize = 11.5.sp),
                color = Color.White,
                maxLines = 1
            )
        }
    }
}

@Composable
fun AppleSeekBar(
    progress: Float,
    positionMs: Long,
    durationMs: Long,
    onSeek: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
        label = "seek-progress"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .pointerInput(onSeek) {
                    detectTapGestures { offset ->
                        onSeek?.invoke((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(onSeek) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            onSeek?.invoke((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                        },
                        onHorizontalDrag = { change, _ ->
                            onSeek?.invoke((change.position.x / size.width.toFloat()).coerceIn(0f, 1f))
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val midY = size.height / 2f
                val stroke = 4.dp.toPx()
                drawLine(
                    color = Color(0x29FFFFFF),
                    start = Offset(0f, midY),
                    end = Offset(size.width, midY),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                val x = size.width * animatedProgress
                if (x > 0f) {
                    drawLine(
                        color = Color.White,
                        start = Offset(0f, midY),
                        end = Offset(x, midY),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
        Spacer(Modifier.height(1.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatClock(positionMs),
                style = IslandType.time,
                color = Apple.tertiaryLabel
            )
            Text(
                text = if (durationMs > 0) "-${formatClock((durationMs - positionMs).coerceAtLeast(0))}" else "--:--",
                style = IslandType.time,
                color = Apple.tertiaryLabel
            )
        }
    }
}

@Composable
private fun ActivityTransportButton(
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .semanticsLabel(label),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun ActivityCloseButton(onClose: (() -> Unit)?) {
    if (onClose == null) return
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(Color(0x1FFFFFFF))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose
            ),
        contentAlignment = Alignment.Center
    ) {
        GloomIcon(GloomIcons.Close, Apple.secondaryLabel, 10.dp)
    }
}

@Composable
fun AppleActivityButton(
    text: String,
    background: Color,
    foreground: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = IslandType.button, color = foreground, maxLines = 1)
    }
}

private fun Modifier.semanticsLabel(label: String): Modifier =
    this.then(
        Modifier.semantics { contentDescription = label }
    )

private fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
}
