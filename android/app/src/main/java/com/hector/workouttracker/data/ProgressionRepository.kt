package com.hector.workouttracker.data

import androidx.room.withTransaction
import com.hector.workouttracker.core.backup.BackupCodec
import com.hector.workouttracker.core.database.*
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.domain.*
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

data class TrainingSnapshot(
    val basis: String,
    val workouts: List<JsonObject>,
    val settings: JsonObject,
) {
    fun guidance(name: String) = trainingGuidance(name, workouts, settings)

    private val ordered by lazy { interchangeOrder(workouts) }
    private val lastByName by lazy {
        buildMap<String, LastTraining> {
            for (workout in sortedWorkouts(ordered)) {
                for (row in (workout["exercises"] as? JsonArray).orEmpty()) {
                    val exercise = row.jsonObject
                    val key = trainingName(exercise.getValue("name").jsonPrimitive.content)
                    if (key !in this)
                        put(
                            key,
                            LastTraining(
                                workout.getValue("id"),
                                workout.getValue("date").jsonPrimitive.content,
                                workout.getValue("type").jsonPrimitive.content,
                                (exercise["sets"] as? JsonArray).orEmpty().map { it.jsonObject },
                            ),
                        )
                }
            }
        }
    }

    fun lastSets(name: String): List<JsonObject> = lastByName[trainingName(name)]?.sets.orEmpty()

    /**
     * One history scan for all visible exercises, preserving the original typed-ID PR tie order.
     */
    fun guidance(names: List<String>): Map<String, TrainingGuidance> {
        val selected = names.distinct()
        if (selected.size <= 1) return selected.associateWith(::guidance)
        val keys = selected.map(::trainingName).toSet()
        val records = mutableMapOf<String, RecordSet>()
        for (workout in ordered) {
            for (row in (workout["exercises"] as? JsonArray).orEmpty()) {
                val exercise = row.jsonObject
                val key = trainingName(exercise.getValue("name").jsonPrimitive.content)
                if (key !in keys) continue
                val best = exerciseRecords(exercise).estimated1rm ?: continue
                if (records[key]?.let { best.estimated1rm > it.estimated1rm } != false)
                    records[key] = best.copy(date = workout.getValue("date").jsonPrimitive.content)
            }
        }
        return selected.associateWith { name ->
            val key = trainingName(name)
            val last = lastByName[key]
            TrainingGuidance(
                name,
                trainingProfile(name, settings),
                last,
                records[key],
                trainingTarget(name, last?.sets.orEmpty(), settings),
            )
        }
    }
}

data class SetCopyRequest(
    val draft: SessionDraft,
    val exercise: Exercise,
    val basis: String,
    val target: Boolean,
) {
    val replacesInput: Boolean
        get() =
            exercise.sets.any {
                it.weight.isNotBlank() ||
                    it.reps.isNotBlank() ||
                    it.rpe.isNotBlank() ||
                    it.done ||
                    it.warmup
            }
}

class ProgressionConflict :
    IllegalStateException(
        "History, preferences or this exercise changed. Your sets were kept. Review the latest guidance and choose again."
    )

internal suspend fun trainingSnapshot(
    dao: TrackerDao,
    workoutHistory: WorkoutHistoryCache,
): TrainingSnapshot {
    val records = dao.recordsOfKind("workout")
    val settings = dao.document("settings")
    val profile = if (settings == null) dao.document("native_profile") else null
    val digest = MessageDigest.getInstance("SHA-256")
    fun add(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        digest.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
        digest.update(bytes)
    }
    records.forEach {
        add(it.idKey)
        add(it.revision.toString())
        add(it.payload)
    }
    for (doc in listOf(settings, profile)) {
        add(doc?.revision?.toString() ?: "absent")
        add(doc?.payload ?: "")
    }
    val canonical =
        settings?.let { trackerJson.parseToJsonElement(it.payload).jsonObject }
            ?: JsonObject(
                BackupCodec.defaultSettings +
                    ("rpeAware" to
                        JsonPrimitive(
                            profile?.let {
                                trackerJson.decodeFromString<Profile>(it.payload).trackRpe
                            } ?: true
                        ))
            )
    return TrainingSnapshot(
        digest.digest().joinToString("") { "%02x".format(it) },
        workoutHistory.read(records),
        canonical,
    )
}

/**
 * Copies known entry values with fresh row keys. History and its unknown extensions stay intact.
 */
fun copiedTrainingSets(sets: List<JsonObject>): List<WorkingSet> {
    require(sets.size in 1..200) { "No previous sets are available to load." }
    val values =
        sets.map { set ->
            fun text(key: String) = trainingScalar(set[key])
            WorkingSet(
                weight = text("weight"),
                reps = text("reps"),
                rpe = text("rpe"),
                warmup = (set["warmup"] as? JsonPrimitive)?.booleanOrNull == true,
                historicalRpeZero = text("rpe").toDoubleOrNull() == 0.0,
            )
        }
    values.forEach { set ->
        require(setEntryError(set) == null) { setEntryError(set) ?: "Check copied sets." }
    }
    // Validate the new entry values against the actual interchange contract too (precision/bounds).
    val validationDraft =
        SessionDraft(
            date = "2000-01-01",
            startedAt = 946684800000,
            startedElapsed = 0,
            bootCount = 0,
            startTime = "00:00",
            exercises = listOf(Exercise(name = "Copy check", sets = values)),
        )
    val payload = workoutPayload(validationDraft, 946684860000, 1, java.time.ZoneId.of("UTC"))
    BackupCodec.encode(
        com.hector.workouttracker.core.backup.BackupData(
            3,
            2,
            listOf(payload),
            emptyList(),
            emptyList(),
            null,
            null,
            null,
        ),
        "2000-01-01T00:01:00Z",
    )
    return values
}

/** Suggestions are derived off-thread; one transaction fences source and destination on apply. */
class ProgressionRepository(
    private val db: TrackerDatabase,
    private val workoutHistory: WorkoutHistoryCache = WorkoutHistoryCache(),
) {
    private val dao = db.trackerDao()

    suspend fun read(): TrainingSnapshot =
        withContext(Dispatchers.Default) {
            db.withTransaction { trainingSnapshot(dao, workoutHistory) }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(
        names: Flow<List<String>>
    ): Flow<Pair<TrainingSnapshot, Map<String, TrainingGuidance>>> =
        names.flatMapLatest { selected ->
            if (selected.isEmpty())
                flowOf(
                    TrainingSnapshot("", emptyList(), BackupCodec.defaultSettings) to
                        emptyMap<String, TrainingGuidance>()
                )
            else
                combine(
                        dao.observeRecords("workout").distinctUntilChanged(),
                        dao.observeDocument("settings").distinctUntilChanged(),
                        dao.observeDocument("native_profile").distinctUntilChanged(),
                    ) { _, _, _ ->
                        Unit
                    }
                    .mapLatest {
                        withContext(Dispatchers.Default) {
                            val source = read()
                            source to source.guidance(selected)
                        }
                    }
        }

    suspend fun apply(request: SetCopyRequest, expected: Long): SessionDraft =
        withContext(Dispatchers.Default) {
            db.withTransaction {
                val current = dao.draft() ?: throw ProgressionConflict()
                if (current.sessionId != request.draft.id || current.revision != expected)
                    throw ProgressionConflict()
                val draft = trackerJson.decodeFromString<SessionDraft>(current.payload)
                if (
                    draft != request.draft ||
                        draft.exercises.find { it.key == request.exercise.key } != request.exercise
                )
                    throw ProgressionConflict()
                val source = trainingSnapshot(dao, workoutHistory)
                if (source.basis != request.basis) throw ProgressionConflict()
                val guide = source.guidance(request.exercise.name)
                val rows =
                    if (request.target) guide.target.targetSets else guide.last?.sets.orEmpty()
                val copied = copiedTrainingSets(rows)
                val changed =
                    draft.copy(
                        exercises =
                            draft.exercises.map {
                                if (it.key == request.exercise.key) it.copy(sets = copied) else it
                            }
                    )
                if (dao.updateDraft(changed.id, expected, trackerJson.encodeToString(changed)) != 1)
                    throw ProgressionConflict()
                changed
            }
        }
}
