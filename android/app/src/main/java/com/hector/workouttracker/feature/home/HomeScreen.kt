package com.hector.workouttracker.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hector.workouttracker.core.designsystem.*
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.core.schedule.*
import com.hector.workouttracker.feature.help.HelpTip
import com.hector.workouttracker.feature.session.TrackerState
import com.hector.workouttracker.feature.session.TrackerViewModel
import com.hector.workouttracker.feature.stats.StatsViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.*

@Composable
internal fun HomeScreen(
    state: TrackerState,
    vm: TrackerViewModel,
    progressVm: StatsViewModel,
    openRoutines: () -> Unit,
    openSchedule: () -> Unit,
    openSession: () -> Unit,
) {
    var opening by rememberSaveable { mutableStateOf(false) }
    var routinesOpen by rememberSaveable { mutableStateOf(false) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    val progressState by progressVm.state.collectAsStateWithLifecycle()
    val progress = progressState.snapshot
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(Unit) {
        val job =
            scope.launch {
                while (isActive) {
                    today = LocalDate.now()
                    progressVm.refreshDate()
                    delay(30_000)
                }
            }
        onPauseOrDispose { job.cancel() }
    }
    val plan = state.schedule?.let { resolveDayPlan(today, it) }
    val planned =
        if (plan?.kind == "gym") state.routines.filter { it.name == plan.routine }.singleOrNull()
        else null
    LaunchedEffect(state.draft, opening, state.busy, state.error) {
        if (opening && state.draft != null) {
            opening = false
            openSession()
        }
        if (opening && !state.busy && state.error != null) opening = false
    }
    LazyColumn(
        Modifier.fillMaxSize().testTag("home-list"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        when (LocalTime.now().hour) {
                            in 0..11 -> "Good morning,"
                            in 12..17 -> "Good afternoon,"
                            else -> "Good evening,"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        state.profile.displayName,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        today.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                progress?.let {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Column(
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("${it.streak}", style = MaterialTheme.typography.titleLarge)
                            Text("day streak", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("This week", style = MaterialTheme.typography.titleSmall)
                        Text(
                            progress?.let {
                                "${it.weekly.completedGymDays}/${it.weekly.gymDays} workouts"
                            } ?: "Loading your week…",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    progress?.let { WeekStrip(it.weekly.days, it.today) }
                }
            }
        }
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
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        if (state.draft != null) "WORKOUT IN PROGRESS"
                        else "${plan?.kind?.replaceFirstChar { it.uppercase() } ?: "Your"} day",
                        style = MaterialTheme.typography.labelMedium,
                        color = TrackerPalette.MutedNavy,
                    )
                    Text(
                        state.draft?.type ?: plan?.title ?: "Your next workout",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        if (state.draft != null)
                            "${state.draft.exercises.size} exercises · ${if(state.pending) "Saving your latest entries…" else "Draft saved on this device"}"
                        else plan?.note ?: "Start fresh or choose one of your routines.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TrackerPalette.MutedNavy,
                    )
                    if (state.draft == null && planned != null) {
                        Button(
                            onClick = {
                                opening = true
                                vm.startPlanned(
                                    planned,
                                    requireNotNull(state.schedule)[today.dayOfWeek.value % 7],
                                    today,
                                )
                            },
                            enabled = !state.busy,
                            modifier =
                                Modifier.fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .testTag("start-planned-workout"),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = TrackerPalette.Coral,
                                    contentColor = TrackerPalette.OnCoral,
                                ),
                        ) {
                            Icon(TrackerIcons.Workout, null, Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Start planned routine")
                        }
                    }
                    Button(
                        onClick = {
                            if (state.draft != null) openSession()
                            else {
                                opening = true
                                vm.start()
                            }
                        },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor =
                                    if (planned != null && state.draft == null)
                                        TrackerPalette.OnNavy.copy(alpha = .12f)
                                    else TrackerPalette.Coral,
                                contentColor =
                                    if (planned != null && state.draft == null)
                                        TrackerPalette.OnNavy
                                    else TrackerPalette.OnCoral,
                            ),
                    ) {
                        Icon(
                            if (state.draft != null) TrackerIcons.Chevron else TrackerIcons.Add,
                            null,
                            Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(if (state.draft != null) "Resume workout" else "Start workout")
                    }
                    if (state.draft == null && state.routines.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { routinesOpen = true },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            colors =
                                ButtonDefaults.outlinedButtonColors(
                                    contentColor = TrackerPalette.OnNavy
                                ),
                        ) {
                            Text("Use saved routine")
                        }
                    }
                }
            }
        }
        state.completion?.let { summary ->
            item {
                Card(
                    Modifier.fillMaxWidth().testTag("workout-completion"),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "${state.completedName} saved",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        summary.highlights.forEach {
                            Text(it.title, style = MaterialTheme.typography.titleMedium)
                            Text(it.text)
                        }
                        TextButton(onClick = vm::dismissCompletion) { Text("Dismiss summary") }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile("${state.workouts.size}", "Saved workouts", Modifier.weight(1f))
                MetricTile("${state.routines.size}", "Routines", Modifier.weight(1f))
            }
        }
        item {
            ActionRow(
                "Manage routines",
                "Your exercises, your order",
                TrackerIcons.Workout,
                enabled = !state.busy,
                onClick = openRoutines,
            )
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeading("Today's plan", plan?.title)
                    if (plan != null) {
                        Text(
                            plan.note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (plan.kind == "gym" && planned == null)
                            Text(
                                "Choose a saved routine or start a fresh workout.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                    } else
                        Text(
                            if (state.scheduleLoading) "Loading today's plan…"
                            else "Your plan is unavailable. You can still start a workout."
                        )
                    TextButton(onClick = openSchedule, enabled = !state.busy) {
                        Icon(TrackerIcons.Calendar, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Edit schedule")
                    }
                }
            }
        }
        item { HelpTip("homeTour") }
    }
    if (routinesOpen)
        AlertDialog(
            onDismissRequest = { routinesOpen = false },
            title = { Text("Choose a routine") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.routines, key = { it.record.idKey }) { routine ->
                        OutlinedButton(
                            onClick = {
                                routinesOpen = false
                                opening = true
                                vm.start(routine)
                            },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text(routine.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${routine.exercises.size} exercises",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { routinesOpen = false }) { Text("Cancel") } },
        )
}
