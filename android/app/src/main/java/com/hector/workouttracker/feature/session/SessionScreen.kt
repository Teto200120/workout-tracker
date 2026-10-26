package com.hector.workouttracker.feature.session

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hector.workouttracker.core.designsystem.*
import com.hector.workouttracker.core.designsystem.LocalCompletionFeedback
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.data.SetCopyRequest
import com.hector.workouttracker.domain.workoutMetrics
import com.hector.workouttracker.feature.catalog.ExerciseGuide
import com.hector.workouttracker.feature.catalog.ExercisePicker
import com.hector.workouttracker.feature.help.HelpTip
import com.hector.workouttracker.feature.history.WorkoutTags
import java.time.ZoneId
import kotlinx.coroutines.*

@Composable
internal fun SessionScreen(state: TrackerState, vm: TrackerViewModel, back: () -> Unit) {
    val draft = state.draft ?: return
    val completionFeedback = LocalCompletionFeedback.current
    var copyRequest by remember { mutableStateOf<SetCopyRequest?>(null) }
    var exerciseName by rememberSaveable { mutableStateOf("") }
    var discard by rememberSaveable { mutableStateOf(false) }
    var finish by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf(false) }
    var guide by rememberSaveable { mutableStateOf<String?>(null) }
    var orderMessage by rememberSaveable(draft.id) { mutableStateOf<String?>(null) }
    var movedKey by rememberSaveable(draft.id) { mutableStateOf<String?>(null) }
    var activeKey by rememberSaveable(draft.id) { mutableStateOf<String?>(null) }
    var chosenSets by
        rememberSaveable(draft.id) { mutableStateOf<Map<String, String>>(hashMapOf()) }
    var scrollTarget by remember { mutableStateOf<String?>(null) }
    val dialogModifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 560.dp).fillMaxWidth()
    val dialogProperties = DialogProperties(usePlatformDefaultWidth = false)
    val list = rememberLazyListState()
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val bootCount =
        remember(context) {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        }
    var wallNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var elapsedNow by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(draft.id) {
        val job =
            scope.launch {
                while (isActive) {
                    wallNow = System.currentTimeMillis()
                    elapsedNow = SystemClock.elapsedRealtime()
                    delay(1000)
                }
            }
        onPauseOrDispose { job.cancel() }
    }
    val seconds = elapsedMillis(draft, wallNow, elapsedNow, bootCount) / 1000
    val clock =
        if (seconds >= 3600)
            "%d:%02d:%02d"
                .format(java.util.Locale.US, seconds / 3600, seconds / 60 % 60, seconds % 60)
        else "%02d:%02d".format(java.util.Locale.US, seconds / 60, seconds % 60)
    LaunchedEffect(draft.id) {
        if (draft.exercises.any { it.collapsed })
            scrollTarget = draft.exercises.firstOrNull { !it.collapsed }?.key
    }
    LaunchedEffect(scrollTarget, draft.exercises.map { it.key }) {
        val index = draft.exercises.indexOfFirst { it.key == scrollTarget }
        if (index >= 0) list.scrollToItem(index + 2)
        scrollTarget = null
    }
    fun changeExercise(key: String, transform: (Exercise) -> Exercise) {
        vm.edit {
            it.copy(exercises = it.exercises.map { e -> if (e.key == key) transform(e) else e })
        }
    }
    fun selectedSet(exercise: Exercise) =
        exercise.sets.firstOrNull { it.key == chosenSets[exercise.key] }
            ?: exercise.sets.firstOrNull { !it.done }
            ?: exercise.sets.lastOrNull()
    fun select(exercise: Exercise, set: WorkingSet) {
        activeKey = exercise.key
        chosenSets = HashMap(chosenSets).apply { put(exercise.key, set.key) }
    }
    fun advance(key: String) {
        focus.clearFocus()
        val current = vm.state.value.draft ?: return
        if (current.exercises.none { it.key == key }) return
        val next = current.nextExerciseKey(key)
        vm.edit { if (next == null) it.collapseExercise(key) else it.openExercise(next) }
        activeKey = next
        if (next == null) finish = true else scrollTarget = next
    }
    val activeExercise =
        draft.exercises.firstOrNull { it.key == activeKey && !it.collapsed }
            ?: draft.exercises.firstOrNull { !it.collapsed }
    val activeSet = activeExercise?.let(::selectedSet)
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val completed = draft.exercises.sumOf { e -> e.sets.count { it.done } }
    val total = draft.exercises.sumOf { it.sets.size }
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < .5f
    val panel = if (dark) Color(0xFF223650) else Color(0xFFE5EDF7)
    Column(Modifier.fillMaxSize().background(sessionBackgroundBrush())) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().testTag("session-list"),
            state = list,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedIconButton(
                        onClick = back,
                        modifier = Modifier.size(48.dp),
                        border = BorderStroke(1.dp, colors.onSurfaceVariant.copy(alpha = .24f)),
                    ) {
                        Icon(TrackerIcons.Back, "Back to Today")
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextField(
                            draft.type,
                            { value -> vm.edit { it.copy(type = value) } },
                            label = {
                                Text("Workout name", style = MaterialTheme.typography.labelSmall)
                            },
                            textStyle = MaterialTheme.typography.titleLarge,
                            singleLine = true,
                            enabled = !state.busy,
                            colors =
                                TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "Active workout",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                            )
                            Text(
                                clock,
                                Modifier.testTag("session-clock"),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                    FilledTonalIconButton(
                        onClick = {
                            focus.clearFocus()
                            finish = true
                        },
                        enabled = !state.busy,
                        modifier = Modifier.size(48.dp).testTag("review-session"),
                    ) {
                        Icon(TrackerIcons.Check, "Finish workout")
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Column {
                            Text(
                                "Progress",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                            )
                            Text(
                                "$completed / $total sets",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.primary,
                            )
                        }
                        FilledTonalButton(
                            onClick = { picker = true },
                            enabled = !state.busy && draft.exercises.size < 100,
                        ) {
                            Icon(TrackerIcons.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Browse exercises")
                        }
                    }
                    LinearProgressIndicator(
                        progress = { if (total == 0) 0f else completed.toFloat() / total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        if (state.pending) "Saving entries…"
                        else "All entries saved on this device",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            itemsIndexed(draft.exercises, key = { _, e -> e.key }) { position, exercise ->
                val progress = exerciseProgress(exercise)
                val currentSet = selectedSet(exercise)
                var allSets by rememberSaveable(exercise.key) { mutableStateOf(false) }
                var chooseSet by rememberSaveable(exercise.key) { mutableStateOf(false) }
                Card(
                    colors = CardDefaults.cardColors(containerColor = panel),
                    shape = RoundedCornerShape(24.dp),
                    border =
                        BorderStroke(
                            1.dp,
                            if (activeExercise?.key == exercise.key)
                                colors.primary.copy(alpha = .65f)
                            else colors.outlineVariant,
                        ),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = colors.primary.copy(alpha = .14f),
                            ) {
                                Text(
                                    "${position + 1}",
                                    Modifier.padding(12.dp),
                                    color = colors.primary,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    exercise.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "Exercise ${position + 1} of ${draft.exercises.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.testTag("session-position-${exercise.key}"),
                                )
                                Text(
                                    "${progress.completedSets} / ${progress.workingSets} working sets completed" +
                                        if (progress.complete) " · Complete" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.testTag("session-progress-${exercise.key}"),
                                )
                            }
                            IconButton(
                                onClick = {
                                    focus.clearFocus()
                                    vm.edit {
                                        if (
                                            it.exercises
                                                .firstOrNull { e -> e.key == exercise.key }
                                                ?.collapsed == true
                                        )
                                            it.openExercise(exercise.key)
                                        else it.collapseExercise(exercise.key)
                                    }
                                    activeKey = exercise.key
                                    scrollTarget = exercise.key
                                },
                                enabled = !state.busy,
                                modifier =
                                    Modifier.testTag("toggle-session-${exercise.key}").semantics {
                                        contentDescription =
                                            if (exercise.collapsed) "Expand exercise"
                                            else "Collapse exercise"
                                        stateDescription =
                                            "${exercise.name}, ${if(exercise.collapsed) "Collapsed" else "Expanded"}"
                                    },
                            ) {
                                Icon(
                                    if (exercise.collapsed) TrackerIcons.Chevron
                                    else TrackerIcons.Remove,
                                    null,
                                )
                            }
                        }
                        if (exercise.collapsed) {
                            Column(
                                Modifier.testTag("session-summary-${exercise.key}"),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (progress.completedRows.isEmpty())
                                    Text(
                                        "No working sets marked completed.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant,
                                    )
                                progress.completedRows.forEach {
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                                if (progress.remainingCompletedRows > 0)
                                    Text(
                                        "${progress.remainingCompletedRows} more completed sets. Expand to review all sets."
                                    )
                            }
                        } else
                            Column(
                                Modifier.testTag("session-expanded-${exercise.key}"),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (movedKey == exercise.key)
                                    orderMessage?.let {
                                        Text(
                                            it,
                                            Modifier.semantics {
                                                liveRegion = LiveRegionMode.Polite
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (allSets)
                                        Text(
                                            "All sets",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = colors.onSurfaceVariant,
                                        )
                                    else
                                        Box {
                                            TextButton(
                                                onClick = { chooseSet = true },
                                                modifier =
                                                    Modifier.testTag(
                                                        "session-set-selector-${exercise.key}"
                                                    ),
                                            ) {
                                                Text(
                                                    "Set ${exercise.sets.indexOf(currentSet) + 1} of ${exercise.sets.size}",
                                                    style = MaterialTheme.typography.titleMedium,
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
                                                exercise.sets.forEachIndexed { index, set ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                "Set ${index + 1} · ${if(set.done) "Completed" else "Not completed"}"
                                                            )
                                                        },
                                                        onClick = {
                                                            select(exercise, set)
                                                            chooseSet = false
                                                        },
                                                        modifier =
                                                            Modifier.testTag(
                                                                "select-session-set-${set.key}"
                                                            ),
                                                    )
                                                }
                                            }
                                        }
                                    TextButton(
                                        onClick = { allSets = !allSets },
                                        modifier =
                                            Modifier.testTag("review-all-sets-${exercise.key}"),
                                    ) {
                                        Text(if (allSets) "Current set only" else "All sets")
                                    }
                                }
                                exercise.sets.forEachIndexed { index, set ->
                                    if (allSets || set.key == currentSet?.key)
                                        key(set.key) {
                                            SessionSetEntry(
                                                set,
                                                index,
                                                exercise.sets.size,
                                                state.profile.trackRpe,
                                                !state.busy,
                                                state.guidance[exercise.name]?.profile?.increment
                                                    ?: 5.0,
                                                set.key == currentSet?.key,
                                                showHeading = allSets,
                                                onFocused = { select(exercise, set) },
                                                onChange = { value ->
                                                    changeExercise(exercise.key) { e ->
                                                        e.copy(
                                                            sets =
                                                                e.sets.map {
                                                                    if (it.key == set.key) value
                                                                    else it
                                                                }
                                                        )
                                                    }
                                                },
                                                onComplete = { value ->
                                                    focus.clearFocus()
                                                    changeExercise(exercise.key) { e ->
                                                        e.copy(
                                                            sets =
                                                                e.sets.map {
                                                                    if (it.key == set.key)
                                                                        it.copy(done = value)
                                                                    else it
                                                                }
                                                        )
                                                    }
                                                    if (value) {
                                                        chosenSets =
                                                            HashMap(chosenSets).apply {
                                                                remove(exercise.key)
                                                            }
                                                        completionFeedback()
                                                    }
                                                },
                                            )
                                        }
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(
                                        onClick = {
                                            changeExercise(exercise.key) {
                                                it.copy(sets = it.sets + WorkingSet())
                                            }
                                        },
                                        enabled = !state.busy && exercise.sets.size < 200,
                                    ) {
                                        Icon(TrackerIcons.Add, null, Modifier.size(16.dp))
                                        Text("Add set")
                                    }
                                    TextButton(onClick = { guide = exercise.name }) {
                                        Icon(TrackerIcons.Help, null, Modifier.size(16.dp))
                                        Text("Exercise guide")
                                    }
                                }
                                OutlinedButton(
                                    onClick = { advance(exercise.key) },
                                    enabled = !state.busy,
                                    modifier =
                                        Modifier.fillMaxWidth()
                                            .testTag("next-session-${exercise.key}"),
                                ) {
                                    Text(
                                        if (position == draft.exercises.lastIndex) "Review workout"
                                        else "Next exercise"
                                    )
                                }
                                TrainingGuidanceCard(state, exercise, vm) { target ->
                                    vm.setCopyRequest(exercise.key, target)?.let {
                                        if (it.replacesInput) copyRequest = it else vm.applySets(it)
                                    }
                                }
                                if (draft.exercises.size > 1)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(-1 to "up", 1 to "down").forEach { (direction, label)
                                            ->
                                            TextButton(
                                                onClick = {
                                                    focus.clearFocus()
                                                    vm.edit {
                                                        it.moveExercise(exercise.key, direction)
                                                    }
                                                    orderMessage =
                                                        "${exercise.name} moved to exercise ${position + direction + 1} of ${draft.exercises.size}."
                                                    movedKey = exercise.key
                                                    activeKey = exercise.key
                                                    scrollTarget = exercise.key
                                                },
                                                enabled =
                                                    !state.busy &&
                                                        position + direction in
                                                            draft.exercises.indices,
                                                modifier =
                                                    Modifier.testTag(
                                                            "move-session-$label-${exercise.key}"
                                                        )
                                                        .semantics {
                                                            contentDescription =
                                                                "Move ${exercise.name} $label"
                                                        },
                                            ) {
                                                Text("Move $label")
                                            }
                                        }
                                    }
                                TextButton(
                                    onClick = {
                                        vm.edit {
                                            it.copy(
                                                exercises =
                                                    it.exercises.filterNot { e ->
                                                        e.key == exercise.key
                                                    }
                                            )
                                        }
                                    },
                                    enabled = !state.busy,
                                ) {
                                    Text("Remove exercise", color = colors.error)
                                }
                            }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Custom exercise", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        exerciseName,
                        { exerciseName = it },
                        label = { Text("Exercise name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !state.busy,
                    )
                    OutlinedButton(
                        onClick = { if (vm.addExercise(exerciseName)) exerciseName = "" },
                        enabled =
                            visibleName(exerciseName, 120) &&
                                !state.busy &&
                                draft.exercises.size < 100,
                    ) {
                        Text("Add exercise")
                    }
                }
            }
            item {
                OutlinedTextField(
                    draft.notes,
                    { v -> vm.edit { it.copy(notes = v) } },
                    label = { Text("Workout notes") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.busy,
                )
            }
            item { HelpTip("activeWorkoutBasics") }
            if (state.profile.trackRpe) item { HelpTip("rpeBasics") }
            item {
                OutlinedButton(
                    onClick = { finish = true },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Text("Finish workout")
                }
            }
            item {
                TextButton(onClick = { discard = true }, enabled = !state.busy) {
                    Text("Discard workout", color = colors.error)
                }
            }
        }
        if (draft.exercises.isNotEmpty() && !keyboardVisible) {
            Surface(
                Modifier.fillMaxWidth(),
                color = if (dark) Color(0xFF0A1F37) else colors.surface,
            ) {
                Button(
                    onClick = {
                        focus.clearFocus()
                        when {
                            activeExercise == null || activeSet == null -> finish = true
                            !activeSet.done -> {
                                changeExercise(activeExercise.key) { e ->
                                    e.copy(
                                        sets =
                                            e.sets.map {
                                                if (it.key == activeSet.key) it.copy(done = true)
                                                else it
                                            }
                                    )
                                }
                                chosenSets =
                                    HashMap(chosenSets).apply { remove(activeExercise.key) }
                                completionFeedback()
                            }
                            activeExercise.sets.any { !it.done } ->
                                chosenSets =
                                    HashMap(chosenSets).apply { remove(activeExercise.key) }
                            else -> advance(activeExercise.key)
                        }
                    },
                    enabled = !state.busy,
                    shape = RoundedCornerShape(20.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = TrackerPalette.Coral,
                            contentColor = TrackerPalette.OnCoral,
                        ),
                    modifier =
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .testTag(
                                if (activeSet != null && !activeSet.done) "complete-current-set"
                                else "session-primary-action"
                            ),
                ) {
                    Icon(TrackerIcons.Check, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            when {
                                activeExercise == null || activeSet == null -> "Review workout"
                                !activeSet.done ->
                                    "Complete set ${activeExercise.sets.indexOf(activeSet) + 1}"
                                activeExercise.sets.any { !it.done } -> "Next incomplete set"
                                draft.nextExerciseKey(activeExercise.key) != null -> "Next exercise"
                                else -> "Review workout"
                            }
                        )
                        activeExercise?.let {
                            Text(
                                it.name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
    if (picker) ExercisePicker(state, onDismiss = { picker = false }, onAdd = vm::addExercise)
    guide?.let { name -> ExerciseGuide(name, state.catalog, onDismiss = { guide = null }) }
    copyRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { copyRequest = null },
            modifier = dialogModifier,
            properties = dialogProperties,
            title = { Text("Replace entered sets?") },
            text = {
                Text(
                    if (request.target)
                        "Replace the sets for ${request.exercise.name} with the suggested working sets? Previous RPE values are copied and completion starts unchecked."
                    else
                        "Replace the sets for ${request.exercise.name} with its last logged sets, including warm-ups and RPE? Completion starts unchecked.",
                    Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        copyRequest = null
                        vm.applySets(request)
                    }
                ) {
                    Text("Replace sets")
                }
            },
            dismissButton = {
                TextButton(onClick = { copyRequest = null }) { Text("Keep entered sets") }
            },
        )
    }
    if (discard)
        AlertDialog(
            onDismissRequest = { discard = false },
            modifier = dialogModifier,
            properties = dialogProperties,
            title = { Text("Discard this workout?") },
            text = {
                Text(
                    "This removes the active draft. Saved History stays intact.",
                    Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        discard = false
                        vm.discard()
                    }
                ) {
                    Text("Discard")
                }
            },
            dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep workout") } },
        )
    if (finish) {
        val context = LocalContext.current
        val duration =
            elapsedMillis(
                draft,
                System.currentTimeMillis(),
                SystemClock.elapsedRealtime(),
                Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1),
            ) / 60000
        val metrics =
            workoutMetrics(
                workoutPayload(draft, System.currentTimeMillis(), duration, ZoneId.systemDefault())
            )
        AlertDialog(
            onDismissRequest = { finish = false },
            modifier = dialogModifier,
            properties = dialogProperties,
            title = { Text("Save to History?") },
            text = {
                LazyColumn(
                    Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            "${metrics.workingSets} working sets · ${metrics.completedSets} completed · ${metrics.duration.toInt()} min"
                        )
                        Text(
                            "Volume: ${String.format(java.util.Locale.US, "%.1f", metrics.volume)} lb"
                        )
                        Text(
                            "Your sets, notes and tags will be saved, and this active workout will close."
                        )
                    }
                    item {
                        WorkoutTags(draft.tags, !state.busy) { tags ->
                            vm.edit { it.copy(tags = tags) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        finish = false
                        vm.finish()
                    }
                ) {
                    Text("Save workout")
                }
            },
            dismissButton = { TextButton(onClick = { finish = false }) { Text("Keep editing") } },
        )
    }
}

@Composable
private fun SessionSetEntry(
    set: WorkingSet,
    index: Int,
    count: Int,
    trackRpe: Boolean,
    enabled: Boolean,
    increment: Double,
    current: Boolean,
    showHeading: Boolean,
    onFocused: () -> Unit,
    onChange: (WorkingSet) -> Unit,
    onComplete: (Boolean) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().testTag("session-set-${set.key}"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showHeading)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Set ${index + 1} of $count",
                    style = MaterialTheme.typography.titleMedium,
                    color =
                        if (current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                )
                if (set.done)
                    Text(
                        "Completed set",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
        SetEntryField(
            set.weight,
            { onChange(set.copy(weight = it)) },
            "Weight (lb)",
            increment,
            KeyboardOptions(keyboardType = KeyboardType.Decimal),
            enabled = enabled,
            onFocused = onFocused,
        )
        SetEntryField(
            set.reps,
            { onChange(set.copy(reps = it)) },
            "Reps",
            1.0,
            KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = enabled,
            onFocused = onFocused,
        )
        if (trackRpe)
            SetEntryField(
                set.rpe,
                { onChange(set.copy(rpe = it, historicalRpeZero = false)) },
                "RPE",
                .5,
                KeyboardOptions(keyboardType = KeyboardType.Decimal),
                enabled = enabled,
                maximum = 10.0,
                minimum = 1.0,
                onFocused = onFocused,
            )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.heightIn(min = 48.dp)
                    .toggleable(
                        set.done,
                        enabled = enabled,
                        role = Role.Checkbox,
                        onValueChange = onComplete,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(set.done, null)
                Text("Completed", style = MaterialTheme.typography.bodyMedium)
            }
            Row(
                Modifier.heightIn(min = 48.dp)
                    .toggleable(
                        set.warmup,
                        enabled = enabled,
                        role = Role.Checkbox,
                        onValueChange = { onChange(set.copy(warmup = it)) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(set.warmup, null)
                Text("Warm-up", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun sessionBackgroundBrush(): Brush =
    Brush.verticalGradient(
        if (MaterialTheme.colorScheme.background.luminance() < .5f)
            listOf(Color(0xFF19304D), Color(0xFF102540), Color(0xFF071B31))
        else listOf(Color(0xFFF1F5FA), MaterialTheme.colorScheme.background)
    )
