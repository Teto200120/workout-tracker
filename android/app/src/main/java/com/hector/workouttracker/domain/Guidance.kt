package com.hector.workouttracker.domain

import kotlinx.serialization.json.*

data class GuidanceTopic(val id: String, val title: String, val text: String)

val guidanceTopics =
    listOf(
        GuidanceTopic(
            "homeTour",
            "Today",
            "Start fresh or use a saved routine. Today's plan follows your weekly schedule. An unfinished workout stays on this device: choose Resume workout to continue it. Manage routines and edit your schedule from Today.",
        ),
        GuidanceTopic(
            "activeWorkoutBasics",
            "Active workout",
            "Enter weight in lb and reps, then check a set when finished. Mark warm-ups separately; they do not count toward volume or PRs. Entries save locally as you type. Back keeps the draft. Finish workout reviews the session before saving it to History; discarding requires confirmation. Use last or target sets to reuse previous entries; replacing entered sets asks first.",
        ),
        GuidanceTopic(
            "rpeBasics",
            "RPE explained",
            "RPE describes effort: 8 means about two good reps left, 9 about one, and 10 no good reps left. Leave it blank if unsure. You can turn RPE off in You → Edit preferences & goal. Hiding it keeps existing values; progression suggestions then ignore effort.",
        ),
        GuidanceTopic(
            "routineEditorBasics",
            "Routines",
            "Routines hold ordered exercise names, not a completed workout. Create or edit a routine and save explicitly. Changes affect future sessions; your active workout keeps its own exercises. Starting a routine loads previous sets with completion unchecked. Starter routines can be reviewed before adding them.",
        ),
        GuidanceTopic(
            "historyBasics",
            "History",
            "Full workout shows saved notes, tags and every set. Edit or delete there; both actions protect against stale changes. Saved unchecked working sets still count toward volume and PRs. Active drafts are separate and stay out of History and normal backups.",
        ),
        GuidanceTopic(
            "statsBasics",
            "Stats & PRs",
            "Stats use saved History, excluding warm-ups. Best load, best set volume and estimated 1RM can come from different sets; 1RM is an estimate. The weekly goal counts workouts over the last seven days. The activity view uses Monday–Sunday. Exercise names are grouped exactly as saved, including case.",
        ),
        GuidanceTopic(
            "exerciseGuideBasics",
            "Exercise guides",
            "Search the offline catalog or keep your own exercise name. Guides are reference instructions for the listed movement and equipment. A reference match never renames your saved history or merges PRs. Some custom names have no verified guide. Keep reps controlled and stop if a movement causes sharp pain.",
        ),
    )

fun guidanceEditable(root: JsonObject): Boolean =
    (root["schemaVersion"] as? JsonPrimitive)?.intOrNull?.let { it <= 1 } ?: true

private fun experience(root: JsonObject, id: String) =
    (root["experiences"] as? JsonObject)?.get(id) as? JsonObject

fun guidanceVisible(root: JsonObject, id: String): Boolean {
    if (!guidanceEditable(root)) return false
    val record = experience(root, id) ?: return true
    if ((record["contentVersion"] as? JsonPrimitive)?.intOrNull?.let { it > 1 } == true)
        return false
    return (record["status"] as? JsonPrimitive)?.content !in
        listOf("completed", "skipped", "dismissed")
}

/**
 * Device-only native adaptation. Unknown/future content is retained; known tips reset
 * independently.
 */
fun changeGuidance(root: JsonObject, ids: List<String>, status: String): JsonObject {
    check(guidanceEditable(root)) { "Guidance was saved by a newer app. It was kept unchanged." }
    require(status in listOf("unseen", "completed"))
    val records = ((root["experiences"] as? JsonObject)?.toMap() ?: emptyMap()).toMutableMap()
    for (id in ids) {
        require(guidanceTopics.any { it.id == id })
        val before = experience(root, id) ?: JsonObject(emptyMap())
        if ((before["contentVersion"] as? JsonPrimitive)?.intOrNull?.let { it > 1 } == true)
            continue
        records[id] =
            JsonObject(
                before +
                    mapOf("contentVersion" to JsonPrimitive(1), "status" to JsonPrimitive(status))
            )
    }
    return JsonObject(
        root + mapOf("schemaVersion" to JsonPrimitive(1), "experiences" to JsonObject(records))
    )
}
