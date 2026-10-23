package desktopkit.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import desktopkit.shell.WidthClass
import desktopkit.shell.currentWidthClass
import desktopkit.theme.DeskMotion
import desktopkit.theme.DeskTheme
import desktopkit.theme.InputMode

/**
 * A filter chip: a pill with a label, an optional icon and an optional count ("12"). Selected, it
 * takes the selection colour and a check mark slides in. 28 dp tall with a pointer, 36 dp on touch
 * (with a 48 dp touch target). Use several of them in a [ChipRow], or [FilterChips] for a list.
 */
@Composable
fun DeskChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    count: String? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = DeskTheme.colors
    val touch = DeskTheme.inputMode == InputMode.Touch
    val shape = RoundedCornerShape(50)
    val iconSize = if (touch) 18.dp else 14.dp
    val background by animatedStateBackground(state, selected, rest = colors.surface)
    val border by animateColorAsState(if (selected) colors.selection else colors.borderStrong, DeskMotion.color(), label = "chipBorder")
    val content by animateColorAsState(if (selected) colors.onSelection else colors.textSecondary, DeskMotion.color(), label = "chipText")
    Row(
        modifier = modifier
            .then(if (touch) Modifier.minimumInteractiveComponentSize() else Modifier)
            .height(if (touch) 36.dp else 28.dp)
            .pressScale(state.pressed)
            .focusRing(state, shape)
            .background(background, shape)
            .border(1.dp, border, shape)
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interaction, enabled)
            .toggleable(
                value = selected,
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { onClick() },
            )
            .padding(start = if (touch) 12.dp else 10.dp, end = if (touch) 14.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(tween(DeskMotion.MEDIUM_MS, easing = DeskMotion.EmphasizedDecelerate)) +
                fadeIn(tween(DeskMotion.MEDIUM_MS)),
            exit = shrinkHorizontally(tween(DeskMotion.SHORT_MS + 50, easing = DeskMotion.EmphasizedAccelerate)) +
                fadeOut(tween(DeskMotion.SHORT_MS)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(DeskIcons.Check, contentDescription = null, tint = content, modifier = Modifier.size(iconSize))
                Spacer(Modifier.width(6.dp))
            }
        }
        if (icon != null && !selected) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(iconSize))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = DeskTheme.type.label, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text(count, style = DeskTheme.type.mono, color = if (selected) content else colors.textTertiary, maxLines = 1)
        }
    }
}

/**
 * Lays out chips (or any small controls) responsively:
 * - [wrap] true (tablet, DeX, any window wider than a phone): they wrap onto as many lines as needed;
 * - [wrap] false (phones by default): one line that scrolls sideways, so filters never take two or
 *   three lines of a small screen. The ends fade out while there is more to scroll to.
 *
 * [contentPadding] is the space before the first and after the last chip; in the scrolling line the
 * chips pass under it up to the edge of the screen, as in Android's own chip rows. [fadeColor] is the
 * surface the row sits on (the page background by default); the ends fade into it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(
    modifier: Modifier = Modifier,
    wrap: Boolean = currentWidthClass() != WidthClass.Compact,
    contentPadding: Dp = DeskTheme.dimens.pagePadding,
    spacing: Dp = 8.dp,
    fadeColor: Color = DeskTheme.colors.background,
    content: @Composable () -> Unit,
) {
    val touch = DeskTheme.inputMode == InputMode.Touch
    if (wrap) {
        FlowRow(
            modifier = modifier.fillMaxWidth().padding(horizontal = contentPadding),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            // touch chips already carry 6 dp above and below in their 48 dp target
            verticalArrangement = Arrangement.spacedBy(if (touch) 0.dp else spacing),
        ) { content() }
    } else {
        val scroll = rememberScrollState()
        Row(
            modifier = modifier
                .fillMaxWidth()
                .fadingEdges(scroll, contentPadding.coerceAtLeast(16.dp), fadeColor)
                .horizontalScroll(scroll)
                .padding(horizontal = contentPadding),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
}

/** One option of [FilterChips]. */
@Immutable
data class ChipOption<T>(val value: T, val label: String, val icon: ImageVector? = null, val count: String? = null)

/**
 * A set of filter chips: [selected] holds the chosen values, [onToggle] adds or removes one. One
 * sideways-scrolling line on phones, wrapped lines on wider windows (see [ChipRow]).
 */
@Composable
fun <T> FilterChips(
    options: List<ChipOption<T>>,
    selected: Set<T>,
    onToggle: (T) -> Unit,
    modifier: Modifier = Modifier,
    wrap: Boolean = currentWidthClass() != WidthClass.Compact,
    contentPadding: Dp = DeskTheme.dimens.pagePadding,
) {
    ChipRow(modifier = modifier, wrap = wrap, contentPadding = contentPadding) {
        options.forEach { option ->
            DeskChip(
                text = option.label,
                selected = option.value in selected,
                onClick = { onToggle(option.value) },
                icon = option.icon,
                count = option.count,
            )
        }
    }
}

/**
 * Fades the start and end of a sideways-scrolling line into [color] (the surface behind it) while it
 * can scroll that way. Painting the surface colour over the edge needs no offscreen layer, so it
 * looks the same on every renderer.
 */
private fun Modifier.fadingEdges(scroll: ScrollState, length: Dp, color: Color): Modifier = this
    .drawWithContent {
        drawContent()
        val fade = length.toPx().coerceAtMost(size.width / 3f)
        if (scroll.value > 0) {
            drawRect(
                brush = Brush.horizontalGradient(listOf(color, color.copy(alpha = 0f)), startX = 0f, endX = fade),
                topLeft = Offset.Zero,
                size = Size(fade, size.height),
            )
        }
        if (scroll.value < scroll.maxValue) {
            drawRect(
                brush = Brush.horizontalGradient(listOf(color.copy(alpha = 0f), color), startX = size.width - fade, endX = size.width),
                topLeft = Offset(size.width - fade, 0f),
                size = Size(fade, size.height),
            )
        }
    }
