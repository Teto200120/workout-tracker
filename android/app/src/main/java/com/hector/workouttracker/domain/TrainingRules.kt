package com.hector.workouttracker.domain

import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.content.orEmpty()

private fun JsonObject.sets() = (get("sets") as? JsonArray)?.map { it.jsonObject }.orEmpty()

private fun truthy(value: JsonElement?): Boolean =
    when (value) {
        null,
        JsonNull -> false
        is JsonPrimitive ->
            if (value.isString) value.content.isNotEmpty()
            else
                value.booleanOrNull
                    ?: (value.doubleOrNull?.let { it != 0.0 && !it.isNaN() } ?: false)
        else -> true
    }

private fun numeric(value: JsonElement?): Double =
    (value as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }?.toDoubleOrNull() ?: 0.0

private fun numberText(value: Double) = progressNumber(value)

fun trainingScalar(value: JsonElement?): String {
    val item = value as? JsonPrimitive ?: return ""
    return if (item is JsonNull) ""
    else if (item.isString) item.content else item.doubleOrNull?.let(::numberText) ?: item.content
}

private fun fixedOne(value: Double) =
    java.math.BigDecimal(value).setScale(1, java.math.RoundingMode.HALF_UP).toPlainString()

fun trainingName(value: String) =
    value
        .trim {
            it in "\t\n\u000b\u000c\r \u00a0\u1680\u2028\u2029\u202f\u205f\u3000\ufeff" ||
                it in '\u2000'..'\u200a'
        }
        .lowercase(Locale.ROOT)

fun trainingWorkSets(sets: List<JsonObject>) =
    sets.filter { !truthy(it["warmup"]) && (truthy(it["weight"]) || truthy(it["reps"])) }

@Serializable
data class TrainingProfile(val min: Int, val max: Int, val increment: Double, val type: String)

fun trainingProfile(name: String, settings: JsonObject): TrainingProfile {
    val lower = name.lowercase(Locale.ROOT)
    val (prefix, type) =
        when {
            listOf(
                    "lateral raise",
                    "rear delt",
                    "fly",
                    "curl",
                    "pushdown",
                    "tricep",
                    "extension",
                    "calf",
                    "shrug",
                )
                .any { lower.contains(it) } -> "isolation" to "isolation"
            listOf(
                    "bench",
                    "squat",
                    "deadlift",
                    "romanian",
                    "rdl",
                    "leg press",
                    "shoulder press",
                    "press",
                )
                .any { lower.contains(it) } -> "compound" to "compound"
            listOf("row", "pulldown", "pull up", "pull-up", "chin").any { lower.contains(it) } ->
                "pull" to "compound pull"
            else -> "general" to "general"
        }
    return TrainingProfile(
        numeric(settings["${prefix}Min"]).toInt(),
        numeric(settings["${prefix}Max"]).toInt(),
        numeric(settings["defaultWeightJump"]).takeIf { it != 0.0 } ?: 5.0,
        type,
    )
}

@Serializable
data class TrainingTarget(
    val weight: String,
    val repsText: String,
    val reason: String,
    val targetSets: List<JsonObject>,
)

/** Exact PWA double-progression rules; saved unchecked sets qualify, warmups do not. */
fun trainingTarget(name: String, lastSets: List<JsonObject>, settings: JsonObject): TrainingTarget {
    val profile = trainingProfile(name, settings)
    val sets = trainingWorkSets(lastSets)
    if (sets.isEmpty())
        return TrainingTarget(
            "-",
            "Log clean work sets",
            "${profile.min}-${profile.max} rep range · build a baseline first",
            emptyList(),
        )
    val rpes =
        if (truthy(settings["rpeAware"])) sets.map { numeric(it["rpe"]) }.filter { it != 0.0 }
        else emptyList()
    val maxRpe = maxOf(0.0, rpes.maxOrNull() ?: 0.0)
    val avg = if (rpes.isEmpty()) 0.0 else rpes.average()
    val counts = linkedMapOf<String, Int>()
    sets
        .filter { truthy(it["weight"]) }
        .forEach {
            counts[trainingScalar(it["weight"])] = (counts[trainingScalar(it["weight"])] ?: 0) + 1
        }
    val primaryWeight = counts.entries.maxByOrNull { it.value }?.key?.toDoubleOrNull()
    val same =
        if (primaryWeight != null && primaryWeight.isFinite())
            sets.filter { numeric(it["weight"]) == primaryWeight }
        else sets
    val reps = same.map { numeric(it["reps"]) }.filter { it != 0.0 }
    val atTop = reps.isNotEmpty() && reps.all { it >= profile.max }
    var weight = primaryWeight?.takeIf { it != 0.0 } ?: numeric(sets.first()["weight"])
    val targetReps =
        (if (reps.isNotEmpty()) reps
            else
                sets.map {
                    if (truthy(it["reps"])) numeric(it["reps"]) else profile.min.toDouble()
                })
            .toMutableList()
    var reason = "${profile.min}-${profile.max} range"
    val rpeText = fixedOne(maxRpe)
    when {
        maxRpe >= 9.5 -> reason += " · last RPE $rpeText, repeat and clean it up"
        maxRpe >= 9 -> {
            val index = targetReps.indices.minByOrNull { targetReps[it] }
            if (index != null)
                targetReps[index] = minOf(profile.max.toDouble(), targetReps[index] + 1)
            reason += " · last RPE $rpeText, progress carefully"
        }
        atTop -> {
            weight += profile.increment
            targetReps.indices.forEach { targetReps[it] = profile.min.toDouble() }
            reason += " · top reached, add ${numberText(profile.increment)} lb"
        }
        else -> {
            val index =
                targetReps.indices
                    .filter { targetReps[it] < profile.max }
                    .minByOrNull { targetReps[it] }
            if (index != null)
                targetReps[index] = minOf(profile.max.toDouble(), targetReps[index] + 1)
            reason += if (avg != 0.0) " · avg RPE ${fixedOne(avg)}" else " · build reps first"
        }
    }
    val targets =
        same.mapIndexed { index, set ->
            JsonObject(
                set +
                    mapOf(
                        "weight" to
                            if (weight != 0.0) JsonPrimitive(numberText(weight))
                            else (set["weight"] ?: JsonNull),
                        "reps" to
                            if (targetReps.getOrNull(index)?.let { it != 0.0 } == true)
                                JsonPrimitive(numberText(targetReps[index]))
                            else (set["reps"] ?: JsonNull),
                        "done" to JsonPrimitive(false),
                    )
            )
        }
    return TrainingTarget(
        if (weight != 0.0) "${numberText(weight)} lb" else "-",
        if (targetReps.isNotEmpty()) "${targetReps.joinToString(" / ") { numberText(it) }} reps"
        else "Hold form",
        reason,
        targets,
    )
}

@Serializable
data class LastTraining(
    val id: JsonElement,
    val date: String,
    val type: String,
    val sets: List<JsonObject>,
)

@Serializable
data class TrainingGuidance(
    val name: String,
    val profile: TrainingProfile,
    val last: LastTraining?,
    val pr: RecordSet?,
    val target: TrainingTarget,
)

/** First matching row in the newest workout wins even if blank. Names do not resolve aliases. */
fun trainingGuidance(
    name: String,
    workouts: List<JsonObject>,
    settings: JsonObject,
): TrainingGuidance {
    val key = trainingName(name)
    val ordered = interchangeOrder(workouts)
    val last =
        sortedWorkouts(ordered).firstNotNullOfOrNull { workout ->
            val exercise =
                (workout["exercises"] as? JsonArray)
                    ?.firstOrNull { trainingName(it.jsonObject.text("name")) == key }
                    ?.jsonObject
            exercise?.let {
                LastTraining(
                    workout.getValue("id"),
                    workout.text("date"),
                    workout.text("type"),
                    it.sets(),
                )
            }
        }
    var pr: RecordSet? = null
    for (workout in ordered) for (exercise in (workout["exercises"] as? JsonArray).orEmpty()) {
        if (trainingName(exercise.jsonObject.text("name")) != key) continue
        val best = exerciseRecords(exercise.jsonObject).estimated1rm ?: continue
        if (pr == null || best.estimated1rm > pr.estimated1rm)
            pr = best.copy(date = workout.text("date"))
    }
    return TrainingGuidance(
        name,
        trainingProfile(name, settings),
        last,
        pr,
        trainingTarget(name, last?.sets.orEmpty(), settings),
    )
}
