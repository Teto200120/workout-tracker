package com.hector.workouttracker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hector.workouttracker.core.backup.BackupCodec
import com.hector.workouttracker.core.database.*
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.core.schedule.*
import com.hector.workouttracker.data.*
import java.io.File
import java.time.*
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
class ScheduleRepositoryTest {
    private lateinit var db: TrackerDatabase
    private lateinit var schedule: ScheduleRepository
    private val context
        get() = ApplicationProvider.getApplicationContext<Context>()

    private fun asset(name: String) =
        requireNotNull(javaClass.classLoader!!.getResource(name)).readText()

    private fun fixture() = BackupCodec.parse(asset("training-backup.json"))

    private suspend fun imported() {
        val backups = BackupRepository(db)
        backups.confirm(backups.preview(fixture()))
    }

    private fun edited(base: ScheduleDraft, name: String = "Legs") =
        base.copy(days = List(7) { ScheduleDay("gym", name) })

    private fun session() =
        newSession(Instant.parse("2026-10-22T12:00:00Z").toEpochMilli(), 10, 1, ZoneId.of("UTC"))

    @Before
    fun open() {
        context.deleteDatabase("schedule-test.db")
        db = Room.databaseBuilder(context, TrackerDatabase::class.java, "schedule-test.db").build()
        schedule = ScheduleRepository(db)
    }

    @After
    fun close() {
        db.close()
        context.deleteDatabase("schedule-test.db")
    }

    private suspend fun rejects(action: suspend () -> Unit) {
        try {
            action()
            fail("Expected rejection")
        } catch (_: IllegalStateException) {} catch (_: IllegalArgumentException) {}
    }

    @Test
    fun localDatePlansMatchTheActualPwaResolver() {
        val cases =
            trackerJson
                .parseToJsonElement(asset("schedule.json"))
                .jsonObject
                .getValue("cases")
                .jsonArray
        cases.forEach { row ->
            val value = row.jsonObject
            val actual =
                resolveDayPlan(
                    LocalDate.parse(value.getValue("date").jsonPrimitive.content),
                    scheduleDays(value.getValue("settings").jsonObject),
                )
            val expected = value.getValue("expected").jsonObject
            assertEquals(
                value.getValue("name").jsonPrimitive.content,
                DayPlan(
                    expected.getValue("kind").jsonPrimitive.content,
                    expected.getValue("title").jsonPrimitive.content,
                    expected.getValue("routine").jsonPrimitive.content,
                    expected.getValue("note").jsonPrimitive.content,
                ),
                actual,
            )
        }
    }

    @Test
    fun editingPreservesEveryOtherFieldActiveWorkoutAndHistoryAndReopens() = runBlocking {
        val data = fixture()
        val original = requireNotNull(data.settings)
        val oldWeek = original.getValue("schedule").jsonObject
        val settings =
            JsonObject(
                original +
                    mapOf(
                        "extension" to buildJsonObject { put("keep", true) },
                        "schedule" to
                            JsonObject(
                                oldWeek +
                                    mapOf(
                                        "extension" to buildJsonObject { put("keep", 1) },
                                        "0" to
                                            JsonObject(
                                                oldWeek.getValue("0").jsonObject +
                                                    ("extension" to JsonPrimitive("day field"))
                                            ),
                                    )
                            ),
                    )
            )
        val backups = BackupRepository(db)
        backups.confirm(backups.preview(data.copy(settings = settings)))
        TrackerRepository(db).start(session())
        val records = db.trackerDao().records()
        val draft = db.trackerDao().draft()
        val documents = db.trackerDao().documents()
        val before = schedule.read()
        assertEquals(before, schedule.save(before))
        assertEquals(documents, db.trackerDao().documents())
        val saved = schedule.save(edited(before))
        val result =
            trackerJson
                .parseToJsonElement(requireNotNull(db.trackerDao().document("settings")).payload)
                .jsonObject
        for ((key, value) in settings) if (key != "schedule") assertEquals(key, value, result[key])
        val week = result.getValue("schedule").jsonObject
        assertEquals(
            settings.getValue("schedule").jsonObject.getValue("extension"),
            week.getValue("extension"),
        )
        assertEquals(
            JsonPrimitive("day field"),
            week.getValue("0").jsonObject.getValue("extension"),
        )
        assertEquals(records, db.trackerDao().records())
        assertEquals(draft, db.trackerDao().draft())
        assertEquals(
            documents.filter { it.name != "settings" },
            db.trackerDao().documents().filter { it.name != "settings" },
        )
        val output = File("build/outputs/compatibility/schedule-export.json")
        output.parentFile.mkdirs()
        output.writeText(backups.export("2026-10-22T12:00:00Z"))
        db.close()
        db = Room.databaseBuilder(context, TrackerDatabase::class.java, "schedule-test.db").build()
        assertEquals(saved, ScheduleRepository(db).read())
    }

    @Test
    fun competingSettingsImportsAndProfilesCannotBeOverwritten() = runBlocking {
        imported()
        val base = schedule.read()
        val results =
            listOf("Legs", "Custom")
                .map { name ->
                    async(Dispatchers.IO) { runCatching { schedule.save(edited(base, name)) } }
                }
                .awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        val tracker = TrackerRepository(db)
        val profile = tracker.readProfile()
        val stale = schedule.read()
        tracker.saveProfile(Profile("New name", !profile.first.trackRpe), profile.second)
        rejects { schedule.save(edited(stale)) }
        assertEquals("New name", tracker.readProfile().first.displayName)
        val backups = BackupRepository(db)
        val preview = backups.preview(fixture())
        val beforeSave = schedule.read()
        schedule.save(edited(beforeSave, "Updated plan"))
        rejects { backups.confirm(preview) }
        val beforeImport = schedule.read()
        backups.confirm(backups.preview(fixture()))
        rejects { schedule.save(edited(beforeImport)) }
    }

    @Test
    fun failedWritesRollBackAndNoopDoesNotTouchTheDocument() = runBlocking {
        imported()
        val before = db.trackerDao().documents()
        val base = schedule.read()
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_schedule BEFORE UPDATE ON documents WHEN NEW.name = 'settings' BEGIN SELECT RAISE(ABORT, 'forced schedule failure'); END"
        )
        assertEquals(base, schedule.save(base))
        try {
            schedule.save(edited(base))
            fail("Forced failure")
        } catch (_: android.database.sqlite.SQLiteException) {}
        assertEquals(before, db.trackerDao().documents())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_schedule")
        assertFalse(schedule.save(edited(base)).changed)
    }

    @Test
    fun missingSettingsUseLegacyProfileAndInvalidChangesLeaveDataUntouched() = runBlocking {
        db.trackerDao()
            .insertDocument(
                DocumentEntity(
                    "native_profile",
                    trackerJson.encodeToString(Profile("Legacy", false)),
                )
            )
        val base = schedule.read()
        schedule.save(base)
        assertNull(db.trackerDao().document("settings"))
        val saved = schedule.save(edited(base))
        val value =
            trackerJson
                .parseToJsonElement(requireNotNull(db.trackerDao().document("settings")).payload)
                .jsonObject
        assertEquals(JsonPrimitive("Legacy"), value.getValue("displayName"))
        assertEquals(JsonPrimitive(false), value.getValue("rpeAware"))
        val before = db.trackerDao().documents()
        rejects { schedule.save(saved.copy(days = saved.days.dropLast(1))) }
        rejects { schedule.save(saved.copy(days = List(7) { ScheduleDay("invalid", "Custom") })) }
        rejects { schedule.save(edited(saved, "x".repeat(81))) }
        assertEquals(before, db.trackerDao().documents())
    }

    @Test
    fun plannedLaunchFencesDatesPlansRoutineRevisionsAmbiguityAndActiveDrafts() = runBlocking {
        imported()
        val routines = RoutineRepository(db)
        val tracker = TrackerRepository(db)
        val day = ScheduleDay("gym", "Training fixture")
        val date = LocalDate.parse("2026-10-22")
        val selected = requireNotNull(routines.read(JsonPrimitive(0)))
        schedule.save(edited(schedule.read(), day.routine))
        rejects { tracker.startPlannedRoutine(selected.record, day, date.plusDays(1), session()) }
        val active = tracker.startPlannedRoutine(selected.record, day, date, session())
        assertEquals(JsonPrimitive(0), active.originRoutineId)
        assertEquals(selected.exercises, active.exercises.map { it.name })
        rejects { tracker.startPlannedRoutine(selected.record, day, date, session()) }
        tracker.discard(active.id, tracker.readDraft()!!.second)
        val base = schedule.read()
        schedule.save(base.copy(days = base.days.map { it.copy(kind = "rest") }))
        rejects { tracker.startPlannedRoutine(selected.record, day, date, session()) }
        schedule.save(edited(schedule.read(), day.routine))
        routines.save(
            RoutineDraft.edit(selected)
                .copy(exercises = selected.exercises + "Changed while selected")
        )
        rejects { tracker.startPlannedRoutine(selected.record, day, date, session()) }
        val data = fixture()
        val duplicate = JsonObject(data.routines.single() + ("id" to JsonPrimitive("duplicate")))
        val backups = BackupRepository(db)
        backups.confirm(backups.preview(data.copy(routines = data.routines + duplicate)))
        schedule.save(edited(schedule.read(), day.routine))
        rejects {
            tracker.startPlannedRoutine(
                routines.read(JsonPrimitive(0))!!.record,
                day,
                date,
                session(),
            )
        }
        assertNull(tracker.readDraft())
    }

    @Test
    fun boundedDraftsExcludeExtensionsAndKeepBlankAndLongCompatibilityNames() = runBlocking {
        val data = fixture()
        val source = data.settings!!
        val settings = JsonObject(source + ("extension" to JsonPrimitive("x".repeat(600000))))
        val backups = BackupRepository(db)
        backups.confirm(backups.preview(data.copy(settings = settings)))
        val base = schedule.read()
        assertTrue(base.days.all { it.routine.isEmpty() })
        val snapshot = requireNotNull(base.restorationState())
        assertTrue(snapshot.length < 3000)
        assertEquals(base, trackerJson.decodeFromString<ScheduleDraft>(snapshot))
        assertNull(edited(base, "x".repeat(200000)).restorationState())
    }
}
