package com.hector.workouttracker.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hector.workouttracker.BuildConfig
import com.hector.workouttracker.core.designsystem.Heading
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.feature.backup.BackupControls
import com.hector.workouttracker.feature.session.TrackerState
import com.hector.workouttracker.feature.session.TrackerViewModel

@Composable
internal fun SettingsScreen(
    state: TrackerState,
    vm: TrackerViewModel,
    openSchedule: () -> Unit,
    openPreferences: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().testTag("settings-list"),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { Heading("Make it yours", "You") }
        item {
            Text(state.profile.displayName, style = MaterialTheme.typography.titleLarge)
            Text(if (state.profile.trackRpe) "RPE tracking on" else "RPE tracking off")
            Button(
                onClick = openPreferences,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Edit preferences & goal")
            }
        }
        item { HorizontalDivider() }
        item {
            OutlinedButton(
                onClick = openSchedule,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Edit weekly schedule")
            }
        }
        item { BackupControls(state, vm) }
        item {
            Text(
                "Workout Tracker ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        item {
            Text(
                "Native Android preview. Workouts, routines, weekly plans, History, Stats and preferences work offline. Native progression suggestions, feedback and final release checks are still being built."
            )
        }
        item {
            Text(
                "Keep your PWA and its backup while the native app is being built. Do not uninstall this app to update it; use an APK with the same signing identity.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
