package com.animecliplabs.clipmark.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.Gradient
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.TextFormat
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.animecliplabs.clipmark.R
import com.animecliplabs.clipmark.core.analysis.Readability
import com.animecliplabs.clipmark.core.model.Anchor
import com.animecliplabs.clipmark.core.model.SizeLevel
import com.animecliplabs.clipmark.core.model.WatermarkFont
import com.animecliplabs.clipmark.core.model.WatermarkStyle
import com.animecliplabs.clipmark.image.WatermarkRenderer
import com.animecliplabs.clipmark.ui.common.SwitchRow
import com.animecliplabs.clipmark.ui.common.anchorLabel
import com.animecliplabs.clipmark.ui.common.sizeLabel
import kotlin.math.roundToInt

private val Swatches = listOf(
    0xFFFFFFFF, 0xFF111111, 0xFFF4F1EA, 0xFF9E9E9E, 0xFFFFD166, 0xFFFF7AA2, 0xFFB9A7FF, 0xFF7FD6FF,
).map { it.toInt() }

@Composable
fun PositionControls(
    style: WatermarkStyle,
    smartAnchor: Anchor?,
    onStyle: ((WatermarkStyle) -> WatermarkStyle) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            PositionGrid(
                selected = style.anchor.takeIf { style.customCenter == null },
                smartPick = smartAnchor,
                onSelect = { anchor -> onStyle { it.copy(anchor = anchor, customCenter = null) } },
            )
            Column(Modifier.weight(1f)) {
                LabeledSlider(
                    label = stringResource(R.string.margin_side),
                    valueText = "${"%.1f".format(style.marginXPercent)}%",
                    value = style.marginXPercent,
                    range = WatermarkStyle.MIN_MARGIN..WatermarkStyle.MAX_MARGIN,
                    onChange = { v -> onStyle { it.copy(marginXPercent = v) } },
                )
                LabeledSlider(
                    label = stringResource(R.string.margin_vertical),
                    valueText = "${"%.1f".format(style.marginYPercent)}%",
                    value = style.marginYPercent,
                    range = WatermarkStyle.MIN_MARGIN..WatermarkStyle.MAX_MARGIN,
                    onChange = { v -> onStyle { it.copy(marginYPercent = v) } },
                )
            }
        }
        if (style.customCenter != null) {
            AssistChip(
                onClick = { onStyle { it.copy(customCenter = null) } },
                label = { Text(stringResource(R.string.custom_position_reset)) },
                leadingIcon = { Icon(Icons.Outlined.OpenWith, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        SwitchRow(
            title = stringResource(R.string.smart_corner),
            description = stringResource(R.string.smart_corner_desc),
            icon = Icons.Outlined.AutoAwesome,
            checked = style.smartCorner,
            onCheckedChange = { on -> onStyle { it.copy(smartCorner = on, customCenter = if (on) null else it.customCenter) } },
        )
        Text(
            stringResource(R.string.drag_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

/**
 * 3×3 quick positions. The selected cell shows a check (not just colour); a Smart Corner pick
 * is outlined so the user can see where it landed.
 */
@Composable
private fun PositionGrid(selected: Anchor?, smartPick: Anchor?, onSelect: (Anchor) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (row in 0..2) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (col in 0..2) {
                        val anchor = Anchor.at(col, row)
                        val isSelected = anchor == selected && smartPick == null
                        val isSmart = anchor == smartPick
                        val label = anchorLabel(anchor)
                        val stateText = when {
                            isSelected -> stringResource(R.string.a11y_selected)
                            isSmart -> stringResource(R.string.a11y_smart_pick)
                            else -> ""
                        }
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .then(
                                    if (isSmart) Modifier.border(2.dp, MaterialTheme.colorScheme.tertiary, RoundedCornerShape(10.dp))
                                    else Modifier,
                                )
                                .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(anchor) })
                                .semantics {
                                    contentDescription = label
                                    stateDescription = stateText
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                            } else {
                                Box(
                                    Modifier.size(if (isSmart) 10.dp else 6.dp).clip(CircleShape)
                                        .background(if (isSmart) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StyleControls(
    style: WatermarkStyle,
    renderer: WatermarkRenderer,
    onStyle: ((WatermarkStyle) -> WatermarkStyle) -> Unit,
) {
    val inset = Modifier.padding(horizontal = 16.dp)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = style.text,
            onValueChange = { v -> onStyle { it.copy(text = v.take(60)) } },
            label = { Text(stringResource(R.string.watermark_text)) },
            singleLine = true,
            isError = style.text.isBlank(),
            supportingText = if (style.text.isBlank()) {
                { Text(stringResource(R.string.error_empty_text)) }
            } else null,
            leadingIcon = { Icon(Icons.Outlined.TextFormat, contentDescription = null) },
            trailingIcon = if (style.text != WatermarkStyle.DEFAULT_TEXT) {
                {
                    IconButton(onClick = { onStyle { it.copy(text = WatermarkStyle.DEFAULT_TEXT) } }) {
                        Icon(Icons.Outlined.RestartAlt, stringResource(R.string.reset_text))
                    }
                }
            } else null,
            modifier = inset.fillMaxWidth(),
        )

        Text(stringResource(R.string.size), style = MaterialTheme.typography.labelLarge, modifier = inset)
        SingleChoiceSegmentedButtonRow(inset.fillMaxWidth()) {
            SizeLevel.entries.forEachIndexed { i, level ->
                SegmentedButton(
                    selected = style.sizeLevel == level && kotlin.math.abs(style.sizePercent - level.percent) < 0.05f,
                    onClick = { onStyle { it.copy(sizePercent = level.percent) } },
                    shape = SegmentedButtonDefaults.itemShape(i, SizeLevel.entries.size),
                ) { Text(sizeLabel(level)) }
            }
        }
        LabeledSlider(
            modifier = inset,
            label = stringResource(R.string.size_fine),
            valueText = "${"%.1f".format(style.sizePercent)}%",
            value = style.sizePercent,
            range = WatermarkStyle.MIN_SIZE..WatermarkStyle.MAX_SIZE,
            onChange = { v -> onStyle { it.copy(sizePercent = v) } },
        )

        Text(stringResource(R.string.font), style = MaterialTheme.typography.labelLarge, modifier = inset)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(WatermarkFont.entries) { font ->
                val family = remember(font) { FontFamily(renderer.typeface(font, false)) }
                FilterChip(
                    selected = style.font == font,
                    onClick = { onStyle { it.copy(font = font) } },
                    label = { Text(fontLabel(font), fontFamily = family) },
                    leadingIcon = if (style.font == font) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                )
            }
        }
        SwitchRow(
            title = stringResource(R.string.bold),
            icon = Icons.Outlined.FormatBold,
            checked = style.bold,
            onCheckedChange = { on -> onStyle { it.copy(bold = on) } },
        )

        Text(stringResource(R.string.color), style = MaterialTheme.typography.labelLarge, modifier = inset)
        FlowRow(modifier = inset, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Swatches.forEach { argb ->
                val selected = (style.colorArgb or OPAQUE) == argb
                val color = Color(argb)
                val name = colorName(argb)
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant), CircleShape)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onStyle { it.copy(colorArgb = argb) } })
                        .semantics { contentDescription = name },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp),
                            tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                        )
                    }
                }
            }
        }

        LabeledSlider(
            modifier = inset,
            label = stringResource(R.string.opacity),
            valueText = "${(style.opacity * 100).roundToInt()}%",
            value = style.opacity,
            range = WatermarkStyle.MIN_OPACITY..1f,
            onChange = { v -> onStyle { it.copy(opacity = v) } },
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
fun ReadabilityControls(
    style: WatermarkStyle,
    readability: Readability,
    onStyle: ((WatermarkStyle) -> WatermarkStyle) -> Unit,
) {
    Column {
        SwitchRow(
            title = stringResource(R.string.shadow),
            description = stringResource(R.string.shadow_desc),
            icon = Icons.Outlined.Gradient,
            checked = style.shadow,
            onCheckedChange = { on -> onStyle { it.copy(shadow = on) } },
        )
        SwitchRow(
            title = stringResource(R.string.outline),
            description = stringResource(R.string.outline_desc),
            icon = Icons.Outlined.TextFormat,
            checked = style.outline,
            onCheckedChange = { on -> onStyle { it.copy(outline = on) } },
        )
        SwitchRow(
            title = stringResource(R.string.contrast_assist),
            description = stringResource(R.string.contrast_assist_desc),
            icon = Icons.Outlined.Contrast,
            checked = style.contrastAssist,
            onCheckedChange = { on -> onStyle { it.copy(contrastAssist = on) } },
        )
        if (style.contrastAssist) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (readability.assisted) Icons.Outlined.AutoAwesome else Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(if (readability.assisted) R.string.assist_active else R.string.assist_idle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    modifier: Modifier = Modifier,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.semantics { contentDescription = label; stateDescription = valueText },
        )
    }
}

@Composable
private fun fontLabel(font: WatermarkFont): String = stringResource(
    when (font) {
        WatermarkFont.CLEAN -> R.string.font_clean
        WatermarkFont.MEDIUM -> R.string.font_medium
        WatermarkFont.CONDENSED -> R.string.font_condensed
        WatermarkFont.SERIF -> R.string.font_serif
        WatermarkFont.MONO -> R.string.font_mono
        WatermarkFont.SCRIPT -> R.string.font_script
        WatermarkFont.CASUAL -> R.string.font_casual
    },
)

@Composable
private fun colorName(argb: Int): String = stringResource(
    when (Swatches.indexOf(argb)) {
        0 -> R.string.color_white
        1 -> R.string.color_black
        2 -> R.string.color_ivory
        3 -> R.string.color_gray
        4 -> R.string.color_gold
        5 -> R.string.color_pink
        6 -> R.string.color_lavender
        else -> R.string.color_sky
    },
)

private const val OPAQUE = 0xFF000000.toInt()
