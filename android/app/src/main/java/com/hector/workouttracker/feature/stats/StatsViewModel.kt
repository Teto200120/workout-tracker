package com.hector.workouttracker.feature.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hector.workouttracker.TrackerApplication
import com.hector.workouttracker.data.StatsSnapshot
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class StatsState(
    val loading: Boolean = true,
    val snapshot: StatsSnapshot? = null,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as TrackerApplication).statsRepository
    private val dates = MutableStateFlow(LocalDate.now())
    private val attempts = MutableStateFlow(0)
    val state =
        attempts
            .flatMapLatest {
                repository
                    .observe(dates)
                    .map { StatsState(loading = false, snapshot = it) }
                    .onStart { emit(StatsState()) }
                    .catch { error ->
                        if (error is CancellationException) throw error
                        else
                            emit(
                                StatsState(
                                    loading = false,
                                    error =
                                        "Stats could not be loaded. Your saved workouts are unchanged.",
                                )
                            )
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsState())

    fun refreshDate() {
        dates.value = LocalDate.now()
    }

    fun retry() {
        attempts.value += 1
    }
}
