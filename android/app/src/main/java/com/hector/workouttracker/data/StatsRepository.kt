package com.hector.workouttracker.data

import androidx.room.withTransaction
import com.hector.workouttracker.core.backup.BackupCodec
import com.hector.workouttracker.core.database.TrackerDatabase
import com.hector.workouttracker.core.model.trackerJson
import com.hector.workouttracker.core.schedule.scheduleDays
import com.hector.workouttracker.domain.*
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

data class StatsSnapshot(
    val totals: ProgressTotals,
    val exercises: List<ExerciseStats>,
    val weekly: WeeklyActivity,
    val streak: Int,
    val recent: Int,
    val goal: Int,
    val today: String,
    val insights: List<ProgressInsight>,
    val recentSessions: List<RecentSession>,
)

/** Read-only derivation from one coherent Room snapshot; no analytics are stored in backups. */
class StatsRepository(private val db: TrackerDatabase) {
    private val dao = db.trackerDao()

    suspend fun read(today: LocalDate): StatsSnapshot =
        withContext(Dispatchers.Default) {
            val (records, settings, goals) =
                db.withTransaction {
                    Triple(
                        dao.recordsOfKind("workout"),
                        dao.document("settings"),
                        dao.document("goals"),
                    )
                }
            val workouts =
                sortedWorkouts(
                    interchangeOrder(
                        records.map { trackerJson.parseToJsonElement(it.payload).jsonObject }
                    )
                )
            val schedule =
                scheduleDays(
                    settings?.let { trackerJson.parseToJsonElement(it.payload).jsonObject }
                        ?: BackupCodec.defaultSettings
                )
            val goal =
                goals?.let {
                    trackerJson
                        .parseToJsonElement(it.payload)
                        .jsonObject["weeklyGoal"]
                        ?.jsonPrimitive
                        ?.doubleOrNull
                        ?.toInt()
                } ?: 4
            val exercises = buildExerciseStats(workouts)
            StatsSnapshot(
                progressTotals(workouts),
                exercises,
                weeklyActivity(workouts, today, schedule),
                workoutStreak(workouts, today),
                recentWorkoutCount(workouts, today),
                goal,
                today.toString(),
                progressInsights(workouts, exercises, today, limit = 6),
                recentSessions(workouts),
            )
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(today: Flow<LocalDate>): Flow<StatsSnapshot> =
        combine(
                dao.observeRecords("workout"),
                dao.observeDocument("settings"),
                dao.observeDocument("goals"),
                today,
            ) { _, _, _, date ->
                date
            }
            .mapLatest { read(it) }
}
