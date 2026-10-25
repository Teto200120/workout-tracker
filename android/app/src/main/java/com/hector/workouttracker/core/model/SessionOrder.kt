package com.hector.workouttracker.core.model

/** Move by stable row key; repeated names and all entered data stay independent. */
fun SessionDraft.moveExercise(key: String, direction: Int): SessionDraft {
    if (direction != -1 && direction != 1) return this
    val from = exercises.indexOfFirst { it.key == key }
    val to = from + direction
    if (from < 0 || to !in exercises.indices) return this
    val reordered = exercises.toMutableList()
    reordered.add(to, reordered.removeAt(from))
    return copy(exercises = reordered)
}
