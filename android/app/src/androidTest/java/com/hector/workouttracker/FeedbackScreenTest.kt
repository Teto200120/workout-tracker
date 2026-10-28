package com.hector.workouttracker

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.hector.workouttracker.core.designsystem.TrackerTheme
import com.hector.workouttracker.feature.feedback.FeedbackScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FeedbackScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun browserFailureCanRetryAndReturnWithoutSubmittingOrChangingData() {
        val app = compose.activity.application as TrackerApplication
        val before = kotlinx.coroutines.runBlocking { app.backups.export("2026-10-24T12:00:00Z") }
        val draft = kotlinx.coroutines.runBlocking { app.repository.readDraft() }
        var calls = 0
        var back = false
        compose.activity.setContent {
            TrackerTheme { FeedbackScreen({ back = true }) { ++calls > 1 } }
        }
        assertEquals(0, calls)
        compose
            .onNodeWithTag("feedback-list")
            .performScrollToNode(hasTestTag("open-feedback-browser"))
        compose.onNodeWithTag("open-feedback-browser").performClick()
        compose.onNodeWithTag("feedback-open-error").assertExists()
        assertEquals(1, calls)
        compose.onNodeWithTag("open-feedback-browser").performClick()
        compose.onNodeWithTag("feedback-open-error").assertDoesNotExist()
        assertEquals(2, calls)
        compose.onNodeWithTag("feedback-list").performScrollToIndex(0)
        compose.onNodeWithText("Back to settings").performClick()
        assertTrue(back)
        assertEquals(
            before,
            kotlinx.coroutines.runBlocking { app.backups.export("2026-10-24T12:00:00Z") },
        )
        assertEquals(draft, kotlinx.coroutines.runBlocking { app.repository.readDraft() })
    }
}
