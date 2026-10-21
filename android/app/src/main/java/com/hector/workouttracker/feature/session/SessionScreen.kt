package com.hector.workouttracker.feature.session

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hector.workouttracker.core.designsystem.Heading
import com.hector.workouttracker.core.model.*

@Composable
internal fun SessionScreen(state: TrackerState, vm: TrackerViewModel, back: () -> Unit) {
    val draft = state.draft ?: return
    var exerciseName by rememberSaveable { mutableStateOf("") }
    var discard by rememberSaveable { mutableStateOf(false) }
    var finish by rememberSaveable { mutableStateOf(false) }
    fun changeExercise(key: String, transform: (Exercise) -> Exercise) {
        vm.edit {
            it.copy(exercises = it.exercises.map { e -> if (e.key == key) transform(e) else e })
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().testTag("session-list"),
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
        items(draft.exercises, key = { it.key }) { exercise ->
            Card {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(exercise.name, style = MaterialTheme.typography.titleLarge)
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
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "Set ${index + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        set.weight,
                                        { v -> changeSet { it.copy(weight = v) } },
                                        label = { Text("Weight (lb)") },
                                        singleLine = true,
                                        keyboardOptions =
                                            KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f),
                                        enabled = !state.busy,
                                    )
                                    OutlinedTextField(
                                        set.reps,
                                        { v -> changeSet { it.copy(reps = v) } },
                                        label = { Text("Reps") },
                                        singleLine = true,
                                        keyboardOptions =
                                            KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        enabled = !state.busy,
                                    )
                                    if (state.profile.trackRpe)
                                        OutlinedTextField(
                                            set.rpe,
                                            { v -> changeSet { it.copy(rpe = v) } },
                                            label = { Text("RPE") },
                                            singleLine = true,
                                            keyboardOptions =
                                                KeyboardOptions(
                                                    keyboardType = KeyboardType.Decimal
                                                ),
                                            modifier = Modifier.weight(1f),
                                            enabled = !state.busy,
                                        )
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(
                                        Modifier.heightIn(min = 48.dp)
                                            .toggleable(
                                                set.done,
                                                enabled = !state.busy,
                                                role = Role.Checkbox,
                                                onValueChange = { v ->
                                                    changeSet { it.copy(done = v) }
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
                            changeExercise(exercise.key) { it.copy(sets = it.sets + WorkingSet()) }
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
                                        it.exercises.filterNot { e -> e.key == exercise.key }
                                )
                            }
                        },
                        enabled = !state.busy,
                    ) {
                        Text("Remove exercise")
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    exerciseName,
                    { exerciseName = it },
                    label = { Text("Exercise name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.busy,
                )
                OutlinedButton(
                    onClick = {
                        val name = exerciseName.trim()
                        vm.edit { it.copy(exercises = it.exercises + Exercise(name = name)) }
                        exerciseName = ""
                    },
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
    if (discard)
        AlertDialog(
            onDismissRequest = { discard = false },
            title = { Text("Discard this workout?") },
            text = { Text("This removes the active draft. Saved History stays intact.") },
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
    if (finish)
        AlertDialog(
            onDismissRequest = { finish = false },
            title = { Text("Save to History?") },
            text = {
                Text("Your sets and notes will be saved, and this active workout will close.")
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
