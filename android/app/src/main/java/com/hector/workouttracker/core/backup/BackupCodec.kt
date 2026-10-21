package com.hector.workouttracker.core.backup

import com.hector.workouttracker.core.model.typedIdKey
import java.time.*
import kotlinx.serialization.json.*

/** Canonical interchange data; native drafts and device preferences are deliberately separate. */
@ConsistentCopyVisibility
data class BackupData
internal constructor(
    val backupVersion: Int,
    val schemaVersion: Int,
    val workouts: List<JsonObject>,
    val routines: List<JsonObject>,
    val weights: List<JsonObject>,
    val settings: JsonObject?,
    val goals: JsonObject?,
    val metadata: JsonObject?,
)

object BackupCodec {
    val defaultSettings: JsonObject =
        Json.parseToJsonElement(
                """
        {"displayName":null,"schedule":{"0":{"kind":"rest","routine":"Custom"},"1":{"kind":"gym","routine":"Chest / Triceps"},"2":{"kind":"gym","routine":"Back / Biceps"},"3":{"kind":"soccer","routine":"Custom"},"4":{"kind":"rest","routine":"Custom"},"5":{"kind":"gym","routine":"Legs"},"6":{"kind":"gym","routine":"Shoulders / Traps"}},"defaultWeightJump":5,"compoundMin":6,"compoundMax":10,"pullMin":8,"pullMax":12,"isolationMin":12,"isolationMax":20,"generalMin":8,"generalMax":12,"rpeAware":true,"haptics":true,"animations":true}
    """
            )
            .jsonObject
    private val numberFields =
        listOf(
            "defaultWeightJump",
            "compoundMin",
            "compoundMax",
            "pullMin",
            "pullMax",
            "isolationMin",
            "isolationMax",
            "generalMin",
            "generalMax",
        )
    private val booleanFields = listOf("rpeAware", "haptics", "animations")

    fun parse(text: String): BackupData {
        val root = checkedJson(text)
        require(
            root["kind"] != JsonPrimitive("workout-tracker-raw-rescue") &&
                !root.containsKey("rescueFileVersion")
        ) {
            "Raw rescue files are diagnostic files, not workout backups."
        }
        fun version(value: JsonElement?, fallback: Int): Int {
            if (value == null) return fallback
            val p =
                value as? JsonPrimitive ?: throw IllegalArgumentException("Invalid backup version.")
            require(
                p !is JsonNull &&
                    p.booleanOrNull == null &&
                    (if (p.isString) p.content.matches(Regex("0|[1-9][0-9]*"))
                    else
                        p.doubleOrNull?.let { it.isFinite() && it >= 0 && it % 1.0 == 0.0 } == true)
            ) {
                "Invalid backup version."
            }
            val n =
                p.content.toDoubleOrNull()
                    ?: throw IllegalArgumentException("Backup version is too new.")
            require(n <= Int.MAX_VALUE) { "Backup version is too new." }
            return n.toInt()
        }
        val file = version(root["backupFileVersion"] ?: root["version"], 0)
        if (root.containsKey("backupFileVersion") && root.containsKey("version"))
            require(file == version(root["version"], 0)) { "Backup version fields disagree." }
        require(file in 0..3) { "Backup was created by a newer application version." }
        require(file != 3 || root.containsKey("backupFileVersion")) {
            "Current backups must use backupFileVersion."
        }
        require(file != 3 || root.containsKey("applicationSchemaVersion")) {
            "Current backup is missing its application schema version."
        }
        val schema = version(root["applicationSchemaVersion"].takeUnless { it is JsonNull }, 0)
        require(schema in 0..2) { "Backup was created by a newer data schema." }
        root["exportedAt"]?.let { timestamp(it, "exportedAt", false) }
        val legacy = schema == 0
        fun collection(key: String, required: Boolean = false): List<JsonObject> {
            val array =
                root[key]
                    ?: if (required) throw IllegalArgumentException("$key is required.")
                    else JsonArray(emptyList())
            require(array is JsonArray) { "$key must be a list." }
            val seen = mutableSetOf<String>()
            return array.mapIndexed { index, value ->
                val obj =
                    value as? JsonObject
                        ?: throw IllegalArgumentException("$key[$index] must be an object.")
                val id = obj["id"] ?: throw IllegalArgumentException("$key[$index].id is required.")
                require(seen.add(typedIdKey(id))) { "Duplicate ID in $key at item ${index + 1}." }
                obj
            }
        }
        val workouts =
            collection("workouts", true).mapIndexed { i, w -> workout(w, legacy, "workouts[$i]") }
        val routines =
            collection("templates")
                .also { require(it.size <= 500) { "Backups are limited to 500 routines." } }
                .mapIndexed { i, r -> routine(r, legacy, "templates[$i]") }
        val weights = collection("weights").mapIndexed { i, w -> weight(w, legacy, "weights[$i]") }
        fun document(key: String): JsonObject? =
            root[key]
                ?.takeUnless { it is JsonNull }
                ?.let {
                    it as? JsonObject
                        ?: throw IllegalArgumentException("$key must be an object or null.")
                }
        val settings = document("settings")?.let { settings(it, schema) }
        val goals =
            document("goals")?.let { source ->
                val value =
                    if (
                        legacy && (source["weeklyGoal"] == null || source["weeklyGoal"] is JsonNull)
                    )
                        JsonPrimitive(4)
                    else
                        source["weeklyGoal"]
                            ?: throw IllegalArgumentException("goals.weeklyGoal is required.")
                val n = number(value, "goals.weeklyGoal", legacy)
                require(n in 1.0..100.0 && n % 1.0 == 0.0) {
                    "Weekly goal must be a whole number from 1 to 100."
                }
                source["targetWeight"]
                    ?.takeUnless { it is JsonNull || it == JsonPrimitive("") }
                    ?.let { number(it, "goals.targetWeight", true) }
                JsonObject(
                    source + ("weeklyGoal" to if (legacy) JsonPrimitive(n.toInt()) else value)
                )
            }
        val meta =
            document("backupMeta")?.also { source ->
                source["lastExportedAt"]
                    ?.takeUnless { it is JsonNull }
                    ?.let { timestamp(it, "backupMeta.lastExportedAt", true) }
            }
        return BackupData(file, schema, workouts, routines, weights, settings, goals, meta)
    }

    fun encode(data: BackupData, exportedAt: String): String =
        buildJsonObject {
                put("app", "Workout Tracker")
                put("backupFileVersion", 3)
                put("applicationSchemaVersion", 2)
                put("database", "hector_workout_tracker_fresh_v1")
                put("exportedAt", exportedAt)
                put("workouts", JsonArray(data.workouts))
                put("templates", JsonArray(data.routines))
                put("weights", JsonArray(data.weights))
                data.settings?.let { put("settings", it) }
                data.goals?.let { put("goals", it) }
                put(
                    "backupMeta",
                    JsonObject(
                        (data.metadata ?: JsonObject(emptyMap())) +
                            ("lastExportedAt" to JsonPrimitive(exportedAt))
                    ),
                )
            }
            .toString()
            .also { parse(it) }

    // ECMAScript trim semantics matter for legacy fields and display-name normalization.
    private fun jsTrim(value: String): String =
        value.trim {
            it in "\t\n\u000b\u000c\r \u00a0\u1680\u2028\u2029\u202f\u205f\u3000\ufeff" ||
                it in '\u2000'..'\u200a'
        }

    private fun numeric(n: Double): JsonPrimitive =
        if (n % 1.0 == 0.0) JsonPrimitive(n.toLong()) else JsonPrimitive(n)

    private fun number(value: JsonElement, path: String, allowText: Boolean): Double {
        val p = value as? JsonPrimitive ?: throw IllegalArgumentException("$path must be numeric.")
        require(
            p !is JsonNull &&
                p.booleanOrNull == null &&
                (!p.isString || allowText) &&
                p.content.isNotBlank()
        ) {
            "$path must be numeric."
        }
        // JSON numbers and legacy numeric strings use JavaScript Number semantics for decimal/hex
        // input.
        val text = jsTrim(p.content)
        val n =
            when {
                text.startsWith("0x", true) -> text.drop(2).toLongOrNull(16)?.toDouble()
                text.startsWith("0b", true) -> text.drop(2).toLongOrNull(2)?.toDouble()
                text.startsWith("0o", true) -> text.drop(2).toLongOrNull(8)?.toDouble()
                else -> text.toDoubleOrNull()
            }
        require(n != null && n.isFinite()) { "$path must be finite." }
        return n
    }

    private fun text(source: JsonObject, key: String, path: String, legacy: Boolean): String {
        val value = source[key]
        if (legacy && (value == null || value is JsonNull)) return ""
        require(value is JsonPrimitive && value.isString) { "$path.$key must be a string." }
        return value.content
    }

    private fun boundedText(
        value: String,
        path: String,
        max: Int,
        required: Boolean = false,
        trim: Boolean = false,
        lines: Boolean = false,
    ) {
        val checked = if (trim) jsTrim(value) else value
        require(checked.codePointCount(0, checked.length) <= max) {
            "$path is too long (maximum $max characters)."
        }
        require(
            checked.none {
                it.code == 127 || (it.code < 32 && !(lines && it.code in listOf(9, 10, 13)))
            }
        ) {
            "$path contains a control character."
        }
        require(
            !required ||
                checked.any { !it.isWhitespace() && it !in "\u200b\u200c\u200d\u2060\ufeff" }
        ) {
            "$path is required."
        }
    }

    private fun timestamp(value: JsonElement, path: String, empty: Boolean) {
        require(value is JsonPrimitive && value.isString) { "$path must be a timestamp." }
        if (empty && value.content.isEmpty()) return
        val t = value.content
        val valid =
            runCatching { Instant.parse(t) }.isSuccess ||
                runCatching { OffsetDateTime.parse(t) }.isSuccess ||
                runCatching { LocalDateTime.parse(t) }.isSuccess ||
                runCatching { LocalDate.parse(t) }.isSuccess
        require(valid) { "$path must be a valid ISO timestamp." }
    }

    private fun date(source: JsonObject, path: String) {
        val value = text(source, "date", path, false)
        require(
            value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) &&
                runCatching { LocalDate.parse(value) }.isSuccess
        ) {
            "$path.date must be a real YYYY-MM-DD date."
        }
    }

    private fun list(source: JsonObject, key: String, path: String, legacy: Boolean): JsonArray {
        val value =
            source[key]
                ?: if (legacy) JsonArray(emptyList())
                else throw IllegalArgumentException("$path.$key is required.")
        return value as? JsonArray ?: throw IllegalArgumentException("$path.$key must be a list.")
    }

    private fun flag(
        source: JsonObject,
        key: String,
        path: String,
        legacy: Boolean,
        fallback: Boolean = false,
    ): Boolean {
        val value = source[key]
        if (legacy && (value == null || value is JsonNull)) return fallback
        if (value is JsonPrimitive && !value.isString && value.booleanOrNull != null)
            return value.boolean
        if (
            legacy &&
                value is JsonPrimitive &&
                !value.isString &&
                value.doubleOrNull in listOf(0.0, 1.0)
        )
            return value.double == 1.0
        if (legacy)
            when (value) {
                JsonPrimitive(0),
                JsonPrimitive("0"),
                JsonPrimitive("false") -> return false
                JsonPrimitive(1),
                JsonPrimitive("1"),
                JsonPrimitive("true") -> return true
                else -> Unit
            }
        throw IllegalArgumentException("$path.$key must be a boolean.")
    }

    private fun scalar(source: JsonObject, key: String, path: String, legacy: Boolean): String {
        val value = source[key]
        if (legacy && (value == null || value is JsonNull)) return ""
        require(value is JsonPrimitive && (legacy || value.isString)) {
            "$path.$key must be numeric text."
        }
        val content =
            if (legacy && !value.isString) numeric(number(value, "$path.$key", false)).content
            else value.content
        if (content.isNotEmpty()) number(value, "$path.$key", true)
        val checked = jsTrim(content)
        if (checked.isNotEmpty()) {
            require(checked.matches(Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"))) {
                "$path.$key must be a decimal without scientific notation."
            }
            val n = checked.toDouble()
            val max = if (key == "rpe") 10.0 else 10000.0
            require(n in 0.0..max) { "$path.$key is outside its supported range." }
            if (key == "reps") require(n % 1.0 == 0.0) { "$path.reps must be a whole number." }
            val decimals = checked.substringAfter('.', "").length
            if (key == "weight")
                require(decimals <= 2) { "$path.weight supports two decimal places." }
            if (key == "rpe")
                require(decimals <= 1 && n * 2 % 1.0 == 0.0) {
                    "$path.rpe must use half-point increments."
                }
        }
        return if (legacy) checked else content
    }

    private fun workout(source: JsonObject, legacy: Boolean, path: String): JsonObject {
        date(source, path)
        source["originRoutineId"]?.takeUnless { it is JsonNull }?.let { typedIdKey(it) }
        val result = source.toMutableMap()
        val type = text(source, "type", path, false)
        boundedText(type, "$path.type", 80, true, true)
        for (key in listOf("startTime", "endTime")) {
            val t = text(source, key, path, legacy)
            require(
                t.isEmpty() ||
                    (t.matches(Regex("[0-9]{2}:[0-9]{2}")) &&
                        runCatching { LocalTime.parse(t) }.isSuccess)
            ) {
                "$path.$key must use HH:MM or be empty."
            }
            result[key] = JsonPrimitive(t)
        }
        val notes = text(source, "notes", path, legacy)
        boundedText(notes, "$path.notes", 4000, lines = true)
        result["notes"] = JsonPrimitive(notes)
        val created = text(source, "createdAt", path, legacy)
        timestamp(JsonPrimitive(created), "$path.createdAt", true)
        result["createdAt"] = JsonPrimitive(created)
        val tags = list(source, "tags", path, legacy)
        require(tags.size <= 32) { "$path has too many tags." }
        tags.forEach {
            require(it is JsonPrimitive && it.isString) { "$path tags must be strings." }
            boundedText(it.content, "$path.tags", 80, true, true)
        }
        result["tags"] = tags
        val exercises = list(source, "exercises", path, legacy)
        require(exercises.size <= 100) { "$path has too many exercises." }
        result["exercises"] =
            JsonArray(
                exercises.mapIndexed { i, value ->
                    val p = "$path.exercises[$i]"
                    val e =
                        value as? JsonObject
                            ?: throw IllegalArgumentException("$p must be an object.")
                    val name = text(e, "name", p, legacy)
                    boundedText(name, "$p.name", 120, true, true)
                    val note = text(e, "notes", p, legacy)
                    boundedText(note, "$p.notes", 2000, lines = true)
                    val sets = list(e, "sets", p, legacy)
                    require(sets.size <= 200) { "$p has too many sets." }
                    JsonObject(
                        e +
                            mapOf(
                                "name" to JsonPrimitive(if (legacy) jsTrim(name) else name),
                                "notes" to JsonPrimitive(note),
                                "sets" to
                                    JsonArray(
                                        sets.mapIndexed { j, item ->
                                            val sp = "$p.sets[$j]"
                                            val s =
                                                item as? JsonObject
                                                    ?: throw IllegalArgumentException(
                                                        "$sp must be an object."
                                                    )
                                            JsonObject(
                                                s +
                                                    mapOf(
                                                        "weight" to
                                                            JsonPrimitive(
                                                                scalar(s, "weight", sp, legacy)
                                                            ),
                                                        "reps" to
                                                            JsonPrimitive(
                                                                scalar(s, "reps", sp, legacy)
                                                            ),
                                                        "rpe" to
                                                            JsonPrimitive(
                                                                scalar(s, "rpe", sp, legacy)
                                                            ),
                                                        "done" to
                                                            JsonPrimitive(
                                                                flag(s, "done", sp, legacy)
                                                            ),
                                                        "warmup" to
                                                            JsonPrimitive(
                                                                flag(s, "warmup", sp, legacy)
                                                            ),
                                                    )
                                            )
                                        }
                                    ),
                            )
                    )
                }
            )
        val duration = source["durationMinutes"]
        if (duration == null || duration is JsonNull || duration == JsonPrimitive("")) {
            if (legacy) result.remove("durationMinutes")
        } else {
            val n = number(duration, "$path.durationMinutes", legacy)
            require(n in 0.0..1440.0) { "$path.durationMinutes must be between 0 and 1440." }
            if (legacy) result["durationMinutes"] = numeric(n)
        }
        if (legacy && result["originRoutineId"] is JsonNull) result.remove("originRoutineId")
        return JsonObject(result)
    }

    private fun routine(source: JsonObject, legacy: Boolean, path: String): JsonObject {
        val name = text(source, "name", path, false)
        boundedText(name, "$path.name", 80, true, true)
        val exercises = list(source, "exercises", path, legacy)
        require(exercises.size <= 100) { "$path has too many exercises." }
        exercises.forEach {
            require(it is JsonPrimitive && it.isString) { "$path exercises must be strings." }
            boundedText(it.content, "$path.exercises", 120, true, true)
        }
        val result = source.toMutableMap()
        result["name"] = JsonPrimitive(if (legacy) jsTrim(name) else name)
        result["exercises"] =
            if (legacy) JsonArray(exercises.map { JsonPrimitive(jsTrim(it.jsonPrimitive.content)) })
            else exercises
        for (key in listOf("createdAt", "updatedAt")) {
            val t = text(source, key, path, legacy)
            timestamp(JsonPrimitive(t), "$path.$key", true)
            result[key] = JsonPrimitive(t)
        }
        return JsonObject(result)
    }

    private fun weight(source: JsonObject, legacy: Boolean, path: String): JsonObject {
        date(source, path)
        val n =
            number(
                source["weight"] ?: throw IllegalArgumentException("$path.weight is required."),
                "$path.weight",
                legacy,
            )
        require(n in 0.0..10000.0) { "$path.weight is outside its supported range." }
        require(java.math.BigDecimal.valueOf(n).stripTrailingZeros().scale() <= 2) {
            "$path.weight supports two decimal places."
        }
        val notes = text(source, "notes", path, legacy)
        boundedText(notes, "$path.notes", 2000, lines = true)
        val created = text(source, "createdAt", path, legacy)
        timestamp(JsonPrimitive(created), "$path.createdAt", true)
        return JsonObject(
            source +
                mapOf(
                    "weight" to if (legacy) numeric(n) else source.getValue("weight"),
                    "notes" to JsonPrimitive(notes),
                    "createdAt" to JsonPrimitive(created),
                )
        )
    }

    private fun settings(source: JsonObject, schema: Int): JsonObject {
        val legacy = schema == 0
        source["displayName"]?.let {
            require(it is JsonNull || (it is JsonPrimitive && it.isString)) {
                "Display name must be a string or null."
            }
        }
        val result =
            if (legacy) (defaultSettings + source).toMutableMap() else source.toMutableMap()
        val display =
            if (legacy) JsonNull
            else
                source["displayName"]
                    ?: if (schema == 1) JsonNull
                    else throw IllegalArgumentException("settings.displayName is required.")
        require(display is JsonNull || (display is JsonPrimitive && display.isString)) {
            "Display name must be a string or null."
        }
        if (display !is JsonNull)
            boundedText(display.jsonPrimitive.content, "settings.displayName", 80, true, true)
        result["displayName"] =
            if (display is JsonNull) display
            else JsonPrimitive(jsTrim(display.jsonPrimitive.content))
        for (key in numberFields) {
            val raw = source[key]
            val value =
                if (legacy && (raw == null || raw is JsonNull)) defaultSettings.getValue(key)
                else raw ?: throw IllegalArgumentException("settings.$key is required.")
            val n = number(value, "settings.$key", legacy)
            if (key == "defaultWeightJump")
                require(
                    n in 0.5..1000.0 &&
                        java.math.BigDecimal.valueOf(n).stripTrailingZeros().scale() <= 2
                ) {
                    "Default load jump is invalid."
                }
            else
                require(n in 1.0..1000.0 && n % 1.0 == 0.0) {
                    "Rep ranges must be whole numbers from 1 to 1000."
                }
            result[key] = if (legacy) numeric(n) else value
        }
        for (prefix in listOf("compound", "pull", "isolation", "general")) require(
            result.getValue("${prefix}Min").jsonPrimitive.double <=
                result.getValue("${prefix}Max").jsonPrimitive.double
        ) {
            "Rep range minimum exceeds maximum."
        }
        for (key in booleanFields) result[key] =
            JsonPrimitive(
                flag(
                    source,
                    key,
                    "settings",
                    legacy,
                    defaultSettings.getValue(key).jsonPrimitive.boolean,
                )
            )
        val schedule =
            source["schedule"]
                ?: if (legacy) JsonObject(emptyMap())
                else throw IllegalArgumentException("settings.schedule is required.")
        require(schedule is JsonObject) { "Schedule must be an object." }
        result["schedule"] = buildJsonObject {
            if (!legacy) schedule.forEach { (key, value) -> put(key, value) }
            for (day in 0..6) {
                val key = day.toString()
                val defaults =
                    defaultSettings.getValue("schedule").jsonObject.getValue(key).jsonObject
                val entry =
                    schedule[key]
                        ?: if (legacy) JsonObject(emptyMap())
                        else throw IllegalArgumentException("Schedule day $day is required.")
                require(entry is JsonObject) { "Schedule day $day must be an object." }
                val kind =
                    entry["kind"]
                        ?: if (legacy) defaults.getValue("kind")
                        else throw IllegalArgumentException("Schedule kind is required.")
                require(
                    kind is JsonPrimitive &&
                        kind.isString &&
                        kind.content in listOf("gym", "rest", "soccer")
                ) {
                    "Invalid schedule kind."
                }
                val routine =
                    entry["routine"]
                        ?: if (legacy) defaults.getValue("routine")
                        else throw IllegalArgumentException("Schedule routine is required.")
                require(routine is JsonPrimitive && routine.isString) {
                    "Schedule routine must be a string."
                }
                put(
                    key,
                    JsonObject(
                        (if (legacy) defaults else JsonObject(emptyMap())) +
                            entry +
                            mapOf(
                                "kind" to kind,
                                "routine" to
                                    if (legacy) JsonPrimitive(jsTrim(routine.content)) else routine,
                            )
                    ),
                )
            }
        }
        return JsonObject(result)
    }
}
