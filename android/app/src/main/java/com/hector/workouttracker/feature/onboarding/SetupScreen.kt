package com.hector.workouttracker.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hector.workouttracker.core.designsystem.*
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.feature.backup.BackupControls
import com.hector.workouttracker.feature.session.TrackerState
import com.hector.workouttracker.feature.session.TrackerViewModel

@Composable
internal fun SetupScreen(state: TrackerState, vm: TrackerViewModel) {
    var name by rememberSaveable { mutableStateOf("") }
    var rpeDetails by rememberSaveable { mutableStateOf(false) }
    val rpe = state.setupRpe
    LazyColumn(
        Modifier.fillMaxSize().background(trackerScreenBackground()).testTag("setup-list"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        TrackerIcons.Workout,
                        null,
                        Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "WORKOUT TRACKER",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    "Welcome to your workout tracker",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    "Log workouts, resume unfinished sessions and see your progress. Your training stays on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            TextField(
                name,
                { name = it },
                label = { Text("Your name") },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
            )
            Text(
                "Up to 80 characters. You can change it later.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = trackerPanelColor()),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("setup-rpe")
                            .semantics { contentDescription = "Track RPE" }
                            .toggleable(
                                rpe,
                                enabled = !state.busy,
                                role = Role.Switch,
                                onValueChange = vm::setSetupRpe,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Track RPE", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Record how hard each set felt.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(rpe, null, enabled = !state.busy)
                    }
                    TextButton(onClick = { rpeDetails = !rpeDetails }) {
                        Text(if (rpeDetails) "Hide RPE explanation" else "What is RPE?")
                    }
                    if (rpeDetails)
                        Text(
                            "RPE records how hard a set felt: 8 means about two good reps left, 9 about one, and 10 no good reps left. You can leave it blank and change tracking later.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                }
            }
        }
        item {
            Button(
                onClick = { vm.saveProfile(name, rpe) },
                enabled = visibleName(name, 80) && !state.busy,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text("Let's begin")
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = trackerPanelColor()),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    BackupControls(state, vm, firstRun = true)
                }
            }
        }
    }
}
