package com.hector.workouttracker.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Collator
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class ProgressInsight(
    val type: String,
    val score: Double,
    val title: String,
    val meta: String,
    val date: String? = null,
)

@Serializable
data class RecentSession(
    val id: JsonPrimitive,
    val type: String,
    val date: String,
    val exercises: Int,
    val duration: Double,
)

private fun insightDate(value: String) =
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.US))

// JS toFixed rounds the actual binary value (for example 2.55 becomes 2.5).
private fun insightDecimal(value: Double) =
    BigDecimal(value).setScale(1, RoundingMode.HALF_UP).toPlainString()

private fun signedInsightNumber(value: Double): String =
    (if (value >= 0) "+" else "") +
        if (value == kotlin.math.floor(value)) progressNumber(value) else insightDecimal(value)

/** PWA narrative priority: two recent PRs, two rising trends, then same-type volume. */
fun progressInsights(
    workouts: List<JsonObject>,
    exercises: List<ExerciseStats>,
    today: LocalDate,
    limit: Int = 3,
): List<ProgressInsight> {
    val cutoff = today.minusDays(60).toString()
    val names = Collator.getInstance(Locale.US)
    val orderedExercises = exercises.sortedWith { a, b -> names.compare(a.name, b.name) }
    val records = mutableListOf<ProgressInsight>()
    for (exercise in orderedExercises) {
        var runningBest = 0.0
        exercise.history
            .sortedBy { it.date }
            .forEachIndexed { index, point ->
                val isPr = point.estimated1rm > runningBest
                runningBest = maxOf(runningBest, point.estimated1rm)
                if (isPr && index > 0 && point.date >= cutoff) {
                    val source = requireNotNull(point.records.estimated1rm)
                    records +=
                        ProgressInsight(
                            "pr",
                            point.estimated1rm,
                            "New PR: ${exercise.name}",
                            "${progressNumber(source.weight)} × ${progressNumber(source.reps)} · " +
                                "${insightDecimal(point.estimated1rm)} est. 1RM · ${insightDate(point.date)}",
                            point.date,
                        )
                }
            }
    }
    val trends =
        orderedExercises
            .mapNotNull { exercise ->
                val recent = exercise.history.sortedBy { it.date }.takeLast(7)
                if (recent.size < 2) return@mapNotNull null
                val delta = recent.last().bestWeight - recent.first().bestWeight
                val estimatedDelta = recent.last().estimated1rm - recent.first().estimated1rm
                if (delta <= 0 && estimatedDelta < 2) return@mapNotNull null
                ProgressInsight(
                    "trend",
                    maxOf(delta, estimatedDelta),
                    "${exercise.name} is trending up",
                    "${recent.size} recent sessions · ${signedInsightNumber(delta)} lb since ${insightDate(recent.first().date)}",
                )
            }
            .sortedByDescending { it.score }
            .take(2)
    val candidates =
        records
            .sortedWith(
                compareByDescending<ProgressInsight> { it.date }.thenByDescending { it.score }
            )
            .take(2)
            .toMutableList()
    candidates += trends
    val latest = workouts.firstOrNull()
    val type = latest?.get("type")?.jsonPrimitive?.content
    val previous = workouts.drop(1).firstOrNull { it["type"]?.jsonPrimitive?.content == type }
    if (latest != null && previous != null) {
        val delta = workoutMetrics(latest).volume - workoutMetrics(previous).volume
        if (kotlin.math.abs(delta) >= 250)
            candidates +=
                ProgressInsight(
                    "volume",
                    kotlin.math.abs(delta),
                    "$type volume ${if (delta > 0) "increased" else "dipped"}",
                    "${signedInsightNumber(kotlin.math.floor(delta + 0.5))} lb vs ${insightDate(previous.getValue("date").jsonPrimitive.content)}",
                )
    }
    return candidates.distinctBy { it.type to it.title }.take(limit.coerceAtLeast(0))
}

/** Input is the same date/createdAt-ordered, typed-ID-stable snapshot used by Stats. */
fun recentSessions(workouts: List<JsonObject>): List<RecentSession> =
    workouts.take(3).map {
        RecentSession(
            it.getValue("id").jsonPrimitive,
            it.getValue("type").jsonPrimitive.content,
            it.getValue("date").jsonPrimitive.content,
            it.getValue("exercises").jsonArray.size,
            workoutMetrics(it).duration,
        )
    }
