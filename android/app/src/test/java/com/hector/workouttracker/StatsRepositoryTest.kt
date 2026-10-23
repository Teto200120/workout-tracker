package com.hector.workouttracker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hector.workouttracker.core.backup.BackupCodec
import com.hector.workouttracker.core.database.*
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.data.*
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class StatsRepositoryTest {
    private lateinit var db: TrackerDatabase
    private val context
        get() = ApplicationProvider.getApplicationContext<Context>()

    private val date = LocalDate.parse("2026-10-22")

    private fun fixture() =
        BackupCodec.parse(javaClass.getResource("/training-backup.json")!!.readText())

    private suspend fun imported() {
        val b = BackupRepository(db)
        b.confirm(b.preview(fixture()))
    }

    @Before
    fun open() {
        context.deleteDatabase("stats-test.db")
        db = Room.databaseBuilder(context, TrackerDatabase::class.java, "stats-test.db").build()
    }

    @After
    fun close() {
        db.close()
        context.deleteDatabase("stats-test.db")
    }

    @Test
    fun statsAreReadOnlyExcludeActiveDraftAndRetainDecimalGoalAcrossReopen() = runBlocking {
        imported()
        val dao = db.trackerDao()
        val goal = dao.document("goals")!!
        val value = trackerJson.parseToJsonElement(goal.payload).jsonObject
        dao.updateDocument(
            "goals",
            goal.revision,
            JsonObject(value + ("weeklyGoal" to JsonPrimitive(6.0))).toString(),
        )
        TrackerRepository(db).start(newSession(100000, 10, 1, ZoneId.of("UTC")))
        val records = dao.records()
        val documents = dao.documents()
        val active = dao.draft()
        val snapshot = StatsRepository(db).read(date)
        assertEquals(2, snapshot.totals.workouts)
        assertEquals(6, snapshot.goal)
        assertEquals(records, dao.records())
        assertEquals(documents, dao.documents())
        assertEquals(active, dao.draft())
        db.close()
        db = Room.databaseBuilder(context, TrackerDatabase::class.java, "stats-test.db").build()
        assertEquals(snapshot, StatsRepository(db).read(date))
    }

    @Test
    fun observerRecomputesAfterEditsDeletesImportsPreferencesAndLocalDateChanges() = runBlocking {
        imported()
        val dates = MutableStateFlow(date)
        val outputs = Channel<StatsSnapshot>(Channel.UNLIMITED)
        val job =
            launch(Dispatchers.Default) {
                StatsRepository(db).observe(dates).collect { outputs.send(it) }
            }
        suspend fun await(predicate: (StatsSnapshot) -> Boolean): StatsSnapshot =
            withTimeout(10000) {
                var result = outputs.receive()
                while (!predicate(result)) result = outputs.receive()
                result
            }
        try {
            await { it.totals.workouts == 2 }
            val history = HistoryRepository(db)
            val base = history.read(JsonPrimitive("0"))!!
            history.save(
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
            await { it.exercises.single { e -> e.name == "Bench Press" }.bestWeight == 150.0 }
            history.delete(history.read(JsonPrimitive(0))!!)
            await { it.totals.workouts == 1 }
            val dao = db.trackerDao()
            val goal = dao.document("goals")!!
            dao.updateDocument(
                "goals",
                goal.revision,
                JsonObject(
                        trackerJson.parseToJsonElement(goal.payload).jsonObject +
                            ("weeklyGoal" to JsonPrimitive(6))
                    )
                    .toString(),
            )
            val schedules = ScheduleRepository(db)
            val plan = schedules.read()
            schedules.save(plan.copy(days = plan.days.map { it.copy(kind = "gym") }))
            await { it.goal == 6 && it.weekly.gymDays == 7 }
            val b = BackupRepository(db)
            b.confirm(b.preview(fixture()))
            await {
                it.totals.workouts == 2 &&
                    it.weekly.gymDays == 0 &&
                    it.exercises.single { e -> e.name == "Bench Press" }.bestWeight == 100.0
            }
            dates.value = LocalDate.parse("2050-01-01")
            val future = await { it.today == "2050-01-01" }
            assertEquals(0, future.recent)
            assertEquals(0, future.streak)
        } finally {
            job.cancelAndJoin()
            outputs.close()
        }
    }

    @Test
    fun finishSummaryOnlyReturnsAfterAtomicCommitAndCanRetryAfterFailure() = runBlocking {
        imported()
        val dao = db.trackerDao()
        val documents = dao.documents()
        val t = TrackerRepository(db)
        val draft =
            newSession(100000, 10, 1, ZoneId.of("UTC"))
                .copy(
                    type = "Push",
                    exercises =
                        listOf(
                            Exercise(
                                name = "Bench Press",
                                sets = listOf(WorkingSet(weight = "200", reps = "8")),
                            )
                        ),
                )
        t.start(draft)
        val payload = workoutPayload(draft, 110000, 10, ZoneId.of("UTC"))
        dao.draft()!!
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_finish BEFORE INSERT ON records BEGIN SELECT RAISE(ABORT, 'forced failure'); END"
        )
        try {
            t.finish(draft, 1, payload)
            fail("Expected rollback")
        } catch (_: android.database.sqlite.SQLiteException) {}
        assertEquals(2, dao.recordsOfKind("workout").size)
        assertNotNull(dao.draft())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_finish")
        val summary = t.finish(draft, 1, payload)
        assertTrue(summary.highlights.any { it.type == "PR" && it.text.contains("200 × 8") })
        assertEquals(3, dao.recordsOfKind("workout").size)
        assertNull(dao.draft())
        try {
            t.finish(draft, 1, payload)
            fail("Duplicate finish")
        } catch (_: WriteConflict) {}
        assertEquals(documents, dao.documents())
    }

    @Test
    fun sameDateAndTimestampTiesUseNumericThenStringIdOrder() = runBlocking {
        val base = fixture().workouts.first()
        val e = base.getValue("exercises").jsonArray.first().jsonObject
        for ((id, reps) in
            listOf(JsonPrimitive(10) to "2", JsonPrimitive("0") to "3", JsonPrimitive(2) to "1")) {
            val set =
                JsonObject(
                    e.getValue("sets").jsonArray.first().jsonObject +
                        mapOf("weight" to JsonPrimitive("200"), "reps" to JsonPrimitive(reps))
                )
            val exercise = JsonObject(e + ("sets" to JsonArray(listOf(set))))
            val w = JsonObject(base + mapOf("id" to id, "exercises" to JsonArray(listOf(exercise))))
            db.trackerDao().insertRecord(RecordEntity("workout", typedIdKey(id), w.toString()))
        }
        val record = StatsRepository(db).read(date).exercises.single()
        assertEquals(1.0, record.records.bestWeight!!.reps, 0.0)
        assertEquals(3.0, record.records.estimated1rm!!.reps, 0.0)
    }
}
