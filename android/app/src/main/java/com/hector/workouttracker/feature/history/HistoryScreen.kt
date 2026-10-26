package com.hector.workouttracker.feature.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.hector.workouttracker.core.database.RecordEntity
import com.hector.workouttracker.core.designsystem.*
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.feature.help.CompactHelpTip
import com.hector.workouttracker.feature.session.TrackerState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
        Modifier.fillMaxSize().background(trackerScreenBackground()).testTag("history-list"),
        state = list,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Session history", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Your saved workouts, all in one place.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = trackerPanelColor()),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Recent sessions", style = MaterialTheme.typography.titleLarge)
                    FilledTonalButton(
                        onClick = {
                            focus.clearFocus()
                            choosingRoutine = true
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("history-routine-filter"),
                        shape = RoundedCornerShape(16.dp),
                        colors =
                            ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                    ) {
                        Icon(TrackerIcons.Workout, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Routine: ${routine ?: "All routines"}", Modifier.weight(1f))
                        Icon(TrackerIcons.Chevron, null, Modifier.size(18.dp))
                    }
                    TextField(
                        query,
                        { query = it },
                        label = { Text("Search exercises") },
                        colors =
                            TextFieldDefaults.colors(
                                unfocusedContainerColor =
                                    MaterialTheme.colorScheme.surfaceContainerLow,
                                focusedContainerColor =
                                    MaterialTheme.colorScheme.surfaceContainerLow,
                                unfocusedIndicatorColor =
                                    androidx.compose.ui.graphics.Color.Transparent,
                            ),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        leadingIcon = { Icon(TrackerIcons.Search, null, Modifier.size(20.dp)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                        modifier = Modifier.fillMaxWidth().testTag("history-search"),
                    )
                    if (routine != null || query.isNotEmpty())
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
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = trackerPanelColor()),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            runCatching {
                                    LocalDate.parse(workout.getValue("date").jsonPrimitive.content)
                                        .format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                                }
                                .getOrDefault(workout.getValue("date").jsonPrimitive.content),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "${metrics.duration.toInt()} min",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        workout.getValue("type").jsonPrimitive.content,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    BoxWithConstraints {
                        val columns =
                            (maxWidth / (92.dp * LocalDensity.current.fontScale))
                                .toInt()
                                .coerceIn(1, 3)
                        val width = (maxWidth - 8.dp * (columns - 1)) / columns
                        FlowRow(
                            maxItemsInEachRow = columns,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            HistoryMetric(
                                "${workout.getValue("exercises").jsonArray.size}",
                                "Exercises",
                                Modifier.width(width),
                            )
                            HistoryMetric(
                                "${metrics.completedSets}/${metrics.workingSets}",
                                "Sets done",
                                Modifier.width(width),
                            )
                            HistoryMetric(
                                String.format(Locale.US, "%.1f", metrics.volume),
                                "Volume · lb × reps",
                                Modifier.width(width),
                            )
                        }
                    }
                    workout["notes"]
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.takeIf { it.isNotBlank() }
                        ?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    val tags =
                        (workout["tags"] as? JsonArray).orEmpty().map { it.jsonPrimitive.content }
                    if (tags.isNotEmpty())
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            tags.take(3).forEach { HistoryPill(it) }
                            if (tags.size > 3) HistoryPill("+${tags.size - 3} tags")
                        }
                    val exercises = workout.getValue("exercises").jsonArray
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        exercises.take(3).forEach { value ->
                            val exercise = value.jsonObject
                            val count = exercise.getValue("sets").jsonArray.size
                            HistoryPill(
                                "${exercise.getValue("name").jsonPrimitive.content} · $count ${if(count == 1) "set" else "sets"}"
                            )
                        }
                    }
                    if (exercises.size > 3)
                        Text(
                            "${exercises.size - 3} more exercises in full workout",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    OutlinedButton(
                        onClick = {
                            focus.clearFocus()
                            open(record)
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                                .testTag("open-history-${typedIdKey(workout.getValue("id"))}"),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("Full workout", Modifier.weight(1f))
                        Icon(TrackerIcons.Chevron, null, Modifier.size(18.dp))
                    }
                }
            }
        }
        item { CompactHelpTip("historyBasics", "Tips for History") }
    }
    if (choosingRoutine)
        AlertDialog(
            onDismissRequest = { choosingRoutine = false },
            modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 560.dp).fillMaxWidth(),
            properties = DialogProperties(usePlatformDefaultWidth = false),
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

@Composable
private fun HistoryMetric(value: String, label: String, modifier: Modifier) {
    Surface(
        modifier.heightIn(min = 80.dp).semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .045f),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HistoryPill(label: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
