package com.hector.workouttracker

import android.os.SystemClock
import android.provider.Settings
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.hector.workouttracker.core.backup.BackupCodec
import com.hector.workouttracker.core.database.*
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.data.*
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Compiled with both app versions. The host owns process termination and APK replacement. */
class LifecycleAcceptanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val checkpoint = File(context.filesDir, "lifecycle-checkpoint.json")
    private val evidence = File(context.getExternalFilesDir(null), "qa-lifecycle")

    @Test
    fun requestedPhase() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val phase = args.getString("lifecyclePhase")
        assumeTrue(
            "Run through android/qa/verify_lifecycle.py on a fresh disposable emulator.",
            phase != null,
        )
        val expectedVersion = requireNotNull(args.getString("expectedVersion")).toInt()
        assertEquals(expectedVersion, BuildConfig.VERSION_CODE)
        val db =
            Room.databaseBuilder(context, TrackerDatabase::class.java, "workout-tracker.db").build()
        try {
            val repository = TrackerRepository(db)
            val backups = BackupRepository(db)
            when (phase) {
                "prepare-old" -> {
                    assertEquals(2, expectedVersion)
                    assertTrue(db.trackerDao().records().isEmpty())
                    assertNull(repository.readDraft())
                    val fixture =
                        instrumentation.context.assets
                            .open("training-backup.json")
                            .bufferedReader()
                            .use { it.readText() }
                    backups.confirm(backups.preview(BackupCodec.parse(fixture)))
                    repository.saveProfile(
                        Profile("Upgrade Fixture", false),
                        repository.readProfile().second,
                    )
                    val completed = fixtureDraft("Saved before upgrade")
                    repository.start(completed)
                    repository.finish(
                        completed,
                        1,
                        workoutPayload(
                            completed,
                            System.currentTimeMillis(),
                            0,
                            ZoneId.systemDefault(),
                        ),
                    )
                    val active = fixtureDraft("Recovery fixture")
                    repository.start(active)
                    repository.saveDraft(
                        active.copy(notes = "Committed notes survive update and process death."),
                        1,
                    )
                    checkpoint.writeText(snapshot(db).toString())
                }
                "prepare-current" -> {
                    assertTrue(expectedVersion >= 3)
                    verifySnapshot(db, repository)
                    val previous = requireNotNull(repository.readDraft())
                    repository.finish(
                        previous.first,
                        previous.second,
                        workoutPayload(
                            previous.first,
                            System.currentTimeMillis(),
                            0,
                            ZoneId.systemDefault(),
                        ),
                    )
                    // JSON keeps this fixture source compatible with version 2's model API.
                    val base =
                        trackerJson
                            .encodeToJsonElement(
                                SessionDraft.serializer(),
                                fixtureDraft("Recovery fixture"),
                            )
                            .jsonObject
                    val current =
                        trackerJson.decodeFromJsonElement(
                            SessionDraft.serializer(),
                            JsonObject(
                                base +
                                    mapOf(
                                        "originRoutineId" to JsonPrimitive(0),
                                        "exercises" to
                                            JsonArray(
                                                base.getValue("exercises").jsonArray.mapIndexed {
                                                    index,
                                                    value ->
                                                    JsonObject(
                                                        value.jsonObject +
                                                            ("collapsed" to
                                                                JsonPrimitive(index != 1))
                                                    )
                                                }
                                            ),
                                    )
                            ),
                        )
                    repository.start(current)
                    repository.saveDraft(
                        current.copy(
                            notes = "Current draft retains a numeric-zero routine origin."
                        ),
                        1,
                    )
                    checkpoint.writeText(snapshot(db).toString())
                }
                "verify" -> verifySnapshot(db, repository)
                "verify-finished" -> verifyFinished(db, repository)
                else -> error("Unknown lifecycle phase: $phase")
            }
            evidence.mkdirs()
            val actual = snapshot(db)
            File(evidence, "$phase-v$expectedVersion.json").writeText(actual.toString())
            File(evidence, "checkpoint.json").writeText(checkpoint.readText())
            File(evidence, "lifecycle-export.json")
                .writeText(backups.export(Instant.now().toString()))
        } finally {
            db.close()
        }
    }

    private fun fixtureDraft(type: String): SessionDraft =
        newSession(
                System.currentTimeMillis(),
                SystemClock.elapsedRealtime(),
                Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1),
                ZoneId.systemDefault(),
            )
            .copy(
                type = type,
                exercises =
                    listOf(
                        Exercise(
                            name = "Flat Bench Press",
                            notes = "Keep the saved spelling.",
                            sets =
                                listOf(
                                    WorkingSet(
                                        weight = "20",
                                        reps = "10",
                                        rpe = "",
                                        done = true,
                                        warmup = true,
                                    ),
                                    WorkingSet(
                                        weight = "0",
                                        reps = "8",
                                        rpe = "7.5",
                                        done = false,
                                        warmup = false,
                                    ),
                                ),
                        ),
                        Exercise(
                            name = "My custom lift",
                            sets = listOf(WorkingSet(weight = "", reps = "", rpe = "")),
                        ),
                        Exercise(
                            name = "Flat Bench Press",
                            sets =
                                listOf(
                                    WorkingSet(weight = "100", reps = "5", rpe = "8.5", done = true)
                                ),
                        ),
                    ),
            )

    private suspend fun snapshot(db: TrackerDatabase): JsonObject =
        db.withTransaction {
            val dao = db.trackerDao()
            buildJsonObject {
                put("schema", db.openHelper.readableDatabase.version)
                put(
                    "records",
                    buildJsonArray {
                        dao.records().forEach { row ->
                            add(
                                buildJsonObject {
                                    put("kind", row.kind)
                                    put("idKey", row.idKey)
                                    put("payload", row.payload)
                                    put("revision", row.revision)
                                }
                            )
                        }
                    },
                )
                put(
                    "documents",
                    buildJsonArray {
                        dao.documents().forEach { row ->
                            add(
                                buildJsonObject {
                                    put("name", row.name)
                                    put("payload", row.payload)
                                    put("revision", row.revision)
                                }
                            )
                        }
                    },
                )
                val draft = dao.draft()
                put(
                    "draft",
                    if (draft == null) JsonNull
                    else
                        buildJsonObject {
                            put("slot", draft.slot)
                            put("sessionId", draft.sessionId)
                            put("payload", draft.payload)
                            put("revision", draft.revision)
                        },
                )
            }
        }

    private suspend fun verifySnapshot(db: TrackerDatabase, repository: TrackerRepository) {
        assertTrue("The persisted checkpoint must survive APK replacement.", checkpoint.isFile)
        assertEquals(trackerJson.parseToJsonElement(checkpoint.readText()), snapshot(db))
        assertEquals(Profile("Upgrade Fixture", false), repository.readProfile().first)
        val draft = requireNotNull(repository.readDraft())
        assertEquals(2L, draft.second)
        assertEquals("Recovery fixture", draft.first.type)
        assertEquals(
            listOf("Flat Bench Press", "My custom lift", "Flat Bench Press"),
            draft.first.exercises.map { it.name },
        )
        assertEquals("0", draft.first.exercises.first().sets[1].weight)
        assertEquals("", draft.first.exercises[1].sets.single().reps)
        assertEquals("7.5", draft.first.exercises.first().sets[1].rpe)
        assertTrue(draft.first.exercises.first().sets.first().warmup)
        assertFalse(draft.first.exercises.first().sets[1].done)
        // JSON keeps this assertion compilable against the historical version-2 model.
        val encoded =
            trackerJson.encodeToJsonElement(SessionDraft.serializer(), draft.first).jsonObject
        if (encoded.containsKey("originRoutineId")) {
            assertEquals(
                listOf(true, false, true),
                encoded.getValue("exercises").jsonArray.map {
                    (it.jsonObject["collapsed"] as? JsonPrimitive)?.booleanOrNull ?: false
                },
            )
        }
    }

    private suspend fun verifyFinished(db: TrackerDatabase, repository: TrackerRepository) {
        val before = trackerJson.parseToJsonElement(checkpoint.readText()).jsonObject
        val savedDraft =
            trackerJson.decodeFromString<SessionDraft>(
                before.getValue("draft").jsonObject.getValue("payload").jsonPrimitive.content
            )
        val after = snapshot(db)
        assertNull(repository.readDraft())
        assertEquals(before.getValue("documents"), after.getValue("documents"))
        assertEquals(before.getValue("schema"), after.getValue("schema"))
        val oldRows = before.getValue("records").jsonArray
        val newRows = after.getValue("records").jsonArray
        assertTrue(newRows.containsAll(oldRows))
        assertEquals(oldRows.size + 1, newRows.size)
        val saved =
            requireNotNull(
                db.trackerDao().record("workout", typedIdKey(JsonPrimitive(savedDraft.id)))
            )
        val payload = trackerJson.parseToJsonElement(saved.payload).jsonObject
        val expected =
            workoutPayload(savedDraft, System.currentTimeMillis(), 0, ZoneId.systemDefault())
        for (field in
            listOf(
                "id",
                "date",
                "type",
                "notes",
                "exercises",
                "originRoutineId",
                "createdAt",
                "tags",
            )) assertEquals("Saved $field", expected[field], payload[field])
        assertEquals(JsonPrimitive(0), payload["originRoutineId"])
        assertEquals(Profile("Upgrade Fixture", false), repository.readProfile().first)
    }
}
