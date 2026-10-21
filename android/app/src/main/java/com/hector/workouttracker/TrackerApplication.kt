package com.hector.workouttracker

import android.app.Application
import androidx.room.Room
import com.hector.workouttracker.core.database.TrackerDatabase
import com.hector.workouttracker.data.TrackerRepository

class TrackerApplication : Application() {
    val repository by lazy {
        TrackerRepository(
            Room.databaseBuilder(this, TrackerDatabase::class.java, "workout-tracker.db").build()
        )
    }
}
