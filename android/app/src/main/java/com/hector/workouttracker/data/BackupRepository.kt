package com.hector.workouttracker.data

import androidx.room.withTransaction
import com.hector.workouttracker.core.backup.*
import com.hector.workouttracker.core.database.*
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.domain.BackupSnapshot
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

data class ImportCounts(val added: Int, val replaced: Int, val unchanged: Int)

class ImportPreview
internal constructor(
    internal val data: BackupData,
    internal val fingerprint: String,
    val workouts: ImportCounts,
    val routines: ImportCounts,
    val weights: ImportCounts,
    val displayName: String?,
    val trackRpe: Boolean,
    val settingsSupplied: Boolean,
    val goalsSupplied: Boolean,
    val metadataSupplied: Boolean,
    val changedPreferences: List<String>,
    val weeklyGoal: Int,
)

/** Validation happens before this boundary; every merge and document write commits together. */
class BackupRepository(private val db: TrackerDatabase) {
    private val dao = db.trackerDao()

    val status: Flow<BackupSnapshot> =
        combine(dao.observeBackupData(), dao.observeDocument("backup_meta")) { hasData, meta ->
                BackupSnapshot(
                    hasData,
                    meta?.let {
                        trackerJson
                            .parseToJsonElement(it.payload)
                            .jsonObject["lastExportedAt"]
                            ?.takeUnless { value -> value is JsonNull }
                            ?.jsonPrimitive
                            ?.content
                    },
                )
            }
            .flowOn(Dispatchers.Default)

    /**
     * The writer must return only after closing its output. Failed/cancelled writes never refresh
     * reminders.
     */
    suspend fun writeExport(exportedAt: String, write: suspend (String) -> Unit): Boolean {
        val encoded = export(exportedAt)
        write(encoded)
        return try {
            withContext(Dispatchers.IO) {
                db.withTransaction {
                    val before = dao.document("backup_meta")
                    val old =
                        before?.let { trackerJson.parseToJsonElement(it.payload).jsonObject }
                            ?: JsonObject(emptyMap())
                    val next = JsonObject(old + ("lastExportedAt" to JsonPrimitive(exportedAt)))
                    if (before == null)
                        dao.insertDocument(DocumentEntity("backup_meta", next.toString()))
                    else if (
                        next != old &&
                            dao.updateDocument(before.name, before.revision, next.toString()) != 1
                    )
                        throw WriteConflict()
                }
            }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    private data class Snapshot(
        val records: List<RecordEntity>,
        val documents: List<DocumentEntity>,
    )

    private suspend fun snapshot() = Snapshot(dao.records(), dao.documents())

    private fun Snapshot.fingerprint(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun add(s: String) {
            val bytes = s.toByteArray(Charsets.UTF_8)
            digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array())
            digest.update(bytes)
        }
        records.forEach {
            add("record")
            add(it.kind)
            add(it.idKey)
            add(it.revision.toString())
            add(it.payload)
        }
        documents
            .filter { it.name != "native_guidance" }
            .forEach {
                add("document")
                add(it.name)
                add(it.revision.toString())
                add(it.payload)
            }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun Snapshot.document(name: String) =
        documents
            .find { it.name == name }
            ?.let { trackerJson.parseToJsonElement(it.payload).jsonObject }

    private fun Snapshot.profile() =
        document("native_profile")?.let { trackerJson.decodeFromJsonElement<Profile>(it) }
            ?: Profile()

    private fun Snapshot.settings(): JsonObject {
        val profile = profile()
        return JsonObject(
            (document("settings") ?: BackupCodec.defaultSettings) +
                mapOf(
                    "displayName" to
                        if (profile.displayName.isEmpty()) JsonNull
                        else JsonPrimitive(profile.displayName),
                    "rpeAware" to JsonPrimitive(profile.trackRpe),
                )
        )
    }

    private fun Snapshot.importSettings(data: BackupData) =
        data.settings ?: JsonObject(settings() + ("displayName" to JsonNull))

    suspend fun preview(data: BackupData): ImportPreview =
        db.withTransaction {
            check(dao.draft() == null) {
                "Finish or discard your active workout before importing a backup."
            }
            val current = snapshot()
            val index = current.records.associateBy { it.kind to it.idKey }
            fun counts(kind: String, incoming: List<JsonObject>): ImportCounts {
                var added = 0
                var replaced = 0
                var unchanged = 0
                incoming.forEach { payload ->
                    val existing = index[kind to typedIdKey(payload.getValue("id"))]
                    if (existing == null) added++
                    else if (trackerJson.parseToJsonElement(existing.payload) == payload)
                        unchanged++
                    else replaced++
                }
                return ImportCounts(added, replaced, unchanged)
            }
            val settings = current.importSettings(data)
            val routines = counts("routine", data.routines)
            check(current.records.count { it.kind == "routine" } + routines.added <= 500) {
                "The merged collection would exceed 500 routines. No data was imported."
            }
            val beforeSettings = current.settings()
            val known =
                mapOf(
                    "displayName" to "name",
                    "rpeAware" to "RPE tracking",
                    "schedule" to "schedule",
                    "defaultWeightJump" to "default load jump",
                    "haptics" to "haptics",
                    "animations" to "animations",
                ) +
                    listOf("compound", "pull", "isolation", "general")
                        .flatMap { prefix ->
                            listOf(
                                "${prefix}Min" to "progression rep ranges",
                                "${prefix}Max" to "progression rep ranges",
                            )
                        }
                        .toMap()
            val changes =
                (beforeSettings.keys + settings.keys)
                    .filter { beforeSettings[it] != settings[it] }
                    .map { known[it] ?: "other saved preferences" }
                    .distinct()
            val goal =
                (data.goals ?: current.document("goals"))
                    ?.get("weeklyGoal")
                    ?.jsonPrimitive
                    ?.double
                    ?.toInt() ?: 4
            ImportPreview(
                data,
                current.fingerprint(),
                counts("workout", data.workouts),
                routines,
                counts("legacy-weight", data.weights),
                settings["displayName"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content,
                settings.getValue("rpeAware").jsonPrimitive.boolean,
                data.settings != null,
                data.goals != null,
                data.metadata != null,
                changes,
                goal,
            )
        }

    private suspend fun putDocument(name: String, value: JsonObject) {
        val before = dao.document(name)
        if (before == null) dao.insertDocument(DocumentEntity(name, value.toString()))
        else if (
            trackerJson.parseToJsonElement(before.payload) != value &&
                dao.updateDocument(name, before.revision, value.toString()) != 1
        )
            throw WriteConflict()
    }

    suspend fun confirm(preview: ImportPreview) =
        db.withTransaction {
            check(dao.draft() == null) {
                "Finish or discard your active workout before importing a backup."
            }
            val current = snapshot()
            check(current.fingerprint() == preview.fingerprint) {
                "Data changed after the preview. Choose the backup again to review the current changes."
            }
            val data = preview.data
            val index = current.records.associateBy { it.kind to it.idKey }
            for ((kind, records) in
                listOf(
                    "workout" to data.workouts,
                    "routine" to data.routines,
                    "legacy-weight" to data.weights,
                )) {
                for (payload in records) {
                    val id = typedIdKey(payload.getValue("id"))
                    val before = index[kind to id]
                    if (before == null) dao.insertRecord(RecordEntity(kind, id, payload.toString()))
                    else if (
                        trackerJson.parseToJsonElement(before.payload) != payload &&
                            dao.updateRecord(kind, id, before.revision, payload.toString()) != 1
                    )
                        throw WriteConflict()
                }
            }
            check(dao.records().count { it.kind == "routine" } <= 500) {
                "The merged collection would exceed 500 routines. No data was imported."
            }
            val settings = current.importSettings(data)
            putDocument("settings", settings)
            putDocument(
                "native_profile",
                trackerJson
                    .encodeToJsonElement(Profile(preview.displayName ?: "", preview.trackRpe))
                    .jsonObject,
            )
            data.goals?.let { putDocument("goals", it) }
            data.metadata?.let { putDocument("backup_meta", it) }
        }

    /** Coherent snapshot; file I/O occurs afterwards and never changes the database. */
    suspend fun export(exportedAt: String): String {
        val current = db.withTransaction { snapshot() }
        fun records(kind: String) =
            current.records
                .filter { it.kind == kind }
                .map { trackerJson.parseToJsonElement(it.payload).jsonObject }
        return BackupCodec.encode(
            BackupData(
                3,
                2,
                records("workout"),
                records("routine"),
                records("legacy-weight"),
                current.settings(),
                current.document("goals") ?: buildJsonObject { put("weeklyGoal", 4) },
                current.document("backup_meta"),
            ),
            exportedAt,
        )
    }
}
