package com.hector.workouttracker.feature.session

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
    compact: Boolean = false,
    maximum: Double = 10000.0,
    minimum: Double = 0.0,
    onFocused: () -> Unit = {},
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
    val stacked = LocalDensity.current.fontScale >= 1.5f
    @Composable
    fun Entry(modifier: Modifier) {
        TextField(
            value,
            onValueChange,
            label = {
                Text(
                    label,
                    Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            },
            singleLine = true,
            textStyle =
                MaterialTheme.typography.headlineSmall.copy(
                    textAlign = TextAlign.Center,
                    fontFeatureSettings = "tnum",
                ),
            keyboardOptions = keyboardOptions,
            enabled = enabled,
            shape = RoundedCornerShape(18.dp),
            colors =
                TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .07f),
                    unfocusedContainerColor =
                        MaterialTheme.colorScheme.onSurface.copy(alpha = .04f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            modifier =
                modifier.heightIn(min = 68.dp).onFocusChanged { if (it.isFocused) onFocused() },
        )
    }
    @Composable
    fun Step(direction: Int) {
        IconButton(
            onClick = {
                onFocused()
                adjust(direction)
            },
            enabled =
                enabled &&
                    valid &&
                    if (direction < 0) (number ?: 0.0) > minimum else (number ?: maximum) < maximum,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                if (direction < 0) TrackerIcons.Remove else TrackerIcons.Add,
                "${if(direction < 0) "Decrease" else "Increase"} $label",
            )
        }
    }
    if (stacked || compact)
        Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Entry(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Step(-1)
                Step(1)
            }
        }
    else
        Row(
            modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Step(-1)
            Entry(Modifier.weight(1f))
            Step(1)
        }
}
