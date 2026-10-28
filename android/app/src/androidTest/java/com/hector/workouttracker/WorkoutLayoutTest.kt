package com.hector.workouttracker

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.hector.workouttracker.core.designsystem.TrackerTheme
import com.hector.workouttracker.feature.session.WorkoutMotion
import com.hector.workouttracker.feature.session.workoutPlacement
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
                                enter =
                                    expandVertically(
                                        tween(
                                            WorkoutMotion.Disclosure,
                                            easing = WorkoutMotion.Easing,
                                        )
                                    ),
                                exit =
                                    shrinkVertically(
                                        tween(
                                            WorkoutMotion.Disclosure,
                                            easing = WorkoutMotion.Easing,
                                        )
                                    ),
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
}
