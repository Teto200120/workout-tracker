package com.hector.workouttracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hector.workouttracker.core.designsystem.TrackerTheme
import com.hector.workouttracker.feature.session.SetEntryField
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WorkoutControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun numericValueHasItsOwnTargetAndTypingNeverTriggersAStepper() {
        var value by mutableStateOf("10")
        compose.setContent {
            TrackerTheme {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    SetEntryField(
                        value,
                        { value = it },
                        "Reps",
                        1.0,
                        KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
        }
        val input = compose.onNodeWithContentDescription("Reps")
        val minus = compose.onNodeWithContentDescription("Decrease Reps")
        val plus = compose.onNodeWithContentDescription("Increase Reps")
        val fieldBounds = input.fetchSemanticsNode().boundsInRoot
        assertTrue(fieldBounds.left >= minus.fetchSemanticsNode().boundsInRoot.right)
        assertTrue(fieldBounds.right <= plus.fetchSemanticsNode().boundsInRoot.left)
        input.performClick().assertIsFocused()
        compose.runOnIdle { assertEquals("10", value) }
        input.performTextReplacement("12")
        input.assertTextContains("12")
        plus.performClick()
        compose.runOnIdle { assertEquals("13", value) }
        minus.performClick()
        input.assertTextContains("12")
        compose.runOnIdle { assertEquals("12", value) }
    }
}
