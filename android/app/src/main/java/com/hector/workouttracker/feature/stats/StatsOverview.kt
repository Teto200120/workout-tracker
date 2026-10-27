package com.hector.workouttracker.feature.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.hector.workouttracker.core.designsystem.*
import com.hector.workouttracker.data.StatsSnapshot
import com.hector.workouttracker.domain.progressNumber
import java.util.Locale

@Composable
internal fun StatsWeekSummary(snapshot: StatsSnapshot, details: () -> Unit) {
    TrainingHero(Modifier.testTag("stats-week-summary")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Weekly goal", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${snapshot.recent} / ${snapshot.goal}",
                        Modifier.testTag("stats-goal"),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "workouts · last 7 days",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = details, modifier = Modifier.testTag("stats-week-details")) {
                    Text("Weekly details")
                }
            }
            LinearProgressIndicator(
                progress = {
                    (snapshot.recent.toFloat() / snapshot.goal.coerceAtLeast(1)).coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "${snapshot.weekly.completedGymDays} / ${snapshot.weekly.gymDays} planned gym days · Mon–Sun",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                snapshot.weekly.days.forEach { day ->
                    val current = day.key == snapshot.today
                    Surface(
                        Modifier.weight(1f).clearAndSetSemantics {
                            contentDescription =
                                "${day.key}, ${day.title}, ${day.status}${if (current) ", Today" else ""}"
                        },
                        shape = RoundedCornerShape(12.dp),
                        color =
                            if (day.complete) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                        border =
                            if (current) BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            else null,
                    ) {
                        Column(
                            Modifier.padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(day.label.take(1), style = MaterialTheme.typography.labelMedium)
                            if (day.complete)
                                Icon(
                                    TrackerIcons.Check,
                                    null,
                                    Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            else
                                Text(
                                    day.key.takeLast(2),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun StatsLifetimeSummary(snapshot: StatsSnapshot, about: () -> Unit) {
    StatsPanel(Modifier.testTag("stats-lifetime-summary")) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("All time", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(
                "${snapshot.totals.workouts} ${if (snapshot.totals.workouts == 1) "workout" else "workouts"}",
                Modifier.testTag("stats-totals"),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(
                onClick = about,
                modifier = Modifier.size(48.dp).testTag("stats-number-info"),
            ) {
                Icon(TrackerIcons.Help, "How these numbers work", Modifier.size(18.dp))
            }
        }
        val values =
            listOf(
                snapshot.totals.sets.toString() to "Working sets",
                String.format(Locale.US, "%.1f", snapshot.totals.volume) to "Volume · lb × reps",
                snapshot.streak.toString() to "Streak · days",
            )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            values.chunked(if (LocalDensity.current.fontScale > 1.3f) 2 else 3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (value, label) ->
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(value, style = MaterialTheme.typography.titleLarge)
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
        Text(
            "Avg. duration ${snapshot.totals.avgDuration} min",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun StatsStrengthPreview(snapshot: StatsSnapshot, open: () -> Unit) {
    Surface(
        onClick = open,
        modifier = Modifier.fillMaxWidth().testTag("stats-open-insights"),
        shape = RoundedCornerShape(20.dp),
        color = trackerPanelColor(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Strength snapshot",
                    Modifier.weight(1f).testTag("stats-strength-heading"),
                    style = MaterialTheme.typography.titleSmall,
                )
                Icon(TrackerIcons.Chevron, "View training insights", Modifier.size(18.dp))
            }
            val insight = snapshot.insights.firstOrNull()
            Text(
                insight?.title ?: "No trends yet. Keep logging sessions.",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            insight?.let {
                Text(
                    it.meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun StatsLatestSession(snapshot: StatsSnapshot, open: () -> Unit) {
    val session = snapshot.recentSessions.firstOrNull() ?: return
    Surface(
        onClick = open,
        modifier = Modifier.fillMaxWidth().testTag("stats-latest-session"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                TrackerIcons.History,
                null,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Latest session · ${session.date}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    session.type,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${session.exercises} exercises · ${if (session.duration == 0.0) "Duration: —" else "${progressNumber(session.duration)} min"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(TrackerIcons.Chevron, "Open session history", Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun StatsWeekDialog(snapshot: StatsSnapshot, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 560.dp).fillMaxWidth(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Your week") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).testTag("stats-week-dialog"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("${snapshot.recent} / ${snapshot.goal} workouts in the last 7 days")
                Text(
                    "Includes today. Multiple workouts on one date count separately.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("This week · Monday–Sunday", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${snapshot.weekly.completedGymDays} / ${snapshot.weekly.gymDays} planned gym days logged"
                )
                snapshot.weekly.days.forEach { day ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "${day.key}${if (day.key == snapshot.today) " · Today" else ""}",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text("${day.status} · ${day.title}")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Done") } },
    )
}
