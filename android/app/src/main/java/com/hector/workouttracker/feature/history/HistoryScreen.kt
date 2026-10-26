package com.hector.workouttracker.feature.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.hector.workouttracker.core.database.RecordEntity
import com.hector.workouttracker.core.designsystem.Heading
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.feature.help.HelpTip
import com.hector.workouttracker.feature.session.TrackerState
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

@Composable
internal fun HistoryScreen(state: TrackerState, open: (RecordEntity) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var routine by rememberSaveable { mutableStateOf<String?>(null) }
    var choosingRoutine by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val list = rememberLazyListState()
    var entries by remember { mutableStateOf<List<HistoryEntry>?>(null) }
    LaunchedEffect(state.workouts) {
        entries = null
        entries = withContext(Dispatchers.Default) { historyEntries(state.workouts) }
    }
    val routines =
        remember(entries, state.routines, routine) {
            // Historical names remain filterable after a routine is renamed or deleted.
            (state.routines.map { it.name } +
                    entries.orEmpty().map { it.routine } +
                    listOfNotNull(routine))
                .distinct()
                .sorted()
        }
    val visible by
        produceState<List<HistoryEntry>?>(null, entries, routine, query) {
            value = null
            val snapshot = entries
            if (snapshot != null)
                value = withContext(Dispatchers.Default) { filterHistory(snapshot, routine, query) }
        }
    LazyColumn(
        Modifier.fillMaxSize().testTag("history-list"),
        state = list,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Heading("Your progress", "History", "Saved and imported workouts.") }
        item { HelpTip("historyBasics") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search exercises") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().testTag("history-search"),
                )
                OutlinedButton(
                    onClick = {
                        focus.clearFocus()
                        choosingRoutine = true
                    },
                    modifier = Modifier.fillMaxWidth().testTag("history-routine-filter"),
                ) {
                    Text("Routine: ${routine ?: "All routines"}")
                }
                if (routine != null || query.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            focus.clearFocus()
                            query = ""
                            routine = null
                        },
                        modifier = Modifier.testTag("history-clear-filters"),
                    ) {
                        Text("Clear filters")
                    }
                }
            }
        }
        if (visible == null) item { CircularProgressIndicator() }
        visible?.let { results ->
            item {
                Text(
                    "${results.size} ${if (results.size == 1) "workout" else "workouts"} found",
                    modifier =
                        Modifier.testTag("history-result-count").semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                )
            }
            if (results.isEmpty())
                item {
                    Column {
                        Text("No workouts found", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (entries?.isEmpty() == true && routine == null && query.isBlank())
                                "Your first workout starts the story. Saved sessions will appear here."
                            else "Try a different routine filter or exercise search."
                        )
                    }
                }
        }
        items(visible.orEmpty(), key = { it.record.idKey }) { entry ->
            val (record, workout) = entry
            val metrics = entry.metrics
            Card {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        workout.getValue("type").jsonPrimitive.content,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        workout.getValue("date").jsonPrimitive.content,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${metrics.workingSets} working ${if (metrics.workingSets == 1) "set" else "sets"} · ${metrics.completedSets} completed · ${metrics.duration.toInt()} min",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Volume: ${String.format(Locale.US, "%.1f", metrics.volume)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    TextButton(
                        onClick = {
                            focus.clearFocus()
                            open(record)
                        },
                        modifier =
                            Modifier.testTag("open-history-${typedIdKey(workout.getValue("id"))}"),
                    ) {
                        Text("Full workout")
                    }
                    workout.getValue("exercises").jsonArray.forEach { exercise ->
                        val e = exercise.jsonObject
                        Text(
                            "${e.getValue("name").jsonPrimitive.content} · ${e.getValue("sets").jsonArray.size} ${if (e.getValue("sets").jsonArray.size == 1) "set" else "sets"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    if (choosingRoutine)
        AlertDialog(
            onDismissRequest = { choosingRoutine = false },
            title = { Text("Filter by routine") },
            text = {
                LazyColumn(Modifier.testTag("history-routine-options")) {
                    item {
                        RoutineOption("All routines", routine == null, "history-filter-all") {
                            routine = null
                            choosingRoutine = false
                        }
                    }
                    items(routines, key = { it }) { name ->
                        RoutineOption(name, routine == name, "history-filter-$name") {
                            routine = name
                            choosingRoutine = false
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingRoutine = false }) { Text("Cancel") } },
        )
}

@Composable
private fun RoutineOption(name: String, selected: Boolean, tag: String, choose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected, role = Role.RadioButton, onClick = choose)
            .testTag(tag)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick = null)
        Text(name, Modifier.weight(1f).padding(start = 12.dp))
    }
}
