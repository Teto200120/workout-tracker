package com.hector.workouttracker.feature.stats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hector.workouttracker.core.designsystem.*
import com.hector.workouttracker.domain.*
import com.hector.workouttracker.feature.help.HelpTip
import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.serialization.json.jsonPrimitive

private fun decimal(value: Double) = String.format(Locale.US, "%.1f", value)

private fun label(metric: String) =
    when (metric) {
        "bestWeight" -> "Best load"
        "bestVolume" -> "Best set volume"
        else -> "Estimated 1RM"
    }

private fun unit(metric: String) = if (metric == "bestVolume") "lb × reps" else "lb"

@Composable
internal fun StatsScreen(vm: StatsViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(Unit) {
        val job =
            scope.launch {
                while (isActive) {
                    vm.refreshDate()
                    delay(30000)
                }
            }
        onPauseOrDispose { job.cancel() }
    }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var metric by rememberSaveable { mutableStateOf("bestWeight") }
    var query by rememberSaveable { mutableStateOf("") }
    var allInsights by rememberSaveable { mutableStateOf(false) }
    var weekDetails by rememberSaveable { mutableStateOf(false) }
    BackHandler(selected != null) { selected = null }
    val list = rememberLazyListState()
    LaunchedEffect(selected) { list.scrollToItem(0) }
    val snapshot = state.snapshot
    val exercise = snapshot?.exercises?.firstOrNull { it.name == selected }
    LazyColumn(
        Modifier.fillMaxSize().testTag("stats-list"),
        state = list,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            if (selected != null)
                TextButton(onClick = { selected = null }) { Text("Back to Stats") }
            Heading(
                if (selected == null) "Your progress" else "Exercise progress",
                selected ?: "Stats",
                "Saved workouts only. Your active session is not included.",
            )
        }
        if (state.loading) item { CircularProgressIndicator() }
        state.error?.let { message ->
            item {
                Text(message)
                TextButton(onClick = vm::retry) { Text("Retry") }
            }
        }
        if (snapshot != null && selected == null) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("All time", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${snapshot.totals.workouts} workouts · ${snapshot.totals.sets} working sets",
                            modifier = Modifier.testTag("stats-totals"),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricTile(
                                "${snapshot.totals.workouts}",
                                "Workouts",
                                Modifier.weight(1f),
                            )
                            MetricTile(
                                "${snapshot.totals.sets}",
                                "Working sets",
                                Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricTile(
                                decimal(snapshot.totals.volume),
                                "Volume · lb × reps",
                                Modifier.weight(1f),
                            )
                            MetricTile("${snapshot.streak}", "Streak · days", Modifier.weight(1f))
                        }
                        Text(
                            "Average duration: ${snapshot.totals.avgDuration} min",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Average uses sessions with a nonzero duration.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item { HelpTip("statsBasics") }
            item {
                Text(
                    "Strength snapshot",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.testTag("stats-strength-heading"),
                )
                Text(
                    "Recent PRs, rising trends and changes against the previous workout of the same type."
                )
                if (snapshot.insights.isEmpty()) {
                    Text("Keep logging sessions", style = MaterialTheme.typography.titleMedium)
                    Text("Strength trends will appear here once you have a few sessions logged.")
                }
            }
            items(
                if (allInsights) snapshot.insights else snapshot.insights.take(3),
                key = { "insight:${it.type}:${it.title}" },
            ) { insight ->
                OutlinedCard(
                    Modifier.fillMaxWidth()
                        .testTag("stats-insight-${insight.type}-${insight.title}")
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(insight.title, style = MaterialTheme.typography.titleMedium)
                        Text(insight.meta)
                    }
                }
            }
            if (snapshot.insights.size > 3)
                item {
                    TextButton(
                        onClick = { allInsights = !allInsights },
                        modifier = Modifier.testTag("stats-insights-toggle"),
                    ) {
                        Text(if (allInsights) "Show fewer insights" else "View all insights")
                    }
                }
            item {
                Text(
                    "Recent sessions",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.testTag("stats-recent-heading"),
                )
                Text("The three newest saved workouts. Full details are in History.")
                if (snapshot.recentSessions.isEmpty())
                    Text("No sessions yet. Logged workouts will appear here.")
            }
            items(snapshot.recentSessions, key = { "recent:${it.id}" }) { session ->
                OutlinedCard(Modifier.fillMaxWidth().testTag("stats-recent-${session.id}")) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(session.type, style = MaterialTheme.typography.titleMedium)
                        Text("${session.date} · ${session.exercises} exercises")
                        Text(
                            if (session.duration == 0.0) "Duration: —"
                            else "${progressNumber(session.duration)} min"
                        )
                    }
                }
            }
            item {
                Text("Weekly goal", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${snapshot.recent} / ${snapshot.goal} workouts in the last 7 days",
                    modifier = Modifier.testTag("stats-goal"),
                )
                LinearProgressIndicator(
                    progress = {
                        (snapshot.recent.toFloat() / snapshot.goal.coerceAtLeast(1)).coerceIn(
                            0f,
                            1f,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Includes today. Multiple workouts on one date count separately.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item {
                Text("This week · Monday–Sunday", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${snapshot.weekly.completedGymDays} / ${snapshot.weekly.gymDays} planned gym days logged"
                )
            }
            item {
                WeekStrip(snapshot.weekly.days, snapshot.today)
                TextButton(onClick = { weekDetails = !weekDetails }) {
                    Text(if (weekDetails) "Hide weekly details" else "Weekly details")
                }
            }
            if (weekDetails) {
                items(snapshot.weekly.days, key = { "day:${it.key}" }) { day ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "${day.key}${if(day.key == snapshot.today) " · Today" else ""}",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text("${day.status} · ${day.title}")
                        }
                    }
                }
            }
            item {
                Text("Personal records", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Warm-ups are excluded. Loaded sets with positive weight and reps qualify even when not marked completed. Estimated 1RM uses the Epley formula and is an estimate.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    query,
                    { query = it },
                    label = { Text("Find an exercise") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("stats-search"),
                )
            }
            val records =
                snapshot.exercises
                    .filter {
                        it.bestEstimated1rm > 0 && it.name.contains(query.trim(), ignoreCase = true)
                    }
                    .sortedByDescending { it.bestEstimated1rm }
            if (records.isEmpty())
                item {
                    Text(
                        if (query.isEmpty())
                            "No personal records yet. Log loaded sets and reps to see progress."
                        else "No matching records."
                    )
                }
            items(records, key = { "record:${it.name}" }) { record ->
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(record.name, style = MaterialTheme.typography.titleLarge)
                        Text("${record.sessions} sessions · ${record.sets} working sets")
                        Text(
                            "Best estimated 1RM: ${decimal(record.bestEstimated1rm)} lb · ${record.bestDate}"
                        )
                        TextButton(
                            onClick = { selected = record.name },
                            modifier = Modifier.testTag("stats-exercise-${record.name}"),
                        ) {
                            Text("View progress")
                        }
                    }
                }
            }
        } else if (snapshot != null && selected != null) {
            if (exercise == null || exercise.history.isEmpty())
                item { Text("This exercise no longer has loaded sets in saved History.") }
            else {
                item {
                    Text("${exercise.sessions} sessions · ${exercise.sets} working sets")
                    Text(
                        "Saved spellings are kept separate. Repeated exercise rows remain separate progress entries.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                for (key in listOf("bestWeight", "bestVolume", "estimated1rm")) {
                    val record = requireNotNull(exercise.records.value(key))
                    item(key = "best:$key") {
                        OutlinedCard(Modifier.fillMaxWidth().testTag("stats-record-$key")) {
                            Column(Modifier.padding(16.dp)) {
                                val value =
                                    when (key) {
                                        "bestWeight" -> record.weight
                                        "bestVolume" -> record.volume
                                        else -> record.estimated1rm
                                    }
                                Text(
                                    "${label(key)}: ${decimal(value)} ${unit(key)}",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    "${progressNumber(record.weight)} lb × ${progressNumber(record.reps)} reps · ${record.date}"
                                )
                                Text("RPE: ${record.rpe.jsonPrimitive.content.ifEmpty { "—" }}")
                            }
                        }
                    }
                }
                item {
                    Text("Recent progress", style = MaterialTheme.typography.headlineSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (key in listOf("bestWeight", "bestVolume", "estimated1rm")) FilterChip(
                            metric == key,
                            { metric = key },
                            label = { Text(label(key)) },
                            modifier = Modifier.testTag("stats-metric-$key"),
                        )
                    }
                }
                val recent = exercise.history.sortedBy { it.date }.takeLast(7)
                val max = recent.maxOf { it.value(metric) }.coerceAtLeast(1.0)
                item {
                    val delta = recent.last().value(metric) - recent.first().value(metric)
                    Text(
                        if (recent.size < 2) "Log another entry to see a trend."
                        else
                            "${if(delta >= 0) "+" else ""}${decimal(delta)} ${unit(metric)} over ${recent.size} entries"
                    )
                    Text(
                        "Bars compare the last ${recent.size} entries with the largest value shown.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                items(recent.withIndex().toList(), key = { "point:${it.index}" }) { (_, point) ->
                    val record = requireNotNull(point.records.value(metric))
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "${point.date} · ${point.type}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "${label(metric)}: ${decimal(point.value(metric))} ${unit(metric)}"
                            )
                            Text(
                                "${progressNumber(record.weight)} lb × ${progressNumber(record.reps)} reps"
                            )
                            LinearProgressIndicator(
                                progress = {
                                    (point.value(metric) / max).toFloat().coerceIn(0f, 1f)
                                },
                                modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
                            )
                        }
                    }
                }
            }
        }
    }
}
