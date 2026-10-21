package com.hector.workouttracker.core.model

import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

val trackerJson = Json { ignoreUnknownKeys = false }

/** Native draft format is independent of the PWA backup envelope. */
@Serializable
data class SessionDraft(
    val id: String = UUID.randomUUID().toString(),
    val date: String,
    val type: String = "Quick workout",
    val startedAt: Long,
    val startedElapsed: Long,
    val bootCount: Int,
    val startTime: String,
    val notes: String = "",
    val exercises: List<Exercise> = emptyList(),
)

@Serializable
data class Exercise(
    val key: String = UUID.randomUUID().toString(),
    val name: String,
    val notes: String = "",
    val sets: List<WorkingSet> = listOf(WorkingSet()),
)

@Serializable
data class WorkingSet(
    val key: String = UUID.randomUUID().toString(),
    val weight: String = "",
    val reps: String = "",
    val rpe: String = "",
    val done: Boolean = false,
    val warmup: Boolean = false,
)

@Serializable data class Profile(val displayName: String = "", val trackRpe: Boolean = true)

fun typedIdKey(id: JsonElement): String {
    require(id is JsonPrimitive && id !is JsonNull) { "Record ID is missing." }
    return if (id.isString) {
        require(id.content.isNotEmpty()) { "Record ID is empty." }
        "string:${id.content}"
    } else {
        require(id.content != "true" && id.content != "false") {
            "Record ID must be a number or string."
        }
        val number = id.content.toDoubleOrNull()
        require(number != null && number.isFinite()) { "Record ID is invalid." }
        "number:${BigDecimal.valueOf(number).stripTrailingZeros().toPlainString()}"
    }
}

fun visibleName(value: String, maxCodePoints: Int): Boolean =
    value.codePointCount(0, value.length) <= maxCodePoints &&
        value.any { !it.isWhitespace() && it !in "\u200b\u200c\u200d\u2060\ufeff" } &&
        value.none { it.code < 32 || it.code == 127 }

fun sessionError(draft: SessionDraft): String? {
    if (!visibleName(draft.type, 80)) return "Enter a workout name (up to 80 characters)."
    if (draft.notes.length > 4000) return "Workout notes are too long."
    if (draft.exercises.isEmpty()) return "Add an exercise before saving."
    if (draft.exercises.size > 100) return "A workout can contain up to 100 exercises."
    draft.exercises.forEach { exercise ->
        if (!visibleName(exercise.name, 120)) return "Check the exercise name."
        if (exercise.sets.isEmpty() || exercise.sets.size > 200) return "Check the number of sets."
        exercise.sets.forEach { set ->
            for ((label, value) in
                listOf("Weight" to set.weight, "Reps" to set.reps, "RPE" to set.rpe)) {
                if (value.isBlank()) continue
                val number = value.toDoubleOrNull()
                if (
                    number == null ||
                        !number.isFinite() ||
                        number < 0 ||
                        number > (if (label == "RPE") 10.0 else 10000.0)
                )
                    return "$label must be a valid number within range."
                if (label == "Reps" && number % 1.0 != 0.0) return "Reps must be a whole number."
                if (label == "RPE" && number < 1) return "RPE must be between 1 and 10, or empty."
            }
        }
    }
    if (
        draft.exercises.none { e ->
            e.sets.any { it.reps.toDoubleOrNull()?.let { n -> n > 0 } == true }
        }
    )
        return "Enter repetitions for at least one set."
    return null
}

fun elapsedMillis(draft: SessionDraft, wallNow: Long, elapsedNow: Long, bootCount: Int): Long {
    val delta =
        if (bootCount >= 0 && bootCount == draft.bootCount && elapsedNow >= draft.startedElapsed)
            elapsedNow - draft.startedElapsed
        else wallNow - draft.startedAt
    return delta.coerceIn(0, 24 * 60 * 60 * 1000L)
}

fun newSession(wallNow: Long, elapsedNow: Long, bootCount: Int, zone: ZoneId): SessionDraft {
    val dateTime = Instant.ofEpochMilli(wallNow).atZone(zone)
    return SessionDraft(
        date = dateTime.toLocalDate().toString(),
        startedAt = wallNow,
        startedElapsed = elapsedNow,
        bootCount = bootCount,
        startTime = dateTime.format(DateTimeFormatter.ofPattern("HH:mm")),
    )
}

/** Native-only row keys/timing fields never enter the canonical workout payload. */
fun workoutPayload(
    draft: SessionDraft,
    wallNow: Long,
    durationMinutes: Long,
    zone: ZoneId,
): JsonObject = buildJsonObject {
    put("id", draft.id)
    put("date", draft.date)
    put("type", draft.type.trim())
    put("startTime", draft.startTime)
    put(
        "endTime",
        Instant.ofEpochMilli(wallNow).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm")),
    )
    put("durationMinutes", durationMinutes)
    put("notes", draft.notes)
    put("tags", JsonArray(emptyList()))
    put("createdAt", Instant.ofEpochMilli(draft.startedAt).toString())
    put(
        "exercises",
        buildJsonArray {
            draft.exercises.forEach { exercise ->
                add(
                    buildJsonObject {
                        put("name", exercise.name.trim())
                        put("notes", exercise.notes)
                        put(
                            "sets",
                            buildJsonArray {
                                exercise.sets.forEach { set ->
                                    add(
                                        buildJsonObject {
                                            put("weight", set.weight)
                                            put("reps", set.reps)
                                            put("rpe", set.rpe)
                                            put("done", set.done)
                                            put("warmup", set.warmup)
                                        }
                                    )
                                }
                            },
                        )
                    }
                )
            }
        },
    )
}
