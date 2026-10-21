package com.hector.workouttracker

import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.domain.workoutMetrics
import java.time.ZoneId
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CompatibilityTest {
    @Test
    fun sharedWebFixtureMatchesNativeMetricsAndTypedIds() {
        val fixture =
            trackerJson
                .parseToJsonElement(javaClass.getResource("/baseline.json")!!.readText())
                .jsonObject
        val workouts = fixture.getValue("backup").jsonObject.getValue("workouts").jsonArray
        val expected = fixture.getValue("expected").jsonArray
        workouts.forEachIndexed { index, value ->
            val w = value.jsonObject
            val e = expected[index].jsonObject
            val m = workoutMetrics(w)
            assertEquals(e.getValue("idKey").jsonPrimitive.content, typedIdKey(w.getValue("id")))
            assertEquals(e.getValue("volume").jsonPrimitive.double, m.volume, 0.00001)
            assertEquals(e.getValue("workingSets").jsonPrimitive.int, m.workingSets)
            assertEquals(e.getValue("completedSets").jsonPrimitive.int, m.completedSets)
            assertEquals(e.getValue("duration").jsonPrimitive.double, m.duration, 0.00001)
            assertEquals(value, trackerJson.parseToJsonElement(value.toString()))
        }
    }

    @Test
    fun numericAliasesMatchButStringIdStaysDistinct() {
        assertEquals(typedIdKey(JsonPrimitive(0)), typedIdKey(JsonPrimitive(0.0)))
        assertNotEquals(typedIdKey(JsonPrimitive(0)), typedIdKey(JsonPrimitive("0")))
        for (invalid in listOf(JsonNull, JsonPrimitive(true), JsonPrimitive(""))) {
            assertThrows(IllegalArgumentException::class.java) { typedIdKey(invalid) }
        }
    }

    @Test
    fun timerUsesMonotonicTimeOnSameBootAndBoundedWallTimeAfterReboot() {
        val draft = newSession(100000, 40000, 3, ZoneId.of("America/New_York"))
        assertEquals(60000L, elapsedMillis(draft, 999999999, 100000, 3))
        assertEquals(5000L, elapsedMillis(draft, 105000, 10, 4))
        assertEquals(0L, elapsedMillis(draft, 0, 10, 4))
        assertEquals(86400000L, elapsedMillis(draft, Long.MAX_VALUE, 10, 4))
    }

    @Test
    fun nativePayloadOmitsInternalKeysAndKeepsZeroAndLocalDate() {
        val draft =
            newSession(1792540800000, 1, 1, ZoneId.of("America/New_York"))
                .copy(
                    exercises =
                        listOf(
                            Exercise(
                                name = "Bodyweight squat",
                                sets = listOf(WorkingSet(weight = "0", reps = "10")),
                            )
                        )
                )
        val payload = workoutPayload(draft, draft.startedAt, 0, ZoneId.of("America/New_York"))
        assertEquals(draft.date, payload.getValue("date").jsonPrimitive.content)
        assertEquals(0, payload.getValue("durationMinutes").jsonPrimitive.int)
        val set =
            payload
                .getValue("exercises")
                .jsonArray[0]
                .jsonObject
                .getValue("sets")
                .jsonArray[0]
                .jsonObject
        assertEquals("0", set.getValue("weight").jsonPrimitive.content)
        assertFalse(set.containsKey("key"))
        assertFalse(payload.containsKey("startedElapsed"))
        assertNull(sessionError(draft))
    }

    @Test
    fun guardrailsRejectInvalidNumbersWithoutClamping() {
        val draft = newSession(0, 0, 1, ZoneId.of("UTC"))
        assertNotNull(sessionError(draft))
        for (value in listOf("NaN", "Infinity", "-1", "10001", "abc")) {
            assertNotNull(
                sessionError(
                    draft.copy(
                        exercises =
                            listOf(
                                Exercise(
                                    name = "Test",
                                    sets = listOf(WorkingSet(weight = value, reps = "5")),
                                )
                            )
                    )
                )
            )
        }
        assertFalse(visibleName("\u200b", 80))
        assertTrue(visibleName("Hector", 80))
    }
}
