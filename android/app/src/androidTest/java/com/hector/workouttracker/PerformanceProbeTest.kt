package com.hector.workouttracker

import android.os.Debug
import androidx.test.platform.app.InstrumentationRegistry
import com.hector.workouttracker.core.catalog.ExerciseCatalog
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.data.StatsSnapshot
import com.hector.workouttracker.feature.history.historyEntries
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in timings on a host-seeded disposable device; ordinary functional tests skip this driver.
 */
class PerformanceProbeTest {
    @Test
    fun measureLocalReads() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("performance") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as TrackerApplication
        val records = app.repository.workouts.first()
        val text =
            context.assets.open("exercise-catalog.json").bufferedReader().use { it.readText() }
        val day = LocalDate.of(2026, 10, 27)
        fun digest(value: String) =
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") {
                "%02x".format(it)
            }
        val statsMs = mutableListOf<Double>()
        val catalogMs = mutableListOf<Double>()
        val historyMs = mutableListOf<Double>()
        var reference: StatsSnapshot? = null
        repeat(9) {
            val start = System.nanoTime()
            val snapshot = app.statsRepository.read(day)
            statsMs += (System.nanoTime() - start) / 1e6
            if (reference == null) reference = snapshot else assertEquals(reference, snapshot)
        }
        var catalogDigest: String? = null
        repeat(7) {
            val start = System.nanoTime()
            val catalog = ExerciseCatalog.parse(text)
            catalogMs += (System.nanoTime() - start) / 1e6
            val fingerprint = digest(catalog.exercises.toString())
            if (catalogDigest == null) catalogDigest = fingerprint
            else assertEquals(catalogDigest, fingerprint)
        }
        repeat(7) {
            val start = System.nanoTime()
            val entries = historyEntries(records)
            historyMs += (System.nanoTime() - start) / 1e6
            assertEquals(records.size, entries.size)
        }
        val startMs = mutableListOf<Double>()
        val routine = app.repository.routines.first().first()
        var startedDigest: String? = null
        repeat(7) {
            val empty =
                newSession(1793059200000, 0, 0, java.time.ZoneId.of("UTC"))
                    .copy(id = "performance-start")
            val start = System.nanoTime()
            val draft = app.repository.startRoutine(routine, empty)
            startMs += (System.nanoTime() - start) / 1e6
            val fingerprint =
                digest(
                    workoutPayload(draft, 1793059200000, 0, java.time.ZoneId.of("UTC")).toString()
                )
            if (startedDigest == null) startedDigest = fingerprint
            else assertEquals(startedDigest, fingerprint)
            app.repository.discard(draft.id, 1)
        }
        val memory = Debug.MemoryInfo()
        Debug.getMemoryInfo(memory)
        val result = buildJsonObject {
            put("workouts", records.size)
            put("routine_start_ms", JsonArray(startMs.map(::JsonPrimitive)))
            put("started_workout_sha256", startedDigest)
            put("records_sha256", digest(records.toString()))
            put("stats_sha256", digest(requireNotNull(reference).toString()))
            put("catalog_sha256", catalogDigest)
            put("stats_ms", JsonArray(statsMs.map(::JsonPrimitive)))
            put("catalog_ms", JsonArray(catalogMs.map(::JsonPrimitive)))
            put("history_ms", JsonArray(historyMs.map(::JsonPrimitive)))
            put("total_pss_kib", memory.totalPss)
        }
        val folder = File(context.filesDir, "qa-performance").apply { mkdirs() }
        File(folder, "probe.json").writeText(result.toString())
    }
}
