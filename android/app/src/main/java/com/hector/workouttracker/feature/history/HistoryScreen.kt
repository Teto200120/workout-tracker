package com.hector.workouttracker.feature.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hector.workouttracker.core.database.RecordEntity
import com.hector.workouttracker.core.designsystem.Heading
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.domain.workoutMetrics
import com.hector.workouttracker.feature.session.TrackerState
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

@Composable
internal fun HistoryScreen(state: TrackerState, open: (RecordEntity) -> Unit) {
    var workouts by remember { mutableStateOf<List<Pair<RecordEntity, JsonObject>>?>(null) }
    LaunchedEffect(state.workouts) {
        workouts =
            withContext(Dispatchers.Default) {
                state.workouts
                    .map { it to trackerJson.parseToJsonElement(it.payload).jsonObject }
                    .sortedWith(
                        compareByDescending<Pair<RecordEntity, JsonObject>> {
                                it.second.getValue("date").jsonPrimitive.content
                            }
                            .thenByDescending {
                                it.second.getValue("createdAt").jsonPrimitive.content
                            }
                    )
            }
    }
    LazyColumn(
        Modifier.fillMaxSize().testTag("history-list"),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Heading("Your progress", "History", "Saved and imported workouts.") }
        if (workouts == null) item { CircularProgressIndicator() }
        if (workouts?.isEmpty() == true)
            item { Text("Your first workout starts the story. Saved sessions will appear here.") }
        items(workouts.orEmpty(), key = { it.first.idKey }) { (record, workout) ->
            val metrics = workoutMetrics(workout)
            Card {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        workout.getValue("type").jsonPrimitive.content,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        workout.getValue("date").jsonPrimitive.content,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${metrics.workingSets} working ${if (metrics.workingSets == 1) "set" else "sets"} · ${metrics.completedSets} completed · ${metrics.duration.toInt()} min"
                    )
                    Text(
                        "Volume: ${String.format(Locale.US, "%.1f", metrics.volume)}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    TextButton(
                        onClick = { open(record) },
                        modifier =
                            Modifier.testTag("open-history-${typedIdKey(workout.getValue("id"))}"),
                    ) {
                        Text("Full workout")
                    }
                    workout.getValue("exercises").jsonArray.forEach { exercise ->
                        val e = exercise.jsonObject
                        Text(
                            "${e.getValue("name").jsonPrimitive.content} · ${e.getValue("sets").jsonArray.size} ${if (e.getValue("sets").jsonArray.size == 1) "set" else "sets"}"
                        )
                    }
                }
            }
        }
    }
}
