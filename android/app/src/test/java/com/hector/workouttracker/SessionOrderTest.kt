package com.hector.workouttracker

import com.hector.workouttracker.core.model.*
import java.time.ZoneId
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class SessionOrderTest {
    private fun session() =
        newSession(100000, 10, 1, ZoneId.of("UTC"))
            .copy(
                notes = "Keep workout notes",
                tags = listOf("Good session"),
                originRoutineId = JsonPrimitive(0),
                exercises =
                    listOf(
                        Exercise(
                            key = "a",
                            name = "Repeated lift",
                            notes = "First",
                            sets = listOf(WorkingSet(weight = "", reps = "5")),
                        ),
                        Exercise(
                            key = "b",
                            name = "Repeated lift",
                            notes = "Second",
                            sets =
                                listOf(
                                    WorkingSet(
                                        weight = "0",
                                        reps = "8",
                                        rpe = "9",
                                        done = true,
                                        warmup = true,
                                    )
                                ),
                        ),
                        Exercise(key = "c", name = "Other lift"),
                    ),
            )

    @Test
    fun movingByKeyKeepsRepeatedNamesAndEveryEnteredValue() {
        val original = session()
        val moved = original.moveExercise("b", -1)
        assertEquals(
            original.copy(
                exercises =
                    listOf(original.exercises[1], original.exercises[0], original.exercises[2])
            ),
            moved,
        )
        assertSame(original.exercises[1], moved.exercises[0])
        assertSame(original.exercises[0], moved.exercises[1])
        assertEquals(original, moved.moveExercise("b", 1))
        assertEquals(listOf("a", "b", "c"), original.exercises.map { it.key })
    }

    @Test
    fun boundariesMissingRowsAndInvalidDirectionsDoNotChangeTheDraft() {
        val draft = session()
        assertSame(draft, draft.moveExercise("a", -1))
        assertSame(draft, draft.moveExercise("c", 1))
        assertSame(draft, draft.moveExercise("missing", 1))
        assertSame(draft, draft.moveExercise("a", 0))
        assertSame(draft, draft.moveExercise("a", 2))
        val empty = draft.copy(exercises = emptyList())
        assertSame(empty, empty.moveExercise("a", 1))
    }
}
