package com.gloom.island.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
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
import com.gloom.island.settings.GloomIslandSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

data class InstalledAppItem(val label: String, val packageName: String)

/**
 * Notification rules: which alerts may enter the island, and how they are
 * triaged before anything is shown. Supports all installed applications on the device.
 */
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { GloomIslandSettings.instance }
    val scrollState = rememberScrollState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val blocked = settings.blockedPackages

    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) { loadAllInstalledApps(context) }
        apps = loaded
        isLoading = false
    }

    val filtered = remember(query, apps, blocked) {
        val base = apps.toMutableList()
        // Ensure all blocked packages appear even if not yet enumerated
        blocked.forEach { packageName ->
            if (base.none { it.packageName == packageName }) {
                base.add(InstalledAppItem(packageName.substringAfterLast('.'), packageName))
            }
        }
        val needle = query.trim().lowercase()
        val list = if (needle.isEmpty()) {
            base
        } else {
            base.filter {
                it.label.lowercase().contains(needle) ||
                    it.packageName.lowercase().contains(needle)
            }
        }
        list.sortedBy { it.label.lowercase() }
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
                title = "Exclusive Island Notifications",
                description = "Suppresses Android's background heads-up popups so alerts appear ONLY in the Dynamic Island.",
                checked = settings.suppressNativeHeadsUp,
                onCheckedChange = {
                    settings.suppressNativeHeadsUp = it
                    settings.save(context)
                    scope.launch {
                        com.gloom.island.root.RootBridge.setNativeHeadsUpSuppressed(it)
                    }
                }
            )
            AppleDivider()
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
            AppleSwitchRow(
                title = "Swipe-to-Dismiss Cards",
                description = "Fling expanded notification cards horizontally to dismiss and sync with system shade.",
                checked = settings.notificationSwipeDismissEnabled,
                onCheckedChange = {
                    settings.notificationSwipeDismissEnabled = it
                    settings.save(context)
                }
            )
            AppleDivider()
            AppleRow(
                title = "Notification access",
                subtitle = if (hasNotificationAccess(context)) "Connected" else "Required to mirror alerts",
                icon = GloomIcons.Bell,
                iconTint = if (hasNotificationAccess(context)) Apple.systemGreen else Apple.systemOrange,
                showChevron = !hasNotificationAccess(context),
                onClick = if (!hasNotificationAccess(context)) ({
                    context.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }) else null
            )
        }

        AppleSection(
            header = "Apps (${if (isLoading) "Loading…" else "${filtered.size} available"})",
            footer = "Turning an app off keeps its alerts in the shade. Everything else continues to appear in the island."
        ) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search all installed apps", color = Apple.tertiaryLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {}),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Reading all installed packages…",
                        style = AppleType.footnote,
                        color = Apple.secondaryLabel
                    )
                }
            } else if (filtered.isEmpty()) {
                AppleEmptyState(
                    icon = GloomIcons.Apps,
                    title = "No matching apps",
                    message = "Try a different name or package."
                )
            } else {
                // Show up to 150 matching apps in the scroll list
                val displayList = filtered.take(150)
                displayList.forEachIndexed { index, app ->
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
                if (filtered.size > 150) {
                    AppleDivider()
                    Text(
                        text = "+ ${filtered.size - 150} more apps (refine search to see specific apps)",
                        style = AppleType.footnote,
                        color = Apple.secondaryLabel,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

private fun loadAllInstalledApps(context: Context): List<InstalledAppItem> {
    val manager = context.packageManager
    val packages = try {
        manager.getInstalledPackages(0)
    } catch (e: Exception) {
        emptyList()
    }
    return packages.mapNotNull { pkgInfo ->
        val packageName = pkgInfo.packageName ?: return@mapNotNull null
        if (packageName == context.packageName) return@mapNotNull null
        val label = try {
            pkgInfo.applicationInfo?.loadLabel(manager)?.toString() ?: packageName
        } catch (e: Exception) {
            packageName
        }
        InstalledAppItem(label = label.ifBlank { packageName }, packageName = packageName)
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
                com.gloom.island.service.GloomNotificationListener::class.java
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
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(appTileColor(packageName)),
            contentAlignment = Alignment.Center
        ) {
            val glyph = label.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString()
                ?: packageName.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString()
                ?: "•"
            Text(
                text = glyph,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = AppleType.body,
                color = Apple.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = packageName,
                style = AppleType.caption2,
                color = Apple.tertiaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        AppleSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun appTileColor(packageName: String): Color {
    val hash = packageName.hashCode().absoluteValue
    val hues = listOf(
        Color(0xFF007AFF), // Blue
        Color(0xFF34C759), // Green
        Color(0xFFFF9500), // Orange
        Color(0xFFFF2D55), // Pink
        Color(0xFF5856D6), // Purple
        Color(0xFF30B0C7), // Teal
        Color(0xFFFF3B30), // Red
        Color(0xFF64D2FF), // Cyan
    )
    return hues[hash % hues.size]
}
