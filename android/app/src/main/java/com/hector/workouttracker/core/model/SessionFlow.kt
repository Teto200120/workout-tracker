package com.hector.workouttracker.core.model

/** Native presentation state is durable in drafts and excluded by workoutPayload. */
fun SessionDraft.openExercise(key: String): SessionDraft {
    if (exercises.none { it.key == key }) return this
    return copy(exercises = exercises.map { it.copy(collapsed = it.key != key) })
}

fun SessionDraft.collapseExercise(key: String): SessionDraft {
    if (exercises.none { it.key == key }) return this
    return copy(exercises = exercises.map { if (it.key == key) it.copy(collapsed = true) else it })
}

/** Resolve the next row from current order, including any intervening reorder/removal. */
fun SessionDraft.nextExerciseKey(key: String): String? {
    val index = exercises.indexOfFirst { it.key == key }
    return if (index < 0) null else exercises.getOrNull(index + 1)?.key
}

data class ExerciseProgress(
    val workingSets: Int,
    val completedSets: Int,
    val completedRows: List<String>,
    val remainingCompletedRows: Int,
) {
    val complete: Boolean
        get() = workingSets > 0 && workingSets == completedSets
}

/** Match the PWA's completed working-set numbering and keep the compact view bounded. */
fun exerciseProgress(exercise: Exercise): ExerciseProgress {
    val working = exercise.sets.filterNot { it.warmup }
    val done = working.withIndex().filter { it.value.done }
    return ExerciseProgress(
        working.size,
        done.size,
        done.take(3).map { "Set ${it.index + 1}: ${it.value.reps.ifEmpty { "-" }} reps" },
        (done.size - 3).coerceAtLeast(0),
    )
}

/** Repair older drafts with several open rows; an intentional all-collapsed draft stays closed. */
fun SessionDraft.singleExpandedExercise(): SessionDraft {
    if (exercises.count { !it.collapsed } <= 1) return this
    return openExercise(exercises.first { !it.collapsed }.key)
}
