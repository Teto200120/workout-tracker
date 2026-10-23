package com.hector.workouttracker

import android.app.Application
import androidx.room.Room
import com.hector.workouttracker.core.database.TrackerDatabase
import com.hector.workouttracker.data.BackupRepository
import com.hector.workouttracker.data.GuidanceRepository
import com.hector.workouttracker.data.HistoryRepository
import com.hector.workouttracker.data.PreferencesRepository
import com.hector.workouttracker.data.ProgressionRepository
import com.hector.workouttracker.data.RoutineRepository
import com.hector.workouttracker.data.ScheduleRepository
import com.hector.workouttracker.data.StatsRepository
import com.hector.workouttracker.data.TrackerRepository

class TrackerApplication : Application() {
    private val database by lazy {
        Room.databaseBuilder(this, TrackerDatabase::class.java, "workout-tracker.db").build()
    }
    val progressionRepository by lazy { ProgressionRepository(database) }
    val guidanceRepository by lazy { GuidanceRepository(database) }
    val preferencesRepository by lazy { PreferencesRepository(database) }
    val repository by lazy { TrackerRepository(database) }
    val backups by lazy { BackupRepository(database) }
    val routineRepository by lazy { RoutineRepository(database) }
    val scheduleRepository by lazy { ScheduleRepository(database) }
    val historyRepository by lazy { HistoryRepository(database) }
    val statsRepository by lazy { StatsRepository(database) }
}
