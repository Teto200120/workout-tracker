package com.hector.workouttracker.data

import androidx.room.withTransaction
import com.hector.workouttracker.core.backup.BackupCodec
import com.hector.workouttracker.core.database.*
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.core.schedule.*
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

private fun settingsFingerprint(record: DocumentEntity): String =
    MessageDigest.getInstance("SHA-256")
        .digest(record.payload.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

@Serializable
data class ScheduleDraft(
    val days: List<ScheduleDay>,
    val originalDays: List<ScheduleDay>,
    val revision: Long,
    val fingerprint: String?,
) {
    val changed: Boolean
        get() = days != originalDays

    fun restorationState(): String? {
        if ((days + originalDays).sumOf { it.routine.length.toLong() } > 64 * 1024) return null
        return trackerJson.encodeToString(this).takeIf {
            it.toByteArray(Charsets.UTF_8).size <= 192 * 1024
        }
    }
}

class ScheduleRepository(private val db: TrackerDatabase) {
    private val dao = db.trackerDao()
    val schedule =
        dao.observeDocument("settings")
            .map { record ->
                scheduleDays(
                    record?.let { trackerJson.parseToJsonElement(it.payload).jsonObject }
                        ?: BackupCodec.defaultSettings
                )
            }
            .flowOn(Dispatchers.Default)

    private fun snapshot(record: DocumentEntity?): ScheduleDraft {
        val days =
            scheduleDays(
                record?.let { trackerJson.parseToJsonElement(it.payload).jsonObject }
                    ?: BackupCodec.defaultSettings
            )
        return ScheduleDraft(days, days, record?.revision ?: 0, record?.let(::settingsFingerprint))
    }

    suspend fun read(): ScheduleDraft = db.withTransaction { snapshot(dao.document("settings")) }

    suspend fun save(draft: ScheduleDraft): ScheduleDraft =
        db.withTransaction {
            val current = dao.document("settings")
            if (
                (current?.revision ?: 0) != draft.revision ||
                    current?.let(::settingsFingerprint) != draft.fingerprint
            )
                throw WriteConflict()
            require(draft.days.size == 7 && draft.originalDays.size == 7)
            draft.days.forEachIndexed { index, day ->
                require(day.kind in scheduleKinds)
                require(
                    day.routine == draft.originalDays[index].routine || visibleName(day.routine, 80)
                ) {
                    "Choose a saved routine (up to 80 characters)."
                }
            }
            if (!draft.changed) return@withTransaction snapshot(current)
            val profile =
                dao.document("native_profile")?.let {
                    trackerJson.decodeFromString<Profile>(it.payload)
                } ?: Profile()
            val original =
                current?.let { trackerJson.parseToJsonElement(it.payload).jsonObject }
                    ?: JsonObject(
                        BackupCodec.defaultSettings +
                            mapOf(
                                "displayName" to
                                    if (profile.displayName.isEmpty()) JsonNull
                                    else JsonPrimitive(profile.displayName),
                                "rpeAware" to JsonPrimitive(profile.trackRpe),
                            )
                    )
            val oldSchedule = original.getValue("schedule").jsonObject
            val schedule =
                JsonObject(
                    oldSchedule +
                        draft.days
                            .mapIndexed { index, day ->
                                val key = index.toString()
                                key to
                                    JsonObject(
                                        oldSchedule.getValue(key).jsonObject +
                                            mapOf(
                                                "kind" to JsonPrimitive(day.kind),
                                                "routine" to JsonPrimitive(day.routine),
                                            )
                                    )
                            }
                            .toMap()
                )
            val payload = JsonObject(original + ("schedule" to schedule)).toString()
            if (current == null) dao.insertDocument(DocumentEntity("settings", payload))
            else if (dao.updateDocument("settings", current.revision, payload) != 1)
                throw WriteConflict()
            snapshot(requireNotNull(dao.document("settings")))
        }
}
