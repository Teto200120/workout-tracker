package com.hector.workouttracker.feature.help

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hector.workouttracker.core.designsystem.Heading
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
    OutlinedCard(Modifier.fillMaxWidth().testTag("help-tip-$id")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Tip · ${topic.title}", style = MaterialTheme.typography.titleSmall)
            if (expanded) Text(topic.text, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        Modifier.fillMaxSize().testTag("help-list"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TextButton(onClick = back) { Text("Back to You") }
            Heading(
                "Learn at your pace",
                "Help & guidance",
                "Works offline. Tips open only when you choose them.",
            )
            (state.error ?: state.message)?.let { Text(it, Modifier.testTag("help-message")) }
        }
        items(guidanceTopics, key = { it.id }) { topic ->
            var expanded by rememberSaveable(topic.id) { mutableStateOf(false) }
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(topic.title, style = MaterialTheme.typography.titleMedium)
                    TextButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.testTag("help-read-${topic.id}"),
                    ) {
                        Text(if (expanded) "Close explanation" else "Read explanation")
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
