package com.hector.workouttracker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hector.workouttracker.core.backup.*
import com.hector.workouttracker.core.database.*
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.data.*
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class BackupRepositoryTest {
    private lateinit var db: TrackerDatabase
    private lateinit var backups: BackupRepository
    private lateinit var tracker: TrackerRepository
    private val context
        get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun open() {
        context.deleteDatabase("backup-test.db")
        reopen()
    }

    private fun reopen() {
        db = Room.databaseBuilder(context, TrackerDatabase::class.java, "backup-test.db").build()
        backups = BackupRepository(db)
        tracker = TrackerRepository(db)
    }

    @After
    fun close() {
        db.close()
        context.deleteDatabase("backup-test.db")
    }

    private fun data(): BackupData {
        val root =
            Json.parseToJsonElement(javaClass.getResource("/backup-migration.json")!!.readText())
                .jsonObject
        return BackupCodec.parse(
            root.getValue("cases").jsonArray.last().jsonObject.getValue("backup").toString()
        )
    }

    private suspend fun import(data: BackupData = data()) = backups.confirm(backups.preview(data))

    private fun draft() = newSession(100000, 10, 1, ZoneId.of("UTC"))

    @Test
    fun mergeIsTypedIdSafeIdempotentRevisionedAndDurable() = runBlocking {
        val initial = data()
        val unrelated = JsonObject(initial.workouts.first() + ("id" to JsonPrimitive("unrelated")))
        db.trackerDao()
            .insertRecord(RecordEntity("workout", "string:unrelated", unrelated.toString()))
        val preview = backups.preview(initial)
        assertEquals(ImportCounts(2, 0, 0), preview.workouts)
        backups.confirm(preview)
        assertEquals(Profile("Fixture User", true), tracker.readProfile().first)
        assertEquals(3, db.trackerDao().records().count { it.kind == "workout" })
        val before = db.trackerDao().records()
        val docs = db.trackerDao().documents()
        assertEquals(ImportCounts(0, 0, 2), backups.preview(initial).workouts)
        import(initial)
        assertEquals(before, db.trackerDao().records())
        assertEquals(docs, db.trackerDao().documents())
        val changed =
            initial.copy(
                workouts =
                    listOf(
                        JsonObject(
                            initial.workouts.first() + ("notes" to JsonPrimitive("replacement"))
                        )
                    )
            )
        val replacement = backups.preview(changed)
        assertEquals(ImportCounts(0, 1, 0), replacement.workouts)
        backups.confirm(replacement)
        assertEquals(2L, db.trackerDao().record("workout", "number:0")!!.revision)
        assertEquals(1L, db.trackerDao().record("workout", "string:0")!!.revision)
        assertEquals(0, db.trackerDao().updateRecord("workout", "number:0", 1, "stale"))
        db.close()
        reopen()
        assertEquals(
            "replacement",
            Json.parseToJsonElement(db.trackerDao().record("workout", "number:0")!!.payload)
                .jsonObject
                .getValue("notes")
                .jsonPrimitive
                .content,
        )
        assertEquals(
            initial.routines.single(),
            Json.parseToJsonElement(db.trackerDao().record("routine", "number:0")!!.payload),
        )
    }

    @Test
    fun protectsActiveDraftAndRejectsChangedPreviewWithoutPartialWrites() = runBlocking {
        val preview = backups.preview(data())
        tracker.start(draft())
        try {
            backups.confirm(preview)
            fail("active draft")
        } catch (_: IllegalStateException) {}
        try {
            backups.preview(data())
            fail("active draft")
        } catch (_: IllegalStateException) {}
        assertNotNull(tracker.readDraft())
        assertTrue(db.trackerDao().records().isEmpty())
        val draft = tracker.readDraft()!!
        tracker.discard(draft.first.id, draft.second)
        val fresh = backups.preview(data())
        tracker.saveProfile(Profile("Changed while preview open"), 0)
        try {
            backups.confirm(fresh)
            fail("stale preview")
        } catch (_: IllegalStateException) {}
        assertTrue(db.trackerDao().records().isEmpty())
        assertEquals("Changed while preview open", tracker.readProfile().first.displayName)
    }

    @Test
    fun databaseFailureRollsBackRecordsAndEveryDocument() = runBlocking {
        val preview = backups.preview(data())
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_import BEFORE INSERT ON documents WHEN NEW.name = 'goals' BEGIN SELECT RAISE(ABORT, 'forced document failure'); END"
        )
        try {
            backups.confirm(preview)
            fail("forced failure")
        } catch (_: android.database.sqlite.SQLiteException) {}
        assertTrue(db.trackerDao().records().isEmpty())
        assertTrue(db.trackerDao().documents().isEmpty())
        assertNull(tracker.readDraft())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_import")
        backups.confirm(preview)
        assertEquals(4, db.trackerDao().records().size)
    }

    @Test
    fun omittedDocumentsRetainGoalsMetadataAndPreferencesButRequireNameSetup() = runBlocking {
        import()
        val empty = BackupCodec.parse("{\"workouts\":[]}")
        val before =
            db.trackerDao().documents().filter { it.name in listOf("goals", "backup_meta") }
        val preview = backups.preview(empty)
        assertFalse(preview.settingsSupplied)
        assertNull(preview.displayName)
        backups.confirm(preview)
        assertEquals(
            before,
            db.trackerDao().documents().filter { it.name in listOf("goals", "backup_meta") },
        )
        assertEquals(Profile("", true), tracker.readProfile().first)
        assertEquals(
            data().workouts,
            BackupCodec.parse(backups.export("2026-10-21T18:00:00Z")).workouts,
        )
    }

    @Test
    fun mergedRoutineCapacityFailureRollsBackEarlierWorkoutWrites() = runBlocking {
        val template = data().routines.first()
        for (i in 1..500) db.trackerDao()
            .insertRecord(
                RecordEntity(
                    "routine",
                    "number:$i",
                    JsonObject(template + ("id" to JsonPrimitive(i))).toString(),
                )
            )
        val before = db.trackerDao().records()
        try {
            import()
            fail("capacity")
        } catch (_: IllegalStateException) {}
        assertEquals(before, db.trackerDao().records())
        assertTrue(db.trackerDao().documents().isEmpty())
    }

    @Test
    fun nativeExportRetainsExtensionsAndMapsProfileWithoutDeletingHistoricalRpe() = runBlocking {
        import()
        val profile = tracker.readProfile()
        tracker.saveProfile(Profile("Android User", false), profile.second)
        val draft =
            draft()
                .copy(
                    exercises =
                        listOf(
                            Exercise(
                                name = "Native exercise",
                                sets = listOf(WorkingSet(weight = "0", reps = "10", rpe = "8.5")),
                            )
                        )
                )
        tracker.start(draft)
        val payload = workoutPayload(draft, 110000, 0, ZoneId.of("UTC"))
        tracker.finish(draft, 1, payload)
        val text = backups.export("2026-10-21T18:00:00Z")
        val exported = BackupCodec.parse(text)
        assertEquals(3, exported.workouts.size)
        assertEquals(data().routines, exported.routines)
        assertEquals(data().weights, exported.weights)
        assertEquals(
            data().settings!!.getValue("extension"),
            exported.settings!!.getValue("extension"),
        )
        assertEquals(JsonPrimitive("Android User"), exported.settings.getValue("displayName"))
        assertEquals(JsonPrimitive(false), exported.settings.getValue("rpeAware"))
        assertEquals(
            data().workouts.first(),
            exported.workouts.first { it.getValue("id") == JsonPrimitive(0) },
        )
        assertEquals(data().goals, exported.goals)
        assertEquals(
            data().metadata!!.getValue("extension"),
            exported.metadata!!.getValue("extension"),
        )
        val root = Json.parseToJsonElement(text).jsonObject
        assertFalse(root.containsKey("draft"))
        assertFalse(root.containsKey("native_profile"))
        // Consumed by the PWA's real parser + semantic validator in native CI.
        File("build/outputs/compatibility").mkdirs()
        File("build/outputs/compatibility/native-export.json").writeText(text)
        db.close()
        reopen()
        assertEquals(Profile("Android User", false), tracker.readProfile().first)
    }

    @Test
    fun importsAndExportsARealisticMultiYearHistory() = runBlocking {
        val initial = data()
        val records =
            (1..2000).map { i ->
                JsonObject(initial.workouts[i % 2] + ("id" to JsonPrimitive("history-$i")))
            }
        val large =
            BackupCodec.parse(
                BackupCodec.encode(initial.copy(workouts = records), "2026-10-21T18:00:00Z")
            )
        import(large)
        val exported = BackupCodec.parse(backups.export("2026-10-21T18:00:00Z"))
        assertEquals(2000, exported.workouts.size)
        assertEquals(
            records.associateBy { it.getValue("id") },
            exported.workouts.associateBy { it.getValue("id") },
        )
    }

    @Test
    fun foundationSchemaProfileAndDraftAreReadWithoutResettingUserData() = runBlocking {
        // Schema 1 was shipped in foundation. Simulate its native-only profile (no canonical
        // settings).
        db.trackerDao()
            .insertDocument(
                DocumentEntity(
                    "native_profile",
                    "{\"displayName\":\"Foundation User\",\"trackRpe\":false}",
                )
            )
        val draft = draft()
        tracker.start(draft)
        db.close()
        reopen()
        assertEquals(draft, tracker.readDraft()!!.first)
        val exported = BackupCodec.parse(backups.export("2026-10-21T18:00:00Z"))
        assertEquals(JsonPrimitive("Foundation User"), exported.settings!!.getValue("displayName"))
        assertEquals(JsonPrimitive(false), exported.settings.getValue("rpeAware"))
        assertTrue(exported.workouts.isEmpty())
    }
}
