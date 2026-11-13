package desktopkit.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import desktopkit.components.TooltipArea
import desktopkit.components.TooltipPlacement
import desktopkit.components.animateInteractionColor
import desktopkit.components.collectState
import desktopkit.components.deskClickable
import desktopkit.components.focusRing
import desktopkit.components.pressScale
import desktopkit.theme.DeskMotion
import desktopkit.theme.DeskTheme
import desktopkit.theme.LocalMotionTier

/**
 * The screen's main action ("New message", "New playlist"), where each layout keeps it (DeskMail's
 * request 5):
 * - on a phone a floating button at the end, above the bottom bar and the player: it goes down with
 *   the bars when the list scrolls them away, shows its [label] while the list is at its top (only the
 *   [icon] once it is scrolled), and is gone while the on-screen keyboard is up. Lists laid out under
 *   the bars ([rememberShellContentPadding]) get room for it at their end;
 * - on the rail an accent button at the rail's top, named by a tooltip;
 * - in the sidebar an accent button with the label at the sidebar's top, the icon alone while the
 *   sidebar is collapsed.
 *
 * Call it in the screen. The last call of the screen shown wins; a screen being left gives way at once,
 * and leaving composition takes the button away. Outside a [DesktopShell] nothing shows.
 */
@Composable
fun ShellPrimaryAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    val host = LocalPrimaryActionHost.current ?: return
    val latestClick by rememberUpdatedState(onClick)
    val stableClick: () -> Unit = remember { { latestClick() } }
    val action = PrimaryAction(label, icon, stableClick)
    val leaving = LocalScreenLeaving.current
    val entry = remember(host) { PrimaryActionHost.Entry(action, leaving) }
    SideEffect {
        entry.value = action
        entry.leaving = leaving
    }
    DisposableEffect(host, entry) {
        host.entries.add(entry)
        onDispose { host.entries.remove(entry) }
    }
}

/** What a [ShellPrimaryAction] call asks for. */
@Immutable
internal data class PrimaryAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/** The [ShellPrimaryAction] calls in composition, oldest first; the newest of a screen not being left shows. */
@Stable
internal class PrimaryActionHost {
    class Entry(initial: PrimaryAction, leaving: () -> Boolean) {
        var value by mutableStateOf(initial)
        var leaving by mutableStateOf(leaving)
    }

    val entries = mutableStateListOf<Entry>()

    val current: PrimaryAction? get() = entries.lastOrNull { !it.leaving() }?.value
}

internal val LocalPrimaryActionHost = staticCompositionLocalOf<PrimaryActionHost?> { null }

/**
 * The accent button of a [PrimaryAction]: [height] tall, the label beside the icon while [extended],
 * the icon alone (square) otherwise; the label comes and goes sideways. [floating] lifts it with a shadow.
 */
@Composable
internal fun PrimaryActionButton(action: PrimaryAction, extended: Boolean, height: Dp, modifier: Modifier = Modifier, floating: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val tier = LocalMotionTier.current
    val shape = RoundedCornerShape(if (floating) dimens.radiusCard.coerceAtLeast(16.dp) else dimens.radiusControl)
    val fill by animateInteractionColor(
        when {
            state.pressed -> lerp(colors.accent, colors.onAccent, 0.24f)
            state.hovered -> lerp(colors.accent, colors.onAccent, 0.12f)
            else -> colors.accent
        },
    )
    val button = @Composable {
        Row(
            modifier
                .height(height)
                .widthIn(min = height)
                .pressScale(state.pressed)
                .then(if (floating) Modifier.shadow(8.dp, shape) else Modifier)
                .focusRing(state, shape)
                .background(fill, shape)
                .semantics { if (!extended) contentDescription = action.label }
                .deskClickable(interaction, action.onClick)
                .padding(horizontal = (height - dimens.icon) / 2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(action.icon, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(dimens.icon))
            AnimatedVisibility(
                visible = extended,
                enter = fadeIn(DeskMotion.fade(tier, tween(DeskMotion.MEDIUM_MS))) + expandHorizontally(DeskMotion.fade(tier, DeskMotion.fastSpatial())),
                exit = fadeOut(DeskMotion.fade(tier, tween(DeskMotion.SHORT_MS))) + shrinkHorizontally(DeskMotion.fade(tier, DeskMotion.fastSpatial())),
                label = "primaryActionLabel",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(dimens.gapS))
                    Text(action.label, style = DeskTheme.type.label, color = colors.onAccent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(dimens.gapXs))
                }
            }
        }
    }
    // named by a tooltip as well once the label is gone (mouse rest, keyboard focus); always the same tree, so the
    // label's way in and out plays
    TooltipArea(action.label, placement = TooltipPlacement.End) { button() }
}

/**
 * The phone's floating button: at the end, a margin above what [modifier] keeps it above, gone (scaled
 * down into its corner) while not [shown] or without an [action]; with the label while [extended]. Its
 * height with the margin goes to [metrics] (lists end above it, toasts sit over it). An expanding player
 * covers it.
 */
@Composable
internal fun PhoneActionButton(
    action: PrimaryAction?,
    shown: Boolean,
    extended: Boolean,
    metrics: ShellChromeMetrics,
    expandProgress: () -> Float,
    modifier: Modifier,
) {
    val dimens = DeskTheme.dimens
    val tier = LocalMotionTier.current
    // the last action stays on the button while it goes
    val last = remember { LastAction() }
    if (action != null) last.value = action
    val visible = action != null && shown
    // its room, also when it comes back before its way out has ended (the size does not change then, so no new
    // measurement says it)
    SideEffect { metrics.actionPx = if (visible) last.height else 0 }
    val corner = TransformOrigin(1f, 1f)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.graphicsLayer { alpha = 1f - expandProgress().coerceIn(0f, 1f) },
        enter = fadeIn(DeskMotion.fade(tier, tween(DeskMotion.MEDIUM_MS))) + scaleIn(DeskMotion.fade(tier, DeskMotion.open()), initialScale = 0.6f, transformOrigin = corner),
        exit = fadeOut(DeskMotion.fade(tier, tween(DeskMotion.SHORT_MS))) + scaleOut(DeskMotion.fade(tier, DeskMotion.fastSpatial()), targetScale = 0.6f, transformOrigin = corner),
        label = "phoneAction",
    ) {
        val current = last.value ?: return@AnimatedVisibility
        Box(
            Modifier
                .onSizeChanged {
                    last.height = it.height
                    if (visible) metrics.actionPx = it.height
                }
                .padding(end = dimens.gapL, bottom = dimens.gapL),
        ) {
            PrimaryActionButton(current, extended, height = 56.dp, floating = true)
        }
    }
}

/** The action the phone's button last showed and its height with the margin; nothing redraws from them. */
private class LastAction {
    var value: PrimaryAction? = null
    var height = 0
}
