package com.hector.workouttracker

import android.app.Application
import androidx.room.Room
import com.hector.workouttracker.core.database.TrackerDatabase
import com.hector.workouttracker.data.BackupRepository
import com.hector.workouttracker.data.RoutineRepository
import com.hector.workouttracker.data.ScheduleRepository
import com.hector.workouttracker.data.TrackerRepository

class TrackerApplication : Application() {
    private val database by lazy {
        Room.databaseBuilder(this, TrackerDatabase::class.java, "workout-tracker.db").build()
    }
    val repository by lazy { TrackerRepository(database) }
    val backups by lazy { BackupRepository(database) }
    val routineRepository by lazy { RoutineRepository(database) }
    val scheduleRepository by lazy { ScheduleRepository(database) }
}
