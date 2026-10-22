package com.hector.workouttracker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hector.workouttracker.core.backup.BackupCodec
import com.hector.workouttracker.core.database.*
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.data.*
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RoutineRepositoryTest {
    private lateinit var db: TrackerDatabase
    private lateinit var routines: RoutineRepository
    private val context
        get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun open() {
        context.deleteDatabase("routine-test.db")
        db = Room.databaseBuilder(context, TrackerDatabase::class.java, "routine-test.db").build()
        routines = RoutineRepository(db)
    }

    @After
    fun close() {
        db.close()
        context.deleteDatabase("routine-test.db")
    }

    private fun fixture() =
        BackupCodec.parse(
            requireNotNull(javaClass.classLoader!!.getResource("training-backup.json")).readText()
        )

    private suspend fun imported(): SavedRoutine {
        val backups = BackupRepository(db)
        backups.confirm(backups.preview(fixture()))
        return requireNotNull(routines.read(JsonPrimitive(0)))
    }

    private suspend fun rejected(action: suspend () -> Unit) {
        try {
            action()
            fail("Expected rejection")
        } catch (_: IllegalStateException) {} catch (_: IllegalArgumentException) {}
    }

    @Test
    fun editsPreserveRawExtensionsTypedIdAndUntouchedValues() = runBlocking {
        val before = imported()
        val draft = RoutineDraft.edit(before)
        assertFalse(draft.changed)
        assertEquals(before, routines.save(draft))
        val after =
            routines.save(
                draft.copy(
                    name = "Edited fixture",
                    exercises = listOf("My custom lift", "Flat Bench Press", "Flat Bench Press"),
                ),
                "2026-10-21T12:00:00Z",
            )
        assertEquals(before.record.revision + 1, after.record.revision)
        val original = trackerJson.parseToJsonElement(before.record.payload).jsonObject
        val edited = trackerJson.parseToJsonElement(after.record.payload).jsonObject
        for ((key, value) in original) if (key !in listOf("name", "exercises", "updatedAt"))
            assertEquals(key, value, edited[key])
        assertEquals(JsonPrimitive(0), after.id)
        assertEquals(
            listOf("My custom lift", "Flat Bench Press", "Flat Bench Press"),
            after.exercises,
        )
        // Large unknown fields never enter Android's saved-instance-state Bundle.
        val large =
            RecordEntity(
                "routine",
                typedIdKey(JsonPrimitive("large")),
                JsonObject(
                        original +
                            mapOf(
                                "id" to JsonPrimitive("large"),
                                "extension" to JsonPrimitive("x".repeat(600000)),
                            )
                    )
                    .toString(),
            )
        assertTrue(
            trackerJson.encodeToString(RoutineDraft.edit(routineSnapshot(large))).length < 3000
        )
        assertNull(draft.copy(id = JsonPrimitive("x".repeat(200000))).restorationState())
        assertNotNull(draft.restorationState())
        val export = BackupRepository(db).export("2026-10-21T12:00:00Z")
        assertEquals(edited, BackupCodec.parse(export).routines.single())
        File("build/outputs/compatibility").mkdirs()
        File("build/outputs/compatibility/routine-export.json").writeText(export)
    }

    @Test
    fun simultaneousNamesSerializeAndWhitespaceCaseCollisionsFail() = runBlocking {
        val drafts =
            listOf(
                RoutineDraft.create().copy(name = "  Day\u00a0One  "),
                RoutineDraft.create().copy(name = "day   one"),
            )
        val results =
            drafts.map { async(Dispatchers.IO) { runCatching { routines.save(it) } } }.awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, db.trackerDao().records().size)
        assertEquals("day one", routineNameKey("\uFEFFDay\u00a0  One\uFEFF"))
        val saved = results.single { it.isSuccess }.getOrThrow()
        assertTrue(saved.exercises.isEmpty())
    }

    @Test
    fun importedReplacementFencesEditsDeletionAndOldPreview() = runBlocking {
        val original = imported()
        val stale = RoutineDraft.edit(original).copy(name = "stale")
        val backups = BackupRepository(db)
        val preview = backups.preview(fixture())
        val latest = routines.save(RoutineDraft.edit(original).copy(name = "Latest"))
        rejected { backups.confirm(preview) }
        rejected { routines.save(stale) }
        rejected { routines.delete(original) }
        assertEquals(latest, routines.read(original.id))
        backups.confirm(backups.preview(fixture()))
        rejected { routines.save(RoutineDraft.edit(latest).copy(name = "stale after import")) }
        assertEquals("Training fixture", routines.read(original.id)!!.name)
    }

    @Test
    fun capacityUpdatesAndDeletionKeepOtherTypedIdsAndActiveHistory() = runBlocking {
        val selected = imported()
        val tracker = TrackerRepository(db)
        val active =
            tracker.startRoutine(selected.record, newSession(100000, 10, 1, ZoneId.of("UTC")))
        val oldWorkouts = db.trackerDao().records().filter { it.kind == "workout" }
        val oldDocuments = db.trackerDao().documents()
        val json = trackerJson.parseToJsonElement(selected.record.payload).jsonObject
        for (index in 1..499) {
            val id = if (index == 1) JsonPrimitive("0") else JsonPrimitive("capacity-$index")
            db.trackerDao()
                .insertRecord(
                    RecordEntity(
                        "routine",
                        typedIdKey(id),
                        JsonObject(
                                json + mapOf("id" to id, "name" to JsonPrimitive("Capacity $index"))
                            )
                            .toString(),
                    )
                )
        }
        rejected { routines.save(RoutineDraft.create().copy(name = "Over capacity")) }
        val updated = routines.save(RoutineDraft.edit(selected).copy(name = "Renamed"))
        routines.delete(updated)
        assertNull(routines.read(JsonPrimitive(0)))
        assertNotNull(routines.read(JsonPrimitive("0")))
        assertEquals(active, tracker.readDraft()!!.first)
        assertEquals(oldWorkouts, db.trackerDao().records().filter { it.kind == "workout" })
        assertEquals(oldDocuments, db.trackerDao().documents())
        routines.save(RoutineDraft.create().copy(name = "Now fits"))
        assertEquals(500, db.trackerDao().records().count { it.kind == "routine" })
    }

    @Test
    fun draftSelectionKeepsOrderIntentionalDuplicatesAndValidation() = runBlocking {
        val original = imported()
        val draft = RoutineDraft.edit(original)
        assertTrue(draft.duplicate(" flat   bench press ", 1))
        val replaced = draft.select(" Custom row ", 1)
        assertEquals(
            listOf("Flat Bench Press", "Custom row", "Flat Bench Press"),
            replaced.exercises,
        )
        val duplicate = replaced.select("Flat Bench Press")
        assertEquals(4, duplicate.exercises.size)
        assertEquals(
            duplicate,
            trackerJson.decodeFromString<RoutineDraft>(trackerJson.encodeToString(duplicate)),
        )
        rejected { routines.save(draft.copy(name = "\u200b")) }
        rejected { routines.save(draft.copy(name = "x".repeat(81))) }
        rejected { routines.save(draft.copy(exercises = List(101) { "Row" })) }
        rejected { routines.save(draft.copy(exercises = listOf("Bad\nname"))) }
        assertEquals(original, routines.read(original.id))
    }
}
