package com.hector.workouttracker.feature.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.hector.workouttracker.core.designsystem.TrackerIcons
import com.hector.workouttracker.data.ImportCounts
import com.hector.workouttracker.data.ImportPreview
import com.hector.workouttracker.feature.session.*
import java.time.LocalDate

@Composable
internal fun BackupControls(state: TrackerState, vm: TrackerViewModel, firstRun: Boolean = false) {
    val open =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(vm::importBackup)
        }
    val create =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            uri?.let(vm::exportBackup)
        }
    var instructions by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (firstRun) "Bring your workouts with you" else "Your backup",
            style = MaterialTheme.typography.titleLarge,
        )
        if (!firstRun) BackupStatusCard(state.backup)
        val migrationInstructions =
            "In the PWA, finish and save any active workout, then export a JSON backup. Choose that file here to review it before importing. This is a one-time copy, not ongoing sync."
        if (firstRun)
            Text(
                "Finish and save any PWA workout, then import its JSON backup. This copies saved data without syncing the apps.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        else {
            Text(
                "Keep a JSON copy of your saved training, or review a backup before restoring it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(
                onClick = { create.launch("WorkoutTracker-${LocalDate.now()}.json") },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(TrackerIcons.Backup, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Export backup")
            }
        }
        OutlinedButton(
            onClick = {
                open.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            },
            enabled = !state.busy && state.draft == null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("Import PWA backup")
        }
        if (state.draft != null) Text("Finish or discard your active workout before importing.")
        TextButton(onClick = { instructions = !instructions }) {
            Text(if (instructions) "Hide import instructions" else "Import instructions")
        }
        if (instructions) Text(migrationInstructions, style = MaterialTheme.typography.bodyMedium)
        state.backupMessage?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!firstRun)
            Text(
                "Backups include saved workouts, routines, legacy weights, settings and goals. Active workouts are excluded. Keep your PWA backup until you have checked the imported history.",
                style = MaterialTheme.typography.bodySmall,
            )
    }
}

@Composable
internal fun ImportDialog(preview: ImportPreview, confirm: () -> Unit, cancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = cancel,
        modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 560.dp).fillMaxWidth(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Review backup import") },
        text = {
            Column(
                Modifier.testTag("import-review-content").verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "Backup version ${preview.data.backupVersion} · data schema ${preview.data.schemaVersion}"
                )
                fun summary(label: String, count: ImportCounts) =
                    "$label: ${count.added} new, ${count.replaced} replaced, ${count.unchanged} unchanged"
                Text(summary("Workouts", preview.workouts))
                Text(summary("Routines", preview.routines))
                Text(summary("Legacy weights", preview.weights))
                Text(
                    "Matching IDs with changed content will be replaced. Number IDs and text IDs remain distinct. Other saved records are kept."
                )
                Text(
                    if (preview.settingsSupplied) "Supplied settings will replace your preferences."
                    else
                        "No settings supplied. Your preferences are kept; you will be asked to set your name again, matching the PWA migration rules."
                )
                Text(
                    "Changed preferences: ${preview.changedPreferences.joinToString().ifEmpty { "none" }}"
                )
                Text("Weekly goal after import: ${preview.weeklyGoal}")
                Text(
                    "Name after import: ${preview.displayName ?: "setup required"}\nTrack RPE: ${if (preview.trackRpe) "on" else "off"}"
                )
                Text(
                    "Goals: ${if (preview.goalsSupplied) "replace with backup" else "keep existing"}\nBackup metadata: ${if (preview.metadataSupplied) "replace with backup" else "keep existing"}"
                )
                Text(
                    "Imported routines and goals are available in Routines and Stats, and included in exports. RPE preferences never erase historical RPE values. Guidance preferences stay on this device."
                )
            }
        },
        confirmButton = { Button(onClick = confirm) { Text("Import backup") } },
        dismissButton = { TextButton(onClick = cancel) { Text("Cancel") } },
    )
}
