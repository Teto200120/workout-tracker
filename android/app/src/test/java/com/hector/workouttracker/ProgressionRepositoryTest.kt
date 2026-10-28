package com.hector.workouttracker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hector.workouttracker.core.backup.*
import com.hector.workouttracker.core.database.*
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.data.*
import com.hector.workouttracker.domain.*
import java.io.File
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ProgressionRepositoryTest {
    private lateinit var db: TrackerDatabase
    private lateinit var repo: ProgressionRepository
    private val context
        get() = ApplicationProvider.getApplicationContext<Context>()

    private fun asset(name: String) =
        requireNotNull(javaClass.classLoader!!.getResource(name)).readText()

    private fun fixture() = BackupCodec.parse(asset("training-backup.json"))

    private suspend fun imported() {
        val backup = BackupRepository(db)
        backup.confirm(backup.preview(fixture()))
    }

    private fun session() =
        newSession(Instant.parse("2026-10-23T12:00:00Z").toEpochMilli(), 10, 1, ZoneId.of("UTC"))
            .copy(
                notes = "Keep session notes",
                tags = listOf("Good session"),
                exercises =
                    listOf(
                        Exercise(name = "bench press", notes = "Keep exercise note"),
                        Exercise(name = "Another exercise", sets = listOf(WorkingSet(reps = "8"))),
                    ),
            )

    @Before
    fun open() {
        context.deleteDatabase("progression-test.db")
        db =
            Room.databaseBuilder(context, TrackerDatabase::class.java, "progression-test.db")
                .build()
        repo = ProgressionRepository(db)
    }

    @After
    fun close() {
        db.close()
        context.deleteDatabase("progression-test.db")
    }

    private suspend fun rejects(action: suspend () -> Unit) {
        try {
            action()
            fail("Expected rejection")
        } catch (_: IllegalStateException) {} catch (_: IllegalArgumentException) {}
    }

    private suspend fun request(draft: SessionDraft, target: Boolean = false): SetCopyRequest =
        SetCopyRequest(draft, draft.exercises.first(), repo.read().basis, target)

    @Test
    fun statsAndGuidanceShareTheSameCoherentHistoryWithoutChangingGuidance() = runBlocking {
        imported()
        val shared = WorkoutHistoryCache()
        StatsRepository(db, shared).read(LocalDate.parse("2026-10-23"))
        val actual = ProgressionRepository(db, shared).read()
        val expected = repo.read()
        assertEquals(expected, actual)
        assertSame(shared.read(db.trackerDao().recordsOfKind("workout")), actual.workouts)
        assertEquals(
            expected.guidance(listOf("Bench Press", "Squat")),
            actual.guidance(listOf("Bench Press", "Squat")),
        )
    }

    @Test
    fun batchGuidanceAndRoutineCopiesMatchIndependentRulesWithTypedIdsAndBlankRows() {
        val fixture = fixture()
        val names = listOf("Bench Press", " bench press ", "Squat", "Missing lift", "Bench Press")
        val settings = requireNotNull(fixture.settings)
        val snapshot = TrainingSnapshot("test", fixture.workouts, settings)
        assertEquals(
            names.distinct().associateWith { trainingGuidance(it, fixture.workouts, settings) },
            snapshot.guidance(names),
        )
        val records =
            fixture.workouts.map {
                RecordEntity("workout", typedIdKey(it.getValue("id")), it.toString())
            }
        val before = records.toList()
        val index = RoutineHistoryIndex(records)
        val copies = index.lastSets(names)
        for (name in names) assertEquals(
            trainingGuidance(name, fixture.workouts, settings).last?.sets.orEmpty(),
            copies[trainingName(name)].orEmpty(),
        )
        assertEquals(before, records)
        assertTrue(index.lastSets(emptyList()).isEmpty())
    }

    @Test
    fun goldenTargetsMatchAllActualPwaCasesWithoutMutatingInputs() {
        val cases =
            trackerJson
                .parseToJsonElement(asset("training-rules.json"))
                .jsonObject
                .getValue("cases")
                .jsonArray
        assertEquals(25, cases.size)
        for (row in cases) {
            val value = row.jsonObject
            val original = row.toString()
            val name = value.getValue("name").jsonPrimitive.content
            val settings = value.getValue("settings").jsonObject
            val sets = value.getValue("sets").jsonArray.map { it.jsonObject }
            val expectedProfile = value.getValue("profile").jsonObject
            val profile = trainingProfile(name, settings)
            assertEquals(expectedProfile.getValue("min").jsonPrimitive.int, profile.min)
            assertEquals(expectedProfile.getValue("max").jsonPrimitive.int, profile.max)
            assertEquals(expectedProfile.getValue("type").jsonPrimitive.content, profile.type)
            assertEquals(
                expectedProfile.getValue("increment").jsonPrimitive.double,
                profile.increment,
                0.0,
            )
            assertEquals(
                value.getValue("label").jsonPrimitive.content,
                value.getValue("target"),
                trackerJson.encodeToJsonElement(trainingTarget(name, sets, settings)),
            )
            assertEquals(original, row.toString())
        }
    }

    @Test
    fun latestFirstDuplicateCaseMatchingTypedIdTiesAndAliasesFollowThePwa() {
        val original = fixture().workouts.first()
        fun workout(
            id: JsonElement,
            name: String,
            reps: String,
            date: String = "2026-10-20",
        ): JsonObject {
            val set = buildJsonObject {
                put("weight", "100")
                put("reps", reps)
                put("rpe", "")
                put("warmup", false)
                put("done", false)
            }
            val exercise = buildJsonObject {
                put("name", name)
                put("notes", "")
                put("sets", JsonArray(listOf(set)))
            }
            return JsonObject(
                original +
                    mapOf(
                        "id" to id,
                        "date" to JsonPrimitive(date),
                        "exercises" to JsonArray(listOf(exercise)),
                    )
            )
        }
        val a = workout(JsonPrimitive(2), " Bench Press ", "8")
        val b = workout(JsonPrimitive("0"), "bench press", "10")
        val guide = trainingGuidance("BENCH PRESS", listOf(b, a), BackupCodec.defaultSettings)
        assertEquals(JsonPrimitive(2), guide.last!!.id)
        assertEquals(10.0, guide.pr!!.reps, 0.0)
        assertNull(
            trainingGuidance("Flat Bench Press", listOf(a, b), BackupCodec.defaultSettings).last
        )
        val empty =
            JsonObject(
                a +
                    mapOf(
                        "id" to JsonPrimitive("newest"),
                        "date" to JsonPrimitive("2026-10-21"),
                        "exercises" to
                            JsonArray(
                                listOf(
                                    buildJsonObject {
                                        put("name", "bench press")
                                        put("sets", JsonArray(emptyList()))
                                    },
                                    b.getValue("exercises").jsonArray.first(),
                                )
                            ),
                    )
            )
        val latest =
            trainingGuidance("bench press", listOf(a, b, empty), BackupCodec.defaultSettings)
        assertTrue(latest.last!!.sets.isEmpty())
        assertTrue(latest.target.targetSets.isEmpty())
        assertNotNull(latest.pr)
    }

    @Test
    fun explicitLastAndTargetCopiesRetainOtherStateAndExportAfterReopen() = runBlocking {
        imported()
        val tracker = TrackerRepository(db)
        val draft = session()
        tracker.start(draft)
        val records = db.trackerDao().records()
        val docs = db.trackerDao().documents()
        val last = repo.apply(request(draft), 1)
        val rows = last.exercises.first().sets
        assertEquals(3, rows.size)
        assertTrue(rows[1].warmup)
        assertEquals("8", rows[0].rpe)
        assertTrue(rows.none { it.done })
        assertEquals(3, rows.map { it.key }.distinct().size)
        assertEquals(draft.exercises[1], last.exercises[1])
        assertEquals(draft.notes, last.notes)
        assertEquals(draft.tags, last.tags)
        assertEquals(draft.exercises[0].notes, last.exercises[0].notes)
        val target = repo.apply(request(last, true), 2)
        assertEquals(listOf("6"), target.exercises.first().sets.map { it.reps })
        assertTrue(target.exercises.first().sets.none { it.warmup || it.done })
        assertEquals(records, db.trackerDao().records())
        assertEquals(docs, db.trackerDao().documents())
        val oldKeys = rows.map { it.key }
        assertTrue(target.exercises.first().sets.none { it.key in oldKeys })
        db.close()
        db =
            Room.databaseBuilder(context, TrackerDatabase::class.java, "progression-test.db")
                .build()
        assertEquals(target, TrackerRepository(db).readDraft()!!.first)
        val payload =
            workoutPayload(
                target,
                Instant.parse("2026-10-23T12:30:00Z").toEpochMilli(),
                30,
                ZoneId.of("UTC"),
            )
        TrackerRepository(db).finish(target, 3, payload)
        val out = File("build/outputs/compatibility/progression-export.json")
        requireNotNull(out.parentFile).mkdirs()
        out.writeText(BackupRepository(db).export("2026-10-23T13:00:00Z"))
    }

    @Test
    fun staleHistoryPreferencesAndWholeDraftEditsCannotReplaceVisibleValues() = runBlocking {
        imported()
        val tracker = TrackerRepository(db)
        val draft = session()
        tracker.start(draft)
        val stale = request(draft)
        val pref = PreferencesRepository(db)
        val base = pref.read()
        pref.save(
            base.copy(
                values =
                    base.values.copy(numbers = base.values.numbers + ("defaultWeightJump" to "2.5"))
            )
        )
        rejects { repo.apply(stale, 1) }
        assertEquals(draft, tracker.readDraft()!!.first)
        val beforeHistory = request(draft)
        val history = HistoryRepository(db)
        val saved = history.read(JsonPrimitive(0))!!
        history.save(saved.copy(values = saved.values.copy(notes = "Changed history")))
        rejects { repo.apply(beforeHistory, 1) }
        val changed = draft.copy(notes = "Newer draft note")
        tracker.saveDraft(changed, 1)
        rejects { repo.apply(request(draft), 2) }
        assertEquals(changed, tracker.readDraft()!!.first)
        val current = request(changed)
        assertEquals("Newer draft note", repo.apply(current, 2).notes)
    }

    @Test
    fun copyFailureRollsBackAndRetriesAndRepeatOldRequestsAreRejected() = runBlocking {
        imported()
        val tracker = TrackerRepository(db)
        val draft = session()
        tracker.start(draft)
        val request = request(draft)
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_copy BEFORE UPDATE ON drafts BEGIN SELECT RAISE(ABORT, 'forced copy failure'); END"
        )
        try {
            repo.apply(request, 1)
            fail("Expected failure")
        } catch (_: android.database.sqlite.SQLiteException) {}
        assertEquals(draft, tracker.readDraft()!!.first)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_copy")
        val copied = repo.apply(request, 1)
        rejects { repo.apply(request, 2) }
        assertEquals(copied, tracker.readDraft()!!.first)
    }

    @Test
    fun routineAutoloadIsAtomicIndependentAndNeverReseedsExistingDrafts() = runBlocking {
        imported()
        val tracker = TrackerRepository(db)
        val routine = buildJsonObject {
            put("id", "autoload")
            put("name", "Autoload routine")
            put(
                "exercises",
                JsonArray(
                    listOf("Bench Press", "bench press", "Flat Bench Press").map(::JsonPrimitive)
                ),
            )
            put("createdAt", "2026-10-23T00:00:00Z")
            put("updatedAt", "2026-10-23T00:00:00Z")
        }
        val record = RecordEntity("routine", "string:autoload", routine.toString())
        db.trackerDao().insertRecord(record)
        val active = tracker.startRoutine(record, session().copy(exercises = emptyList()))
        assertEquals(3, active.exercises[0].sets.size)
        assertEquals(3, active.exercises[1].sets.size)
        assertTrue(active.exercises[2].sets.single().reps.isEmpty())
        assertEquals(7, active.exercises.flatMap { it.sets }.map { it.key }.distinct().size)
        val changed =
            active.copy(
                exercises = active.exercises.map { it.copy(sets = listOf(WorkingSet(reps = "3"))) }
            )
        tracker.saveDraft(changed, 1)
        assertEquals(changed, tracker.readDraft()!!.first)
        rejects { tracker.startRoutine(record, session()) }
        assertEquals(changed, tracker.readDraft()!!.first)
        tracker.discard(changed.id, 2)
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_start BEFORE INSERT ON drafts BEGIN SELECT RAISE(ABORT, 'forced start failure'); END"
        )
        try {
            tracker.startRoutine(record, session())
            fail("Expected failure")
        } catch (_: android.database.sqlite.SQLiteException) {}
        assertNull(tracker.readDraft())
        assertEquals(record, db.trackerDao().record("routine", record.idKey))
    }

    @Test
    fun liveGuidanceRespondsToHistoryAndPreferencesWithoutWritingAnything() = runBlocking {
        imported()
        val before = db.trackerDao().records()
        val docs = db.trackerDao().documents()
        val names = MutableStateFlow(listOf("Bench Press"))
        val initial = repo.observe(names).first()
        assertNotNull(initial.second["Bench Press"])
        assertEquals(before, db.trackerDao().records())
        assertEquals(docs, db.trackerDao().documents())
        val expected = repo.read().basis
        val observation =
            async(Dispatchers.IO) {
                withTimeout(10000) { repo.observe(names).first { it.first.basis != expected } }
            }
        val pref = PreferencesRepository(db)
        val base = pref.read()
        pref.save(base.copy(values = base.values.copy(trackRpe = false)))
        assertNotEquals(expected, observation.await().first.basis)
        names.value = listOf("Flat Bench Press")
        assertNull(repo.observe(names).first().second.getValue("Flat Bench Press").last)
    }

    @Test
    fun legacyRpeZeroSurvivesCopiesButNewZeroAndUnsafeTargetsAreRejected() = runBlocking {
        val zero = buildJsonObject {
            put("weight", "100")
            put("reps", "10")
            put("rpe", 0)
            put("warmup", false)
            put("done", true)
        }
        val copied = copiedTrainingSets(listOf(zero)).single()
        assertEquals("0", copied.rpe)
        assertTrue(copied.historicalRpeZero)
        assertFalse(copied.done)
        assertNull(setEntryError(copied))
        assertNotNull(setEntryError(copied.copy(historicalRpeZero = false)))
        val payload =
            workoutPayload(
                session()
                    .copy(
                        exercises = listOf(Exercise(name = "Bench Press", sets = listOf(copied)))
                    ),
                Instant.parse("2026-10-23T13:00:00Z").toEpochMilli(),
                60,
                ZoneId.of("UTC"),
            )
        assertFalse(payload.toString().contains("historicalRpeZero"))
        BackupCodec.parse(
            BackupCodec.encode(
                BackupData(
                    3,
                    2,
                    listOf(payload),
                    emptyList(),
                    emptyList(),
                    BackupCodec.defaultSettings,
                    null,
                    null,
                ),
                "2026-10-23T13:00:00Z",
            )
        )
        val excessive =
            trainingTarget(
                "Bench Press",
                listOf(JsonObject(zero + ("weight" to JsonPrimitive("10000")))),
                BackupCodec.defaultSettings,
            )
        rejects { copiedTrainingSets(excessive.targetSets) }
        assertFalse(
            SetCopyRequest(session(), session().exercises.first(), "basis", false).replacesInput
        )
        assertTrue(
            SetCopyRequest(
                    session(),
                    Exercise(name = "Bench", sets = listOf(WorkingSet(rpe = "8"))),
                    "basis",
                    false,
                )
                .replacesInput
        )
    }
}
