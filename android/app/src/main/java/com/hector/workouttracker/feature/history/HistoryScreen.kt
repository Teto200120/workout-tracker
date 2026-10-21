package com.hector.workouttracker.feature.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hector.workouttracker.core.designsystem.Heading
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.domain.workoutMetrics
import com.hector.workouttracker.feature.session.TrackerState
import java.util.Locale
import kotlinx.serialization.json.*

@Composable
internal fun HistoryScreen(state: TrackerState) {
    val workouts =
        remember(state.workouts) {
            state.workouts
                .map { trackerJson.parseToJsonElement(it.payload).jsonObject }
                .sortedByDescending {
                    it["date"]!!.jsonPrimitive.content + it["createdAt"]!!.jsonPrimitive.content
                }
        }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Heading("Your progress", "History", "Workouts saved in the native app.") }
        if (workouts.isEmpty())
            item { Text("Your first workout starts the story. Saved sessions will appear here.") }
        items(workouts, key = { typedIdKey(it.getValue("id")) }) { workout ->
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
                        "${metrics.workingSets} working sets · ${metrics.completedSets} completed · ${metrics.duration.toInt()} min"
                    )
                    Text(
                        "Volume: ${String.format(Locale.US, "%.1f", metrics.volume)}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    workout.getValue("exercises").jsonArray.forEach { exercise ->
                        val e = exercise.jsonObject
                        Text(
                            "${e.getValue("name").jsonPrimitive.content} · ${e.getValue("sets").jsonArray.size} sets"
                        )
                    }
                }
            }
        }
    }
}
