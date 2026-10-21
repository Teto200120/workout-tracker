package com.hector.workouttracker.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hector.workouttracker.core.designsystem.Heading
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.feature.session.TrackerState
import com.hector.workouttracker.feature.session.TrackerViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun HomeScreen(state: TrackerState, vm: TrackerViewModel, openSession: () -> Unit) {
    var opening by rememberSaveable { mutableStateOf(false) }
    var routinesOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.draft, opening, state.busy, state.error) {
        if (opening && state.draft != null) {
            opening = false
            openSession()
        }
        if (opening && !state.busy && state.error != null) opening = false
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Heading(
                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
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
            Text(
                "Bring your PWA history and routines into the app from You → Import PWA backup. Exercise search and text guides work offline. Routine editing and full Stats are coming next.",
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
