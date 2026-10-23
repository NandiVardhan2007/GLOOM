package com.gloom.island.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** iOS system colors, dark appearance (HIG semantic palette). */
object Apple {
    val background = Color(0xFF000000)
    val groupedBackground = Color(0xFF000000)
    val secondaryGroupedBackground = Color(0xFF1C1C1E)
    val tertiaryGroupedBackground = Color(0xFF2C2C2E)
    val quaternary = Color(0xFF3A3A3C)
    val separator = Color(0x5C545458)
    val opaqueSeparator = Color(0xFF38383A)

    val label = Color(0xFFFFFFFF)
    val secondaryLabel = Color(0x99EBEBF5)
    val tertiaryLabel = Color(0x4DEBEBF5)

    val systemBlue = Color(0xFF0A84FF)
    val systemGreen = Color(0xFF30D158)
    val systemRed = Color(0xFFFF453A)
    val systemOrange = Color(0xFFFF9F0A)
    val systemYellow = Color(0xFFFFD60A)
    val systemTeal = Color(0xFF64D2FF)
    val systemIndigo = Color(0xFF5E5CE6)
    val systemPink = Color(0xFFFF375F)
    val systemPurple = Color(0xFFBF5AF2)
    val systemGray = Color(0xFF8E8E93)
    val systemGray2 = Color(0xFF636366)

    val fillSecondary = Color(0x5C787880)
    val fillTertiary = Color(0x3D767680)

    val tabBarBackground = Color(0xF21C1C1E)
}

/** iOS type ramp. The platform font is used, as the HIG recommends. */
object AppleType {
    private val base = TextStyle(fontFamily = FontFamily.Default)

    val largeTitle = base.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, lineHeight = 41.sp)
    val title1 = base.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, lineHeight = 34.sp)
    val title2 = base.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp, lineHeight = 28.sp)
    val title3 = base.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp, lineHeight = 25.sp)
    val headline = base.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
    val body = base.copy(fontSize = 17.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp)
    val callout = base.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 21.sp)
    val subheadline = base.copy(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp)
    val footnote = base.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp)
    val caption1 = base.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp)
    val caption2 = base.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal, lineHeight = 13.sp)
    val tabLabel = base.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium, lineHeight = 12.sp)

    val sectionHeader = base.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.4.sp)
    val sectionFooter = base.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp)
}

private val sectionShape = RoundedCornerShape(12.dp)
private val springSpec = spring<Float>(dampingRatio = 1f, stiffness = 320f)

@Composable
fun AppleLargeTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = AppleType.largeTitle,
        color = Apple.label,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
fun AppleSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (header != null) {
            Text(
                text = header.uppercase(),
                style = AppleType.sectionHeader,
                color = Apple.secondaryLabel,
                modifier = Modifier.padding(start = 36.dp, end = 20.dp, top = 24.dp, bottom = 7.dp)
            )
        } else {
            Spacer(Modifier.height(24.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(sectionShape)
                .background(Apple.secondaryGroupedBackground)
        ) {
            content()
        }
        if (footer != null) {
            Text(
                text = footer,
                style = AppleType.sectionFooter,
                color = Apple.secondaryLabel,
                modifier = Modifier.padding(start = 36.dp, end = 36.dp, top = 7.dp)
            )
        }
    }
}

@Composable
fun AppleDivider(insetStart: Dp = 60.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = insetStart)
            .height(0.5.dp)
            .background(Apple.separator)
    )
}

@Composable
fun AppleIconTile(
    icon: ImageVector,
    tint: Color,
    size: Dp = 29.dp,
    iconSize: Dp = 17.dp,
    backgroundOverride: Color? = null,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f))
            .background(backgroundOverride ?: tint),
        contentAlignment = Alignment.Center
    ) {
        GloomIcon(icon = icon, tint = Color.White, size = iconSize)
    }
}

@Composable
fun GloomIcon(icon: ImageVector, tint: Color, size: Dp) {
    androidx.compose.material3.Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(size)
    )
}

@Composable
fun AppleRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = Apple.systemBlue,
    subtitle: String? = null,
    value: String? = null,
    showChevron: Boolean = false,
    trailingIcon: ImageVector? = null,
    trailingTint: Color = Apple.secondaryLabel,
    destructive: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                } else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) {
            AppleIconTile(icon = icon, tint = iconTint)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppleType.body,
                color = if (destructive) Apple.systemRed else Apple.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = AppleType.footnote,
                    color = Apple.secondaryLabel,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (value != null) {
            Text(
                text = value,
                style = AppleType.body,
                color = Apple.secondaryLabel,
                maxLines = 1
            )
        }
        if (trailingIcon != null) {
            GloomIcon(icon = trailingIcon, tint = trailingTint, size = 20.dp)
        }
        if (showChevron) {
            GloomIcon(
                icon = GloomIcons.ChevronRight,
                tint = Apple.tertiaryLabel,
                size = 14.dp
            )
        }
    }
}

@Composable
fun AppleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val trackColor by animateColorAsState(
        targetValue = if (checked) Apple.systemGreen else Apple.quaternary,
        animationSpec = spring(dampingRatio = 1f, stiffness = 380f),
        label = "switchTrack"
    )
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 23.dp else 2.dp,
        animationSpec = spring(dampingRatio = 1f, stiffness = 420f),
        label = "switchKnob"
    )
    Box(
        modifier = modifier
            .size(width = 51.dp, height = 31.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(trackColor)
            .clickable(enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange(!checked)
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(start = knobOffset)
                .size(27.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
        )
    }
}

@Composable
fun AppleSwitchRow(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = AppleType.body, color = Apple.label)
            if (description != null) {
                Text(
                    description,
                    style = AppleType.footnote,
                    color = Apple.secondaryLabel,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        AppleSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun AppleSliderRow(
    title: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = AppleType.body, color = Apple.label)
            Text(valueText, style = AppleType.body, color = Apple.secondaryLabel)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Apple.systemBlue,
                inactiveTrackColor = Apple.fillSecondary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .padding(top = 4.dp)
        )
    }
}

@Composable
fun AppleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    icon: ImageVector? = null,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary) Apple.systemBlue else Apple.secondaryGroupedBackground)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            GloomIcon(icon, if (primary) Color.White else Apple.systemBlue, 18.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = AppleType.headline,
            color = if (primary) Color.White else Apple.systemBlue
        )
    }
}

@Composable
fun AppleSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Apple.fillTertiary)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (selected) Apple.quaternary else Color.Transparent)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = AppleType.footnote.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (selected) Apple.label else Apple.secondaryLabel
                )
            }
        }
    }
}

@Composable
fun AppleSubHeader(
    title: String,
    onBack: () -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onBack)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Icon(
                imageVector = GloomIcons.ChevronRight,
                contentDescription = "Back",
                tint = Apple.systemBlue,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(180f)
            )
            Spacer(Modifier.width(5.dp))
            Text("Back", style = AppleType.body, color = Apple.systemBlue)
        }
        Text(
            text = title,
            style = AppleType.headline,
            color = Apple.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 92.dp)
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = AppleType.body,
                color = Apple.systemBlue,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onAction() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun AppleEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GloomIcon(icon, Apple.tertiaryLabel, 40.dp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = AppleType.headline, color = Apple.label)
        Spacer(Modifier.height(4.dp))
        Text(
            message,
            style = AppleType.subheadline,
            color = Apple.secondaryLabel,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

data class AppleTab(val id: String, val label: String, val icon: ImageVector)

@Composable
fun AppleTabBar(
    tabs: List<AppleTab>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(Apple.tabBarBackground)
            .navigationBarsPadding()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Apple.separator)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 7.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tabs.forEach { tab ->
                val selected = tab.id == selectedId
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(tab.id)
                        }
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GloomIcon(
                        icon = tab.icon,
                        tint = if (selected) Apple.systemBlue else Apple.secondaryLabel,
                        size = 24.dp
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = tab.label,
                        style = AppleType.tabLabel,
                        color = if (selected) Apple.systemBlue else Apple.secondaryLabel
                    )
                }
            }
        }
    }
}
