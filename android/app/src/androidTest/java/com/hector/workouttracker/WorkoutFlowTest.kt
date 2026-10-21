package com.hector.workouttracker

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
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
        screenshot("today")
        compose.onNodeWithText("Start workout").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Exercise name").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Exercise name").performScrollTo().performTextInput("Bench Press")
        compose.onNodeWithText("Add exercise").performScrollTo().performClick()
        compose.onNodeWithText("Weight (lb)").performScrollTo().performTextInput("100")
        compose.onNodeWithText("Reps").performScrollTo().performTextInput("5")
        screenshot("session")
        compose.onNodeWithText("Back to Today").performScrollTo().performClick()
        compose.onNodeWithText("Resume workout").performClick()
        compose.onNodeWithText("Weight (lb)").performScrollTo().assertTextContains("100")
        compose.onNodeWithTag("session-list").performScrollToNode(hasText("Finish workout"))
        compose.onNodeWithText("Finish workout").performClick()
        compose.onNodeWithText("Save workout").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("History").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("History").performClick()
        compose.onNodeWithText("Bench Press · 1 sets").assertExists()
        compose.onNodeWithText("Volume: 500.0").assertExists()
        screenshot("history")
    }

    private fun screenshot(name: String) {
        val directory =
            File(
                InstrumentationRegistry.getInstrumentation()
                    .targetContext
                    .getExternalFilesDir(null),
                "qa",
            )
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use {
            compose
                .onRoot()
                .captureToImage()
                .asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
