package com.hector.workouttracker.feature.session

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hector.workouttracker.core.model.Exercise
import com.hector.workouttracker.domain.progressNumber
import kotlinx.serialization.json.*

@Composable
internal fun TrainingGuidanceCard(
    state: TrackerState,
    exercise: Exercise,
    vm: TrackerViewModel,
    copy: (Boolean) -> Unit,
) {
    val guide = state.guidance[exercise.name]
    var details by rememberSaveable(exercise.key) { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth().testTag("training-${exercise.key}")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Next-session guidance", style = MaterialTheme.typography.titleSmall)
            when {
                state.guidanceError != null -> {
                    Text(state.guidanceError)
                    TextButton(onClick = vm::observeGuidance) { Text("Retry guidance") }
                }
                guide == null -> Text("Loading guidance…")
                else -> {
                    if (guide.last == null)
                        Text(
                            "No previous session · ${guide.profile.min}–${guide.profile.max} reps to build a baseline"
                        )
                    else {
                        Text(
                            "Last logged ${guide.last.date} · ${guide.last.type}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Target: ${guide.target.weight} · ${guide.target.repsText}",
                            modifier = Modifier.testTag("training-target-${exercise.key}"),
                        )
                        Text(guide.target.reason, style = MaterialTheme.typography.bodySmall)
                    }
                    guide.pr?.let { pr ->
                        Text(
                            "PR: ${progressNumber(pr.weight)} lb × ${progressNumber(pr.reps)} · ${pr.date} (best estimated 1RM)",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (guide.last?.sets?.isNotEmpty() == true)
                            TextButton(
                                onClick = { copy(false) },
                                enabled = !state.busy,
                                modifier = Modifier.testTag("use-last-${exercise.key}"),
                            ) {
                                Text("Use last sets")
                            }
                        if (guide.target.targetSets.isNotEmpty())
                            TextButton(
                                onClick = { copy(true) },
                                enabled = !state.busy,
                                modifier = Modifier.testTag("use-target-${exercise.key}"),
                            ) {
                                Text("Use target sets")
                            }
                        if (guide.last?.sets?.isNotEmpty() == true)
                            TextButton(
                                onClick = { details = true },
                                modifier = Modifier.testTag("review-last-${exercise.key}"),
                            ) {
                                Text("Review last sets")
                            }
                    }
                }
            }
        }
    }
    if (details && guide?.last != null)
        AlertDialog(
            onDismissRequest = { details = false },
            title = { Text("Last sets · ${guide.last.date}") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(guide.last.sets.size) { index ->
                        val set = guide.last.sets[index]
                        fun text(key: String) =
                            (set[key] as? JsonPrimitive)
                                ?.takeUnless { it is JsonNull }
                                ?.content
                                ?.ifEmpty { "—" } ?: "—"
                        Text(
                            "${index + 1}. ${text("weight")} lb × ${text("reps")} · RPE ${text("rpe")}" +
                                if ((set["warmup"] as? JsonPrimitive)?.booleanOrNull == true)
                                    " · Warm-up"
                                else " · Working"
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { details = false }) { Text("Done") } },
        )
}
