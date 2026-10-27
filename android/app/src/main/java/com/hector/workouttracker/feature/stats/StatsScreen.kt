package com.hector.workouttracker.feature.stats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
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
    val focus = LocalFocusManager.current
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
    var section by rememberSaveable { mutableStateOf("overview") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var metric by rememberSaveable { mutableStateOf("bestWeight") }
    var query by rememberSaveable { mutableStateOf("") }
    var weekDetails by rememberSaveable { mutableStateOf(false) }
    var numberDetails by rememberSaveable { mutableStateOf(false) }
    var entriesExpanded by rememberSaveable { mutableStateOf(false) }
    val overviewList = rememberLazyListState()
    val exerciseList = rememberLazyListState()
    val insightsList = rememberLazyListState()
    val progressList = rememberLazyListState()
    val list =
        if (selected != null) progressList
        else
            when (section) {
                "exercises" -> exerciseList
                "insights" -> insightsList
                else -> overviewList
            }
    fun back() {
        focus.clearFocus()
        if (selected != null) selected = null else section = "overview"
    }
    BackHandler(selected != null || section != "overview") { back() }
    LaunchedEffect(selected) { if (selected != null) progressList.scrollToItem(0) }
    val snapshot = state.snapshot
    val exercise = snapshot?.exercises?.firstOrNull { it.name == selected }
    LazyColumn(
        Modifier.fillMaxSize().background(trackerScreenBackground()).testTag("stats-list"),
        state = list,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (selected != null || section != "overview")
                    IconButton(onClick = ::back, modifier = Modifier.testTag("stats-back")) {
                        Icon(
                            TrackerIcons.Back,
                            if (selected != null) "Back to exercises" else "Back to Stats",
                        )
                    }
                Text(
                    selected
                        ?: when (section) {
                            "exercises" -> "Exercise records"
                            "insights" -> "Training insights"
                            else -> "Your stats"
                        },
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineMedium,
                )
                IconButton(onClick = onHistory, modifier = Modifier.testTag("open-history")) {
                    Icon(
                        painterResource(R.drawable.ic_history),
                        "Session history",
                        Modifier.size(22.dp),
                    )
                }
            }
            if (selected != null)
                Text(
                    "Exercise progress · saved workouts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { message ->
            item {
                Text(message)
                TextButton(onClick = vm::retry) { Text("Retry") }
            }
        }
        if (snapshot != null && selected == null && section == "overview") {
            item { StatsWeekSummary(snapshot) { weekDetails = true } }
            item { StatsLifetimeSummary(snapshot) { numberDetails = true } }
            item { StatsStrengthPreview(snapshot) { section = "insights" } }
            item {
                ActionRow(
                    "Exercise records",
                    "${snapshot.exercises.count { it.bestEstimated1rm > 0 }} ${if (snapshot.exercises.count { it.bestEstimated1rm > 0 } == 1) "exercise" else "exercises"} · load, volume, 1RM",
                    TrackerIcons.Search,
                    modifier = Modifier.testTag("stats-open-exercises"),
                    onClick = { section = "exercises" },
                )
            }
            if (snapshot.recentSessions.isNotEmpty())
                item { StatsLatestSession(snapshot, onHistory) }
        } else if (snapshot != null && selected == null && section == "insights") {
            item {
                Text(
                    "Strength snapshot",
                    Modifier.testTag("stats-strength-heading"),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (snapshot.insights.isEmpty())
                item {
                    Text(
                        "Keep logging sessions. Strength trends will appear here once you have a few sessions logged."
                    )
                }
            items(snapshot.insights, key = { "insight:${it.type}:${it.title}" }) { insight ->
                StatsPanel(Modifier.testTag("stats-insight-${insight.type}-${insight.title}")) {
                    Text(insight.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        insight.meta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Text(
                    "Recent sessions",
                    Modifier.testTag("stats-recent-heading"),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (snapshot.recentSessions.isEmpty())
                item { Text("No sessions yet. Logged workouts will appear here.") }
            items(snapshot.recentSessions, key = { "recent:${it.id}" }) { session ->
                StatsPanel(Modifier.testTag("stats-recent-${session.id}")) {
                    Text(session.type, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${session.date} · ${session.exercises} exercises",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        if (session.duration == 0.0) "Duration: —"
                        else "${progressNumber(session.duration)} min",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            item {
                OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) {
                    Text("Open session history")
                }
            }
            item {
                TextButton(onClick = { numberDetails = true }) { Text("How these numbers work") }
                CompactHelpTip("statsBasics", "About your stats")
            }
        } else if (snapshot != null && selected == null && section == "exercises") {
            item {
                TextField(
                    query,
                    { query = it },
                    label = { Text("Find an exercise") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    leadingIcon = { Icon(TrackerIcons.Search, null, Modifier.size(20.dp)) },
                    modifier = Modifier.fillMaxWidth().testTag("stats-search"),
                )
            }
            val records =
                snapshot.exercises
                    .filter {
                        it.bestEstimated1rm > 0 && it.name.contains(query.trim(), ignoreCase = true)
                    }
                    .sortedByDescending { it.bestEstimated1rm }
            item {
                Text(
                    "${records.size} ${if (records.size == 1) "exercise" else "exercises"} with loaded sets",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (records.isEmpty())
                item {
                    Text(
                        if (query.isEmpty())
                            "No personal records yet. Log loaded sets and reps to see progress."
                        else "No matching records."
                    )
                }
            items(records, key = { "record:${it.name}" }) { record ->
                Surface(
                    onClick = {
                        focus.clearFocus()
                        selected = record.name
                        entriesExpanded = false
                    },
                    modifier = Modifier.fillMaxWidth().testTag("stats-exercise-${record.name}"),
                    shape = RoundedCornerShape(20.dp),
                    color = trackerPanelColor(),
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(record.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Best estimated 1RM: ${decimal(record.bestEstimated1rm)} lb · ${record.bestDate}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(TrackerIcons.Chevron, "View progress", Modifier.size(20.dp))
                    }
                }
            }
            item {
                TextButton(onClick = { numberDetails = true }) { Text("How these numbers work") }
            }
        } else if (snapshot != null && selected != null) {
            if (exercise == null || exercise.history.isEmpty())
                item { Text("This exercise no longer has loaded sets in saved History.") }
            else {
                item {
                    Text(
                        "${exercise.sessions} sessions · ${exercise.sets} working sets",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                item {
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
                val record = requireNotNull(exercise.records.value(metric))
                item {
                    StatsPanel(Modifier.testTag("stats-record-$metric")) {
                        val value =
                            when (metric) {
                                "bestWeight" -> record.weight
                                "bestVolume" -> record.volume
                                else -> record.estimated1rm
                            }
                        Text(
                            "${label(metric)}: ${decimal(value)} ${unit(metric)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "${progressNumber(record.weight)} lb × ${progressNumber(record.reps)} reps · ${record.date}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "RPE: ${record.rpe.jsonPrimitive.content.ifEmpty { "—" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val delta = recent.last().value(metric) - recent.first().value(metric)
                        Text(
                            if (recent.size < 2) "Log another entry to see a trend."
                            else
                                "${if (delta >= 0) "+" else ""}${decimal(delta)} ${unit(metric)} over ${recent.size} entries",
                            style = MaterialTheme.typography.bodySmall,
                        )
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
                item {
                    TextButton(
                        onClick = { entriesExpanded = !entriesExpanded },
                        modifier = Modifier.fillMaxWidth().testTag("stats-entries-toggle"),
                    ) {
                        Text(
                            if (entriesExpanded) "Hide entry details"
                            else "Show ${recent.size} entry details"
                        )
                    }
                }
                if (entriesExpanded)
                    items(recent.withIndex().toList(), key = { "point:${it.index}" }) {
                        (index, point) ->
                        val source = requireNotNull(point.records.value(metric))
                        StatsPanel(Modifier.testTag("stats-point-$index")) {
                            Text(
                                "${point.date} · ${point.type}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "${label(metric)}: ${decimal(point.value(metric))} ${unit(metric)}"
                            )
                            Text(
                                "${progressNumber(source.weight)} lb × ${progressNumber(source.reps)} reps"
                            )
                            LinearProgressIndicator(
                                progress = {
                                    (point.value(metric) / max).toFloat().coerceIn(0f, 1f)
                                },
                                modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
                            )
                        }
                    }
                item {
                    TextButton(onClick = { numberDetails = true }) {
                        Text("How these numbers work")
                    }
                }
            }
        }
    }
    if (weekDetails && snapshot != null) StatsWeekDialog(snapshot) { weekDetails = false }
    if (numberDetails)
        AlertDialog(
            onDismissRequest = { numberDetails = false },
            modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 560.dp).fillMaxWidth(),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            title = { Text("About your stats") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Average duration uses sessions with a nonzero duration.")
                    Text(
                        "The weekly goal counts workouts in the last seven days, including today. Multiple workouts on one date count separately. Planned gym days use the Monday–Sunday calendar week."
                    )
                    Text(
                        "Warm-ups are excluded. Loaded sets with positive weight and reps qualify even when not marked completed. Estimated 1RM uses the Epley formula and is an estimate."
                    )
                    Text(
                        "Saved spellings are kept separate. Repeated exercise rows remain separate progress entries. Each metric keeps its own source set, date and RPE."
                    )
                }
            },
            confirmButton = { TextButton(onClick = { numberDetails = false }) { Text("Done") } },
        )
}

@Composable
internal fun StatsPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = trackerPanelColor()),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f)),
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun ProgressChart(points: List<ExercisePoint>, metric: String) {
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val max = points.maxOf { it.value(metric) }.coerceAtLeast(1.0)
    Column(Modifier.testTag("stats-chart"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${decimal(max)} ${unit(metric)}", style = MaterialTheme.typography.labelSmall)
        Canvas(Modifier.fillMaxWidth().height(120.dp).clearAndSetSemantics {}) {
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
