package com.hector.workouttracker.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
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
    val large = LocalDensity.current.fontScale > 1.3f
    LazyColumn(
        Modifier.fillMaxSize().background(trackerScreenBackground()).testTag("profile-list"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            TrainingHero {
                Column(
                    Modifier.fillMaxWidth().padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        TrainingEmblem(size = 76.dp) {
                            Text(
                                String(
                                    Character.toChars(
                                        state.profile.displayName
                                            .uppercase()
                                            .codePoints()
                                            .findFirst()
                                            .orElse(63)
                                    )
                                ),
                                style = MaterialTheme.typography.headlineMedium,
                            )
                        }
                        FilledTonalIconButton(
                            onClick = settings,
                            modifier = Modifier.size(48.dp).testTag("open-settings"),
                            shape = CircleShape,
                            colors =
                                IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                        ) {
                            Icon(TrackerIcons.Settings, "Open settings", Modifier.size(22.dp))
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Training profile",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            state.profile.displayName,
                            style = MaterialTheme.typography.displaySmall,
                        )
                    }
                }
            }
        }
        item {
            Surface(
                Modifier.fillMaxWidth().testTag("profile-goal"),
                shape = RoundedCornerShape(28.dp),
                color = trackerPanelColor(),
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
            ) {
                BoxWithConstraints(Modifier.padding(20.dp)) {
                    val stacked = large || maxWidth < 280.dp
                    val summary: @Composable () -> Unit = {
                        Column(
                            Modifier.semantics(mergeDescendants = true) {},
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("Training goal", style = MaterialTheme.typography.titleMedium)
                            Text(
                                snapshot?.let { "${it.recent} / ${it.goal}" } ?: "—",
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "workouts in the last 7 days",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                snapshot?.let { "${it.goal} workouts / week" }
                                    ?: "Loading progress",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    if (stacked) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            summary()
                            TrainingGoalRing(snapshot?.recent ?: 0, snapshot?.goal ?: 1)
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Box(Modifier.weight(1f)) { summary() }
                            TrainingGoalRing(snapshot?.recent ?: 0, snapshot?.goal ?: 1)
                        }
                    }
                }
            }
        }
        item {
            Surface(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border =
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f)),
            ) {
                Row(
                    Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        state.workouts.size.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Logged sessions", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Saved on this device",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your essentials", style = MaterialTheme.typography.titleMedium)
                ActionRow(
                    "Routines",
                    "${state.routines.size} saved ${if (state.routines.size == 1) "routine" else "routines"}",
                    TrackerIcons.Workout,
                    modifier = Modifier.testTag("profile-routines"),
                    onClick = routines,
                )
                ActionRow(
                    "Backup & data",
                    "Keep a copy of your training",
                    TrackerIcons.Backup,
                    modifier = Modifier.testTag("profile-backup"),
                    onClick = backup,
                )
            }
        }
        item {
            Text(
                "Workout Tracker ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
