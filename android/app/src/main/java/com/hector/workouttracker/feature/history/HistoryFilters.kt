package com.hector.workouttracker.feature.history

import com.hector.workouttracker.core.database.RecordEntity
import com.hector.workouttracker.core.model.trackerJson
import com.hector.workouttracker.domain.WorkoutMetrics
import com.hector.workouttracker.domain.workoutMetrics
import java.util.Locale
import kotlinx.serialization.json.*

internal data class HistoryEntry(
    val record: RecordEntity,
    val workout: JsonObject,
    val routine: String,
    val exerciseNames: List<String>,
    val metrics: WorkoutMetrics,
)

/** Read-only search index; original records and typed IDs stay attached to each result. */
internal fun historyEntries(records: List<RecordEntity>): List<HistoryEntry> =
    records
        .map { record ->
            val workout = trackerJson.parseToJsonElement(record.payload).jsonObject
            HistoryEntry(
                record,
                workout,
                workout.getValue("type").jsonPrimitive.content,
                workout.getValue("exercises").jsonArray.map {
                    it.jsonObject.getValue("name").jsonPrimitive.content.lowercase(Locale.ROOT)
                },
                workoutMetrics(workout),
            )
        }
        .sortedWith(
            compareByDescending<HistoryEntry> { it.workout.getValue("date").jsonPrimitive.content }
                .thenByDescending { it.workout.getValue("createdAt").jsonPrimitive.content }
        )

internal fun filterHistory(
    entries: List<HistoryEntry>,
    routine: String?,
    search: String,
): List<HistoryEntry> {
    // ECMAScript trim whitespace, including the BOM, matches the PWA's String.trim().
    val query =
        search
            .trim {
                it in
                    "\u0009\u000A\u000B\u000C\u000D\u0020\u00A0\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A\u2028\u2029\u202F\u205F\u3000\uFEFF"
            }
            .lowercase(Locale.ROOT)
    return entries.filter { entry ->
        (routine == null || entry.routine == routine) &&
            (query.isEmpty() || entry.exerciseNames.any { query in it })
    }
}
