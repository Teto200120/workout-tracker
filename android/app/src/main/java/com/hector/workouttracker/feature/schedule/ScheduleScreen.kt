package com.hector.workouttracker.feature.schedule

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hector.workouttracker.core.designsystem.*
import com.hector.workouttracker.core.schedule.*
import com.hector.workouttracker.feature.session.TrackerState

@Composable
internal fun ScheduleScreen(
    library: TrackerState,
    back: () -> Unit,
    vm: ScheduleViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val draft = state.draft
    var discard by rememberSaveable { mutableStateOf(false) }
    var reload by rememberSaveable { mutableStateOf(false) }
    var kindDay by rememberSaveable { mutableIntStateOf(-1) }
    var routineDay by rememberSaveable { mutableIntStateOf(-1) }
    val dialogModifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 560.dp).fillMaxWidth()
    val dialogProperties = DialogProperties(usePlatformDefaultWidth = false)
    fun leave() {
        if (!state.busy) {
            if (draft?.changed == true) discard = true else back()
        }
    }
    BackHandler { leave() }
    Column(Modifier.fillMaxSize().background(trackerScreenBackground())) {
        state.error?.let { message ->
            Card(Modifier.fillMaxWidth().padding(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(message, color = MaterialTheme.colorScheme.error)
                    if (state.conflict)
                        TextButton(onClick = { reload = true }, enabled = !state.busy) {
                            Text("Reload saved schedule")
                        }
                }
            }
        }
        LazyColumn(
            Modifier.weight(1f).testTag("schedule-editor"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                TextButton(onClick = ::leave, enabled = !state.busy) { Text("Back") }
                Text("Weekly schedule", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Make room for training and recovery.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Changes apply to future workouts after you save.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.message?.let { Text(it) }
                if (!state.restorable)
                    Text("Save before leaving the app to keep these schedule edits.")
                if (library.draft != null)
                    Text("Your active workout keeps its own routine and exercises.")
                if (draft == null && !state.busy)
                    TextButton(onClick = vm::reload) { Text("Retry loading schedule") }
                if (draft == null && state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (draft != null)
                items(listOf(1, 2, 3, 4, 5, 6, 0), key = { it }) { index ->
                    val day = draft.days[index]
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = trackerPanelColor()),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        scheduleDayNames[index].take(2),
                                        Modifier.padding(10.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                                Column {
                                    Text(
                                        scheduleDayNames[index],
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        "Workout suggestion",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            FilledTonalButton(
                                onClick = { kindDay = index },
                                shape = RoundedCornerShape(16.dp),
                                colors =
                                    ButtonDefaults.filledTonalButtonColors(
                                        containerColor =
                                            MaterialTheme.colorScheme.surfaceContainerLow,
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                    ),
                                enabled = !state.busy,
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .heightIn(min = 52.dp)
                                        .testTag("schedule-kind-$index")
                                        .semantics {
                                            contentDescription =
                                                "${scheduleDayNames[index]} activity"
                                        },
                            ) {
                                Text(
                                    day.kind.replaceFirstChar { it.uppercase() },
                                    Modifier.weight(1f),
                                )
                                Icon(TrackerIcons.Chevron, null, Modifier.size(18.dp))
                            }
                            if (day.kind == "gym") {
                                FilledTonalButton(
                                    onClick = { routineDay = index },
                                    shape = RoundedCornerShape(16.dp),
                                    colors =
                                        ButtonDefaults.filledTonalButtonColors(
                                            containerColor =
                                                MaterialTheme.colorScheme.surfaceContainerLow,
                                            contentColor = MaterialTheme.colorScheme.onSurface,
                                        ),
                                    enabled = !state.busy,
                                    modifier =
                                        Modifier.fillMaxWidth()
                                            .testTag("schedule-routine-$index")
                                            .semantics {
                                                contentDescription =
                                                    "${scheduleDayNames[index]} routine"
                                            },
                                ) {
                                    Text(
                                        "Routine: ${day.routine.take(120).ifEmpty { "Default (${scheduleDays()[index].routine})" }}",
                                        modifier = Modifier.weight(1f),
                                        maxLines =
                                            if (LocalDensity.current.fontScale > 1.3f) 4 else 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Icon(TrackerIcons.Chevron, null, Modifier.size(18.dp))
                                }
                                val matches = library.routines.count { it.name == day.routine }
                                if (matches == 0 && day.routine.isNotEmpty())
                                    Text(
                                        "This saved routine name is not in your library. Choose one, or keep the name for later."
                                    )
                                if (matches > 1)
                                    Text(
                                        "More than one saved routine has this name. Today will ask you to choose a routine."
                                    )
                            } else
                                Text(
                                    if (day.kind == "soccer")
                                        "Soccer stays separate from gym workout logging."
                                    else "A recovery day. A fresh workout is always available."
                                )
                        }
                    }
                }
        }
        Button(
            shape = RoundedCornerShape(20.dp),
            onClick = vm::save,
            enabled = !state.busy && draft?.changed == true,
            modifier =
                Modifier.fillMaxWidth()
                    .padding(20.dp)
                    .heightIn(min = 56.dp)
                    .testTag("save-schedule"),
        ) {
            Text(if (state.busy) "Saving…" else "Save schedule")
        }
    }
    if (kindDay >= 0)
        AlertDialog(
            onDismissRequest = { kindDay = -1 },
            modifier = dialogModifier,
            properties = dialogProperties,
            title = { Text("${scheduleDayNames[kindDay]} activity") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    scheduleKinds.forEach { kind ->
                        val selected = draft?.days?.get(kindDay)?.kind == kind
                        Row(
                            Modifier.fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("choose-kind-$kind")
                                .selectable(
                                    selected,
                                    enabled = !state.busy,
                                    role = Role.RadioButton,
                                    onClick = {
                                        vm.edit(kindDay) { it.copy(kind = kind) }
                                        kindDay = -1
                                    },
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RadioButton(selected, null)
                            Text(kind.replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { kindDay = -1 }) { Text("Cancel") } },
        )
    if (routineDay >= 0)
        AlertDialog(
            onDismissRequest = { routineDay = -1 },
            modifier = dialogModifier,
            properties = dialogProperties,
            title = { Text("${scheduleDayNames[routineDay]} routine") },
            text = {
                LazyColumn(Modifier.testTag("schedule-routine-options")) {
                    item {
                        Text(
                            "Only the name is stored in the schedule. Existing names stay until you choose a replacement."
                        )
                    }
                    items(library.routines.distinctBy { it.name }, key = { it.record.idKey }) {
                        routine ->
                        val selected = draft?.days?.get(routineDay)?.routine == routine.name
                        Row(
                            Modifier.fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("choose-schedule-${routine.record.idKey}")
                                .selectable(
                                    selected,
                                    enabled = !state.busy,
                                    role = Role.RadioButton,
                                    onClick = {
                                        vm.edit(routineDay) { it.copy(routine = routine.name) }
                                        routineDay = -1
                                    },
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RadioButton(selected, null)
                            Text(routine.name)
                        }
                    }
                    if (library.routines.isEmpty())
                        item { Text("Create or import a routine from Manage routines first.") }
                }
            },
            confirmButton = {
                TextButton(onClick = { routineDay = -1 }) { Text("Keep saved name") }
            },
        )
    if (discard)
        AlertDialog(
            onDismissRequest = { discard = false },
            modifier = dialogModifier,
            properties = dialogProperties,
            title = { Text("Discard schedule edits?") },
            text = {
                Text(
                    "Your saved schedule has not changed.",
                    Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        discard = false
                        back()
                    }
                ) {
                    Text("Discard edits")
                }
            },
            dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } },
        )
    if (reload)
        AlertDialog(
            onDismissRequest = { reload = false },
            modifier = dialogModifier,
            properties = dialogProperties,
            title = { Text("Reload saved schedule?") },
            text = {
                Text(
                    "This replaces your unsaved plan with the latest saved settings.",
                    Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        reload = false
                        vm.reload()
                    }
                ) {
                    Text("Reload")
                }
            },
            dismissButton = { TextButton(onClick = { reload = false }) { Text("Keep editing") } },
        )
}
