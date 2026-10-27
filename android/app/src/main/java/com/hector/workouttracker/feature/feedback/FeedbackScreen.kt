package com.hector.workouttracker.feature.feedback

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hector.workouttracker.BuildConfig
import com.hector.workouttracker.core.designsystem.*

@Composable
internal fun FeedbackScreen(
    back: () -> Unit,
    openBrowser: (Context) -> Boolean = ::openFeedbackBrowser,
) {
    val context = LocalContext.current
    var failed by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().background(trackerScreenBackground()).testTag("feedback-list"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TextButton(onClick = back) { Text("Back to settings") }
            Text("Beta feedback", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Help improve Workout Tracker.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = trackerPanelColor()),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(
                        TrackerIcons.Feedback,
                        null,
                        Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Send feedback in your browser",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "The feedback page uses browser verification. Connect to the internet to open it and send your report.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (failed)
                        Text(
                            "A browser could not be opened. Enable a browser and try again. Your workout is unchanged.",
                            Modifier.testTag("feedback-open-error"),
                            color = MaterialTheme.colorScheme.error,
                        )
                    Button(
                        onClick = { failed = !openBrowser(context) },
                        shape = RoundedCornerShape(16.dp),
                        modifier =
                            Modifier.fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .testTag("open-feedback-browser"),
                    ) {
                        Text(
                            if (failed) "Try opening browser again" else "Open feedback in browser"
                        )
                    }
                }
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = trackerPanelColor()),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("Before you send", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Include Workout Tracker ${BuildConfig.VERSION_NAME}, what happened and the steps to reproduce it. You can choose to attach a screenshot.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Reports are saved in that browser before sending. If sending fails, reopen feedback in the same browser to retry or delete the pending report. Avoid private browsing if you want to keep pending reports.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Android workouts, backups and device diagnostics are not attached automatically. Optional diagnostics describe the browser page. Clearing local workout data in this app does not delete browser reports.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Text(
                "Use Android Back to return to your workout after feedback.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
