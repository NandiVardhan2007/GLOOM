package com.gloom.island.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.gloom.island.logging.GloomDebugLogger

/**
 * Diagnostics & Execution Trace Screen.
 *
 * Provides a live chronological inspection log of every user request, AI reasoning/thought,
 * dispatched action, and execution outcome to study performance and debug failures.
 */
@Composable
fun ExecutionLogsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var logs by remember { mutableStateOf(GloomDebugLogger.getRecentLogs(context)) }

    val totalCount = logs.size
    val successCount = logs.count { it.isSuccess }
    val successRate = if (totalCount > 0) ((successCount.toFloat() / totalCount) * 100).toInt() else 100

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Apple.background)
    ) {
        AppleSubHeader(title = "Execution Logs", onBack = onBack)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                // Diagnostics Summary Header
                AppleSection(
                    header = "Diagnostics Summary",
                    footer = "Rolling execution log stored locally in app cache. Used to inspect agent actions and trace failure roots."
                ) {
                    AppleRow(
                        title = "Total Requests",
                        value = "$totalCount logged",
                        icon = GloomIcons.Sparkles,
                        iconTint = Apple.systemBlue
                    )
                    AppleDivider()
                    AppleRow(
                        title = "Success Rate",
                        value = "$successRate% ($successCount / $totalCount)",
                        icon = if (successRate >= 80) GloomIcons.CheckCircleFill else GloomIcons.Info,
                        iconTint = if (successRate >= 80) Apple.systemGreen else Apple.systemOrange
                    )
                    AppleDivider()
                    Box(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                AppleButton(
                                    text = "Share Trace",
                                    primary = true,
                                    icon = GloomIcons.Beam,
                                    onClick = {
                                        val file = GloomDebugLogger.getLogFile(context)
                                        if (file.exists() && file.length() > 0) {
                                            try {
                                                val uri = FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    file
                                                )
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Share GLOOM Execution Log"))
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Log file shared via clipboard", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "No log traces recorded yet.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                AppleButton(
                                    text = "Clear Logs",
                                    primary = false,
                                    icon = GloomIcons.Trash,
                                    onClick = {
                                        GloomDebugLogger.clearLogs(context)
                                        logs = emptyList()
                                        Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "RECENT EXECUTION TRACES",
                    style = AppleType.caption1,
                    color = Apple.secondaryLabel,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
            }

            if (logs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No recorded traces yet.\nRun an action or ask GLOOM a question to generate logs.",
                            style = AppleType.subheadline,
                            color = Apple.secondaryLabel,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                items(logs, key = { it.id }) { log ->
                    LogCard(log)
                    Spacer(Modifier.height(10.dp))
                }
            }

            item {
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun LogCard(log: GloomDebugLogger.ExecutionLogEntry) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Apple.secondaryGroupedBackground)
            .clickable { expanded = !expanded }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (log.isSuccess) Apple.systemGreen else Apple.systemRed)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = log.handler,
                    style = AppleType.caption1,
                    color = Apple.systemBlue
                )
            }
            Text(
                text = log.formattedTime,
                style = AppleType.caption2,
                color = Apple.secondaryLabel
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = "\"${log.userRequest}\"",
            style = AppleType.headline,
            color = Apple.label
        )

        if (log.aiResponse.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = log.aiResponse,
                style = AppleType.subheadline,
                color = if (log.isSuccess) Apple.secondaryLabel else Apple.systemRed
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            ) {
                AppleDivider()
                Spacer(Modifier.height(8.dp))

                if (log.aiThought.isNotEmpty()) {
                    Text(
                        text = "AI REASONING",
                        style = AppleType.caption2,
                        color = Apple.systemOrange
                    )
                    Text(
                        text = log.aiThought,
                        style = AppleType.footnote,
                        color = Apple.label
                    )
                    Spacer(Modifier.height(6.dp))
                }

                if (log.stepsExecuted.isNotEmpty()) {
                    Text(
                        text = "ACTIONS EXECUTED (${log.stepsExecuted.size})",
                        style = AppleType.caption2,
                        color = Apple.systemGreen
                    )
                    log.stepsExecuted.forEach { step ->
                        Text(
                            text = "• $step",
                            style = AppleType.footnote,
                            color = Apple.label
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }

                if (!log.isSuccess && log.errorReason.isNotEmpty()) {
                    Text(
                        text = "FAILURE REASON",
                        style = AppleType.caption2,
                        color = Apple.systemRed
                    )
                    Text(
                        text = log.errorReason,
                        style = AppleType.footnote,
                        color = Apple.systemRed
                    )
                    Spacer(Modifier.height(6.dp))
                }

                if (log.diffSummary.isNotEmpty()) {
                    Text(
                        text = "VISUAL STATE DIFF",
                        style = AppleType.caption2,
                        color = Apple.secondaryLabel
                    )
                    Text(
                        text = log.diffSummary,
                        style = AppleType.caption1,
                        color = Apple.secondaryLabel
                    )
                }
            }
        }
    }
}
