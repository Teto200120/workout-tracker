package com.hector.workouttracker

import android.content.Context
import androidx.compose.animation.core.SnapSpec
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hector.workouttracker.core.backup.BackupCodec
import com.hector.workouttracker.core.database.*
import com.hector.workouttracker.core.designsystem.*
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.data.*
import java.io.File
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PreferencesRepositoryTest {
    private lateinit var db: TrackerDatabase
    private lateinit var preferences: PreferencesRepository
    private val context
        get() = ApplicationProvider.getApplicationContext<Context>()

    private fun fixture() =
        BackupCodec.parse(
            requireNotNull(javaClass.classLoader!!.getResource("training-backup.json")).readText()
        )

    private suspend fun imported() {
        val backups = BackupRepository(db)
        backups.confirm(backups.preview(fixture()))
    }

    private fun edited(base: PreferencesDraft) =
        base.copy(
            values =
                base.values.copy(
                    displayName = "Preferences user",
                    trackRpe = false,
                    haptics = false,
                    animations = false,
                    weeklyGoal = "6",
                    numbers = base.values.numbers + ("defaultWeightJump" to "2,50"),
                )
        )

    @Before
    fun open() {
        context.deleteDatabase("preferences-test.db")
        db =
            Room.databaseBuilder(context, TrackerDatabase::class.java, "preferences-test.db")
                .build()
        preferences = PreferencesRepository(db)
    }

    @After
    fun close() {
        db.close()
        context.deleteDatabase("preferences-test.db")
    }

    private suspend fun rejects(action: suspend () -> Unit) {
        try {
            action()
            fail("Expected rejection")
        } catch (_: IllegalStateException) {} catch (_: IllegalArgumentException) {}
    }

    @Test
    fun preservesExtensionsRecordsAndActiveDraftAndExportsAfterReopen() = runBlocking {
        val data = fixture()
        val settings =
            JsonObject(
                data.settings!! +
                    mapOf(
                        "extension" to
                            buildJsonObject {
                                put("keep", JsonArray(listOf(JsonPrimitive(0), JsonNull)))
                            },
                        "compoundMin" to JsonPrimitive(java.math.BigDecimal("6.00")),
                    )
            )
        val goals = buildJsonObject {
            put("weeklyGoal", 4)
            put("targetWeight", 165)
            put("extension", "keep goal")
        }
        val backups = BackupRepository(db)
        backups.confirm(backups.preview(data.copy(settings = settings, goals = goals)))
        val tracker = TrackerRepository(db)
        val active =
            newSession(
                    Instant.parse("2026-10-23T00:00:00Z").toEpochMilli(),
                    10,
                    1,
                    ZoneId.of("UTC"),
                )
                .copy(
                    exercises =
                        listOf(
                            Exercise(
                                name = "Bench Press",
                                sets = listOf(WorkingSet(weight = "100", reps = "5", rpe = "8")),
                            )
                        )
                )
        tracker.start(active)
        val records = db.trackerDao().records()
        val draft = db.trackerDao().draft()
        val base = preferences.read()
        val saved = preferences.save(edited(base))
        assertFalse(saved.changed)
        assertEquals(Profile("Preferences user", false), tracker.readProfile().first)
        assertEquals(InteractionPreferences(false, false), preferences.interactions.first())
        val stored =
            trackerJson
                .parseToJsonElement(db.trackerDao().document("settings")!!.payload)
                .jsonObject
        for ((key, value) in settings) if (
            key !in setOf("displayName", "rpeAware", "haptics", "animations", "defaultWeightJump")
        )
            assertEquals(key, value, stored[key])
        assertTrue(db.trackerDao().document("settings")!!.payload.contains("6.00"))
        val storedGoals =
            trackerJson.parseToJsonElement(db.trackerDao().document("goals")!!.payload).jsonObject
        assertEquals(JsonObject(goals + ("weeklyGoal" to JsonPrimitive(6))), storedGoals)
        assertEquals(records, db.trackerDao().records())
        assertEquals(draft, db.trackerDao().draft())
        assertEquals("8", tracker.readDraft()!!.first.exercises.single().sets.single().rpe)
        val out = File("build/outputs/compatibility/preferences-export.json")
        requireNotNull(out.parentFile).mkdirs()
        out.writeText(backups.export("2026-10-23T00:00:00Z"))
        db.close()
        db =
            Room.databaseBuilder(context, TrackerDatabase::class.java, "preferences-test.db")
                .build()
        assertEquals(saved, PreferencesRepository(db).read())
    }

    @Test
    fun noopAndEquivalentNumbersDoNotRewriteRawDocumentsOrInventDefaults() = runBlocking {
        val tracker = TrackerRepository(db)
        tracker.saveProfile(Profile("Legacy", false), 0)
        // Simulate the original foundation: no canonical settings or goal document.
        db.openHelper.writableDatabase.execSQL("DELETE FROM documents WHERE name = 'settings'")
        val base = preferences.read()
        val before = db.trackerDao().documents()
        assertEquals(base, preferences.save(base))
        preferences.save(
            base.copy(
                values =
                    base.values.copy(
                        numbers = base.values.numbers + ("defaultWeightJump" to "05.00"),
                        weeklyGoal = "4.0",
                    )
            )
        )
        assertEquals(before, db.trackerDao().documents())
        val saved = preferences.save(base.copy(values = base.values.copy(weeklyGoal = "5")))
        assertEquals("Legacy", saved.values.displayName)
        assertFalse(saved.values.trackRpe)
        assertNull(db.trackerDao().document("settings"))
        assertEquals(
            JsonPrimitive(false),
            BackupCodec.parse(BackupRepository(db).export("2026-10-23T00:00:00Z")).settings!![
                "rpeAware"],
        )
    }

    @Test
    fun fencesConcurrentSavesScheduleProfileImportsAndGoalAba() = runBlocking {
        imported()
        val base = preferences.read()
        val results =
            listOf("One", "Two")
                .map { name ->
                    async(Dispatchers.IO) {
                        runCatching {
                            preferences.save(
                                base.copy(values = base.values.copy(displayName = name))
                            )
                        }
                    }
                }
                .awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        val schedule = ScheduleRepository(db)
        val stale = preferences.read()
        val plan = schedule.read()
        schedule.save(plan.copy(days = plan.days.map { it.copy(kind = "gym") }))
        rejects { preferences.save(edited(stale)) }
        val stalePlan = schedule.read()
        preferences.save(edited(preferences.read()))
        rejects {
            schedule.save(stalePlan.copy(days = stalePlan.days.map { it.copy(kind = "soccer") }))
        }
        val profileStale = preferences.read()
        val tracker = TrackerRepository(db)
        val profile = tracker.readProfile()
        tracker.saveProfile(Profile("Another", true), profile.second)
        rejects { preferences.save(edited(profileStale)) }
        val backups = BackupRepository(db)
        val importPreview = backups.preview(fixture())
        preferences.save(edited(preferences.read()))
        rejects { backups.confirm(importPreview) }
        val importStale = preferences.read()
        backups.confirm(backups.preview(fixture()))
        rejects { preferences.save(edited(importStale)) }
        val aba = preferences.read()
        val old = db.trackerDao().document("goals")!!
        db.openHelper.writableDatabase.execSQL("DELETE FROM documents WHERE name = 'goals'")
        db.trackerDao().insertDocument(old.copy(payload = "{\"weeklyGoal\":7}"))
        rejects { preferences.save(edited(aba)) }
    }

    @Test
    fun aFailureAfterProfileSettingsAndGoalWritesRollsBackEverythingThenRetries() = runBlocking {
        imported()
        val base = preferences.read()
        val before = db.trackerDao().documents()
        val old = db.trackerDao().document("native_profile")
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_preferences BEFORE UPDATE ON documents WHEN NEW.name = 'native_profile' BEGIN SELECT RAISE(ABORT, 'forced preferences failure'); END"
        )
        try {
            preferences.save(edited(base))
            fail("Expected failure")
        } catch (_: android.database.sqlite.SQLiteException) {}
        assertEquals(before, db.trackerDao().documents())
        assertEquals(old, db.trackerDao().document("native_profile"))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_preferences")
        assertFalse(preferences.save(edited(base)).changed)
    }

    @Test
    fun rejectsInvalidNamesRangesNumbersAndOutOfBoundsGoalsWithoutWrites() = runBlocking {
        imported()
        val base = preferences.read()
        val before = db.trackerDao().documents()
        val invalid =
            listOf(
                base.values.copy(displayName = "\u200b"),
                base.values.copy(displayName = "x".repeat(81)),
                base.values.copy(weeklyGoal = "101"),
                base.values.copy(weeklyGoal = "0"),
                base.values.copy(weeklyGoal = "1.5"),
                base.values.copy(numbers = base.values.numbers + ("defaultWeightJump" to "0.49")),
                base.values.copy(numbers = base.values.numbers + ("defaultWeightJump" to "1.234")),
                base.values.copy(
                    numbers =
                        base.values.numbers + ("compoundMin" to "11") + ("compoundMax" to "10")
                ),
                base.values.copy(numbers = base.values.numbers + ("pullMin" to "NaN")),
                base.values.copy(numbers = base.values.numbers + ("generalMax" to "1e3")),
                base.values.copy(numbers = base.values.numbers + ("isolationMax" to "1001")),
            )
        for (values in invalid) {
            assertNotNull(preferencesError(values))
            rejects { preferences.save(base.copy(values = values)) }
        }
        assertEquals(before, db.trackerDao().documents())
        assertNotNull(preferencesWarning(base.values.copy(weeklyGoal = "15")))
        assertNotNull(
            preferencesWarning(
                base.values.copy(numbers = base.values.numbers + ("pullMax" to "101"))
            )
        )
        assertNull(preferencesWarning(base.values.copy(weeklyGoal = "14")))
    }

    @Test
    fun restorationExcludesLargeImportedExtensionsAndNormalizesExponentRepresentations() =
        runBlocking {
            val data = fixture()
            val backups = BackupRepository(db)
            backups.confirm(
                backups.preview(
                    data.copy(
                        settings =
                            JsonObject(
                                data.settings!! +
                                    mapOf(
                                        "extension" to JsonPrimitive("x".repeat(600000)),
                                        "defaultWeightJump" to trackerJson.parseToJsonElement("5e0"),
                                    )
                            )
                    )
                )
            )
            val base = preferences.read()
            val snapshot = base.restorationState()!!
            assertTrue(snapshot.length < 4000)
            assertEquals(base, trackerJson.decodeFromString<PreferencesDraft>(snapshot))
            assertNull(
                base
                    .copy(values = base.values.copy(displayName = "x".repeat(100000)))
                    .restorationState()
            )
            val old = db.trackerDao().document("settings")!!
            preferences.save(base.copy(values = base.values.copy(weeklyGoal = "5")))
            assertEquals(old, db.trackerDao().document("settings"))
        }

    @Test
    fun goalObserverRecalculatesWithoutChangingOtherDataAndReopens() = runBlocking {
        imported()
        val stats = StatsRepository(db)
        val records = db.trackerDao().records()
        val initial = stats.read(LocalDate.parse("2026-10-23"))
        val observed =
            async(Dispatchers.IO) {
                withTimeout(10000) {
                    stats
                        .observe(kotlinx.coroutines.flow.flowOf(LocalDate.parse("2026-10-23")))
                        .first { it.goal == 9 }
                }
            }
        val base = preferences.read()
        preferences.save(base.copy(values = base.values.copy(weeklyGoal = "9")))
        assertEquals(initial.copy(goal = 9), observed.await())
        assertEquals(records, db.trackerDao().records())
    }

    @Test
    fun appHapticsAreGatedAndEveryReducedMotionSpecSnaps() {
        var count = 0
        val platform =
            object : HapticFeedback {
                override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                    count++
                }
            }
        PreferenceHaptics(false, platform).performHapticFeedback(HapticFeedbackType.LongPress)
        assertEquals(0, count)
        PreferenceHaptics(true, platform).performHapticFeedback(HapticFeedbackType.LongPress)
        assertEquals(1, count)
        assertTrue(StillMotion.defaultSpatialSpec<Float>() is SnapSpec)
        assertTrue(StillMotion.fastSpatialSpec<Float>() is SnapSpec)
        assertTrue(StillMotion.slowSpatialSpec<Float>() is SnapSpec)
        assertTrue(StillMotion.defaultEffectsSpec<Float>() is SnapSpec)
        assertTrue(StillMotion.fastEffectsSpec<Float>() is SnapSpec)
        assertTrue(StillMotion.slowEffectsSpec<Float>() is SnapSpec)
    }
}
