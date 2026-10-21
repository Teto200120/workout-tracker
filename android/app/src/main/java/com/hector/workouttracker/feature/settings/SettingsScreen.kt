package com.hector.workouttracker.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hector.workouttracker.BuildConfig
import com.hector.workouttracker.core.designsystem.Heading
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.feature.session.TrackerState
import com.hector.workouttracker.feature.session.TrackerViewModel

@Composable
internal fun SettingsScreen(state: TrackerState, vm: TrackerViewModel) {
    var name by
        rememberSaveable(state.profile.displayName) { mutableStateOf(state.profile.displayName) }
    var rpe by rememberSaveable(state.profile.trackRpe) { mutableStateOf(state.profile.trackRpe) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { Heading("Make it yours", "You") }
        item {
            OutlinedTextField(
                name,
                { name = it },
                label = { Text("Your name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Track RPE", Modifier.weight(1f))
                Switch(rpe, { rpe = it }, enabled = !state.busy)
            }
        }
        item {
            Button(
                onClick = { vm.saveProfile(name, rpe) },
                enabled =
                    visibleName(name, 80) &&
                        !state.busy &&
                        (name.trim() != state.profile.displayName || rpe != state.profile.trackRpe),
            ) {
                Text("Save preferences")
            }
        }
        item { HorizontalDivider() }
        item {
            Text(
                "Workout Tracker ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        item {
            Text(
                "Native Android foundation. Workouts and drafts are stored locally. PWA import/export, routine planning and full analytics are not available in this build."
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
