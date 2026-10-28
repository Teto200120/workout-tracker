package com.hector.workouttracker.feature.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    detail: Boolean = false,
    maximum: Double = 10000.0,
    minimum: Double = 0.0,
    onFocused: () -> Unit = {},
    animations: Boolean = true,
    secondary: Boolean = false,
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
    val colors = MaterialTheme.colorScheme
    @Composable
    fun Step(direction: Int) {
        WorkoutIconButton(
            onClick = {
                onFocused()
                adjust(direction)
            },
            enabled =
                enabled &&
                    valid &&
                    if (direction < 0) (number ?: 0.0) > minimum else (number ?: maximum) < maximum,
            animations = animations,
            detail = detail,
        ) {
            Icon(
                if (direction < 0) WorkoutIcons.Minus else WorkoutIcons.Plus,
                "${if(direction < 0) "Decrease" else "Increase"} $label",
                Modifier.size(22.dp),
            )
        }
    }
    val numberStyle =
        if (secondary) MaterialTheme.typography.titleLarge
        else MaterialTheme.typography.headlineSmall
    @Composable
    fun Value(modifier: Modifier) {
        var focused by remember { mutableStateOf(false) }
        val accent = workoutAccent()
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            enabled = enabled,
            keyboardOptions = keyboardOptions,
            textStyle =
                numberStyle.copy(
                    textAlign = TextAlign.Center,
                    fontFeatureSettings = "tnum",
                    color =
                        if (!enabled) colors.onSurface.copy(alpha = .38f)
                        else if (secondary) colors.onSurfaceVariant else colors.onSurface,
                ),
            cursorBrush = SolidColor(accent),
            modifier =
                modifier
                    .heightIn(min = 56.dp)
                    .background(
                        if (focused) accent.copy(alpha = .05f) else Color.Transparent,
                        RoundedCornerShape(14.dp),
                    )
                    .border(
                        1.dp,
                        if (focused) accent.copy(alpha = .34f) else Color.Transparent,
                        RoundedCornerShape(14.dp),
                    )
                    .onFocusChanged {
                        focused = it.isFocused
                        if (it.isFocused) onFocused()
                    }
                    .semantics { contentDescription = label },
            decorationBox = { entry ->
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (value.isEmpty())
                        Text(
                            "-",
                            Modifier.clearAndSetSemantics {},
                            style = numberStyle,
                            color = colors.onSurfaceVariant,
                        )
                    entry()
                }
            },
        )
    }
    @Composable
    fun Label(modifier: Modifier = Modifier) {
        // The editable value already carries this accessible name.
        Text(
            label,
            modifier.clearAndSetSemantics {},
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
    }
    if (stacked)
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Step(-1)
                Value(Modifier.weight(1f))
                Step(1)
            }
        }
    else
        Row(
            modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Label(Modifier.weight(1f))
            Step(-1)
            Value(Modifier.width(if (detail) 72.dp else 64.dp))
            Step(1)
        }
}
