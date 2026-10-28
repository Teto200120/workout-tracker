package com.hector.workouttracker.core.catalog

import com.hector.workouttracker.core.model.trackerJson
import com.hector.workouttracker.core.model.visibleName
import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.json.*

/** Disposable, bundled reference data. Only the selected name enters a workout. */
data class CatalogExercise(
    val id: String,
    val name: String,
    val category: String,
    val difficulty: String,
    val equipment: List<String>,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val instructions: List<String>,
)

data class CatalogFilters(
    val muscle: String = "",
    val equipment: String = "",
    val category: String = "",
)

data class ExerciseResults(
    val local: List<String> = emptyList(),
    val catalog: List<CatalogExercise> = emptyList(),
)

internal fun catalogKey(value: String) = value.trim().lowercase(Locale.US)

private val textOrder = compareBy<String>({ catalogKey(it) }, { it })

/** Mirrors the PWA's reviewed resolver; deliberately no fuzzy movement matching. */
private val guideApostrophes = Regex("['’]+")
private val guideHyphens = Regex("[-\\u2010-\\u2015]+")
private val guidePunctuation = Regex("[.,:;/\\\\()\\[\\]{}]+")
private val guideSpaces = Regex("[\\s\\p{Z}]+")

fun guideKey(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFKC)
        .trim()
        .lowercase(Locale.US)
        .replace(guideApostrophes, "")
        .replace(guideHyphens, " ")
        .replace(guidePunctuation, " ")
        .replace(guideSpaces, " ")

val reviewedAliases =
    mapOf(
        "Flat Bench Press" to "free-exercise-db:Barbell_Bench_Press_-_Medium_Grip",
        "Tricep Pushdown" to "free-exercise-db:Triceps_Pushdown",
        "V-Bar Lat Pulldown" to "free-exercise-db:V-Bar_Pulldown",
        "V-Bar Cable Row" to "free-exercise-db:Seated_Cable_Rows",
        "Incline Hammer Curl" to "free-exercise-db:Incline_Hammer_Curls",
        "Cable Bicep Curl" to "free-exercise-db:Standing_Biceps_Cable_Curl",
        "Standing Calf Raise" to "free-exercise-db:Standing_Calf_Raises",
    )

class ExerciseCatalog(val exercises: List<CatalogExercise>, val sourceRevision: String) {
    private val names = exercises.groupBy { guideKey(it.name) }
    private val ids = exercises.groupBy { it.id }
    private val aliases = reviewedAliases.entries.groupBy({ guideKey(it.key) }, { it.value })
    val muscles = exercises.flatMap { it.primaryMuscles }.distinct().sortedWith(textOrder)
    val equipment = exercises.flatMap { it.equipment }.distinct().sortedWith(textOrder)
    val categories =
        exercises.map { it.category }.filter { it.isNotBlank() }.distinct().sortedWith(textOrder)

    fun guide(name: String): CatalogExercise? {
        val key = guideKey(name)
        if (key.isEmpty()) return null
        val exact = names[key].orEmpty().distinctBy { it.id }
        if (exact.isNotEmpty()) return exact.singleOrNull()
        val target = aliases[key].orEmpty().distinct().singleOrNull() ?: return null
        return ids[target].orEmpty().singleOrNull()
    }

    fun canonicalName(value: String, localNames: List<String>): String {
        val key = catalogKey(value)
        return localNames.firstOrNull { catalogKey(it) == key }?.trim()
            ?: exercises.firstOrNull { catalogKey(it.name) == key }?.name
            ?: value.trim()
    }

    fun search(
        localNames: List<String>,
        query: String,
        filters: CatalogFilters = CatalogFilters(),
    ): ExerciseResults {
        val key = catalogKey(query)
        fun rank(name: String): Int {
            val n = catalogKey(name)
            return when {
                key.isEmpty() -> 3
                n == key -> 0
                n.startsWith(key) -> 1
                key in n -> 2
                else -> 4
            }
        }
        val localKeys = localNames.map(::catalogKey).toSet()
        val local =
            localNames
                .map { it.trim() }
                .filter { it.isNotEmpty() && rank(it) < 4 }
                .distinctBy(::catalogKey)
                .sortedWith(compareBy<String> { rank(it) }.then(textOrder))
        fun matches(values: List<String>, filter: String) =
            filter.isBlank() || values.any { catalogKey(it) == catalogKey(filter) }
        val catalog =
            exercises
                .filter {
                    rank(it.name) < 4 &&
                        catalogKey(it.name) !in localKeys &&
                        matches(it.primaryMuscles, filters.muscle) &&
                        matches(it.equipment, filters.equipment) &&
                        matches(listOf(it.category), filters.category)
                }
                .sortedWith(
                    compareBy<CatalogExercise> { rank(it.name) }
                        .thenBy(textOrder) { it.name }
                        .thenBy(textOrder) { it.id }
                )
        return ExerciseResults(local, catalog)
    }

    companion object {
        fun parse(text: String): ExerciseCatalog {
            val root = trackerJson.parseToJsonElement(text).jsonObject
            val metadata = root.getValue("metadata").jsonObject
            require(metadata.getValue("catalogFormatVersion").jsonPrimitive.int == 1)
            require(metadata.getValue("normalizerVersion").jsonPrimitive.int == 1)
            require(metadata.getValue("source").jsonPrimitive.content == "free-exercise-db")
            val revision = metadata.getValue("sourceRevision").jsonPrimitive.content
            require(revision.isNotBlank())
            val rows = root.getValue("exercises").jsonArray
            require(
                rows.size in 1..10000 &&
                    rows.size == metadata.getValue("exerciseCount").jsonPrimitive.int
            )
            val ids = mutableSetOf<String>()
            val names = mutableSetOf<String>()
            val valid =
                rows.mapNotNull { row ->
                    runCatching {
                            val o = row.jsonObject
                            fun string(key: String, limit: Int): String {
                                val p = o.getValue(key).jsonPrimitive
                                require(p.isString && p.content.length <= limit)
                                return p.content.trim()
                            }
                            fun list(key: String, limit: Int = 500): List<String> {
                                val a = o.getValue(key).jsonArray
                                require(a.size <= 100)
                                val values =
                                    a.map { value ->
                                            require(
                                                value is JsonPrimitive &&
                                                    value.isString &&
                                                    value.content.length <= limit
                                            )
                                            value.content.trim()
                                        }
                                        .filter { it.isNotEmpty() }
                                return if (key == "instructions") values
                                else values.distinctBy(::catalogKey)
                            }
                            CatalogExercise(
                                string("catalogId", 500),
                                string("name", 120),
                                string("category", 500),
                                string("difficulty", 500),
                                list("equipment"),
                                list("primaryMuscles"),
                                list("secondaryMuscles"),
                                list("instructions", 2000),
                            )
                        }
                        .getOrNull()
                        ?.takeIf {
                            visibleName(it.name, 120) &&
                                it.id.startsWith("free-exercise-db:") &&
                                it.id !in ids &&
                                catalogKey(it.name) !in names
                        }
                        ?.also {
                            ids.add(it.id)
                            names.add(catalogKey(it.name))
                        }
                }
            require(valid.isNotEmpty())
            return ExerciseCatalog(valid, revision)
        }
    }
}
