package com.hector.workouttracker.data

import androidx.room.withTransaction
import com.hector.workouttracker.core.backup.BackupCodec
import com.hector.workouttracker.core.database.*
import com.hector.workouttracker.core.model.*
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

@Serializable
data class HistorySet(
    val key: String = UUID.randomUUID().toString(),
    val weight: String = "",
    val reps: String = "",
    val rpe: String = "",
    val done: Boolean = false,
    val warmup: Boolean = false,
)

@Serializable
data class HistoryExercise(
    val key: String = UUID.randomUUID().toString(),
    val name: String,
    val notes: String = "",
    val sets: List<HistorySet> = listOf(HistorySet()),
)

@Serializable
data class HistoryValues(
    val date: String,
    val type: String,
    val startTime: String,
    val endTime: String,
    val duration: String,
    val notes: String,
    val tags: List<String>,
    val exercises: List<HistoryExercise>,
)

/** Only editable values enter saved-instance state. Unknown imported JSON stays in Room. */
@Serializable
data class HistoryDraft(
    val id: JsonElement,
    val revision: Long,
    val fingerprint: String,
    val original: HistoryValues,
    val values: HistoryValues = original,
) {
    val changed: Boolean
        get() = values != original

    fun restorationState(): String? {
        // Avoid serializing huge compatibility strings merely to reject the resulting Bundle.
        fun size(v: HistoryValues): Long =
            v.date.length.toLong() +
                v.type.length +
                v.startTime.length +
                v.endTime.length +
                v.duration.length +
                v.notes.length +
                v.tags.sumOf { it.length.toLong() } +
                v.exercises.sumOf { e ->
                    e.key.length.toLong() +
                        e.name.length +
                        e.notes.length +
                        e.sets.sumOf {
                            it.key.length.toLong() +
                                it.weight.length +
                                it.reps.length +
                                it.rpe.length +
                                80
                        }
                }
        if (id.toString().length.toLong() + size(original) + size(values) > 192 * 1024) return null
        return trackerJson.encodeToString(this).takeIf {
            it.toByteArray(Charsets.UTF_8).size <= 192 * 1024
        }
    }
}

private fun historyFingerprint(record: RecordEntity): String =
    MessageDigest.getInstance("SHA-256")
        .digest(record.payload.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

private fun JsonObject.text(key: String) =
    get(key)?.takeUnless { it is JsonNull }?.jsonPrimitive?.content.orEmpty()

fun historyDraft(record: RecordEntity): HistoryDraft {
    require(record.kind == "workout")
    val w = trackerJson.parseToJsonElement(record.payload).jsonObject
    val id = w.getValue("id")
    require(typedIdKey(id) == record.idKey)
    val values =
        HistoryValues(
            w.text("date"),
            w.text("type"),
            w.text("startTime"),
            w.text("endTime"),
            w.text("durationMinutes"),
            w.text("notes"),
            w.getValue("tags").jsonArray.map { it.jsonPrimitive.content },
            w.getValue("exercises").jsonArray.mapIndexed { i, e ->
                val row = e.jsonObject
                HistoryExercise(
                    "original:$i",
                    row.text("name"),
                    row.text("notes"),
                    row.getValue("sets").jsonArray.mapIndexed { j, s ->
                        val set = s.jsonObject
                        HistorySet(
                            "original:$i:$j",
                            set.text("weight"),
                            set.text("reps"),
                            set.text("rpe"),
                            set.getValue("done").jsonPrimitive.boolean,
                            set.getValue("warmup").jsonPrimitive.boolean,
                        )
                    },
                )
            },
        )
    return HistoryDraft(id, record.revision, historyFingerprint(record), values)
}

/** Revision and payload fencing also cover imported replacements and delete/reinsert ABA. */
class HistoryRepository(private val db: TrackerDatabase) {
    private val dao = db.trackerDao()

    suspend fun read(id: JsonElement): HistoryDraft? =
        dao.record("workout", typedIdKey(id))?.let(::historyDraft)

    private suspend fun current(draft: HistoryDraft): RecordEntity {
        val row = dao.record("workout", typedIdKey(draft.id)) ?: throw WriteConflict()
        if (row.revision != draft.revision || historyFingerprint(row) != draft.fingerprint)
            throw WriteConflict()
        // Never trust a restored baseline to select fields from another original record.
        if (historyDraft(row).original != draft.original) throw WriteConflict()
        return row
    }

    suspend fun save(draft: HistoryDraft): HistoryDraft =
        db.withTransaction {
            val row = current(draft)
            if (!draft.changed) return@withTransaction historyDraft(row)
            val source = trackerJson.parseToJsonElement(row.payload).jsonObject
            val old = draft.original
            val value = draft.values
            val result = source.toMutableMap()
            fun putText(key: String, before: String, after: String) {
                if (before != after) result[key] = JsonPrimitive(after)
            }
            putText("date", old.date, value.date)
            putText("type", old.type, value.type)
            putText("startTime", old.startTime, value.startTime)
            putText("endTime", old.endTime, value.endTime)
            putText("notes", old.notes, value.notes)
            if (old.duration != value.duration) {
                if (value.duration.isEmpty()) result.remove("durationMinutes")
                else {
                    val minutes = value.duration.toDoubleOrNull()
                    require(minutes != null && minutes.isFinite() && minutes in 0.0..1440.0) {
                        "Duration must be 0–1440 minutes, or empty to use the start/end times."
                    }
                    result["durationMinutes"] = JsonPrimitive(minutes)
                }
            }
            if (old.tags != value.tags) result["tags"] = JsonArray(value.tags.map(::JsonPrimitive))
            require(value.exercises.map { it.key }.distinct().size == value.exercises.size) {
                "Duplicate exercise row."
            }
            val originals = old.exercises.withIndex().associateBy { it.value.key }
            result["exercises"] =
                JsonArray(
                    value.exercises.map { exercise ->
                        val original = originals[exercise.key]
                        require(!exercise.key.startsWith("original:") || original != null) {
                            "Invalid exercise row."
                        }
                        val base =
                            original?.let {
                                source.getValue("exercises").jsonArray[it.index].jsonObject
                            }
                        val fields = (base ?: JsonObject(emptyMap())).toMutableMap()
                        val previous = original?.value
                        if (previous?.name != exercise.name)
                            fields["name"] = JsonPrimitive(exercise.name)
                        if (previous?.notes != exercise.notes)
                            fields["notes"] = JsonPrimitive(exercise.notes)
                        require(
                            exercise.sets.map { it.key }.distinct().size == exercise.sets.size
                        ) {
                            "Duplicate set row."
                        }
                        val sets =
                            previous?.sets?.withIndex()?.associateBy { it.value.key }.orEmpty()
                        fields["sets"] =
                            JsonArray(
                                exercise.sets.map { set ->
                                    val originalSet = sets[set.key]
                                    require(
                                        !set.key.startsWith("original:") || originalSet != null
                                    ) {
                                        "Invalid set row."
                                    }
                                    val s =
                                        originalSet?.let {
                                            base!!.getValue("sets").jsonArray[it.index].jsonObject
                                        }
                                    val changed = (s ?: JsonObject(emptyMap())).toMutableMap()
                                    val before = originalSet?.value
                                    if (before?.weight != set.weight)
                                        changed["weight"] = JsonPrimitive(set.weight)
                                    if (before?.reps != set.reps)
                                        changed["reps"] = JsonPrimitive(set.reps)
                                    if (before?.rpe != set.rpe)
                                        changed["rpe"] = JsonPrimitive(set.rpe)
                                    if (before?.done != set.done)
                                        changed["done"] = JsonPrimitive(set.done)
                                    if (before?.warmup != set.warmup)
                                        changed["warmup"] = JsonPrimitive(set.warmup)
                                    JsonObject(changed)
                                }
                            )
                        JsonObject(fields)
                    }
                )
            val payload = JsonObject(result)
            // Reuse the canonical import bounds, including dates, numeric precision and extension
            // keys.
            BackupCodec.parse(
                buildJsonObject {
                        put("backupFileVersion", 3)
                        put("applicationSchemaVersion", 2)
                        put("workouts", JsonArray(listOf(payload)))
                    }
                    .toString()
            )
            if (payload == source) return@withTransaction historyDraft(row)
            if (dao.updateRecord("workout", row.idKey, row.revision, payload.toString()) != 1)
                throw WriteConflict()
            historyDraft(row.copy(payload = payload.toString(), revision = row.revision + 1))
        }

    suspend fun delete(draft: HistoryDraft) =
        db.withTransaction {
            val row = current(draft)
            if (dao.deleteRecord("workout", row.idKey, row.revision) != 1) throw WriteConflict()
            // A saved workout is independent of the active draft, routines and preferences.
        }
}
