package com.hector.workouttracker.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
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
    openRoutines: () -> Unit,
) {
    val progressState by progressVm.state.collectAsStateWithLifecycle()
    val progress = progressState.snapshot
    state.dataActionReview?.let {
        DataActionDialog(it, vm::cancelDataAction, vm::confirmDataAction)
    }
    LazyColumn(
        Modifier.fillMaxSize().background(trackerScreenBackground()).testTag("settings-list"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SettingsPanel {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
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
                            Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            state.profile.displayName,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            "Your training, on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val goal = progress?.goal?.toString() ?: "—"
                val metrics =
                    listOf(
                        "$goal / week" to "Workout goal",
                        "${state.workouts.size}" to "Saved sessions",
                    )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    metrics.chunked(if (LocalDensity.current.fontScale > 1.3f) 1 else 2).forEach {
                        row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { (value, label) ->
                                Surface(
                                    Modifier.weight(1f).semantics(mergeDescendants = true) {},
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                ) {
                                    Column(
                                        Modifier.padding(16.dp).heightIn(min = 64.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(value, style = MaterialTheme.typography.headlineSmall)
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                progress?.let {
                    Text(
                        "${it.recent} / ${it.goal} workouts in the last 7 days",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    LinearProgressIndicator(
                        progress = {
                            (it.recent.toFloat() / it.goal.coerceAtLeast(1)).coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    if (state.profile.trackRpe) "RPE tracking on" else "RPE tracking off",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Text("Your training", style = MaterialTheme.typography.titleLarge)
            Text(
                "Routines, preferences and your weekly plan.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            SettingsToolGroup {
                SettingsTool(
                    "Routines",
                    "${state.routines.size} saved routines",
                    TrackerIcons.Workout,
                    enabled = !state.busy,
                    onClick = openRoutines,
                )
                HorizontalDivider(
                    Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                SettingsTool(
                    "Edit preferences & goal",
                    "RPE, targets and app feel",
                    TrackerIcons.Tune,
                    enabled = !state.busy,
                    onClick = openPreferences,
                )
                HorizontalDivider(
                    Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                SettingsTool(
                    "Edit weekly schedule",
                    "Gym, soccer and recovery days",
                    TrackerIcons.Calendar,
                    enabled = !state.busy,
                    onClick = openSchedule,
                )
            }
        }
        item { SettingsPanel { BackupControls(state, vm) } }
        item {
            Text("Support", style = MaterialTheme.typography.titleLarge)
            SettingsToolGroup {
                SettingsTool(
                    "Help & guidance",
                    "Training tips, available offline",
                    TrackerIcons.Help,
                    Modifier.testTag("open-help"),
                    onClick = openHelp,
                )
                HorizontalDivider(
                    Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                SettingsTool(
                    "Beta feedback",
                    "Share feedback in your browser",
                    TrackerIcons.Feedback,
                    Modifier.testTag("open-feedback"),
                    onClick = openFeedback,
                )
            }
        }
        item {
            SettingsPanel {
                Text("Manage local data", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Reset settings keeps your saved records. Clear local data starts over on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.maintenanceMessage?.let { Text(it, Modifier.testTag("maintenance-message")) }
                if (state.pending)
                    Text("Wait for your workout entries to finish saving before continuing.")
                OutlinedButton(
                    onClick = { vm.reviewDataAction(DataAction.RESET_SETTINGS) },
                    enabled = !state.busy && !state.pending && state.importPreview == null,
                    modifier = Modifier.fillMaxWidth().testTag("review-reset-settings"),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Reset app settings")
                }
                TextButton(
                    onClick = { vm.reviewDataAction(DataAction.CLEAR_DATA) },
                    enabled = !state.busy && !state.pending && state.importPreview == null,
                    modifier = Modifier.fillMaxWidth().testTag("review-clear-data"),
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                ) {
                    Text("Clear local data")
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Workout Tracker ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    "Your workouts stay on this device. Export a backup to keep a separate copy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun SettingsPanel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = trackerPanelColor()),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
private fun SettingsToolGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = trackerPanelColor()),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsTool(
    label: String,
    detail: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(detail) },
        leadingContent = {
            Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        },
        trailingContent = { Icon(TrackerIcons.Chevron, null, Modifier.size(18.dp)) },
        colors = ListItemDefaults.colors(containerColor = trackerPanelColor()),
        modifier = modifier.clickable(enabled = enabled, onClick = onClick).heightIn(min = 72.dp),
    )
}
