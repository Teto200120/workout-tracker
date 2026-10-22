package com.hector.workouttracker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hector.workouttracker.core.backup.BackupCodec
import com.hector.workouttracker.core.database.*
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.data.*
import com.hector.workouttracker.domain.workoutMetrics
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
class HistoryRepositoryTest {
    private lateinit var db: TrackerDatabase
    private lateinit var history: HistoryRepository
    private val context
        get() = ApplicationProvider.getApplicationContext<Context>()

    private fun fixture() =
        BackupCodec.parse(
            requireNotNull(javaClass.classLoader!!.getResource("training-backup.json")).readText()
        )

    private fun session() = newSession(100000, 10, 1, ZoneId.of("UTC"))

    private suspend fun imported() {
        val b = BackupRepository(db)
        b.confirm(b.preview(fixture()))
    }

    private suspend fun original() = requireNotNull(history.read(JsonPrimitive(0)))

    private fun changed(d: HistoryDraft, name: String = "Reviewed workout") =
        d.copy(values = d.values.copy(type = name))

    private suspend fun rejects(action: suspend () -> Unit) {
        try {
            action()
            fail("Expected rejection")
        } catch (_: IllegalStateException) {} catch (_: IllegalArgumentException) {}
    }

    @Before
    fun open() {
        context.deleteDatabase("history-test.db")
        db = Room.databaseBuilder(context, TrackerDatabase::class.java, "history-test.db").build()
        history = HistoryRepository(db)
    }

    @After
    fun close() {
        db.close()
        context.deleteDatabase("history-test.db")
    }

    @Test
    fun noOpPreservesExactPayloadAndNullableDurationWhileAnotherFieldChanges() = runBlocking {
        imported()
        val dao = db.trackerDao()
        val before = dao.records()
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_history BEFORE UPDATE ON records BEGIN SELECT RAISE(ABORT, 'forced failure'); END"
        )
        history.save(original())
        assertEquals(before, dao.records())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_history")
        val row = dao.record("workout", "number:0")!!
        val source = trackerJson.parseToJsonElement(row.payload).jsonObject
        dao.updateRecord(
            "workout",
            row.idKey,
            row.revision,
            JsonObject(source + ("durationMinutes" to JsonNull)).toString(),
        )
        history.save(changed(original()))
        assertEquals(
            JsonNull,
            trackerJson
                .parseToJsonElement(dao.record("workout", row.idKey)!!.payload)
                .jsonObject["durationMinutes"],
        )
        assertEquals(
            before.single { it.kind == "workout" && it.idKey == "string:0" },
            dao.record("workout", "string:0"),
        )
    }

    @Test
    fun realExportRetainsImportedFieldsAndOtherRecordsAndReopens() = runBlocking {
        imported()
        val tracker = TrackerRepository(db)
        tracker.start(session())
        val dao = db.trackerDao()
        val records = dao.records().filterNot { it.kind == "workout" && it.idKey == "number:0" }
        val documents = dao.documents()
        val active = dao.draft()
        val base = original()
        val edited =
            base.copy(
                values =
                    base.values.copy(
                        type = "Reviewed workout",
                        notes = "History reviewed",
                        duration = "",
                        tags = base.values.tags + "Good session",
                        exercises =
                            base.values.exercises.map {
                                it.copy(
                                    sets =
                                        it.sets.mapIndexed { i, s ->
                                            if (i == 0) s.copy(reps = "6") else s
                                        }
                                )
                            },
                    )
            )
        val saved = history.save(edited)
        assertFalse(saved.changed)
        assertEquals(
            records,
            dao.records().filterNot { it.kind == "workout" && it.idKey == "number:0" },
        )
        assertEquals(documents, dao.documents())
        assertEquals(active, dao.draft())
        val encoded = BackupRepository(db).export("2026-10-22T12:00:00Z")
        val actual = BackupCodec.parse(encoded).workouts.single { it["id"] == JsonPrimitive(0) }
        assertEquals(20.0, workoutMetrics(actual).duration, 0.0)
        assertEquals(fixture().workouts.first()["extension"], actual["extension"])
        File("build/outputs/compatibility").mkdirs()
        File("build/outputs/compatibility/history-export.json").writeText(encoded)
        db.close()
        db = Room.databaseBuilder(context, TrackerDatabase::class.java, "history-test.db").build()
        history = HistoryRepository(db)
        assertEquals(saved, original())
        assertEquals(active, db.trackerDao().draft())
    }

    @Test
    fun reorderedDuplicateRowsKeepTheirOwnNestedExtensionsAndRemovedRowsStayRemoved() =
        runBlocking {
            imported()
            val base = original()
            val dao = db.trackerDao()
            val row = dao.record("workout", "number:0")!!
            val source = trackerJson.parseToJsonElement(row.payload).jsonObject
            val e = source.getValue("exercises").jsonArray.first().jsonObject
            val other = JsonObject(e + ("extension" to JsonPrimitive("second duplicate")))
            val payload =
                JsonObject(
                    source +
                        mapOf(
                            "exercises" to JsonArray(listOf(e, other)),
                            "originRoutineId" to JsonPrimitive(0),
                        )
                )
            dao.updateRecord("workout", row.idKey, row.revision, payload.toString())
            val d = original()
            val first = d.values.exercises[0]
            val second = d.values.exercises[1]
            val added =
                HistoryExercise(
                    name = "New movement",
                    notes = "new note",
                    sets = listOf(HistorySet(weight = "0", reps = "0", rpe = "0")),
                )
            val result =
                history.save(
                    d.copy(
                        values =
                            d.values.copy(
                                exercises =
                                    listOf(
                                        second.copy(sets = second.sets.take(1)),
                                        first.copy(sets = first.sets.reversed()),
                                        added,
                                    )
                            )
                    )
                )
            val w =
                trackerJson
                    .parseToJsonElement(dao.record("workout", "number:0")!!.payload)
                    .jsonObject
            val exercises = w.getValue("exercises").jsonArray
            assertEquals(JsonPrimitive("second duplicate"), exercises[0].jsonObject["extension"])
            assertEquals(e["extension"], exercises[1].jsonObject["extension"])
            assertEquals(
                e.getValue("sets").jsonArray.reversed(),
                exercises[1].jsonObject.getValue("sets").jsonArray.toList(),
            )
            assertEquals(1, exercises[0].jsonObject.getValue("sets").jsonArray.size)
            assertEquals(JsonPrimitive(0), w["originRoutineId"])
            assertEquals(source["createdAt"], w["createdAt"])
            assertEquals(3, result.values.exercises.size)
            rejects { history.save(base) }
        }

    @Test
    fun competingSavesAndStaleDeleteImportAndAbaAreFenced() = runBlocking {
        imported()
        val base = original()
        val results =
            listOf("One", "Two")
                .map { name ->
                    async(Dispatchers.IO) { runCatching { history.save(changed(base, name)) } }
                }
                .awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        rejects { history.delete(base) }
        val beforeImport = original()
        val b = BackupRepository(db)
        b.confirm(b.preview(fixture()))
        rejects { history.save(changed(beforeImport)) }
        rejects { history.delete(beforeImport) }
        val old = original()
        history.delete(old)
        val changedRow =
            JsonObject(fixture().workouts.first() + ("notes" to JsonPrimitive("reinserted")))
        db.trackerDao()
            .insertRecord(RecordEntity("workout", "number:0", changedRow.toString(), old.revision))
        rejects { history.delete(old) }
        rejects { history.save(changed(old)) }
    }

    @Test
    fun failedSaveAndDeleteRollBackAndCanBeRetried() = runBlocking {
        imported()
        val dao = db.trackerDao()
        val before = dao.records()
        val base = original()
        for (operation in listOf("UPDATE", "DELETE")) {
            db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_history BEFORE $operation ON records BEGIN SELECT RAISE(ABORT, 'forced failure'); END"
            )
            try {
                if (operation == "UPDATE") history.save(changed(base)) else history.delete(base)
                fail("Forced failure")
            } catch (_: android.database.sqlite.SQLiteException) {}
            assertEquals(before, dao.records())
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_history")
        }
        val saved = history.save(changed(base))
        history.delete(saved)
        assertNull(history.read(JsonPrimitive(0)))
        assertNotNull(history.read(JsonPrimitive("0")))
    }

    @Test
    fun canonicalValidationRejectsBadDatesNumbersTagsAndRowKeysWithoutWriting() = runBlocking {
        imported()
        val base = original()
        val v = base.values
        val changes =
            listOf(
                v.copy(date = "2026-02-30"),
                v.copy(startTime = "25:00"),
                v.copy(duration = "NaN"),
                v.copy(notes = "x".repeat(4001)),
                v.copy(tags = List(33) { "tag" }),
                v.copy(tags = listOf("\u0000")),
                v.copy(exercises = List(101) { HistoryExercise(name = "X") }),
                v.copy(exercises = listOf(v.exercises.first(), v.exercises.first())),
            )
        changes.forEach { rejects { history.save(base.copy(values = it)) } }
        for (set in
            listOf(
                HistorySet(weight = "1e3"),
                HistorySet(weight = "1.001"),
                HistorySet(reps = "1.5"),
                HistorySet(rpe = "8.1"),
                HistorySet(rpe = "11"),
            )) {
            rejects {
                history.save(
                    base.copy(
                        values =
                            v.copy(
                                exercises = listOf(HistoryExercise(name = "X", sets = listOf(set)))
                            )
                    )
                )
            }
        }
        assertEquals(base, original())
    }

    @Test
    fun boundedSavedStateExcludesRawExtensionsAndRoundTripsEdits() = runBlocking {
        imported()
        val row = db.trackerDao().record("workout", "number:0")!!
        val payload = trackerJson.parseToJsonElement(row.payload).jsonObject
        db.trackerDao()
            .updateRecord(
                "workout",
                row.idKey,
                row.revision,
                JsonObject(payload + ("largeExtension" to JsonPrimitive("x".repeat(600000))))
                    .toString(),
            )
        val d = changed(original())
        val snapshot = requireNotNull(d.restorationState())
        assertTrue(snapshot.length < 10000)
        assertFalse(snapshot.contains("largeExtension"))
        assertEquals(d, trackerJson.decodeFromString<HistoryDraft>(snapshot))
        assertNull(d.copy(values = d.values.copy(notes = "x".repeat(200000))).restorationState())
        val restored = trackerJson.decodeFromString<HistoryDraft>(snapshot)
        history.save(restored)
        val b = BackupRepository(db)
        val preview = b.preview(fixture())
        history.delete(original())
        rejects { b.confirm(preview) }
    }

    @Test
    fun completionTagsAreDurableAndOldDraftsStillDecodeAndFinishOnce() = runBlocking {
        val old = trackerJson.encodeToString(session())
        assertEquals(emptyList<String>(), trackerJson.decodeFromString<SessionDraft>(old).tags)
        val draft =
            session()
                .copy(
                    tags = listOf("Good session", "Felt strong"),
                    exercises =
                        listOf(
                            Exercise(
                                name = "Press",
                                sets =
                                    listOf(
                                        WorkingSet(weight = "30", reps = "10", done = true),
                                        WorkingSet(weight = "10", reps = "5", warmup = true),
                                    ),
                            )
                        ),
                )
        val t = TrackerRepository(db)
        t.start(draft)
        assertEquals(draft.tags, t.readDraft()!!.first.tags)
        val w = workoutPayload(draft, 200000, 10, ZoneId.of("UTC"))
        t.finish(draft, 1, w)
        rejects { t.finish(draft, 1, w) }
        assertEquals(1, db.trackerDao().recordsOfKind("workout").size)
        assertEquals(JsonArray(draft.tags.map(::JsonPrimitive)), w["tags"])
        assertEquals(300.0, workoutMetrics(w).volume, 0.0)
        assertEquals(1, workoutMetrics(w).workingSets)
        BackupCodec.parse(BackupRepository(db).export("2026-10-22T12:00:00Z"))
        Unit
    }
}
