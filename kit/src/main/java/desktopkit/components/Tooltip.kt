package desktopkit.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import kotlinx.coroutines.delay
import desktopkit.theme.DeskMotion
import desktopkit.theme.DeskTheme
import desktopkit.theme.LocalMotionTier
import desktopkit.theme.MotionTier

/** Where a tooltip appears: under its anchor (buttons, toolbars) or beside it (a collapsed sidebar's entries). */
enum class TooltipPlacement { Below, End }

/**
 * Desktop tooltip: appears after the mouse rests on [content] for [delayMillis] (500 ms), or as soon
 * as the keyboard focus reaches it ([showOnFocus]), and shows the label and, when there is one, the
 * keyboard shortcut (so menus, buttons and the shortcut list agree). Moving on to a neighbour while
 * one is showing, or within a second of it closing, shows the next one at once (100 ms), as on a
 * desktop. Touch input never opens it; long press is the context menu. A press, or focus that came
 * with a pointer's press, closes it.
 */
@Composable
fun TooltipArea(
    text: String,
    modifier: Modifier = Modifier,
    shortcut: String? = null,
    delayMillis: Long = TooltipTiming.DELAY_MS,
    placement: TooltipPlacement = TooltipPlacement.Below,
    showOnFocus: Boolean = true,
    content: @Composable () -> Unit,
) {
    val tooltip = rememberTooltipState(delayMillis, showOnFocus)
    Box(modifier = modifier.tooltipTrigger(tooltip)) {
        content()
        TooltipPopup(tooltip, text, shortcut, placement)
    }
}

/** Whether a tooltip is up, and what brings it: the mouse resting on its element, the keyboard's focus in it. */
@Stable
internal class TooltipState {
    var hovering by mutableStateOf(false)
    var focused by mutableStateOf(false)
    var visible by mutableStateOf(false)
    var showOnFocus = true

    /** A focus that comes with a pointer's press (a click that also focuses) is not a keyboard's. Long ago at first. */
    var pressedAt = Long.MIN_VALUE / 2
}

/** A [TooltipState] that opens after [delayMillis] of rest (less while a neighbour's is up; see [TooltipTiming]). */
@Composable
internal fun rememberTooltipState(delayMillis: Long = TooltipTiming.DELAY_MS, showOnFocus: Boolean = true): TooltipState {
    val state = remember { TooltipState() }
    state.showOnFocus = showOnFocus
    LaunchedEffect(state.hovering, state.focused) {
        if (state.hovering || state.focused) {
            delay(TooltipTiming.delayFor(delayMillis))
            if (!state.visible) TooltipTiming.opened()
            state.visible = true
        } else {
            if (state.visible) TooltipTiming.closed()
            state.visible = false
        }
    }
    DisposableEffect(state) { onDispose { if (state.visible) TooltipTiming.closed() } }
    return state
}

/**
 * The element the tooltip names: the mouse resting on it and the keyboard's focus in it open the tooltip, a press
 * closes it. On the element itself, so its own modifiers (a tag, an alignment, a focus requester) stay its own.
 */
internal fun Modifier.tooltipTrigger(state: TooltipState): Modifier = this
    .onFocusChanged { focus ->
        val now = android.os.SystemClock.uptimeMillis()
        state.focused = state.showOnFocus && focus.hasFocus && now - state.pressedAt > TooltipTiming.PRESS_FOCUS_MS
    }
    .pointerInput(state) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val mouse = event.changes.any { it.type == PointerType.Mouse }
                when (event.type) {
                    PointerEventType.Enter -> if (mouse) state.hovering = true
                    PointerEventType.Exit -> state.hovering = false
                    PointerEventType.Press -> {
                        state.pressedAt = android.os.SystemClock.uptimeMillis()
                        state.hovering = false
                        state.focused = false
                        if (state.visible) TooltipTiming.closed()
                        state.visible = false
                    }
                    else -> Unit
                }
            }
        }
    }

/** The tooltip of [state], placed by its element: compose it inside the element (a pop-up takes no room). */
@Composable
internal fun TooltipPopup(state: TooltipState, text: String, shortcut: String?, placement: TooltipPlacement = TooltipPlacement.Below) {
    if (!state.visible) return
    val position = remember(placement) { if (placement == TooltipPlacement.End) EndOfAnchorPositionProvider() else BelowAnchorPositionProvider() }
    Popup(popupPositionProvider = position) {
        val appear = remember { MutableTransitionState(false).apply { targetState = true } }
        AnimatedVisibility(
            visibleState = appear,
            enter = if (LocalMotionTier.current == MotionTier.Reduced) EnterTransition.None else
                fadeIn(tween(DeskMotion.SHORT_MS, easing = DeskMotion.StandardDecelerate)) +
                    scaleIn(tween(DeskMotion.SHORT_MS + 50, easing = DeskMotion.EmphasizedDecelerate), initialScale = 0.94f),
            label = "tooltip",
        ) {
            TooltipBubble(text, shortcut)
        }
    }
}

/** When tooltips appear: the first after a rest, the next ones at once while the user moves along a toolbar. */
internal object TooltipTiming {
    /** The rest before a tooltip appears. */
    const val DELAY_MS = 500L

    /** The short wait for a neighbour's tooltip, while one is up or [HANDOFF_WINDOW_MS] after it closed. */
    const val HANDOFF_MS = 100L
    const val HANDOFF_WINDOW_MS = 1000L

    /** A focus within this long after a press came with the press. */
    const val PRESS_FOCUS_MS = 300L

    private var lastClosed = Long.MIN_VALUE / 2
    private var open = 0

    fun delayFor(rest: Long): Long {
        val now = android.os.SystemClock.uptimeMillis()
        return if (open > 0 || now - lastClosed < HANDOFF_WINDOW_MS) minOf(rest, HANDOFF_MS) else rest
    }

    fun opened() {
        open++
    }

    fun closed() {
        lastClosed = android.os.SystemClock.uptimeMillis()
        if (open > 0) open--
    }

    /** Forgets the last tooltip (tests run one after another in one process). */
    fun reset() {
        lastClosed = Long.MIN_VALUE / 2
        open = 0
    }
}

@Composable
internal fun TooltipBubble(text: String, shortcut: String?) {
    val colors = DeskTheme.colors
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier
            .background(colors.surfaceRaised, shape)
            .border(1.dp, colors.borderStrong, shape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = DeskTheme.type.labelSmall, color = colors.text, maxLines = 2)
        if (shortcut != null) {
            Spacer(Modifier.width(8.dp))
            Text(shortcut, style = DeskTheme.type.mono, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Beside the anchor at its end, vertically centred; on the other side when there is no room. */
internal class EndOfAnchorPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val gap = 8
        val ltr = layoutDirection == LayoutDirection.Ltr
        var x = if (ltr) anchorBounds.right + gap else anchorBounds.left - gap - popupContentSize.width
        if (x < 0 || x + popupContentSize.width > windowSize.width) {
            x = if (ltr) anchorBounds.left - gap - popupContentSize.width else anchorBounds.right + gap
        }
        val y = anchorBounds.top + (anchorBounds.height - popupContentSize.height) / 2
        return IntOffset(
            x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}

/** Centred under the anchor with a small gap; above it when there is no room below. */
internal class BelowAnchorPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val gap = 6
        var x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
        var y = anchorBounds.bottom + gap
        if (y + popupContentSize.height > windowSize.height) y = anchorBounds.top - gap - popupContentSize.height
        x = x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        return IntOffset(x, y.coerceAtLeast(0))
    }
}
