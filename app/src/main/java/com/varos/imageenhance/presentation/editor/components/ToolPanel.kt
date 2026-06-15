package com.varos.imageenhance.presentation.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.BorderOuter
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Deblur
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterBAndW
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.varos.imageenhance.domain.model.FilterParameter
import com.varos.imageenhance.domain.model.ImageFilter
import com.varos.imageenhance.domain.model.PipelineSettings

/**
 * Fixed-height editing panel, fully driven by the registered [filters]: one tab
 * per filter, and the selected tab's body renders a single control from that
 * filter's [FilterParameter] (a seek bar, or a switch for toggle parameters).
 *
 * The panel is a constant height ([PANEL_HEIGHT]) so switching tabs never reflows
 * the layout — the preview viewport above keeps exactly the same size.
 */
@Composable
fun ToolPanel(
    filters: List<ImageFilter>,
    selectedFilterId: String,
    onSelect: (String) -> Unit,
    settings: PipelineSettings,
    onChange: (filterId: String, value: Float) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Body: the selected filter's control. Fixed height; only content swaps.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PANEL_HEIGHT)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            val filter = filters.firstOrNull { it.id == selectedFilterId } ?: filters.firstOrNull()
            if (filter != null) {
                val value = settings.valueOf(filter)
                when (filter.parameter.kind) {
                    FilterParameter.Kind.TOGGLE -> ToggleTool(
                        label = filter.displayName,
                        checked = value >= 0.5f,
                        // A toggle has no "drag": change + commit full-res at once.
                        onCheckedChange = {
                            onChange(filter.id, if (it) 1f else 0f)
                            onCommit()
                        },
                    )

                    FilterParameter.Kind.SLIDER -> ToolSlider(
                        label = filter.displayName,
                        value = value,
                        valueRange = filter.parameter.min..filter.parameter.max,
                        display = filter.format(value),
                        onValueChange = { onChange(filter.id, it) }, // live preview
                        onValueChangeFinished = onCommit,             // final render
                    )
                }
            }
        }

        // Tab strip — one tab per registered filter.
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(filters, key = { it.id }) { filter ->
                ToolTab(
                    label = filter.displayName,
                    icon = iconFor(filter.id),
                    selected = filter.id == selectedFilterId,
                    onClick = { onSelect(filter.id) },
                )
            }
        }
    }
}

/** Maps a filter id to a tab icon; unknown (newly added) filters get a default. */
private fun iconFor(filterId: String): ImageVector = when (filterId) {
    "brightness" -> Icons.Filled.Brightness6
    "contrast" -> Icons.Filled.Contrast
    "denoise" -> Icons.Filled.Grain
    "sharpen" -> Icons.Filled.Deblur
    "edge" -> Icons.Filled.BorderOuter
    "blur" -> Icons.Filled.BlurOn
    "grayscale" -> Icons.Filled.Gradient
    "threshold" -> Icons.Filled.FilterBAndW
    "document" -> Icons.Filled.Description
    else -> Icons.Filled.Tune
}

@Composable
private fun ToolTab(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val container =
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val content =
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = container,
        contentColor = content,
    ) {
        Column(
            modifier = Modifier
                .size(width = 76.dp, height = 64.dp)
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ToolSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    display: String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(
                display,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
        )
    }
}

@Composable
private fun ToggleTool(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private val PANEL_HEIGHT = 110.dp
