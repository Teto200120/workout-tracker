package com.hector.workouttracker.feature.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hector.workouttracker.core.designsystem.*
import com.hector.workouttracker.core.model.visibleName
import com.hector.workouttracker.data.*
import com.hector.workouttracker.feature.catalog.ExercisePicker
import com.hector.workouttracker.feature.session.TrackerState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal val quickWorkoutTags =
    listOf(
        "Good session",
        "Low energy",
        "Bad sleep",
        "Sore",
        "Felt strong",
        "Felt weak",
        "Great pump",
        "Rushed workout",
        "Shoulder discomfort",
        "Elbow discomfort",
        "Knee discomfort",
        "Soccer fatigue",
    )

@Composable
internal fun WorkoutTags(tags: List<String>, enabled: Boolean, change: (List<String>) -> Unit) {
    Text("Quick tags", style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (quickWorkoutTags + tags).distinct().forEach { tag ->
            FilterChip(
                selected = tag in tags,
                onClick = { change(if (tag in tags) tags.filterNot { it == tag } else tags + tag) },
                enabled = enabled && (tag in tags || tags.size < 32),
                label = { Text(tag) },
                modifier = Modifier.testTag("workout-tag-$tag"),
            )
        }
    }
}

@Composable
private fun HistoryField(
    value: String,
    label: String,
    enabled: Boolean,
    tag: String,
    numeric: Boolean = false,
    lines: Boolean = false,
    onPickDate: (() -> Unit)? = null,
    change: (String) -> Unit,
) {
    OutlinedTextField(
        value,
        change,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        shape = RoundedCornerShape(16.dp),
        trailingIcon =
            if (onPickDate != null) {
                {
                    IconButton(onClick = onPickDate, enabled = enabled) {
                        Icon(TrackerIcons.Calendar, "Choose workout date")
                    }
                }
            } else null,
        enabled = enabled,
        singleLine = !lines,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text
            ),
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

@Composable
internal fun HistoryWorkoutScreen(state: TrackerState, vm: HistoryViewModel, back: () -> Unit) {
    val editor by vm.state.collectAsStateWithLifecycle()
    val draft = editor.draft
    val list = rememberLazyListState()
    LaunchedEffect(editor.editing, draft?.id) { list.scrollToItem(0) }
    var discard by rememberSaveable { mutableStateOf(false) }
    var delete by rememberSaveable { mutableStateOf(false) }
    var reload by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf(false) }
    var replacement by rememberSaveable { mutableStateOf<String?>(null) }
    var newTag by rememberSaveable { mutableStateOf("") }
    var datePicker by rememberSaveable { mutableStateOf(false) }
    val dialogModifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 560.dp).fillMaxWidth()
    val dialogProperties = DialogProperties(usePlatformDefaultWidth = false)
    fun leave() {
        if (editor.busy) return
        if (draft?.changed == true) discard = true
        else {
            vm.close()
            back()
        }
    }
    BackHandler { leave() }
    val values = draft?.values
    val enabled = !editor.busy
    fun exercise(key: String, change: (HistoryExercise) -> HistoryExercise) {
        vm.edit {
            it.copy(exercises = it.exercises.map { e -> if (e.key == key) change(e) else e })
        }
    }
    Column(Modifier.fillMaxSize().background(trackerScreenBackground())) {
        editor.error?.let { error ->
            Card(
                Modifier.fillMaxWidth().padding(12.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(error)
                    if (editor.conflict)
                        TextButton(onClick = { reload = true }, enabled = enabled) {
                            Text("Reload saved workout")
                        }
                }
            }
        }
        LazyColumn(
            Modifier.weight(1f).testTag("history-workout"),
            state = list,
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                TextButton(onClick = { leave() }, enabled = enabled) { Text("Back to History") }
                TrainingHeading(
                    values?.type ?: "History",
                    if (editor.editing) "Edit saved workout" else "Saved workout",
                    TrackerIcons.History,
                    hero = !editor.editing,
                )
                editor.message?.let { Text(it) }
                if (!editor.restorable)
                    Text(
                        "Save before leaving. This workout is too large to restore unsaved edits after the app is recreated."
                    )
            }
            if (values == null)
                item {
                    if (editor.busy) CircularProgressIndicator()
                    else Text("Open a workout from History to see its details.")
                }
            else {
                item {
                    if (editor.editing)
                        HistorySection {
                            HistoryField(values.type, "Workout name", enabled, "history-name") { v
                                ->
                                vm.edit { it.copy(type = v) }
                            }
                            HistoryField(
                                values.date,
                                "Date (YYYY-MM-DD)",
                                enabled,
                                "history-date",
                                onPickDate = { datePicker = true },
                            ) { v ->
                                vm.edit { it.copy(date = v) }
                            }
                            HistoryField(
                                values.startTime,
                                "Start time (HH:MM)",
                                enabled,
                                "history-start",
                            ) { v ->
                                vm.edit { it.copy(startTime = v) }
                            }
                            HistoryField(
                                values.endTime,
                                "End time (HH:MM)",
                                enabled,
                                "history-end",
                            ) { v ->
                                vm.edit { it.copy(endTime = v) }
                            }
                            HistoryField(
                                values.duration,
                                "Duration (minutes)",
                                enabled,
                                "history-duration",
                                numeric = true,
                            ) { v ->
                                vm.edit { it.copy(duration = v) }
                            }
                            Text(
                                "Clear duration to calculate from start/end times, including overnight workouts.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            HistoryField(
                                values.notes,
                                "Workout notes",
                                enabled,
                                "history-notes",
                                lines = true,
                            ) { v ->
                                vm.edit { it.copy(notes = v) }
                            }
                            WorkoutTags(values.tags, enabled) { v -> vm.edit { it.copy(tags = v) } }
                            HistoryField(newTag, "Custom tag", enabled, "history-custom-tag") {
                                newTag = it
                            }
                            TextButton(
                                onClick = {
                                    vm.edit { it.copy(tags = it.tags + newTag.trim()) }
                                    newTag = ""
                                },
                                enabled =
                                    enabled &&
                                        visibleName(newTag.trim(), 80) &&
                                        values.tags.size < 32,
                            ) {
                                Text("Add tag")
                            }
                        }
                    else
                        HistorySection {
                            Text(values.date)
                            Text(
                                "${values.startTime.ifEmpty { "—" }} → ${values.endTime.ifEmpty { "—" }}"
                            )
                            Text(
                                if (values.duration.isEmpty())
                                    "Duration calculated from start/end times"
                                else "${values.duration} minutes"
                            )
                            if (values.notes.isNotEmpty()) Text(values.notes)
                            if (values.tags.isNotEmpty()) Text(values.tags.joinToString(" · "))
                            FilledTonalButton(
                                onClick = vm::beginEditing,
                                enabled = enabled,
                                modifier = Modifier.testTag("edit-history-workout"),
                            ) {
                                Text("Edit workout")
                            }
                        }
                }
                values.exercises.forEachIndexed { i, e ->
                    item(key = "header:${e.key}") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (editor.editing) {
                                HistoryField(
                                    e.name,
                                    "Exercise ${i + 1}",
                                    enabled,
                                    "history-exercise-$i",
                                ) { v ->
                                    exercise(e.key) { it.copy(name = v) }
                                }
                                TextButton(
                                    onClick = {
                                        replacement = e.key
                                        picker = true
                                    },
                                    enabled = enabled,
                                ) {
                                    Text("Choose another exercise")
                                }
                                HistoryField(
                                    e.notes,
                                    "Exercise notes",
                                    enabled,
                                    "history-exercise-notes-$i",
                                    lines = true,
                                ) { v ->
                                    exercise(e.key) { it.copy(notes = v) }
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(
                                        onClick = {
                                            vm.edit { v ->
                                                v.copy(
                                                    exercises =
                                                        v.exercises.toMutableList().apply {
                                                            add(i - 1, removeAt(i))
                                                        }
                                                )
                                            }
                                        },
                                        enabled = enabled && i > 0,
                                        modifier = Modifier.testTag("history-up-$i"),
                                    ) {
                                        Text("Move up")
                                    }
                                    TextButton(
                                        onClick = {
                                            vm.edit { v ->
                                                v.copy(
                                                    exercises =
                                                        v.exercises.toMutableList().apply {
                                                            add(i + 1, removeAt(i))
                                                        }
                                                )
                                            }
                                        },
                                        enabled = enabled && i < values.exercises.lastIndex,
                                        modifier = Modifier.testTag("history-down-$i"),
                                    ) {
                                        Text("Move down")
                                    }
                                    TextButton(
                                        onClick = {
                                            vm.edit { v ->
                                                v.copy(
                                                    exercises =
                                                        v.exercises.filterNot { it.key == e.key }
                                                )
                                            }
                                        },
                                        enabled = enabled,
                                        modifier = Modifier.testTag("history-remove-$i"),
                                    ) {
                                        Text("Remove exercise")
                                    }
                                }
                            } else {
                                Text(e.name, style = MaterialTheme.typography.titleLarge)
                                if (e.notes.isNotEmpty()) Text(e.notes)
                            }
                        }
                    }
                    e.sets.forEachIndexed { j, set ->
                        item(key = "set:${e.key}:${set.key}") {
                            Card(
                                Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors =
                                    CardDefaults.cardColors(containerColor = trackerPanelColor()),
                                border =
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Column(
                                    Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        "Set ${j + 1}",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    if (editor.editing) {
                                        fun change(change: (HistorySet) -> HistorySet) {
                                            exercise(e.key) { row ->
                                                row.copy(
                                                    sets =
                                                        row.sets.map {
                                                            if (it.key == set.key) change(it)
                                                            else it
                                                        }
                                                )
                                            }
                                        }
                                        HistoryField(
                                            set.weight,
                                            "Weight (lb)",
                                            enabled,
                                            "history-weight-$i-$j",
                                            numeric = true,
                                        ) { v ->
                                            change { it.copy(weight = v) }
                                        }
                                        HistoryField(
                                            set.reps,
                                            "Reps",
                                            enabled,
                                            "history-reps-$i-$j",
                                            numeric = true,
                                        ) { v ->
                                            change { it.copy(reps = v) }
                                        }
                                        // Historical RPE remains editable even when tracking is
                                        // disabled for new sessions.
                                        HistoryField(
                                            set.rpe,
                                            "RPE",
                                            enabled,
                                            "history-rpe-$i-$j",
                                            numeric = true,
                                        ) { v ->
                                            change { it.copy(rpe = v) }
                                        }
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Row(
                                                Modifier.heightIn(min = 48.dp)
                                                    .testTag("history-completed-$i-$j")
                                                    .semantics {
                                                        contentDescription =
                                                            "Completed, exercise ${i + 1}, ${e.name}, set ${j + 1}"
                                                    }
                                                    .toggleable(
                                                        set.done,
                                                        enabled = enabled,
                                                        role = Role.Checkbox,
                                                        onValueChange = { v ->
                                                            change { it.copy(done = v) }
                                                        },
                                                    ),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Checkbox(set.done, null, enabled = enabled)
                                                Text("Completed")
                                            }
                                            Row(
                                                Modifier.heightIn(min = 48.dp)
                                                    .testTag("history-warmup-$i-$j")
                                                    .semantics {
                                                        contentDescription =
                                                            "Warm-up, exercise ${i + 1}, ${e.name}, set ${j + 1}"
                                                    }
                                                    .toggleable(
                                                        set.warmup,
                                                        enabled = enabled,
                                                        role = Role.Checkbox,
                                                        onValueChange = { v ->
                                                            change { it.copy(warmup = v) }
                                                        },
                                                    ),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Checkbox(set.warmup, null, enabled = enabled)
                                                Text("Warm-up")
                                            }
                                        }
                                        TextButton(
                                            onClick = {
                                                exercise(e.key) {
                                                    it.copy(
                                                        sets =
                                                            it.sets.filterNot { s ->
                                                                s.key == set.key
                                                            }
                                                    )
                                                }
                                            },
                                            enabled = enabled,
                                            modifier = Modifier.testTag("history-remove-set-$i-$j"),
                                        ) {
                                            Text("Remove set")
                                        }
                                    } else
                                        Text(
                                            "${set.weight.ifEmpty { "—" }} lb · ${set.reps.ifEmpty { "—" }} reps · RPE ${set.rpe.ifEmpty { "—" }}\n${if (set.warmup) "Warm-up" else "Work set"}${if (set.done) " · Completed" else ""}"
                                        )
                                }
                            }
                        }
                    }
                    if (editor.editing)
                        item(key = "add:${e.key}") {
                            TextButton(
                                onClick = {
                                    exercise(e.key) { it.copy(sets = it.sets + HistorySet()) }
                                },
                                enabled = enabled && e.sets.size < 200,
                                modifier = Modifier.testTag("history-add-set-$i"),
                            ) {
                                Text("Add set")
                            }
                        }
                }
                if (editor.editing)
                    item {
                        OutlinedButton(
                            onClick = {
                                replacement = null
                                picker = true
                            },
                            enabled = enabled && values.exercises.size < 100,
                            modifier = Modifier.fillMaxWidth().testTag("history-add-exercise"),
                        ) {
                            Text("Add exercise")
                        }
                    }
                if (!editor.editing)
                    item {
                        TextButton(
                            onClick = { delete = true },
                            enabled = enabled,
                            modifier = Modifier.testTag("delete-history-workout"),
                        ) {
                            Text("Delete workout", color = MaterialTheme.colorScheme.error)
                        }
                    }
            }
        }
        if (editor.editing && values != null)
            Button(
                onClick = vm::save,
                shape = RoundedCornerShape(20.dp),
                enabled = enabled && draft?.changed == true,
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(20.dp)
                        .heightIn(min = 56.dp)
                        .testTag("save-history-workout"),
            ) {
                Text(if (editor.busy) "Saving…" else "Save changes")
            }
    }
    if (datePicker && values != null) {
        val selected =
            runCatching {
                    LocalDate.parse(values.date)
                        .takeIf { it.year in 1..9999 }
                        ?.let { it.toEpochDay() * 86_400_000L }
                }
                .getOrNull()
        val pickerState =
            rememberDatePickerState(initialSelectedDateMillis = selected, yearRange = 1..9999)
        val calendarHeight =
            (with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } *
                    .6f)
                .coerceAtLeast(160.dp)
        DatePickerDialog(
            onDismissRequest = { datePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            vm.edit {
                                it.copy(
                                    date =
                                        Instant.ofEpochMilli(millis)
                                            .atZone(ZoneOffset.UTC)
                                            .toLocalDate()
                                            .toString()
                                )
                            }
                        }
                        datePicker = false
                    },
                    enabled = pickerState.selectedDateMillis != null,
                ) {
                    Text("Use date")
                }
            },
            dismissButton = { TextButton(onClick = { datePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(
                pickerState,
                modifier =
                    Modifier.heightIn(max = calendarHeight).verticalScroll(rememberScrollState()),
            )
        }
    }
    if (picker)
        ExercisePicker(
            state,
            { picker = false },
            { name ->
                if (visibleName(name, 120) && !editor.busy) {
                    val replacing = replacement
                    vm.edit { v ->
                        v.copy(
                            exercises =
                                if (replacing == null) v.exercises + HistoryExercise(name = name)
                                else
                                    v.exercises.map {
                                        if (it.key == replacing) it.copy(name = name) else it
                                    }
                        )
                    }
                    true
                } else false
            },
            returnLabel = "Back to saved workout",
            addLabel = "Use exercise",
        )
    if (discard)
        AlertDialog(
            onDismissRequest = { discard = false },
            modifier = dialogModifier,
            properties = dialogProperties,
            title = { Text("Discard these edits?") },
            text = {
                Text(
                    "The saved workout and active session stay unchanged.",
                    Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        discard = false
                        vm.close()
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
            title = { Text("Reload saved workout?") },
            text = {
                Text(
                    "This replaces your visible values with the latest saved workout.",
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
    if (delete)
        AlertDialog(
            onDismissRequest = { delete = false },
            modifier = dialogModifier,
            properties = dialogProperties,
            title = { Text("Delete this workout?") },
            text = {
                Text(
                    "This permanently removes this saved workout. Your active session and routines stay intact.",
                    Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        delete = false
                        vm.delete()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { delete = false }) { Text("Cancel") } },
        )
}

@Composable
private fun HistorySection(content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = trackerPanelColor()),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}
