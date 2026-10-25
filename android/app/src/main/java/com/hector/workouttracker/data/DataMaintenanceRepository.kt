package com.hector.workouttracker.data

import androidx.room.withTransaction
import com.hector.workouttracker.core.backup.BackupCodec
import com.hector.workouttracker.core.catalog.StarterLibrary
import com.hector.workouttracker.core.database.*
import com.hector.workouttracker.core.model.*
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

enum class DataAction {
    RESET_SETTINGS,
    CLEAR_DATA,
}

class DataReviewChanged :
    IllegalStateException(
        "Local data changed after this review. Nothing was reset or cleared. Review the action again."
    )

internal data class DataBasis(
    val records: List<RecordEntity>,
    val documents: List<DocumentEntity>,
    val draft: DraftEntity?,
)

class DataActionReview
internal constructor(
    val action: DataAction,
    val displayName: String,
    internal val basis: DataBasis,
) {
    val workouts = basis.records.count { it.kind == "workout" }
    val routines = basis.records.count { it.kind == "routine" }
    val legacyWeights = basis.records.count { it.kind == "legacy-weight" }
    val hasDraft = basis.draft != null
}

data class DataActionResult(val profile: Profile, val profileRevision: Long, val epoch: String)

/** Reviewed, revision-fenced maintenance. Failed writes leave the entire snapshot intact. */
class DataMaintenanceRepository(private val db: TrackerDatabase) {
    private val dao = db.trackerDao()

    private fun epoch(document: DocumentEntity?) =
        document?.let {
            trackerJson
                .parseToJsonElement(it.payload)
                .jsonObject
                .getValue("id")
                .jsonPrimitive
                .content
        } ?: "initial"

    val epochs: Flow<String> =
        dao.observeDocument("native_data_epoch").map(::epoch).flowOn(Dispatchers.Default)

    suspend fun currentEpoch(): String = epoch(dao.document("native_data_epoch"))

    private suspend fun snapshot(action: DataAction): DataBasis =
        when (action) {
            DataAction.RESET_SETTINGS ->
                DataBasis(
                    emptyList(),
                    dao.documents().filter {
                        it.name in setOf("settings", "native_profile", "native_data_epoch")
                    },
                    null,
                )
            DataAction.CLEAR_DATA -> DataBasis(dao.records(), dao.documents(), dao.draft())
        }

    suspend fun review(action: DataAction): DataActionReview =
        db.withTransaction {
            val name =
                if (action == DataAction.RESET_SETTINGS)
                    PreferencesRepository(db).read().values.displayName
                else ""
            DataActionReview(action, name, snapshot(action))
        }

    suspend fun confirm(
        review: DataActionReview,
        starters: StarterLibrary? = null,
        now: String = Instant.now().toString(),
    ): DataActionResult =
        db.withTransaction {
            val before = snapshot(review.action)
            if (before != review.basis) throw DataReviewChanged()
            if (review.action == DataAction.RESET_SETTINGS) {
                val settings =
                    JsonObject(
                        BackupCodec.defaultSettings +
                            ("displayName" to
                                if (review.displayName.isEmpty()) JsonNull
                                else JsonPrimitive(review.displayName))
                    )
                val profile =
                    Profile(review.displayName, settings.getValue("rpeAware").jsonPrimitive.boolean)
                suspend fun put(name: String, payload: String): Long {
                    val old = before.documents.find { it.name == name }
                    if (old == null) {
                        dao.insertDocument(DocumentEntity(name, payload))
                        return 1L
                    }
                    val next = Math.addExact(old.revision, 1L)
                    if (dao.updateDocument(name, old.revision, payload) != 1) throw WriteConflict()
                    return next
                }
                put("settings", settings.toString())
                val revision = put("native_profile", trackerJson.encodeToString(profile))
                DataActionResult(profile, revision, currentEpoch())
            } else {
                // Validate assets before deleting anything, and keep revisions beyond old editor
                // fences.
                val library =
                    requireNotNull(starters) {
                        "Starter routines are unavailable. No data was cleared."
                    }
                val maximum =
                    (before.records.map { it.revision } +
                            before.documents.map { it.revision } +
                            listOfNotNull(before.draft?.revision))
                        .maxOrNull() ?: 0L
                check(maximum < Long.MAX_VALUE) {
                    "Local revisions cannot be advanced. No data was cleared."
                }
                val revision = maximum + 1
                val id = UUID.randomUUID().toString()
                val profile = Profile()
                dao.clearDrafts()
                dao.clearRecords()
                dao.clearDocuments()
                RoutineRepository(db).seedStarters(library, now, revision)
                for ((name, payload) in
                    listOf(
                        "settings" to BackupCodec.defaultSettings.toString(),
                        "goals" to buildJsonObject { put("weeklyGoal", 4) }.toString(),
                        "native_profile" to trackerJson.encodeToString(profile),
                        "native_data_epoch" to buildJsonObject { put("id", id) }.toString(),
                    )) dao.insertDocument(DocumentEntity(name, payload, revision))
                DataActionResult(profile, revision, id)
            }
        }
}
