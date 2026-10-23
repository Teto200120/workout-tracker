package com.hector.workouttracker.feature.help

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hector.workouttracker.TrackerApplication
import com.hector.workouttracker.domain.guidanceTopics
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.JsonObject

data class HelpState(
    val progress: JsonObject = JsonObject(emptyMap()),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class HelpViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as TrackerApplication).guidanceRepository
    private val mutable = MutableStateFlow(HelpState())
    val state = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            repo.progress
                .catch {
                    mutable.update {
                        it.copy(
                            loading = false,
                            error =
                                "Guidance preferences could not be loaded. Help remains available; reopen the app to retry.",
                        )
                    }
                }
                .collect { value -> mutable.update { it.copy(progress = value, loading = false) } }
        }
    }

    fun complete(id: String) = change(listOf(id), "completed", "Tip saved as read.")

    fun replay(id: String) =
        change(
            listOf(id),
            "unseen",
            "Tip will be available on its screen. No workout was started or changed.",
        )

    fun reset() =
        change(
            guidanceTopics.map { it.id },
            "unseen",
            "Guidance reset. Your workouts and preferences were kept.",
        )

    private fun change(ids: List<String>, status: String, success: String) {
        if (state.value.busy || state.value.loading) return
        mutable.update { it.copy(busy = true, message = null, error = null) }
        viewModelScope.launch {
            try {
                repo.change(ids, status)
                mutable.update { it.copy(busy = false, message = success) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.update {
                    it.copy(
                        busy = false,
                        error =
                            "Guidance preference could not be saved. Your workout data is unchanged. Try again.",
                    )
                }
            }
        }
    }
}
