package com.hector.workouttracker.feature.help

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hector.workouttracker.core.designsystem.*
import com.hector.workouttracker.core.designsystem.TrackerIcons
import com.hector.workouttracker.domain.*

internal val LocalHelp = staticCompositionLocalOf<HelpViewModel?> { null }

/** Inline, user-opened help never takes focus or opens over a field/picker. */
@Composable
internal fun HelpTip(id: String) {
    val vm = LocalHelp.current ?: return
    val state by vm.state.collectAsStateWithLifecycle()
    if (state.loading || !guidanceVisible(state.progress, id)) return
    val topic = guidanceTopics.first { it.id == id }
    var expanded by rememberSaveable(id) { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().testTag("help-tip-$id"),
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    TrackerIcons.Help,
                    null,
                    Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Tip · ${topic.title}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) Text(topic.text, style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.testTag("read-tip-$id"),
                ) {
                    Text(if (expanded) "Close tip" else "Read tip")
                }
                TextButton(
                    onClick = { vm.complete(id) },
                    enabled = !state.busy,
                    modifier = Modifier.testTag("dismiss-tip-$id"),
                ) {
                    Text("Got it")
                }
            }
            // A failed optional write is honest, without blocking the session or opening a dialog.
            state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
internal fun HelpScreen(back: () -> Unit) {
    val vm = LocalHelp.current ?: return
    val state by vm.state.collectAsStateWithLifecycle()
    var reset by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().background(trackerScreenBackground()).testTag("help-list"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TextButton(onClick = back) { Text("Back to settings") }
            Text("Help & guidance", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Works offline. Open a topic when you need it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            (state.error ?: state.message)?.let { Text(it, Modifier.testTag("help-message")) }
        }
        items(guidanceTopics, key = { it.id }) { topic ->
            var expanded by rememberSaveable(topic.id) { mutableStateOf(false) }
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = trackerPanelColor()),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { expanded = !expanded },
                        modifier =
                            Modifier.fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("help-read-${topic.id}")
                                .semantics {
                                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                                },
                        colors =
                            ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                    ) {
                        Text(
                            topic.title,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Icon(
                            TrackerIcons.Chevron,
                            null,
                            Modifier.rotate(if (expanded) 90f else 0f).size(20.dp),
                        )
                    }
                    if (expanded) {
                        Text(topic.text)
                        TextButton(
                            onClick = { vm.replay(topic.id) },
                            enabled =
                                !state.busy && !state.loading && guidanceEditable(state.progress),
                            modifier = Modifier.testTag("help-replay-${topic.id}"),
                        ) {
                            Text("Show this tip again")
                        }
                    }
                }
            }
        }
        item {
            Text("Backups & moving from the PWA", style = MaterialTheme.typography.titleMedium)
            Text(
                "Your PWA and Android data are separate. Finish and save a PWA workout before exporting it. In You, choose Import PWA backup, review changes and confirm. This copies saved records; it does not synchronize the apps. Keep the PWA and its backup until you have checked your imported data."
            )
            Text(
                "Export a JSON backup from You and keep it somewhere you can find again. Normal backups exclude active workouts and guidance preferences. A successful export means the destination accepted the file; a cloud provider may still need to sync it. Reimporting a backup reviews matching IDs before replacing them."
            )
            Text(
                "The reminder uses the last recorded export time, including imported metadata. It does not verify that an older file still exists or covers edits made since then. Reminders stay inside the app; no notifications are scheduled."
            )
        }
        item {
            OutlinedButton(
                onClick = { reset = true },
                enabled = !state.busy && !state.loading && guidanceEditable(state.progress),
                modifier = Modifier.fillMaxWidth().testTag("reset-guidance"),
            ) {
                Text("Reset all guidance")
            }
            Text(
                "Resets known tips only. Workouts, active entries, routines, schedule, goals, backup status and preferences are kept.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    if (reset)
        AlertDialog(
            onDismissRequest = { reset = false },
            modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 560.dp).fillMaxWidth(),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            title = { Text("Reset guidance?") },
            text = {
                Text(
                    "Show known tips again without changing workout or profile data.",
                    Modifier.testTag("help-reset-content").verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        reset = false
                        vm.reset()
                    }
                ) {
                    Text("Reset tips")
                }
            },
            dismissButton = { TextButton(onClick = { reset = false }) { Text("Keep guidance") } },
        )
}

/** Small optional entry used by the screens imported from the PWA. */
@Composable
internal fun CompactHelpTip(id: String, label: String) {
    val vm = LocalHelp.current ?: return
    val state by vm.state.collectAsStateWithLifecycle()
    if (state.loading || !guidanceVisible(state.progress, id)) return
    var expanded by rememberSaveable(id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.testTag("read-tip-$id"),
        ) {
            Icon(TrackerIcons.Help, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (expanded) "Close tip" else label)
        }
        if (expanded) {
            Text(
                guidanceTopics.first { it.id == id }.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = { vm.complete(id) },
                enabled = !state.busy,
                modifier = Modifier.testTag("dismiss-tip-$id"),
            ) {
                Text("Got it")
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
