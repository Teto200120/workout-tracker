package com.hector.workouttracker.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.hector.workouttracker.TrackerApplication
import com.hector.workouttracker.core.model.trackerJson
import com.hector.workouttracker.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PreferencesEditorState(
    val draft: PreferencesDraft? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val conflict: Boolean = false,
    val message: String? = null,
    val restorable: Boolean = true,
)

class PreferencesViewModel(application: Application, private val saved: SavedStateHandle) :
    AndroidViewModel(application) {
    private val repository = (application as TrackerApplication).preferencesRepository
    private val mutable =
        MutableStateFlow(
            PreferencesEditorState(
                draft =
                    saved.get<String>("preferences-editor")?.let {
                        trackerJson.decodeFromString<PreferencesDraft>(it)
                    }
            )
        )
    val state = mutable.asStateFlow()

    init {
        if (state.value.draft == null) reload()
    }

    private fun draft(value: PreferencesDraft) {
        val snapshot = value.restorationState()
        saved["preferences-editor"] = snapshot
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
                        conflict = error is WriteConflict,
                        error =
                            if (error is WriteConflict)
                                "Preferences changed since you opened this editor. Your edits are still here. Reload the saved preferences before trying again."
                            else
                                error.message
                                    ?: "Unable to save preferences. Your edits are still here.",
                    )
            }
        }
    }

    fun edit(change: (PreferenceValues) -> PreferenceValues) {
        if (state.value.busy) return
        val current = state.value.draft ?: return
        draft(current.copy(values = change(current.values)))
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
            draft(withContext(Dispatchers.Default) { repository.save(current) })
            mutable.value = state.value.copy(message = "Preferences saved.")
        }
    }
}
