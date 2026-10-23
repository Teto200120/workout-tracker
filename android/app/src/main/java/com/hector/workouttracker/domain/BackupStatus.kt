package com.hector.workouttracker.domain

import java.time.*

data class BackupSnapshot(val hasData: Boolean, val lastExportedAt: String?)

data class BackupStatus(val label: String, val detail: String, val needsExport: Boolean)

/** PWA elapsed-day thresholds, independent of timezone and calendar-day boundaries. */
fun backupDaysSince(lastExportedAt: String?, now: Instant, zone: ZoneId): Long? =
    lastExportedAt?.let {
        val then =
            runCatching { Instant.parse(it) }.getOrNull()
                ?: runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull()
                ?: runCatching { LocalDateTime.parse(it).atZone(zone).toInstant() }.getOrNull()
                ?: runCatching { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant() }
                    .getOrNull()
        then?.let { value -> Duration.between(value, now).toDays().coerceAtLeast(0) }
    }

fun backupStatus(
    snapshot: BackupSnapshot,
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): BackupStatus {
    val days = backupDaysSince(snapshot.lastExportedAt, now, zone)
    val age =
        when (days) {
            null -> "No backup exported yet"
            0L -> "Last backup: today"
            1L -> "Last backup: yesterday"
            else -> "Last backup: $days days ago"
        }
    return when {
        !snapshot.hasData ->
            BackupStatus(
                "No data",
                "No workout data yet. Backup reminders will appear once there is progress to protect.",
                false,
            )
        days == null ->
            BackupStatus(
                "Backup",
                "$age. Export once so your progress has a safe local copy.",
                true,
            )
        days < 14 -> BackupStatus("Current", "$age. Your local backup is recent.", false)
        days < 30 ->
            BackupStatus(
                "Backup soon",
                "$age. Export when convenient so your local progress stays protected.",
                true,
            )
        else ->
            BackupStatus(
                "Backup now",
                "$age. Export a fresh backup before adding much more data.",
                true,
            )
    }
}
