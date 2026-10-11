package com.inputleaf.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLargeEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp, top = 8.dp),
    )
}

class SettingsGroupScope {
    internal val rows = mutableListOf<@Composable (ListItemShapes) -> Unit>()

    fun row(content: @Composable (shapes: ListItemShapes) -> Unit) {
        rows += content
    }
}

/** A connected group of [SegmentedListItem]s; each row gets its position-aware shape. */
@Composable
fun SettingsGroup(modifier: Modifier = Modifier, content: SettingsGroupScope.() -> Unit) {
    val rows = SettingsGroupScope().apply(content).rows
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        rows.forEachIndexed { index, row -> row(ListItemDefaults.segmentedShapes(index, rows.size)) }
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    shapes: ListItemShapes,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
) = SettingsRowImpl(
    leadingContent = { RowIcon(icon) },
    title = title,
    shapes = shapes,
    subtitle = subtitle,
    onClick = onClick,
    trailingContent = trailingContent,
)

@Composable
fun SettingsRow(
    painter: Painter,
    title: String,
    shapes: ListItemShapes,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
) = SettingsRowImpl(
    leadingContent = {
        CircularAvatar(
            painter = painter,
            size = 40.dp,
            iconSize = 22.dp,
            backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
        )
    },
    title = title,
    shapes = shapes,
    subtitle = subtitle,
    onClick = onClick,
    trailingContent = trailingContent,
)

@Composable
fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shapes: ListItemShapes,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = shapes,
        enabled = enabled,
        leadingContent = { RowIcon(icon) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    ) {
        Text(title)
    }
}

/** Single-choice connected button group, e.g. System / Light / Dark. */
@Composable
fun <T> ConnectedChoiceRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, (value, label) ->
            ToggleButton(
                checked = value == selected,
                onCheckedChange = { onSelect(value) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun RowIcon(icon: ImageVector) {
    CircularAvatar(
        icon = icon,
        size = 40.dp,
        iconSize = 22.dp,
        backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
    )
}

@Composable
private fun SettingsRowImpl(
    leadingContent: @Composable () -> Unit,
    title: String,
    shapes: ListItemShapes,
    subtitle: String?,
    onClick: (() -> Unit)?,
    trailingContent: @Composable (() -> Unit)?,
) {
    val supporting: (@Composable () -> Unit)? = subtitle?.let { { Text(it) } }
    val trailing: (@Composable () -> Unit)? = trailingContent ?: onClick?.let {
        { Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Navigate") }
    }
    if (onClick != null) {
        SegmentedListItem(
            onClick = onClick,
            shapes = shapes,
            leadingContent = leadingContent,
            supportingContent = supporting,
            trailingContent = trailing,
        ) { Text(title) }
    } else {
        SegmentedListItem(
            shapes = shapes,
            leadingContent = leadingContent,
            supportingContent = supporting,
            trailingContent = trailing,
        ) { Text(title) }
    }
}
