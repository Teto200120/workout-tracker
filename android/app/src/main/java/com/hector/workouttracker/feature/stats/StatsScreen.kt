package com.hector.workouttracker.feature.stats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hector.workouttracker.R
import com.hector.workouttracker.core.designsystem.*
import com.hector.workouttracker.domain.*
import com.hector.workouttracker.feature.help.CompactHelpTip
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
internal fun StatsScreen(vm: StatsViewModel = viewModel(), onHistory: () -> Unit = {}) {
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
        Modifier.fillMaxSize().background(trackerScreenBackground()).testTag("stats-list"),
        state = list,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            if (selected != null)
                TextButton(onClick = { selected = null }) { Text("Back to Stats") }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(selected ?: "Your stats", style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (selected == null) "A clear view of your training."
                    else "Exercise progress · saved workouts",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
                TrainingHero {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("All time", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${snapshot.totals.workouts} workouts · ${snapshot.totals.sets} working sets",
                            modifier = Modifier.testTag("stats-totals"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        StatsMetrics(
                            listOf(
                                "${snapshot.totals.workouts}" to "Workouts",
                                "${snapshot.totals.sets}" to "Working sets",
                                decimal(snapshot.totals.volume) to "Volume · lb × reps",
                                "${snapshot.streak}" to "Streak · days",
                            )
                        )
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
            item {
                OutlinedButton(
                    onClick = onHistory,
                    modifier = Modifier.fillMaxWidth().testTag("open-history"),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_history), null, Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Session history", Modifier.weight(1f))
                    Icon(TrackerIcons.Chevron, null, Modifier.size(18.dp))
                }
            }
            item {
                StatsPanel {
                    Text("Weekly activity", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "This week · Monday–Sunday",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${snapshot.weekly.completedGymDays} / ${snapshot.weekly.gymDays} planned gym days logged",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    StatsWeek(snapshot.weekly.days, snapshot.today)
                    TextButton(onClick = { weekDetails = !weekDetails }) {
                        Text(if (weekDetails) "Hide weekly details" else "Weekly details")
                    }
                    if (weekDetails) {
                        snapshot.weekly.days.forEach { day ->
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "${day.key}${if(day.key == snapshot.today) " · Today" else ""}",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    "${day.status} · ${day.title}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
            item {
                StatsPanel {
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Text(
                    "Strength snapshot",
                    style = MaterialTheme.typography.titleLarge,
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
                StatsPanel(Modifier.testTag("stats-insight-${insight.type}-${insight.title}")) {
                    Text(insight.title, style = MaterialTheme.typography.titleMedium)
                    Text(insight.meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.testTag("stats-recent-heading"),
                )
                Text("The three newest saved workouts. Full details are in History.")
                if (snapshot.recentSessions.isEmpty())
                    Text("No sessions yet. Logged workouts will appear here.")
            }
            items(snapshot.recentSessions, key = { "recent:${it.id}" }) { session ->
                StatsPanel(Modifier.testTag("stats-recent-${session.id}")) {
                    Text(session.type, style = MaterialTheme.typography.titleMedium)
                    Text("${session.date} · ${session.exercises} exercises")
                    Text(
                        if (session.duration == 0.0) "Duration: —"
                        else "${progressNumber(session.duration)} min"
                    )
                }
            }
            item {
                Text("Personal records", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Warm-ups are excluded. Loaded sets with positive weight and reps qualify even when not marked completed. Estimated 1RM uses the Epley formula and is an estimate.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextField(
                    query,
                    { query = it },
                    label = { Text("Find an exercise") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    leadingIcon = { Icon(TrackerIcons.Search, null, Modifier.size(20.dp)) },
                    colors =
                        TextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
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
                StatsPanel {
                    Text(record.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${record.sessions} sessions · ${record.sets} working sets",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Best estimated 1RM: ${decimal(record.bestEstimated1rm)} lb · ${record.bestDate}"
                    )
                    OutlinedButton(
                        onClick = { selected = record.name },
                        modifier = Modifier.fillMaxWidth().testTag("stats-exercise-${record.name}"),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("View progress", Modifier.weight(1f))
                        Icon(TrackerIcons.Chevron, null, Modifier.size(18.dp))
                    }
                }
            }
            item { CompactHelpTip("statsBasics", "About your stats") }
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
                        StatsPanel(Modifier.testTag("stats-record-$key")) {
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
                            Text(
                                "RPE: ${record.rpe.jsonPrimitive.content.ifEmpty { "—" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    Text("Recent progress", style = MaterialTheme.typography.titleLarge)
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
                    StatsPanel {
                        ProgressChart(recent, metric)
                        Text(
                            "Last ${recent.size} entries · ${unit(metric)} · zero baseline",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(recent.first().date, style = MaterialTheme.typography.labelSmall)
                            if (recent.size > 1)
                                Text(
                                    recent.last().date,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                        }
                    }
                }
                items(recent.withIndex().toList(), key = { "point:${it.index}" }) { (_, point) ->
                    val record = requireNotNull(point.records.value(metric))
                    StatsPanel {
                        Text(
                            "${point.date} · ${point.type}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text("${label(metric)}: ${decimal(point.value(metric))} ${unit(metric)}")
                        Text(
                            "${progressNumber(record.weight)} lb × ${progressNumber(record.reps)} reps"
                        )
                        LinearProgressIndicator(
                            progress = { (point.value(metric) / max).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = trackerPanelColor()),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun StatsMetrics(values: List<Pair<String, String>>) {
    val large = LocalDensity.current.fontScale > 1.3f
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        values.withIndex().toList().chunked(if (large) 1 else 2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (index, entry) ->
                    val (value, label) = entry
                    val emphasized = index == 0
                    Surface(
                        Modifier.weight(1f).semantics(mergeDescendants = true) {},
                        shape =
                            if (emphasized)
                                RoundedCornerShape(
                                    topStart = 24.dp,
                                    topEnd = 24.dp,
                                    bottomEnd = 24.dp,
                                    bottomStart = 8.dp,
                                )
                            else RoundedCornerShape(22.dp),
                        color =
                            if (emphasized) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor =
                            if (emphasized) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                        shadowElevation = if (emphasized) 3.dp else 0.dp,
                    ) {
                        Column(
                            Modifier.padding(16.dp).heightIn(min = 82.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                value,
                                style =
                                    if (emphasized) MaterialTheme.typography.displaySmall
                                    else MaterialTheme.typography.headlineLarge,
                            )
                            Text(
                                label,
                                style = MaterialTheme.typography.bodySmall,
                                color =
                                    if (emphasized) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsWeek(days: List<ActivityDay>, today: String) {
    val fontScale = LocalDensity.current.fontScale
    val large = fontScale > 1.3f
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = if (large) 52.dp * fontScale else ((maxWidth - 24.dp) / 7).coerceAtLeast(40.dp)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            days.forEach { day ->
                Surface(
                    Modifier.width(width).clearAndSetSemantics {
                        contentDescription =
                            "${day.key}, ${day.title}, ${day.status}${if(day.key == today) ", Today" else ""}"
                    },
                    shape = RoundedCornerShape(16.dp),
                    color =
                        if (day.complete) MaterialTheme.colorScheme.primaryContainer
                        else if (day.key == today)
                            MaterialTheme.colorScheme.primary.copy(alpha = .1f)
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                    border =
                        if (day.key == today) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        else null,
                ) {
                    Column(
                        Modifier.padding(horizontal = 2.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(day.label, style = MaterialTheme.typography.labelMedium)
                        if (day.complete) Icon(TrackerIcons.Check, null, Modifier.size(18.dp))
                        else Text(day.key.takeLast(2), style = MaterialTheme.typography.labelMedium)
                        Text(day.status, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressChart(points: List<ExercisePoint>, metric: String) {
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val max = points.maxOf { it.value(metric) }.coerceAtLeast(1.0)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${decimal(max)} ${unit(metric)}", style = MaterialTheme.typography.labelSmall)
        Canvas(Modifier.fillMaxWidth().height(140.dp).clearAndSetSemantics {}) {
            val inset = 6.dp.toPx()
            val width = size.width - 2 * inset
            val height = size.height - 2 * inset
            for (fraction in listOf(0f, .5f, 1f)) drawLine(
                grid,
                Offset(inset, inset + height * fraction),
                Offset(size.width - inset, inset + height * fraction),
                strokeWidth = 1.dp.toPx(),
            )
            val offsets =
                points.mapIndexed { index, point ->
                    Offset(
                        if (points.size == 1) size.width / 2
                        else inset + width * index / (points.size - 1),
                        inset + height * (1 - point.value(metric) / max).toFloat(),
                    )
                }
            if (offsets.size > 1) {
                val line =
                    Path().apply {
                        moveTo(offsets.first().x, offsets.first().y)
                        offsets.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                val area =
                    Path().apply {
                        moveTo(offsets.first().x, inset + height)
                        offsets.forEach { lineTo(it.x, it.y) }
                        lineTo(offsets.last().x, inset + height)
                        close()
                    }
                drawPath(
                    area,
                    Brush.verticalGradient(
                        listOf(primary.copy(alpha = .22f), primary.copy(alpha = .01f))
                    ),
                )
                drawPath(line, primary, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
            offsets.forEach { drawCircle(primary, 4.dp.toPx(), it) }
        }
        Text("0", style = MaterialTheme.typography.labelSmall)
    }
}
