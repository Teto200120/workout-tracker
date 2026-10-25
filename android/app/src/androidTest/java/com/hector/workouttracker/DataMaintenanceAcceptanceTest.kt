package com.hector.workouttracker

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hector.workouttracker.core.backup.BackupCodec
import com.hector.workouttracker.core.model.Profile
import com.hector.workouttracker.core.model.trackerJson
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Host calls this only after driving the actual destructive confirmation on its disposable AVD. */
@RunWith(AndroidJUnit4::class)
class DataMaintenanceAcceptanceTest {
    @Test
    fun requestedPhase() = runBlocking {
        val phase = InstrumentationRegistry.getArguments().getString("maintenancePhase")
        assumeTrue("Run the dedicated data-clear UI/cold-process acceptance first.", phase != null)
        require(phase in listOf("verify-cleared", "verify-new-profile"))
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as TrackerApplication
        assertNull(app.repository.readDraft())
        assertTrue(app.repository.workouts.first().isEmpty())
        val routines = app.repository.routines.first()
        assertEquals(6, routines.size)
        assertTrue(
            routines.all {
                trackerJson
                    .parseToJsonElement(it.payload)
                    .jsonObject
                    .getValue("id")
                    .jsonPrimitive
                    .content
                    .startsWith("starter:v1:")
            }
        )
        assertTrue(app.guidanceRepository.progress.first().isEmpty())
        assertFalse(app.backups.status.first().hasData)
        assertNull(app.backups.status.first().lastExportedAt)
        assertNotEquals("initial", app.dataMaintenance.currentEpoch())
        val preferences = app.preferencesRepository.read()
        assertEquals("4", preferences.values.weeklyGoal)
        assertEquals(0, app.statsRepository.read(LocalDate.now()).totals.workouts)
        if (phase == "verify-cleared") {
            assertEquals(Profile(), app.repository.readProfile().first)
            val text = app.backups.export("2026-10-24T12:00:00Z")
            val data = BackupCodec.parse(text)
            assertEquals(BackupCodec.defaultSettings, data.settings)
            assertTrue(data.weights.isEmpty())
            assertFalse(text.contains("native_data_epoch"))
            val file = File(app.getExternalFilesDir(null), "qa/data-cleared-export.json")
            file.parentFile!!.mkdirs()
            file.writeText(text)
            assertNull(app.backups.status.first().lastExportedAt)
        } else {
            assertEquals(Profile("Fresh QA", false), app.repository.readProfile().first)
            assertEquals("Fresh QA", preferences.values.displayName)
        }
    }
}
