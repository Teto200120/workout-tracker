package com.hector.workouttracker

import com.hector.workouttracker.core.backup.*
import java.io.ByteArrayInputStream
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class BackupCodecTest {
    private fun fixture() =
        Json.parseToJsonElement(javaClass.getResource("/backup-migration.json")!!.readText())
            .jsonObject
            .getValue("cases")
            .jsonArray

    @Test
    fun matchesPwaMigrationsAndPreservesEveryNestedValue() {
        for (case in fixture()) {
            val obj = case.jsonObject
            val actual = BackupCodec.parse(obj.getValue("backup").toString())
            val expected = obj.getValue("expected").jsonObject
            assertEquals(expected.getValue("workouts"), JsonArray(actual.workouts))
            assertEquals(expected.getValue("routines"), JsonArray(actual.routines))
            assertEquals(expected.getValue("legacyWeights"), JsonArray(actual.weights))
            assertEquals(expected.getValue("settings"), actual.settings)
            assertEquals(expected.getValue("goals"), actual.goals)
            assertEquals(expected.getValue("backupMeta"), actual.metadata)
            val again = BackupCodec.parse(BackupCodec.encode(actual, "2026-10-21T18:00:00Z"))
            assertEquals(actual.workouts, again.workouts)
            assertEquals(actual.routines, again.routines)
            assertEquals(actual.weights, again.weights)
            assertEquals(actual.settings, again.settings)
        }
    }

    private fun base() = fixture().last().jsonObject.getValue("backup").jsonObject

    private fun rejected(root: JsonObject) {
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.parse(root.toString()) }
    }

    @Test
    fun rejectsFutureConflictingMissingAndInvalidVersions() {
        rejected(JsonObject(base() + mapOf("backupFileVersion" to JsonPrimitive(4))))
        rejected(JsonObject(base() + mapOf("applicationSchemaVersion" to JsonPrimitive(3))))
        rejected(JsonObject(base() + mapOf("version" to JsonPrimitive(2))))
        rejected(JsonObject(base() - "applicationSchemaVersion"))
        rejected(JsonObject(base() + mapOf("backupFileVersion" to JsonPrimitive("03"))))
        rejected(JsonObject(base() + mapOf("workouts" to JsonNull)))
        assertEquals(
            3,
            BackupCodec.parse(
                    JsonObject(base() + ("backupFileVersion" to JsonPrimitive(3.0))).toString()
                )
                .backupVersion,
        )
        rejected(JsonObject(base() + ("kind" to JsonPrimitive("workout-tracker-raw-rescue"))))
        for (version in 0..2) {
            val legacy = fixture().first().jsonObject.getValue("backup").jsonObject
            assertEquals(
                version,
                BackupCodec.parse(
                        JsonObject(legacy + mapOf("version" to JsonPrimitive(version))).toString()
                    )
                    .backupVersion,
            )
        }
    }

    @Test
    fun rejectsDuplicateTypedIdsBadDatesAndInvalidSetFields() {
        val first = base().getValue("workouts").jsonArray.first().jsonObject
        rejected(
            JsonObject(
                base() +
                    mapOf(
                        "workouts" to
                            JsonArray(
                                listOf(first, JsonObject(first + ("id" to JsonPrimitive(0.0))))
                            )
                    )
            )
        )
        for ((key, value) in
            listOf(
                "date" to JsonPrimitive("2026-02-30"),
                "durationMinutes" to JsonPrimitive(1441),
                "type" to JsonPrimitive("\u200b"),
            )) rejected(
            JsonObject(
                base() + mapOf("workouts" to JsonArray(listOf(JsonObject(first + (key to value)))))
            )
        )
        val exercise = first.getValue("exercises").jsonArray.first().jsonObject
        val set = exercise.getValue("sets").jsonArray.first().jsonObject
        for ((key, value) in
            listOf(
                "weight" to JsonPrimitive("1e3"),
                "reps" to JsonPrimitive("1.2"),
                "rpe" to JsonPrimitive("8.2"),
                "done" to JsonPrimitive("true"),
                "warmup" to JsonNull,
            )) {
            val e =
                JsonObject(
                    exercise + ("sets" to JsonArray(listOf(JsonObject(set + (key to value)))))
                )
            val w = JsonObject(first + ("exercises" to JsonArray(listOf(e))))
            rejected(JsonObject(base() + ("workouts" to JsonArray(listOf(w)))))
        }
    }

    @Test
    fun usesWebWhitespaceRulesForNamesWithoutHidingControlCharacters() {
        val source = base()
        val settings = source.getValue("settings").jsonObject
        val named =
            JsonObject(
                source +
                    ("settings" to
                        JsonObject(
                            settings + ("displayName" to JsonPrimitive("\ufeffFixture User\ufeff"))
                        ))
            )
        assertEquals(
            JsonPrimitive("Fixture User"),
            BackupCodec.parse(named.toString()).settings!!.getValue("displayName"),
        )
        rejected(
            JsonObject(
                source +
                    ("settings" to
                        JsonObject(
                            settings + ("displayName" to JsonPrimitive("\u001cFixture User"))
                        ))
            )
        )
    }

    @Test
    fun boundsUntrustedStreamsAndJsonBeforeMutation() {
        assertThrows(IllegalArgumentException::class.java) {
            readBackup(ByteArrayInputStream(ByteArray(MAX_BACKUP_BYTES + 1)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            readBackup(ByteArrayInputStream(byteArrayOf()))
        }
        assertThrows(java.nio.charset.CharacterCodingException::class.java) {
            readBackup(ByteArrayInputStream(byteArrayOf(0xc3.toByte(), 0x28)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.parse("[".repeat(30000) + "]".repeat(30000))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.parse("{\"workouts\":[],\"constructor\":{}}")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.parse("{\"workouts\":[],\"extra\":[" + "0,".repeat(500000) + "0]}")
        }
        assertEquals("{}", readBackup(ByteArrayInputStream("\uFEFF{}".toByteArray())))
    }
}
