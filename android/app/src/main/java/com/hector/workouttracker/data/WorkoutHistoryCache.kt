package com.hector.workouttracker.data

import com.hector.workouttracker.core.database.RecordEntity
import com.hector.workouttracker.core.model.trackerJson
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Shares one bounded, read-only parse; callers still read a coherent current database snapshot. */
class WorkoutHistoryCache(private val maxCharacters: Long = 4L * 1024 * 1024) {
    private data class Parsed(val records: List<RecordEntity>, val workouts: List<JsonObject>)

    private val lock = Mutex()
    private var cached: Parsed? = null

    suspend fun read(records: List<RecordEntity>): List<JsonObject> =
        lock.withLock {
            cached
                ?.takeIf { it.records == records }
                ?.let {
                    return@withLock it.workouts
                }
            cached = null
            val workouts = records.map { trackerJson.parseToJsonElement(it.payload).jsonObject }
            if (records.sumOf { it.payload.length.toLong() } <= maxCharacters)
                cached = Parsed(records, workouts)
            workouts
        }
}
