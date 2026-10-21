package com.hector.workouttracker.feature.routines

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.hector.workouttracker.TrackerApplication
import com.hector.workouttracker.core.model.trackerJson
import com.hector.workouttracker.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RoutineEditorState(
    val draft: RoutineDraft? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val conflict: Boolean = false,
    val message: String? = null,
    val restorable: Boolean = true,
)

class RoutineViewModel(application: Application, private val saved: SavedStateHandle) :
    AndroidViewModel(application) {
    private val repository = (application as TrackerApplication).routineRepository
    private val mutable =
        MutableStateFlow(
            RoutineEditorState(
                draft =
                    saved.get<String>("routine-editor")?.let {
                        trackerJson.decodeFromString<RoutineDraft>(it)
                    }
            )
        )
    val state = mutable.asStateFlow()

    private fun draft(value: RoutineDraft?) {
        val snapshot = value?.restorationState()
        saved["routine-editor"] = snapshot
        mutable.value =
            mutable.value.copy(draft = value, restorable = value == null || snapshot != null)
    }

    fun open(routine: SavedRoutine? = null) {
        if (state.value.busy || state.value.draft != null) return
        run {
            val editor =
                withContext(Dispatchers.Default) {
                    routine?.let(RoutineDraft::edit) ?: RoutineDraft.create()
                }
            draft(editor)
        }
    }

    fun edit(change: (RoutineDraft) -> RoutineDraft) {
        if (state.value.busy) return
        state.value.draft?.let { draft(change(it)) }
        mutable.value =
            state.value.copy(error = if (state.value.conflict) state.value.error else null)
    }

    fun close() {
        if (state.value.busy) return
        draft(null)
        mutable.value = RoutineEditorState()
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
                            if (error is WriteConflict && state.value.draft == null)
                                "This routine changed or was removed. Review the current library before deleting."
                            else if (error is WriteConflict)
                                "This routine changed or was removed. Your edits are still here. Reload the saved routine or discard your edits."
                            else error.message ?: "Unable to save. Your edits are still here.",
                        conflict = error is WriteConflict,
                    )
            }
        }
    }

    fun save() {
        val current = state.value.draft ?: return
        run {
            repository.save(current)
            draft(null)
            mutable.value = state.value.copy(message = "Routine saved.")
        }
    }

    fun reload() {
        val current = state.value.draft ?: return
        run {
            val latest =
                withContext(Dispatchers.Default) {
                    repository.read(current.id)?.let(RoutineDraft::edit)
                }
            draft(latest)
            mutable.value =
                state.value.copy(
                    message = if (latest == null) "This routine was removed." else null
                )
        }
    }

    fun delete(routine: SavedRoutine) = run {
        repository.delete(routine)
        mutable.value =
            state.value.copy(
                message = "Routine deleted. Saved workouts and active sessions were kept."
            )
    }
}
