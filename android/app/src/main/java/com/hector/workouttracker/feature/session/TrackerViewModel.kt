package com.hector.workouttracker.feature.session

import android.app.Application
import android.os.SystemClock
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hector.workouttracker.TrackerApplication
import com.hector.workouttracker.core.database.RecordEntity
import com.hector.workouttracker.core.model.*
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** All writes have one owner. UI values update immediately; snapshots queue in order. */
data class TrackerState(
    val loading: Boolean = true,
    val profile: Profile = Profile(),
    val draft: SessionDraft? = null,
    val workouts: List<RecordEntity> = emptyList(),
    val busy: Boolean = false,
    val editVersion: Long = 0,
    val savedVersion: Long = 0,
    val error: String? = null,
) {
    val pending: Boolean
        get() = editVersion != savedVersion
}

class TrackerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as TrackerApplication).repository
    private val mutable = MutableStateFlow(TrackerState())
    val state = mutable.asStateFlow()
    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var draftRevision = 0L
    private var profileRevision = 0L

    private fun boot() =
        Settings.Global.getInt(
            getApplication<Application>().contentResolver,
            Settings.Global.BOOT_COUNT,
            -1,
        )

    init {
        viewModelScope.launch {
            for (command in commands) {
                try {
                    command()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    mutable.update {
                        it.copy(
                            busy = false,
                            error =
                                error.message
                                    ?: "Unable to save. Your visible entries are retained.",
                        )
                    }
                }
            }
        }
        enqueue {
            val profile = repository.readProfile()
            profileRevision = profile.second
            val draft = repository.readDraft()
            draftRevision = draft?.second ?: 0L
            mutable.update {
                it.copy(loading = false, profile = profile.first, draft = draft?.first)
            }
        }
        viewModelScope.launch {
            repository.workouts
                .catch {
                    mutable.update {
                        it.copy(error = "History could not be loaded. Reopen the app to retry.")
                    }
                }
                .collect { records -> mutable.update { it.copy(workouts = records) } }
        }
    }

    private fun enqueue(command: suspend () -> Unit) {
        commands.trySend(command)
    }

    fun clearError() {
        mutable.update { it.copy(error = null) }
    }

    fun saveProfile(name: String, rpe: Boolean) {
        if (state.value.busy || state.value.loading) return
        mutable.update { it.copy(busy = true) }
        enqueue {
            val profile = Profile(name.trim(), rpe)
            profileRevision = repository.saveProfile(profile, profileRevision)
            mutable.update { it.copy(profile = profile, busy = false, error = null) }
        }
    }

    fun start() {
        if (state.value.busy || state.value.draft != null || state.value.loading) return
        mutable.update { it.copy(busy = true) }
        enqueue {
            val draft =
                newSession(
                    System.currentTimeMillis(),
                    SystemClock.elapsedRealtime(),
                    boot(),
                    ZoneId.systemDefault(),
                )
            draftRevision = repository.start(draft)
            mutable.update {
                it.copy(
                    draft = draft,
                    busy = false,
                    error = null,
                    editVersion = 0,
                    savedVersion = 0,
                )
            }
        }
    }

    fun edit(change: (SessionDraft) -> SessionDraft) {
        val before = state.value
        if (before.busy || before.loading) return
        val draft = before.draft?.let(change) ?: return
        val version = before.editVersion + 1
        mutable.update { it.copy(draft = draft, editVersion = version) }
        enqueue {
            draftRevision = repository.saveDraft(draft, draftRevision)
            mutable.update { it.copy(savedVersion = version, error = null) }
        }
    }

    fun retryDraft() {
        edit { it }
    }

    fun finish() {
        val current = state.value
        val draft = current.draft ?: return
        if (current.busy) return
        val error = sessionError(draft)
        if (error != null) {
            mutable.update { it.copy(error = error) }
            return
        }
        mutable.update { it.copy(busy = true) }
        enqueue {
            val wallNow = System.currentTimeMillis()
            val duration =
                elapsedMillis(draft, wallNow, SystemClock.elapsedRealtime(), boot()) / 60000
            repository.finish(
                draft,
                draftRevision,
                workoutPayload(draft, wallNow, duration, ZoneId.systemDefault()),
            )
            draftRevision = 0
            mutable.update {
                it.copy(draft = null, busy = false, error = null, editVersion = 0, savedVersion = 0)
            }
        }
    }

    fun discard() {
        val draft = state.value.draft ?: return
        if (state.value.busy) return
        mutable.update { it.copy(busy = true) }
        enqueue {
            repository.discard(draft.id, draftRevision)
            draftRevision = 0
            mutable.update {
                it.copy(draft = null, busy = false, error = null, editVersion = 0, savedVersion = 0)
            }
        }
    }
}
