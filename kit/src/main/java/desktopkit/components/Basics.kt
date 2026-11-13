package desktopkit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import desktopkit.theme.DeskTheme

/**
 * Square icon button with hover, press and focus states. Its size never drops below the touch or
 * pointer target. Its [contentDescription] is also its tooltip, shown when a mouse rests on it or
 * the keyboard focus reaches it, with [shortcut] when there is one ([tooltip] false leaves it out:
 * a button whose name is written next to it).
 */
@Composable
fun DeskIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    size: Dp = DeskTheme.dimens.minTarget.coerceAtLeast(DeskTheme.dimens.controlHeight),
    iconSize: Dp = DeskTheme.dimens.icon,
    tint: Color = if (selected) DeskTheme.colors.onSelection else DeskTheme.colors.textSecondary,
    tooltip: Boolean = true,
    shortcut: String? = null,
) {
    if (tooltip) {
        TooltipArea(contentDescription, modifier, shortcut = shortcut) {
            IconButtonBody(icon, contentDescription, onClick, Modifier, enabled, selected, size, iconSize, tint)
        }
    } else {
        IconButtonBody(icon, contentDescription, onClick, modifier, enabled, selected, size, iconSize, tint)
    }
}

@Composable
private fun IconButtonBody(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    selected: Boolean,
    size: Dp,
    iconSize: Dp,
    tint: Color,
) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(DeskTheme.dimens.radiusControl)
    val background by animatedStateBackground(state, selected)
    Box(
        modifier = modifier
            .size(size)
            .pressScale(state.pressed, 0.92f)
            .focusRing(state, shape)
            .background(background, shape)
            .deskClickable(interaction, onClick, enabled = enabled, onClickLabel = contentDescription),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.4f),
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * Text button. [primary] fills it with the accent colour. [icon] goes before the text,
 * [trailingIcon] after it (a menu button's chevron). [danger] marks an action that cannot be undone
 * ("Remove account", "Empty the bin"): its text, icon and edge take the danger colour and hover
 * and press tint it with that colour; it is never filled, so it never looks like the screen's
 * main action ([primary] is ignored with it).
 */
@Composable
fun DeskButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
    trailingIcon: ImageVector? = null,
    role: Role = Role.Button,
    danger: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    @Suppress("NAME_SHADOWING") val primary = primary && !danger
    val target = when {
        danger && state.pressed -> colors.danger.copy(alpha = 0.22f).compositeOver(colors.surfaceRaised)
        danger && state.hovered -> colors.danger.copy(alpha = 0.12f).compositeOver(colors.surfaceRaised)
        danger -> colors.surfaceRaised
        // the accent moves toward its text colour: darker on the dark themes, lighter on the light one
        primary && state.pressed -> lerp(colors.accent, colors.onAccent, 0.24f)
        primary && state.hovered -> lerp(colors.accent, colors.onAccent, 0.12f)
        primary -> colors.accent
        else -> stateBackground(state, rest = colors.surfaceRaised)
    }
    val background by animateInteractionColor(target)
    val content = when {
        danger -> colors.danger
        primary -> colors.onAccent
        else -> colors.text
    }
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = dimens.minTarget.coerceAtLeast(dimens.controlHeight))
            // disabled: the whole button at half strength, fill and border too
            .alpha(if (enabled) 1f else 0.5f)
            .pressScale(state.pressed)
            .focusRing(state, shape)
            .background(background, shape)
            .then(if (primary) Modifier else Modifier.border(1.dp, if (danger) colors.danger.copy(alpha = 0.6f) else colors.borderStrong, shape))
            .deskClickable(interaction, onClick, enabled = enabled, role = role)
            .padding(start = dimens.gapM, end = if (trailingIcon != null) dimens.gapS else dimens.gapM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(dimens.icon))
            Spacer(Modifier.width(dimens.gapS))
        }
        Text(text, style = DeskTheme.type.label, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailingIcon != null) {
            Spacer(Modifier.width(dimens.gapXs + 2.dp))
            Icon(trailingIcon, contentDescription = null, tint = if (primary || danger) content else colors.textSecondary, modifier = Modifier.size(dimens.icon))
        }
    }
}

/** Small uppercase-free section caption used in the sidebar and settings. */
@Composable
fun SectionCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = DeskTheme.type.labelSmall,
        color = DeskTheme.colors.textTertiary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
fun HorizontalRule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(DeskTheme.colors.border))
}

@Composable
fun VerticalRule(modifier: Modifier = Modifier) {
    Box(modifier.width(1.dp).background(DeskTheme.colors.border))
}
