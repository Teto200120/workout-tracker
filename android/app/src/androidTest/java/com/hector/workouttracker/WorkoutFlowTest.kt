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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.hector.workouttracker.core.backup.BackupCodec
import com.hector.workouttracker.core.designsystem.TrackerTheme
import com.hector.workouttracker.core.model.openExercise
import com.hector.workouttracker.core.model.trackerJson
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
        val importApp = compose.activity.application as TrackerApplication
        val beforeImportReview =
            kotlinx.coroutines.runBlocking { importApp.backups.export("2026-10-25T00:00:00Z") }
        val beforeImportDraft = kotlinx.coroutines.runBlocking { importApp.repository.readDraft() }
        nextUri = Uri.parse("content://com.hector.workouttracker.test.documents/fixture.json")
        compose.onNodeWithText("Import PWA backup").performScrollTo().performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Review backup import").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Workouts: 2 new, 0 replaced, 0 unchanged").assertExists()
        screenshot("import-preview")
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { installTestContent(it, owner) }
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Review backup import").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Workouts: 2 new, 0 replaced, 0 unchanged").assertExists()
        for (explanation in
            listOf(
                "Matching IDs with changed content will be replaced. Number IDs and text IDs remain distinct. Other saved records are kept.",
                "Supplied settings will replace your preferences.",
                "Imported routines and goals are available in Routines and Stats, and included in exports. RPE preferences never erase historical RPE values. Guidance preferences stay on this device.",
            )) {
            compose.onNodeWithTag("import-review-content").performScrollToNode(hasText(explanation))
            compose.onNodeWithText(explanation).assertIsDisplayed()
        }
        screenshot("import-preview-restored")
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(
            beforeImportReview,
            kotlinx.coroutines.runBlocking { importApp.backups.export("2026-10-25T00:00:00Z") },
        )
        assertEquals(
            beforeImportDraft,
            kotlinx.coroutines.runBlocking { importApp.repository.readDraft() },
        )

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
        compose.onNodeWithTag("setup-rpe").assertContentDescriptionEquals("Track RPE")
        compose
            .onNodeWithTag("setup-rpe")
            .assert(
                SemanticsMatcher.expectValue(
                    androidx.compose.ui.semantics.SemanticsProperties.Role,
                    androidx.compose.ui.semantics.Role.Switch,
                )
            )
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
        compose.onNodeWithTag("home-routine-chooser").performScrollTo().performClick()
        compose
            .onNode(hasText("Quick workout") and hasAnyAncestor(hasTestTag("home-routine-options")))
            .performClick()
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
        compose
            .onNodeWithTag("session-list")
            .performScrollToNode(
                (hasText("Back to Today") or hasContentDescription("Back to Today"))
            )
        compose
            .onNode(hasText("Back to Today") or hasContentDescription("Back to Today"))
            .performClick()
        compose.onNodeWithText("Resume workout").performClick()
        compose.onNodeWithText("Weight (lb)").performScrollTo().assertTextContains("100")
        compose.onNodeWithTag("session-list").performScrollToNode(hasText("Finish workout"))
        compose.onNodeWithText("Finish workout").performClick()
        compose.onNodeWithTag("workout-tag-Good session").performScrollTo().performClick()
        screenshot("completion-summary")
        compose.onNodeWithText("Save workout").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("nav-stats").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("home-list").performScrollToNode(hasTestTag("workout-completion"))
        compose.onNodeWithTag("workout-completion").assertExists()
        screenshot("completion-result")
        compose.onNodeWithText("Dismiss summary").performScrollTo().performClick()
        openHistory()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Bench Press · 1 set").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithText("Bench Press · 1 set").onFirst().assertExists()
        compose
            .onAllNodes(hasText("500.0") and hasText("Volume · lb × reps"))
            .onFirst()
            .assertExists()
        screenshot("history")
        openData()
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
        goHome()
        compose.onNodeWithTag("home-list").performScrollToNode(hasTestTag("home-routine-chooser"))
        compose
            .onNodeWithTag("home-routine-chooser")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        screenshot("routine-chooser")
        compose
            .onNode(
                hasText("Training fixture") and hasAnyAncestor(hasTestTag("home-routine-options"))
            )
            .performClick()
        // Selecting previews a routine; only the persistent primary action starts it.
        assertNull(
            kotlinx.coroutines.runBlocking {
                (compose.activity.application as TrackerApplication).repository.readDraft()
            }
        )
        compose.onNodeWithText("Start workout").performClick()
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
        compose.onNodeWithText("Active workout").assertIsDisplayed()
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
                        .performScrollToNode(
                            hasContentDescription("Guide for Alternate Hammer Curl")
                        )
                }
                .isSuccess
        }
        screenshot("exercise-picker")
        compose
            .onNode(hasContentDescription("Guide for Alternate Hammer Curl"))
            .performScrollTo()
            .performClick()
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
        sessionKeyboardViewport()
        closeSoftKeyboard()
        compose
            .onNodeWithTag("session-list")
            .performScrollToNode(
                (hasText("Back to Today") or hasContentDescription("Back to Today"))
            )
        compose
            .onNode(hasText("Back to Today") or hasContentDescription("Back to Today"))
            .performClick()
        compose.onNodeWithText("Resume workout").performClick()
        compose.onNodeWithText("Workout name").assertTextContains("Training fixture")
        activeOrderFlow(vm, owner)
        activeExerciseFlow(vm, owner)
        compose.onNodeWithTag("session-list").performScrollToNode(hasText("Finish workout"))
        compose.onNodeWithText("Finish workout").performClick()
        compose.onNodeWithText("Save workout").performClick()
        compose.waitUntil(10000) {
            compose
                .onAllNodesWithText("Profile", substring = false)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        openData()
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

    private fun primaryNavigation() {
        if (compose.onAllNodesWithTag("settings-list").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("data-back"))
            compose.onNodeWithTag("data-back").performScrollTo().performClick()
        }
        if (compose.onAllNodesWithTag("preferences-editor").fetchSemanticsNodes().isNotEmpty()) {
            compose
                .onNodeWithTag("preferences-editor")
                .performScrollToNode(hasText("Back to Profile"))
            compose.waitUntil(10000) {
                compose
                    .onAllNodes(hasText("Back to Profile") and isEnabled())
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose.onNodeWithText("Back to Profile").performScrollTo().performClick()
        }
    }

    private fun goHome() {
        primaryNavigation()
        compose.onNodeWithTag("nav-home").performClick()
    }

    private fun openData() {
        if (compose.onAllNodesWithTag("settings-list").fetchSemanticsNodes().isNotEmpty()) return
        if (compose.onAllNodesWithTag("preferences-editor").fetchSemanticsNodes().isNotEmpty()) {
            compose
                .onNodeWithTag("preferences-editor")
                .performScrollToNode(hasTestTag("settings-backup"))
            compose.onNodeWithTag("settings-backup").performScrollTo().performClick()
        } else {
            compose.onNodeWithTag("nav-settings").performClick()
            compose.onNodeWithTag("profile-list").performScrollToNode(hasTestTag("profile-backup"))
            compose.onNodeWithTag("profile-backup").performScrollTo().performClick()
        }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("settings-list").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openAppSettings() {
        primaryNavigation()
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("profile-list").performScrollToNode(hasTestTag("open-settings"))
        compose.onNodeWithTag("open-settings").performScrollTo().performClick()
        compose.waitUntil(10000) {
            compose
                .onAllNodes(hasTestTag("preference-name") and isEnabled())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun openHistory() {
        primaryNavigation()
        compose.onNodeWithTag("nav-stats").performClick()
        compose.onNodeWithTag("stats-list").performScrollToNode(hasTestTag("open-history"))
        compose.onNodeWithTag("open-history").performScrollTo().performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("history-list").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun routineEditorFlow(
        vm: com.hector.workouttracker.feature.session.TrackerViewModel,
        owner: ActivityResultRegistryOwner,
    ) {
        fun editorText(text: String) {
            if (text == "Save routine") {
                compose.onNodeWithTag("save-routine").assertIsDisplayed().performClick()
                return
            }
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
        goHome()
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
        compose.onNodeWithText("Your saved routine has not changed.").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { installTestContent(it, owner) }
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Discard routine edits?").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Your saved routine has not changed.").assertIsDisplayed()
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
        compose
            .onNodeWithText("This replaces your unsaved edits with the latest saved routine.")
            .assertIsDisplayed()
        val latestRoutine =
            kotlinx.coroutines.runBlocking { app.routineRepository.read(JsonPrimitive(0)) }
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithTag("routine-name").assertTextContains("Unsaved stale")
        assertEquals(
            latestRoutine,
            kotlinx.coroutines.runBlocking { app.routineRepository.read(JsonPrimitive(0)) },
        )
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
        compose
            .onNodeWithTag("routine-library")
            .performScrollToNode(hasContentDescription("Delete Native empty"))
        compose
            .onNodeWithContentDescription("Delete Native empty")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose.onNodeWithText("Cancel", substring = false).performClick()
        compose
            .onNodeWithContentDescription("Delete Native empty")
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
        compose
            .onNodeWithTag("routine-library")
            .performScrollToNode(
                (hasText("Back to Today") or hasContentDescription("Back to Today"))
            )
        compose
            .onNode(hasText("Back to Today") or hasContentDescription("Back to Today"))
            .performClick()
        compose.onNodeWithTag("home-list").performScrollToNode(hasTestTag("home-routine-chooser"))
        compose
            .onNodeWithTag("home-routine-chooser")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose
            .onNode(
                hasText("Edited fixture") and hasAnyAncestor(hasTestTag("home-routine-options"))
            )
            .performClick()
        assertNull(vm.state.value.draft)
        compose.onNodeWithText("Start workout").performClick()
        compose.waitUntil(10000) { vm.state.value.draft != null }
        val active = vm.state.value.draft!!
        assertEquals(JsonPrimitive(0), active.originRoutineId)
        assertEquals(expected, active.exercises.map { it.name })
        compose
            .onNode(hasText("Back to Today") or hasContentDescription("Back to Today"))
            .performClick()
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
        compose
            .onNodeWithTag("routine-library")
            .performScrollToNode(
                (hasText("Back to Today") or hasContentDescription("Back to Today"))
            )
        compose
            .onNode(hasText("Back to Today") or hasContentDescription("Back to Today"))
            .performClick()
        openSchedule()
        scheduleTag("schedule-kind-1")
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { installTestContent(it, owner) }
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Monday activity").fetchSemanticsNodes().isNotEmpty()
        }
        for (value in listOf("gym", "soccer", "rest")) {
            compose.onNodeWithTag("choose-kind-$value").assertIsDisplayed()
        }
        compose.onNodeWithText("Cancel", substring = false).performClick()
        assertEquals(original, kotlinx.coroutines.runBlocking { app.scheduleRepository.read() })
        kind(0, "soccer")
        backFromSchedule()
        compose.onNodeWithText("Your saved schedule has not changed.").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { installTestContent(it, owner) }
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Discard schedule edits?").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Your saved schedule has not changed.").assertIsDisplayed()
        compose.onNodeWithText("Keep editing").performClick()
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { installTestContent(it, owner) }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("schedule-editor").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("schedule-editor").performScrollToNode(hasTestTag("schedule-kind-0"))
        compose
            .onNodeWithTag("schedule-kind-0")
            .assertTextContains("Soccer")
            .assertContentDescriptionEquals("Sunday activity")
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
        compose
            .onNodeWithText("This replaces your unsaved plan with the latest saved settings.")
            .assertIsDisplayed()
        val latestSchedule = kotlinx.coroutines.runBlocking { app.scheduleRepository.read() }
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithTag("schedule-editor").performScrollToNode(hasTestTag("schedule-kind-1"))
        compose
            .onNodeWithTag("schedule-kind-1")
            .assertTextContains("Soccer")
            .assertContentDescriptionEquals("Monday activity")
        assertEquals(
            latestSchedule,
            kotlinx.coroutines.runBlocking { app.scheduleRepository.read() },
        )
        compose.onNodeWithText("Reload saved schedule").performClick()
        compose.onNodeWithText("Reload", substring = false).performClick()
        compose.onNodeWithTag("schedule-editor").performScrollToNode(hasTestTag("schedule-kind-1"))
        compose.waitUntil(10000) {
            runCatching {
                    compose
                        .onNodeWithTag("schedule-kind-1")
                        .assertTextContains("Rest")
                        .assertContentDescriptionEquals("Monday activity")
                }
                .isSuccess
        }
        compose
            .onNodeWithTag("schedule-kind-1")
            .assertTextContains("Rest")
            .assertContentDescriptionEquals("Monday activity")
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
        compose.onNodeWithText("Resume workout").assertIsDisplayed()
        compose.onNodeWithText("Resume workout").performClick()
        compose.onNodeWithTag("session-list").performScrollToNode(hasText("Discard workout"))
        compose.onNodeWithText("Discard workout").performScrollTo().performClick()
        compose.onNodeWithText("Discard", substring = false).performClick()
        compose.waitUntil(10000) {
            vm.state.value.draft == null &&
                compose.onAllNodesWithTag("home-list").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("home-routine-chooser").performScrollTo().performClick()
        compose
            .onNode(hasText("Legs") and hasAnyAncestor(hasTestTag("home-routine-options")))
            .performScrollTo()
            .performClick()
        compose.onNodeWithTag("start-planned-workout").assertIsDisplayed()
        screenshot("today-plan")
        compose.onNodeWithTag("start-planned-workout").assertIsDisplayed().performClick()
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
        compose
            .onNodeWithTag("session-list")
            .performScrollToNode(
                (hasText("Back to Today") or hasContentDescription("Back to Today"))
            )
        compose
            .onNode(hasText("Back to Today") or hasContentDescription("Back to Today"))
            .performScrollTo()
            .performClick()
        openHistory()
        historyFiltersFlow(vm, owner)
        val original =
            kotlinx.coroutines.runBlocking { app.historyRepository.read(JsonPrimitive(0)) }!!
        open()
        // Applying the calendar's current selection preserves the exact date without a timezone
        // shift.
        tag("history-date")
        compose.onNodeWithContentDescription("Choose workout date").performClick()
        compose.onNodeWithText("Use date").performClick()
        compose.onNodeWithTag("history-date").assertTextContains(original.values.date)
        // The interchange format accepts year zero; the calendar must not crash or replace it.
        field("history-date", "0000-01-01")
        compose.onNodeWithContentDescription("Choose workout date").performClick()
        compose.onNodeWithText("Use date").assertIsNotEnabled()
        compose.onNodeWithText("Cancel", substring = false).performClick()
        compose.onNodeWithTag("history-date").assertTextContains("0000-01-01")
        field("history-date", original.values.date)
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
        val originalSet = original.values.exercises.first().sets.first()
        val exerciseName = original.values.exercises.first().name
        for ((key, label, checked) in
            listOf(
                Triple("completed", "Completed", originalSet.done),
                Triple("warmup", "Warm-up", originalSet.warmup),
            )) {
            val control = "history-$key-0-0"
            tag(control)
            val node = compose.onNodeWithTag(control)
            node.assertContentDescriptionEquals("$label, exercise 1, $exerciseName, set 1")
            node.assert(
                SemanticsMatcher.expectValue(
                    androidx.compose.ui.semantics.SemanticsProperties.Role,
                    androidx.compose.ui.semantics.Role.Checkbox,
                )
            )
            if (checked) node.assertIsOn() else node.assertIsOff()
            node.performClick()
            if (checked) node.assertIsOff() else node.assertIsOn()
            node.performClick()
            if (checked) node.assertIsOn() else node.assertIsOff()
        }
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
        goHome()
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
        compose.onNodeWithTag("stats-week-summary").assertIsDisplayed()
        compose.onNodeWithTag("stats-lifetime-summary").assertIsDisplayed()
        compose.onNodeWithTag("stats-open-insights").assertIsDisplayed()
        compose.onNodeWithTag("stats-open-exercises").assertIsDisplayed()
        compose.onNodeWithTag("stats-latest-session").assertIsDisplayed()
        compose.onNodeWithTag("open-history").assertIsDisplayed()
        screenshot("stats-overview")
        compose.onNodeWithTag("stats-open-insights").performClick()
        compose
            .onNodeWithTag("stats-list")
            .performScrollToNode(hasTestTag("stats-strength-heading"))
        compose.onNodeWithText("Strength snapshot").assertExists()
        compose.onNodeWithTag("stats-list").performScrollToNode(hasTestTag("stats-recent-heading"))
        compose.onNodeWithTag("stats-recent-heading").performScrollTo().assertIsDisplayed()
        screenshot("stats-recent-sessions")
        val recent =
            com.hector.workouttracker.domain
                .sortedWorkouts(
                    com.hector.workouttracker.domain.interchangeOrder(
                        vm.state.value.workouts.map {
                            trackerJson.parseToJsonElement(it.payload).jsonObject
                        }
                    )
                )
                .take(3)
        assertEquals(3, recent.size)
        // The preceding History acceptance deliberately deleted numeric zero.
        assertFalse(recent.any { it["id"] == JsonPrimitive(0) })
        assertTrue(recent.any { it["id"] == JsonPrimitive("0") })
        for (workout in recent) {
            val tag = "stats-recent-${workout.getValue("id")}"
            compose.onNodeWithTag("stats-list").performScrollToNode(hasTestTag(tag))
            compose.onNodeWithTag(tag).assertExists()
            compose
                .onNode(
                    hasText(workout.getValue("type").jsonPrimitive.content) and
                        hasAnyAncestor(hasTestTag(tag))
                )
                .assertExists()
        }
        compose.onNodeWithTag("stats-list").performScrollToNode(hasTestTag("stats-back"))
        compose.onNodeWithTag("stats-back").performClick()
        compose.onNodeWithTag("stats-open-exercises").performClick()
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
        compose.onNodeWithTag("stats-chart").assertIsDisplayed()
        screenshot("stats-records")
        compose
            .onNodeWithTag("stats-list")
            .performScrollToNode(hasTestTag("stats-metric-bestVolume"))
        compose.onNodeWithTag("stats-metric-bestVolume").performScrollTo().performClick()
        compose.onNodeWithTag("stats-record-bestVolume").assertExists()
        compose.onNodeWithTag("stats-metric-estimated1rm").performScrollTo().performClick()
        compose.onNodeWithTag("stats-record-estimated1rm").assertExists()
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
                            date = java.time.LocalDate.now().toString(),
                            exercises =
                                base.values.exercises.map {
                                    it.copy(
                                        sets =
                                            it.sets.mapIndexed { i, s ->
                                                if (i == 0) s.copy(weight = "150", reps = "5")
                                                else s
                                            }
                                    )
                                },
                        )
                )
            )
        }
        compose
            .onNodeWithTag("stats-list")
            .performScrollToNode(hasTestTag("stats-metric-bestWeight"))
        compose.onNodeWithTag("stats-metric-bestWeight").performScrollTo().performClick()
        compose
            .onNodeWithTag("stats-list")
            .performScrollToNode(hasTestTag("stats-record-bestWeight"))
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Best load: 150.0 lb").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Best load: 150.0 lb").assertExists()
        screenshot("stats-live-update")
        assertEquals(active, vm.state.value.draft)
        compose.onNodeWithTag("stats-list").performScrollToNode(hasTestTag("stats-back"))
        compose.onNodeWithTag("stats-back").performClick()
        compose.onNodeWithTag("stats-search").assertTextContains("Bench Press")
        compose.onNodeWithTag("stats-list").performScrollToNode(hasTestTag("stats-back"))
        compose.onNodeWithTag("stats-back").performClick()
        compose.onNodeWithTag("stats-open-insights").performClick()
        compose
            .onNodeWithTag("stats-list")
            .performScrollToNode(hasTestTag("stats-insight-pr-New PR: Bench Press"))
        compose.onNodeWithText("New PR: Bench Press").assertExists()
        compose
            .onNodeWithTag("stats-insight-pr-New PR: Bench Press")
            .performScrollTo()
            .assertIsDisplayed()
        screenshot("stats-strength-live-update")
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { installTestContent(it, owner) }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("stats-list").fetchSemanticsNodes().isNotEmpty()
        }
        compose
            .onNodeWithTag("stats-list")
            .performScrollToNode(hasTestTag("stats-insight-pr-New PR: Bench Press"))
        compose.onNodeWithText("New PR: Bench Press").assertExists()
        kotlinx.coroutines.runBlocking {
            app.historyRepository.delete(app.historyRepository.read(JsonPrimitive("0"))!!)
        }
        compose.onNodeWithTag("stats-list").performScrollToNode(hasTestTag("stats-back"))
        compose.onNodeWithTag("stats-back").performClick()
        compose.onNodeWithTag("stats-list").performScrollToNode(hasTestTag("stats-totals"))
        compose.waitUntil(10000) {
            runCatching {
                    compose
                        .onNodeWithTag("stats-totals")
                        .assertTextContains("2 workouts", substring = true)
                }
                .isSuccess
        }
        assertEquals(active, vm.state.value.draft)
        goHome()
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
            openAppSettings()
        }
        fun back() {
            compose
                .onNodeWithTag("preferences-editor")
                .performScrollToNode(hasText("Back to Profile"))
            compose.onNodeWithText("Back to Profile").performScrollTo().performClick()
        }
        open()
        field("name", "Unsaved preferences")
        back()
        compose.onNodeWithText("Your saved preferences have not changed.").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { installTestContent(it, owner) }
        compose.waitUntil(10000) {
            compose
                .onAllNodesWithText("Discard preference edits?")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithText("Your saved preferences have not changed.").assertIsDisplayed()
        screenshot("preferences-discard-restored")
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
            compose.onAllNodesWithText("Settings saved.").fetchSemanticsNodes().isNotEmpty() &&
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
        val concurrentPreferences =
            kotlinx.coroutines.runBlocking { app.preferencesRepository.read() }
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
        compose
            .onNodeWithText(
                "This replaces your unsaved entries with the latest saved preferences and goal."
            )
            .assertIsDisplayed()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithTag("preference-name").assertTextContains("Retained preference edit")
        assertEquals(
            concurrentPreferences,
            kotlinx.coroutines.runBlocking { app.preferencesRepository.read() },
        )
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
        compose
            .onNodeWithText(
                "This goal or rep range is unusually high. Review the numbers before saving."
            )
            .assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { installTestContent(it, owner) }
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Review these values").fetchSemanticsNodes().isNotEmpty()
        }
        compose
            .onNodeWithText(
                "This goal or rep range is unusually high. Review the numbers before saving."
            )
            .assertIsDisplayed()
        screenshot("preferences-warning-restored")
        compose.onNodeWithText("Keep editing").performClick()
        compose
            .onNodeWithTag("preferences-editor")
            .performScrollToNode(hasTestTag("preference-weeklyGoal"))
        compose.onNodeWithTag("preference-weeklyGoal").assertTextContains("15")
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
                    compose.onNodeWithTag("stats-goal").assertTextContains("/ 6", substring = true)
                }
                .isSuccess
        }
        compose.onNodeWithText("workouts · last 7 days").assertExists()
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
        goHome()
        compose.onNodeWithText("Resume workout").assertIsDisplayed()
        compose.onNodeWithText("Resume workout").performClick()
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
        assertEquals(last, kotlinx.coroutines.runBlocking { app.repository.readDraft() }!!.first)
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
        compose.onNodeWithText("Start workout", substring = false).assertIsDisplayed()
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
        compose
            .onNodeWithTag("session-list")
            .performScrollToNode(
                (hasText("Back to Today") or hasContentDescription("Back to Today"))
            )
        compose
            .onNode(hasText("Back to Today") or hasContentDescription("Back to Today"))
            .performClick()
        openAppSettings()
        compose.onNodeWithTag("preferences-editor").performScrollToNode(hasTestTag("open-help"))
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
        val beforeHelpReview =
            kotlinx.coroutines.runBlocking { app.backups.export("2026-10-25T12:00:00Z") }
        val beforeHelpDraft = kotlinx.coroutines.runBlocking { app.repository.readDraft() }
        compose.onNodeWithTag("help-list").performScrollToNode(hasTestTag("reset-guidance"))
        compose.onNodeWithTag("reset-guidance").performClick()
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { installTestContent(it, owner) }
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Reset guidance?").fetchSemanticsNodes().isNotEmpty()
        }
        compose
            .onNodeWithText("Show known tips again without changing workout or profile data.")
            .assertIsDisplayed()
        screenshot("guidance-reset-review-restored")
        compose.onNodeWithText("Keep guidance").performClick()
        assertEquals(
            progress,
            kotlinx.coroutines.runBlocking { app.guidanceRepository.progress.first() },
        )
        assertEquals(
            beforeHelpReview,
            kotlinx.coroutines.runBlocking { app.backups.export("2026-10-25T12:00:00Z") },
        )
        assertEquals(beforeHelpDraft, kotlinx.coroutines.runBlocking { app.repository.readDraft() })
        assertEquals(active, vm.state.value.draft)
        assertEquals(records, vm.state.value.workouts)
        assertEquals(beforeStatus, vm.state.value.backup)
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
        compose.onNodeWithTag("help-list").performScrollToNode(hasText("Back to settings"))
        compose.onNodeWithText("Back to settings").performClick()
        openData()
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
        settingsResetFlow(vm, owner)
    }

    private fun sessionKeyboardViewport() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val key = "show_ime_with_hard_keyboard"
        val original =
            android.provider.Settings.Secure.getString(compose.activity.contentResolver, key)
        fun setting(value: String?) {
            val command =
                if (value == null) "settings delete secure $key"
                else "settings put secure $key $value"
            android.os.ParcelFileDescriptor.AutoCloseInputStream(
                    instrumentation.uiAutomation.executeShellCommand(command)
                )
                .use { it.readBytes() }
        }
        try {
            // Exercise a real IME even on emulators configured with a hardware keyboard.
            setting("1")
            val field = compose.onAllNodesWithText("Reps", substring = false).onFirst()
            field.performScrollTo().performClick()
            val decor = compose.activity.window.decorView
            compose.waitUntil(10000) {
                ViewCompat.getRootWindowInsets(decor)?.isVisible(WindowInsetsCompat.Type.ime()) ==
                    true
            }
            compose.waitUntil(10000) {
                val ime =
                    ViewCompat.getRootWindowInsets(decor)!!.getInsets(WindowInsetsCompat.Type.ime())
                val viewport =
                    compose.onNodeWithTag("session-list").fetchSemanticsNode().boundsInWindow
                kotlin.math.abs(viewport.bottom - (decor.height - ime.bottom)) <= 2f
            }
            val ime =
                ViewCompat.getRootWindowInsets(decor)!!.getInsets(WindowInsetsCompat.Type.ime())
            val bounds = field.fetchSemanticsNode().boundsInWindow
            assertTrue(
                "Focused set entry remains above the keyboard",
                bounds.bottom <= decor.height - ime.bottom + 2f,
            )
            field.assertTextContains("8")
            screenshot("session-keyboard-viewport")
        } finally {
            closeSoftKeyboard()
            setting(original)
        }
    }

    private fun settingsResetFlow(
        vm: com.hector.workouttracker.feature.session.TrackerViewModel,
        owner: ActivityResultRegistryOwner,
    ) {
        val app = compose.activity.application as TrackerApplication
        val active = vm.state.value.draft
        val storedActive = kotlinx.coroutines.runBlocking { app.repository.readDraft() }
        val epoch = kotlinx.coroutines.runBlocking { app.dataMaintenance.currentEpoch() }
        val progress = kotlinx.coroutines.runBlocking { app.guidanceRepository.progress.first() }
        val backup = vm.state.value.backup
        fun exported() =
            kotlinx.coroutines.runBlocking { app.backups.export("2026-10-24T12:00:00Z") }
        fun open(tag: String) {
            compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag(tag))
            compose.onNodeWithTag(tag).performScrollTo().performClick()
            compose.waitUntil(10000) {
                vm.state.value.dataActionReview != null && !vm.state.value.busy
            }
        }
        val before = exported()
        open("review-clear-data")
        val clearReview = vm.state.value.dataActionReview
        assertTrue(vm.state.value.dataActionReview!!.hasDraft)
        screenshot("clear-data-review")
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { installTestContent(it, owner) }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("data-action-review").fetchSemanticsNodes().isNotEmpty()
        }
        assertSame(clearReview, vm.state.value.dataActionReview)
        for (warning in
            listOf(
                "Your active workout and all its entered sets will also be removed.",
                "Remove your profile, custom preferences, weekly goal, backup reminder and guidance progress. Restore the six starter routines, then return to setup.",
                "This cannot be undone in the app. Export a backup first if you want to keep saved records. Backups do not include the active workout. Files you already exported and your PWA data stay unchanged.",
            )) {
            compose.onNodeWithTag("data-action-content").performScrollToNode(hasText(warning))
            compose.onNodeWithText(warning).assertIsDisplayed()
        }
        screenshot("clear-data-review-restored")
        compose.onNodeWithTag("cancel-data-action").performClick()
        assertEquals(before, exported())
        assertEquals(active, vm.state.value.draft)
        assertEquals(storedActive, kotlinx.coroutines.runBlocking { app.repository.readDraft() })
        assertEquals(epoch, kotlinx.coroutines.runBlocking { app.dataMaintenance.currentEpoch() })
        open("review-reset-settings")
        val resetReview = vm.state.value.dataActionReview
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { installTestContent(it, owner) }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("data-action-review").fetchSemanticsNodes().isNotEmpty()
        }
        assertSame(resetReview, vm.state.value.dataActionReview)
        for (warning in
            listOf(
                "Restore the default weekly schedule, load jump, rep ranges, RPE tracking, haptics and animations.",
                "Keep your display name, weekly goal, workouts, routines, legacy weight records, backup reminder, guidance and active workout, including entered RPE values.",
            )) {
            compose.onNodeWithTag("data-action-content").performScrollToNode(hasText(warning))
            compose.onNodeWithText(warning).assertIsDisplayed()
        }
        screenshot("settings-reset-review-restored")
        compose.onNodeWithTag("cancel-data-action").performClick()
        assertEquals(before, exported())
        assertEquals(active, vm.state.value.draft)
        assertEquals(storedActive, kotlinx.coroutines.runBlocking { app.repository.readDraft() })
        assertEquals(epoch, kotlinx.coroutines.runBlocking { app.dataMaintenance.currentEpoch() })
        assertEquals(
            progress,
            kotlinx.coroutines.runBlocking { app.guidanceRepository.progress.first() },
        )
        assertEquals(backup, vm.state.value.backup)

        open("review-reset-settings")
        val old = kotlinx.coroutines.runBlocking { app.preferencesRepository.read() }
        kotlinx.coroutines.runBlocking {
            app.preferencesRepository.save(
                old.copy(
                    values =
                        old.values.copy(
                            numbers = old.values.numbers + ("defaultWeightJump" to "7.5")
                        )
                )
            )
        }
        val external = exported()
        compose.onNodeWithTag("confirm-data-action").performClick()
        compose.waitUntil(10000) {
            !vm.state.value.busy &&
                vm.state.value.error?.contains("changed after this review") == true
        }
        assertEquals(external, exported())
        assertEquals(active, vm.state.value.draft)
        compose.onNodeWithText("Dismiss", substring = false).performClick()

        val preferences = kotlinx.coroutines.runBlocking { app.preferencesRepository.read() }
        open("review-reset-settings")
        screenshot("settings-reset-review")
        compose.onNodeWithTag("confirm-data-action").performClick()
        compose.runOnIdle { vm.confirmDataAction() } // The reviewed command was already consumed.
        compose.waitUntil(10000) {
            !vm.state.value.busy && vm.state.value.maintenanceMessage != null
        }
        val saved = kotlinx.coroutines.runBlocking { app.preferencesRepository.read() }
        assertEquals(preferences.values.displayName, saved.values.displayName)
        assertEquals(preferences.values.weeklyGoal, saved.values.weeklyGoal)
        assertTrue(saved.values.trackRpe)
        assertTrue(saved.values.haptics)
        assertTrue(saved.values.animations)
        val after = exported()
        val oldData = BackupCodec.parse(external)
        val newData = BackupCodec.parse(after)
        assertEquals(
            JsonObject(
                BackupCodec.defaultSettings +
                    ("displayName" to JsonPrimitive(saved.values.displayName))
            ),
            newData.settings,
        )
        assertEquals(oldData.workouts, newData.workouts)
        assertEquals(oldData.routines, newData.routines)
        assertEquals(oldData.weights, newData.weights)
        assertEquals(oldData.goals, newData.goals)
        assertEquals(oldData.metadata, newData.metadata)
        assertEquals(active, vm.state.value.draft)
        assertEquals(active, kotlinx.coroutines.runBlocking { app.repository.readDraft() }!!.first)
        assertEquals(
            progress,
            kotlinx.coroutines.runBlocking { app.guidanceRepository.progress.first() },
        )
        assertEquals(backup, vm.state.value.backup)
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { installTestContent(it, owner) }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("settings-list").fetchSemanticsNodes().isNotEmpty()
        }
        compose
            .onNodeWithTag("settings-list")
            .performScrollToNode(hasTestTag("maintenance-message"))
        compose.onNodeWithTag("maintenance-message").performScrollTo().assertIsDisplayed()
        screenshot("settings-reset-complete")
        File(compose.activity.getExternalFilesDir(null), "qa/settings-reset-export.json")
            .writeText(after)
        feedbackFlow(vm, owner)
    }

    private fun feedbackFlow(
        vm: com.hector.workouttracker.feature.session.TrackerViewModel,
        owner: ActivityResultRegistryOwner,
    ) {
        val app = compose.activity.application as TrackerApplication
        val active = vm.state.value.draft
        val before = kotlinx.coroutines.runBlocking { app.backups.export("2026-10-24T12:00:00Z") }
        openAppSettings()
        compose.onNodeWithTag("preferences-editor").performScrollToNode(hasTestTag("open-feedback"))
        compose.onNodeWithTag("open-feedback").performClick()
        compose.onNodeWithText("Beta feedback").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { installTestContent(it, owner) }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("feedback-list").fetchSemanticsNodes().isNotEmpty()
        }
        compose
            .onNodeWithTag("feedback-list")
            .performScrollToNode(hasTestTag("open-feedback-browser"))
        screenshot("feedback-browser-handoff")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var launched: Intent? = null
        val monitor =
            object : android.app.Instrumentation.ActivityMonitor() {
                override fun onStartActivity(
                    intent: Intent
                ): android.app.Instrumentation.ActivityResult? {
                    if (intent.action != Intent.ACTION_VIEW) return null
                    launched = intent
                    // Verify the real production intent without sending a report or leaving the
                    // test.
                    return android.app.Instrumentation.ActivityResult(
                        Activity.RESULT_CANCELED,
                        null,
                    )
                }
            }
        instrumentation.addMonitor(monitor)
        try {
            compose.onNodeWithTag("open-feedback-browser").performClick()
            compose.runOnIdle {
                assertEquals(
                    "https://teto200120.github.io/workout-tracker/?feedback=android",
                    requireNotNull(launched).dataString,
                )
                assertNull(launched!!.extras)
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
        compose.onNodeWithTag("feedback-list").performScrollToIndex(0)
        compose.onNodeWithText("Back to settings").performClick()
        compose.onNodeWithTag("preferences-editor").assertExists()
        assertEquals(active, vm.state.value.draft)
        assertEquals(active, kotlinx.coroutines.runBlocking { app.repository.readDraft() }!!.first)
        assertEquals(
            before,
            kotlinx.coroutines.runBlocking { app.backups.export("2026-10-24T12:00:00Z") },
        )
    }

    private fun activeExerciseFlow(
        vm: com.hector.workouttracker.feature.session.TrackerViewModel,
        owner: ActivityResultRegistryOwner,
    ) {
        val app = compose.activity.application as TrackerApplication
        val original = requireNotNull(vm.state.value.draft)
        val workouts = vm.state.value.workouts
        val first = original.exercises.first()
        val second = original.exercises[1]
        val set = first.sets.first()
        assertFalse(set.warmup)
        fun scroll(tag: String) {
            compose.onNodeWithTag("session-list").performScrollToNode(hasTestTag(tag))
        }
        fun click(tag: String) {
            scroll(tag)
            compose.onNodeWithTag(tag).performScrollTo().performClick()
            compose.waitUntil(10000) { !vm.state.value.pending }
        }
        // A stepper on another expanded exercise must move the primary action to that set.
        val secondSet = second.sets.first()
        scroll("session-set-${secondSet.key}")
        compose
            .onNode(
                hasContentDescription("Increase Reps") and
                    hasAnyAncestor(hasTestTag("session-set-${secondSet.key}"))
            )
            .performScrollTo()
            .performClick()
        compose.waitUntil(10000) { !vm.state.value.pending }
        compose.onNodeWithTag("complete-current-set").assertTextContains(second.name)
        compose
            .onNode(hasText("Reps") and hasAnyAncestor(hasTestTag("session-set-${secondSet.key}")))
            .performTextReplacement(secondSet.reps)
        closeSoftKeyboard()
        compose.waitUntil(10000) { !vm.state.value.pending }
        assertEquals(original, vm.state.value.draft)
        scroll("session-set-${set.key}")
        compose
            .onNode(hasText("Reps") and hasAnyAncestor(hasTestTag("session-set-${set.key}")))
            .performTextReplacement("9")
        closeSoftKeyboard()
        compose
            .onNode(hasText("Completed") and hasAnyAncestor(hasTestTag("session-set-${set.key}")))
            .performScrollTo()
            .performClick()
        compose.waitUntil(10000) { !vm.state.value.pending }
        val entered = requireNotNull(vm.state.value.draft)
        assertEquals("9", entered.exercises.first().sets.first().reps)
        assertTrue(entered.exercises.first().sets.first().done)
        click("next-session-${first.key}")
        val advanced = entered.openExercise(second.key)
        assertEquals(advanced, vm.state.value.draft)
        assertEquals(
            advanced,
            kotlinx.coroutines.runBlocking { app.repository.readDraft() }!!.first,
        )
        compose.onAllNodes(isFocused()).assertCountEquals(0)
        scroll("session-summary-${first.key}")
        compose.onNodeWithText("Set 1: 9 reps").assertExists()
        screenshot("session-collapsed-summary")

        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { installTestContent(it, owner) }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("session-list").fetchSemanticsNodes().isNotEmpty()
        }
        scroll("session-expanded-${second.key}")
        compose.onNodeWithTag("session-expanded-${second.key}").assertExists()
        assertEquals(advanced, vm.state.value.draft)
        click("toggle-session-${first.key}")
        click("review-all-sets-${first.key}")
        scroll("session-set-${set.key}")
        compose
            .onNode(hasText("Reps") and hasAnyAncestor(hasTestTag("session-set-${set.key}")))
            .assertTextContains("9")
        compose
            .onNode(hasText("Completed") and hasAnyAncestor(hasTestTag("session-set-${set.key}")))
            .assertIsOn()
        original.exercises.forEach { exercise -> click("next-session-${exercise.key}") }
        compose.onNodeWithText("Save to History?").assertExists()
        compose.onNodeWithText("Keep editing").performClick()
        assertEquals(workouts, vm.state.value.workouts)
        val reviewed = requireNotNull(vm.state.value.draft)
        assertTrue(reviewed.exercises.all { it.collapsed })
        assertEquals(
            com.hector.workouttracker.core.model.workoutPayload(
                entered,
                110000,
                0,
                java.time.ZoneId.of("UTC"),
            ),
            com.hector.workouttracker.core.model.workoutPayload(
                reviewed,
                110000,
                0,
                java.time.ZoneId.of("UTC"),
            ),
        )
        screenshot("session-review-cancelled")
        compose.onNodeWithTag("session-list").performScrollToNode(hasText("Discard workout"))
        compose.onNodeWithText("Discard workout").performScrollTo().performClick()
        compose
            .onNodeWithText("This removes the active draft. Saved History stays intact.")
            .assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { installTestContent(it, owner) }
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Discard this workout?").fetchSemanticsNodes().isNotEmpty()
        }
        compose
            .onNodeWithText("This removes the active draft. Saved History stays intact.")
            .assertIsDisplayed()
        screenshot("session-discard-review-restored")
        compose.onNodeWithText("Keep workout").performClick()
        assertEquals(reviewed, vm.state.value.draft)
        assertEquals(
            reviewed,
            kotlinx.coroutines.runBlocking { app.repository.readDraft() }!!.first,
        )
        assertEquals(workouts, vm.state.value.workouts)
        // Restore the synthetic fixture for the existing full training/export flow.
        compose.runOnIdle { vm.edit { original } }
        compose.waitUntil(10000) { !vm.state.value.pending }
        assertEquals(
            original,
            kotlinx.coroutines.runBlocking { app.repository.readDraft() }!!.first,
        )
    }

    private fun activeOrderFlow(
        vm: com.hector.workouttracker.feature.session.TrackerViewModel,
        owner: ActivityResultRegistryOwner,
    ) {
        val app = compose.activity.application as TrackerApplication
        val original = requireNotNull(vm.state.value.draft)
        val target = original.exercises[2]
        fun move(direction: String) {
            val tag = "move-session-$direction-${target.key}"
            compose.onNodeWithTag("session-list").performScrollToNode(hasTestTag(tag))
            compose.onNodeWithTag(tag).performScrollTo().performClick()
            compose.waitUntil(10000) { !vm.state.value.pending }
        }
        move("up")
        move("up")
        val expected =
            original.copy(
                exercises = listOf(target) + original.exercises.filterNot { it.key == target.key }
            )
        assertEquals(expected, vm.state.value.draft)
        assertEquals(
            expected,
            kotlinx.coroutines.runBlocking { app.repository.readDraft() }!!.first,
        )
        compose.onNodeWithTag("move-session-up-${target.key}").assertIsNotEnabled()
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { installTestContent(it, owner) }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("session-list").fetchSemanticsNodes().isNotEmpty()
        }
        compose
            .onNodeWithTag("session-list")
            .performScrollToNode(hasTestTag("session-position-${target.key}"))
        compose
            .onNodeWithTag("session-position-${target.key}")
            .assertTextEquals("Exercise 1 of ${original.exercises.size}")
        assertEquals(expected, vm.state.value.draft)
        screenshot("session-reordered")
        move("down")
        move("down")
        assertEquals(original, vm.state.value.draft)
        assertEquals(
            original,
            kotlinx.coroutines.runBlocking { app.repository.readDraft() }!!.first,
        )
    }

    private fun historyFiltersFlow(
        vm: com.hector.workouttracker.feature.session.TrackerViewModel,
        owner: ActivityResultRegistryOwner,
    ) {
        val app = compose.activity.application as TrackerApplication
        val active = vm.state.value.draft
        val before = kotlinx.coroutines.runBlocking { app.backups.export("2026-10-23T12:00:00Z") }
        fun scroll(tag: String) {
            compose.onNodeWithTag("history-list").performScrollToNode(hasTestTag(tag))
        }
        fun search(value: String) {
            scroll("history-search")
            compose.onNodeWithTag("history-search").performTextReplacement(value)
            closeSoftKeyboard()
        }
        fun count(value: Int) {
            val text = "$value ${if (value == 1) "workout" else "workouts"} found"
            compose.waitUntil(10000) {
                compose.onAllNodesWithTag("history-list").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("history-list").performScrollToIndex(0)
            compose.waitUntil(10000) {
                compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
            }
            scroll("history-result-count")
            compose.onNodeWithTag("history-result-count").assertTextEquals(text)
        }
        scroll("history-routine-filter")
        compose.onNodeWithTag("history-routine-filter").performClick()
        compose
            .onNodeWithTag("history-routine-options")
            .performScrollToNode(hasTestTag("history-filter-Compatibility fixture"))
        compose.onNodeWithTag("history-filter-Compatibility fixture").performClick()
        search("  bEnCh  ")
        count(2)
        scroll("open-history-number:0")
        compose.onNodeWithTag("open-history-number:0").assertExists()
        scroll("open-history-string:0")
        compose.onNodeWithTag("open-history-string:0").assertExists()
        scroll("history-search")
        screenshot("history-filtered")

        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { installTestContent(it, owner) }
        count(2)
        scroll("history-search")
        compose.onNodeWithTag("history-search").assertTextContains("  bEnCh  ")
        compose
            .onNodeWithTag("history-routine-filter")
            .assertTextEquals("Routine: Compatibility fixture")
        goHome()
        openHistory()
        count(2)
        scroll("open-history-number:0")
        compose.onNodeWithTag("open-history-number:0").performClick()
        compose.onNodeWithTag("history-workout").performScrollToNode(hasText("Back to History"))
        compose.onNodeWithText("Back to History").performClick()
        count(2)

        val original =
            kotlinx.coroutines.runBlocking { app.historyRepository.read(JsonPrimitive(0)) }!!
        kotlinx.coroutines.runBlocking {
            app.historyRepository.save(
                original.copy(values = original.values.copy(type = "Filter changed"))
            )
        }
        count(1)
        scroll("open-history-string:0")
        compose.onNodeWithTag("open-history-string:0").assertExists()
        compose.onNodeWithTag("open-history-number:0").assertDoesNotExist()
        kotlinx.coroutines.runBlocking {
            val current = app.historyRepository.read(JsonPrimitive(0))!!
            app.historyRepository.save(current.copy(values = original.values))
        }
        count(2)
        search("deadlift")
        count(0)
        compose.onNodeWithTag("history-list").performScrollToNode(hasText("No workouts found"))
        compose.onNodeWithText("Try a different routine filter or exercise search.").assertExists()
        screenshot("history-no-matches")
        scroll("history-clear-filters")
        compose.onNodeWithTag("history-clear-filters").performClick()
        count(vm.state.value.workouts.size)
        assertEquals(active, vm.state.value.draft)
        assertEquals(
            before,
            kotlinx.coroutines.runBlocking { app.backups.export("2026-10-23T12:00:00Z") },
        )
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
