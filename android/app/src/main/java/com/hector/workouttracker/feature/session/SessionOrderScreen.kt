package com.hector.workouttracker.feature.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.hector.workouttracker.core.designsystem.*
import com.hector.workouttracker.core.model.SessionDraft
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Drag changes only a local preview; one keyed permutation is persisted on a completed drop. */
@Composable
internal fun SessionOrderScreen(
    draft: SessionDraft,
    enabled: Boolean,
    animations: Boolean,
    onBack: () -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    val currentKeys = draft.exercises.map { it.key }
    var preview by remember(draft.id) { mutableStateOf(currentKeys) }
    var draggedKey by remember(draft.id) { mutableStateOf<String?>(null) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    var touchOffset by remember { mutableFloatStateOf(0f) }
    var message by remember { mutableStateOf<String?>(null) }
    val latestKeys by rememberUpdatedState(currentKeys)
    val commit by rememberUpdatedState(onReorder)
    val canMove by rememberUpdatedState(enabled)
    val list = rememberLazyListState()
    val density = LocalDensity.current
    val edge = with(density) { 64.dp.toPx() }
    val maxScroll = with(density) { 12.dp.toPx() }
    val handleHalf = with(density) { 24.dp.toPx() }
    val rows = draft.exercises.associateBy { it.key }
    val displayed = preview.mapNotNull(rows::get)
    fun cancel() {
        draggedKey = null
        preview = latestKeys
    }
    fun moveToPointer() {
        val key = draggedKey ?: return
        val from = preview.indexOf(key)
        val visible = list.layoutInfo.visibleItemsInfo
        val target =
            visible
                .filter { preview.indexOf(it.key) > from && pointerY > it.offset + it.size / 2f }
                .maxByOrNull { preview.indexOf(it.key) }
                ?: visible
                    .filter {
                        preview.indexOf(it.key) in 0 until from &&
                            pointerY < it.offset + it.size / 2f
                    }
                    .minByOrNull { preview.indexOf(it.key) }
                ?: return
        val to = preview.indexOf(target.key)
        if (from >= 0 && to >= 0)
            preview = preview.toMutableList().apply { add(to, removeAt(from)) }
    }
    fun moveAccessible(key: String, direction: Int): Boolean {
        if (!canMove || draggedKey != null) return false
        val from = preview.indexOf(key)
        val to = from + direction
        if (from < 0 || to !in preview.indices) return false
        val result = preview.toMutableList().apply { add(to, removeAt(from)) }
        preview = result
        commit(result)
        message = "${rows[key]?.name} moved to exercise ${to + 1} of ${result.size}."
        return true
    }
    LaunchedEffect(currentKeys, enabled) {
        if (!enabled || currentKeys.toSet() != preview.toSet()) cancel()
        else if (draggedKey == null) preview = currentKeys
    }
    LaunchedEffect(draggedKey) {
        while (draggedKey != null && isActive) {
            val info = list.layoutInfo
            val delta =
                when {
                    pointerY < info.viewportStartOffset + edge ->
                        -maxScroll *
                            ((info.viewportStartOffset + edge - pointerY) / edge).coerceIn(0f, 1f)
                    pointerY > info.viewportEndOffset - edge ->
                        maxScroll *
                            ((pointerY - info.viewportEndOffset + edge) / edge).coerceIn(0f, 1f)
                    else -> 0f
                }
            if (delta != 0f) {
                list.scrollBy(delta)
                moveToPointer()
            }
            delay(16)
        }
    }
    Column(Modifier.fillMaxSize().background(sessionBackgroundBrush()).testTag("exercise-order")) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilledTonalIconButton(
                onClick = {
                    cancel()
                    onBack()
                },
                shape = CircleShape,
                modifier = Modifier.size(48.dp).testTag("order-back"),
            ) {
                Icon(TrackerIcons.Back, "Back to workout")
            }
            Text(
                "Exercise order",
                Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
            )
            TextButton(
                onClick = onBack,
                enabled = draggedKey == null,
                modifier = Modifier.testTag("order-done"),
            ) {
                Text("Done")
            }
        }
        Text(
            "Touch and hold a handle, then drag. Order saves when you drop.",
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        message?.let {
            Text(
                it,
                Modifier.padding(horizontal = 20.dp).semantics {
                    liveRegion = LiveRegionMode.Polite
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().testTag("exercise-order-list"),
            state = list,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(displayed, key = { _, e -> e.key }) { index, exercise ->
                val dragging = draggedKey == exercise.key
                val actions = buildList {
                    if (index > 0)
                        add(
                            CustomAccessibilityAction("Move up") {
                                moveAccessible(exercise.key, -1)
                            }
                        )
                    if (index < displayed.lastIndex)
                        add(
                            CustomAccessibilityAction("Move down") {
                                moveAccessible(exercise.key, 1)
                            }
                        )
                }
                val offset =
                    if (dragging)
                        list.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.key == exercise.key }
                            ?.let { pointerY - touchOffset - it.offset } ?: 0f
                    else 0f
                Card(
                    modifier =
                        Modifier.fillMaxWidth()
                            .zIndex(if (dragging) 1f else 0f)
                            .then(if (animations && !dragging) Modifier.animateItem() else Modifier)
                            .graphicsLayer { translationY = offset }
                            .testTag("order-card-${exercise.key}")
                            .semantics { customActions = actions },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = trackerPanelColor()),
                    border =
                        BorderStroke(
                            if (dragging) 2.dp else 1.dp,
                            if (dragging) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                        ),
                    elevation =
                        CardDefaults.cardElevation(defaultElevation = if (dragging) 8.dp else 1.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${exercise.sets.size} sets",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box(
                            Modifier.size(48.dp)
                                .testTag("drag-session-${exercise.key}")
                                .semantics {
                                    contentDescription =
                                        "Drag exercise ${index + 1}: ${exercise.name}"
                                    customActions = actions
                                }
                                .pointerInput(exercise.key, enabled) {
                                    if (enabled)
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { start ->
                                                val item =
                                                    list.layoutInfo.visibleItemsInfo.firstOrNull {
                                                        it.key == exercise.key
                                                    }
                                                if (item != null && canMove) {
                                                    touchOffset =
                                                        item.size / 2f + start.y - handleHalf
                                                    pointerY = item.offset + touchOffset
                                                    draggedKey = exercise.key
                                                }
                                            },
                                            onDrag = { change, amount ->
                                                if (draggedKey == exercise.key && canMove) {
                                                    change.consume()
                                                    pointerY += amount.y
                                                    moveToPointer()
                                                }
                                            },
                                            onDragEnd = {
                                                if (
                                                    draggedKey == exercise.key &&
                                                        canMove &&
                                                        preview != latestKeys
                                                ) {
                                                    commit(preview)
                                                    message =
                                                        "${exercise.name} moved to exercise ${preview.indexOf(exercise.key) + 1} of ${preview.size}."
                                                }
                                                draggedKey = null
                                            },
                                            onDragCancel = { cancel() },
                                        )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                TrackerIcons.Grip,
                                null,
                                Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}
