package com.hector.workouttracker.feature.session

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.hector.workouttracker.core.designsystem.Heading
import com.hector.workouttracker.core.designsystem.LocalCompletionFeedback
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.data.SetCopyRequest
import com.hector.workouttracker.domain.workoutMetrics
import com.hector.workouttracker.feature.catalog.ExerciseGuide
import com.hector.workouttracker.feature.catalog.ExercisePicker
import com.hector.workouttracker.feature.help.HelpTip
import com.hector.workouttracker.feature.history.WorkoutTags
import java.time.ZoneId

@Composable
internal fun SessionScreen(state: TrackerState, vm: TrackerViewModel, back: () -> Unit) {
    val draft = state.draft ?: return
    val completionFeedback = LocalCompletionFeedback.current
    var copyRequest by remember { mutableStateOf<SetCopyRequest?>(null) }
    var exerciseName by rememberSaveable { mutableStateOf("") }
    var discard by rememberSaveable { mutableStateOf(false) }
    var finish by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf(false) }
    var guide by rememberSaveable { mutableStateOf<String?>(null) }
    var orderMessage by rememberSaveable(draft.id) { mutableStateOf<String?>(null) }
    var movedKey by rememberSaveable(draft.id) { mutableStateOf<String?>(null) }
    var scrollTarget by remember { mutableStateOf<String?>(null) }
    val dialogModifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 560.dp).fillMaxWidth()
    val dialogProperties = DialogProperties(usePlatformDefaultWidth = false)
    val list = rememberLazyListState()
    val focus = LocalFocusManager.current
    LaunchedEffect(draft.id) {
        if (draft.exercises.any { it.collapsed }) {
            scrollTarget = draft.exercises.firstOrNull { !it.collapsed }?.key
        }
    }
    LaunchedEffect(scrollTarget, draft.exercises.map { it.key }) {
        val target = scrollTarget
        val index = draft.exercises.indexOfFirst { it.key == target }
        if (index >= 0) list.scrollToItem(index + 3)
        scrollTarget = null
    }
    fun changeExercise(key: String, transform: (Exercise) -> Exercise) {
        vm.edit {
            it.copy(exercises = it.exercises.map { e -> if (e.key == key) transform(e) else e })
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().testTag("session-list"),
        state = list,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TextButton(onClick = back) { Text("Back to Today") }
            Heading("Active workout", "Find your rhythm.")
            Text(
                if (state.pending) "Saving entries…" else "All entries saved on this device",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedTextField(
                draft.type,
                { value -> vm.edit { it.copy(type = value) } },
                label = { Text("Workout name") },
                singleLine = true,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { HelpTip("activeWorkoutBasics") }
        itemsIndexed(draft.exercises, key = { _, exercise -> exercise.key }) { position, exercise ->
            Card {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(exercise.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Exercise ${position + 1} of ${draft.exercises.size}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.testTag("session-position-${exercise.key}"),
                    )
                    val progress = exerciseProgress(exercise)
                    Text(
                        "${progress.completedSets} / ${progress.workingSets} working sets completed" +
                            if (progress.complete) " · Complete" else "",
                        modifier = Modifier.testTag("session-progress-${exercise.key}"),
                    )
                    OutlinedButton(
                        onClick = {
                            focus.clearFocus()
                            vm.edit {
                                if (
                                    it.exercises
                                        .firstOrNull { row -> row.key == exercise.key }
                                        ?.collapsed == true
                                )
                                    it.openExercise(exercise.key)
                                else it.collapseExercise(exercise.key)
                            }
                            scrollTarget = exercise.key
                        },
                        enabled = !state.busy,
                        modifier =
                            Modifier.testTag("toggle-session-${exercise.key}").semantics {
                                contentDescription =
                                    if (exercise.collapsed) "Expand ${exercise.name}"
                                    else "Collapse ${exercise.name}"
                                stateDescription =
                                    if (exercise.collapsed) "Collapsed" else "Expanded"
                            },
                    ) {
                        Text(if (exercise.collapsed) "Expand exercise" else "Collapse exercise")
                    }
                    if (exercise.collapsed) {
                        Column(
                            Modifier.testTag("session-summary-${exercise.key}"),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (progress.completedRows.isEmpty())
                                Text("No working sets marked completed.")
                            progress.completedRows.forEach { Text(it) }
                            if (progress.remainingCompletedRows > 0)
                                Text(
                                    "${progress.remainingCompletedRows} more completed sets. Expand to review all sets."
                                )
                        }
                    }
                    if (!exercise.collapsed) {
                        Column(
                            Modifier.testTag("session-expanded-${exercise.key}"),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (movedKey == exercise.key)
                                orderMessage?.let { message ->
                                    Text(
                                        message,
                                        modifier =
                                            Modifier.semantics {
                                                liveRegion = LiveRegionMode.Polite
                                            },
                                    )
                                }
                            if (draft.exercises.size > 1)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(-1 to "up", 1 to "down").forEach { (direction, label) ->
                                        TextButton(
                                            onClick = {
                                                focus.clearFocus()
                                                vm.edit { it.moveExercise(exercise.key, direction) }
                                                orderMessage =
                                                    "${exercise.name} moved to exercise ${position + direction + 1} of ${draft.exercises.size}."
                                                movedKey = exercise.key
                                                scrollTarget = exercise.key
                                            },
                                            enabled =
                                                !state.busy &&
                                                    position + direction in draft.exercises.indices,
                                            modifier =
                                                Modifier.testTag(
                                                        "move-session-$label-${exercise.key}"
                                                    )
                                                    .semantics {
                                                        contentDescription =
                                                            "Move ${exercise.name} $label"
                                                    },
                                        ) {
                                            Text("Move $label")
                                        }
                                    }
                                }
                            TextButton(onClick = { guide = exercise.name }) {
                                Text("Exercise guide")
                            }
                            TrainingGuidanceCard(state, exercise, vm) { target ->
                                vm.setCopyRequest(exercise.key, target)?.let { request ->
                                    if (request.replacesInput) copyRequest = request
                                    else vm.applySets(request)
                                }
                            }
                            exercise.sets.forEachIndexed { index, set ->
                                key(set.key) {
                                    fun changeSet(transform: (WorkingSet) -> WorkingSet) {
                                        changeExercise(exercise.key) { e ->
                                            e.copy(
                                                sets =
                                                    e.sets.map {
                                                        if (it.key == set.key) transform(it) else it
                                                    }
                                            )
                                        }
                                    }
                                    Column(
                                        Modifier.testTag("session-set-${set.key}"),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            "Set ${index + 1}",
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                        BoxWithConstraints {
                                            val density = LocalDensity.current
                                            val columns =
                                                (maxWidth / (90.dp * density.fontScale))
                                                    .toInt()
                                                    .coerceIn(
                                                        1,
                                                        if (state.profile.trackRpe) 3 else 2,
                                                    )
                                            val fieldWidth =
                                                with(density) {
                                                    // Round down so fractional pixels never wrap
                                                    // the final field onto an unintended line.
                                                    ((constraints.maxWidth -
                                                            8.dp.roundToPx() * (columns - 1)) /
                                                            columns)
                                                        .toDp()
                                                }
                                            FlowRow(
                                                maxItemsInEachRow = columns,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                OutlinedTextField(
                                                    set.weight,
                                                    { v -> changeSet { it.copy(weight = v) } },
                                                    label = { Text("Weight (lb)") },
                                                    singleLine = true,
                                                    keyboardOptions =
                                                        KeyboardOptions(
                                                            keyboardType = KeyboardType.Decimal
                                                        ),
                                                    modifier = Modifier.width(fieldWidth),
                                                    enabled = !state.busy,
                                                )
                                                OutlinedTextField(
                                                    set.reps,
                                                    { v -> changeSet { it.copy(reps = v) } },
                                                    label = { Text("Reps") },
                                                    singleLine = true,
                                                    keyboardOptions =
                                                        KeyboardOptions(
                                                            keyboardType = KeyboardType.Number
                                                        ),
                                                    modifier = Modifier.width(fieldWidth),
                                                    enabled = !state.busy,
                                                )
                                                if (state.profile.trackRpe)
                                                    OutlinedTextField(
                                                        set.rpe,
                                                        { v ->
                                                            changeSet {
                                                                it.copy(
                                                                    rpe = v,
                                                                    historicalRpeZero = false,
                                                                )
                                                            }
                                                        },
                                                        label = { Text("RPE") },
                                                        singleLine = true,
                                                        keyboardOptions =
                                                            KeyboardOptions(
                                                                keyboardType = KeyboardType.Decimal
                                                            ),
                                                        modifier = Modifier.width(fieldWidth),
                                                        enabled = !state.busy,
                                                    )
                                            }
                                        }
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Row(
                                                Modifier.heightIn(min = 48.dp)
                                                    .toggleable(
                                                        set.done,
                                                        enabled = !state.busy,
                                                        role = Role.Checkbox,
                                                        onValueChange = { v ->
                                                            changeSet { it.copy(done = v) }
                                                            if (v) completionFeedback()
                                                        },
                                                    ),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Checkbox(set.done, null)
                                                Text("Completed")
                                            }
                                            Row(
                                                Modifier.heightIn(min = 48.dp)
                                                    .toggleable(
                                                        set.warmup,
                                                        enabled = !state.busy,
                                                        role = Role.Checkbox,
                                                        onValueChange = { v ->
                                                            changeSet { it.copy(warmup = v) }
                                                        },
                                                    ),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Checkbox(set.warmup, null)
                                                Text("Warm-up")
                                            }
                                        }
                                    }
                                }
                            }
                            TextButton(
                                onClick = {
                                    changeExercise(exercise.key) {
                                        it.copy(sets = it.sets + WorkingSet())
                                    }
                                },
                                enabled = !state.busy && exercise.sets.size < 200,
                            ) {
                                Text("Add set")
                            }
                            TextButton(
                                onClick = {
                                    vm.edit {
                                        it.copy(
                                            exercises =
                                                it.exercises.filterNot { e ->
                                                    e.key == exercise.key
                                                }
                                        )
                                    }
                                },
                                enabled = !state.busy,
                            ) {
                                Text("Remove exercise")
                            }
                            Button(
                                onClick = {
                                    focus.clearFocus()
                                    val current = vm.state.value.draft ?: return@Button
                                    if (current.exercises.none { it.key == exercise.key })
                                        return@Button
                                    val next = current.nextExerciseKey(exercise.key)
                                    vm.edit {
                                        if (next == null) it.collapseExercise(exercise.key)
                                        else it.openExercise(next)
                                    }
                                    if (next == null) finish = true else scrollTarget = next
                                },
                                enabled = !state.busy,
                                modifier =
                                    Modifier.fillMaxWidth().testTag("next-session-${exercise.key}"),
                            ) {
                                Text(
                                    if (position == draft.exercises.lastIndex) "Review workout"
                                    else "Next exercise"
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { picker = true },
                    enabled = !state.busy && draft.exercises.size < 100,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Text("Browse exercises")
                }
                OutlinedTextField(
                    exerciseName,
                    { exerciseName = it },
                    label = { Text("Exercise name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.busy,
                )
                OutlinedButton(
                    onClick = { if (vm.addExercise(exerciseName)) exerciseName = "" },
                    enabled =
                        visibleName(exerciseName, 120) && !state.busy && draft.exercises.size < 100,
                ) {
                    Text("Add exercise")
                }
            }
        }
        item {
            OutlinedTextField(
                draft.notes,
                { v -> vm.edit { it.copy(notes = v) } },
                label = { Text("Workout notes") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
            )
        }
        if (state.profile.trackRpe) item { HelpTip("rpeBasics") }
        item {
            Button(
                onClick = { finish = true },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text("Finish workout")
            }
        }
        item {
            TextButton(onClick = { discard = true }, enabled = !state.busy) {
                Text("Discard workout")
            }
        }
    }
    if (picker) ExercisePicker(state, onDismiss = { picker = false }, onAdd = vm::addExercise)
    guide?.let { name -> ExerciseGuide(name, state.catalog, onDismiss = { guide = null }) }
    copyRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { copyRequest = null },
            modifier = dialogModifier,
            properties = dialogProperties,
            title = { Text("Replace entered sets?") },
            text = {
                Text(
                    if (request.target)
                        "Replace the sets for ${request.exercise.name} with the suggested working sets? Previous RPE values are copied and completion starts unchecked."
                    else
                        "Replace the sets for ${request.exercise.name} with its last logged sets, including warm-ups and RPE? Completion starts unchecked.",
                    Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        copyRequest = null
                        vm.applySets(request)
                    }
                ) {
                    Text("Replace sets")
                }
            },
            dismissButton = {
                TextButton(onClick = { copyRequest = null }) { Text("Keep entered sets") }
            },
        )
    }
    if (discard)
        AlertDialog(
            onDismissRequest = { discard = false },
            modifier = dialogModifier,
            properties = dialogProperties,
            title = { Text("Discard this workout?") },
            text = {
                Text(
                    "This removes the active draft. Saved History stays intact.",
                    Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        discard = false
                        vm.discard()
                    }
                ) {
                    Text("Discard")
                }
            },
            dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep workout") } },
        )
    if (finish) {
        val context = LocalContext.current
        val duration =
            elapsedMillis(
                draft,
                System.currentTimeMillis(),
                SystemClock.elapsedRealtime(),
                Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1),
            ) / 60000
        val metrics =
            workoutMetrics(
                workoutPayload(draft, System.currentTimeMillis(), duration, ZoneId.systemDefault())
            )
        AlertDialog(
            onDismissRequest = { finish = false },
            modifier = dialogModifier,
            properties = dialogProperties,
            title = { Text("Save to History?") },
            text = {
                LazyColumn(
                    Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            "${metrics.workingSets} working sets · ${metrics.completedSets} completed · ${metrics.duration.toInt()} min"
                        )
                        Text(
                            "Volume: ${String.format(java.util.Locale.US, "%.1f", metrics.volume)} lb"
                        )
                        Text(
                            "Your sets, notes and tags will be saved, and this active workout will close."
                        )
                    }
                    item {
                        WorkoutTags(draft.tags, !state.busy) { tags ->
                            vm.edit { it.copy(tags = tags) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        finish = false
                        vm.finish()
                    }
                ) {
                    Text("Save workout")
                }
            },
            dismissButton = { TextButton(onClick = { finish = false }) { Text("Keep editing") } },
        )
    }
}
