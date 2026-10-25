package com.hector.workouttracker.feature.feedback

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

internal const val HOSTED_FEEDBACK_URL =
    "https://teto200120.github.io/workout-tracker/?feedback=android"

internal fun openFeedbackBrowser(context: Context): Boolean =
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(HOSTED_FEEDBACK_URL))
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
