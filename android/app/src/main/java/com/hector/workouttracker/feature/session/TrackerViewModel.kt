package com.hector.workouttracker.feature.session

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hector.workouttracker.TrackerApplication
import com.hector.workouttracker.core.backup.*
import com.hector.workouttracker.core.catalog.ExerciseCatalog
import com.hector.workouttracker.core.database.RecordEntity
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.core.schedule.ScheduleDay
import com.hector.workouttracker.data.ImportPreview
import com.hector.workouttracker.data.InteractionPreferences
import com.hector.workouttracker.data.SavedRoutine
import com.hector.workouttracker.data.SetCopyRequest
import com.hector.workouttracker.data.localExerciseNames
import com.hector.workouttracker.data.routineSnapshot
import com.hector.workouttracker.domain.CompletionSummary
import com.hector.workouttracker.domain.TrainingGuidance
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** All writes have one owner. UI values update immediately; snapshots queue in order. */
data class TrackerState(
    val loading: Boolean = true,
    val interactions: InteractionPreferences = InteractionPreferences(),
    val guidance: Map<String, TrainingGuidance> = emptyMap(),
    val guidanceBasis: String? = null,
    val guidanceError: String? = null,
    val profile: Profile = Profile(),
    val setupRpe: Boolean = true,
    val draft: SessionDraft? = null,
    val workouts: List<RecordEntity> = emptyList(),
    val routines: List<SavedRoutine> = emptyList(),
    val localNames: List<String> = emptyList(),
    val catalog: ExerciseCatalog? = null,
    val catalogLoading: Boolean = true,
    val schedule: List<ScheduleDay>? = null,
    val scheduleLoading: Boolean = true,
    val busy: Boolean = false,
    val editVersion: Long = 0,
    val savedVersion: Long = 0,
    val error: String? = null,
    val importPreview: ImportPreview? = null,
    val backupMessage: String? = null,
    val completion: CompletionSummary? = null,
    val completedName: String? = null,
) {
    val pending: Boolean
        get() = editVersion != savedVersion
}

class TrackerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as TrackerApplication).repository
    private val progression = (application as TrackerApplication).progressionRepository
    private val backups = (application as TrackerApplication).backups
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

    private var guidanceJob: kotlinx.coroutines.Job? = null

    init {
        observeGuidance()
        viewModelScope.launch {
            (application as TrackerApplication)
                .preferencesRepository
                .interactions
                .catch {
                    mutable.update {
                        it.copy(
                            error =
                                "Interaction preferences could not be loaded. Reopen the app to retry."
                        )
                    }
                }
                .collect { value -> mutable.update { it.copy(interactions = value) } }
        }
        viewModelScope.launch {
            repository.profileChanges
                .catch {
                    mutable.update {
                        it.copy(
                            error = "Your profile could not be refreshed. Reopen the app to retry."
                        )
                    }
                }
                .collect { (profile, revision) ->
                    profileRevision = revision
                    mutable.update { it.copy(profile = profile, setupRpe = profile.trackRpe) }
                }
        }
        viewModelScope.launch {
            (application as TrackerApplication)
                .scheduleRepository
                .schedule
                .catch {
                    mutable.update {
                        it.copy(
                            scheduleLoading = false,
                            error =
                                "Your schedule could not be loaded. Fresh workouts and saved routines are still available.",
                        )
                    }
                }
                .collect { days ->
                    mutable.update { it.copy(schedule = days, scheduleLoading = false) }
                }
        }
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
                            backupMessage = null,
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
                it.copy(
                    loading = false,
                    profile = profile.first,
                    setupRpe = profile.first.trackRpe,
                    draft = draft?.first,
                )
            }
        }
        viewModelScope.launch {
            combine(repository.workouts, repository.routines) { workouts, records ->
                    withContext(Dispatchers.Default) {
                        val routines =
                            records.map(::routineSnapshot).sortedBy {
                                it.name.lowercase(java.util.Locale.US)
                            }
                        Triple(workouts, routines, localExerciseNames(workouts, routines))
                    }
                }
                .catch {
                    mutable.update {
                        it.copy(
                            error = "Your library could not be loaded. Reopen the app to retry."
                        )
                    }
                }
                .collect { (workouts, routines, localNames) ->
                    mutable.update {
                        it.copy(workouts = workouts, routines = routines, localNames = localNames)
                    }
                }
        }
        viewModelScope.launch {
            val catalog =
                withContext(Dispatchers.IO) {
                    try {
                        application.assets.open("exercise-catalog.json").bufferedReader().use {
                            ExerciseCatalog.parse(it.readText())
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                }
            mutable.update { it.copy(catalog = catalog, catalogLoading = false) }
        }
    }

    fun observeGuidance() {
        guidanceJob?.cancel()
        mutable.update { it.copy(guidanceError = null) }
        guidanceJob =
            viewModelScope.launch {
                progression
                    .observe(
                        state
                            .map { it.draft?.exercises?.map { e -> e.name }.orEmpty() }
                            .distinctUntilChanged()
                    )
                    .catch {
                        mutable.update {
                            it.copy(
                                guidance = emptyMap(),
                                guidanceBasis = null,
                                guidanceError =
                                    "Guidance could not be loaded. Your entered sets are available.",
                            )
                        }
                    }
                    .collect { (source, guidance) ->
                        mutable.update {
                            it.copy(
                                guidance = guidance,
                                guidanceBasis = source.basis,
                                guidanceError = null,
                            )
                        }
                    }
            }
    }

    fun setCopyRequest(key: String, target: Boolean): SetCopyRequest? {
        val current = state.value
        val draft = current.draft ?: return null
        val exercise = draft.exercises.find { it.key == key } ?: return null
        val guide = current.guidance[exercise.name] ?: return null
        val basis = current.guidanceBasis ?: return null
        if (
            current.busy ||
                (if (target) guide.target.targetSets else guide.last?.sets.orEmpty()).isEmpty()
        )
            return null
        return SetCopyRequest(draft, exercise, basis, target)
    }

    fun applySets(request: SetCopyRequest) {
        val current = state.value
        if (current.busy) return
        if (current.draft != request.draft) {
            mutable.update {
                it.copy(
                    error =
                        "This workout changed. Your sets were kept. Review the guidance and choose again."
                )
            }
            return
        }
        mutable.update { it.copy(busy = true) }
        enqueue {
            val changed = progression.apply(request, draftRevision)
            draftRevision += 1
            val version = current.editVersion + 1
            mutable.update {
                it.copy(
                    draft = changed,
                    busy = false,
                    error = null,
                    editVersion = version,
                    savedVersion = version,
                )
            }
        }
    }

    private fun enqueue(command: suspend () -> Unit) {
        commands.trySend(command)
    }

    fun dismissCompletion() {
        mutable.update { it.copy(completion = null, completedName = null) }
    }

    fun clearError() {
        mutable.update { it.copy(error = null) }
    }

    fun setSetupRpe(value: Boolean) {
        if (state.value.busy || state.value.loading) return
        mutable.update { it.copy(setupRpe = value) }
    }

    fun saveProfile(name: String, rpe: Boolean) {
        if (state.value.busy || state.value.loading) return
        mutable.update { it.copy(busy = true) }
        enqueue {
            val profile = Profile(name.trim(), rpe)
            profileRevision = repository.saveProfile(profile, profileRevision)
            mutable.update {
                it.copy(profile = profile, setupRpe = profile.trackRpe, busy = false, error = null)
            }
        }
    }

    fun importBackup(uri: Uri) {
        if (state.value.busy || state.value.loading) return
        mutable.update {
            it.copy(
                busy = true,
                error = null,
                importPreview = null,
                backupMessage = "Checking backup…",
            )
        }
        enqueue {
            val preview =
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val text =
                        resolver.openInputStream(uri)?.use { readBackup(it) }
                            ?: error("The backup file could not be opened.")
                    backups.preview(BackupCodec.parse(text))
                }
            mutable.update { it.copy(busy = false, importPreview = preview, backupMessage = null) }
        }
    }

    fun cancelImport() {
        mutable.update {
            it.copy(importPreview = null, backupMessage = "Import cancelled. No data changed.")
        }
    }

    fun confirmImport() {
        val preview = state.value.importPreview ?: return
        if (state.value.busy) return
        mutable.update {
            it.copy(busy = true, importPreview = null, backupMessage = "Importing backup…")
        }
        enqueue {
            withContext(Dispatchers.IO) { backups.confirm(preview) }
            val profile = repository.readProfile()
            profileRevision = profile.second
            mutable.update {
                it.copy(
                    profile = profile.first,
                    setupRpe = profile.first.trackRpe,
                    busy = false,
                    error = null,
                    backupMessage = "Backup imported. Your other saved records were kept.",
                )
            }
        }
    }

    fun exportBackup(uri: Uri) {
        if (state.value.busy || state.value.loading) return
        mutable.update { it.copy(busy = true, error = null, backupMessage = "Creating backup…") }
        enqueue {
            withContext(Dispatchers.IO) {
                val encoded = backups.export(Instant.now().toString())
                val resolver = getApplication<Application>().contentResolver
                val output =
                    resolver.openOutputStream(uri, "wt")
                        ?: error("The destination could not be opened.")
                output.use { it.write(encoded.toByteArray(Charsets.UTF_8)) }
            }
            mutable.update {
                it.copy(
                    busy = false,
                    backupMessage = "Backup file created. Active workouts are not included.",
                )
            }
        }
    }

    fun start(routine: SavedRoutine? = null) = startSession(routine, null)

    fun startPlanned(routine: SavedRoutine, day: ScheduleDay, date: LocalDate) =
        startSession(routine, day to date)

    private fun startSession(routine: SavedRoutine?, planned: Pair<ScheduleDay, LocalDate>?) {
        if (state.value.busy || state.value.draft != null || state.value.loading) return
        mutable.update { it.copy(busy = true) }
        enqueue {
            val empty =
                newSession(
                    System.currentTimeMillis(),
                    SystemClock.elapsedRealtime(),
                    boot(),
                    ZoneId.systemDefault(),
                )
            val draft =
                if (routine == null) empty
                else if (planned == null) repository.startRoutine(routine.record, empty)
                else
                    repository.startPlannedRoutine(
                        routine.record,
                        planned.first,
                        planned.second,
                        empty,
                    )
            draftRevision = if (routine == null) repository.start(draft) else 1L
            mutable.update {
                it.copy(
                    draft = draft,
                    busy = false,
                    error = null,
                    editVersion = 0,
                    savedVersion = 0,
                    completion = null,
                    completedName = null,
                )
            }
        }
    }

    /** The accepted name is the entire persistence boundary for picker selections. */
    fun addExercise(value: String): Boolean {
        val current = state.value
        val draft = current.draft ?: return false
        if (current.loading || current.busy || draft.exercises.size >= 100) return false
        val local = draft.exercises.map { it.name } + current.localNames
        val name =
            current.catalog?.canonicalName(value, local)
                ?: local.firstOrNull { it.trim().equals(value.trim(), ignoreCase = true) }
                ?: value.trim()
        if (!visibleName(name, 120)) return false
        edit { it.copy(exercises = it.exercises + Exercise(name = name)) }
        return true
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
            val summary =
                repository.finish(
                    draft,
                    draftRevision,
                    workoutPayload(draft, wallNow, duration, ZoneId.systemDefault()),
                )
            draftRevision = 0
            mutable.update {
                it.copy(
                    draft = null,
                    busy = false,
                    error = null,
                    editVersion = 0,
                    savedVersion = 0,
                    completion = summary,
                    completedName = draft.type,
                )
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
