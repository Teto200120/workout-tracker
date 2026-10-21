package com.hector.workouttracker

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.hector.workouttracker.core.backup.BackupCodec
import com.hector.workouttracker.core.designsystem.TrackerTheme
import com.hector.workouttracker.feature.home.TrackerApp
import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WorkoutFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private var nextUri: Uri? = null
    private val pickerActions = mutableListOf<String?>()

    @Test
    fun onboardingSessionNavigationAndSavedHistory() {
        try {
            workoutFlow()
        } catch (failure: Throwable) {
            runCatching { screenshot("failure") }
            throw failure
        }
    }

    private fun workoutFlow() {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val fixture =
            Json.parseToJsonElement(
                    testContext.assets.open("baseline.json").bufferedReader().use { it.readText() }
                )
                .jsonObject
                .getValue("backup")
                .jsonObject
        val registry =
            object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(
                    requestCode: Int,
                    contract: ActivityResultContract<I, O>,
                    input: I,
                    options: ActivityOptionsCompat?,
                ) {
                    pickerActions += contract.createIntent(compose.activity, input).action
                    dispatchResult(
                        requestCode,
                        if (nextUri == null) Activity.RESULT_CANCELED else Activity.RESULT_OK,
                        Intent().setData(nextUri),
                    )
                }
            }
        val owner =
            object : ActivityResultRegistryOwner {
                override val activityResultRegistry = registry
            }
        compose.activity.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                TrackerTheme { TrackerApp() }
            }
        }
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Import PWA backup").fetchSemanticsNodes().isNotEmpty()
        }
        // Cancellation and rejected files leave onboarding and storage untouched.
        compose.onNodeWithText("Import PWA backup").performScrollTo().performClick()
        nextUri = Uri.parse("content://com.hector.workouttracker.test.documents/future.json")
        compose.onNodeWithText("Import PWA backup").performScrollTo().performClick()
        compose.waitUntil(10000) {
            compose
                .onAllNodesWithText("Backup was created by a newer application version.")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithText("Dismiss").performClick()
        nextUri = Uri.parse("content://com.hector.workouttracker.test.documents/fixture.json")
        compose.onNodeWithText("Import PWA backup").performScrollTo().performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Review backup import").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Workouts: 2 new, 0 replaced, 0 unchanged").assertExists()
        screenshot("import-preview")
        compose.onNodeWithText("Cancel").performClick()

        // Legacy backups require name setup, but their RPE preference must survive it.
        nextUri = Uri.parse("content://com.hector.workouttracker.test.documents/legacy.json")
        compose.onNodeWithText("Import PWA backup").performScrollTo().performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Review backup import").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Import backup").performClick()
        compose.waitUntil(10000) {
            compose
                .onAllNodesWithText("Backup imported. Your other saved records were kept.")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithTag("setup-list").performScrollToNode(hasTestTag("setup-rpe"))
        compose.onNodeWithTag("setup-rpe").assertIsOff()
        screenshot("legacy-onboarding")
        // Re-import must reset an unsaved toggle even when the stored preference is unchanged.
        compose.onNodeWithTag("setup-rpe").performClick().assertIsOn()
        compose.onNodeWithTag("setup-list").performScrollToNode(hasText("Import PWA backup"))
        compose.onNodeWithText("Import PWA backup").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Review backup import").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Import backup").performClick()
        compose.waitUntil(10000) {
            compose
                .onAllNodesWithText("Backup imported. Your other saved records were kept.")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithTag("setup-list").performScrollToNode(hasTestTag("setup-rpe"))
        compose.onNodeWithTag("setup-rpe").assertIsOff()

        compose.onNodeWithTag("setup-list").performScrollToNode(hasText("Your name"))
        compose.onNodeWithText("Your name").performTextInput("Fixture User")
        compose.onNodeWithTag("setup-list").performScrollToNode(hasText("Let's begin"))
        compose.onNodeWithText("Let's begin").performClick()
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
        compose.onNodeWithTag("session-list").performScrollToNode(hasText("Back to Today"))
        compose.onNodeWithText("Back to Today").performClick()
        compose.onNodeWithText("Resume workout").performClick()
        compose.onNodeWithText("Weight (lb)").performScrollTo().assertTextContains("100")
        compose.onNodeWithTag("session-list").performScrollToNode(hasText("Finish workout"))
        compose.onNodeWithText("Finish workout").performClick()
        compose.onNodeWithText("Save workout").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("History").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("History").performClick()
        compose.onAllNodesWithText("Bench Press · 1 set").onFirst().assertExists()
        compose.onAllNodesWithText("Volume: 500.0").onFirst().assertExists()
        screenshot("history")
        compose.onNodeWithText("You").performClick()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Import PWA backup"))
        nextUri = Uri.parse("content://com.hector.workouttracker.test.documents/fixture.json")
        compose.onNodeWithText("Import PWA backup").performScrollTo().performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Review backup import").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Import backup").performClick()
        compose.waitUntil(10000) {
            compose
                .onAllNodesWithText("Backup imported. Your other saved records were kept.")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        nextUri = Uri.parse("content://com.hector.workouttracker.test.documents/export.json")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Export backup"))
        compose.onNodeWithText("Export backup").performClick()
        compose.waitUntil(10000) {
            compose
                .onAllNodesWithText("Backup file created. Active workouts are not included.")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        val exported =
            BackupCodec.parse(
                compose.activity.contentResolver
                    .openInputStream(
                        Uri.parse("content://com.hector.workouttracker.test.documents/export.json")
                    )!!
                    .bufferedReader()
                    .use { it.readText() }
            )
        assertEquals(3, exported.workouts.size)
        assertEquals(
            fixture.getValue("workouts").jsonArray.toList(),
            exported.workouts.filter {
                it["id"] == JsonPrimitive(0) || it["id"] == JsonPrimitive("0")
            },
        )
        val native =
            exported.workouts.single {
                it["id"] != JsonPrimitive(0) && it["id"] != JsonPrimitive("0")
            }
        val nativeSet =
            native
                .getValue("exercises")
                .jsonArray
                .single()
                .jsonObject
                .getValue("sets")
                .jsonArray
                .single()
                .jsonObject
        assertEquals(JsonPrimitive("100"), nativeSet.getValue("weight"))
        assertEquals(JsonPrimitive("5"), nativeSet.getValue("reps"))
        assertTrue(pickerActions.contains(Intent.ACTION_OPEN_DOCUMENT))
        assertTrue(pickerActions.contains(Intent.ACTION_CREATE_DOCUMENT))
        screenshot("backup-settings")

        // Launch an imported routine without changing its names, duplicate rows or typed ID.
        nextUri = Uri.parse("content://com.hector.workouttracker.test.documents/training.json")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Import PWA backup"))
        compose.onNodeWithText("Import PWA backup").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Review backup import").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Import backup").performClick()
        compose.waitUntil(10000) {
            compose
                .onAllNodesWithText("Backup imported. Your other saved records were kept.")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithText("Today").performClick()
        compose.onNodeWithText("Use saved routine").performClick()
        screenshot("routine-chooser")
        compose.onNodeWithText("Training fixture").performClick()
        val vm =
            compose.runOnIdle {
                androidx.lifecycle
                    .ViewModelProvider(compose.activity)[
                        com.hector.workouttracker.feature.session.TrackerViewModel::class.java]
            }
        compose.waitUntil(10000) { vm.state.value.draft != null || vm.state.value.error != null }
        assertNull(vm.state.value.error)
        assertEquals("Training fixture", vm.state.value.draft!!.type)
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("session-list").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("ACTIVE WORKOUT").assertIsDisplayed()
        compose.onNodeWithText("Workout name").assertTextContains("Training fixture")
        compose.onAllNodesWithText("Exercise guide").onFirst().performScrollTo().performClick()
        compose
            .onNodeWithText("Reference movement: Barbell Bench Press - Medium Grip")
            .assertExists()
        screenshot("exercise-guide")
        compose.onNodeWithText("Back", substring = false).performClick()
        compose.onNodeWithTag("session-list").performScrollToNode(hasText("Browse exercises"))
        compose.onNodeWithText("Browse exercises").performClick()
        compose.onNodeWithTag("exercise-search").performTextInput("curl")
        compose.onNodeWithText("Show filters").performClick()
        compose.onNodeWithText("Equipment", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("dumbbell", substring = false).performScrollTo().performClick()
        compose.waitUntil(10000) {
            runCatching {
                    compose
                        .onNodeWithTag("exercise-picker")
                        .performScrollToNode(hasText("Guide for Alternate Hammer Curl"))
                }
                .isSuccess
        }
        screenshot("exercise-picker")
        compose.onNodeWithText("Guide for Alternate Hammer Curl").performScrollTo().performClick()
        compose.onNodeWithTag("add-from-guide").performClick()
        compose
            .onNodeWithTag("session-list")
            .performScrollToNode(hasText("Alternate Hammer Curl", substring = false))
        compose.onNodeWithText("Alternate Hammer Curl", substring = false).assertExists()
        compose.onNodeWithTag("session-list").performScrollToNode(hasText("Workout name"))
        compose
            .onAllNodesWithText("Reps", substring = false)
            .onFirst()
            .performScrollTo()
            .performTextInput("8")
        compose.onNodeWithTag("session-list").performScrollToNode(hasText("Back to Today"))
        compose.onNodeWithText("Back to Today").performClick()
        compose.onNodeWithText("Resume workout").performClick()
        compose.onNodeWithText("Workout name").assertTextContains("Training fixture")
        compose.onNodeWithTag("session-list").performScrollToNode(hasText("Finish workout"))
        compose.onNodeWithText("Finish workout").performClick()
        compose.onNodeWithText("Save workout").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("You", substring = false).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("You", substring = false).performClick()
        nextUri = Uri.parse("content://com.hector.workouttracker.test.documents/export.json")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Export backup"))
        compose.onNodeWithText("Export backup").performClick()
        compose.waitUntil(10000) {
            compose
                .onAllNodesWithText("Backup file created. Active workouts are not included.")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        val trainingExport =
            BackupCodec.parse(
                compose.activity.contentResolver.openInputStream(nextUri!!)!!.bufferedReader().use {
                    it.readText()
                }
            )
        assertEquals(4, trainingExport.workouts.size)
        val training =
            trainingExport.workouts.single {
                it["originRoutineId"] == JsonPrimitive(0) &&
                    it["type"] == JsonPrimitive("Training fixture")
            }
        assertEquals(
            listOf(
                "Flat Bench Press",
                "My custom lift",
                "Flat Bench Press",
                "Alternate Hammer Curl",
            ),
            training.getValue("exercises").jsonArray.map {
                it.jsonObject.getValue("name").jsonPrimitive.content
            },
        )
        training.getValue("exercises").jsonArray.forEach {
            assertEquals(setOf("name", "notes", "sets"), it.jsonObject.keys)
        }
        assertEquals(
            JsonPrimitive(true),
            trainingExport.routines.single().getValue("extension").jsonObject.getValue("keep"),
        )
    }

    private fun screenshot(name: String) {
        compose.runOnIdle {
            val input =
                compose.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                    as android.view.inputmethod.InputMethodManager
            input.hideSoftInputFromWindow(compose.activity.window.decorView.windowToken, 0)
        }
        compose.waitForIdle()
        val directory =
            File(
                InstrumentationRegistry.getInstrumentation()
                    .targetContext
                    .getExternalFilesDir(null),
                "qa",
            )
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use {
            requireNotNull(
                    InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                ) {
                    "The emulator screen could not be captured."
                }
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
