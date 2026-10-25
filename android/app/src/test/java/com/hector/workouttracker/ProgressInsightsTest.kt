package com.hector.workouttracker

import com.hector.workouttracker.core.model.trackerJson
import com.hector.workouttracker.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ProgressInsightsTest {
    private fun cases() =
        trackerJson
            .parseToJsonElement(javaClass.getResource("/insights.json")!!.readText())
            .jsonObject
            .getValue("cases")
            .jsonArray
            .map { it.jsonObject }

    private fun rows(insights: List<ProgressInsight>) =
        JsonArray(
            insights.map {
                buildJsonObject {
                    put("type", it.type)
                    put("title", it.title)
                    put("meta", it.meta)
                }
            }
        )

    @Test
    fun actualPwaRendererMatchesPriorityThresholdsDeduplicationAndRecentPreviews() {
        for (c in cases()) {
            val name = c.getValue("name").jsonPrimitive.content
            val input = c.getValue("workouts").jsonArray.map { it.jsonObject }
            val before = input.toString()
            val workouts = sortedWorkouts(input)
            val stats = buildExerciseStats(workouts)
            val today = LocalDate.parse(c.getValue("today").jsonPrimitive.content)
            assertEquals(
                name,
                c.getValue("preview"),
                rows(progressInsights(workouts, stats, today)),
            )
            assertEquals(
                name,
                c.getValue("detail"),
                rows(progressInsights(workouts, stats, today, 6)),
            )
            val recent = recentSessions(workouts)
            assertEquals(name, workouts.take(3).map { it.getValue("id") }, recent.map { it.id })
            val summaries =
                JsonArray(
                    recent.map {
                        val date =
                            LocalDate.parse(it.date)
                                .format(DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.US))
                        val hours = kotlin.math.floor(it.duration / 60).toInt()
                        val minutes = it.duration % 60
                        val duration =
                            when {
                                it.duration == 0.0 -> "-"
                                hours == 0 -> "${progressNumber(minutes)}m"
                                minutes == 0.0 -> "${hours}h"
                                else -> "${hours}h ${progressNumber(minutes)}m"
                            }
                        buildJsonObject {
                            put("type", it.type)
                            put("summary", "$date - ${it.exercises} exercises")
                            put("duration", duration)
                        }
                    }
                )
            assertEquals(name, c.getValue("recent"), summaries)
            assertEquals(name, before, input.toString())
            assertTrue(progressInsights(workouts, stats, today, 0).isEmpty())
        }
    }
}
