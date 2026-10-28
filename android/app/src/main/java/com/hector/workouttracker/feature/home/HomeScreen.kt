package com.hector.workouttracker.feature.home

import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hector.workouttracker.core.designsystem.*
import com.hector.workouttracker.core.schedule.*
import com.hector.workouttracker.domain.ActivityDay
import com.hector.workouttracker.domain.guidanceTopics
import com.hector.workouttracker.domain.guidanceVisible
import com.hector.workouttracker.feature.help.LocalHelp
import com.hector.workouttracker.feature.session.TrackerState
import com.hector.workouttracker.feature.session.TrackerViewModel
import com.hector.workouttracker.feature.stats.StatsViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.*

private val HomeFlame =
    ImageVector.Builder("Streak", 20.dp, 20.dp, 24f, 24f)
        .apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(13f, 2f)
                curveTo(14f, 7f, 7f, 8f, 9f, 13f)
                curveTo(6f, 12f, 6f, 10f, 6f, 9f)
                curveTo(1f, 16f, 6f, 22f, 12f, 22f)
                curveTo(20f, 22f, 23f, 13f, 17f, 7f)
                curveTo(18f, 11f, 14f, 13f, 14f, 13f)
                curveTo(16f, 9f, 16f, 5f, 13f, 2f)
                close()
            }
        }
        .build()

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
    var preview by rememberSaveable { mutableStateOf(false) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    var hour by remember { mutableIntStateOf(LocalTime.now().hour) }
    var choice by rememberSaveable(today.toString()) { mutableStateOf<String?>(null) }
    val progressState by progressVm.state.collectAsStateWithLifecycle()
    val progress = progressState.snapshot
    // Report the first complete Home frame, including local library and reference data.
    ReportDrawnWhen {
        !state.libraryLoading && !state.scheduleLoading && !state.catalogLoading && progress != null
    }
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(Unit) {
        val job =
            scope.launch {
                while (isActive) {
                    today = LocalDate.now()
                    hour = LocalTime.now().hour
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
    val selected =
        if (choice == "quick") null
        else
            state.routines.firstOrNull { it.record.idKey == choice }
                ?: planned
                ?: state.routines.firstOrNull()
    val draft = state.draft
    val title = draft?.type ?: selected?.name ?: "Quick workout"
    val exercises = draft?.exercises?.map { it.name } ?: selected?.exercises.orEmpty()
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val ink = if (dark) Color(0xFFF8FBFF) else Color(0xFF14283F)
    val muted = if (dark) Color(0xFFADC1D9) else Color(0xFF4A6078)
    val accent = if (dark) Color(0xFFFF8A68) else MaterialTheme.colorScheme.primary
    val card = if (dark) Color(0xFF22354F) else Color(0xFFE5EDF7)
    val edge = if (dark) Color(0xFF425671) else Color(0xFFBFCDDD)
    val background = homeBackgroundBrush()
    LaunchedEffect(state.draft, opening, state.busy, state.error) {
        if (opening && state.draft != null) {
            opening = false
            openSession()
        }
        if (opening && !state.busy && state.error != null) opening = false
    }
    fun start() {
        if (draft != null) {
            openSession()
            return
        }
        opening = true
        if (selected != null && selected.record.idKey == planned?.record?.idKey) {
            vm.startPlanned(
                selected,
                requireNotNull(state.schedule)[today.dayOfWeek.value % 7],
                today,
            )
        } else vm.start(selected)
    }
    Column(Modifier.fillMaxSize().background(background)) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().testTag("home-list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            buildAnnotatedString {
                                append(
                                    when (hour) {
                                        in 0..11 -> "Good morning, "
                                        in 12..17 -> "Good afternoon, "
                                        else -> "Good evening, "
                                    }
                                )
                                withStyle(SpanStyle(color = accent)) {
                                    append(state.profile.displayName)
                                }
                            },
                            color = ink,
                            fontSize = 24.sp,
                            lineHeight = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-.8).sp,
                        )
                        Text(
                            today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                            color = muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    progress?.let {
                        Surface(
                            shape = CircleShape,
                            color = ink.copy(alpha = .06f),
                            border = BorderStroke(1.dp, edge.copy(alpha = .6f)),
                            modifier =
                                Modifier.clearAndSetSemantics {
                                    contentDescription = "${it.streak} day streak"
                                },
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(HomeFlame, null, Modifier.size(16.dp), tint = accent)
                                Text(
                                    "${it.streak}",
                                    color = accent,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }
            }
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = card.copy(alpha = if (dark) .85f else 1f),
                    border = BorderStroke(1.dp, edge.copy(alpha = .4f)),
                    shadowElevation = 2.dp,
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                "This week",
                                color = muted,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                progress?.let {
                                    "${it.weekly.completedGymDays}/${it.weekly.gymDays} workouts"
                                } ?: if (progressState.error != null) "Unavailable" else "Loading…",
                                color = accent,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        progress?.let { HomeWeek(it.weekly.days, it.today, dark, muted, accent) }
                        if (progressState.error != null)
                            TextButton(onClick = progressVm::retry) {
                                Text("Retry weekly progress")
                            }
                    }
                }
            }
            item {
                TrainingHero(Modifier.testTag("home-workout-card")) {
                    Column(
                        Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TrainingEmblem(size = 32.dp) {
                                Icon(TrackerIcons.Workout, null, Modifier.size(20.dp))
                            }
                            Text(
                                if (draft != null) "Workout in progress"
                                else
                                    when (plan?.kind) {
                                        "gym" ->
                                            if (selected?.record?.idKey == planned?.record?.idKey)
                                                "Today's workout"
                                            else "Selected workout"
                                        "rest" -> "Rest day"
                                        "soccer" -> "Soccer day"
                                        else -> "Your workout"
                                    },
                                color = muted,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                title,
                                color = ink,
                                fontSize = 34.sp,
                                lineHeight = 38.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-1).sp,
                            )
                            Text(
                                when {
                                    draft != null ->
                                        "Your session is saved. Pick up where you left off."
                                    plan?.kind == "rest" || plan?.kind == "soccer" -> plan.note
                                    selected != null ->
                                        if (selected.record.idKey == planned?.record?.idKey)
                                            "Suggested by your ${today.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }} schedule."
                                        else "Your routine, ready when you are."
                                    else -> "Start fresh. Add your exercises as you go."
                                },
                                color = muted,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (exercises.isNotEmpty()) HomePill("${exercises.size} exercises", ink)
                            if (draft != null)
                                HomePill(
                                    "${draft.exercises.sumOf { e -> e.sets.count { it.done } }}/${draft.exercises.sumOf { it.sets.size }} sets logged",
                                    ink,
                                )
                            else
                                HomePill(
                                    if (plan?.kind == "rest") "Recovery"
                                    else if (plan?.kind == "soccer") "Soccer" else "Gym",
                                    ink,
                                )
                        }
                        if (exercises.isNotEmpty())
                            TextButton(
                                onClick = { preview = true },
                                modifier = Modifier.testTag("preview-home-workout"),
                                colors = ButtonDefaults.textButtonColors(contentColor = muted),
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text("Preview workout", style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.width(6.dp))
                                Icon(TrackerIcons.Chevron, null, Modifier.size(18.dp))
                            }
                        if (draft == null) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = ink.copy(alpha = .045f),
                                border = BorderStroke(1.dp, edge.copy(alpha = .7f)),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth()
                                        .padding(
                                            start = 14.dp,
                                            end = 8.dp,
                                            top = 6.dp,
                                            bottom = 6.dp,
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Column(
                                        Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        Text(
                                            "Selected workout",
                                            color = muted,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                        Text(
                                            title,
                                            color = ink,
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                    }
                                    TextButton(
                                        onClick = { routinesOpen = true },
                                        enabled = !state.busy,
                                        modifier =
                                            Modifier.testTag("home-routine-chooser").semantics {
                                                contentDescription = "Use saved routine"
                                            },
                                        colors =
                                            ButtonDefaults.textButtonColors(contentColor = accent),
                                    ) {
                                        Text("Change")
                                    }
                                }
                            }
                        }
                        if (state.pending)
                            Text(
                                "Saving your latest entries…",
                                color = muted,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        if (plan == null && !state.scheduleLoading)
                            Text(
                                "Your schedule is unavailable. You can still start a workout.",
                                color = muted,
                                style = MaterialTheme.typography.bodySmall,
                            )
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
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(
                        onClick = openRoutines,
                        enabled = !state.busy,
                        colors = ButtonDefaults.textButtonColors(contentColor = accent),
                    ) {
                        Icon(TrackerIcons.Workout, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Manage routines")
                    }
                    TextButton(
                        onClick = openSchedule,
                        enabled = !state.busy,
                        colors = ButtonDefaults.textButtonColors(contentColor = accent),
                    ) {
                        Icon(TrackerIcons.Calendar, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit schedule")
                    }
                }
            }
            item { HomeTip(muted) }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Button(
                onClick = ::start,
                enabled = !state.busy && !opening,
                shape =
                    RoundedCornerShape(
                        topStart = 28.dp,
                        topEnd = 28.dp,
                        bottomEnd = 28.dp,
                        bottomStart = 12.dp,
                    ),
                elevation =
                    ButtonDefaults.buttonElevation(
                        defaultElevation = 3.dp,
                        pressedElevation = 0.dp,
                    ),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = TrackerPalette.Coral,
                        contentColor = TrackerPalette.OnCoral,
                    ),
                modifier =
                    Modifier.fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .testTag(
                            if (
                                draft == null &&
                                    selected != null &&
                                    selected.record.idKey == planned?.record?.idKey
                            )
                                "start-planned-workout"
                            else "home-primary-action"
                        ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            ) {
                Icon(
                    if (draft != null) TrackerIcons.Chevron else HomePlay,
                    null,
                    Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (draft != null) "Resume workout" else "Start workout",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
    val dialogModifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 560.dp).fillMaxWidth()
    val dialogProperties = DialogProperties(usePlatformDefaultWidth = false)
    if (routinesOpen)
        AlertDialog(
            onDismissRequest = { routinesOpen = false },
            modifier = dialogModifier,
            properties = dialogProperties,
            title = { Text("Choose a routine") },
            text = {
                LazyColumn(
                    Modifier.testTag("home-routine-options"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        OutlinedButton(
                            onClick = {
                                choice = "quick"
                                routinesOpen = false
                            },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Quick workout")
                        }
                    }
                    items(state.routines, key = { it.record.idKey }) { routine ->
                        OutlinedButton(
                            onClick = {
                                choice = routine.record.idKey
                                routinesOpen = false
                            },
                            enabled = !state.busy,
                            modifier =
                                Modifier.fillMaxWidth()
                                    .testTag("home-routine-${routine.record.idKey}"),
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
    if (preview)
        AlertDialog(
            onDismissRequest = { preview = false },
            modifier = dialogModifier,
            properties = dialogProperties,
            title = { Text(title) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(exercises.size) { index -> Text("${index + 1}. ${exercises[index]}") }
                }
            },
            confirmButton = { TextButton(onClick = { preview = false }) { Text("Done") } },
        )
}

@Composable
private fun HomePill(text: String, ink: Color) {
    Surface(
        shape = CircleShape,
        color = ink.copy(alpha = .06f),
        border = BorderStroke(1.dp, ink.copy(alpha = .13f)),
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            color = ink,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun HomeWeek(
    days: List<ActivityDay>,
    today: String,
    dark: Boolean,
    muted: Color,
    accent: Color,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        days.forEach { day ->
            val current = day.key == today
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Surface(
                    Modifier.widthIn(max = 40.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clearAndSetSemantics {
                            contentDescription =
                                "${day.key}, ${day.title}, ${day.status}${if(current) ", Today" else ""}"
                        },
                    shape = CircleShape,
                    color =
                        when {
                            current ->
                                if (dark) TrackerPalette.Coral
                                else MaterialTheme.colorScheme.primary
                            day.complete -> accent.copy(alpha = .16f)
                            else -> if (dark) Color(0xFF172D45) else Color(0xFFD5E1EF)
                        },
                    border =
                        if (current)
                            BorderStroke(2.dp, if (dark) Color(0xFFEAF1FA) else Color.White)
                        else
                            BorderStroke(
                                1.dp,
                                accent.copy(alpha = if (day.complete) .35f else .04f),
                            ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (day.complete && !current)
                            Icon(TrackerIcons.Check, null, Modifier.size(18.dp), tint = accent)
                        else
                            Text(
                                day.label.take(1),
                                color =
                                    if (current) if (dark) TrackerPalette.OnCoral else Color.White
                                    else muted,
                                style = MaterialTheme.typography.labelMedium,
                            )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTip(muted: Color) {
    val vm = LocalHelp.current ?: return
    val state by vm.state.collectAsStateWithLifecycle()
    if (state.loading || !guidanceVisible(state.progress, "homeTour")) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.testTag("read-tip-homeTour"),
            colors = ButtonDefaults.textButtonColors(contentColor = muted),
        ) {
            Icon(TrackerIcons.Help, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (expanded) "Close tips" else "Tips for Today")
        }
        if (expanded) {
            Text(
                guidanceTopics.first { it.id == "homeTour" }.text,
                color = muted,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = { vm.complete("homeTour") },
                enabled = !state.busy,
                modifier = Modifier.testTag("dismiss-tip-homeTour"),
            ) {
                Text("Got it")
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

private val HomePlay =
    ImageVector.Builder("Start", 24.dp, 24.dp, 24f, 24f)
        .apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(8f, 5f)
                lineTo(19f, 12f)
                lineTo(8f, 19f)
                close()
            }
        }
        .build()

/** Shared only with the Home scaffold so its background continues behind system bars. */
@Composable
internal fun homeBackgroundBrush(): Brush =
    Brush.verticalGradient(
        if (MaterialTheme.colorScheme.background.luminance() < .5f)
            listOf(Color(0xFF1B334D), Color(0xFF0C1E31), Color(0xFF070F1C))
        else listOf(Color(0xFFF1F5FA), MaterialTheme.colorScheme.background)
    )
