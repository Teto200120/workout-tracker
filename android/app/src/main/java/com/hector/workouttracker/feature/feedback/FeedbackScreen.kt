package com.hector.workouttracker.feature.feedback

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hector.workouttracker.BuildConfig
import com.hector.workouttracker.core.designsystem.Heading

@Composable
internal fun FeedbackScreen(
    back: () -> Unit,
    openBrowser: (Context) -> Boolean = ::openFeedbackBrowser,
) {
    val context = LocalContext.current
    var failed by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().testTag("feedback-list"),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TextButton(onClick = back) { Text("Back to You") }
            Heading("Help improve the app", "Beta feedback")
        }
        item {
            Text("Send feedback in your browser", style = MaterialTheme.typography.titleLarge)
            Text(
                "The feedback page uses browser verification. Connect to the internet to open it and send your report."
            )
        }
        item {
            Text(
                "Include Workout Tracker ${BuildConfig.VERSION_NAME}, what happened and the steps to reproduce it. You can choose to attach a screenshot."
            )
        }
        item {
            Text(
                "Reports are saved in that browser before sending. If sending fails, reopen feedback in the same browser to retry or delete the pending report. Avoid private browsing if you want to keep pending reports."
            )
        }
        item {
            Text(
                "Android workouts, backups and device diagnostics are not attached automatically. Optional diagnostics describe the browser page. Clearing local workout data in this app does not delete browser reports."
            )
        }
        item {
            if (failed) {
                Text(
                    "A browser could not be opened. Enable a browser and try again. Your workout is unchanged.",
                    Modifier.testTag("feedback-open-error"),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = { failed = !openBrowser(context) },
                modifier = Modifier.fillMaxWidth().testTag("open-feedback-browser"),
            ) {
                Text(if (failed) "Try opening browser again" else "Open feedback in browser")
            }
            Text("Use Android Back to return to your workout after feedback.")
        }
    }
}
