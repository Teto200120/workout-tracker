package com.hector.workouttracker.data

import androidx.room.withTransaction
import com.hector.workouttracker.core.backup.*
import com.hector.workouttracker.core.database.*
import com.hector.workouttracker.core.model.*
import java.math.BigDecimal
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

val preferenceNumberKeys =
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

@Serializable
data class PreferenceValues(
    val displayName: String,
    val trackRpe: Boolean,
    val haptics: Boolean,
    val animations: Boolean,
    val weeklyGoal: String,
    val numbers: Map<String, String>,
)

@Serializable data class DocumentFence(val revision: Long, val fingerprint: String?)

@Serializable
data class PreferencesDraft(
    val values: PreferenceValues,
    val original: PreferenceValues,
    val fences: Map<String, DocumentFence>,
) {
    val changed: Boolean
        get() = values != original

    fun restorationState(): String? {
        if (
            values.displayName.length > 320 ||
                values.weeklyGoal.length > 32 ||
                values.numbers.values.any { it.length > 32 }
        )
            return null
        return trackerJson.encodeToString(this).takeIf {
            it.toByteArray(Charsets.UTF_8).size <= 16 * 1024
        }
    }
}

data class InteractionPreferences(val haptics: Boolean = true, val animations: Boolean = true)

private fun documentFence(document: DocumentEntity?): DocumentFence =
    DocumentFence(
        document?.revision ?: 0,
        document?.let {
            MessageDigest.getInstance("SHA-256")
                .digest(it.payload.toByteArray(Charsets.UTF_8))
                .joinToString("") { b -> "%02x".format(b) }
        },
    )

private fun preferenceNumber(value: String): BigDecimal {
    require(value.length <= 32 && Regex("[0-9]+([.,][0-9]+)?").matches(value.trim())) {
        "Enter a positive number; use a period or comma for decimals."
    }
    return value.trim().replace(',', '.').toBigDecimal()
}

fun preferencesError(values: PreferenceValues): String? =
    runCatching {
            require(visibleName(values.displayName.trim(), 80)) {
                "Enter a display name (up to 80 characters)."
            }
            require(values.numbers.keys == preferenceNumberKeys.toSet()) {
                "Reload the saved preferences."
            }
            val goal = preferenceNumber(values.weeklyGoal)
            require(
                goal >= BigDecimal.ONE &&
                    goal <= BigDecimal(100) &&
                    goal.stripTrailingZeros().scale() <= 0
            ) {
                "Weekly goal must be a whole number from 1 to 100."
            }
            val jump = preferenceNumber(values.numbers.getValue("defaultWeightJump"))
            require(
                jump >= BigDecimal("0.5") &&
                    jump <= BigDecimal(1000) &&
                    jump.stripTrailingZeros().scale() <= 2
            ) {
                "Load jump must be 0.5–1000 lb, with up to two decimal places."
            }
            for (prefix in listOf("compound", "pull", "isolation", "general")) {
                val min = preferenceNumber(values.numbers.getValue("${prefix}Min"))
                val max = preferenceNumber(values.numbers.getValue("${prefix}Max"))
                require(
                    listOf(min, max).all {
                        it >= BigDecimal.ONE &&
                            it <= BigDecimal(1000) &&
                            it.stripTrailingZeros().scale() <= 0
                    }
                ) {
                    "Rep ranges must be whole numbers from 1 to 1000."
                }
                require(min <= max) { "Rep range minimum must not exceed its maximum." }
            }
        }
        .exceptionOrNull()
        ?.message

fun preferencesWarning(values: PreferenceValues): String? {
    if (preferencesError(values) != null) return null
    return if (
        preferenceNumber(values.weeklyGoal) > BigDecimal(14) ||
            values.numbers.any { (key, value) ->
                key != "defaultWeightJump" && preferenceNumber(value) > BigDecimal(100)
            }
    )
        "This goal or rep range is unusually high. Review the numbers before saving."
    else null
}

/**
 * One transaction owns settings, goals and the native name/RPE projection. Unknown fields stay in
 * Room.
 */
class PreferencesRepository(private val db: TrackerDatabase) {
    private val dao = db.trackerDao()
    private val names = listOf("settings", "goals", "native_profile")
    val interactions =
        dao.observeDocument("settings")
            .map { document ->
                val settings =
                    document?.let { trackerJson.parseToJsonElement(it.payload).jsonObject }
                        ?: BackupCodec.defaultSettings
                InteractionPreferences(
                    settings.getValue("haptics").jsonPrimitive.boolean,
                    settings.getValue("animations").jsonPrimitive.boolean,
                )
            }
            .flowOn(Dispatchers.Default)

    private fun canonical(documents: Map<String, DocumentEntity?>): JsonObject {
        documents["settings"]?.let {
            return trackerJson.parseToJsonElement(it.payload).jsonObject
        }
        val profile =
            documents["native_profile"]?.let { trackerJson.decodeFromString<Profile>(it.payload) }
                ?: Profile()
        return JsonObject(
            BackupCodec.defaultSettings +
                mapOf(
                    "displayName" to
                        if (profile.displayName.isEmpty()) JsonNull
                        else JsonPrimitive(profile.displayName),
                    "rpeAware" to JsonPrimitive(profile.trackRpe),
                )
        )
    }

    private suspend fun documents() = names.associateWith { dao.document(it) }

    private fun snapshot(documents: Map<String, DocumentEntity?>): PreferencesDraft {
        val settings = canonical(documents)
        val goals =
            documents["goals"]?.let { trackerJson.parseToJsonElement(it.payload).jsonObject }
        val values =
            PreferenceValues(
                settings["displayName"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
                    ?: "",
                settings.getValue("rpeAware").jsonPrimitive.boolean,
                settings.getValue("haptics").jsonPrimitive.boolean,
                settings.getValue("animations").jsonPrimitive.boolean,
                goals
                    ?.getValue("weeklyGoal")
                    ?.jsonPrimitive
                    ?.content
                    ?.toBigDecimal()
                    ?.stripTrailingZeros()
                    ?.toPlainString() ?: "4",
                preferenceNumberKeys.associateWith {
                    settings
                        .getValue(it)
                        .jsonPrimitive
                        .content
                        .toBigDecimal()
                        .stripTrailingZeros()
                        .toPlainString()
                },
            )
        return PreferencesDraft(values, values, documents.mapValues { documentFence(it.value) })
    }

    suspend fun read(): PreferencesDraft = db.withTransaction { snapshot(documents()) }

    suspend fun save(draft: PreferencesDraft): PreferencesDraft =
        db.withTransaction {
            val before = documents()
            val base = snapshot(before)
            if (base.fences != draft.fences || base.values != draft.original) throw WriteConflict()
            require(preferencesError(draft.values) == null) {
                preferencesError(draft.values) ?: "Check preferences."
            }
            if (!draft.changed) return@withTransaction base
            val v = draft.values
            val original = canonical(before)
            val settings = original.toMutableMap()
            if (v.displayName.trim() != base.values.displayName)
                settings["displayName"] = JsonPrimitive(v.displayName.trim())
            if (v.trackRpe != base.values.trackRpe) settings["rpeAware"] = JsonPrimitive(v.trackRpe)
            if (v.haptics != base.values.haptics) settings["haptics"] = JsonPrimitive(v.haptics)
            if (v.animations != base.values.animations)
                settings["animations"] = JsonPrimitive(v.animations)
            for (key in preferenceNumberKeys) {
                val number = preferenceNumber(v.numbers.getValue(key))
                if (number.compareTo(preferenceNumber(base.values.numbers.getValue(key))) != 0)
                    settings[key] = JsonPrimitive(number)
            }
            val oldGoals =
                before["goals"]?.let { trackerJson.parseToJsonElement(it.payload).jsonObject }
            val goalChanged =
                preferenceNumber(v.weeklyGoal)
                    .compareTo(preferenceNumber(base.values.weeklyGoal)) != 0
            val goals =
                if (goalChanged)
                    JsonObject(
                        (oldGoals ?: JsonObject(emptyMap())) +
                            ("weeklyGoal" to JsonPrimitive(preferenceNumber(v.weeklyGoal).toInt()))
                    )
                else oldGoals
            val result = JsonObject(settings)
            // Apply the same canonical interchange bounds as imports before writing anything.
            BackupCodec.encode(
                BackupData(3, 2, emptyList(), emptyList(), emptyList(), result, goals, null),
                "2026-10-23T00:00:00Z",
            )
            suspend fun put(name: String, payload: String) {
                val old = before[name]
                if (old == null) dao.insertDocument(DocumentEntity(name, payload))
                else if (
                    trackerJson.parseToJsonElement(old.payload) !=
                        trackerJson.parseToJsonElement(payload) &&
                        dao.updateDocument(name, old.revision, payload) != 1
                )
                    throw WriteConflict()
            }
            if (result != original) put("settings", result.toString())
            if (goalChanged) put("goals", requireNotNull(goals).toString())
            if (
                v.displayName.trim() != base.values.displayName ||
                    v.trackRpe != base.values.trackRpe
            ) {
                val profile =
                    before["native_profile"]?.let {
                        trackerJson.parseToJsonElement(it.payload).jsonObject
                    } ?: JsonObject(emptyMap())
                put(
                    "native_profile",
                    JsonObject(
                            profile +
                                mapOf(
                                    "displayName" to JsonPrimitive(v.displayName.trim()),
                                    "trackRpe" to JsonPrimitive(v.trackRpe),
                                )
                        )
                        .toString(),
                )
            }
            snapshot(documents())
        }
}
