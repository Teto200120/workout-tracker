package com.hector.workouttracker.feature.session

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.hector.workouttracker.core.designsystem.*
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.domain.progressNumber
import com.hector.workouttracker.feature.catalog.GuideContent
import kotlinx.serialization.json.*

@Composable
internal fun FocusedExerciseScreen(
    state: TrackerState,
    vm: TrackerViewModel,
    exercise: Exercise,
    selectedSet: WorkingSet?,
    onBack: () -> Unit,
    onSelect: (WorkingSet) -> Unit,
    onChange: (Exercise) -> Unit,
    onComplete: (WorkingSet, Boolean) -> Unit,
    onCopy: (Boolean) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    var guide by rememberSaveable(exercise.key) { mutableStateOf(false) }
    var allSets by rememberSaveable(exercise.key) { mutableStateOf(false) }
    var chooseSet by rememberSaveable(exercise.key) { mutableStateOf(false) }
    var removeKey by rememberSaveable(exercise.key) { mutableStateOf<String?>(null) }
    val list = rememberSaveable(exercise.key, saver = LazyListState.Saver) { LazyListState() }
    val focus = LocalFocusManager.current
    val notesViewport = remember(exercise.key) { BringIntoViewRequester() }
    var notesFocused by remember(exercise.key) { mutableStateOf(false) }
    val index = state.draft!!.exercises.indexOfFirst { it.key == exercise.key }
    val progress = exerciseProgress(exercise)
    val keyboard = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize().testTag("focused-exercise")) {
        val compact = maxHeight < 480.dp || keyboard
        val showToolbar = !keyboard || maxHeight >= 180.dp
        LaunchedEffect(keyboard, notesFocused, constraints.maxHeight) {
            if (keyboard && notesFocused && showToolbar) {
                withFrameNanos {}
                notesViewport.bringIntoView()
            }
        }
        Column(Modifier.fillMaxSize().background(sessionBackgroundBrush())) {
            if (showToolbar)
                Row(
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = if (compact) 4.dp else 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilledTonalIconButton(
                        onClick = onBack,
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp).testTag("focused-back"),
                    ) {
                        Icon(TrackerIcons.Back, "Back to workout")
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            exercise.name,
                            Modifier.semantics {
                                heading()
                                if (compact)
                                    stateDescription =
                                        "Exercise ${index + 1} of ${state.draft.exercises.size}, ${progress.completedSets} of ${progress.workingSets} working sets completed"
                            },
                            style =
                                if (compact) MaterialTheme.typography.titleMedium
                                else MaterialTheme.typography.headlineSmall,
                            maxLines = if (compact) 1 else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!compact)
                            Text(
                                "Exercise ${index + 1} of ${state.draft.exercises.size} · ${progress.completedSets}/${progress.workingSets} working sets",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.testTag("focused-position"),
                            )
                    }
                }
            if (!keyboard)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = !guide,
                        onClick = {
                            focus.clearFocus()
                            guide = false
                        },
                        label = { Text("Log") },
                        modifier = Modifier.testTag("focused-log-tab"),
                    )
                    FilterChip(
                        selected = guide,
                        onClick = {
                            focus.clearFocus()
                            guide = true
                        },
                        label = { Text("Guide") },
                        modifier = Modifier.testTag("focused-guide-tab"),
                    )
                }
            Crossfade(
                targetState = guide,
                modifier = Modifier.weight(1f),
                animationSpec =
                    if (state.interactions.animations) tween(TrackerMotion.Quick) else snap(),
                label = "exercise-log-guide",
            ) { showGuide ->
                if (showGuide)
                    Box(Modifier.fillMaxSize()) {
                        GuideContent(
                            exercise.name,
                            state.catalog,
                            { guide = false },
                            embedded = true,
                        )
                    }
                else
                    LazyColumn(
                        Modifier.fillMaxSize().testTag("focused-log-list"),
                        state = list,
                        contentPadding =
                            PaddingValues(
                                horizontal = 16.dp,
                                vertical = if (keyboard) 0.dp else 16.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item {
                            Surface(
                                color = trackerPanelColor(),
                                shape = RoundedCornerShape(28.dp),
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box {
                                            TextButton(
                                                onClick = { chooseSet = true },
                                                enabled = !state.busy,
                                                modifier = Modifier.testTag("focused-set-selector"),
                                            ) {
                                                Text(
                                                    if (selectedSet == null) "Select set"
                                                    else
                                                        "Set ${exercise.sets.indexOf(selectedSet) + 1} of ${exercise.sets.size}"
                                                )
                                                Icon(
                                                    TrackerIcons.Chevron,
                                                    null,
                                                    Modifier.size(18.dp),
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = chooseSet,
                                                onDismissRequest = { chooseSet = false },
                                            ) {
                                                exercise.sets.forEachIndexed { n, set ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                "Set ${n + 1} · ${if (set.done) "Completed" else "Not completed"}"
                                                            )
                                                        },
                                                        onClick = {
                                                            onSelect(set)
                                                            chooseSet = false
                                                        },
                                                        modifier =
                                                            Modifier.testTag(
                                                                "focused-select-${set.key}"
                                                            ),
                                                    )
                                                }
                                            }
                                        }
                                        TextButton(
                                            onClick = { allSets = !allSets },
                                            modifier = Modifier.testTag("focused-all-sets"),
                                        ) {
                                            Text(if (allSets) "Current set" else "All sets")
                                        }
                                    }
                                    exercise.sets.forEachIndexed { n, set ->
                                        if (allSets || set.key == selectedSet?.key)
                                            key(set.key) {
                                                SessionSetEntry(
                                                    set,
                                                    n,
                                                    exercise.sets.size,
                                                    state.profile.trackRpe,
                                                    !state.busy,
                                                    state.guidance[exercise.name]
                                                        ?.profile
                                                        ?.increment ?: 5.0,
                                                    set.key == selectedSet?.key,
                                                    showHeading = allSets,
                                                    onFocused = { onSelect(set) },
                                                    onChange = { value ->
                                                        onChange(
                                                            exercise.copy(
                                                                sets =
                                                                    exercise.sets.map {
                                                                        if (it.key == set.key) value
                                                                        else it
                                                                    }
                                                            )
                                                        )
                                                    },
                                                    onComplete = { done -> onComplete(set, done) },
                                                )
                                            }
                                    }
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(
                                            onClick = {
                                                onChange(
                                                    exercise.copy(
                                                        sets = exercise.sets + WorkingSet()
                                                    )
                                                )
                                            },
                                            enabled = !state.busy && exercise.sets.size < 200,
                                            modifier = Modifier.testTag("focused-add-set"),
                                        ) {
                                            Text("Add set")
                                        }
                                        TextButton(
                                            onClick = {
                                                val candidate =
                                                    exercise.sets.lastOrNull {
                                                        !it.warmup && !it.done
                                                    } ?: exercise.sets.lastOrNull { !it.warmup }
                                                if (candidate != null) {
                                                    if (
                                                        candidate.done ||
                                                            candidate.weight.isNotEmpty() ||
                                                            candidate.reps.isNotEmpty() ||
                                                            candidate.rpe.isNotEmpty()
                                                    )
                                                        removeKey = candidate.key
                                                    else
                                                        onChange(
                                                            exercise.copy(
                                                                sets =
                                                                    exercise.sets.filterNot {
                                                                        it.key == candidate.key
                                                                    }
                                                            )
                                                        )
                                                }
                                            },
                                            enabled =
                                                !state.busy &&
                                                    exercise.sets.size > 1 &&
                                                    exercise.sets.any { !it.warmup },
                                            modifier = Modifier.testTag("focused-remove-set"),
                                        ) {
                                            Text("Remove last set")
                                        }
                                        if (exercise.sets.any { it.warmup })
                                            TextButton(
                                                onClick = {
                                                    val last = exercise.sets.last { it.warmup }
                                                    onChange(
                                                        exercise.copy(
                                                            sets =
                                                                exercise.sets.map {
                                                                    if (it.key == last.key)
                                                                        it.copy(warmup = false)
                                                                    else it
                                                                }
                                                        )
                                                    )
                                                },
                                                enabled = !state.busy,
                                                modifier = Modifier.testTag("focused-undo-warmup"),
                                            ) {
                                                Text("Undo last warm-up")
                                            }
                                    }
                                }
                            }
                        }
                        item { FocusedGuidanceSummary(state, exercise) }
                        item {
                            OutlinedTextField(
                                exercise.notes,
                                { onChange(exercise.copy(notes = it)) },
                                label = { Text("Exercise notes") },
                                placeholder = { Text("Grip, form cues, rest or machine settings") },
                                isError = exercise.notes.length > 4000,
                                supportingText = {
                                    if (exercise.notes.length > 4000)
                                        Text("Use up to 4,000 characters.")
                                },
                                enabled = !state.busy,
                                minLines = if (compact) 1 else 3,
                                maxLines = if (compact) 3 else Int.MAX_VALUE,
                                shape = RoundedCornerShape(20.dp),
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .bringIntoViewRequester(notesViewport)
                                        .onFocusChanged { notesFocused = it.isFocused }
                                        .testTag("focused-notes"),
                            )
                        }
                        item { TrainingGuidanceCard(state, exercise, vm, onCopy) }
                        item {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        focus.clearFocus()
                                        onPrevious()
                                    },
                                    enabled = !state.busy && index > 0,
                                    modifier = Modifier.weight(1f).testTag("focused-previous"),
                                ) {
                                    Text("Previous")
                                }
                                OutlinedButton(
                                    onClick = {
                                        focus.clearFocus()
                                        onNext()
                                    },
                                    enabled = !state.busy,
                                    modifier = Modifier.weight(1f).testTag("focused-next"),
                                ) {
                                    Text(
                                        if (index == state.draft.exercises.lastIndex)
                                            "Review workout"
                                        else "Next exercise"
                                    )
                                }
                            }
                        }
                        item {
                            Text(
                                if (state.pending) "Saving entries…"
                                else "All entries saved on this device",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
            }
            if (!keyboard && !guide)
                Button(
                    onClick = {
                        focus.clearFocus()
                        when {
                            selectedSet == null -> onNext()
                            !selectedSet.done -> onComplete(selectedSet, true)
                            exercise.sets.any { !it.done } ->
                                onSelect(exercise.sets.first { !it.done })
                            else -> onNext()
                        }
                    },
                    enabled = !state.busy,
                    shape = RoundedCornerShape(24.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = TrackerPalette.Coral,
                            contentColor = TrackerPalette.OnCoral,
                        ),
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(16.dp)
                            .heightIn(min = 64.dp)
                            .testTag("focused-primary-action"),
                ) {
                    Icon(TrackerIcons.Check, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            selectedSet == null -> "Review workout"
                            !selectedSet.done ->
                                "Complete set ${exercise.sets.indexOf(selectedSet) + 1}"
                            exercise.sets.any { !it.done } -> "Next incomplete set"
                            index == state.draft.exercises.lastIndex -> "Review workout"
                            else -> "Next exercise"
                        }
                    )
                }
        }
    }
    val removal = exercise.sets.firstOrNull { it.key == removeKey }
    if (removal != null)
        AlertDialog(
            onDismissRequest = { removeKey = null },
            modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 560.dp).fillMaxWidth(),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            title = { Text("Remove entered set?") },
            text = {
                Text(
                    "This removes its load, reps, RPE and completion state from this exercise.",
                    Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        removeKey = null
                        if (exercise.sets.size > 1)
                            onChange(
                                exercise.copy(
                                    sets = exercise.sets.filterNot { it.key == removal.key }
                                )
                            )
                    },
                    enabled = !state.busy,
                ) {
                    Text("Remove set")
                }
            },
            dismissButton = { TextButton(onClick = { removeKey = null }) { Text("Keep set") } },
        )
}

@Composable
private fun FocusedGuidanceSummary(state: TrackerState, exercise: Exercise) {
    val guidance = state.guidance[exercise.name]
    val working =
        guidance?.last?.sets.orEmpty().filter {
            (it["warmup"] as? JsonPrimitive)?.booleanOrNull != true
        }
    fun weights() =
        working
            .mapNotNull {
                (it["weight"] as? JsonPrimitive)
                    ?.takeUnless { value -> value is JsonNull }
                    ?.content
                    ?.takeIf(String::isNotBlank)
            }
            .distinct()
            .joinToString(" / ")
            .ifEmpty { "—" }
    val metrics = buildList {
        guidance?.last?.let { add(Triple("Last", "${weights()} lb", it.date.orEmpty())) }
        guidance?.target?.let { add(Triple("Target", it.weight ?: "—", it.repsText.orEmpty())) }
        guidance?.pr?.let {
            add(
                Triple(
                    "PR",
                    "${progressNumber(it.weight)} × ${progressNumber(it.reps)}",
                    it.date.orEmpty(),
                )
            )
        }
    }
    if (metrics.isEmpty()) return
    Surface(shape = RoundedCornerShape(20.dp), color = trackerPanelColor()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            metrics.forEachIndexed { index, (label, value, detail) ->
                if (index > 0)
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .3f)
                    )
                FlowRow(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        label,
                        Modifier.width(52.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(value, style = MaterialTheme.typography.titleSmall)
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
