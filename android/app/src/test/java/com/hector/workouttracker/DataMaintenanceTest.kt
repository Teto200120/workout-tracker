package com.hector.workouttracker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hector.workouttracker.core.backup.BackupCodec
import com.hector.workouttracker.core.catalog.*
import com.hector.workouttracker.core.database.*
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.data.*
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class DataMaintenanceTest {
    private lateinit var db: TrackerDatabase
    private val context
        get() = ApplicationProvider.getApplicationContext<Context>()

    private val dao
        get() = db.trackerDao()

    private val maintenance
        get() = DataMaintenanceRepository(db)

    private fun asset(name: String) = javaClass.getResource("/$name")!!.readText()

    private fun fixture() = BackupCodec.parse(asset("training-backup.json"))

    private fun starters() =
        StarterLibrary.parse(
            asset("starter-routines.json"),
            ExerciseCatalog.parse(asset("exercise-catalog.json")),
        )

    @Before
    fun open() {
        context.deleteDatabase("maintenance-test.db")
        db =
            Room.databaseBuilder(context, TrackerDatabase::class.java, "maintenance-test.db")
                .build()
    }

    @After
    fun close() {
        db.close()
        context.deleteDatabase("maintenance-test.db")
    }

    private suspend fun state() = Triple(dao.records(), dao.documents(), dao.draft())

    private suspend fun populated() {
        val backups = BackupRepository(db)
        backups.confirm(backups.preview(fixture()))
        val preferences = PreferencesRepository(db)
        val before = preferences.read()
        preferences.save(
            before.copy(
                values =
                    before.values.copy(
                        displayName = "Reset user",
                        trackRpe = false,
                        haptics = false,
                        animations = false,
                        weeklyGoal = "6",
                        numbers = before.values.numbers + ("defaultWeightJump" to "2.5"),
                    )
            )
        )
        dao.insertRecord(
            RecordEntity(
                "legacy-weight",
                typedIdKey(JsonPrimitive("legacy")),
                """{"id":"legacy","date":"2026-10-20","weight":165,"notes":"keep","createdAt":"2026-10-20T12:00:00Z"}""",
            )
        )
        dao.insertDocument(
            DocumentEntity(
                "native_guidance",
                """{"extension":"clear only with all data","experiences":{}}""",
            )
        )
        TrackerRepository(db)
            .start(
                newSession(100000, 10, 1, ZoneId.of("UTC"))
                    .copy(
                        notes = "Keep draft",
                        exercises =
                            listOf(
                                Exercise(
                                    name = "Bench Press",
                                    collapsed = true,
                                    sets =
                                        listOf(
                                            WorkingSet(
                                                weight = "100",
                                                reps = "5",
                                                rpe = "8",
                                                done = true,
                                            )
                                        ),
                                )
                            ),
                    )
            )
    }

    private suspend fun rejected(action: suspend () -> Unit) {
        try {
            action()
            fail("Expected rejection")
        } catch (_: IllegalStateException) {} catch (_: IllegalArgumentException) {}
    }

    private fun output(name: String, text: String) {
        val file = File("build/outputs/compatibility/$name.json")
        file.parentFile!!.mkdirs()
        file.writeText(text)
    }

    @Test
    fun actualPwaResetCasesPreserveAllOtherDataAndReopen() = runBlocking {
        val cases =
            trackerJson
                .parseToJsonElement(asset("data-actions.json"))
                .jsonObject
                .getValue("cases")
                .jsonArray
        for (c in cases.map { it.jsonObject }) {
            dao.clearDrafts()
            dao.clearRecords()
            dao.clearDocuments()
            val source =
                fixture()
                    .copy(
                        settings = c.getValue("initial").jsonObject,
                        goals = c.getValue("goals").jsonObject,
                    )
            val backups = BackupRepository(db)
            backups.confirm(backups.preview(source))
            TrackerRepository(db)
                .start(newSession(100000, 10, 1, ZoneId.of("UTC")).copy(notes = "Untouched"))
            val before = state()
            val review = maintenance.review(DataAction.RESET_SETTINGS)
            assertEquals(before, state()) // Opening/cancelling a review never writes.
            val mode = c.getValue("mode").jsonPrimitive.content
            when (mode) {
                "confirm" -> maintenance.confirm(review)
                "cancel" -> Unit
                "stale" -> {
                    val old = dao.document("settings")!!
                    dao.updateDocument(old.name, old.revision, c.getValue("settings").toString())
                    rejected { maintenance.confirm(review) }
                }
                "failure" -> {
                    db.openHelper.writableDatabase.execSQL(
                        "CREATE TRIGGER fail_reset BEFORE UPDATE ON documents WHEN OLD.name='native_profile' BEGIN SELECT RAISE(ABORT,'synthetic failure'); END"
                    )
                    try {
                        maintenance.confirm(review)
                        fail("Expected rollback")
                    } catch (_: android.database.sqlite.SQLiteException) {}
                    db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_reset")
                    assertEquals(before, state())
                }
            }
            assertEquals(
                mode,
                c.getValue("settings"),
                trackerJson.parseToJsonElement(dao.document("settings")!!.payload),
            )
            assertEquals(
                mode,
                c.getValue("goals"),
                trackerJson.parseToJsonElement(dao.document("goals")!!.payload),
            )
            assertEquals(before.first, dao.records())
            assertEquals(before.third, dao.draft())
        }
        populatedAfterClear()
        val before = state()
        val stalePreferences = PreferencesRepository(db).read()
        val result = maintenance.confirm(maintenance.review(DataAction.RESET_SETTINGS))
        assertEquals(Profile("Reset user", true), result.profile)
        assertEquals(before.first, dao.records())
        assertEquals(before.third, dao.draft())
        assertEquals(
            before.second.filter { it.name !in setOf("settings", "native_profile") },
            dao.documents().filter { it.name !in setOf("settings", "native_profile") },
        )
        rejected { PreferencesRepository(db).save(stalePreferences) }
        val after = state()
        output("settings-reset-export", BackupRepository(db).export("2026-10-24T12:00:00Z"))
        db.close()
        db =
            Room.databaseBuilder(context, TrackerDatabase::class.java, "maintenance-test.db")
                .build()
        assertEquals(after, state())
    }

    private suspend fun populatedAfterClear() {
        dao.clearDrafts()
        dao.clearRecords()
        dao.clearDocuments()
        populated()
    }

    @Test
    fun clearIsAtomicRestoresReviewedStartersAndSurvivesReopen() = runBlocking {
        populated()
        val before = state()
        val oldDraft = TrackerRepository(db).readDraft()!!
        val oldPreferences = PreferencesRepository(db).read()
        val review = maintenance.review(DataAction.CLEAR_DATA)
        assertEquals(2, review.workouts)
        assertEquals(1, review.routines)
        assertEquals(1, review.legacyWeights)
        assertTrue(review.hasDraft)
        assertEquals(before, state())
        val result = maintenance.confirm(review, starters(), "2026-10-24T12:00:00Z")
        assertEquals(Profile(), result.profile)
        assertNotEquals("initial", result.epoch)
        assertNull(dao.draft())
        assertTrue(dao.records().all { it.kind == "routine" })
        assertEquals(6, dao.records().size)
        assertEquals(
            setOf("settings", "goals", "native_profile", "native_data_epoch"),
            dao.documents().map { it.name }.toSet(),
        )
        assertEquals(
            BackupCodec.defaultSettings,
            trackerJson.parseToJsonElement(dao.document("settings")!!.payload),
        )
        assertEquals("4", PreferencesRepository(db).read().values.weeklyGoal)
        assertTrue(result.profileRevision > before.second.maxOf { it.revision })
        rejected { TrackerRepository(db).saveDraft(oldDraft.first, oldDraft.second) }
        rejected { PreferencesRepository(db).save(oldPreferences) }
        rejected { maintenance.confirm(review, starters()) }
        val after = state()
        val text = BackupRepository(db).export("2026-10-24T12:00:00Z")
        val exported = BackupCodec.parse(text)
        assertTrue(exported.workouts.isEmpty())
        assertTrue(exported.weights.isEmpty())
        assertEquals(6, exported.routines.size)
        assertFalse(text.contains("native_data_epoch"))
        assertFalse(text.contains("Reset user"))
        assertFalse(text.contains("Keep draft"))
        output("data-cleared-export", text)
        db.close()
        db =
            Room.databaseBuilder(context, TrackerDatabase::class.java, "maintenance-test.db")
                .build()
        assertEquals(after, state())
        assertEquals(result.epoch, maintenance.currentEpoch())
        val revision =
            TrackerRepository(db).saveProfile(Profile("New user", false), result.profileRevision)
        assertEquals(result.profileRevision + 1, revision)
        assertEquals(Profile("New user", false), TrackerRepository(db).readProfile().first)
    }

    @Test
    fun clearRejectsChangedRecordsDraftGuidanceAndSettingsWhileResetKeepsUnrelatedChanges() =
        runBlocking {
            populated()
            suspend fun changed(mutate: suspend () -> Unit) {
                val review = maintenance.review(DataAction.CLEAR_DATA)
                mutate()
                val after = state()
                rejected { maintenance.confirm(review, starters()) }
                assertEquals(after, state())
            }
            changed {
                val row = dao.recordsOfKind("workout").first()
                dao.updateRecord(row.kind, row.idKey, row.revision, row.payload)
            }
            changed {
                val draft = TrackerRepository(db).readDraft()!!
                TrackerRepository(db).saveDraft(draft.first.copy(notes = "New entry"), draft.second)
            }
            changed {
                val row = dao.document("native_guidance")!!
                dao.updateDocument(row.name, row.revision, "{}")
            }
            changed {
                val row = dao.document("settings")!!
                dao.updateDocument(row.name, row.revision, row.payload)
            }
            changed {
                dao.insertRecord(
                    RecordEntity(
                        "workout",
                        "string:new",
                        JsonObject(fixture().workouts.first() + ("id" to JsonPrimitive("new")))
                            .toString(),
                    )
                )
            }
            val review = maintenance.review(DataAction.RESET_SETTINGS)
            val row = dao.recordsOfKind("workout").first()
            dao.updateRecord(row.kind, row.idKey, row.revision, row.payload)
            val records = dao.records()
            maintenance.confirm(review)
            assertEquals(records, dao.records())
        }

    @Test
    fun clearRollsBackEveryDeletionAndDefaultSeedingFailure() = runBlocking {
        populated()
        val before = state()
        rejected { maintenance.confirm(maintenance.review(DataAction.CLEAR_DATA)) }
        assertEquals(before, state())
        for ((table, event, condition) in
            listOf(
                Triple("records", "DELETE", ""),
                Triple("documents", "DELETE", ""),
                Triple("records", "INSERT", "WHEN NEW.kind='routine'"),
                Triple("documents", "INSERT", "WHEN NEW.name='native_data_epoch'"),
            )) {
            db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_clear BEFORE $event ON $table $condition BEGIN SELECT RAISE(ABORT,'synthetic failure'); END"
            )
            try {
                maintenance.confirm(maintenance.review(DataAction.CLEAR_DATA), starters())
                fail("Expected rollback")
            } catch (_: android.database.sqlite.SQLiteException) {}
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_clear")
            assertEquals("$event $table", before, state())
        }
    }

    @Test
    fun competingClearReviewsOnlyCommitOnceAndOldImportCannotReplay() = runBlocking {
        val backups = BackupRepository(db)
        backups.confirm(backups.preview(fixture()))
        val oldImport = backups.preview(fixture())
        val a = maintenance.review(DataAction.CLEAR_DATA)
        val b = maintenance.review(DataAction.CLEAR_DATA)
        val library = starters()
        val results =
            awaitAll(
                async(Dispatchers.Default) { runCatching { maintenance.confirm(a, library) } },
                async(Dispatchers.Default) { runCatching { maintenance.confirm(b, library) } },
            )
        assertEquals(1, results.count { it.isSuccess })
        assertTrue(results.single { it.isFailure }.exceptionOrNull() is DataReviewChanged)
        rejected { backups.confirm(oldImport) }
        assertEquals(6, dao.records().size)
        assertNull(dao.draft())
    }
}
