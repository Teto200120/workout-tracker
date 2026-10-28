package com.hector.workouttracker.data

import androidx.room.withTransaction
import com.hector.workouttracker.core.backup.BackupCodec
import com.hector.workouttracker.core.database.DocumentEntity
import com.hector.workouttracker.core.database.RecordEntity
import com.hector.workouttracker.core.database.TrackerDatabase
import com.hector.workouttracker.core.model.trackerJson
import com.hector.workouttracker.core.schedule.scheduleDays
import com.hector.workouttracker.domain.*
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
class StatsRepository(
    private val db: TrackerDatabase,
    private val workoutHistory: WorkoutHistoryCache = WorkoutHistoryCache(),
) {
    private val dao = db.trackerDao()
    private val calculation = Mutex()

    private data class Input(
        val records: List<RecordEntity>,
        val settings: DocumentEntity?,
        val goals: DocumentEntity?,
        val date: LocalDate,
    )

    private data class WorkoutSummary(
        val records: List<RecordEntity>,
        val dates: List<JsonObject>,
        val insightSources: List<JsonObject>,
        val exercises: List<ExerciseStats>,
        val totals: ProgressTotals,
        val recentSessions: List<RecentSession>,
    )

    // Keep one summary only. Do not retain the full parsed history or persist derived values.
    private var summary: WorkoutSummary? = null
    private var lastInput: Input? = null
    private var lastResult: StatsSnapshot? = null

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
            calculation.withLock {
                val input = Input(records, settings, goals, today)
                if (input == lastInput) return@withLock requireNotNull(lastResult)
                val source =
                    summary?.takeIf { it.records == records }
                        ?: run {
                            val workouts =
                                sortedWorkouts(interchangeOrder(workoutHistory.read(records)))
                            val latest = workouts.firstOrNull()
                            val previous =
                                workouts.drop(1).firstOrNull { it["type"] == latest?.get("type") }
                            WorkoutSummary(
                                records,
                                workouts.map {
                                    buildJsonObject { put("date", it.getValue("date")) }
                                },
                                listOfNotNull(latest, previous),
                                buildExerciseStats(workouts),
                                progressTotals(workouts),
                                recentSessions(workouts),
                            )
                        }
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
                StatsSnapshot(
                        source.totals,
                        source.exercises,
                        weeklyActivity(source.dates, today, schedule),
                        workoutStreak(source.dates, today),
                        recentWorkoutCount(source.dates, today),
                        goal,
                        today.toString(),
                        progressInsights(source.insightSources, source.exercises, today, limit = 6),
                        source.recentSessions,
                    )
                    .also {
                        summary = source
                        lastInput = input
                        lastResult = it
                    }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(today: Flow<LocalDate>): Flow<StatsSnapshot> = changes(today).mapLatest { read(it) }

    private fun changes(today: Flow<LocalDate>): Flow<LocalDate> =
        combine(
            dao.observeRecords("workout").distinctUntilChanged(),
            dao.observeDocument("settings").distinctUntilChanged(),
            dao.observeDocument("goals").distinctUntilChanged(),
            today,
        ) { _, _, _, date ->
            date
        }
}
