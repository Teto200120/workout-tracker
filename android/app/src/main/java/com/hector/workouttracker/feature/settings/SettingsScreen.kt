package com.hector.workouttracker.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hector.workouttracker.BuildConfig
import com.hector.workouttracker.core.designsystem.*
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.data.DataAction
import com.hector.workouttracker.feature.backup.BackupControls
import com.hector.workouttracker.feature.session.TrackerState
import com.hector.workouttracker.feature.session.TrackerViewModel
import com.hector.workouttracker.feature.stats.StatsViewModel

@Composable
internal fun SettingsScreen(
    state: TrackerState,
    vm: TrackerViewModel,
    progressVm: StatsViewModel,
    openSchedule: () -> Unit,
    openPreferences: () -> Unit,
    openHelp: () -> Unit,
    openFeedback: () -> Unit,
) {
    val progressState by progressVm.state.collectAsStateWithLifecycle()
    val progress = progressState.snapshot
    state.dataActionReview?.let {
        DataActionDialog(it, vm::cancelDataAction, vm::confirmDataAction)
    }
    LazyColumn(
        Modifier.fillMaxSize().testTag("settings-list"),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors =
                    CardDefaults.cardColors(
                        containerColor = TrackerPalette.Navy,
                        contentColor = TrackerPalette.OnNavy,
                    ),
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = TrackerPalette.Coral,
                            contentColor = TrackerPalette.OnCoral,
                        ) {
                            Text(
                                String(
                                        Character.toChars(
                                            state.profile.displayName
                                                .codePoints()
                                                .findFirst()
                                                .orElse(63)
                                        )
                                    )
                                    .uppercase(),
                                Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.headlineMedium,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                state.profile.displayName,
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            Text(
                                if (state.profile.trackRpe) "RPE tracking on"
                                else "RPE tracking off",
                                style = MaterialTheme.typography.bodySmall,
                                color = TrackerPalette.MutedNavy,
                            )
                        }
                    }
                    Text(
                        "${state.workouts.size} saved sessions · ${state.routines.size} routines",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TrackerPalette.MutedNavy,
                    )
                    progress?.let {
                        Text(
                            "${it.recent} / ${it.goal} workouts in the last 7 days",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        LinearProgressIndicator(
                            progress = {
                                (it.recent.toFloat() / it.goal.coerceAtLeast(1)).coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = TrackerPalette.Coral,
                            trackColor = TrackerPalette.OnNavy.copy(alpha = .15f),
                        )
                    }
                }
            }
        }
        item { SectionHeading("Your training", "Preferences, plans and support.") }
        item {
            ActionRow(
                "Edit preferences & goal",
                "Training effort, targets and app feel",
                TrackerIcons.Tune,
                enabled = !state.busy,
                onClick = openPreferences,
            )
        }
        item {
            ActionRow(
                "Edit weekly schedule",
                "Your gym, soccer and recovery days",
                TrackerIcons.Calendar,
                enabled = !state.busy,
                onClick = openSchedule,
            )
        }
        item {
            ActionRow(
                "Help & guidance",
                "Training tips, available offline",
                TrackerIcons.Help,
                modifier = Modifier.testTag("open-help"),
                onClick = openHelp,
            )
        }
        item {
            ActionRow(
                "Beta feedback",
                "Share feedback in your browser",
                TrackerIcons.Feedback,
                modifier = Modifier.testTag("open-feedback"),
                onClick = openFeedback,
            )
        }
        item { BackupControls(state, vm) }
        item {
            Text("Manage local data", style = MaterialTheme.typography.titleLarge)
            Text(
                "Reset settings keeps your saved records. Clear local data starts over on this device."
            )
            state.maintenanceMessage?.let { Text(it, Modifier.testTag("maintenance-message")) }
            if (state.pending)
                Text("Wait for your workout entries to finish saving before continuing.")
            OutlinedButton(
                onClick = { vm.reviewDataAction(DataAction.RESET_SETTINGS) },
                enabled = !state.busy && !state.pending && state.importPreview == null,
                modifier = Modifier.fillMaxWidth().testTag("review-reset-settings"),
            ) {
                Text("Reset app settings")
            }
            OutlinedButton(
                onClick = { vm.reviewDataAction(DataAction.CLEAR_DATA) },
                enabled = !state.busy && !state.pending && state.importPreview == null,
                modifier = Modifier.fillMaxWidth().testTag("review-clear-data"),
            ) {
                Text("Clear local data")
            }
        }
        item {
            Text(
                "Workout Tracker ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        item {
            Text(
                "Your workouts stay on this device. Export a backup to keep a separate copy.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
