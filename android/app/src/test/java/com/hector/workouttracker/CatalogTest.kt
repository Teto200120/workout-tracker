package com.hector.workouttracker

import com.hector.workouttracker.core.catalog.*
import com.hector.workouttracker.core.model.trackerJson
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CatalogTest {
    private fun resource(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    private val fixture = trackerJson.parseToJsonElement(resource("catalog.json")).jsonObject
    private val catalog = ExerciseCatalog.parse(resource("exercise-catalog.json"))

    private fun JsonElement.strings() = jsonArray.map { it.jsonPrimitive.content }

    private val local = fixture.getValue("local").strings()

    @Test
    fun sharedPwaSearchAndCanonicalNamesMatch() {
        assertEquals(fixture.getValue("count").jsonPrimitive.int, catalog.exercises.size)
        assertEquals(fixture.getValue("revision").jsonPrimitive.content, catalog.sourceRevision)
        fixture.getValue("searches").jsonArray.forEach { row ->
            val o = row.jsonObject
            val f = o.getValue("filters").jsonObject
            fun filter(key: String) = f[key]?.jsonPrimitive?.content.orEmpty()
            val result =
                catalog.search(
                    local,
                    o.getValue("query").jsonPrimitive.content,
                    CatalogFilters(filter("primaryMuscle"), filter("equipment"), filter("category")),
                )
            assertEquals(o.getValue("local").strings(), result.local)
            assertEquals(o.getValue("catalog").strings(), result.catalog.map { it.id })
        }
        fixture.getValue("canonical").jsonArray.forEach {
            val o = it.jsonObject
            assertEquals(
                o.getValue("name").jsonPrimitive.content,
                catalog.canonicalName(o.getValue("value").jsonPrimitive.content, local),
            )
        }
    }

    @Test
    fun onlyExactAndReviewedUnambiguousGuidesResolve() {
        assertEquals(
            fixture.getValue("aliases").jsonArray.associate {
                it.jsonObject.let { o ->
                    o.getValue("localName").jsonPrimitive.content to
                        o.getValue("catalogId").jsonPrimitive.content
                }
            },
            reviewedAliases,
        )
        fixture.getValue("guides").jsonArray.forEach {
            val o = it.jsonObject
            assertEquals(
                o["id"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.content,
                catalog.guide(o.getValue("name").jsonPrimitive.content)?.id,
            )
        }
        val original = catalog.guide("Flat Bench Press")!!
        val ambiguous =
            ExerciseCatalog(
                listOf(original, original.copy(id = "other", name = original.name.uppercase())),
                "test",
            )
        assertNull(ambiguous.guide(original.name))
        assertNull(ambiguous.guide("Bench Press"))
        val duplicateTarget =
            ExerciseCatalog(listOf(original, original.copy(name = "Another movement")), "test")
        assertNull(duplicateTarget.guide("Flat Bench Press"))
    }

    @Test
    fun brokenCatalogFailsClosedWhileLocalSearchRemainsAvailable() {
        for (text in
            listOf(
                "{}",
                "invalid",
                resource("exercise-catalog.json")
                    .replace("\"catalogFormatVersion\": 1", "\"catalogFormatVersion\": 9"),
            )) {
            assertTrue(runCatching { ExerciseCatalog.parse(text) }.isFailure)
        }
        val empty = ExerciseCatalog(emptyList(), "")
        assertEquals(
            listOf("barbell bench press - medium grip", "Flat Bench Press"),
            empty.search(local, "bench", CatalogFilters(equipment = "dumbbell")).local,
        )
        assertNull(empty.guide("Flat Bench Press"))
    }
}
