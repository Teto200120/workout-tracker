package com.hector.workouttracker.feature.session

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hector.workouttracker.core.designsystem.TrackerIcons
import java.math.BigDecimal

/** Keeps direct entry and blank values; stepping never repairs invalid input silently. */
@Composable
internal fun SetEntryField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    step: Double,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maximum: Double = 10000.0,
    minimum: Double = 0.0,
) {
    val number = if (value.isBlank()) 0.0 else value.toDoubleOrNull()
    val valid =
        number != null &&
            number.isFinite() &&
            (value.isBlank() || number in minimum..maximum) &&
            (keyboardOptions.keyboardType != KeyboardType.Number || number % 1.0 == 0.0)
    fun adjust(direction: Int) {
        if (!valid) return
        val next = ((number ?: 0.0) + direction * step).coerceIn(minimum, maximum)
        onValueChange(BigDecimal.valueOf(next).stripTrailingZeros().toPlainString())
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value,
            onValueChange,
            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
            singleLine = true,
            keyboardOptions = keyboardOptions,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            FilledTonalIconButton(
                onClick = { adjust(-1) },
                enabled = enabled && valid && (number ?: 0.0) > minimum,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(TrackerIcons.Remove, "Decrease $label")
            }
            FilledTonalIconButton(
                onClick = { adjust(1) },
                enabled = enabled && valid && (number ?: maximum) < maximum,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(TrackerIcons.Add, "Increase $label")
            }
        }
    }
}
