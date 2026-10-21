package com.hector.workouttracker.feature.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hector.workouttracker.core.designsystem.Heading
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.feature.session.TrackerState
import com.hector.workouttracker.feature.session.TrackerViewModel

@Composable
internal fun SetupScreen(state: TrackerState, vm: TrackerViewModel) {
    var name by rememberSaveable { mutableStateOf("") }
    var rpe by rememberSaveable { mutableStateOf(true) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Heading(
                "Workout Tracker",
                "Make room for progress.",
                "Your workouts stay on this device. Start with a name and make it yours.",
            )
        }
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
            Text(
                "RPE records how hard a set felt. You can change this later.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Button(
                onClick = { vm.saveProfile(name, rpe) },
                enabled = visibleName(name, 80) && !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text("Let's begin")
            }
        }
        item {
            Text(
                "This is the first native version. PWA backup import is coming in the next stage; your existing PWA data stays where it is.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
