package com.hector.workouttracker

import com.hector.workouttracker.core.database.RecordEntity
import com.hector.workouttracker.core.model.trackerJson
import com.hector.workouttracker.data.WorkoutHistoryCache
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class WorkoutHistoryCacheTest {
    @Test
    fun sharedReadRetainsRawExtensionsAndDetectsEditsReplacementDeletionAndClear() = runBlocking {
        val cache = WorkoutHistoryCache()
        val first =
            listOf(
                RecordEntity("workout", "number:1", """{"id":1,"extra":{"keep":[0,false,"x"]}}""")
            )
        val expected = first.map { trackerJson.parseToJsonElement(it.payload).jsonObject }
        val parsed = cache.read(first)
        assertEquals(expected, parsed)
        assertSame(parsed, cache.read(first.map { it.copy() }))
        // A delete/reinsert can reuse both ID and revision: compare payloads too.
        val replacement = first.map { it.copy(payload = """{"id":1,"extra":"replacement"}""") }
        assertNotSame(parsed, cache.read(replacement))
        assertEquals(
            "replacement",
            cache.read(replacement).single()["extra"]?.toString()?.trim('"'),
        )
        assertEquals(emptyList<Any>(), cache.read(emptyList()))
        assertNotSame(parsed, cache.read(first))
        assertEquals(expected, cache.read(first))
        assertEquals("""{"id":1,"extra":{"keep":[0,false,"x"]}}""", first.single().payload)
    }

    @Test
    fun oversizedHistoriesRemainReadableWithoutRetainingTheParse() = runBlocking {
        val cache = WorkoutHistoryCache(maxCharacters = 20)
        val records =
            listOf(
                RecordEntity(
                    "workout",
                    "number:1",
                    """{"id":1,"note":"A longer historical value"}""",
                )
            )
        val first = cache.read(records)
        val second = cache.read(records)
        assertEquals(first, second)
        assertNotSame(first, second)
    }
}
