package com.hector.workouttracker

import com.hector.workouttracker.core.database.RecordEntity
import com.hector.workouttracker.core.model.*
import com.hector.workouttracker.feature.history.*
import java.util.Locale
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class HistoryFiltersTest {
    private fun fixture() =
        trackerJson
            .parseToJsonElement(javaClass.getResource("/history-filters.json")!!.readText())
            .jsonObject

    private fun records(fixture: JsonObject) =
        fixture.getValue("workouts").jsonArray.map {
            RecordEntity("workout", typedIdKey(it.jsonObject.getValue("id")), it.toString())
        }

    @Test
    fun actualPwaRendererCasesMatchWithTypedIdsAndUnchangedRecords() {
        val fixture = fixture()
        val records = records(fixture)
        val before = records.map { it.copy() }
        val entries = historyEntries(records)
        for (value in fixture.getValue("cases").jsonArray) {
            val case = value.jsonObject
            val visible =
                filterHistory(
                    entries,
                    case.getValue("routine").jsonPrimitive.contentOrNull,
                    case.getValue("search").jsonPrimitive.content,
                )
            assertEquals(
                case.getValue("name").jsonPrimitive.content,
                case.getValue("ids").jsonArray.toList(),
                visible.map { it.workout.getValue("id") },
            )
            visible.forEach { entry ->
                assertSame(records.single { it.idKey == entry.record.idKey }, entry.record)
                assertEquals(trackerJson.parseToJsonElement(entry.record.payload), entry.workout)
            }
        }
        assertEquals(before, records)
    }

    @Test
    fun searchDoesNotDependOnDeviceLocaleAndLiteralAllRoutineCanBeSelected() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val entries = historyEntries(records(fixture()))
            assertEquals(
                listOf("string:unicode"),
                filterHistory(entries, null, "INCLINE").map { it.record.idKey },
            )
            val all =
                entries.first().workout.toMutableMap().apply {
                    put("id", JsonPrimitive("all"))
                    put("type", JsonPrimitive("All"))
                }
            val record = RecordEntity("workout", "string:all", JsonObject(all).toString())
            assertEquals(
                listOf(record),
                filterHistory(historyEntries(listOf(record)), "All", "").map { it.record },
            )
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun rebuildingAfterEditDeleteAndImportRefreshesResultsWithoutChangingInput() {
        val original = records(fixture())
        val edited =
            original.first().let { record ->
                val payload =
                    trackerJson.parseToJsonElement(record.payload).jsonObject.toMutableMap()
                payload["type"] = JsonPrimitive("Renamed")
                record.copy(
                    payload = JsonObject(payload).toString(),
                    revision = record.revision + 1,
                )
            }
        val changed = original.drop(1) + edited
        assertEquals(
            listOf(edited),
            filterHistory(historyEntries(changed), "Renamed", "bench").map { it.record },
        )
        assertFalse(
            filterHistory(historyEntries(changed), "Upper", "bench").any {
                it.record.idKey == edited.idKey
            }
        )
        val deleted = changed.filterNot { it.idKey == edited.idKey }
        assertTrue(filterHistory(historyEntries(deleted), "Renamed", "bench").isEmpty())
        assertEquals(
            listOf(original.first()),
            filterHistory(historyEntries(deleted + original.first()), "Upper", "bench").map {
                it.record
            },
        )
    }
}
