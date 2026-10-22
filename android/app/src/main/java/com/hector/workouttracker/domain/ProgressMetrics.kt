package com.hector.workouttracker.domain

import com.hector.workouttracker.core.schedule.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

private fun JsonObject.rows(key: String) = (get(key) as? JsonArray)?.map { it.jsonObject }.orEmpty()

private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.content.orEmpty()

private fun JsonObject.flag(key: String) = (get(key) as? JsonPrimitive)?.booleanOrNull == true

private fun JsonObject.number(key: String) = (get(key) as? JsonPrimitive)?.doubleOrNull ?: 0.0

private fun jsTrim(value: String) =
    value.trim {
        it in "\t\n\u000b\u000c\r \u00a0\u1680\u2028\u2029\u202f\u205f\u3000\ufeff" ||
            it in '\u2000'..'\u200a'
    }

fun progressNumber(value: Double): String =
    BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

@Serializable
data class RecordSet(
    val weight: Double,
    val reps: Double,
    val estimated1rm: Double,
    val volume: Double,
    val rpe: JsonElement = JsonPrimitive(""),
    val date: String? = null,
)

@Serializable
data class ExerciseRecords(
    val bestWeight: RecordSet? = null,
    val bestVolume: RecordSet? = null,
    val estimated1rm: RecordSet? = null,
) {
    fun value(metric: String): RecordSet? =
        when (metric) {
            "bestWeight" -> bestWeight
            "bestVolume" -> bestVolume
            else -> estimated1rm
        }
}

@Serializable
data class ExercisePoint(
    val date: String,
    val type: String,
    val bestWeight: Double,
    val bestVolume: Double,
    val estimated1rm: Double,
    val reps: Double,
    val records: ExerciseRecords,
) {
    fun value(metric: String): Double =
        when (metric) {
            "bestWeight" -> bestWeight
            "bestVolume" -> bestVolume
            else -> estimated1rm
        }
}

@Serializable
data class ExerciseStats(
    val name: String,
    val sessions: Int = 0,
    val sets: Int = 0,
    val bestWeight: Double = 0.0,
    val bestVolume: Double = 0.0,
    val bestEstimated1rm: Double = 0.0,
    val bestDate: String = "",
    val records: ExerciseRecords = ExerciseRecords(),
    val history: List<ExercisePoint> = emptyList(),
)

/** Each metric retains its own source set; ties keep the first source in input order. */
fun exerciseRecords(exercise: JsonObject): ExerciseRecords {
    var load: RecordSet? = null
    var volume: RecordSet? = null
    var estimated: RecordSet? = null
    for (set in exercise.rows("sets")) {
        if (set.flag("warmup")) continue
        val w = set.number("weight")
        val r = set.number("reps")
        if (!w.isFinite() || !r.isFinite() || w <= 0 || r <= 0) continue
        val rpe =
            set["rpe"].takeUnless {
                it == null || it is JsonNull || it == JsonPrimitive(0) || it == JsonPrimitive(false)
            } ?: JsonPrimitive("")
        val candidate = RecordSet(w, r, w * (1 + r / 30), w * r, rpe)
        if (load == null || w > load.weight) load = candidate
        if (volume == null || candidate.volume > volume.volume) volume = candidate
        if (estimated == null || candidate.estimated1rm > estimated.estimated1rm)
            estimated = candidate
    }
    return ExerciseRecords(load, volume, estimated)
}

/** Stats use trimmed, case-sensitive saved names. Catalog aliases never merge user records. */
fun buildExerciseStats(workouts: List<JsonObject>): List<ExerciseStats> {
    class Aggregate(val name: String) {
        var sessions = 0
        var sets = 0
        var records = ExerciseRecords()
        var best = 0.0
        var bestDate = ""
        val history = mutableListOf<ExercisePoint>()

        fun result() =
            ExerciseStats(
                name,
                sessions,
                sets,
                records.bestWeight?.weight ?: 0.0,
                records.bestVolume?.volume ?: 0.0,
                best,
                bestDate,
                records,
                history.toList(),
            )
    }
    val stats = linkedMapOf<String, Aggregate>()
    for (workout in workouts) {
        val seen = mutableSetOf<String>()
        for (exercise in workout.rows("exercises")) {
            val name = jsTrim(exercise.text("name"))
            if (name.isEmpty()) continue
            val item = stats.getOrPut(name) { Aggregate(name) }
            if (seen.add(name)) item.sessions++
            item.sets += exercise.rows("sets").count { !it.flag("warmup") }
            val records = exerciseRecords(exercise)
            val best = records.estimated1rm ?: continue
            val date = workout.text("date")
            fun record(metric: String, field: (RecordSet) -> Double): RecordSet {
                val old = item.records.value(metric)
                val current = requireNotNull(records.value(metric))
                return if (old == null || field(current) > field(old)) current.copy(date = date)
                else old
            }
            item.records =
                ExerciseRecords(
                    record("bestWeight") { it.weight },
                    record("bestVolume") { it.volume },
                    record("estimated1rm") { it.estimated1rm },
                )
            if (best.estimated1rm > item.best) {
                item.best = best.estimated1rm
                item.bestDate = date
            }
            item.history +=
                ExercisePoint(
                    date,
                    workout.text("type"),
                    records.bestWeight!!.weight,
                    records.bestVolume!!.volume,
                    best.estimated1rm,
                    best.reps,
                    records,
                )
        }
    }
    return stats.values
        .map { it.result() }
        .sortedWith(
            compareBy<ExerciseStats, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
                .thenBy { it.name }
        )
}

/** IndexedDB orders numeric keys before string keys, with numeric (not lexical) ordering. */
fun interchangeOrder(workouts: List<JsonObject>): List<JsonObject> =
    workouts.sortedWith { a, b ->
        val x = a.getValue("id").jsonPrimitive
        val y = b.getValue("id").jsonPrimitive
        when {
            !x.isString && y.isString -> -1
            x.isString && !y.isString -> 1
            x.isString -> x.content.compareTo(y.content)
            else -> x.double.compareTo(y.double)
        }
    }

fun sortedWorkouts(workouts: List<JsonObject>): List<JsonObject> =
    workouts.sortedWith(
        compareByDescending<JsonObject> { it.text("date") }
            .thenByDescending { it.text("createdAt") }
    )

@Serializable
data class ProgressTotals(
    val workouts: Int,
    val sets: Int,
    val volume: Double,
    val avgDuration: Int,
)

fun progressTotals(workouts: List<JsonObject>): ProgressTotals {
    val metrics = workouts.map(::workoutMetrics)
    val duration = metrics.map { it.duration }.filter { it != 0.0 }
    return ProgressTotals(
        workouts.size,
        metrics.sumOf { it.workingSets },
        metrics.sumOf { it.volume },
        if (duration.isEmpty()) 0 else kotlin.math.floor(duration.average() + 0.5).toInt(),
    )
}

@Serializable
data class ActivityDay(
    val key: String,
    val label: String,
    val title: String,
    val kind: String,
    val complete: Boolean,
    val status: String,
)

@Serializable
data class WeeklyActivity(val days: List<ActivityDay>, val gymDays: Int, val completedGymDays: Int)

fun weeklyActivity(
    workouts: List<JsonObject>,
    today: LocalDate,
    schedule: List<ScheduleDay>,
): WeeklyActivity {
    val dates = workouts.map { it.text("date") }.toSet()
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val days =
        (0..6).map { i ->
            val date = monday.plusDays(i.toLong())
            val key = date.toString()
            val plan = resolveDayPlan(date, schedule)
            val done = key in dates
            ActivityDay(
                key,
                listOf("M", "T", "W", "T", "F", "S", "S")[i],
                plan.title,
                plan.kind,
                done,
                if (done) "Logged"
                else
                    when (plan.kind) {
                        "rest" -> "Rest"
                        "soccer" -> "Soccer"
                        else -> "Open"
                    },
            )
        }
    return WeeklyActivity(
        days,
        days.count { it.kind == "gym" },
        days.count { it.kind == "gym" && it.complete },
    )
}

fun workoutStreak(workouts: List<JsonObject>, today: LocalDate): Int {
    val dates = workouts.map { it.text("date") }.toSet()
    var cursor = today
    var count = 0
    while (cursor.toString() in dates) {
        count++
        cursor = cursor.minusDays(1)
    }
    return count
}

fun recentWorkoutCount(workouts: List<JsonObject>, today: LocalDate): Int {
    val cutoff = today.minusDays(6).toString()
    val end = today.toString()
    return workouts.count { it.text("date") in cutoff..end }
}

@Serializable data class CompletionHighlight(val type: String, val title: String, val text: String)

@Serializable
data class CompletionSummary(
    val volume: Long,
    val sets: Int,
    val duration: Double,
    val highlights: List<CompletionHighlight>,
)

fun completionSummary(workout: JsonObject, previousWorkouts: List<JsonObject>): CompletionSummary {
    val metrics = workoutMetrics(workout)
    val volume = kotlin.math.floor(metrics.volume + 0.5).toLong()
    val highlights = mutableListOf<CompletionHighlight>()
    val historical = mutableMapOf<String, RecordSet>()
    for (saved in previousWorkouts) for (exercise in saved.rows("exercises")) {
        val name = jsTrim(exercise.text("name")).lowercase(Locale.US)
        val best = exerciseRecords(exercise).estimated1rm ?: continue
        if (historical[name]?.let { best.estimated1rm > it.estimated1rm } != false)
            historical[name] = best
    }
    for (exercise in workout.rows("exercises")) {
        val best = exerciseRecords(exercise).estimated1rm ?: continue
        val name = jsTrim(exercise.text("name")).lowercase(Locale.US)
        val previous = historical[name]
        if (previous == null || best.estimated1rm > previous.estimated1rm)
            highlights +=
                CompletionHighlight(
                    "PR",
                    "New PR: ${exercise.text("name")}",
                    "${progressNumber(best.weight)} × ${progressNumber(best.reps)}" +
                        (previous?.let {
                            " · beat ${progressNumber(it.weight)} × ${progressNumber(it.reps)}"
                        } ?: ""),
                )
    }
    val previous =
        sortedWorkouts(previousWorkouts).firstOrNull { it.text("type") == workout.text("type") }
    if (previous != null) {
        val old = workoutMetrics(previous)
        val delta = volume - kotlin.math.floor(old.volume + 0.5).toLong()
        if (delta > 0)
            highlights +=
                CompletionHighlight(
                    "Volume",
                    "Volume increased",
                    "+${String.format(Locale.US, "%,d", delta)} lb vs last ${workout.text("type")}",
                )
        if (metrics.workingSets > old.workingSets)
            highlights +=
                CompletionHighlight(
                    "Work",
                    "More work completed",
                    "${metrics.workingSets} work sets today vs ${old.workingSets} last time",
                )
    }
    if (highlights.isEmpty())
        highlights +=
            CompletionHighlight(
                "Saved",
                "Session logged",
                "Workout completed and saved. Progress still counts even without a PR.",
            )
    return CompletionSummary(volume, metrics.workingSets, metrics.duration, highlights.take(4))
}
