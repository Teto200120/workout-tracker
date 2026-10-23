package com.hector.workouttracker.data

import androidx.room.withTransaction
import com.hector.workouttracker.core.database.*
import com.hector.workouttracker.core.model.trackerJson
import com.hector.workouttracker.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

class GuidanceRepository(private val db: TrackerDatabase) {
    private val dao = db.trackerDao()

    private fun parse(payload: String?) =
        payload?.let {
            runCatching { trackerJson.parseToJsonElement(it) as? JsonObject }.getOrNull()
        } ?: JsonObject(emptyMap())

    val progress =
        dao.observeDocument("native_guidance")
            .map { parse(it?.payload) }
            .flowOn(Dispatchers.Default)

    suspend fun change(ids: List<String>, status: String) =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val before = dao.document("native_guidance")
                val root = parse(before?.payload)
                val next = changeGuidance(root, ids, status)
                if (before == null)
                    dao.insertDocument(DocumentEntity("native_guidance", next.toString()))
                else if (
                    root != next &&
                        dao.updateDocument(before.name, before.revision, next.toString()) != 1
                )
                    throw WriteConflict()
            }
        }
}
