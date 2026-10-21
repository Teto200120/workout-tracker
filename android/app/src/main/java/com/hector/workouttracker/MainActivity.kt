package com.hector.workouttracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hector.workouttracker.core.designsystem.TrackerTheme
import com.hector.workouttracker.feature.home.TrackerApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TrackerTheme { TrackerApp() } }
    }
}
