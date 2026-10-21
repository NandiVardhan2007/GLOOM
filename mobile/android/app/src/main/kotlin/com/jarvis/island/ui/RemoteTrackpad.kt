package com.jarvis.island.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.island.bridge.JarvisLiveBridgeClient

/**
 * KDE Connect-inspired Remote Trackpad for controlling PC Cursor and Clicks over WiFi.
 */
@Composable
fun RemoteTrackpad(
    onClose: () -> Unit,
    bridgeClient: JarvisLiveBridgeClient? = JarvisLiveBridgeClient.activeClient
) {
    val haptic = LocalHapticFeedback.current
    var sensitivity by remember { mutableFloatStateOf(1.75f) }
    val isConnected = bridgeClient?.isConnected == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07070A))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Top Header ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClose()
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF14141E))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "PC Remote Mousepad",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isConnected) Color(0xFF30D158) else Color(0xFFFF453A))
                        )
                    }
                    Text(
                        text = if (isConnected) "Connected • ${bridgeClient?.desktopIp ?: "Desktop"}" else "Connecting to PC...",
                        color = Color(0xFF8E8E93),
                        fontSize = 11.5.sp
                    )
                }
            }

            // Sensitivity Switcher
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF14141E))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                listOf(1.0f to "1x", 1.75f to "2x", 2.5f to "3x").forEach { (speed, label) ->
                    val isSelected = sensitivity == speed
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (isSelected) Color(0xFF0A84FF) else Color.Transparent)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                sensitivity = speed
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.Black else Color(0xFF8E8E93),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // ── Main Touchpad Area & Scroll Strip ──
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Main Touchpad Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF101018), Color(0xFF0D0D14))
                        )
                    )
                    .border(1.dp, Color(0x2238BDF8), RoundedCornerShape(20.dp))
                    .pointerInput(sensitivity, isConnected) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            bridgeClient?.sendMouseInput(
                                action = "move",
                                dx = dragAmount.x * sensitivity,
                                dy = dragAmount.y * sensitivity
                            )
                        }
                    }
                    .pointerInput(isConnected) {
                        detectTapGestures(
                            onTap = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                bridgeClient?.sendMouseInput("click", 0f, 0f, "left")
                            },
                            onDoubleTap = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                bridgeClient?.sendMouseInput("double_click", 0f, 0f, "left")
                            },
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                bridgeClient?.sendMouseInput("click", 0f, 0f, "right")
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mouse,
                        contentDescription = "Trackpad",
                        tint = Color(0x3338BDF8),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Touchpad Surface",
                        color = Color(0x88FFFFFF),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Drag to move • Tap to left-click\nHold for right-click • Double-tap to open",
                        color = Color(0x44FFFFFF),
                        fontSize = 11.5.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            }

            // Scroll Strip (Wheel simulation)
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF101018))
                    .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(20.dp))
                    .pointerInput(isConnected) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            // Scroll delta
                            val wheelDelta = -dragAmount.y * 0.35f
                            bridgeClient?.sendMouseInput(
                                action = "scroll",
                                dx = 0f,
                                dy = wheelDelta,
                                button = "middle"
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = "Scroll Up",
                        tint = Color(0x66FFFFFF),
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        repeat(5) {
                            Box(
                                modifier = Modifier
                                    .width(16.dp)
                                    .height(2.dp)
                                    .background(Color(0x33FFFFFF), RoundedCornerShape(1.dp))
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Scroll Down",
                        tint = Color(0x66FFFFFF),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }
        }

        // ── Tactile Physical Click Buttons (Left / Middle / Right) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Left Click (Primary)
            Box(
                modifier = Modifier
                    .weight(1.4f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF161622))
                    .border(1.dp, Color(0x2238BDF8), RoundedCornerShape(16.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        bridgeClient?.sendMouseInput("click", 0f, 0f, "left")
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Left Click",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Middle Click (Scroll wheel press)
            Box(
                modifier = Modifier
                    .weight(0.8f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF12121C))
                    .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(16.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        bridgeClient?.sendMouseInput("click", 0f, 0f, "middle")
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Middle",
                    color = Color(0xFF8E8E93),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Right Click (Context Menu)
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF161622))
                    .border(1.dp, Color(0x2238BDF8), RoundedCornerShape(16.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        bridgeClient?.sendMouseInput("click", 0f, 0f, "right")
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Right Click",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
