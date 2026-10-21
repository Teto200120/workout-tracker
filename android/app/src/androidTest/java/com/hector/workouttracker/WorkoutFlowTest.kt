package com.hector.workouttracker

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class WorkoutFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun onboardingSessionNavigationAndSavedHistory() {
        compose.onNodeWithText("Your name").performTextInput("Fixture User")
        compose.onNodeWithText("Let's begin").performScrollTo().performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Start workout").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Start workout").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Exercise name").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Exercise name").performScrollTo().performTextInput("Bench Press")
        compose.onNodeWithText("Add exercise").performScrollTo().performClick()
        compose.onNodeWithText("Weight (lb)").performScrollTo().performTextInput("100")
        compose.onNodeWithText("Reps").performScrollTo().performTextInput("5")
        compose.onNodeWithText("Back to Today").performScrollTo().performClick()
        compose.onNodeWithText("Resume workout").performClick()
        compose.onNodeWithText("Weight (lb)").performScrollTo().assertTextContains("100")
        compose.onNodeWithText("Finish workout").performScrollTo().performClick()
        compose.onNodeWithText("Save workout").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("History").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("History").performClick()
        compose.onNodeWithText("Bench Press · 1 sets").assertExists()
        compose.onNodeWithText("Volume: 500.0").assertExists()
    }
}
