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
                started.exercises.all { it.notes.isEmpty() && it.sets.single().reps.isEmpty() }
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
