package com.hector.workouttracker.feature.session

import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
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
import kotlin.math.roundToInt
import kotlinx.coroutines.*

@Composable
internal fun SessionScreen(state: TrackerState, vm: TrackerViewModel, back: () -> Unit) {
    val draft = state.draft ?: return
    val completionFeedback = LocalCompletionFeedback.current
    var copyRequest by remember { mutableStateOf<SetCopyRequest?>(null) }
    var focusedKey by rememberSaveable(draft.id) { mutableStateOf<String?>(null) }
    fun closeExercise() {
        focusedKey = null
    }
    BackHandler(enabled = focusedKey != null) { closeExercise() }
    var exerciseName by rememberSaveable { mutableStateOf("") }
    var discard by rememberSaveable { mutableStateOf(false) }
    var finish by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf(false) }
    var guide by rememberSaveable { mutableStateOf<String?>(null) }
    var activeKey by rememberSaveable(draft.id) { mutableStateOf<String?>(null) }
    var allSetViews by
        rememberSaveable(draft.id) { mutableStateOf<Map<String, Boolean>>(hashMapOf()) }
    var guidanceViews by
        rememberSaveable(draft.id) { mutableStateOf<Map<String, Boolean>>(hashMapOf()) }
    var chosenSets by
        rememberSaveable(draft.id) { mutableStateOf<Map<String, String>>(hashMapOf()) }
    var scrollTarget by remember { mutableStateOf<String?>(null) }
    val dialogModifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 560.dp).fillMaxWidth()
    val dialogProperties = DialogProperties(usePlatformDefaultWidth = false)
    // Start at the current card instead of laying out the heading and then jumping past it.
    val initialExercise = draft.exercises.indexOfFirst { !it.collapsed }
    val list =
        rememberLazyListState(
            initialFirstVisibleItemIndex = if (initialExercise < 0) 0 else initialExercise + 2
        )
    val currentDraft by rememberUpdatedState(draft)
    val drag = rememberSessionDrag(draft.id, draft.exercises.map { it.key }, list, !state.busy)
    BackHandler(enabled = drag.key != null) { drag.cancel(draft.exercises.map { it.key }) }
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val bootCount =
        remember(context) {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        }
    val scope = rememberCoroutineScope()
    LaunchedEffect(draft.id) { scrollTarget = draft.exercises.firstOrNull { !it.collapsed }?.key }
    LaunchedEffect(scrollTarget, draft.exercises.map { it.collapsed }) {
        val key = scrollTarget ?: return@LaunchedEffect
        val index = draft.exercises.indexOfFirst { it.key == key && !it.collapsed }
        if (index >= 0 && drag.key == null && focusedKey == null) {
            list.scrollToItem(index + 2)
            // Wait for the expanded row to be measured before placing it in the usable focus band.
            withFrameNanos {}
            withFrameNanos {}
            list.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == key }
                ?.let { item ->
                    val available = list.layoutInfo.viewportEndOffset
                    val desiredTop = ((available - item.size).coerceAtLeast(0) * .44f)
                    list.scrollBy(item.offset - desiredTop)
                }
        }
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
        if (next == null) finish = true
        else {
            scrollTarget = next
            if (focusedKey != null) focusedKey = next
        }
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
    val focusedExercise = draft.exercises.firstOrNull { it.key == focusedKey }
    LaunchedEffect(draft.exercises.map { it.key }) {
        if (focusedKey != null && focusedExercise == null) focusedKey = null
    }
    fun moveActions(exercise: Exercise): List<CustomAccessibilityAction> {
        val position = draft.exercises.indexOfFirst { it.key == exercise.key }
        return listOf(-1 to "up", 1 to "down").mapNotNull { (direction, label) ->
            if (position + direction !in draft.exercises.indices) null
            else
                CustomAccessibilityAction("Move $label") {
                    if (state.busy || drag.key != null) false
                    else {
                        focus.clearFocus()
                        vm.edit { it.moveExercise(exercise.key, direction) }
                        true
                    }
                }
        }
    }
    val renderExercise: @Composable (Int, Exercise, Modifier, Modifier) -> Unit =
        { position, exercise, cardModifier, handleModifier ->
            val progress = exerciseProgress(exercise)
            val currentSet = selectedSet(exercise)
            val allSets = allSetViews[exercise.key] == true
            var more by rememberSaveable(exercise.key) { mutableStateOf(false) }
            val guidance = guidanceViews[exercise.key] == true
            var chooseSet by rememberSaveable(exercise.key) { mutableStateOf(false) }
            fun toggleExercise() {
                focus.clearFocus()
                vm.edit {
                    if (it.exercises.firstOrNull { e -> e.key == exercise.key }?.collapsed == true)
                        it.openExercise(exercise.key)
                    else it.collapseExercise(exercise.key)
                }
                activeKey = exercise.key
                if (exercise.collapsed) scrollTarget = exercise.key
            }
            Card(
                modifier =
                    cardModifier
                        .fillMaxWidth()
                        .testTag("session-card-${exercise.key}")
                        .semantics { customActions = moveActions(exercise) }
                        .then(
                            if (exercise.collapsed)
                                Modifier.clickable(
                                    enabled = !state.busy,
                                    onClick = ::toggleExercise,
                                )
                            else Modifier
                        ),
                colors = CardDefaults.cardColors(containerColor = panel),
                shape =
                    if (activeExercise?.key == exercise.key)
                        RoundedCornerShape(
                            topStart = 24.dp,
                            topEnd = 24.dp,
                            bottomEnd = 24.dp,
                            bottomStart = 10.dp,
                        )
                    else RoundedCornerShape(24.dp),
                elevation =
                    CardDefaults.cardElevation(
                        defaultElevation =
                            if (drag.key == exercise.key) 12.dp
                            else if (activeExercise?.key == exercise.key) 4.dp else 1.dp
                    ),
                border =
                    BorderStroke(
                        if (activeExercise?.key == exercise.key) 2.dp else 1.dp,
                        if (activeExercise?.key == exercise.key) colors.primary.copy(alpha = .65f)
                        else colors.outlineVariant,
                    ),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clickable(
                                    enabled = !state.busy,
                                    role = Role.Button,
                                    onClick = ::toggleExercise,
                                )
                                .testTag("toggle-session-${exercise.key}")
                                .semantics {
                                    contentDescription =
                                        if (exercise.collapsed) "Expand exercise"
                                        else "Collapse exercise"
                                    stateDescription =
                                        "${exercise.name}, ${if (exercise.collapsed) "Collapsed" else "Expanded"}"
                                },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            shape =
                                RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomEnd = 16.dp,
                                    bottomStart = 6.dp,
                                ),
                            color =
                                if (activeExercise?.key == exercise.key) colors.primary
                                else colors.primaryContainer,
                        ) {
                            Text(
                                "${position + 1}",
                                Modifier.padding(12.dp),
                                color =
                                    if (activeExercise?.key == exercise.key) colors.onPrimary
                                    else colors.onPrimaryContainer,
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                exercise.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "${progress.completedSets}/${progress.workingSets} sets" +
                                    if (progress.complete) " · Complete" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                modifier =
                                    Modifier.testTag("session-summary-${exercise.key}").semantics {
                                        stateDescription =
                                            "Exercise ${position + 1} of ${draft.exercises.size}"
                                    },
                            )
                        }
                        if (draft.exercises.size > 1)
                            Box(
                                Modifier.size(48.dp)
                                    .then(handleModifier)
                                    .testTag("drag-session-${exercise.key}"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    TrackerIcons.Grip,
                                    null,
                                    Modifier.size(24.dp),
                                    tint = colors.primary,
                                )
                            }
                        Icon(
                            if (exercise.collapsed) TrackerIcons.Chevron else TrackerIcons.Remove,
                            null,
                            Modifier.size(24.dp),
                        )
                    }
                    if (!exercise.collapsed)
                        Column(
                            Modifier.testTag("session-expanded-${exercise.key}"),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FlowRow(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
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
                                            Icon(TrackerIcons.Chevron, null, Modifier.size(18.dp))
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
                                    onClick = {
                                        allSetViews =
                                            HashMap(allSetViews).apply {
                                                put(exercise.key, !allSets)
                                            }
                                    },
                                    modifier = Modifier.testTag("review-all-sets-${exercise.key}"),
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
                                                                if (it.key == set.key) value else it
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
                            FlowRow(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                FilledTonalIconButton(
                                    onClick = {
                                        focus.clearFocus()
                                        activeKey = exercise.key
                                        focusedKey = exercise.key
                                    },
                                    enabled = !state.busy,
                                    modifier = Modifier.testTag("focus-session-${exercise.key}"),
                                ) {
                                    Icon(
                                        TrackerIcons.Focus,
                                        "Exercise details",
                                        Modifier.size(20.dp),
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        changeExercise(exercise.key) {
                                            it.copy(sets = it.sets + WorkingSet())
                                        }
                                    },
                                    enabled = !state.busy && exercise.sets.size < 200,
                                ) {
                                    Icon(TrackerIcons.Add, null, Modifier.size(18.dp))
                                    Text("Add set")
                                }
                                IconButton(
                                    onClick = { advance(exercise.key) },
                                    enabled = !state.busy,
                                    modifier = Modifier.testTag("next-session-${exercise.key}"),
                                ) {
                                    Icon(
                                        if (position == draft.exercises.lastIndex)
                                            TrackerIcons.Check
                                        else TrackerIcons.Chevron,
                                        if (position == draft.exercises.lastIndex) "Review workout"
                                        else "Next exercise",
                                    )
                                }
                                Box {
                                    IconButton(
                                        onClick = { more = true },
                                        modifier = Modifier.testTag("session-more-${exercise.key}"),
                                    ) {
                                        Icon(TrackerIcons.More, "More exercise actions")
                                    }
                                    DropdownMenu(
                                        expanded = more,
                                        onDismissRequest = { more = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Exercise guide") },
                                            onClick = {
                                                more = false
                                                guide = exercise.name
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (guidance) "Hide training guidance"
                                                    else "Training guidance"
                                                )
                                            },
                                            onClick = {
                                                more = false
                                                guidanceViews =
                                                    HashMap(guidanceViews).apply {
                                                        put(exercise.key, !guidance)
                                                    }
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text("Remove exercise", color = colors.error)
                                            },
                                            enabled = !state.busy,
                                            onClick = {
                                                more = false
                                                vm.edit {
                                                    it.copy(
                                                        exercises =
                                                            it.exercises.filterNot { e ->
                                                                e.key == exercise.key
                                                            }
                                                    )
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                            if (guidance)
                                TrainingGuidanceCard(state, exercise, vm) { target ->
                                    vm.setCopyRequest(exercise.key, target)?.let {
                                        if (it.replacesInput) copyRequest = it else vm.applySets(it)
                                    }
                                }
                        }
                }
            }
        }
    if (focusedExercise != null) {
        FocusedExerciseScreen(
            state,
            vm,
            focusedExercise,
            selectedSet(focusedExercise),
            onBack = {
                focus.clearFocus()
                closeExercise()
            },
            onSelect = { select(focusedExercise, it) },
            onChange = { value -> changeExercise(focusedExercise.key) { value } },
            onComplete = { set, done ->
                focus.clearFocus()
                changeExercise(focusedExercise.key) { e ->
                    e.copy(
                        sets = e.sets.map { if (it.key == set.key) it.copy(done = done) else it }
                    )
                }
                if (done) {
                    chosenSets = HashMap(chosenSets).apply { remove(focusedExercise.key) }
                    completionFeedback()
                }
            },
            onCopy = { target ->
                vm.setCopyRequest(focusedExercise.key, target)?.let {
                    if (it.replacesInput) copyRequest = it else vm.applySets(it)
                }
            },
            onPrevious = {
                val index = draft.exercises.indexOfFirst { it.key == focusedExercise.key }
                draft.exercises.getOrNull(index - 1)?.let {
                    focus.clearFocus()
                    focusedKey = it.key
                    activeKey = it.key
                }
            },
            onNext = { advance(focusedExercise.key) },
        )
    } else
        Column(Modifier.fillMaxSize().background(sessionBackgroundBrush())) {
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                LazyColumn(
                    Modifier.fillMaxSize()
                        .testTag("session-list")
                        .sessionDrag(
                            drag,
                            !state.busy && draft.exercises.size > 1,
                            keys = { currentDraft.exercises.map { it.key } },
                            onStart = {
                                focus.clearFocus()
                                scrollTarget = null
                            },
                            onDrop = { keys -> vm.edit { it.reorderExercises(keys) } },
                        ),
                    state = list,
                    contentPadding =
                        PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        TrainingHero {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedIconButton(
                                    onClick = back,
                                    modifier = Modifier.size(48.dp),
                                    border =
                                        BorderStroke(
                                            1.dp,
                                            colors.onSurfaceVariant.copy(alpha = .24f),
                                        ),
                                ) {
                                    Icon(TrackerIcons.Back, "Back to Today")
                                }
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    TextField(
                                        draft.type,
                                        { value -> vm.edit { it.copy(type = value) } },
                                        label = {
                                            Text(
                                                "Workout name",
                                                style = MaterialTheme.typography.labelSmall,
                                            )
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
                                        SessionClock(draft, bootCount)
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
                                modifier =
                                    Modifier.fillMaxWidth().semantics {
                                        stateDescription =
                                            if (state.pending) "Saving entries"
                                            else "Saved on this device"
                                    },
                            )
                        }
                    }
                    val rows = draft.exercises.associateBy { it.key }
                    itemsIndexed(drag.preview.mapNotNull(rows::get), key = { _, e -> e.key }) {
                        position,
                        exercise ->
                        if (drag.key == exercise.key) {
                            Surface(
                                Modifier.fillMaxWidth()
                                    .height(with(LocalDensity.current) { drag.height.toDp() }),
                                shape = RoundedCornerShape(24.dp),
                                color = colors.primary.copy(alpha = .08f),
                                border = BorderStroke(1.dp, colors.primary.copy(alpha = .3f)),
                            ) {}
                        } else
                            renderExercise(
                                position,
                                exercise,
                                if (state.interactions.animations) Modifier.animateItem()
                                else Modifier,
                                Modifier.sessionDragHandle(
                                    drag,
                                    exercise.key,
                                    "Drag exercise ${position + 1}: ${exercise.name}",
                                    moveActions(exercise),
                                ),
                            )
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                drag.key?.let { key ->
                    draft.exercises
                        .firstOrNull { it.key == key }
                        ?.let { exercise ->
                            renderExercise(
                                drag.preview.indexOf(key),
                                exercise,
                                Modifier.offset {
                                        IntOffset(
                                            0,
                                            (drag.pointerY - drag.touchOffset).roundToInt(),
                                        )
                                    }
                                    .padding(horizontal = 16.dp)
                                    .height(with(LocalDensity.current) { drag.height.toDp() })
                                    .zIndex(2f)
                                    .graphicsLayer { shadowElevation = 12.dp.toPx() },
                                Modifier,
                            )
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
                                                    if (it.key == activeSet.key)
                                                        it.copy(done = true)
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
                                    draft.nextExerciseKey(activeExercise.key) != null ->
                                        "Next exercise"
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
internal fun SessionSetEntry(
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

/**
 * Timer state is read here so ticking never recomposes exercise entry fields or the focused view.
 */
@Composable
private fun SessionClock(draft: SessionDraft, bootCount: Int) {
    val scope = rememberCoroutineScope()
    var wallNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var elapsedNow by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
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
    Text(
        clock,
        Modifier.testTag("session-clock"),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
