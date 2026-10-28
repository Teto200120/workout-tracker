package com.hector.workouttracker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hector.workouttracker.core.catalog.ExerciseCatalog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class CatalogPackagingTest {
    @Test
    fun smallerNativeAssetRetainsEveryExerciseInstructionAndSourceRevision() {
        val reference = requireNotNull(javaClass.getResource("/exercise-catalog.json")).readText()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bundled =
            context.assets.open("exercise-catalog.json").bufferedReader().use { it.readText() }
        val expected = ExerciseCatalog.parse(reference)
        val actual = ExerciseCatalog.parse(bundled)
        assertEquals(expected.exercises, actual.exercises)
        assertEquals(expected.sourceRevision, actual.sourceRevision)
        assertTrue(bundled.length < reference.length)
    }
}
