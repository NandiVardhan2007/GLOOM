package com.jarvis.island.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.island.automation.AutomationSelfCheck
import com.jarvis.island.settings.JarvisIslandSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

private data class LaunchableApp(val label: String, val packageName: String)

/**
 * Notification rules: which alerts may enter the island, and how they are
 * triaged before anything is shown.
 */
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { JarvisIslandSettings.instance }
    val scrollState = rememberScrollState()
    val blocked = settings.blockedPackages

    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
    }

    val filtered = remember(query, apps, blocked) {
        val base = apps.toMutableList()
        // Keep muted packages visible even when they have no launcher icon.
        blocked.forEach { packageName ->
            if (base.none { it.packageName == packageName }) {
                base.add(LaunchableApp(packageName.substringAfterLast('.'), packageName))
            }
        }
        val needle = query.trim().lowercase()
        base
            .filter {
                needle.isEmpty() ||
                    it.label.lowercase().contains(needle) ||
                    it.packageName.lowercase().contains(needle)
            }
            .sortedBy { it.label.lowercase() }
            .take(80)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        AppleSubHeader(title = "Notifications", onBack = onBack)

        AppleSection(
            header = "Rules",
            footer = "Silent channels and promotional alerts are kept in the shade. One-time codes are always shown and can be copied from the island."
        ) {
            AppleSwitchRow(
                title = "Filter silent alerts",
                description = "Quiet channel updates stay in the shade.",
                checked = settings.filterSilentNotifications,
                onCheckedChange = {
                    settings.filterSilentNotifications = it
                    settings.save(context)
                }
            )
            AppleDivider()
            AppleSwitchRow(
                title = "Triage alerts",
                description = "Ignore marketing, keep messages and codes.",
                checked = settings.notificationTriageEnabled,
                onCheckedChange = {
                    settings.notificationTriageEnabled = it
                    settings.save(context)
                }
            )
            AppleDivider()
            AppleSwitchRow(
                title = "One-tap code copy",
                description = "Show a Copy Code button for verification codes.",
                checked = settings.otpCopyEnabled,
                onCheckedChange = {
                    settings.otpCopyEnabled = it
                    settings.save(context)
                }
            )
            AppleDivider()
            AppleRow(
                title = "Notification access",
                subtitle = if (hasNotificationAccess(context)) "Connected" else "Required to mirror alerts",
                icon = JarvisIcons.Bell,
                iconTint = if (hasNotificationAccess(context)) Apple.systemGreen else Apple.systemOrange,
                showChevron = !hasNotificationAccess(context),
                onClick = if (!hasNotificationAccess(context)) ({
                    context.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }) else null
            )
        }

        AppleSection(
            header = "Apps",
            footer = "Turning an app off keeps its alerts in the shade. Everything else continues to appear in the island."
        ) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search apps", color = Apple.tertiaryLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {}),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (apps.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Reading installed apps…",
                        style = AppleType.footnote,
                        color = Apple.secondaryLabel
                    )
                }
            } else if (filtered.isEmpty()) {
                AppleEmptyState(
                    icon = JarvisIcons.Apps,
                    title = "No matching apps",
                    message = "Try a different name or package."
                )
            } else {
                filtered.forEachIndexed { index, app ->
                    if (index > 0) AppleDivider()
                    AppToggleRow(
                        label = app.label,
                        packageName = app.packageName,
                        checked = !blocked.contains(app.packageName),
                        onCheckedChange = { allowed ->
                            settings.setPackageAllowed(context, app.packageName, allowed)
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

private fun loadLaunchableApps(context: Context): List<LaunchableApp> {
    val manager = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolved = try {
        manager.queryIntentActivities(intent, 0)
    } catch (e: Exception) {
        emptyList()
    }
    return resolved.mapNotNull { info ->
        val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
        if (packageName == context.packageName) return@mapNotNull null
        val label = try {
            info.loadLabel(manager).toString()
        } catch (e: Exception) {
            packageName
        }
        LaunchableApp(label, packageName)
    }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

private fun hasNotificationAccess(context: Context): Boolean {
    val manager = context.getSystemService(android.app.NotificationManager::class.java) ?: return false
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
        manager.isNotificationListenerAccessGranted(
            android.content.ComponentName(
                context,
                com.jarvis.island.service.JarvisNotificationListener::class.java
            )
        )
    } else {
        val enabled = android.provider.Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        enabled != null && enabled.contains(context.packageName)
    }
}

@Composable
private fun AppToggleRow(
    label: String,
    packageName: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(29.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(monogramColor(packageName)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label.take(1).uppercase(),
                style = AppleType.footnote.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = AppleType.body,
                color = Apple.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                packageName,
                style = AppleType.caption2,
                color = Apple.tertiaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))
        AppleSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun monogramColor(packageName: String): Color {
    val palette = listOf(
        Apple.systemBlue,
        Apple.systemIndigo,
        Apple.systemPurple,
        Apple.systemPink,
        Apple.systemRed,
        Apple.systemOrange,
        Apple.systemYellow,
        Apple.systemGreen,
        Apple.systemTeal,
        Apple.systemGray,
    )
    return palette[(packageName.hashCode() and Int.MAX_VALUE) % palette.size]
}
