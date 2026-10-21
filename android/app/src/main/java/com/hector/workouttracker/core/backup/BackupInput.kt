package com.hector.workouttracker.core.backup

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.serialization.json.*

const val MAX_BACKUP_BYTES = 25 * 1024 * 1024

/** Bound the stream itself; a provider's reported length is not authoritative. */
fun readBackup(input: InputStream): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        require(output.size() + count <= MAX_BACKUP_BYTES) { "Backup files are limited to 25 MB." }
        output.write(buffer, 0, count)
    }
    require(output.size() > 0) { "The selected backup is empty." }
    return Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(output.toByteArray()))
        .toString()
        .removePrefix("\uFEFF")
}

/** Check nesting before recursive JSON parsing, then bound the accepted JSON tree. */
internal fun checkedJson(text: String): JsonObject {
    require(text.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) {
        "Backup files are limited to 25 MB."
    }
    var quoted = false
    var escaped = false
    var depth = 0
    var tokens = 0
    text.forEach { c ->
        if (quoted) {
            if (escaped) escaped = false
            else if (c == '\\') escaped = true else if (c == '"') quoted = false
        } else {
            if (c == '"') quoted = true
            if (c == '{' || c == '[') {
                depth++
                require(depth <= 21) { "Backup nesting is limited to 20 levels." }
            }
            if (c == '}' || c == ']') depth--
            if (c == ',' || c == ':' || c == '{' || c == '[') {
                tokens++
                require(tokens <= 1_000_000) { "Backup contains too many values." }
            }
        }
    }
    val root = Json.parseToJsonElement(text)
    var nodes = 0
    val unsafeKeys = setOf("__proto__", "prototype", "constructor")
    fun inspect(value: JsonElement, level: Int) {
        nodes++
        require(nodes <= 500_000) { "Backup contains too many values." }
        when (value) {
            is JsonObject -> {
                require(level <= 20) { "Backup nesting is limited to 20 levels." }
                value.forEach { (key, child) ->
                    require(key !in unsafeKeys) { "Backup contains an unsafe extension key: $key." }
                    inspect(child, level + 1)
                }
            }
            is JsonArray -> {
                require(level <= 20) { "Backup nesting is limited to 20 levels." }
                value.forEach { inspect(it, level + 1) }
            }
            is JsonPrimitive ->
                if (!value.isString && value !is JsonNull && value.booleanOrNull == null) {
                    require(value.doubleOrNull?.isFinite() == true) {
                        "Backup contains a non-finite number."
                    }
                }
        }
    }
    inspect(root, 0)
    return root as? JsonObject ?: throw IllegalArgumentException("Backup must be a JSON object.")
}
