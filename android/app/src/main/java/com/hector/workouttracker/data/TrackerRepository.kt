package com.hector.workouttracker.data

import androidx.room.withTransaction
import com.hector.workouttracker.core.backup.BackupCodec
import com.hector.workouttracker.core.database.*
import com.hector.workouttracker.core.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

class WriteConflict :
    IllegalStateException(
        "Saved data changed. Reopen the app before continuing; your visible values have not been discarded."
    )

class TrackerRepository(private val db: TrackerDatabase) {
    private val dao = db.trackerDao()
    val workouts = dao.observeRecords("workout")

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
            // One atomic transaction: History insert and draft consumption. Repeat finish cannot
            // duplicate.
            dao.insertRecord(
                RecordEntity("workout", typedIdKey(payload.getValue("id")), payload.toString())
            )
            if (dao.deleteDraft(draft.id, expected) != 1) throw WriteConflict()
        }

    suspend fun discard(id: String, expected: Long) {
        if (dao.deleteDraft(id, expected) != 1) throw WriteConflict()
    }
}
