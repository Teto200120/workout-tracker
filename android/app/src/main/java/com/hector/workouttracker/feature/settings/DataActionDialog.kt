package com.hector.workouttracker.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.hector.workouttracker.data.*

@Composable
internal fun DataActionDialog(review: DataActionReview, cancel: () -> Unit, confirm: () -> Unit) {
    val reset = review.action == DataAction.RESET_SETTINGS
    AlertDialog(
        onDismissRequest = cancel,
        modifier =
            Modifier.padding(horizontal = 24.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .testTag("data-action-review"),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(if (reset) "Reset app settings?" else "Clear local data?") },
        text = {
            Column(
                Modifier.testTag("data-action-content").verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (reset) {
                    Text(
                        "Restore the default weekly schedule, load jump, rep ranges, RPE tracking, haptics and animations."
                    )
                    Text(
                        "Keep your display name, weekly goal, workouts, routines, legacy weight records, backup reminder, guidance and active workout, including entered RPE values."
                    )
                } else {
                    Text(
                        "Remove ${review.workouts} saved workouts, ${review.routines} routines and ${review.legacyWeights} legacy weight records from this device."
                    )
                    Text(
                        if (review.hasDraft)
                            "Your active workout and all its entered sets will also be removed."
                        else "There is no active workout to remove."
                    )
                    Text(
                        "Remove your profile, custom preferences, weekly goal, backup reminder and guidance progress. Restore the six starter routines, then return to setup."
                    )
                    Text(
                        "This cannot be undone in the app. Export a backup first if you want to keep saved records. Backups do not include the active workout. Files you already exported and your PWA data stay unchanged."
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = confirm, modifier = Modifier.testTag("confirm-data-action")) {
                Text(if (reset) "Reset settings" else "Clear and start over")
            }
        },
        dismissButton = {
            TextButton(onClick = cancel, modifier = Modifier.testTag("cancel-data-action")) {
                Text("Keep my data")
            }
        },
    )
}
