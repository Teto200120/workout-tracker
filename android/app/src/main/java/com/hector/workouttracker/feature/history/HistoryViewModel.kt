package com.hector.workouttracker.feature.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.hector.workouttracker.TrackerApplication
import com.hector.workouttracker.core.database.RecordEntity
import com.hector.workouttracker.core.model.trackerJson
import com.hector.workouttracker.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class HistoryEditorState(
    val draft: HistoryDraft? = null,
    val editing: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val conflict: Boolean = false,
    val message: String? = null,
    val restorable: Boolean = true,
)

class HistoryViewModel(application: Application, private val saved: SavedStateHandle) :
    AndroidViewModel(application) {
    private val repository = (application as TrackerApplication).historyRepository
    private val mutable =
        MutableStateFlow(
            HistoryEditorState(
                draft =
                    saved.get<String>("history-workout")?.let {
                        trackerJson.decodeFromString<HistoryDraft>(it)
                    },
                editing = saved["history-editing"] ?: false,
            )
        )
    val state = mutable.asStateFlow()

    private fun draft(value: HistoryDraft?) {
        val snapshot = value?.restorationState()
        saved["history-workout"] = snapshot
        mutable.value =
            state.value.copy(draft = value, restorable = value == null || snapshot != null)
    }

    private fun editing(value: Boolean) {
        saved["history-editing"] = value
        mutable.value = state.value.copy(editing = value)
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
                        conflict = error is WriteConflict,
                        error =
                            if (error is WriteConflict)
                                "This workout changed or was removed. Your visible values are still here. Reload the saved workout before trying again."
                            else
                                error.message
                                    ?: "Unable to save. Your visible values are still here.",
                    )
            }
        }
    }

    fun open(record: RecordEntity) = run {
        draft(withContext(Dispatchers.Default) { historyDraft(record) })
        editing(false)
    }

    fun beginEditing() {
        if (!state.value.busy) editing(true)
    }

    fun edit(change: (HistoryValues) -> HistoryValues) {
        if (state.value.busy || !state.value.editing) return
        val before = state.value.draft ?: return
        draft(before.copy(values = change(before.values)))
        mutable.value =
            state.value.copy(
                error = if (state.value.conflict) state.value.error else null,
                message = null,
            )
    }

    fun close() {
        if (state.value.busy) return
        draft(null)
        editing(false)
        mutable.value = HistoryEditorState()
    }

    fun save() {
        val before = state.value.draft ?: return
        run {
            draft(withContext(Dispatchers.Default) { repository.save(before) })
            editing(false)
            mutable.value = state.value.copy(message = "Workout updated.")
        }
    }

    fun reload() {
        val id = state.value.draft?.id ?: return
        run {
            draft(withContext(Dispatchers.Default) { repository.read(id) })
            editing(false)
            mutable.value =
                state.value.copy(
                    message =
                        if (state.value.draft == null) "This workout was removed."
                        else "Saved workout reloaded."
                )
        }
    }

    fun delete() {
        val before = state.value.draft ?: return
        run {
            withContext(Dispatchers.Default) { repository.delete(before) }
            draft(null)
            editing(false)
            mutable.value =
                state.value.copy(message = "Workout deleted. Your active workout was kept.")
        }
    }
}
