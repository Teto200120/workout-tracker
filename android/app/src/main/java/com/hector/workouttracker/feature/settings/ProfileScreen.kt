package com.hector.workouttracker.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hector.workouttracker.BuildConfig
import com.hector.workouttracker.core.designsystem.*
import com.hector.workouttracker.feature.session.TrackerState
import com.hector.workouttracker.feature.stats.StatsViewModel

@Composable
internal fun ProfileScreen(
    state: TrackerState,
    stats: StatsViewModel,
    settings: () -> Unit,
    routines: () -> Unit,
    backup: () -> Unit,
) {
    val progress by stats.state.collectAsStateWithLifecycle()
    val snapshot = progress.snapshot
    LazyColumn(
        Modifier.fillMaxSize().background(trackerScreenBackground()).testTag("profile-list"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = trackerPanelColor()),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.headlineMedium,
                            )
                        }
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                state.profile.displayName,
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            Text(
                                "Training profile",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = settings,
                            modifier = Modifier.testTag("open-settings"),
                        ) {
                            Icon(TrackerIcons.Settings, "Open settings", Modifier.size(22.dp))
                        }
                    }
                    ProfileMetric(
                        "Training goal",
                        snapshot?.let { "${it.goal} workouts / week" } ?: "—",
                        snapshot?.let { "${it.recent} logged in the last 7 days" }
                            ?: "Loading progress",
                        true,
                    )
                    ProfileMetric(
                        "Logged sessions",
                        state.workouts.size.toString(),
                        "Saved on this device",
                    )
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    "Weekly goal progress",
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    snapshot?.let { "${it.recent}/${it.goal}" } ?: "—",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                            LinearProgressIndicator(
                                progress = {
                                    snapshot?.let {
                                        (it.recent.toFloat() / it.goal.coerceAtLeast(1)).coerceIn(
                                            0f,
                                            1f,
                                        )
                                    } ?: 0f
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
        item {
            SettingsToolGroup {
                SettingsTool(
                    "Routines",
                    "${state.routines.size} saved ${if(state.routines.size == 1) "routine" else "routines"}",
                    TrackerIcons.Workout,
                    Modifier.testTag("profile-routines"),
                    onClick = routines,
                )
                HorizontalDivider(
                    Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                SettingsTool(
                    "Backup & data",
                    "Keep a copy of your training",
                    TrackerIcons.Backup,
                    Modifier.testTag("profile-backup"),
                    onClick = backup,
                )
            }
        }
        item {
            Text(
                "Workout Tracker ${BuildConfig.VERSION_NAME}",
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProfileMetric(label: String, value: String, detail: String, accent: Boolean = false) {
    Surface(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border =
            if (accent) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .45f))
            else null,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
