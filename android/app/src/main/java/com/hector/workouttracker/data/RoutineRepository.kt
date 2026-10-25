package com.hector.workouttracker.data

import androidx.room.withTransaction
import com.hector.workouttracker.core.catalog.StarterLibrary
import com.hector.workouttracker.core.database.*
import com.hector.workouttracker.core.model.*
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

private val routineWhitespace = Regex("[\\s\\p{Z}\\uFEFF]+")

fun routineText(value: String): String =
    value.replace(Regex("^[\\s\\p{Z}\\uFEFF]+|[\\s\\p{Z}\\uFEFF]+$"), "")

fun routineNameKey(value: String): String =
    routineText(value).replace(routineWhitespace, " ").lowercase(Locale.US)

/** Editor state is separate from the stored record. Opening never normalizes saved values. */
@Serializable
data class RoutineDraft(
    val id: JsonElement,
    val name: String = "",
    val exercises: List<String> = emptyList(),
    val originalName: String? = null,
    val originalExercises: List<String> = emptyList(),
    val originalFingerprint: String? = null,
    val originalRevision: Long = 0,
) {
    fun restorationState(): String? =
        trackerJson.encodeToString(this).takeIf {
            it.toByteArray(Charsets.UTF_8).size <= 192 * 1024
        }

    val changed: Boolean
        get() =
            originalName?.let { name != it || exercises != originalExercises }
                ?: (name.isNotEmpty() || exercises.isNotEmpty())

    fun duplicate(name: String, replacing: Int = -1): Boolean =
        exercises.withIndex().any {
            it.index != replacing && routineNameKey(it.value) == routineNameKey(name)
        }

    fun select(name: String, replacing: Int = -1): RoutineDraft {
        val selected = routineText(name)
        require(visibleName(selected, 120)) { "Enter an exercise name (up to 120 characters)." }
        require(replacing == -1 || replacing in exercises.indices)
        require(replacing != -1 || exercises.size < 100) {
            "A routine can contain up to 100 exercises."
        }
        return copy(
            exercises =
                if (replacing == -1) exercises + selected
                else exercises.mapIndexed { i, old -> if (i == replacing) selected else old }
        )
    }

    companion object {
        fun create() = RoutineDraft(JsonPrimitive(UUID.randomUUID().toString()))

        fun edit(routine: SavedRoutine) =
            RoutineDraft(
                routine.id,
                routine.name,
                routine.exercises,
                routine.name,
                routine.exercises,
                routineFingerprint(routine.record),
                routine.record.revision,
            )
    }
}

private fun routineFingerprint(record: RecordEntity): String =
    MessageDigest.getInstance("SHA-256")
        .digest(record.payload.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

/** Name/capacity checks and revision fencing share the same transaction as the mutation. */
class RoutineRepository(private val db: TrackerDatabase) {
    private val dao = db.trackerDao()

    suspend fun read(id: JsonElement): SavedRoutine? =
        dao.record("routine", typedIdKey(id))?.let(::routineSnapshot)

    /** Explicit empty-library seeding. Never replace an imported or user-owned record. */
    suspend fun seedStarters(
        library: StarterLibrary,
        now: String = Instant.now().toString(),
        initialRevision: Long = 1,
    ): Int =
        db.withTransaction {
            require(initialRevision > 0)
            check(dao.recordsOfKind("routine").isEmpty()) {
                "Your routine library changed. Existing routines were kept. Go back to review them."
            }
            library.routines.forEach { routine ->
                val id = JsonPrimitive(routine.id)
                val payload = buildJsonObject {
                    put("id", id)
                    put("name", routine.name)
                    put("exercises", JsonArray(routine.exercises.map(::JsonPrimitive)))
                    put("createdAt", now)
                    put("updatedAt", now)
                }
                dao.insertRecord(
                    RecordEntity("routine", typedIdKey(id), payload.toString(), initialRevision)
                )
            }
            library.routines.size
        }

    suspend fun save(draft: RoutineDraft, now: String = Instant.now().toString()): SavedRoutine =
        db.withTransaction {
            val key = typedIdKey(draft.id)
            val current = dao.record("routine", key)
            if (current == null) {
                if (draft.originalFingerprint != null) throw WriteConflict()
            } else if (
                draft.originalFingerprint == null ||
                    current.revision != draft.originalRevision ||
                    routineFingerprint(current) != draft.originalFingerprint
            )
                throw WriteConflict()
            val name = routineText(draft.name)
            require(visibleName(name, 80)) { "Enter a routine name (up to 80 characters)." }
            require(draft.exercises.size <= 100 && draft.exercises.all { visibleName(it, 120) }) {
                "Check the routine's exercise names and 100-exercise limit."
            }
            val routines = dao.recordsOfKind("routine")
            check(
                routines.none {
                    it.idKey != key &&
                        routineNameKey(routineSnapshot(it).name) == routineNameKey(name)
                }
            ) {
                "Another routine already uses this name."
            }
            check(current != null || routines.size < 500) {
                "The app is limited to 500 saved routines."
            }
            val old = current?.let { trackerJson.parseToJsonElement(it.payload).jsonObject }
            if (
                old != null &&
                    old["name"] == JsonPrimitive(name) &&
                    old["exercises"] == JsonArray(draft.exercises.map(::JsonPrimitive))
            )
                return@withTransaction routineSnapshot(current)
            val payload =
                JsonObject(
                    (old ?: mapOf("id" to draft.id, "createdAt" to JsonPrimitive(now))) +
                        mapOf(
                            "name" to JsonPrimitive(name),
                            "exercises" to JsonArray(draft.exercises.map(::JsonPrimitive)),
                            "updatedAt" to JsonPrimitive(now),
                        )
                )
            val record =
                RecordEntity("routine", key, payload.toString(), (current?.revision ?: 0) + 1)
            if (current == null) dao.insertRecord(record)
            else if (dao.updateRecord("routine", key, current.revision, record.payload) != 1)
                throw WriteConflict()
            routineSnapshot(record)
        }

    suspend fun delete(selected: SavedRoutine) =
        db.withTransaction {
            if (dao.record("routine", selected.record.idKey) != selected.record)
                throw WriteConflict()
            if (dao.deleteRecord("routine", selected.record.idKey, selected.record.revision) != 1)
                throw WriteConflict()
            // History, schedule name references and any already-started draft remain untouched.
        }
}
