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

/** Commit a drag preview only if it still names every current row exactly once. */
fun SessionDraft.reorderExercises(keys: List<String>): SessionDraft {
    if (keys == exercises.map { it.key }) return this
    val rows = exercises.associateBy { it.key }
    if (keys.size != exercises.size || keys.toSet().size != keys.size || keys.toSet() != rows.keys)
        return this
    return copy(exercises = keys.map { rows.getValue(it) })
}
