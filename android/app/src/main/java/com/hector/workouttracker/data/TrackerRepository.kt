package com.hector.workouttracker.data

import androidx.room.withTransaction
import com.hector.workouttracker.core.backup.BackupCodec
import com.hector.workouttracker.core.database.*
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.core.schedule.*
import com.hector.workouttracker.domain.*
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

class WriteConflict :
    IllegalStateException(
        "Saved data changed. Reopen the app before continuing; your visible values have not been discarded."
    )

class TrackerRepository(private val db: TrackerDatabase) {
    private val dao = db.trackerDao()
    val profileChanges =
        dao.observeDocument("native_profile")
            .map { document ->
                document?.let { trackerJson.decodeFromString<Profile>(it.payload) to it.revision }
                    ?: (Profile() to 0L)
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
    val workouts = dao.observeRecords("workout")
    val routines = dao.observeRecords("routine")

    suspend fun readDraft(): Pair<SessionDraft, Long>? =
        dao.draft()?.let { trackerJson.decodeFromString<SessionDraft>(it.payload) to it.revision }

    suspend fun readProfile(): Pair<Profile, Long> =
        dao.document("native_profile")?.let {
            trackerJson.decodeFromString<Profile>(it.payload) to it.revision
        } ?: (Profile() to 0L)

    suspend fun saveProfile(profile: Profile, expected: Long): Long =
        db.withTransaction {
            require(visibleName(profile.displayName, 80)) {
                "Enter a display name (up to 80 characters)."
            }
            val payload =
                trackerJson.encodeToString(profile.copy(displayName = profile.displayName.trim()))
            if (expected == 0L) {
                if (dao.document("native_profile") != null) throw WriteConflict()
                dao.insertDocument(DocumentEntity("native_profile", payload))
            } else if (dao.updateDocument("native_profile", expected, payload) != 1)
                throw WriteConflict()
            val settings = dao.document("settings")
            val canonical =
                settings?.let { trackerJson.parseToJsonElement(it.payload).jsonObject }
                    ?: BackupCodec.defaultSettings
            val updated =
                JsonObject(
                        canonical +
                            mapOf(
                                "displayName" to JsonPrimitive(profile.displayName.trim()),
                                "rpeAware" to JsonPrimitive(profile.trackRpe),
                            )
                    )
                    .toString()
            if (settings == null) dao.insertDocument(DocumentEntity("settings", updated))
            else if (
                settings.payload != updated &&
                    dao.updateDocument("settings", settings.revision, updated) != 1
            )
                throw WriteConflict()
            expected + 1
        }

    suspend fun start(draft: SessionDraft): Long =
        db.withTransaction {
            check(dao.draft() == null) { "Resume or discard the current workout first." }
            dao.insertDraft(
                DraftEntity(sessionId = draft.id, payload = trackerJson.encodeToString(draft))
            )
            1L
        }

    /**
     * Selection is fenced against imports; the routine and new draft are read/written atomically.
     */
    suspend fun startRoutine(selected: RecordEntity, empty: SessionDraft): SessionDraft =
        db.withTransaction {
            val current = dao.record("routine", selected.idKey) ?: throw WriteConflict()
            if (selected.kind != "routine" || current != selected) throw WriteConflict()
            val routine = routineSnapshot(current)
            val history = trainingSnapshot(dao)
            val draft =
                empty.copy(
                    type = routine.name,
                    originRoutineId = routine.id,
                    exercises =
                        routine.exercises.mapIndexed { index, name ->
                            val last = history.guidance(name).last?.sets.orEmpty()
                            Exercise(
                                name = name,
                                collapsed = index != 0,
                                sets =
                                    if (last.isEmpty()) listOf(WorkingSet())
                                    else copiedTrainingSets(last),
                            )
                        },
                )
            start(draft)
            draft
        }

    /** Plan and unique exact-name match are rechecked in the same transaction as draft creation. */
    suspend fun startPlannedRoutine(
        selected: RecordEntity,
        expected: ScheduleDay,
        date: LocalDate,
        empty: SessionDraft,
    ): SessionDraft =
        db.withTransaction {
            check(empty.date == date.toString()) {
                "Today changed. Review your plan before starting."
            }
            val settings =
                dao.document("settings")?.let {
                    trackerJson.parseToJsonElement(it.payload).jsonObject
                } ?: BackupCodec.defaultSettings
            val days = scheduleDays(settings)
            val plan = resolveDayPlan(date, days)
            val routine = routineSnapshot(selected)
            if (
                days[date.dayOfWeek.value % 7] != expected ||
                    plan.kind != "gym" ||
                    plan.routine != routine.name ||
                    dao.recordsOfKind("routine").count {
                        routineSnapshot(it).name == plan.routine
                    } != 1
            )
                throw WriteConflict()
            startRoutine(selected, empty)
        }

    suspend fun saveDraft(draft: SessionDraft, expected: Long): Long {
        if (dao.updateDraft(draft.id, expected, trackerJson.encodeToString(draft)) != 1)
            throw WriteConflict()
        return expected + 1
    }

    suspend fun finish(draft: SessionDraft, expected: Long, payload: JsonObject) =
        db.withTransaction {
            require(sessionError(draft) == null) { sessionError(draft) ?: "Check the workout." }
            val current = dao.draft() ?: throw WriteConflict()
            if (current.sessionId != draft.id || current.revision != expected) throw WriteConflict()
            val summary =
                completionSummary(
                    payload,
                    interchangeOrder(
                        dao.recordsOfKind("workout").map {
                            trackerJson.parseToJsonElement(it.payload).jsonObject
                        }
                    ),
                )
            // One atomic transaction: History insert and draft consumption. Repeat finish cannot
            // duplicate.
            dao.insertRecord(
                RecordEntity("workout", typedIdKey(payload.getValue("id")), payload.toString())
            )
            if (dao.deleteDraft(draft.id, expected) != 1) throw WriteConflict()
            summary
        }

    suspend fun discard(id: String, expected: Long) {
        if (dao.deleteDraft(id, expected) != 1) throw WriteConflict()
    }
}

/** No catalog resolution here: imported spellings, duplicate rows and order remain user data. */
data class SavedRoutine(
    val record: RecordEntity,
    val id: JsonElement,
    val name: String,
    val exercises: List<String>,
)

fun routineSnapshot(record: RecordEntity): SavedRoutine {
    require(record.kind == "routine")
    val json = trackerJson.parseToJsonElement(record.payload).jsonObject
    val id = json.getValue("id")
    require(typedIdKey(id) == record.idKey)
    val name =
        json.getValue("name").jsonPrimitive.let {
            require(it.isString)
            it.content
        }
    require(visibleName(name, 80))
    val exercises =
        json.getValue("exercises").jsonArray.map { value ->
            value.jsonPrimitive.let {
                require(it.isString && visibleName(it.content, 120))
                it.content
            }
        }
    require(exercises.size <= 100)
    return SavedRoutine(record, id, name, exercises)
}

fun localExerciseNames(workouts: List<RecordEntity>, routines: List<SavedRoutine>): List<String> =
    (routines.flatMap { it.exercises } +
            workouts.flatMap { record ->
                trackerJson
                    .parseToJsonElement(record.payload)
                    .jsonObject
                    .getValue("exercises")
                    .jsonArray
                    .map { it.jsonObject.getValue("name").jsonPrimitive.content }
            })
        .map { it.trim() }
        .filter { visibleName(it, 120) }
        .distinctBy { it.lowercase(java.util.Locale.US) }
