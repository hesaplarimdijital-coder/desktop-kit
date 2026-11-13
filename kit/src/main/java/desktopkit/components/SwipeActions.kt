package desktopkit.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import desktopkit.theme.DeskMotion
import desktopkit.theme.DeskTheme
import desktopkit.theme.LocalMotionTier
import desktopkit.theme.MotionTier
import desktopkit.theme.rememberDeskHaptics
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sign

/**
 * What a finger does by sliding a [DeskListRow] sideways ("Archive", "Snooze"): the row follows the
 * finger and uncovers the action's [icon] and [label]; past the threshold the action takes its colour
 * (red when [danger]) and the phone ticks, and letting go there runs [onSwipe]. An action that takes
 * the row out of the list ([removesRow]: archive, delete) leaves it out; another (mark as read) brings
 * it back. Offer the way back yourself: a toast with "Undo".
 *
 * A mouse and the keyboard do not swipe (the row's menu has the same actions); TalkBack offers each
 * action by its [label].
 */
@Immutable
class SwipeAction(
    val label: String,
    val icon: ImageVector,
    val onSwipe: () -> Unit,
    val danger: Boolean = false,
    val removesRow: Boolean = true,
)

/** The share of the row's width past which letting go runs the action. */
private const val THRESHOLD = 0.35f

/** A swipe this fast (dp/s) the way the row already went runs the action short of the threshold. */
private const val FLING_DP = 1000f

/** A row whose action should have taken it out comes back after this, if it is still there. */
private const val STILL_THERE_MS = 1000L

/**
 * The row [content] on top of the [start] and [end] actions it uncovers; [start] lies at the start edge
 * and is uncovered by sliding toward the end. Nothing moves unless [enabled].
 */
@Composable
internal fun SwipeBox(start: SwipeAction?, end: SwipeAction?, enabled: Boolean, content: @Composable () -> Unit) {
    val colors = DeskTheme.colors
    val tier = LocalMotionTier.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    val haptics = rememberDeskHaptics()
    // toward the end in the row's own direction, px
    val offset = remember { FingerOffset() }
    var width by remember { mutableIntStateOf(1) }
    var armed by remember { mutableStateOf(false) }
    val latestStart by rememberUpdatedState(start)
    val latestEnd by rememberUpdatedState(end)
    val side by remember { derivedStateOf { sign(offset.value).toInt() } }
    val page = colors.background
    val canSwipe = enabled && (start != null || end != null)
    Box(Modifier.fillMaxWidth().onSizeChanged { width = it.width.coerceAtLeast(1) }) {
        val shown = when (side) {
            1 -> start
            -1 -> end
            else -> null
        }
        if (shown != null) SwipeBackdrop(shown, atStart = side > 0, armed = armed, modifier = Modifier.matchParentSize())
        Box(
            Modifier
                .graphicsLayer { translationX = if (rtl) -offset.value else offset.value }
                // the row is see-through at rest: while it slides it hides the action under it
                .drawBehind { if (offset.value != 0f) drawRect(page) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = canSwipe,
                    state = rememberDraggableState { delta ->
                        val toward = if (rtl) -delta else delta
                        var next = offset.value + toward
                        if (latestStart == null) next = next.coerceAtMost(0f)
                        if (latestEnd == null) next = next.coerceAtLeast(0f)
                        next = next.coerceIn(-width.toFloat(), width.toFloat())
                        offset.moveTo(next)
                        val nowArmed = abs(next) >= width * THRESHOLD
                        if (nowArmed != armed) {
                            armed = nowArmed
                            if (nowArmed) haptics.threshold() else haptics.tick()
                        }
                    },
                    onDragStopped = { velocity ->
                        val toward = if (rtl) -velocity else velocity
                        val at = offset.value
                        val action = when {
                            at > 0f -> latestStart
                            at < 0f -> latestEnd
                            else -> null
                        }
                        val fling = with(density) { FLING_DP.dp.toPx() }
                        val flung = abs(toward) > fling && sign(toward) == sign(at) && abs(at) > width * THRESHOLD / 2
                        val spec: AnimationSpec<Float> = if (tier == MotionTier.Reduced) spring(stiffness = 10_000f) else DeskMotion.gesture()
                        if (action != null && (armed || flung)) {
                            if (!armed) haptics.threshold()
                            armed = false
                            // out the way it went, then the action
                            offset.settle(sign(at) * width, spec, toward)
                            action.onSwipe()
                            if (action.removesRow) delay(STILL_THERE_MS)
                            offset.settle(0f, spec)
                        } else {
                            armed = false
                            offset.settle(0f, spec, toward)
                        }
                    },
                ),
        ) { content() }
    }
}

/** The action a sliding row uncovers, at the edge it slides from. */
@Composable
private fun SwipeBackdrop(action: SwipeAction, atStart: Boolean, armed: Boolean, modifier: Modifier) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val tier = LocalMotionTier.current
    val fill by animateColorAsState(
        when {
            !armed -> colors.surfaceRaised
            action.danger -> colors.danger
            else -> colors.accent
        },
        DeskMotion.fade(tier, tween(DeskMotion.SHORT_MS)),
        label = "swipeFill",
    )
    val ink = if (armed) colors.onAccent else colors.textSecondary
    val grow by animateFloatAsState(if (armed) 1.2f else 1f, DeskMotion.fade(tier, DeskMotion.fastSpatial()), label = "swipeIcon")
    Row(
        modifier.background(fill).padding(horizontal = dimens.pagePadding),
        horizontalArrangement = if (atStart) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = @Composable {
            Icon(action.icon, contentDescription = null, tint = ink,
                modifier = Modifier.size(dimens.icon).graphicsLayer { scaleX = grow; scaleY = grow })
        }
        val label = @Composable {
            Text(action.label, style = DeskTheme.type.label, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (atStart) {
            icon()
            Spacer(Modifier.width(dimens.gapS))
            label()
        } else {
            label()
            Spacer(Modifier.width(dimens.gapS))
            icon()
        }
    }
}
