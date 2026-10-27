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

    @Test
    fun dragPermutationRetainsEveryRowAndRoundTripsTheDraft() {
        val original = session()
        val reordered = original.reorderExercises(listOf("c", "b", "a"))
        assertEquals(original.copy(exercises = original.exercises.reversed()), reordered)
        original.exercises.forEach { row ->
            assertSame(row, reordered.exercises.single { it.key == row.key })
        }
        assertEquals(
            reordered,
            trackerJson.decodeFromString<SessionDraft>(
                trackerJson.encodeToString(SessionDraft.serializer(), reordered)
            ),
        )
        assertEquals(original, reordered.reorderExercises(listOf("a", "b", "c")))
    }

    @Test
    fun staleOrMalformedDragOrdersCannotDropOrDuplicateExercises() {
        val original = session()
        for (keys in
            listOf(
                listOf("a", "b"),
                listOf("a", "a", "c"),
                listOf("a", "b", "foreign"),
                emptyList(),
            )) {
            assertSame(original, original.reorderExercises(keys))
        }
        assertSame(original, original.reorderExercises(listOf("a", "b", "c")))
        val changed =
            original.copy(
                exercises = original.exercises + Exercise(key = "new", name = "New exercise")
            )
        assertSame(changed, changed.reorderExercises(listOf("c", "b", "a")))
    }

    @Test
    fun focusedNotesRespectTheBackupTextLimit() {
        val original = session()
        fun withNotes(notes: String) =
            original.copy(
                exercises =
                    original.exercises.mapIndexed { index, row ->
                        if (index == 0) row.copy(notes = notes) else row
                    }
            )
        assertNull(sessionError(withNotes("x".repeat(4000))))
        assertEquals("Exercise notes are too long.", sessionError(withNotes("x".repeat(4001))))
    }
}
