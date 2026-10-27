package com.hector.workouttracker.feature.backup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hector.workouttracker.domain.*
import java.time.Instant
import kotlinx.coroutines.*

@Composable
internal fun BackupStatusCard(snapshot: BackupSnapshot?) {
    var now by remember { mutableStateOf(Instant.now()) }
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(Unit) {
        val job =
            scope.launch {
                while (isActive) {
                    now = Instant.now()
                    delay(30_000)
                }
            }
        onPauseOrDispose { job.cancel() }
    }
    val status = snapshot?.let { backupStatus(it, now) }
    Card(
        Modifier.fillMaxWidth().testTag("backup-status"),
        shape = RoundedCornerShape(18.dp),
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                status?.label ?: "Backup status unavailable",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                status?.detail
                    ?: "You can still export your saved data. Reopen the app to retry the status."
            )
            Text(
                "Last recorded export time, including imported metadata. Keep the file and export again after important changes.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
