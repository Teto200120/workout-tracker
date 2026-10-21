package com.hector.workouttracker.core.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun Heading(eyebrow: String, title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            eyebrow.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
