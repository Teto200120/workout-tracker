package com.hector.workouttracker.feature.routines

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hector.workouttracker.core.catalog.StarterLibrary
import com.hector.workouttracker.core.designsystem.*
import com.hector.workouttracker.data.*
import com.hector.workouttracker.feature.catalog.ExercisePicker
import com.hector.workouttracker.feature.help.CompactHelpTip
import com.hector.workouttracker.feature.session.TrackerState

@Composable
internal fun RoutinesScreen(
    library: TrackerState,
    back: () -> Unit,
    vm: RoutineViewModel = viewModel(),
    backLabel: String = "Back to Today",
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val draft = state.draft
    var discard by rememberSaveable { mutableStateOf(false) }
    var reload by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SavedRoutine?>(null) }
    val dialogModifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 560.dp).fillMaxWidth()
    val dialogProperties = DialogProperties(usePlatformDefaultWidth = false)
    fun closeEditor() {
        if (!state.busy) {
            if (draft?.changed == true) discard = true else vm.close()
        }
    }
    BackHandler(draft != null || state.starters != null || state.busy) {
        if (state.starters != null) vm.cancelStarters() else closeEditor()
    }
    Column(Modifier.fillMaxSize().background(trackerScreenBackground())) {
        if (!state.restorable)
            Text("Save before leaving the app to keep these edits.", Modifier.padding(16.dp))
        state.error?.let { message ->
            Card(
                Modifier.fillMaxWidth().padding(16.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(message)
                    if (state.conflict && draft != null)
                        TextButton(onClick = { reload = true }, enabled = !state.busy) {
                            Text("Reload saved routine")
                        }
                }
            }
        }
        val starters = state.starters
        if (starters != null) {
            StarterPreview(
                starters,
                state.busy,
                library.routines.isEmpty(),
                vm::cancelStarters,
                vm::addStarters,
            )
        } else if (draft == null) {
            LazyColumn(
                Modifier.fillMaxSize().testTag("routine-library"),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    TextButton(onClick = back, enabled = !state.busy) { Text(backLabel) }
                    RoutineHeading(
                        "Routines",
                        "Plan exercises here, then start from Today.",
                        hero = true,
                    )
                    state.message?.let { Text(it, Modifier.padding(top = 12.dp)) }
                    if (library.draft != null)
                        Text(
                            "Changes here apply to future workouts. Your active workout keeps its own exercises."
                        )
                }
                item {
                    Button(
                        onClick = { vm.open() },
                        enabled = !state.busy && library.routines.size < 500,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) {
                        Text("Create routine")
                    }
                    Text(
                        "${library.routines.size} of 500 routines",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (library.routines.isEmpty()) {
                    item {
                        Text("Build your first routine or import your saved routines from You.")
                        OutlinedButton(
                            onClick = { library.catalog?.let(vm::previewStarters) },
                            enabled = !state.busy && library.catalog != null,
                            modifier = Modifier.fillMaxWidth().testTag("preview-starter-routines"),
                        ) {
                            Text("See starter routines")
                        }
                        if (library.catalogLoading) Text("Loading offline starter routines…")
                        else if (library.catalog == null)
                            Text(
                                "Starter routines are unavailable. You can still create a routine."
                            )
                    }
                }
                items(library.routines, key = { it.record.idKey }) { routine ->
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = trackerPanelColor()),
                        border =
                            BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f),
                            ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Column(
                            Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(routine.name, style = MaterialTheme.typography.titleLarge)
                            Text(
                                "${routine.exercises.size} exercises",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            routine.exercises.take(3).forEachIndexed { index, name ->
                                Text(
                                    "${index + 1}. $name",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (routine.exercises.size > 3)
                                Text(
                                    "${routine.exercises.size - 3} more exercises",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = { vm.open(routine) },
                                    enabled = !state.busy,
                                    modifier =
                                        Modifier.testTag("edit-routine-${routine.record.idKey}")
                                            .semantics {
                                                contentDescription = "Edit ${routine.name}"
                                            },
                                ) {
                                    Text("Edit")
                                }
                                TextButton(
                                    onClick = { deleting = routine },
                                    enabled = !state.busy,
                                    colors =
                                        ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        ),
                                    modifier =
                                        Modifier.testTag("delete-routine-${routine.record.idKey}")
                                            .semantics {
                                                contentDescription = "Delete ${routine.name}"
                                            },
                                ) {
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }
        } else {
            RoutineEditor(library, draft, state.busy, vm::edit, ::closeEditor, vm::save)
        }
    }
    if (discard)
        AlertDialog(
            onDismissRequest = { discard = false },
            modifier = dialogModifier,
            properties = dialogProperties,
            title = { Text("Discard routine edits?") },
            text = {
                Text(
                    "Your saved routine has not changed.",
                    Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        discard = false
                        vm.close()
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
            title = { Text("Reload saved routine?") },
            text = {
                Text(
                    "This replaces your unsaved edits with the latest saved routine.",
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
    deleting?.let { routine ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            modifier = dialogModifier,
            properties = dialogProperties,
            title = { Text("Delete ${routine.name}?") },
            text = {
                Text(
                    "Saved workouts and any active session will remain. Schedule references keep their existing names.",
                    Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        vm.delete(routine)
                    },
                    enabled = !state.busy,
                ) {
                    Text("Delete routine")
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StarterPreview(
    starters: StarterLibrary,
    busy: Boolean,
    emptyLibrary: Boolean,
    back: () -> Unit,
    add: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(trackerScreenBackground())) {
        LazyColumn(
            Modifier.weight(1f).testTag("starter-preview"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                TextButton(onClick = back, enabled = !busy) { Text("Back to routines") }
                RoutineHeading(
                    "Starter routines",
                    "Add six routines to your empty library, then make them your own. Your workouts and active session stay as they are.",
                )
            }
            items(starters.routines, key = { it.id }) { routine ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = trackerPanelColor()),
                    border =
                        BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f),
                        ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(routine.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (routine.exercises.isEmpty())
                                "An empty routine for your own exercises."
                            else routine.exercises.joinToString("\n")
                        )
                    }
                }
            }
            item {
                Text(
                    "Exercise selections: Free Exercise DB · Unlicense. You can edit or delete these routines afterward.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (!emptyLibrary)
            Text(
                "Your library now contains routines. Go back to review them.",
                Modifier.padding(horizontal = 20.dp),
            )
        Button(
            onClick = add,
            enabled = !busy && emptyLibrary,
            modifier =
                Modifier.fillMaxWidth()
                    .padding(20.dp)
                    .heightIn(min = 56.dp)
                    .testTag("add-starter-routines"),
        ) {
            Text(if (busy) "Adding…" else "Add 6 routines")
        }
    }
}

@Composable
private fun RoutineEditor(
    library: TrackerState,
    draft: RoutineDraft,
    busy: Boolean,
    edit: ((RoutineDraft) -> RoutineDraft) -> Unit,
    back: () -> Unit,
    save: () -> Unit,
) {
    val focus = LocalFocusManager.current
    var picker by rememberSaveable { mutableStateOf(false) }
    var replacing by rememberSaveable { mutableIntStateOf(-1) }
    var duplicate by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).testTag("routine-editor"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                TextButton(onClick = back, enabled = !busy) { Text("Back to routines") }
                RoutineHeading(
                    if (draft.originalFingerprint == null) "Create routine" else "Edit routine",
                    "Changes stay in this editor until you save.",
                )
            }
            item {
                TextField(
                    draft.name,
                    { value ->
                        if (value.codePointCount(0, value.length) <= 80)
                            edit { it.copy(name = value) }
                    },
                    label = { Text("Routine name") },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("routine-name"),
                )
            }
            item { CompactHelpTip("routineEditorBasics", "Routine building tips") }
            items(draft.exercises.withIndex().toList(), key = { it.index }) { (index, name) ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = trackerPanelColor()),
                    border =
                        BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f),
                        ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("${index + 1}. $name", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    focus.clearFocus()
                                    replacing = index
                                    picker = true
                                },
                                enabled = !busy,
                                modifier = Modifier.testTag("routine-change-$index"),
                            ) {
                                Text("Change")
                            }
                            TextButton(
                                onClick = {
                                    edit {
                                        it.copy(
                                            exercises =
                                                it.exercises.filterIndexed { i, _ -> i != index }
                                        )
                                    }
                                },
                                enabled = !busy,
                                colors =
                                    ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                modifier = Modifier.testTag("routine-remove-$index"),
                            ) {
                                Text("Remove")
                            }
                            IconButton(
                                onClick = {
                                    edit {
                                        val rows = it.exercises.toMutableList()
                                        java.util.Collections.swap(rows, index, index - 1)
                                        it.copy(exercises = rows)
                                    }
                                },
                                enabled = !busy && index > 0,
                                modifier = Modifier.testTag("routine-up-$index"),
                            ) {
                                Icon(
                                    TrackerIcons.Chevron,
                                    "Move $name up",
                                    Modifier.rotate(-90f).size(20.dp),
                                )
                            }
                            IconButton(
                                onClick = {
                                    edit {
                                        val rows = it.exercises.toMutableList()
                                        java.util.Collections.swap(rows, index, index + 1)
                                        it.copy(exercises = rows)
                                    }
                                },
                                enabled = !busy && index < draft.exercises.lastIndex,
                                modifier = Modifier.testTag("routine-down-$index"),
                            ) {
                                Icon(
                                    TrackerIcons.Chevron,
                                    "Move $name down",
                                    Modifier.rotate(90f).size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        focus.clearFocus()
                        replacing = -1
                        picker = true
                    },
                    enabled = !busy && draft.exercises.size < 100,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Browse exercises")
                }
                Text(
                    "${draft.exercises.size} of 100 exercises. Empty routines can be saved.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Button(
            onClick = save,
            enabled = !busy,
            shape = RoundedCornerShape(20.dp),
            modifier =
                Modifier.fillMaxWidth().padding(20.dp).heightIn(min = 56.dp).testTag("save-routine"),
        ) {
            Text(if (busy) "Saving…" else "Save routine")
        }
    }
    if (picker)
        ExercisePicker(
            state = library.copy(localNames = (draft.exercises + library.localNames).distinct()),
            onDismiss = { picker = false },
            onAdd = { name ->
                if (!busy) {
                    if (draft.duplicate(name, replacing)) duplicate = name
                    else edit { it.select(name, replacing) }
                    true
                } else false
            },
            returnLabel = "Back to routine",
            addLabel = if (replacing == -1) "Add to routine" else "Replace exercise",
            selectionVerb = if (replacing == -1) "Add" else "Replace with",
        )
    duplicate?.let { name ->
        AlertDialog(
            onDismissRequest = { duplicate = null },
            modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 560.dp).fillMaxWidth(),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            title = { Text("Keep a repeated exercise?") },
            text = {
                Text(
                    "This routine already contains $name. Keep both rows?",
                    Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        edit { it.select(name, replacing) }
                        duplicate = null
                    }
                ) {
                    Text("Keep both")
                }
            },
            dismissButton = { TextButton(onClick = { duplicate = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RoutineHeading(title: String, detail: String, hero: Boolean = false) {
    TrainingHeading(title, detail, TrackerIcons.Workout, hero)
}
