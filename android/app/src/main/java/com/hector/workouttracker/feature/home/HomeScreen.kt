package com.hector.workouttracker.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hector.workouttracker.core.designsystem.Heading
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.core.schedule.*
import com.hector.workouttracker.feature.help.HelpTip
import com.hector.workouttracker.feature.session.TrackerState
import com.hector.workouttracker.feature.session.TrackerViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.*

@Composable
internal fun HomeScreen(
    state: TrackerState,
    vm: TrackerViewModel,
    openRoutines: () -> Unit,
    openSchedule: () -> Unit,
    openSession: () -> Unit,
) {
    var opening by rememberSaveable { mutableStateOf(false) }
    var routinesOpen by rememberSaveable { mutableStateOf(false) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(Unit) {
        val job =
            scope.launch {
                while (isActive) {
                    today = LocalDate.now()
                    delay(30_000)
                }
            }
        onPauseOrDispose { job.cancel() }
    }
    val plan = state.schedule?.let { resolveDayPlan(today, it) }
    LaunchedEffect(state.draft, opening, state.busy, state.error) {
        if (opening && state.draft != null) {
            opening = false
            openSession()
        }
        if (opening && !state.busy && state.error != null) opening = false
    }
    LazyColumn(
        Modifier.fillMaxSize().testTag("home-list"),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Heading(
                today.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                "Let's move, ${state.profile.displayName}.",
                "One set at a time.",
            )
        }
        item {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        if (state.draft == null) "Your next workout"
                        else "Pick up where you left off",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        if (state.draft == null) "Start fresh or train from a saved routine."
                        else "${state.draft.type} · ${state.draft.exercises.size} exercises"
                    )
                    Button(
                        onClick = {
                            if (state.draft != null) openSession()
                            else {
                                opening = true
                                vm.start()
                            }
                        },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) {
                        Text(if (state.draft == null) "Start workout" else "Resume workout")
                    }
                    if (state.draft == null && state.routines.isNotEmpty())
                        OutlinedButton(
                            onClick = { routinesOpen = true },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        ) {
                            Text("Use saved routine")
                        }
                    if (state.draft != null)
                        Text(
                            if (state.pending) "Saving your latest entries…"
                            else "Draft saved on this device",
                            style = MaterialTheme.typography.labelMedium,
                        )
                }
            }
        }
        item { HelpTip("homeTour") }
        state.completion?.let { summary ->
            item {
                Card(Modifier.fillMaxWidth().testTag("workout-completion")) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "${state.completedName} saved",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        summary.highlights.forEach { highlight ->
                            Text(highlight.title, style = MaterialTheme.typography.titleMedium)
                            Text(highlight.text)
                        }
                        TextButton(onClick = vm::dismissCompletion) { Text("Dismiss summary") }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Today's plan", style = MaterialTheme.typography.labelLarge)
                    if (plan != null) {
                        Text(
                            plan.title.take(160),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(plan.note)
                        if (plan.kind == "gym" && state.draft == null) {
                            val matches = state.routines.filter { it.name == plan.routine }
                            if (matches.size == 1)
                                OutlinedButton(
                                    onClick = {
                                        opening = true
                                        vm.startPlanned(
                                            matches.single(),
                                            requireNotNull(state.schedule)[
                                                today.dayOfWeek.value % 7],
                                            today,
                                        )
                                    },
                                    enabled = !state.busy,
                                    modifier =
                                        Modifier.fillMaxWidth().testTag("start-planned-workout"),
                                ) {
                                    Text("Start planned routine")
                                }
                            else
                                Text(
                                    if (matches.isEmpty())
                                        "This routine is not in your library. Choose a saved routine or start a fresh workout."
                                    else
                                        "Several saved routines share this name. Use saved routine to choose one."
                                )
                        }
                    } else
                        Text(
                            if (state.scheduleLoading) "Loading today's plan…"
                            else "Your plan is unavailable. You can still start a workout."
                        )
                    TextButton(onClick = openSchedule, enabled = !state.busy) {
                        Text("Edit schedule")
                    }
                }
            }
        }
        item {
            Card {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("${state.workouts.size}", style = MaterialTheme.typography.displaySmall)
                    Text(
                        "workouts saved in the Android app",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        item {
            OutlinedButton(
                onClick = openRoutines,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text("Manage routines")
            }
        }
        item {
            Text(
                "Bring your PWA history and routines into the app from You → Import PWA backup. Routine editing, exercise search and text guides work offline. History, Stats and training guidance also work offline. Help & guidance is available from You.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (routinesOpen)
        AlertDialog(
            onDismissRequest = { routinesOpen = false },
            title = { Text("Choose a routine") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.routines, key = { it.record.idKey }) { routine ->
                        OutlinedButton(
                            onClick = {
                                routinesOpen = false
                                opening = true
                                vm.start(routine)
                            },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text(routine.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${routine.exercises.size} exercises",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { routinesOpen = false }) { Text("Cancel") } },
        )
}
