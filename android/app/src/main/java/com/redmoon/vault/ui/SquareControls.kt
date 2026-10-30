package com.redmoon.vault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.unit.dp

// Material3's buttons, chips and dialogs use independent rounded tokens. Keep the
// application policy explicit here rather than relying on MaterialTheme.shapes alone.
@Composable
internal fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RectangleShape,
        content = content,
    )
}

@Composable
internal fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RectangleShape,
        content = content,
    )
}

@Composable
internal fun FilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    androidx.compose.material3.FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RectangleShape,
        content = content,
    )
}

@Composable
internal fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RectangleShape,
        content = content,
    )
}

@Composable
internal fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        shape = RectangleShape,
    )
}

@Composable
internal fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        title = title,
        text = text,
        shape = RectangleShape,
        tonalElevation = androidx.compose.ui.unit.Dp(0f),
    )
}

@Composable
internal fun Checkbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val semantics =
        if (onCheckedChange != null)
            Modifier.toggleable(
                value = checked,
                enabled = enabled,
                role = androidx.compose.ui.semantics.Role.Checkbox,
                onValueChange = onCheckedChange,
            )
        else
            Modifier.semantics {
                role = androidx.compose.ui.semantics.Role.Checkbox
                toggleableState = androidx.compose.ui.state.ToggleableState(checked)
            }
    androidx.compose.foundation.layout.Box(
        modifier.size(48.dp).then(semantics),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        val tint =
            if (checked) androidx.compose.material3.MaterialTheme.colorScheme.primary
            else androidx.compose.material3.MaterialTheme.colorScheme.outline
        androidx.compose.foundation.layout.Box(
            Modifier.size(18.dp)
                .background(
                    if (checked) tint else androidx.compose.ui.graphics.Color.Transparent,
                    RectangleShape,
                )
                .border(1.dp, tint, RectangleShape),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            if (checked)
                androidx.compose.material3.Text(
                    "✓",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
) {
    androidx.compose.material3.Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange,
        steps = steps,
        thumb = {
            androidx.compose.foundation.layout.Box(
                Modifier.width(12.dp)
                    .height(24.dp)
                    .background(
                        androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        RectangleShape,
                    )
            )
        },
        track = { state ->
            val fraction =
                ((state.value - valueRange.start) / (valueRange.endInclusive - valueRange.start))
                    .coerceIn(0f, 1f)
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxWidth()
                    .height(3.dp)
                    .background(
                        androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
                        RectangleShape,
                    )
            ) {
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(
                            androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            RectangleShape,
                        )
                )
            }
        },
    )
}
