package desktopkit.components

import desktopkit.theme.rememberDeskHaptics
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import desktopkit.theme.DeskMotion
import desktopkit.theme.DeskTheme

/** Row of mutually exclusive options (theme, density, view mode). */
@Composable
fun <T> SegmentedChoice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val outer = RoundedCornerShape(dimens.radiusControl + 2.dp)
    val haptics = rememberDeskHaptics()
    Row(
        modifier = modifier
            .background(colors.surface, outer)
            .border(1.dp, colors.border, outer)
            .padding(2.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            val interaction = remember { MutableInteractionSource() }
            val state = interaction.collectState()
            val isSelected = value == selected
            val shape = RoundedCornerShape(dimens.radiusControl)
            val background by animatedStateBackground(state, isSelected)
            val labelColor by animateColorAsState(if (isSelected) colors.onSelection else colors.textSecondary, DeskMotion.color(),
                label = "segmentLabel")
            Box(
                modifier = Modifier
                    .heightIn(min = dimens.minTarget.coerceAtLeast(dimens.controlHeight))
                    .pressScale(state.pressed)
                    .focusRing(state, shape)
                    .background(background, shape)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .selectable(selected = isSelected, interactionSource = interaction, indication = null, role = Role.RadioButton) {
                        if (!isSelected) haptics.tick()
                        onSelect(value)
                    }
                    .padding(horizontal = dimens.gapM),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = DeskTheme.type.label, color = labelColor, maxLines = 1)
            }
        }
    }
}

/** Colour swatches for the accent choice. */
@Composable
fun <T> SwatchChoice(options: List<Triple<T, Color, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val colors = DeskTheme.colors
    Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        options.forEach { (value, swatch, label) ->
            val interaction = remember { MutableInteractionSource() }
            val state = interaction.collectState()
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .size(DeskTheme.dimens.minTarget.coerceAtLeast(32.dp))
                    .focusRing(state, CircleShape)
                    .background(stateBackground(state), CircleShape) // hover and press: a halo around the swatch
                    .pointerHoverIcon(PointerIcon.Hand)
                    .semantics { contentDescription = label }
                    .selectable(selected = isSelected, interactionSource = interaction, indication = null, role = Role.RadioButton) {
                        onSelect(value)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(26.dp)
                        .background(swatch, CircleShape)
                        .border(if (isSelected) 2.dp else 1.dp, if (isSelected) colors.text else colors.borderStrong, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) Icon(DeskIcons.Check, contentDescription = null, tint = Color(0xFF0E1420), modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

/**
 * Label and control on one settings line. When the control does not fit beside the label (a long
 * segmented choice on a phone), the control moves under the label instead of squeezing it.
 */
@Composable
fun SettingRow(title: String, modifier: Modifier = Modifier, subtitle: String? = null, control: @Composable () -> Unit) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val gap = dimens.gapL
    val stackGap = dimens.gapS
    Layout(
        modifier = modifier.padding(vertical = dimens.gapS),
        content = {
            androidx.compose.foundation.layout.Column {
                Text(title, style = DeskTheme.type.bodyStrong, color = colors.text)
                if (subtitle != null) Text(subtitle, style = DeskTheme.type.labelSmall, color = colors.textTertiary)
            }
            Box { control() }
        },
    ) { measurables, constraints ->
        val label = measurables[0]
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val controlPlaceable = measurables[1].measure(loose)
        val gapPx = gap.roundToPx()
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val minLabel = minOf(label.maxIntrinsicWidth(constraints.maxHeight), 120.dp.roundToPx())
        val sideBySide = !constraints.hasBoundedWidth || controlPlaceable.width + gapPx + minLabel <= width
        if (sideBySide) {
            val labelMax = if (constraints.hasBoundedWidth) (width - controlPlaceable.width - gapPx).coerceAtLeast(0) else Constraints.Infinity
            val labelPlaceable = label.measure(loose.copy(maxWidth = labelMax))
            val rowWidth = if (constraints.hasBoundedWidth) width else labelPlaceable.width + gapPx + controlPlaceable.width
            val height = maxOf(labelPlaceable.height, controlPlaceable.height).coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(rowWidth, height) {
                labelPlaceable.placeRelative(0, (height - labelPlaceable.height) / 2)
                controlPlaceable.placeRelative(rowWidth - controlPlaceable.width, (height - controlPlaceable.height) / 2)
            }
        } else {
            val labelPlaceable = label.measure(loose.copy(maxWidth = width))
            val between = stackGap.roundToPx()
            val height = (labelPlaceable.height + between + controlPlaceable.height).coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(width, height) {
                labelPlaceable.placeRelative(0, 0)
                controlPlaceable.placeRelative(0, labelPlaceable.height + between)
            }
        }
    }
}
