package com.hector.workouttracker.feature.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (firstRun) "Bring your workouts with you" else "Your backup",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            "In the PWA, finish and save any active workout, then export a JSON backup. Choose that file here to review it before importing. This is a one-time copy, not ongoing sync."
        )
        OutlinedButton(
            onClick = {
                open.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            },
            enabled = !state.busy && state.draft == null,
        ) {
            Text("Import PWA backup")
        }
        if (state.draft != null) Text("Finish or discard your active workout before importing.")
        if (!firstRun)
            OutlinedButton(
                onClick = { create.launch("WorkoutTracker-${LocalDate.now()}.json") },
                enabled = !state.busy,
            ) {
                Text("Export backup")
            }
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
        title = { Text("Review backup import") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
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
                    "Imported routines and goals are preserved and included in exports. Their native screens are still being built. RPE preferences never erase historical RPE values."
                )
            }
        },
        confirmButton = { Button(onClick = confirm) { Text("Import backup") } },
        dismissButton = { TextButton(onClick = cancel) { Text("Cancel") } },
    )
}
