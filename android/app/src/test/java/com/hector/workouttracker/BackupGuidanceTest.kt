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
import java.io.IOException
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
class BackupGuidanceTest {
    private lateinit var db: TrackerDatabase
    private lateinit var backups: BackupRepository
    private lateinit var help: GuidanceRepository
    private val at = "2026-10-23T18:00:00Z"
    private val context
        get() = ApplicationProvider.getApplicationContext<Context>()

    private fun asset(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    private fun data() = BackupCodec.parse(asset("training-backup.json"))

    private suspend fun imported() {
        backups.confirm(backups.preview(data()))
    }

    private fun reopen() {
        db =
            Room.databaseBuilder(context, TrackerDatabase::class.java, "backup-guidance.db").build()
        backups = BackupRepository(db)
        help = GuidanceRepository(db)
    }

    @Before
    fun open() {
        context.deleteDatabase("backup-guidance.db")
        reopen()
    }

    @After
    fun close() {
        db.close()
        context.deleteDatabase("backup-guidance.db")
    }

    @Test
    fun elapsedBackupAgeMatchesActualPwaIncludingDstFutureAndBoundaries() {
        val fixture = trackerJson.parseToJsonElement(asset("backup-status.json")).jsonObject
        val zone = ZoneId.of(fixture.getValue("zone").jsonPrimitive.content)
        val now = Instant.parse(fixture.getValue("now").jsonPrimitive.content)
        for (case in fixture.getValue("cases").jsonArray) {
            val row = case.jsonObject
            val stamp = row["lastExportedAt"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
            val expected = row["days"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.long
            assertEquals(row.toString(), expected, backupDaysSince(stamp, now, zone))
            assertTrue(
                backupStatus(BackupSnapshot(true, stamp), now, zone)
                    .detail
                    .startsWith(row.getValue("text").jsonPrimitive.content)
            )
        }
        fun status(days: Long) =
            backupStatus(BackupSnapshot(true, now.minusSeconds(days * 86400).toString()), now, zone)
                .label
        assertEquals("Current", status(13))
        assertEquals("Backup soon", status(14))
        assertEquals("Backup soon", status(29))
        assertEquals("Backup now", status(30))
        assertEquals("No data", backupStatus(BackupSnapshot(false, null), now, zone).label)
        assertEquals("Backup", backupStatus(BackupSnapshot(true, null), now, zone).label)
    }

    @Test
    fun successfulClosedExportRetainsMetadataExtensionsAndOtherDataAndSurvivesReopen() =
        runBlocking {
            imported()
            val dao = db.trackerDao()
            val meta = dao.document("backup_meta")
            val extended =
                JsonObject(
                    (meta?.let { trackerJson.parseToJsonElement(it.payload).jsonObject }
                        ?: JsonObject(emptyMap())) +
                        ("extension" to buildJsonObject { put("retained", true) })
                )
            if (meta == null) dao.insertDocument(DocumentEntity("backup_meta", extended.toString()))
            else dao.updateDocument(meta.name, meta.revision, extended.toString())
            val draft = newSession(100000, 10, 1, ZoneOffset.UTC)
            TrackerRepository(db).start(draft)
            val records = dao.records()
            val docs = dao.documents().filter { it.name != "backup_meta" }
            var closed = false
            assertTrue(
                backups.writeExport(at) { text ->
                    assertFalse(closed)
                    assertEquals(data().workouts, BackupCodec.parse(text).workouts)
                    assertEquals(
                        extended,
                        trackerJson.parseToJsonElement(dao.document("backup_meta")!!.payload),
                    )
                    closed = true
                }
            )
            assertTrue(closed)
            assertEquals(records, dao.records())
            assertEquals(docs, dao.documents().filter { it.name != "backup_meta" })
            assertEquals(draft, TrackerRepository(db).readDraft()!!.first)
            val saved =
                trackerJson.parseToJsonElement(dao.document("backup_meta")!!.payload).jsonObject
            assertEquals(JsonPrimitive(at), saved["lastExportedAt"])
            assertEquals(extended["extension"], saved["extension"])
            val version = dao.document("backup_meta")!!.revision
            assertTrue(backups.writeExport(at) {})
            assertEquals(version, dao.document("backup_meta")!!.revision)
            db.close()
            reopen()
            assertEquals(at, backups.status.first().lastExportedAt)
            assertEquals(draft, TrackerRepository(db).readDraft()!!.first)
        }

    @Test
    fun failedOrCancelledWriterNeverRefreshesReminderAndMetadataFailureDoesNotClaimFileFailure() =
        runBlocking {
            imported()
            val before = db.trackerDao().documents()
            for (cancel in listOf(false, true)) {
                try {
                    backups.writeExport(at) {
                        if (cancel) throw CancellationException("cancelled")
                        else throw IOException("close failed")
                    }
                    fail("Expected writer failure")
                } catch (_: CancellationException) {} catch (_: IOException) {}
                assertEquals(before, db.trackerDao().documents())
            }
            // Force whichever metadata mutation applies after import.
            for (op in listOf("INSERT", "UPDATE")) db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_meta_${op.lowercase()} BEFORE $op ON documents WHEN NEW.name = 'backup_meta' BEGIN SELECT RAISE(ABORT, 'forced metadata failure'); END"
            )
            var file: String? = null
            assertFalse(backups.writeExport(at) { file = it })
            assertNotNull(file)
            BackupCodec.parse(file!!)
            assertEquals(before, db.trackerDao().documents())
            for (op in listOf("insert", "update")) db.openHelper.writableDatabase.execSQL(
                "DROP TRIGGER fail_meta_$op"
            )
            assertTrue(backups.writeExport(at) {})
            assertEquals(at, backups.status.first().lastExportedAt)
        }

    @Test
    fun backupStatusCountsLegacyDataButNotRoutinesAndRefreshesLive() = runBlocking {
        assertFalse(backups.status.first().hasData)
        val source = data()
        val dao = db.trackerDao()
        val routine = source.routines.first()
        dao.insertRecord(
            RecordEntity("routine", typedIdKey(routine.getValue("id")), routine.toString())
        )
        assertFalse(backups.status.first().hasData)
        val observing =
            async(Dispatchers.IO) { withTimeout(10000) { backups.status.first { it.hasData } } }
        val weight = buildJsonObject {
            put("id", "legacy")
            put("date", "2026-10-23")
            put("weight", 150)
            put("notes", "Retained legacy weight")
            put("createdAt", "2026-10-23T00:00:00Z")
        }
        dao.insertRecord(
            RecordEntity("legacy-weight", typedIdKey(weight.getValue("id")), weight.toString())
        )
        assertTrue(observing.await().hasData)
        assertTrue(backups.writeExport(at) {})
        assertEquals(at, backups.status.first().lastExportedAt)
    }

    @Test
    fun guidanceRetainsFutureUnknownAndPartialStateWithoutMutatingInput() {
        val root =
            trackerJson
                .parseToJsonElement(
                    """{"extension":{"keep":true},"schemaVersion":1,"experiences":{"future":{"contentVersion":9,"status":"completed"},"homeTour":{"contentVersion":1,"status":"completed","extension":7},"rpeBasics":{"contentVersion":99,"status":"unseen"},"historyBasics":null}}"""
                )
                .jsonObject
        val original = root.toString()
        assertFalse(guidanceVisible(root, "homeTour"))
        assertFalse(guidanceVisible(root, "rpeBasics"))
        assertTrue(guidanceVisible(root, "historyBasics"))
        val reset = changeGuidance(root, guidanceTopics.map { it.id }, "unseen")
        assertTrue(guidanceVisible(reset, "homeTour"))
        assertFalse(guidanceVisible(reset, "rpeBasics"))
        assertEquals(
            root.getValue("experiences").jsonObject["future"],
            reset.getValue("experiences").jsonObject["future"],
        )
        assertEquals(
            JsonPrimitive(7),
            reset.getValue("experiences").jsonObject.getValue("homeTour").jsonObject["extension"],
        )
        assertEquals(root["extension"], reset["extension"])
        assertEquals(original, root.toString())
        val future = JsonObject(root + ("schemaVersion" to JsonPrimitive(2)))
        assertFalse(guidanceVisible(future, "homeTour"))
        try {
            changeGuidance(future, listOf("homeTour"), "completed")
            fail("future schema")
        } catch (_: IllegalStateException) {}
    }

    @Test
    fun guidanceReplayResetNoOpReopenAndImportKeepWorkoutStateAndStayOutOfBackup() = runBlocking {
        imported()
        val dao = db.trackerDao()
        val before = dao.records()
        val documents = dao.documents()
        val preview = backups.preview(data())
        help.change(listOf("homeTour"), "completed")
        val first = dao.document("native_guidance")!!
        help.change(listOf("homeTour"), "completed")
        assertEquals(first, dao.document("native_guidance"))
        // Education is not application data and does not invalidate an otherwise reviewed import.
        backups.confirm(preview)
        assertEquals(first, dao.document("native_guidance"))
        val draft = newSession(100000, 10, 1, ZoneOffset.UTC)
        TrackerRepository(db).start(draft)
        help.change(guidanceTopics.map { it.id }, "unseen")
        assertEquals(before, dao.records())
        assertEquals(documents, dao.documents().filter { it.name != "native_guidance" })
        assertEquals(draft, TrackerRepository(db).readDraft()!!.first)
        help.change(listOf("homeTour"), "completed")
        db.close()
        reopen()
        assertFalse(guidanceVisible(help.progress.first(), "homeTour"))
        val text = backups.export(at)
        assertFalse(text.contains("native_guidance"))
        assertFalse(text.contains("homeTour"))
        assertEquals(data().workouts, BackupCodec.parse(text).workouts)
        val out = File("build/outputs/compatibility/guidance-export.json")
        out.parentFile!!.mkdirs()
        out.writeText(text)
    }

    @Test
    fun failedGuidanceWritesRollbackAndRetryWithoutTouchingOtherState() = runBlocking {
        imported()
        help.change(listOf("homeTour"), "completed")
        val before = db.trackerDao().documents()
        val records = db.trackerDao().records()
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_help BEFORE UPDATE ON documents WHEN NEW.name = 'native_guidance' BEGIN SELECT RAISE(ABORT, 'forced guidance failure'); END"
        )
        try {
            help.change(guidanceTopics.map { it.id }, "unseen")
            fail("failure")
        } catch (_: android.database.sqlite.SQLiteException) {}
        assertEquals(before, db.trackerDao().documents())
        assertEquals(records, db.trackerDao().records())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_help")
        help.change(guidanceTopics.map { it.id }, "unseen")
        assertTrue(guidanceVisible(help.progress.first(), "homeTour"))
    }

    @Test
    fun successfulExportInvalidatesReviewedImportButFailedFileWriteDoesNot() = runBlocking {
        imported()
        val preview = backups.preview(data())
        try {
            backups.writeExport(at) { throw IOException("failed") }
            fail("failure")
        } catch (_: IOException) {}
        backups.confirm(preview)
        val stale = backups.preview(data())
        assertTrue(backups.writeExport(at) {})
        val saved = db.trackerDao().documents()
        try {
            backups.confirm(stale)
            fail("stale import")
        } catch (_: IllegalStateException) {}
        assertEquals(saved, db.trackerDao().documents())
    }
}
