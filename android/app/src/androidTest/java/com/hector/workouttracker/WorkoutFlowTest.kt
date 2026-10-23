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
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.hector.workouttracker.core.backup.BackupCodec
import com.hector.workouttracker.core.designsystem.TrackerTheme
import com.hector.workouttracker.feature.home.TrackerApp
import java.io.File
import kotlinx.coroutines.flow.first
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

    // Reuse this composition path after Activity recreation so the test registry and
    // saveable navigation keys match the original test content.
    private fun installTestContent(activity: MainActivity, owner: ActivityResultRegistryOwner) {
        activity.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                TrackerTheme { TrackerApp() }
            }
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
        installTestContent(compose.activity, owner)
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
        closeSoftKeyboard()
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
        closeSoftKeyboard()
        screenshot("session")
        compose.onNodeWithTag("session-list").performScrollToNode(hasText("Back to Today"))
        compose.onNodeWithText("Back to Today").performClick()
        compose.onNodeWithText("Resume workout").performClick()
        compose.onNodeWithText("Weight (lb)").performScrollTo().assertTextContains("100")
        compose.onNodeWithTag("session-list").performScrollToNode(hasText("Finish workout"))
        compose.onNodeWithText("Finish workout").performClick()
        compose.onNodeWithTag("workout-tag-Good session").performScrollTo().performClick()
        screenshot("completion-summary")
        compose.onNodeWithText("Save workout").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("History").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("home-list").performScrollToNode(hasTestTag("workout-completion"))
        compose.onNodeWithTag("workout-completion").assertExists()
        screenshot("completion-result")
        compose.onNodeWithText("Dismiss summary").performScrollTo().performClick()
        compose.onNodeWithText("History").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Bench Press · 1 set").fetchSemanticsNodes().isNotEmpty()
        }
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
        assertEquals(JsonArray(listOf(JsonPrimitive("Good session"))), native.getValue("tags"))
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
        compose.onNodeWithTag("home-list").performScrollToNode(hasText("Use saved routine"))
        compose
            .onNodeWithText("Use saved routine")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
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
        routineEditorFlow(vm, owner)
    }

    private fun routineEditorFlow(
        vm: com.hector.workouttracker.feature.session.TrackerViewModel,
        owner: ActivityResultRegistryOwner,
    ) {
        fun editorText(text: String) {
            compose
                .onNodeWithTag("routine-editor")
                .performScrollToNode(hasText(text, substring = false))
            compose
                .onNodeWithText(text, substring = false)
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
        }
        fun editorTag(tag: String) {
            compose.onNodeWithTag("routine-editor").performScrollToNode(hasTestTag(tag))
            compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().performClick()
        }
        fun name(value: String) {
            compose.onNodeWithTag("routine-editor").performScrollToNode(hasTestTag("routine-name"))
            compose.onNodeWithTag("routine-name").performTextReplacement(value)
            closeSoftKeyboard()
        }
        fun waitLibrary() =
            compose.waitUntil(10000) {
                compose.onAllNodesWithTag("routine-library").fetchSemanticsNodes().isNotEmpty()
            }
        fun openImported() {
            compose
                .onNodeWithTag("routine-library")
                .performScrollToNode(hasTestTag("edit-routine-number:0"))
            compose
                .onNodeWithTag("edit-routine-number:0")
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            compose.waitUntil(10000) {
                compose.onAllNodesWithTag("routine-name").fetchSemanticsNodes().isNotEmpty()
            }
        }
        compose.onNodeWithText("Today", substring = false).performClick()
        compose.onNodeWithTag("home-list").performScrollToNode(hasText("Manage routines"))
        compose
            .onNodeWithText("Manage routines")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        waitLibrary()
        openImported()
        name("Edited fixture")
        editorTag("routine-change-1")
        compose.onNodeWithText("Back to routine").performClick() // Cancel changes nothing.
        editorTag("routine-change-1")
        compose.onNodeWithTag("exercise-search").performTextReplacement("Fixture replacement")
        closeSoftKeyboard()
        compose
            .onNodeWithTag("add-search-name")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        editorText("Browse exercises")
        compose.onNodeWithTag("exercise-search").performTextReplacement("flat bench press")
        closeSoftKeyboard()
        compose
            .onNodeWithTag("add-search-name")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose.waitUntil(10000) {
            compose
                .onAllNodesWithText("Keep a repeated exercise?")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithText("Keep a repeated exercise?").assertExists()
        compose.onNodeWithText("Keep both").performClick()
        editorTag("routine-up-1")
        editorTag("routine-remove-3")
        name("Edited fixture")
        screenshot("routine-editor")
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { installTestContent(it, owner) }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("routine-name").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("routine-name").assertTextContains("Edited fixture")
        editorText("Save routine")
        waitLibrary()
        compose.onNodeWithText("Routine saved.").assertExists()
        compose.waitUntil(10000) { vm.state.value.routines.any { it.name == "Edited fixture" } }
        val app = compose.activity.application as TrackerApplication
        val saved =
            kotlinx.coroutines.runBlocking {
                requireNotNull(app.routineRepository.read(JsonPrimitive(0)))
            }
        val expected = listOf("Fixture replacement", "Flat Bench Press", "Flat Bench Press")
        assertEquals(expected, saved.exercises)
        assertEquals(
            JsonPrimitive(true),
            Json.parseToJsonElement(saved.record.payload)
                .jsonObject
                .getValue("extension")
                .jsonObject
                .getValue("keep"),
        )
        openImported()
        name("Discard me")
        editorText("Back to routines")
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithTag("routine-name").assertTextContains("Discard me")
        editorText("Back to routines")
        compose.onNodeWithText("Discard edits").performClick()
        waitLibrary()
        assertEquals(
            saved,
            kotlinx.coroutines.runBlocking { app.routineRepository.read(JsonPrimitive(0)) },
        )

        // A competing save must retain the visible draft until explicit reload/discard.
        openImported()
        name("Unsaved stale")
        kotlinx.coroutines.runBlocking {
            app.routineRepository.save(
                com.hector.workouttracker.data.RoutineDraft.edit(saved)
                    .copy(name = "Latest fixture")
            )
        }
        editorText("Save routine")
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Reload saved routine").fetchSemanticsNodes().isNotEmpty()
        }
        name("Unsaved stale")
        compose.onNodeWithText("Reload saved routine").performClick()
        compose.onNodeWithText("Reload", substring = false).performClick()
        compose.waitUntil(10000) {
            runCatching {
                    compose.onNodeWithTag("routine-name").assertTextContains("Latest fixture")
                }
                .isSuccess
        }
        name("Edited fixture")
        editorText("Save routine")
        waitLibrary()
        compose.onNodeWithText("Create routine").performScrollTo().performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("routine-name").fetchSemanticsNodes().isNotEmpty()
        }
        name(" edited   FIXTURE ")
        editorText("Save routine")
        compose.waitUntil(10000) {
            compose
                .onAllNodesWithText("Another routine already uses this name.")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        name("Native empty")
        editorText("Save routine")
        waitLibrary()
        compose.waitUntil(10000) { vm.state.value.routines.any { it.name == "Native empty" } }
        compose.onNodeWithTag("routine-library").performScrollToNode(hasText("Delete Native empty"))
        compose
            .onNodeWithText("Delete Native empty")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose.onNodeWithText("Cancel", substring = false).performClick()
        compose
            .onNodeWithText("Delete Native empty")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose.onNodeWithText("Delete routine", substring = false).performClick()
        compose.waitUntil(10000) { vm.state.value.routines.size == 1 }
        screenshot("routine-library")
        val output =
            File(compose.activity.getExternalFilesDir(null), "qa/routine-editor-export.json")
        output.writeText(
            kotlinx.coroutines.runBlocking { app.backups.export("2026-10-21T12:00:00Z") }
        )
        compose.onNodeWithTag("routine-library").performScrollToNode(hasText("Back to Today"))
        compose.onNodeWithText("Back to Today").performClick()
        compose.onNodeWithTag("home-list").performScrollToNode(hasText("Use saved routine"))
        compose
            .onNodeWithText("Use saved routine")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose.onNodeWithText("Edited fixture", substring = false).performClick()
        compose.waitUntil(10000) { vm.state.value.draft != null }
        val active = vm.state.value.draft!!
        assertEquals(JsonPrimitive(0), active.originRoutineId)
        assertEquals(expected, active.exercises.map { it.name })
        compose.onNodeWithText("Back to Today").performClick()
        compose.onNodeWithTag("home-list").performScrollToNode(hasText("Manage routines"))
        compose
            .onNodeWithText("Manage routines")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        waitLibrary()
        compose
            .onNodeWithTag("routine-library")
            .performScrollToNode(hasTestTag("delete-routine-number:0"))
        compose
            .onNodeWithTag("delete-routine-number:0")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose.onNodeWithText("Delete routine", substring = false).performClick()
        compose.waitUntil(10000) { vm.state.value.routines.isEmpty() }
        assertEquals(active, vm.state.value.draft)
        assertEquals(4, vm.state.value.workouts.size)

        // Previewing/cancelling an empty-library seed writes nothing, including during a session.
        val historyBeforeSeed = vm.state.value.workouts
        fun previewStarters() {
            compose
                .onNodeWithTag("routine-library")
                .performScrollToNode(hasTestTag("preview-starter-routines"))
            compose
                .onNodeWithTag("preview-starter-routines")
                .performScrollTo()
                .assertIsEnabled()
                .performClick()
            compose.waitUntil(10000) {
                compose.onAllNodesWithTag("starter-preview").fetchSemanticsNodes().isNotEmpty()
            }
        }
        previewStarters()
        screenshot("starter-preview")
        assertTrue(vm.state.value.routines.isEmpty())
        compose.onNodeWithTag("starter-preview").performScrollToNode(hasText("Back to routines"))
        compose.onNodeWithText("Back to routines").performClick()
        waitLibrary()
        assertTrue(vm.state.value.routines.isEmpty())
        previewStarters()
        compose.onNodeWithTag("add-starter-routines").assertIsDisplayed().performClick()
        waitLibrary()
        compose.waitUntil(10000) { vm.state.value.routines.size == 6 }
        compose.onNodeWithTag("preview-starter-routines").assertDoesNotExist()
        assertEquals(active, vm.state.value.draft)
        assertEquals(historyBeforeSeed, vm.state.value.workouts)
        val seeded = vm.state.value.routines
        assertEquals(6, seeded.map { it.id }.distinct().size)
        assertEquals(0, seeded.single { it.name == "Custom" }.exercises.size)
        val starterOutput =
            File(compose.activity.getExternalFilesDir(null), "qa/starter-export.json")
        starterOutput.writeText(
            kotlinx.coroutines.runBlocking { app.backups.export("2026-10-22T12:00:00Z") }
        )
        compose.onNodeWithTag("routine-library").performScrollToNode(hasText("Create routine"))
        screenshot("starter-library")

        // Recreating the screen keeps the committed library without seeding another copy.
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { installTestContent(it, owner) }
        waitLibrary()
        assertEquals(seeded, vm.state.value.routines)
        assertEquals(active, vm.state.value.draft)
        scheduleFlow(vm, owner)
    }

    private fun scheduleFlow(
        vm: com.hector.workouttracker.feature.session.TrackerViewModel,
        owner: ActivityResultRegistryOwner,
    ) {
        val app = compose.activity.application as TrackerApplication
        val original = kotlinx.coroutines.runBlocking { app.scheduleRepository.read() }
        val active = requireNotNull(vm.state.value.draft)
        fun scheduleTag(tag: String) {
            compose.onNodeWithTag("schedule-editor").performScrollToNode(hasTestTag(tag))
            compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().performClick()
        }
        fun kind(index: Int, value: String) {
            scheduleTag("schedule-kind-$index")
            compose.onNodeWithTag("choose-kind-$value").performClick()
        }
        fun backFromSchedule() {
            compose
                .onNodeWithTag("schedule-editor")
                .performScrollToNode(hasText("Back", substring = false))
            compose.onNodeWithText("Back", substring = false).performScrollTo().performClick()
        }
        fun openSchedule() {
            compose.onNodeWithTag("home-list").performScrollToNode(hasText("Edit schedule"))
            compose.onNodeWithText("Edit schedule").performScrollTo().performClick()
            compose.waitUntil(10000) {
                compose.onAllNodesWithTag("schedule-kind-1").fetchSemanticsNodes().isNotEmpty()
            }
        }
        compose.onNodeWithTag("routine-library").performScrollToNode(hasText("Back to Today"))
        compose.onNodeWithText("Back to Today").performClick()
        openSchedule()
        kind(0, "soccer")
        backFromSchedule()
        compose.onNodeWithText("Keep editing").performClick()
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { installTestContent(it, owner) }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("schedule-editor").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("schedule-editor").performScrollToNode(hasTestTag("schedule-kind-0"))
        compose.onNodeWithTag("schedule-kind-0").assertTextContains("Sunday · Soccer")
        assertEquals(original, kotlinx.coroutines.runBlocking { app.scheduleRepository.read() })
        backFromSchedule()
        compose.onNodeWithText("Discard edits").performClick()
        openSchedule()
        for (index in listOf(1, 2, 3, 4, 5, 6, 0)) {
            kind(index, "gym")
            scheduleTag("schedule-routine-$index")
            compose
                .onNodeWithTag("schedule-routine-options")
                .performScrollToNode(hasTestTag("choose-schedule-string:starter:v1:Legs"))
            compose
                .onNodeWithTag("choose-schedule-string:starter:v1:Legs")
                .performScrollTo()
                .performClick()
        }
        compose.onNodeWithTag("save-schedule").performClick()
        compose.waitUntil(10000) {
            vm.state.value.schedule?.all { it.kind == "gym" && it.routine == "Legs" } == true
        }
        assertEquals(active, vm.state.value.draft)
        compose
            .onNodeWithTag("schedule-editor")
            .performScrollToNode(hasText("Back", substring = false))
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Schedule saved.").fetchSemanticsNodes().isNotEmpty()
        }
        screenshot("weekly-schedule")

        kind(1, "soccer")
        kotlinx.coroutines.runBlocking {
            val base = app.scheduleRepository.read()
            app.scheduleRepository.save(
                base.copy(
                    days =
                        base.days.mapIndexed { i, day ->
                            if (i == 1) day.copy(kind = "rest") else day
                        }
                )
            )
        }
        compose.onNodeWithTag("save-schedule").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Reload saved schedule").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Reload saved schedule").performClick()
        compose.onNodeWithText("Reload", substring = false).performClick()
        compose.onNodeWithTag("schedule-editor").performScrollToNode(hasTestTag("schedule-kind-1"))
        compose.waitUntil(10000) {
            runCatching {
                    compose.onNodeWithTag("schedule-kind-1").assertTextContains("Monday · Rest")
                }
                .isSuccess
        }
        compose.onNodeWithTag("schedule-kind-1").assertTextContains("Monday · Rest")
        kind(1, "gym")
        compose.onNodeWithTag("save-schedule").performClick()
        compose.waitUntil(10000) {
            vm.state.value.schedule?.all { it.kind == "gym" && it.routine == "Legs" } == true
        }
        compose.waitUntil(10000) {
            runCatching {
                    compose.onNodeWithTag("save-schedule").assertTextContains("Save schedule")
                    compose.onNodeWithTag("save-schedule").assertIsNotEnabled()
                }
                .isSuccess
        }
        backFromSchedule()
        compose.onNodeWithTag("start-planned-workout").assertDoesNotExist()
        compose.onNodeWithTag("home-list").performScrollToNode(hasText("Resume workout"))
        compose.onNodeWithText("Resume workout").performScrollTo().performClick()
        compose.onNodeWithTag("session-list").performScrollToNode(hasText("Discard workout"))
        compose.onNodeWithText("Discard workout").performScrollTo().performClick()
        compose.onNodeWithText("Discard", substring = false).performClick()
        compose.waitUntil(10000) {
            vm.state.value.draft == null &&
                compose.onAllNodesWithTag("home-list").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("home-list").performScrollToNode(hasTestTag("start-planned-workout"))
        screenshot("today-plan")
        compose
            .onNodeWithTag("start-planned-workout")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose.waitUntil(10000) { vm.state.value.draft?.type == "Legs" }
        assertEquals(JsonPrimitive("starter:v1:Legs"), vm.state.value.draft!!.originRoutineId)
        assertEquals(
            vm.state.value.routines.single { it.name == "Legs" }.exercises,
            vm.state.value.draft!!.exercises.map { it.name },
        )
        assertEquals(4, vm.state.value.workouts.size)
        val output = File(compose.activity.getExternalFilesDir(null), "qa/schedule-export.json")
        output.writeText(
            kotlinx.coroutines.runBlocking { app.backups.export("2026-10-22T12:00:00Z") }
        )
        historyFlow(vm, owner)
    }

    private fun historyFlow(
        vm: com.hector.workouttracker.feature.session.TrackerViewModel,
        owner: ActivityResultRegistryOwner,
    ) {
        val app = compose.activity.application as TrackerApplication
        val active = requireNotNull(vm.state.value.draft)
        val original =
            kotlinx.coroutines.runBlocking { app.historyRepository.read(JsonPrimitive(0)) }!!
        fun tag(name: String) {
            compose.onNodeWithTag("history-workout").performScrollToNode(hasTestTag(name))
            compose.onNodeWithTag(name).performScrollTo()
        }
        fun field(name: String, value: String) {
            tag(name)
            compose.onNodeWithTag(name).performTextReplacement(value)
            closeSoftKeyboard()
        }
        fun back() {
            compose.onNodeWithTag("history-workout").performScrollToNode(hasText("Back to History"))
            compose.onNodeWithText("Back to History").performScrollTo().performClick()
        }
        fun open() {
            compose.waitUntil(10000) {
                compose.onAllNodesWithText("Full workout").fetchSemanticsNodes().isNotEmpty()
            }
            compose
                .onNodeWithTag("history-list")
                .performScrollToNode(hasTestTag("open-history-number:0"))
            compose.onNodeWithTag("open-history-number:0").performScrollTo().performClick()
            compose.waitUntil(10000) {
                compose.onAllNodesWithTag("edit-history-workout").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("edit-history-workout").performScrollTo().performClick()
        }
        compose.onNodeWithTag("session-list").performScrollToNode(hasText("Back to Today"))
        compose.onNodeWithText("Back to Today").performScrollTo().performClick()
        compose.onNodeWithText("History").performClick()
        open()
        field("history-notes", "Unsaved History note")
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { installTestContent(it, owner) }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("history-workout").fetchSemanticsNodes().isNotEmpty()
        }
        tag("history-notes")
        compose.onNodeWithTag("history-notes").assertTextContains("Unsaved History note")
        assertEquals(
            original,
            kotlinx.coroutines.runBlocking { app.historyRepository.read(JsonPrimitive(0)) },
        )
        back()
        compose.onNodeWithText("Keep editing").performClick()
        back()
        compose.onNodeWithText("Discard edits").performClick()
        open()
        field("history-name", "Reviewed workout")
        field("history-notes", "History reviewed")
        field("history-duration", "")
        tag("workout-tag-Good session")
        compose.onNodeWithTag("workout-tag-Good session").performClick()
        field("history-reps-0-0", "6")
        compose.onNodeWithTag("save-history-workout").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("edit-history-workout").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("history-workout").performScrollToNode(hasText("Back to History"))
        screenshot("history-details")
        val edited =
            kotlinx.coroutines.runBlocking { app.historyRepository.read(JsonPrimitive(0)) }!!
        assertEquals("Reviewed workout", edited.values.type)
        assertEquals("History reviewed", edited.values.notes)
        assertEquals("6", edited.values.exercises.first().sets.first().reps)
        assertEquals(active, vm.state.value.draft)
        File(compose.activity.getExternalFilesDir(null), "qa/history-export.json")
            .writeText(
                kotlinx.coroutines.runBlocking { app.backups.export("2026-10-22T12:00:00Z") }
            )
        compose.onNodeWithTag("edit-history-workout").performScrollTo().performClick()
        field("history-name", "Retained stale edit")
        kotlinx.coroutines.runBlocking {
            app.historyRepository.save(
                edited.copy(values = edited.values.copy(notes = "External saved note"))
            )
        }
        compose.onNodeWithTag("save-history-workout").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Reload saved workout").fetchSemanticsNodes().isNotEmpty()
        }
        tag("history-name")
        compose.onNodeWithTag("history-name").assertTextContains("Retained stale edit")
        screenshot("history-conflict")
        compose.onNodeWithText("Reload saved workout").performClick()
        compose.onNodeWithText("Reload", substring = false).performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("edit-history-workout").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("history-workout").performScrollToNode(hasText("External saved note"))
        compose.onNodeWithText("External saved note").assertExists()
        tag("delete-history-workout")
        compose.onNodeWithTag("delete-history-workout").performClick()
        compose.onNodeWithText("Cancel", substring = false).performClick()
        assertNotNull(
            kotlinx.coroutines.runBlocking { app.historyRepository.read(JsonPrimitive(0)) }
        )
        tag("delete-history-workout")
        compose.onNodeWithTag("delete-history-workout").performClick()
        compose.onNodeWithText("Delete", substring = false).performClick()
        compose.waitUntil(10000) { vm.state.value.workouts.size == 3 }
        assertNull(kotlinx.coroutines.runBlocking { app.historyRepository.read(JsonPrimitive(0)) })
        assertNotNull(
            kotlinx.coroutines.runBlocking { app.historyRepository.read(JsonPrimitive("0")) }
        )
        assertEquals(active, vm.state.value.draft)
        back()
        compose.onNodeWithText("Today").performClick()
        compose.onNodeWithTag("home-list").performScrollToNode(hasText("Resume workout"))
        compose.onNodeWithText("Resume workout").assertIsDisplayed()
        statsFlow(vm, owner)
    }

    private fun statsFlow(
        vm: com.hector.workouttracker.feature.session.TrackerViewModel,
        owner: ActivityResultRegistryOwner,
    ) {
        val app = compose.activity.application as TrackerApplication
        val active = vm.state.value.draft
        compose.onNodeWithText("Stats", substring = false).performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("stats-totals").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("stats-totals").assertTextContains("3 workouts", substring = true)
        screenshot("stats-overview")
        compose.onNodeWithTag("stats-list").performScrollToNode(hasTestTag("stats-search"))
        compose.onNodeWithTag("stats-search").performScrollTo().performTextInput("Bench Press")
        closeSoftKeyboard()
        compose
            .onNodeWithTag("stats-list")
            .performScrollToNode(hasTestTag("stats-exercise-Bench Press"))
        compose.onNodeWithTag("stats-exercise-Bench Press").performScrollTo().performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Best load: 100.0 lb").fetchSemanticsNodes().isNotEmpty()
        }
        screenshot("stats-records")
        compose
            .onNodeWithTag("stats-list")
            .performScrollToNode(hasTestTag("stats-metric-bestVolume"))
        compose.onNodeWithTag("stats-metric-bestVolume").performScrollTo().performClick()
        compose.onNodeWithTag("stats-metric-estimated1rm").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { installTestContent(it, owner) }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("stats-list").fetchSemanticsNodes().isNotEmpty()
        }
        compose
            .onNodeWithTag("stats-list")
            .performScrollToNode(hasTestTag("stats-metric-estimated1rm"))
        compose.onNodeWithTag("stats-metric-estimated1rm").assertIsSelected()
        val base =
            kotlinx.coroutines.runBlocking { app.historyRepository.read(JsonPrimitive("0")) }!!
        kotlinx.coroutines.runBlocking {
            app.historyRepository.save(
                base.copy(
                    values =
                        base.values.copy(
                            exercises =
                                base.values.exercises.map {
                                    it.copy(
                                        sets =
                                            it.sets.mapIndexed { i, s ->
                                                if (i == 0) s.copy(weight = "150", reps = "5")
                                                else s
                                            }
                                    )
                                }
                        )
                )
            )
        }
        compose
            .onNodeWithTag("stats-list")
            .performScrollToNode(hasTestTag("stats-record-bestWeight"))
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Best load: 150.0 lb").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Best load: 150.0 lb").assertExists()
        screenshot("stats-live-update")
        assertEquals(active, vm.state.value.draft)
        kotlinx.coroutines.runBlocking {
            app.historyRepository.delete(app.historyRepository.read(JsonPrimitive("0"))!!)
        }
        compose.onNodeWithTag("stats-list").performScrollToNode(hasText("Back to Stats"))
        compose.onNodeWithText("Back to Stats").performScrollTo().performClick()
        compose.waitUntil(10000) {
            runCatching {
                    compose
                        .onNodeWithTag("stats-totals")
                        .assertTextContains("2 workouts", substring = true)
                }
                .isSuccess
        }
        assertEquals(active, vm.state.value.draft)
        compose.onNodeWithText("Today", substring = false).performClick()
        compose.onNodeWithTag("home-list").performScrollToNode(hasText("Resume workout"))
        compose.onNodeWithText("Resume workout").assertIsDisplayed()
        preferencesFlow(vm, owner)
    }

    private fun preferencesFlow(
        vm: com.hector.workouttracker.feature.session.TrackerViewModel,
        owner: ActivityResultRegistryOwner,
    ) {
        val app = compose.activity.application as TrackerApplication
        val active = vm.state.value.draft
        val history = vm.state.value.workouts
        val original = kotlinx.coroutines.runBlocking { app.preferencesRepository.read() }
        fun field(key: String, value: String) {
            compose
                .onNodeWithTag("preferences-editor")
                .performScrollToNode(hasTestTag("preference-$key"))
            compose.onNodeWithTag("preference-$key").performScrollTo().performTextReplacement(value)
            closeSoftKeyboard()
        }
        fun open() {
            compose.onNode(hasText("You", substring = false) and hasClickAction()).performClick()
            compose
                .onNodeWithTag("settings-list")
                .performScrollToNode(hasText("Edit preferences & goal"))
            compose.onNodeWithText("Edit preferences & goal").performScrollTo().performClick()
            compose.waitUntil(10000) {
                compose.onAllNodesWithTag("preference-name").fetchSemanticsNodes().isNotEmpty()
            }
        }
        fun back() {
            compose.onNodeWithTag("preferences-editor").performScrollToNode(hasText("Back to You"))
            compose.onNodeWithText("Back to You").performScrollTo().performClick()
        }
        open()
        field("name", "Unsaved preferences")
        back()
        compose.onNodeWithText("Keep editing").performClick()
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { installTestContent(it, owner) }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("preferences-editor").fetchSemanticsNodes().isNotEmpty()
        }
        compose
            .onNodeWithTag("preferences-editor")
            .performScrollToNode(hasTestTag("preference-name"))
        compose.onNodeWithTag("preference-name").assertTextContains("Unsaved preferences")
        assertEquals(original, kotlinx.coroutines.runBlocking { app.preferencesRepository.read() })
        back()
        compose.onNodeWithText("Discard edits").performClick()
        open()
        field("name", "Native preferences")
        field("weeklyGoal", "0")
        compose.onNodeWithTag("save-preferences").assertIsNotEnabled()
        field("weeklyGoal", "6")
        for (key in listOf("rpe", "haptics", "animations")) {
            compose
                .onNodeWithTag("preferences-editor")
                .performScrollToNode(hasTestTag("preference-$key"))
            compose.onNodeWithTag("preference-$key").performScrollTo().performClick()
        }
        compose
            .onNodeWithTag("preferences-editor")
            .performScrollToNode(hasTestTag("progression-preferences"))
        compose.onNodeWithTag("progression-preferences").performScrollTo().performClick()
        field("defaultWeightJump", "2.5")
        field("compoundMin", "7")
        compose.onNodeWithTag("save-preferences").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Preferences saved.").fetchSemanticsNodes().isNotEmpty() &&
                vm.state.value.profile.displayName == "Native preferences"
        }
        assertEquals(!original.values.trackRpe, vm.state.value.profile.trackRpe)
        val saved = kotlinx.coroutines.runBlocking { app.preferencesRepository.read() }
        assertEquals("6", saved.values.weeklyGoal)
        assertEquals("2.5", saved.values.numbers.getValue("defaultWeightJump"))
        compose.waitUntil(10000) {
            vm.state.value.interactions.animations == !original.values.animations &&
                vm.state.value.interactions.haptics == !original.values.haptics
        }
        assertEquals(active, vm.state.value.draft)
        assertEquals(history, vm.state.value.workouts)
        screenshot("preferences-saved")
        field("name", "Retained preference edit")
        kotlinx.coroutines.runBlocking {
            val plan = app.scheduleRepository.read()
            val oldDay = plan.days[0]
            app.scheduleRepository.save(
                plan.copy(
                    days =
                        plan.days.mapIndexed { index, day ->
                            if (index == 0)
                                day.copy(kind = if (oldDay.kind == "soccer") "rest" else "soccer")
                            else day
                        }
                )
            )
        }
        compose.onNodeWithTag("save-preferences").performClick()
        compose.waitUntil(10000) {
            compose
                .onAllNodesWithText("Reload saved preferences")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithTag("preference-name").assertTextContains("Retained preference edit")
        screenshot("preferences-conflict")
        compose.onNodeWithText("Reload saved preferences").performClick()
        compose.onNodeWithText("Reload", substring = false).performClick()
        compose.waitUntil(10000) {
            runCatching {
                    compose
                        .onNodeWithTag("preference-name")
                        .assertTextContains("Native preferences")
                }
                .isSuccess
        }
        field("weeklyGoal", "15")
        compose.onNodeWithTag("save-preferences").performClick()
        compose.onNodeWithText("Keep editing").performClick()
        assertEquals(
            "6",
            kotlinx.coroutines.runBlocking { app.preferencesRepository.read() }.values.weeklyGoal,
        )
        field("weeklyGoal", "6")
        back()
        compose.onNodeWithText("Stats", substring = false).performClick()
        compose.onNodeWithTag("stats-list").performScrollToNode(hasTestTag("stats-goal"))
        compose.waitUntil(10000) {
            runCatching {
                    compose
                        .onNodeWithTag("stats-goal")
                        .assertTextContains("/ 6 workouts", substring = true)
                }
                .isSuccess
        }
        screenshot("preferences-goal")
        File(compose.activity.getExternalFilesDir(null), "qa/preferences-export.json")
            .writeText(
                kotlinx.coroutines.runBlocking { app.backups.export("2026-10-23T00:00:00Z") }
            )
        assertEquals(active, vm.state.value.draft)
        progressionFlow(vm, owner)
    }

    private fun progressionFlow(
        vm: com.hector.workouttracker.feature.session.TrackerViewModel,
        owner: ActivityResultRegistryOwner,
    ) {
        val app = compose.activity.application as TrackerApplication
        val active = requireNotNull(vm.state.value.draft)
        val sourceRecord =
            vm.state.value.workouts.first { row ->
                Json.parseToJsonElement(row.payload)
                    .jsonObject
                    .getValue("exercises")
                    .jsonArray
                    .any { it.jsonObject.getValue("name").jsonPrimitive.content == "Bench Press" }
            }
        val sourceId = Json.parseToJsonElement(sourceRecord.payload).jsonObject.getValue("id")
        kotlinx.coroutines.runBlocking {
            val source = app.historyRepository.read(sourceId)!!
            app.historyRepository.save(
                source.copy(
                    values =
                        source.values.copy(
                            exercises =
                                source.values.exercises.map { exercise ->
                                    if (exercise.name == "Bench Press")
                                        exercise.copy(
                                            sets =
                                                exercise.sets.take(1).map {
                                                    it.copy(
                                                        weight = "100",
                                                        reps = "10",
                                                        rpe = "8",
                                                        done = false,
                                                        warmup = false,
                                                    )
                                                }
                                        )
                                    else exercise
                                }
                        )
                )
            )
        }
        compose.onNodeWithText("Today", substring = false).performClick()
        compose.onNodeWithTag("home-list").performScrollToNode(hasText("Resume workout"))
        compose.onNodeWithText("Resume workout").performScrollTo().performClick()
        compose
            .onNodeWithTag("session-list")
            .performScrollToNode(hasText("Exercise name", substring = false))
        compose
            .onNodeWithText("Exercise name", substring = false)
            .performScrollTo()
            .performTextInput("Bench Press")
        closeSoftKeyboard()
        compose.onNodeWithText("Add exercise", substring = false).performScrollTo().performClick()
        compose.waitUntil(10000) {
            vm.state.value.draft!!.exercises.size == active.exercises.size + 1 &&
                vm.state.value.guidance["Bench Press"]?.target?.weight == "102.5 lb"
        }
        val key = vm.state.value.draft!!.exercises.last().key
        fun click(tag: String) {
            compose.onNodeWithTag("session-list").performScrollToNode(hasTestTag(tag))
            compose.onNodeWithTag(tag).performScrollTo().performClick()
        }
        fun entry() = vm.state.value.draft!!.exercises.last()
        fun awaitEntry(weight: String, reps: String) {
            compose.waitUntil(10000) {
                !vm.state.value.busy &&
                    !vm.state.value.pending &&
                    entry().sets.singleOrNull()?.let { it.weight == weight && it.reps == reps } ==
                        true
            }
        }
        click("review-last-$key")
        compose.onNodeWithText("1. 100 lb × 10 · RPE 8 · Working").assertExists()
        compose.onNodeWithText("Done", substring = false).performClick()
        compose
            .onNodeWithTag("training-target-$key")
            .assertTextContains("102.5 lb", substring = true)
        screenshot("training-guidance")
        click("use-last-$key")
        awaitEntry("100", "10")
        assertFalse(entry().sets.single().done)
        assertEquals("8", entry().sets.single().rpe)
        val last = vm.state.value.draft
        click("use-target-$key")
        compose.onNodeWithText("Keep entered sets").performClick()
        assertEquals(last, vm.state.value.draft)
        click("use-target-$key")
        compose.onNodeWithText("Replace sets", substring = false).performClick()
        awaitEntry("102.5", "7")
        val target = vm.state.value.draft
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { installTestContent(it, owner) }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("session-list").fetchSemanticsNodes().isNotEmpty() &&
                vm.state.value.guidance["Bench Press"] != null
        }
        assertEquals(target, vm.state.value.draft)
        click("use-last-$key")
        val oldBasis = vm.state.value.guidanceBasis
        kotlinx.coroutines.runBlocking {
            val source = app.historyRepository.read(sourceId)!!
            app.historyRepository.save(
                source.copy(values = source.values.copy(notes = "Changed during copy review"))
            )
        }
        compose.onNodeWithText("Replace sets", substring = false).performClick()
        compose.waitUntil(10000) {
            vm.state.value.error?.startsWith("History, preferences or this exercise changed.") ==
                true && !vm.state.value.busy
        }
        assertEquals(target, vm.state.value.draft)
        screenshot("training-conflict")
        compose.onNodeWithText("Dismiss", substring = false).performClick()
        compose.waitUntil(10000) { vm.state.value.guidanceBasis != oldBasis }
        click("use-target-$key")
        compose.onNodeWithText("Replace sets", substring = false).performClick()
        awaitEntry("102.5", "7")
        assertEquals(active.exercises, vm.state.value.draft!!.exercises.dropLast(1))
        compose
            .onNodeWithTag("session-list")
            .performScrollToNode(hasText("Finish workout", substring = false))
        compose.onNodeWithText("Finish workout", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Save workout", substring = false).performClick()
        compose.waitUntil(10000) {
            vm.state.value.draft == null &&
                !vm.state.value.busy &&
                vm.state.value.workouts.size == 3
        }
        File(compose.activity.getExternalFilesDir(null), "qa/progression-export.json")
            .writeText(
                kotlinx.coroutines.runBlocking { app.backups.export("2026-10-23T13:00:00Z") }
            )
        backupGuidanceFlow(vm, owner)
    }

    private fun backupGuidanceFlow(
        vm: com.hector.workouttracker.feature.session.TrackerViewModel,
        owner: ActivityResultRegistryOwner,
    ) {
        val app = compose.activity.application as TrackerApplication
        val records = vm.state.value.workouts
        compose
            .onNodeWithTag("home-list")
            .performScrollToNode(hasText("Start workout", substring = false))
        compose.onNodeWithText("Start workout", substring = false).performClick()
        compose.waitUntil(10000) {
            vm.state.value.draft != null && !vm.state.value.pending && !vm.state.value.busy
        }
        compose.onNodeWithText("Workout name").performTextReplacement("Guidance keeps this draft")
        closeSoftKeyboard()
        compose.waitUntil(10000) { !vm.state.value.pending }
        val active = vm.state.value.draft
        compose
            .onNodeWithTag("session-list")
            .performScrollToNode(hasTestTag("read-tip-activeWorkoutBasics"))
        compose.onNodeWithTag("read-tip-activeWorkoutBasics").performClick()
        screenshot("guidance-inline")
        compose.onNodeWithTag("dismiss-tip-activeWorkoutBasics").performScrollTo().performClick()
        compose.waitUntil(10000) {
            compose
                .onAllNodesWithTag("help-tip-activeWorkoutBasics")
                .fetchSemanticsNodes()
                .isEmpty()
        }
        compose.onNodeWithTag("session-list").performScrollToNode(hasText("Back to Today"))
        compose.onNodeWithText("Back to Today").performClick()
        compose.onNodeWithText("You", substring = false).performClick()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("open-help"))
        compose.onNodeWithTag("open-help").performClick()
        compose
            .onNodeWithTag("help-list")
            .performScrollToNode(hasTestTag("help-read-activeWorkoutBasics"))
        compose.onNodeWithTag("help-read-activeWorkoutBasics").performClick()
        compose.onNodeWithTag("help-replay-activeWorkoutBasics").performScrollTo().performClick()
        compose.waitUntil(10000) {
            kotlinx.coroutines.runBlocking {
                com.hector.workouttracker.domain.guidanceVisible(
                    app.guidanceRepository.progress.first(),
                    "activeWorkoutBasics",
                )
            }
        }
        assertEquals(active, vm.state.value.draft)
        screenshot("guidance-help")
        val progress = kotlinx.coroutines.runBlocking { app.guidanceRepository.progress.first() }
        val beforeStatus = vm.state.value.backup
        compose.onNodeWithTag("help-list").performScrollToNode(hasTestTag("reset-guidance"))
        compose.onNodeWithTag("reset-guidance").performClick()
        compose.onNodeWithText("Keep guidance").performClick()
        assertEquals(
            progress,
            kotlinx.coroutines.runBlocking { app.guidanceRepository.progress.first() },
        )
        compose.onNodeWithTag("reset-guidance").performClick()
        compose.onNodeWithText("Reset tips", substring = false).performClick()
        compose.waitUntil(10000) {
            kotlinx.coroutines.runBlocking {
                val records = app.guidanceRepository.progress.first()["experiences"]?.jsonObject
                com.hector.workouttracker.domain.guidanceTopics.all { topic ->
                    records?.get(topic.id)?.jsonObject?.get("status")?.jsonPrimitive?.content ==
                        "unseen"
                }
            }
        }
        assertEquals(active, vm.state.value.draft)
        assertEquals(records, vm.state.value.workouts)
        assertEquals(beforeStatus, vm.state.value.backup)
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { installTestContent(it, owner) }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("help-list").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("help-list").performScrollToNode(hasText("Back to You"))
        compose.onNodeWithText("Back to You").performClick()
        fun export() {
            compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Export backup"))
            compose.onNodeWithText("Export backup").performClick()
        }
        nextUri = null
        export()
        compose.waitForIdle()
        assertEquals(beforeStatus, vm.state.value.backup)
        nextUri = Uri.parse("content://com.hector.workouttracker.test.documents/fail.json")
        export()
        compose.waitUntil(10000) { !vm.state.value.busy && vm.state.value.error != null }
        assertEquals(beforeStatus, vm.state.value.backup)
        assertEquals(active, vm.state.value.draft)
        compose.onNodeWithText("Dismiss", substring = false).performClick()
        nextUri = Uri.parse("content://com.hector.workouttracker.test.documents/export.json")
        export()
        compose.waitUntil(10000) {
            !vm.state.value.busy &&
                vm.state.value.backup?.lastExportedAt != beforeStatus?.lastExportedAt
        }
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("backup-status"))
        compose
            .onNode(hasText("Current") and hasAnyAncestor(hasTestTag("backup-status")))
            .assertExists()
        screenshot("backup-reminder")
        val text =
            compose.activity.contentResolver.openInputStream(nextUri!!)!!.bufferedReader().use {
                it.readText()
            }
        val saved = BackupCodec.parse(text)
        assertEquals(3, saved.workouts.size)
        assertEquals(
            JsonPrimitive(vm.state.value.backup!!.lastExportedAt),
            saved.metadata!!.getValue("lastExportedAt"),
        )
        assertFalse(text.contains("native_guidance"))
        assertFalse(text.contains("Guidance keeps this draft"))
        assertEquals(active, vm.state.value.draft)
        File(compose.activity.getExternalFilesDir(null), "qa/guidance-export.json").writeText(text)
    }

    private fun screenshot(name: String) {
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
