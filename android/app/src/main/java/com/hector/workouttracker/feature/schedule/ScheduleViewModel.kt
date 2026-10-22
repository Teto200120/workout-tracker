package com.hector.workouttracker.feature.schedule

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.hector.workouttracker.TrackerApplication
import com.hector.workouttracker.core.model.trackerJson
import com.hector.workouttracker.core.schedule.ScheduleDay
import com.hector.workouttracker.data.ScheduleDraft
import com.hector.workouttracker.data.WriteConflict
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScheduleEditorState(
    val draft: ScheduleDraft? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val conflict: Boolean = false,
    val message: String? = null,
    val restorable: Boolean = true,
)

class ScheduleViewModel(application: Application, private val saved: SavedStateHandle) :
    AndroidViewModel(application) {
    private val repository = (application as TrackerApplication).scheduleRepository
    private val mutable =
        MutableStateFlow(
            ScheduleEditorState(
                draft =
                    saved.get<String>("schedule-editor")?.let {
                        trackerJson.decodeFromString<ScheduleDraft>(it)
                    }
            )
        )
    val state = mutable.asStateFlow()

    init {
        if (state.value.draft == null) reload()
    }

    private fun draft(value: ScheduleDraft) {
        val snapshot = value.restorationState()
        saved["schedule-editor"] = snapshot
        mutable.value = state.value.copy(draft = value, restorable = snapshot != null)
    }

    private fun run(action: suspend () -> Unit) {
        if (state.value.busy) return
        mutable.value = state.value.copy(busy = true, error = null, message = null)
        viewModelScope.launch {
            try {
                action()
                mutable.value = state.value.copy(busy = false, conflict = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutable.value =
                    state.value.copy(
                        busy = false,
                        error =
                            if (error is WriteConflict)
                                "Settings changed since you opened this plan. Your edits are still here. Reload the saved schedule or discard your edits."
                            else
                                error.message
                                    ?: "Unable to save your schedule. Your edits are still here.",
                        conflict = error is WriteConflict,
                    )
            }
        }
    }

    fun edit(index: Int, change: (ScheduleDay) -> ScheduleDay) {
        if (state.value.busy) return
        val current = state.value.draft ?: return
        draft(
            current.copy(
                days = current.days.mapIndexed { i, day -> if (i == index) change(day) else day }
            )
        )
        mutable.value =
            state.value.copy(
                error = if (state.value.conflict) state.value.error else null,
                message = null,
            )
    }

    fun reload() = run { draft(withContext(Dispatchers.Default) { repository.read() }) }

    fun save() {
        val current = state.value.draft ?: return
        run {
            draft(repository.save(current))
            mutable.value = state.value.copy(message = "Schedule saved.")
        }
    }
}
