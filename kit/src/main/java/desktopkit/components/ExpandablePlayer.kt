package desktopkit.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import desktopkit.R
import desktopkit.shell.LocalShellOverlayHost
import desktopkit.theme.DeskGlass
import desktopkit.theme.DeskMotion
import desktopkit.theme.DeskTheme
import desktopkit.theme.GlassThickness
import desktopkit.theme.LocalMotionTier
import desktopkit.theme.MotionTier
import desktopkit.theme.glass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/** Where an [ExpandablePlayer] rests. */
enum class PlayerValue { Collapsed, Expanded }

/**
 * State of an [ExpandablePlayer]: the full-screen player's top edge, dragged between the mini
 * player's place ([PlayerValue.Collapsed]) and the top of the window ([PlayerValue.Expanded]).
 * [progress] runs from 0 to 1 with it; read it while drawing (`graphicsLayer { alpha = progress }`)
 * to fade things along. Survives configuration changes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Stable
class ExpandablePlayerState(initialValue: PlayerValue, density: Density) {
    internal val draggable = AnchoredDraggableState(
        initialValue = initialValue,
        positionalThreshold = { distance -> distance * 0.5f },
        velocityThreshold = { with(density) { 400.dp.toPx() } },
        snapAnimationSpec = DeskMotion.gesture(),
        decayAnimationSpec = exponentialDecay(),
    )

    /** Distance from the window top to the mini player; the collapsed anchor. */
    internal var collapsedTop by mutableFloatStateOf(0f)
        private set

    /** The settled value, or the one being animated to. */
    val currentValue: PlayerValue get() = draggable.currentValue

    /** The value the player is moving towards (while dragged: the one it would settle at). */
    val targetValue: PlayerValue get() = draggable.targetValue

    val isExpanded: Boolean get() = draggable.currentValue == PlayerValue.Expanded

    /** 0 collapsed … 1 expanded, following the finger. */
    val progress: Float
        get() {
            val top = sheetTop()
            return if (collapsedTop <= 0f) (if (draggable.currentValue == PlayerValue.Expanded) 1f else 0f)
            else (1f - top / collapsedTop).coerceIn(0f, 1f)
        }

    /** Opens the full-screen player on a spring (instantly with reduced motion). */
    suspend fun expand() = draggable.animateTo(PlayerValue.Expanded)

    /** Back to the mini player. */
    suspend fun collapse() = draggable.animateTo(PlayerValue.Collapsed)

    /** Jumps to [value] without animation. */
    suspend fun snapTo(value: PlayerValue) = draggable.snapTo(value)

    internal fun sheetTop(): Float {
        val offset = draggable.offset
        if (!offset.isNaN()) return offset
        return if (draggable.currentValue == PlayerValue.Expanded) 0f else collapsedTop
    }

    internal fun updateCollapsedTop(top: Float) {
        if (top <= 0f || top == collapsedTop) return
        collapsedTop = top
        draggable.updateAnchors(
            DraggableAnchors {
                PlayerValue.Collapsed at top
                PlayerValue.Expanded at 0f
            },
            draggable.targetValue,
        )
    }

    companion object {
        fun saver(density: Density): Saver<ExpandablePlayerState, PlayerValue> =
            Saver(save = { it.currentValue }, restore = { ExpandablePlayerState(it, density) })
    }
}

@Composable
fun rememberExpandablePlayerState(initialValue: PlayerValue = PlayerValue.Collapsed): ExpandablePlayerState {
    val density = LocalDensity.current
    return rememberSaveable(density, saver = ExpandablePlayerState.saver(density)) { ExpandablePlayerState(initialValue, density) }
}

/**
 * The phone's player: a mini player strip that grows into a full-screen player. Put it in
 * [desktopkit.shell.DesktopShell]'s `bottomBar` on compact windows, where it sits on glass above
 * the bottom bar and moves with it:
 *
 * ```
 * DesktopShell(…, bottomBar = { kind ->
 *     if (kind == NavKind.BottomBar) ExpandablePlayer(state, mini = { MiniPlayer() }, full = { FullPlayer() },
 *         onPrevious = player::previous, onNext = player::next)
 *     else TransportBar()
 * })
 * ```
 *
 * - Drag the mini player up (or tap it) to open the full player; drag the full player down to
 *   close it. Letting go settles on a spring that takes over the fling's speed; half way decides.
 * - The scrim behind it and the cross-fade from [mini] to [full] follow the drag; the bottom bar
 *   slides away as it opens.
 * - The predictive Back gesture shrinks the full player with the finger and closes it on release.
 * - Swiping the mini player sideways skips: [onNext] (towards the start edge) or [onPrevious]; the
 *   strip follows the finger, ticks once past the threshold and springs back if let go before it.
 *
 * Scrollable content inside [full] scrolls first; once it is at its top, a downward drag moves the
 * player. Without a surrounding shell only the mini player is shown.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpandablePlayer(
    state: ExpandablePlayerState,
    mini: @Composable () -> Unit,
    full: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    miniHeight: Dp = DeskTheme.dimens.miniPlayerHeight,
) {
    val host = LocalShellOverlayHost.current
    val scope = rememberCoroutineScope()
    val colors = DeskTheme.colors
    val expandLabel = stringResource(R.string.dk_player_expand)
    var miniCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    fun measureAnchor() {
        val coordinates = miniCoordinates ?: return
        val overlay = host?.coordinates ?: return
        if (!coordinates.isAttached || !overlay.isAttached) return
        state.updateCollapsedTop(overlay.localPositionOf(coordinates, Offset.Zero).y)
    }
    if (host != null) {
        SideEffect {
            host.expandProgress = { state.progress }
        }
        // the overlay and the strip are positioned in the same pass, in no fixed order
        LaunchedEffect(host.coordinates, miniCoordinates) { measureAnchor() }
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(miniHeight)
            .onGloballyPositioned {
                miniCoordinates = it
                measureAnchor()
            }
            .glass(DeskGlass.chrome(colors, GlassThickness.Regular))
            // hidden while the sheet (which starts with the same strip) is out
            .graphicsLayer { alpha = if (state.progress > 0.001f) 0f else 1f }
            .anchoredDraggable(state.draggable, Orientation.Vertical, enabled = host != null && state.collapsedTop > 0f)
            .semantics {
                customActions = listOf(CustomAccessibilityAction(expandLabel) { scope.launch { state.expand() }; true })
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = expandLabel) {
                host?.onExpandStart?.invoke()
                measureAnchor()
                scope.launch { state.expand() }
            },
    ) {
        MiniSwipeArea(onPrevious, onNext) { mini() }
    }

    if (host != null) {
        val open by remember(state) { derivedStateOf { state.progress > 0f || state.targetValue == PlayerValue.Expanded } }
        if (open) {
            OverlayLayer(host) { PlayerSheet(state, miniHeight, mini, full) }
        }
    }
}

/** The full-screen part, drawn in the shell's overlay from the mini player's place to the top. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerSheet(
    state: ExpandablePlayerState,
    miniHeight: Dp,
    mini: @Composable () -> Unit,
    full: @Composable () -> Unit,
) {
    val colors = DeskTheme.colors
    val scope = rememberCoroutineScope()
    val backProgress = remember { Animatable(0f) }
    val label = stringResource(R.string.dk_player)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = state.progress }
                .background(colors.scrim),
        )
        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(0, state.sheetTop().roundToInt()) }
                .graphicsLayer {
                    val p = state.progress
                    val back = backProgress.value
                    val scale = 1f - 0.1f * back
                    scaleX = scale
                    scaleY = scale
                    translationY = 48.dp.toPx() * back
                    // rounded while it travels, square once it covers the window
                    shape = RoundedCornerShape((16f * (1f - p) + 28f * back).dp)
                    clip = true
                }
                .background(colors.background)
                .anchoredDraggable(state.draggable, Orientation.Vertical)
                .semantics { paneTitle = label },
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(miniHeight)
                    .graphicsLayer { alpha = (1f - state.progress / 0.25f).coerceIn(0f, 1f) },
            ) { mini() }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = ((state.progress - 0.15f) / 0.85f).coerceIn(0f, 1f) },
            ) { full() }
        }
    }

    PredictiveBackHandler(enabled = state.currentValue == PlayerValue.Expanded) { events ->
        backProgress.snapTo(0f)
        try {
            events.collect { event -> backProgress.snapTo(event.progress) }
            scope.launch { backProgress.animateTo(0f, tween(DeskMotion.LONG_MS, easing = DeskMotion.Emphasized)) }
            state.collapse()
        } catch (e: CancellationException) {
            scope.launch { backProgress.animateTo(0f, DeskMotion.gesture()) }
            throw e
        }
    }
}

/** What a sideways swipe on the mini player does. */
enum class MiniSwipeAction { None, Previous, Next }

/** The mini player's swipe rules, free of Compose so they can be tested on their own. */
object MiniSwipe {
    /**
     * The action for a swipe released at [offset] px (negative: towards the left) moving at
     * [velocity] px/s. It commits past [threshold], or on a fling faster than [flingVelocity] in
     * the direction of the offset. Towards the start edge (left, or right in [rtl]) is "next".
     * A direction without a handler never commits.
     */
    fun decide(
        offset: Float,
        velocity: Float,
        threshold: Float,
        flingVelocity: Float,
        canPrevious: Boolean,
        canNext: Boolean,
        rtl: Boolean = false,
    ): MiniSwipeAction {
        if (offset == 0f) return MiniSwipeAction.None
        val past = abs(offset) >= threshold
        val flung = abs(velocity) >= flingVelocity && sign(velocity) == sign(offset) && abs(offset) >= threshold / 4f
        if (!past && !flung) return MiniSwipeAction.None
        val towardsStart = if (rtl) offset > 0f else offset < 0f
        return when {
            towardsStart && canNext -> MiniSwipeAction.Next
            !towardsStart && canPrevious -> MiniSwipeAction.Previous
            else -> MiniSwipeAction.None
        }
    }

    /** How far a drag of [raw] px moves the strip: freely towards a direction with a handler, a third as far otherwise. */
    fun resist(raw: Float, canPrevious: Boolean, canNext: Boolean, rtl: Boolean = false): Float {
        val towardsStart = if (rtl) raw > 0f else raw < 0f
        val allowed = if (towardsStart) canNext else canPrevious
        return if (allowed) raw else raw / 3f
    }
}

/**
 * The mini player's sideways swipe: the content follows the finger, a haptic tick marks the
 * threshold, a committed swipe slides the strip out and the next one in, anything else springs back.
 */
@Composable
private fun MiniSwipeArea(onPrevious: (() -> Unit)?, onNext: (() -> Unit)?, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val tier = LocalMotionTier.current
    val offset = remember { Animatable(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    val latestPrevious by rememberUpdatedState(onPrevious)
    val latestNext by rememberUpdatedState(onNext)
    val enabled = onPrevious != null || onNext != null
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .then(
                if (!enabled) Modifier else Modifier.pointerInput(Unit) {
                    val tracker = VelocityTracker()
                    var raw = 0f
                    var armed = false
                    val threshold = { minOf(with(density) { 96.dp.toPx() }, width * 0.3f) }
                    detectHorizontalDragGestures(
                        onDragStart = {
                            tracker.resetTracking()
                            raw = offset.value
                            armed = false
                        },
                        onDragEnd = {
                            val velocity = tracker.calculateVelocity().x
                            val action = MiniSwipe.decide(offset.value, velocity, threshold(), with(density) { 700.dp.toPx() },
                                latestPrevious != null, latestNext != null, rtl)
                            scope.launch { finishSwipe(offset, action, velocity, width, tier, rtl, latestPrevious, latestNext) }
                        },
                        onDragCancel = {
                            scope.launch { offset.animateTo(0f, DeskMotion.spatial(tier, tween(DeskMotion.MEDIUM_MS))) }
                        },
                    ) { change, amount ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        raw += amount
                        val moved = MiniSwipe.resist(raw, latestPrevious != null, latestNext != null, rtl)
                        scope.launch { offset.snapTo(moved) }
                        val past = abs(moved) >= threshold()
                        if (past != armed) {
                            armed = past
                            if (past) view.performHapticFeedback(thresholdHaptic())
                        }
                    }
                },
            )
            .graphicsLayer {
                translationX = offset.value
                alpha = 1f - (abs(offset.value) / width * 0.7f).coerceIn(0f, 0.7f)
            },
        contentAlignment = Alignment.CenterStart,
    ) { content() }
}

private suspend fun finishSwipe(
    offset: Animatable<Float, AnimationVector1D>,
    action: MiniSwipeAction,
    velocity: Float,
    width: Float,
    tier: MotionTier,
    rtl: Boolean,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
) {
    val spring = DeskMotion.spatial(tier, tween<Float>(DeskMotion.MEDIUM_MS, easing = DeskMotion.Emphasized))
    if (action == MiniSwipeAction.None) {
        offset.animateTo(0f, spring, initialVelocity = velocity)
        return
    }
    // out on the side it was swiped to, the new track in from the other side
    val towardsStart = action == MiniSwipeAction.Next
    val out = if (towardsStart != rtl) -width else width
    offset.animateTo(out, spring, initialVelocity = velocity)
    if (action == MiniSwipeAction.Next) onNext?.invoke() else onPrevious?.invoke()
    offset.snapTo(-out * 0.4f)
    offset.animateTo(0f, spring)
}

private fun thresholdHaptic(): Int =
    if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else HapticFeedbackConstants.CLOCK_TICK
