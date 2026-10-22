package com.hector.workouttracker.core.schedule

import com.hector.workouttracker.core.backup.BackupCodec
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

val scheduleDayNames =
    listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
val scheduleKinds = listOf("gym", "rest", "soccer")

@Serializable data class ScheduleDay(val kind: String, val routine: String)

data class DayPlan(val kind: String, val title: String, val routine: String, val note: String)

/** Compatibility indexes are Sunday=0; no timezone conversion is applied to a local date. */
fun scheduleDays(settings: JsonObject = BackupCodec.defaultSettings): List<ScheduleDay> {
    val saved = settings["schedule"]?.jsonObject
    val defaults = BackupCodec.defaultSettings.getValue("schedule").jsonObject
    return (0..6).map { index ->
        val entry = (saved?.get(index.toString()) ?: defaults.getValue(index.toString())).jsonObject
        val kind = entry.getValue("kind").jsonPrimitive.content
        require(kind in scheduleKinds)
        ScheduleDay(kind, entry.getValue("routine").jsonPrimitive.content)
    }
}

fun resolveDayPlan(date: LocalDate, days: List<ScheduleDay>): DayPlan {
    require(days.size == 7)
    val index = date.dayOfWeek.value % 7
    val day = days[index]
    val routine = day.routine.ifEmpty { scheduleDays()[index].routine.ifEmpty { "Custom" } }
    return DayPlan(
        day.kind,
        when (day.kind) {
            "gym" -> routine
            "soccer" -> "Soccer Day"
            else -> "Rest Day"
        },
        routine,
        when (day.kind) {
            "gym" -> "Suggested from your ${scheduleDayNames[index]} schedule."
            "soccer" ->
                "Soccer is treated separately, since you do not track it as a gym workout here."
            else -> "No gym workout scheduled. Optional recovery, mobility, or custom workout."
        },
    )
}
