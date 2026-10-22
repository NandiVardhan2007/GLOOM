package com.jarvis.island.ui

import android.Manifest
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.jarvis.island.automation.AutomationSelfCheck
import com.jarvis.island.automation.Notebook
import com.jarvis.island.automation.ReminderScheduler
import com.jarvis.island.automation.SceneStore
import com.jarvis.island.automation.TimeParser
import com.jarvis.island.settings.JarvisIslandSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Assistant settings: which brain answers, whether automation is ready, and the
 * scenes, reminders and notes that live on the phone.
 */
@Composable
fun AutomationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val settings = remember { JarvisIslandSettings.instance }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var refreshTick by remember { mutableIntStateOf(0) }
    var checks by remember { mutableStateOf(AutomationSelfCheck.run(context)) }
    var showSceneDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var pendingDeleteNote by remember { mutableStateOf<Notebook.Note?>(null) }

    val scenes = remember(refreshTick) { SceneStore.scenes(context) }
    val notes = remember(refreshTick) { Notebook.notes(context) }
    val reminders = remember(refreshTick) { Notebook.pendingReminders(context) }

    val contactLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshTick += 1 }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checks = AutomationSelfCheck.run(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun fix(check: AutomationSelfCheck.Check) {
        when (check.fix) {
            AutomationSelfCheck.Fix.ACCESSIBILITY ->
                context.startActivity(android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            AutomationSelfCheck.Fix.NOTIFICATION_ACCESS ->
                context.startActivity(android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            AutomationSelfCheck.Fix.OVERLAY ->
                context.startActivity(
                    android.content.Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            AutomationSelfCheck.Fix.CONTACTS ->
                contactLauncher.launch(Manifest.permission.READ_CONTACTS)
            AutomationSelfCheck.Fix.EXACT_ALARM ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    context.startActivity(
                        android.content.Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }
            AutomationSelfCheck.Fix.BATTERY ->
                context.startActivity(
                    android.content.Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            AutomationSelfCheck.Fix.PAIR ->
                Toast.makeText(context, "Open Home to pair with your desktop.", Toast.LENGTH_SHORT).show()
            AutomationSelfCheck.Fix.NONE -> Unit
        }
    }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        AppleSubHeader(title = "Assistant", onBack = onBack)

        AppleSection(
            header = "Brain",
            footer = "Auto uses the phone first and asks your desktop only when it needs to. On-device never sends a request off the phone."
        ) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                val options = listOf("Auto", "On-device", "Desktop")
                val selected = when (settings.aiBrain) {
                    "local" -> 1
                    "remote" -> 2
                    else -> 0
                }
                AppleSegmentedControl(
                    options = options,
                    selectedIndex = selected,
                    onSelect = { index ->
                        settings.aiBrain = when (index) {
                            1 -> "local"
                            2 -> "remote"
                            else -> "auto"
                        }
                        settings.save(context)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )
            }
        }

        AppleSection(
            header = "Agent Execution Profile",
            footer = "Flash executes immediate reactive actions with minimal latency. Pro performs deep multi-step planning, screen-diff verification, and autonomous self-correction."
        ) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                val profileOptions = listOf("Flash (Fast)", "Pro (Verify & Correct)")
                val selectedProfile = if (settings.plannerProfile == "pro") 1 else 0
                AppleSegmentedControl(
                    options = profileOptions,
                    selectedIndex = selectedProfile,
                    onSelect = { index ->
                        settings.plannerProfile = if (index == 1) "pro" else "flash"
                        settings.save(context)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )
            }
        }

        AppleSection(
            header = "Readiness",
            footer = "Checked on this device. Nothing here is sent anywhere."
        ) {
            checks.forEachIndexed { index, check ->
                if (index > 0) AppleDivider()
                AppleRow(
                    title = check.title,
                    subtitle = check.detail,
                    icon = if (check.ok) JarvisIcons.CheckCircleFill else JarvisIcons.XCircleFill,
                    iconTint = if (check.ok) Apple.systemGreen else Apple.systemOrange,
                    showChevron = !check.ok && check.fix != AutomationSelfCheck.Fix.NONE,
                    onClick = if (!check.ok) ({ fix(check) }) else null
                )
            }
            AppleDivider()
            AppleRow(
                title = "Check again",
                icon = JarvisIcons.ArrowClockwise,
                iconTint = Apple.systemBlue,
                showChevron = true,
                onClick = {
                    checks = AutomationSelfCheck.run(context)
                    refreshTick += 1
                }
            )
        }

        AppleSection(
            header = "Scenes",
            footer = "A scene is a saved sequence of commands. The first step that fails stops the scene so it never acts on the wrong screen."
        ) {
            if (scenes.isEmpty()) {
                AppleEmptyState(
                    icon = JarvisIcons.Grid,
                    title = "No scenes yet",
                    message = "Save a routine like \"open WhatsApp; tap Search; type Alex\" and run it with one phrase."
                )
            } else {
                scenes.forEachIndexed { index, scene ->
                    if (index > 0) AppleDivider()
                    SceneRow(
                        name = scene.name,
                        detail = "${scene.steps.size} ${if (scene.steps.size == 1) "step" else "steps"} · ${scene.steps.first()}",
                        onRun = {
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    SceneStore.run(context, scene)
                                }
                                toast(result.message)
                                refreshTick += 1
                            }
                        },
                        onDelete = {
                            SceneStore.delete(context, scene.id)
                            refreshTick += 1
                            toast("Deleted ${scene.name}.")
                        }
                    )
                }
            }
            AppleDivider()
            AppleRow(
                title = "New Scene",
                icon = JarvisIcons.Plus,
                iconTint = Apple.systemBlue,
                showChevron = true,
                onClick = { showSceneDialog = true }
            )
        }

        AppleSection(
            header = "Reminders",
            footer = "Reminders are scheduled with the system alarm clock and survive a restart."
        ) {
            if (reminders.isEmpty()) {
                AppleEmptyState(
                    icon = JarvisIcons.Alarm,
                    title = "No reminders",
                    message = "Say \"Hey JARVIS, remind me to call mom at seven\" or add one here."
                )
            } else {
                reminders.take(6).forEachIndexed { index, reminder ->
                    if (index > 0) AppleDivider()
                    AppleRow(
                        title = reminder.text,
                        subtitle = TimeParser.format(reminder.triggerAtMs),
                        icon = JarvisIcons.Alarm,
                        iconTint = Apple.systemOrange,
                        trailingIcon = JarvisIcons.CheckCircle,
                        trailingTint = Apple.systemGreen,
                        onClick = {
                            ReminderScheduler.cancel(context, reminder.id)
                            Notebook.completeReminder(context, reminder.id)
                            refreshTick += 1
                            toast("Marked as done.")
                        }
                    )
                }
            }
            AppleDivider()
            AppleRow(
                title = "New Reminder",
                icon = JarvisIcons.Plus,
                iconTint = Apple.systemOrange,
                showChevron = true,
                onClick = { showReminderDialog = true }
            )
        }

        AppleSection(
            header = "Notebook",
            footer = "Notes stay on this phone. Tap a note to delete it."
        ) {
            if (notes.isEmpty()) {
                AppleEmptyState(
                    icon = JarvisIcons.Note,
                    title = "No notes",
                    message = "Say \"remember the parking spot is level three\" and it lands here."
                )
            } else {
                notes.take(8).forEachIndexed { index, note ->
                    if (index > 0) AppleDivider()
                    NoteRow(
                        body = note.body,
                        date = TimeParser.format(note.createdAtMs),
                        onDelete = { pendingDeleteNote = note }
                    )
                }
            }
            AppleDivider()
            AppleRow(
                title = "New Note",
                icon = JarvisIcons.Plus,
                iconTint = Apple.systemYellow,
                showChevron = true,
                onClick = { showNoteDialog = true }
            )
        }

        AppleSection(
            header = "Safety",
            footer = "The safety net refuses payments, purchases and irreversible actions before any gesture is sent."
        ) {
            AppleSwitchRow(
                title = "Block risky actions",
                description = "Refuse financial and irreversible controls.",
                checked = settings.automationSafetyEnabled,
                onCheckedChange = {
                    settings.automationSafetyEnabled = it
                    settings.save(context)
                }
            )
            AppleDivider()
            AppleSwitchRow(
                title = "Verify every tap",
                description = "Check that the screen changed and retry once.",
                checked = settings.verifyActionsEnabled,
                onCheckedChange = {
                    settings.verifyActionsEnabled = it
                    settings.save(context)
                }
            )
        }

        Spacer(Modifier.height(28.dp))
    }

    if (showSceneDialog) {
        SceneDialog(
            onDismiss = { showSceneDialog = false },
            onSave = { name, steps ->
                SceneStore.save(context, name, steps)
                showSceneDialog = false
                refreshTick += 1
                toast("Saved $name.")
            }
        )
    }

    if (showNoteDialog) {
        TextEntryDialog(
            title = "New Note",
            label = "Note",
            placeholder = "Parking spot is level three",
            onDismiss = { showNoteDialog = false },
            onSave = { body ->
                Notebook.addNote(context, body)
                showNoteDialog = false
                refreshTick += 1
                toast("Noted.")
            }
        )
    }

    if (showReminderDialog) {
        ReminderDialog(
            onDismiss = { showReminderDialog = false },
            onSave = { what, whenText ->
                val atMs = TimeParser.parse(whenText)
                if (atMs == null) {
                    toast("I couldn't read that time. Try \"7 pm\" or \"in 20 minutes\".")
                } else {
                    val reminder = Notebook.addReminder(context, what, atMs)
                    val scheduled = ReminderScheduler.schedule(context, reminder)
                    showReminderDialog = false
                    refreshTick += 1
                    toast(
                        if (scheduled) "Reminder set for ${TimeParser.format(atMs)}."
                        else "Saved, but exact alarms are restricted."
                    )
                }
            }
        )
    }

    pendingDeleteNote?.let { note ->
        AlertDialog(
            onDismissRequest = { pendingDeleteNote = null },
            containerColor = Apple.secondaryGroupedBackground,
            titleContentColor = Apple.label,
            textContentColor = Apple.secondaryLabel,
            title = { Text("Delete this note?", style = AppleType.headline, color = Apple.label) },
            text = {
                Text(
                    note.body,
                    style = AppleType.subheadline,
                    color = Apple.secondaryLabel,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Notebook.deleteNote(context, note.id)
                    pendingDeleteNote = null
                    refreshTick += 1
                }) { Text("Delete", color = Apple.systemRed) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteNote = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SceneRow(
    name: String,
    detail: String,
    onRun: () -> Unit,
    onDelete: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppleIconTile(icon = JarvisIcons.Grid, tint = Apple.systemIndigo)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = AppleType.body, color = Apple.label, maxLines = 1)
            Text(
                detail,
                style = AppleType.footnote,
                color = Apple.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Apple.systemBlue.copy(alpha = 0.16f))
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onRun()
                },
            contentAlignment = Alignment.Center
        ) {
            JarvisIcon(JarvisIcons.PlayFill, Apple.systemBlue, 16.dp)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable { onDelete() },
            contentAlignment = Alignment.Center
        ) {
            JarvisIcon(JarvisIcons.Trash, Apple.systemRed, 18.dp)
        }
    }
}

@Composable
private fun NoteRow(body: String, date: String, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDelete() }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppleIconTile(icon = JarvisIcons.Note, tint = Apple.systemYellow.copy(alpha = 0.92f))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                body,
                style = AppleType.body,
                color = Apple.label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(date, style = AppleType.caption1, color = Apple.secondaryLabel)
        }
        JarvisIcon(JarvisIcons.Trash, Apple.tertiaryLabel, 16.dp)
    }
}

@Composable
private fun SceneDialog(
    onDismiss: () -> Unit,
    onSave: (String, List<String>) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf("") }
    val parsedSteps = steps
        .split(Regex("""\s*;\s*|\n"""))
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Apple.secondaryGroupedBackground,
        titleContentColor = Apple.label,
        textContentColor = Apple.secondaryLabel,
        title = { Text("New Scene", style = AppleType.headline, color = Apple.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = steps,
                    onValueChange = { steps = it },
                    label = { Text("Steps") },
                    placeholder = { Text("open WhatsApp; tap Search; type Alex") },
                    minLines = 3,
                    maxLines = 5
                )
                Text(
                    "Separate steps with a semicolon.",
                    style = AppleType.caption1,
                    color = Apple.secondaryLabel
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && parsedSteps.isNotEmpty(),
                onClick = { onSave(name.trim(), parsedSteps) }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun TextEntryDialog(
    title: String,
    label: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Apple.secondaryGroupedBackground,
        titleContentColor = Apple.label,
        textContentColor = Apple.secondaryLabel,
        title = { Text(title, style = AppleType.headline, color = Apple.label) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                placeholder = { Text(placeholder) },
                minLines = 3,
                maxLines = 5
            )
        },
        confirmButton = {
            TextButton(
                enabled = value.isNotBlank(),
                onClick = { onSave(value.trim()) }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ReminderDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var what by remember { mutableStateOf("") }
    var whenText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Apple.secondaryGroupedBackground,
        titleContentColor = Apple.label,
        textContentColor = Apple.secondaryLabel,
        title = { Text("New Reminder", style = AppleType.headline, color = Apple.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = what,
                    onValueChange = { what = it },
                    label = { Text("Remind me to") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = whenText,
                    onValueChange = { whenText = it },
                    label = { Text("When") },
                    placeholder = { Text("7 pm, tomorrow at 9, in 20 minutes") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = what.isNotBlank() && whenText.isNotBlank(),
                onClick = { onSave(what.trim(), whenText.trim()) }
            ) { Text("Set") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
