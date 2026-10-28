package com.hector.workouttracker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hector.workouttracker.core.database.*
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.data.*
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
class RepositoryTest {
    private lateinit var db: TrackerDatabase
    private lateinit var repository: TrackerRepository
    private val context
        get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun open() {
        context.deleteDatabase("repository-test.db")
        reopen()
    }

    private fun reopen() {
        db =
            Room.databaseBuilder(context, TrackerDatabase::class.java, "repository-test.db").build()
        repository = TrackerRepository(db)
    }

    @After
    fun close() {
        db.close()
        context.deleteDatabase("repository-test.db")
    }

    private fun draft() =
        newSession(100000, 10, 1, ZoneId.of("UTC"))
            .copy(
                exercises =
                    listOf(
                        Exercise(
                            name = "Bench Press",
                            sets = listOf(WorkingSet(weight = "100", reps = "5")),
                        )
                    )
            )

    @Test
    fun sharedLibraryIndexRechecksEditedAndDeletedHistoryBeforeCopyingSets() = runBlocking {
        val dao = db.trackerDao()
        val payload = workoutPayload(draft(), 110000, 0, ZoneId.of("UTC"))
        val id = typedIdKey(payload.getValue("id"))
        dao.insertRecord(RecordEntity("workout", id, payload.toString()))
        val routine =
            RecordEntity(
                "routine",
                typedIdKey(JsonPrimitive(0)),
                """{"id":0,"name":"Bench","exercises":["Bench Press"]}""",
            )
        dao.insertRecord(routine)
        val routines = listOf(routineSnapshot(routine))
        val records = dao.recordsOfKind("workout")
        assertEquals(
            localExerciseNames(records, routines),
            repository.localNames(records, routines),
        )
        suspend fun copiedWeight(): String {
            val started = repository.startRoutine(routine, draft().copy(exercises = emptyList()))
            val weight = started.exercises.single().sets.single().weight
            repository.discard(started.id, 1)
            return weight
        }
        assertEquals("100", copiedWeight())
        val exercise = payload.getValue("exercises").jsonArray.single().jsonObject
        val set = exercise.getValue("sets").jsonArray.single().jsonObject
        val revisedExercise =
            JsonObject(
                exercise +
                    ("sets" to
                        JsonArray(listOf(JsonObject(set + ("weight" to JsonPrimitive("120"))))))
            )
        val revised = JsonObject(payload + ("exercises" to JsonArray(listOf(revisedExercise))))
        assertEquals(1, dao.updateRecord("workout", id, 1, revised.toString()))
        assertEquals("120", copiedWeight())
        assertEquals(1, dao.deleteRecord("workout", id, 2))
        assertEquals("", copiedWeight())
        assertEquals(emptyList<String>(), repository.localNames(emptyList(), emptyList()))
    }

    @Test
    fun committedDraftAndProfileSurviveDatabaseReopen() = runBlocking {
        val d = draft()
        repository.start(d)
        repository.saveProfile(Profile("Fixture", false), 0)
        val changed =
            d.copy(
                notes = "persisted",
                exercises = d.exercises.map { it.copy(sets = it.sets + WorkingSet(reps = "10")) },
            )
        repository.saveDraft(changed, 1)
        db.close()
        reopen()
        assertEquals(changed, repository.readDraft()!!.first)
        assertEquals(Profile("Fixture", false), repository.readProfile().first)
    }

    @Test
    fun staleWritesDoNotOverwriteAndFinishConsumesDraftOnce() = runBlocking {
        val d = draft()
        repository.start(d)
        repository.saveDraft(d.copy(notes = "new"), 1)
        try {
            repository.saveDraft(d.copy(notes = "stale"), 1)
            fail("stale write")
        } catch (_: WriteConflict) {}
        assertEquals("new", repository.readDraft()!!.first.notes)
        val current = repository.readDraft()!!
        val payload = workoutPayload(current.first, 110000, 0, ZoneId.of("UTC"))
        repository.finish(current.first, current.second, payload)
        assertNull(repository.readDraft())
        try {
            repository.finish(current.first, current.second, payload)
            fail("duplicate finish")
        } catch (_: WriteConflict) {}
        assertEquals(
            payload.toString(),
            db.trackerDao().record("workout", typedIdKey(payload.getValue("id")))!!.payload,
        )
    }

    @Test
    fun collapsedDraftSurvivesReopenAndStaleWritersWithoutChangingSavedData() = runBlocking {
        val original =
            draft()
                .copy(
                    id = "session-flow-fixture",
                    originRoutineId = JsonPrimitive("0"),
                    exercises = draft().exercises + Exercise(name = "Second lift"),
                )
        repository.start(original)
        val opened = original.openExercise(original.exercises[1].key)
        repository.saveDraft(opened, 1)
        try {
            repository.saveDraft(original, 1)
            fail("Stale writer replaced collapse state")
        } catch (_: WriteConflict) {}
        db.close()
        reopen()
        assertEquals(opened to 2L, repository.readDraft())
        assertTrue(db.trackerDao().records().isEmpty())
        val canonical = workoutPayload(opened, 110000, 0, ZoneId.of("UTC"))
        assertEquals(workoutPayload(original, 110000, 0, ZoneId.of("UTC")), canonical)
        repository.finish(opened, 2, canonical)
        val export = BackupRepository(db).export("2026-10-23T18:00:00Z")
        assertFalse(export.contains("collapsed"))
        val output = java.io.File("build/outputs/compatibility/session-flow-export.json")
        requireNotNull(output.parentFile).mkdirs()
        output.writeText(export)
    }

    @Test
    fun exerciseOrderSurvivesReopenStaleWritesFinishAndExport() = runBlocking {
        val original =
            draft()
                .copy(
                    id = "session-order-fixture",
                    originRoutineId = JsonPrimitive(0),
                    notes = "Workout order retained",
                    tags = listOf("Good session"),
                    exercises =
                        listOf(
                            Exercise(
                                key = "a",
                                name = "Repeated lift",
                                notes = "First",
                                sets = listOf(WorkingSet(weight = "", reps = "5")),
                            ),
                            Exercise(
                                key = "b",
                                name = "Repeated lift",
                                notes = "Second",
                                sets =
                                    listOf(
                                        WorkingSet(
                                            weight = "0",
                                            reps = "8",
                                            rpe = "9",
                                            done = true,
                                            warmup = true,
                                        )
                                    ),
                            ),
                        ),
                )
        repository.start(original)
        val moved = original.moveExercise("b", -1)
        repository.saveDraft(moved, 1)
        try {
            repository.saveDraft(original, 1)
            fail("Stale order replaced the saved order")
        } catch (_: WriteConflict) {}
        db.close()
        reopen()
        assertEquals(moved to 2L, repository.readDraft())
        val canonical = workoutPayload(moved, 110000, 0, ZoneId.of("UTC"))
        assertEquals(
            listOf("Second", "First"),
            canonical.getValue("exercises").jsonArray.map {
                it.jsonObject.getValue("notes").jsonPrimitive.content
            },
        )
        repository.finish(moved, 2, canonical)
        assertNull(repository.readDraft())
        val export = BackupRepository(db).export("2026-10-23T18:00:00Z")
        assertEquals(
            canonical,
            com.hector.workouttracker.core.backup.BackupCodec.parse(export).workouts.single(),
        )
        val output = java.io.File("build/outputs/compatibility/session-order-export.json")
        requireNotNull(output.parentFile).mkdirs()
        output.writeText(export)
    }

    @Test
    fun failedHistoryInsertRollsBackDraftConsumption() = runBlocking {
        val d = draft()
        repository.start(d)
        val payload = workoutPayload(d, 110000, 0, ZoneId.of("UTC"))
        db.trackerDao()
            .insertRecord(
                RecordEntity("workout", typedIdKey(payload.getValue("id")), payload.toString())
            )
        try {
            repository.finish(d, 1, payload)
            fail("collision")
        } catch (_: android.database.sqlite.SQLiteException) {}
        assertEquals(d, repository.readDraft()!!.first)
    }

    @Test
    fun rawRecordStoragePreservesTypedIdsAndUnknownFields() = runBlocking {
        for (id in listOf(JsonPrimitive(0), JsonPrimitive("0"))) {
            val payload = buildJsonObject {
                put("id", id)
                put("extension", buildJsonObject { put("nested", "retained") })
            }
            db.trackerDao()
                .insertRecord(RecordEntity("workout", typedIdKey(id), payload.toString()))
        }
        db.close()
        reopen()
        for (key in listOf("number:0", "string:0")) {
            val stored =
                trackerJson
                    .parseToJsonElement(db.trackerDao().record("workout", key)!!.payload)
                    .jsonObject
            assertEquals(
                "retained",
                stored.getValue("extension").jsonObject.getValue("nested").jsonPrimitive.content,
            )
        }
    }

    @Test
    fun staleProfileSaveAndStaleDiscardFailWithoutMutation() = runBlocking {
        repository.saveProfile(Profile("First"), 0)
        repository.saveProfile(Profile("Second"), 1)
        try {
            repository.saveProfile(Profile("Stale"), 1)
            fail("stale profile")
        } catch (_: WriteConflict) {}
        assertEquals("Second", repository.readProfile().first.displayName)
        val d = draft()
        repository.start(d)
        repository.saveDraft(d.copy(notes = "keep"), 1)
        try {
            repository.discard(d.id, 1)
            fail("stale discard")
        } catch (_: WriteConflict) {}
        assertEquals("keep", repository.readDraft()!!.first.notes)
    }

    @Test
    fun routineLaunchPreservesTypedOriginOrderDuplicatesAndFencesStaleSelections() = runBlocking {
        for (id in listOf(JsonPrimitive(0), JsonPrimitive("0"))) {
            val payload = buildJsonObject {
                put("id", id)
                put("name", "Saved routine")
                put(
                    "exercises",
                    JsonArray(
                        listOf("Flat Bench Press", "My custom lift", "Flat Bench Press")
                            .map(::JsonPrimitive)
                    ),
                )
                put("extension", "retained")
                put("createdAt", "2026-10-21T12:00:00Z")
                put("updatedAt", "2026-10-21T12:00:00Z")
            }
            val record = RecordEntity("routine", typedIdKey(id), payload.toString())
            db.trackerDao().insertRecord(record)
            val started = repository.startRoutine(record, draft().copy(exercises = emptyList()))
            assertEquals(id, started.originRoutineId)
            assertEquals(
                listOf("Flat Bench Press", "My custom lift", "Flat Bench Press"),
                started.exercises.map { it.name },
            )
            assertEquals(3, started.exercises.map { it.key }.toSet().size)
            assertTrue(
                started.exercises.all {
                    it.notes.isEmpty() && it.sets.single().reps == if (id.isString) "5" else ""
                }
            )
            db.close()
            reopen()
            assertEquals(started, repository.readDraft()!!.first)
            val valid =
                started.copy(
                    exercises =
                        started.exercises.map {
                            it.copy(sets = listOf(WorkingSet(weight = "0", reps = "5")))
                        }
                )
            repository.saveDraft(valid, 1)
            val canonical = workoutPayload(valid, 110000, 0, ZoneId.of("UTC"))
            assertEquals(id, canonical.getValue("originRoutineId"))
            val fields = canonical.getValue("exercises").jsonArray.first().jsonObject.keys
            assertEquals(setOf("name", "notes", "sets"), fields)
            repository.finish(valid, 2, canonical)
            assertEquals(record, db.trackerDao().record("routine", record.idKey))
            db.trackerDao().updateRecord("routine", record.idKey, 1, payload.toString())
            try {
                repository.startRoutine(record, draft())
                fail("stale routine")
            } catch (_: WriteConflict) {}
            assertNull(repository.readDraft())
        }
        val output = java.io.File("build/outputs/compatibility/training-export.json")
        requireNotNull(output.parentFile).mkdirs()
        output.writeText(BackupRepository(db).export("2026-10-21T18:00:00Z"))
    }

    @Test
    fun routineLaunchCannotReplaceActiveDraftAndOldDraftFormatStillReads() = runBlocking {
        val record =
            RecordEntity(
                "routine",
                "number:0",
                """{"id":0,"name":"Empty routine","exercises":[]}""",
            )
        db.trackerDao().insertRecord(record)
        val original = draft()
        repository.start(original)
        try {
            repository.startRoutine(record, draft())
            fail("active draft overwritten")
        } catch (_: IllegalStateException) {}
        assertEquals(original, repository.readDraft()!!.first)
        val legacy =
            trackerJson.encodeToJsonElement(SessionDraft.serializer(), original).jsonObject.let {
                JsonObject(it - "originRoutineId")
            }
        assertNull(
            trackerJson.decodeFromJsonElement(SessionDraft.serializer(), legacy).originRoutineId
        )
        assertFalse(
            workoutPayload(original, 110000, 0, ZoneId.of("UTC")).containsKey("originRoutineId")
        )
        repository.discard(original.id, 1)
        val empty = repository.startRoutine(record, original.copy(exercises = emptyList()))
        assertTrue(empty.exercises.isEmpty())
        assertEquals("Empty routine", empty.type)
    }
}
