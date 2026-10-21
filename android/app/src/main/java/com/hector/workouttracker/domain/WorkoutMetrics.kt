package com.hector.workouttracker.domain

import kotlinx.serialization.json.*

private fun JsonObject.rows(key: String) = (get(key) as? JsonArray)?.map { it.jsonObject }.orEmpty()

private fun JsonObject.number(key: String) = (get(key) as? JsonPrimitive)?.doubleOrNull ?: 0.0

private fun JsonObject.flag(key: String) = (get(key) as? JsonPrimitive)?.booleanOrNull == true

data class WorkoutMetrics(
    val volume: Double,
    val workingSets: Int,
    val completedSets: Int,
    val duration: Double,
)

fun workoutMetrics(workout: JsonObject): WorkoutMetrics {
    val sets = workout.rows("exercises").flatMap { it.rows("sets") }.filterNot { it.flag("warmup") }
    val explicit =
        (workout["durationMinutes"] as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull
    fun minutes(key: String): Int? {
        val text = (workout[key] as? JsonPrimitive)?.content ?: return null
        val parts = text.split(':')
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        return hour * 60 + minute
    }
    val start = minutes("startTime")
    val end = minutes("endTime")
    val duration =
        explicit
            ?: if (start == null || end == null) 0.0 else ((end - start + 1440) % 1440).toDouble()
    return WorkoutMetrics(
        sets.sumOf { it.number("weight") * it.number("reps") },
        sets.size,
        sets.count { it.flag("done") },
        duration,
    )
}
