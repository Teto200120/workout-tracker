package com.hector.workouttracker

import com.hector.workouttracker.core.model.*
import java.time.ZoneId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SessionFlowTest {
    private fun session() =
        newSession(100000, 10, 1, ZoneId.of("UTC"))
            .copy(
                originRoutineId = JsonPrimitive(0),
                notes = "Retained",
                tags = listOf("Good session"),
                exercises =
                    listOf(
                        Exercise(
                            key = "a",
                            name = "Repeated lift",
                            notes = "First",
                            sets =
                                listOf(WorkingSet(weight = "0", reps = "5", rpe = "8", done = true)),
                        ),
                        Exercise(key = "b", name = "Repeated lift", notes = "Second"),
                        Exercise(key = "c", name = "Other lift"),
                    ),
            )

    @Test
    fun openCollapseAndNavigationChangeOnlyPresentationAndFollowCurrentOrder() {
        val original = session()
        val opened = original.openExercise("b")
        assertEquals(listOf(true, false, true), opened.exercises.map { it.collapsed })
        assertEquals(
            original,
            opened.copy(exercises = opened.exercises.map { it.copy(collapsed = false) }),
        )
        assertEquals("b", original.nextExerciseKey("a"))
        assertEquals("c", original.moveExercise("c", -1).nextExerciseKey("a"))
        assertNull(original.nextExerciseKey("c"))
        assertNull(original.nextExerciseKey("missing"))
        assertSame(original, original.openExercise("missing"))
        assertSame(original, original.collapseExercise("missing"))
        assertTrue(opened.collapseExercise("b").exercises.all { it.collapsed })
        assertEquals(original.exercises.map { it.sets }, opened.exercises.map { it.sets })
    }

    @Test
    fun oldDraftsDefaultToExpandedAndPresentationNeverEntersCanonicalPayload() {
        val original = session()
        val legacy = trackerJson.encodeToString(original)
        assertFalse(legacy.contains("collapsed"))
        assertEquals(original, trackerJson.decodeFromString<SessionDraft>(legacy))
        val opened = original.openExercise("b")
        assertEquals(
            opened,
            trackerJson.decodeFromString<SessionDraft>(trackerJson.encodeToString(opened)),
        )
        val before = workoutPayload(original, 110000, 0, ZoneId.of("UTC"))
        val after = workoutPayload(opened, 110000, 0, ZoneId.of("UTC"))
        assertEquals(before, after)
        assertFalse(after.toString().contains("collapsed"))
    }

    @Test
    fun compactProgressUsesCompletedWorkingRowsAndBoundsItsSummary() {
        val exercise =
            Exercise(
                name = "Lift",
                sets =
                    listOf(
                        WorkingSet(reps = "10", done = true, warmup = true),
                        WorkingSet(reps = "5", done = true),
                        WorkingSet(reps = "6"),
                        WorkingSet(reps = "", done = true),
                        WorkingSet(reps = "0", done = true),
                        WorkingSet(reps = "8", done = true),
                        WorkingSet(reps = "9", done = true),
                    ),
            )
        assertEquals(
            ExerciseProgress(6, 5, listOf("Set 1: 5 reps", "Set 3: - reps", "Set 4: 0 reps"), 2),
            exerciseProgress(exercise),
        )
        assertFalse(exerciseProgress(exercise).complete)
        assertTrue(
            exerciseProgress(exercise.copy(sets = exercise.sets.map { it.copy(done = true) }))
                .complete
        )
        assertFalse(
            exerciseProgress(exercise.copy(sets = exercise.sets.filter { it.warmup })).complete
        )
        assertEquals(0, exerciseProgress(Exercise(name = "Empty", sets = emptyList())).workingSets)
    }
}
