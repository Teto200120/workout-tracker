package com.hector.workouttracker

import com.hector.workouttracker.core.model.trackerJson
import com.hector.workouttracker.core.schedule.scheduleDays
import com.hector.workouttracker.domain.*
import java.time.LocalDate
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ProgressMetricsTest {
    private val encoding = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    private fun fixtures() =
        trackerJson
            .parseToJsonElement(javaClass.getResource("/progress.json")!!.readText())
            .jsonObject
            .getValue("cases")
            .jsonArray

    private fun equal(expected: JsonElement, actual: JsonElement, path: String) {
        when {
            expected is JsonObject && actual is JsonObject -> {
                assertEquals(path, expected.keys, actual.keys)
                expected.forEach { (k, v) -> equal(v, actual.getValue(k), "$path.$k") }
            }
            expected is JsonArray && actual is JsonArray -> {
                assertEquals(path, expected.size, actual.size)
                expected.forEachIndexed { i, v -> equal(v, actual[i], "$path[$i]") }
            }
            expected is JsonPrimitive &&
                actual is JsonPrimitive &&
                !expected.isString &&
                !actual.isString &&
                expected.doubleOrNull != null &&
                actual.doubleOrNull != null ->
                assertEquals(path, expected.double, actual.double, 0.000001)
            else -> assertEquals(path, expected, actual)
        }
    }

    @Test
    fun actualPwaFixturesMatchRecordsTotalsWeeksStreaksAndCompletion() {
        for (value in fixtures()) {
            val c = value.jsonObject
            val name = c.getValue("name").jsonPrimitive.content
            val workouts = c.getValue("workouts").jsonArray.map { it.jsonObject }
            val expected = c.getValue("expected").jsonObject
            val date = LocalDate.parse(c.getValue("today").jsonPrimitive.content)
            equal(
                expected.getValue("totals"),
                encoding.encodeToJsonElement(progressTotals(workouts)),
                "$name.totals",
            )
            val exercises = buildExerciseStats(workouts).associateBy { it.name }
            val originals =
                expected.getValue("exercises").jsonArray.associateBy {
                    it.jsonObject.getValue("name").jsonPrimitive.content
                }
            assertEquals(name, originals.keys, exercises.keys)
            originals.forEach { (key, e) ->
                equal(e, encoding.encodeToJsonElement(exercises.getValue(key)), "$name.$key")
            }
            equal(
                expected.getValue("weekly"),
                encoding.encodeToJsonElement(
                    weeklyActivity(workouts, date, scheduleDays(c.getValue("settings").jsonObject))
                ),
                "$name.weekly",
            )
            assertEquals(
                name,
                expected.getValue("streak").jsonPrimitive.int,
                workoutStreak(workouts, date),
            )
            assertEquals(
                name,
                expected.getValue("recent").jsonPrimitive.int,
                recentWorkoutCount(workouts, date),
            )
            c["completion"]?.let {
                equal(
                    expected.getValue("completion"),
                    encoding.encodeToJsonElement(completionSummary(it.jsonObject, workouts)),
                    "$name.completion",
                )
            }
        }
    }

    @Test
    fun calculationsDoNotNormalizeOrMutateSavedInput() {
        for (c in fixtures().map { it.jsonObject }) {
            val workouts = c.getValue("workouts").jsonArray.map { it.jsonObject }
            val before = workouts.toString()
            sortedWorkouts(workouts)
            buildExerciseStats(workouts)
            progressTotals(workouts)
            c["completion"]?.let { completionSummary(it.jsonObject, workouts) }
            assertEquals(before, workouts.toString())
        }
    }

    @Test
    fun repeatedRowsCountOncePerWorkoutButKeepEveryProgressEntry() {
        val source = fixtures()[1].jsonObject.getValue("workouts").jsonArray.first().jsonObject
        val row = source.getValue("exercises").jsonArray.first()
        val workouts =
            List(2000) { i ->
                JsonObject(
                    source +
                        mapOf("id" to JsonPrimitive(i), "exercises" to JsonArray(listOf(row, row)))
                )
            }
        val result = buildExerciseStats(workouts).single()
        assertEquals(2000, result.sessions)
        assertEquals(12000, result.sets)
        assertEquals(4000, result.history.size)
        assertEquals(200.0, result.records.bestWeight!!.weight, 0.0)
        assertEquals(180.0, result.records.estimated1rm!!.weight, 0.0)
    }

    @Test
    fun typedIdOrderingMatchesIndexedDbAndDoesNotConflateZero() {
        val ids = listOf(JsonPrimitive("0"), JsonPrimitive(10), JsonPrimitive(2), JsonPrimitive(0))
        val rows = ids.map { buildJsonObject { put("id", it) } }
        assertEquals(
            listOf(JsonPrimitive(0), JsonPrimitive(2), JsonPrimitive(10), JsonPrimitive("0")),
            interchangeOrder(rows).map { it.getValue("id") },
        )
    }
}
