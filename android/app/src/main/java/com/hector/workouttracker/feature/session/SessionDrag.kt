package com.hector.workouttracker.feature.session

import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** The list owns the gesture, so moving a row outside lazy composition never loses the drag. */
@Stable
internal class SessionDragState(val list: LazyListState, initialKeys: List<String>) {
    var preview by mutableStateOf(initialKeys)
    var key by mutableStateOf<String?>(null)
    var pointerY by mutableFloatStateOf(0f)
    var touchOffset by mutableFloatStateOf(0f)
    var height by mutableIntStateOf(0)
    var viewportSize = IntSize.Zero
    var coordinates: LayoutCoordinates? = null
    val handles = mutableMapOf<String, LayoutCoordinates>()

    fun cancel(keys: List<String>) {
        key = null
        preview = keys
    }

    fun move() {
        val from = preview.indexOf(key)
        if (from < 0) return
        val visible = list.layoutInfo.visibleItemsInfo.filter { it.key in preview }
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
        preview = preview.toMutableList().apply { add(to, removeAt(from)) }
    }
}

internal fun Modifier.sessionDragHandle(
    state: SessionDragState,
    key: String,
    label: String,
    actions: List<CustomAccessibilityAction>,
): Modifier =
    onGloballyPositioned { state.handles[key] = it }
        .semantics {
            contentDescription = label
            customActions = actions
        }

@Composable
internal fun rememberSessionDrag(
    id: String,
    keys: List<String>,
    list: LazyListState,
    enabled: Boolean,
): SessionDragState {
    val drag = remember(id, list) { SessionDragState(list, keys) }
    val density = LocalDensity.current
    val edge = with(density) { 64.dp.toPx() }
    val speed = with(density) { 12.dp.toPx() }
    LaunchedEffect(keys, enabled) {
        if (!enabled || keys.toSet() != drag.preview.toSet()) drag.cancel(keys)
        else if (drag.key == null) drag.preview = keys
    }
    LaunchedEffect(drag.key) {
        while (drag.key != null && isActive) {
            val info = list.layoutInfo
            val delta =
                when {
                    drag.pointerY < edge ->
                        -speed * ((edge - drag.pointerY) / edge).coerceIn(0f, 1f)
                    drag.pointerY > info.viewportEndOffset - edge ->
                        speed *
                            ((drag.pointerY - info.viewportEndOffset + edge) / edge).coerceIn(
                                0f,
                                1f,
                            )
                    else -> 0f
                }
            if (delta != 0f) {
                list.scrollBy(delta)
                drag.move()
            }
            delay(16)
        }
    }
    DisposableEffect(drag) { onDispose { drag.cancel(keys) } }
    return drag
}

/**
 * Only the 48dp grip starts a drag. Card taps, text entry and normal scrolling stay independent.
 */
@Composable
internal fun Modifier.sessionDrag(
    state: SessionDragState,
    enabled: Boolean,
    keys: () -> List<String>,
    onStart: () -> Unit,
    onDrop: (List<String>) -> Unit,
): Modifier {
    val currentKeys by rememberUpdatedState(keys)
    val start by rememberUpdatedState(onStart)
    val drop by rememberUpdatedState(onDrop)
    return onGloballyPositioned {
            if (state.key != null && state.viewportSize != it.size) state.cancel(currentKeys())
            state.viewportSize = it.size
            state.coordinates = it
        }
        .pointerInput(state, enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down =
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val parent = state.coordinates ?: return@awaitEachGesture
                val visible = state.list.layoutInfo.visibleItemsInfo.map { it.key }
                val hit =
                    state.handles.entries
                        .firstOrNull { (key, coords) ->
                            key in visible &&
                                coords.isAttached &&
                                parent.isAttached &&
                                parent
                                    .localBoundingBoxOf(coords, clipBounds = false)
                                    .contains(down.position)
                        }
                        ?.key ?: return@awaitEachGesture
                val item =
                    state.list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == hit }
                        ?: return@awaitEachGesture
                down.consume()
                state.preview = currentKeys()
                state.touchOffset = down.position.y - item.offset
                state.pointerY = down.position.y
                state.height = item.size
                state.key = hit
                start()
                try {
                    var dropped = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        // Compose marks synthesized cancellation releases consumed from the start.
                        // Treating those as a physical lift would commit a preview on rotation.
                        if (change.isConsumed) break
                        change.consume()
                        if (!change.pressed) {
                            dropped = true
                            break
                        }
                        if (state.key == hit) {
                            state.pointerY = change.position.y
                            state.move()
                        }
                    }
                    if (
                        dropped &&
                            state.key == hit &&
                            currentKeys().toSet() == state.preview.toSet() &&
                            state.preview != currentKeys()
                    ) {
                        drop(state.preview)
                    }
                } finally {
                    state.cancel(currentKeys())
                }
            }
        }
}
