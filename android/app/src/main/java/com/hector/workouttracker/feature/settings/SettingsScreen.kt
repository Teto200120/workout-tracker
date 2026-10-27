package com.hector.workouttracker.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hector.workouttracker.BuildConfig
import com.hector.workouttracker.core.designsystem.*
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.data.DataAction
import com.hector.workouttracker.feature.backup.BackupControls
import com.hector.workouttracker.feature.session.TrackerState
import com.hector.workouttracker.feature.session.TrackerViewModel

@Composable
internal fun SettingsScreen(state: TrackerState, vm: TrackerViewModel, back: () -> Unit) {
    state.dataActionReview?.let {
        DataActionDialog(it, vm::cancelDataAction, vm::confirmDataAction)
    }
    LazyColumn(
        Modifier.fillMaxSize().background(trackerScreenBackground()).testTag("settings-list"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TextButton(onClick = back, modifier = Modifier.testTag("data-back")) { Text("Back") }
            TrainingHeading("Backup & data", icon = TrackerIcons.Backup, hero = true)
        }
        item { SettingsPanel { BackupControls(state, vm) } }
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
internal fun SettingsToolGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = trackerPanelColor()),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(content = content)
    }
}

@Composable
internal fun SettingsTool(
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
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    icon,
                    null,
                    Modifier.padding(8.dp).size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
        trailingContent = { Icon(TrackerIcons.Chevron, null, Modifier.size(18.dp)) },
        colors = ListItemDefaults.colors(containerColor = trackerPanelColor()),
        modifier = modifier.clickable(enabled = enabled, onClick = onClick).heightIn(min = 72.dp),
    )
}
