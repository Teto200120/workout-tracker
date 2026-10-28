package com.hector.workouttracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.hector.workouttracker.core.designsystem.TrackerTheme
import com.hector.workouttracker.feature.session.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Exercises the production pointer handler and lazy-list state without writing workout data. */
class SessionDragTest {
    // CI disables system animations; this rule controls motion with its own test clock.
    @get:Rule
    val compose =
        createComposeRule(
            effectContext =
                object : MotionDurationScale {
                    override val scaleFactor = 1f
                }
        )
    private lateinit var drag: SessionDragState
    private var keys by mutableStateOf((0..19).map(Int::toString))
    private var commits = 0

    private fun content(animations: Boolean = true) {
        compose.setContent {
            TrackerTheme {
                val list = rememberLazyListState()
                drag = rememberSessionDrag("test", keys, list, true)
                LazyColumn(
                    modifier =
                        Modifier.height(400.dp)
                            .fillMaxWidth()
                            .testTag("drag-list")
                            .sessionDrag(
                                drag,
                                true,
                                { keys },
                                {},
                                {
                                    keys = it
                                    commits++
                                },
                                animations,
                            ),
                    state = list,
                ) {
                    items(drag.preview, key = { it }) { key ->
                        Text(
                            key,
                            Modifier.fillMaxWidth()
                                .height(if (key == "1") 100.dp else 72.dp)
                                .sessionDragHandle(drag, key, "Move $key", emptyList())
                                .testTag("grip-$key"),
                        )
                    }
                }
            }
        }
    }

    private fun move(cancelled: Boolean) {
        val from = compose.onNodeWithTag("grip-0").fetchSemanticsNode().boundsInRoot
        val to = compose.onNodeWithTag("grip-2").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("grip-0").performTouchInput {
            down(center)
            repeat(20) { moveBy(Offset(0f, (to.center.y - from.center.y) / 20), 16) }
            if (cancelled) cancel() else up()
        }
    }

    @Test
    fun releaseKeepsLiftedCardUntilSettledAndCommitsOnce() {
        content()
        compose.mainClock.autoAdvance = false
        move(false)
        compose.mainClock.advanceTimeByFrame()
        compose.runOnUiThread {
            assertEquals(listOf("1", "2", "0"), keys.take(3))
            assertEquals(1, commits)
            assertEquals("0", drag.key)
            assertTrue(drag.settling)
            assertEquals(0, drag.list.firstVisibleItemIndex)
            assertEquals(0, drag.list.firstVisibleItemScrollOffset)
        }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle {
            assertNull(drag.key)
            assertFalse(drag.settling)
            assertEquals(keys, drag.preview)
            assertEquals(1, commits)
        }
    }

    @Test
    fun cancelledPreviewNeverChangesSavedOrder() {
        content()
        val original = keys
        move(true)
        compose.runOnIdle {
            assertEquals(original, keys)
            assertEquals(original, drag.preview)
            assertEquals(0, commits)
            assertNull(drag.key)
        }
    }

    @Test
    fun disabledAnimationsDropImmediately() {
        content(animations = false)
        move(false)
        compose.runOnIdle {
            assertEquals(listOf("1", "2", "0"), keys.take(3))
            assertNull(drag.key)
            assertFalse(drag.settling)
            assertEquals(1, commits)
        }
    }

    @Test
    fun edgeScrollKeepsGestureWhenOriginalRowLeavesViewport() {
        content()
        compose.mainClock.autoAdvance = false
        val viewport = compose.onNodeWithTag("drag-list").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("grip-0").performTouchInput {
            down(center)
            moveTo(Offset(center.x, viewport.height - 12))
        }
        compose.mainClock.advanceTimeBy(1600)
        compose.runOnUiThread {
            assertEquals("0", drag.key)
            assertTrue(drag.list.firstVisibleItemIndex > 0)
            assertEquals(0, commits)
        }
        compose.onNodeWithTag("drag-list").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle {
            assertEquals(1, commits)
            assertEquals(20, keys.toSet().size)
            assertNull(drag.key)
        }
    }
}
