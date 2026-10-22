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
class StarterLibraryTest {
    private lateinit var db: TrackerDatabase
    private lateinit var repository: RoutineRepository
    private val context
        get() = ApplicationProvider.getApplicationContext<Context>()

    private fun asset(name: String) =
        requireNotNull(javaClass.classLoader!!.getResource(name)).readText()

    private fun catalog() = ExerciseCatalog.parse(asset("exercise-catalog.json"))

    private fun starters() = StarterLibrary.parse(asset("starter-routines.json"), catalog())

    @Before
    fun open() {
        context.deleteDatabase("starter-test.db")
        db = Room.databaseBuilder(context, TrackerDatabase::class.java, "starter-test.db").build()
        repository = RoutineRepository(db)
    }

    @After
    fun close() {
        db.close()
        context.deleteDatabase("starter-test.db")
    }

    private suspend fun rejects(action: suspend () -> Unit) {
        try {
            action()
            fail("Expected rejection")
        } catch (_: IllegalStateException) {} catch (_: IllegalArgumentException) {}
    }

    @Test
    fun startersRequireExactAvailableCatalogRecordsAndRevision() = runBlocking {
        val library = starters()
        assertEquals(
            listOf(
                "Chest / Triceps",
                "Back / Biceps",
                "Legs",
                "Shoulders / Traps",
                "Soccer / Conditioning",
                "Custom",
            ),
            library.routines.map { it.name },
        )
        assertTrue(library.routines.take(5).all { it.exercises.size == 4 })
        assertTrue(library.routines.last().exercises.isEmpty())
        val catalog = catalog()
        rejects {
            StarterLibrary.parse(
                asset("starter-routines.json"),
                ExerciseCatalog(catalog.exercises, "different"),
            )
        }
        rejects {
            StarterLibrary.parse(
                asset("starter-routines.json"),
                ExerciseCatalog(emptyList(), catalog.sourceRevision),
            )
        }
        rejects {
            StarterLibrary.parse(
                asset("starter-routines.json").replace("Cable Crossover", "Renamed exercise"),
                catalog,
            )
        }
        assertTrue(db.trackerDao().records().isEmpty())
    }

    @Test
    fun explicitSeedKeepsHistoryDocumentsAndActiveWorkoutAndExportsCompatibleNames() = runBlocking {
        val backups = BackupRepository(db)
        val fixture = BackupCodec.parse(asset("training-backup.json"))
        // Retain all non-routine fixture data while leaving the library intentionally empty.
        backups.confirm(backups.preview(fixture.copy(routines = emptyList())))
        val tracker = TrackerRepository(db)
        val active = newSession(100000, 10, 1, ZoneId.of("UTC"))
        tracker.start(active)
        val beforeRecords = db.trackerDao().records()
        val beforeDocuments = db.trackerDao().documents()
        val beforeDraft = db.trackerDao().draft()
        val library = starters()
        assertEquals(6, repository.seedStarters(library, "2026-10-22T12:00:00Z"))
        assertEquals(beforeRecords, db.trackerDao().records().filter { it.kind != "routine" })
        assertEquals(beforeDocuments, db.trackerDao().documents())
        assertEquals(beforeDraft, db.trackerDao().draft())
        val stored = db.trackerDao().recordsOfKind("routine")
        stored.forEach { record ->
            val routine = routineSnapshot(record)
            val expected = library.routines.single { JsonPrimitive(it.id) == routine.id }
            assertEquals(expected.exercises, routine.exercises)
            assertEquals(
                setOf("id", "name", "exercises", "createdAt", "updatedAt"),
                trackerJson.parseToJsonElement(record.payload).jsonObject.keys,
            )
        }
        val output = File("build/outputs/compatibility/starter-export.json")
        output.parentFile.mkdirs()
        output.writeText(backups.export("2026-10-22T12:00:00Z"))
        db.close()
        db = Room.databaseBuilder(context, TrackerDatabase::class.java, "starter-test.db").build()
        assertEquals(stored, db.trackerDao().recordsOfKind("routine"))
        assertEquals(beforeDraft, db.trackerDao().draft())
    }

    @Test
    fun anyExistingLibraryIncludingImportedAndEditedStartersIsNeverReplaced() = runBlocking {
        val backups = BackupRepository(db)
        backups.confirm(backups.preview(BackupCodec.parse(asset("training-backup.json"))))
        val imported = db.trackerDao().records()
        rejects { repository.seedStarters(starters()) }
        assertEquals(imported, db.trackerDao().records())
        val saved = requireNotNull(repository.read(JsonPrimitive(0)))
        repository.delete(saved)
        val previewBeforeSeed = backups.preview(BackupCodec.parse(asset("training-backup.json")))
        repository.seedStarters(starters())
        rejects { backups.confirm(previewBeforeSeed) }
        val custom = requireNotNull(repository.read(JsonPrimitive("starter:v1:Custom")))
        repository.save(RoutineDraft.edit(custom).copy(name = "My own routine"))
        val edited = db.trackerDao().records()
        rejects { repository.seedStarters(starters()) }
        assertEquals(edited, db.trackerDao().records())
    }

    @Test
    fun partialInsertionRollsBackAndCanBeRetried() = runBlocking {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_seed BEFORE INSERT ON records WHEN NEW.idKey = 'string:starter:v1:Legs' BEGIN SELECT RAISE(ABORT, 'forced seed failure'); END"
        )
        try {
            repository.seedStarters(starters())
            fail("Forced seed failure")
        } catch (_: android.database.sqlite.SQLiteException) {}
        assertTrue(db.trackerDao().records().isEmpty())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_seed")
        assertEquals(6, repository.seedStarters(starters()))
    }

    @Test
    fun concurrentSeedAttemptsCommitExactlyOneLibrary() = runBlocking {
        val library = starters()
        val results =
            List(2) { async(Dispatchers.IO) { runCatching { repository.seedStarters(library) } } }
                .awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(6, db.trackerDao().recordsOfKind("routine").size)
    }
}
