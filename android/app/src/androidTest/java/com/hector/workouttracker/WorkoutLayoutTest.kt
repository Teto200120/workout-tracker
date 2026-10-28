package com.hector.workouttracker

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.hector.workouttracker.core.designsystem.TrackerTheme
import com.hector.workouttracker.feature.session.WorkoutMotion
import com.hector.workouttracker.feature.session.focusWorkoutCard
import com.hector.workouttracker.feature.session.workoutPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WorkoutLayoutTest {
    @get:Rule
    val compose =
        createComposeRule(
            effectContext =
                object : MotionDurationScale {
                    override val scaleFactor = 1f
                }
        )

    @Test
    fun collapsingCardNeverOverlapsFollowingCardsOrFormFields() {
        var expanded by mutableStateOf(true)
        compose.setContent {
            TrackerTheme {
                LazyColumn(
                    Modifier.fillMaxWidth().height(600.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(key = "first") {
                        Column(workoutPlacement(true, null, "first")) {
                            Text("Exercise", Modifier.height(56.dp))
                            AnimatedVisibility(
                                expanded,
                                enter = WorkoutMotion.disclosureEnter(0),
                                exit = WorkoutMotion.disclosureExit(0),
                            ) {
                                Spacer(Modifier.height(220.dp))
                            }
                        }
                    }
                    for (key in listOf("second", "third")) item(key = key) {
                        Box(
                            workoutPlacement(true, null, key)
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag(key)
                        )
                    }
                    item(key = "form") {
                        Text("Exercise name", Modifier.height(56.dp).testTag("form"))
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { expanded = false }
        repeat(20) {
            compose.mainClock.advanceTimeByFrame()
            val second = compose.onNodeWithTag("second").fetchSemanticsNode().boundsInRoot
            val third = compose.onNodeWithTag("third").fetchSemanticsNode().boundsInRoot
            val form = compose.onNodeWithTag("form").fetchSemanticsNode().boundsInRoot
            assertTrue("Following cards overlap during collapse", second.bottom <= third.top)
            assertTrue("A moving card overlaps the form during collapse", third.bottom <= form.top)
        }
    }

    @Test
    fun bodyStaysBelowTheHeaderThroughoutExpansionAndCollapse() {
        var expanded by mutableStateOf(false)
        var headerBottom = 0f
        var bodyTop = 0f
        compose.setContent {
            TrackerTheme {
                Column {
                    Box(
                        Modifier.height(56.dp).onGloballyPositioned {
                            headerBottom = it.positionInRoot().y + it.size.height
                        }
                    )
                    AnimatedVisibility(
                        expanded,
                        enter = WorkoutMotion.disclosureEnter(0),
                        exit = WorkoutMotion.disclosureExit(0),
                    ) {
                        Box(
                            Modifier.height(220.dp).onGloballyPositioned {
                                bodyTop = it.positionInRoot().y
                            }
                        )
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        for (open in listOf(true, false)) {
            compose.runOnUiThread { expanded = open }
            repeat(20) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                compose.runOnUiThread {
                    assertEquals(
                        "Fields must not travel through the header",
                        headerBottom,
                        bodyTop,
                        1f,
                    )
                }
            }
        }
    }

    @Test
    fun focusingNextCardKeepsMovingTowardItsTargetWhilePreviousCardCollapses() {
        var next by mutableStateOf(false)
        compose.setContent {
            TrackerTheme {
                val list = rememberLazyListState()
                LaunchedEffect(next) {
                    if (next) {
                        withFrameNanos {}
                        withFrameNanos {}
                        list.focusWorkoutCard("second", 16f, true)
                    }
                }
                LazyColumn(Modifier.height(500.dp).fillMaxWidth(), state = list) {
                    item(key = "first") {
                        Column {
                            Text("First", Modifier.height(56.dp))
                            AnimatedVisibility(
                                !next,
                                enter = WorkoutMotion.disclosureEnter(0),
                                exit = WorkoutMotion.disclosureExit(0),
                            ) {
                                Spacer(Modifier.height(220.dp))
                            }
                        }
                    }
                    item(key = "second") {
                        Column(Modifier.testTag("focus-card")) {
                            Text("Second", Modifier.height(56.dp))
                            AnimatedVisibility(
                                next,
                                enter = WorkoutMotion.disclosureEnter(0),
                                exit = WorkoutMotion.disclosureExit(0),
                            ) {
                                Spacer(Modifier.height(220.dp))
                            }
                        }
                    }
                    item { Spacer(Modifier.height(600.dp)) }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { next = true }
        var previous = Float.MAX_VALUE
        repeat(24) {
            compose.mainClock.advanceTimeByFrame()
            val top = compose.onNodeWithTag("focus-card").fetchSemanticsNode().boundsInRoot.top
            assertTrue(
                "Focus scroll must not reverse while the old card shrinks",
                top <= previous + 1f,
            )
            assertTrue("Focus scroll must not pass its target and rebound", top >= 15f)
            previous = top
        }
        assertEquals(16f, previous, 1f)
    }
}
