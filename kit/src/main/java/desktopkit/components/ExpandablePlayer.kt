package desktopkit.components

import android.graphics.Picture
import android.view.HapticFeedbackConstants
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
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
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
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
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import desktopkit.R
import desktopkit.shell.LocalShellOverlayHost
import desktopkit.shell.bottomInsetPadding
import desktopkit.theme.DeskGlass
import desktopkit.theme.DeskMotion
import desktopkit.theme.DeskTheme
import desktopkit.theme.GlassThickness
import desktopkit.theme.LocalMotionTier
import desktopkit.theme.MotionTier
import desktopkit.theme.glass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
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
 * to fade things along. Survives configuration changes. ([density] is kept for compatibility; the
 * fling rules now come from the drag itself, see [ExpandablePlayer].)
 */
@Stable
class ExpandablePlayerState(initialValue: PlayerValue, @Suppress("unused") density: Density) {
    internal val draggable = AnchoredDraggableState(initialValue)

    /** Distance from the window top to the mini player; the collapsed anchor. */
    internal var collapsedTop by mutableFloatStateOf(0f)
        private set

    /** The motion tier of the surrounding shell; set by [ExpandablePlayer], so [expand] jumps with reduced motion. */
    internal var tier: MotionTier = MotionTier.Gesture

    /** A tap's expand or collapse: the opening spring's damping, a little softer for the whole screen's way. */
    private fun settle(): FiniteAnimationSpec<Float> =
        DeskMotion.spatial(tier, spring(dampingRatio = 0.9f, stiffness = 500f, visibilityThreshold = DeskMotion.PIXEL), DeskMotion.PIXEL)

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

    /** Opens the full-screen player on a spring. */
    suspend fun expand() = draggable.animateTo(PlayerValue.Expanded, settle())

    /** Back to the mini player. */
    suspend fun collapse() = draggable.animateTo(PlayerValue.Collapsed, settle())

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
 * - One surface grows from the strip to the whole screen: its glass turns into the page as it
 *   rises, its corners round on the way and straighten at both ends, the scrim darkens with it and
 *   the bottom bar is pushed away under it. The [mini] controls fade out in the first quarter of
 *   the way, the [full] ones come in over the last part; a cover and a title marked with
 *   [playerSharedElement] in both fly from their place in the strip to their place in the full player
 *   and back instead of fading.
 * - The predictive Back gesture shrinks the full player with the finger and closes it on release;
 *   the shrink stays with the sheet until it is back on the strip.
 * - Swiping the mini player sideways skips: [onNext] (towards the start edge) or [onPrevious]; the
 *   strip follows the finger, ticks once past the threshold and springs back if let go before it.
 *
 * Without a surrounding shell only the mini player is shown.
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
    val tier = LocalMotionTier.current
    SideEffect { state.tier = tier }
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
            .graphicsLayer { alpha = if (state.progress > SHEET_OUT) 0f else 1f }
            .anchoredDraggable(
                state.draggable, Orientation.Vertical,
                enabled = host != null && state.collapsedTop > 0f,
                flingBehavior = playerFlingBehavior(state),
            )
            .semantics {
                customActions = listOf(CustomAccessibilityAction(expandLabel) { scope.launch { state.expand() }; true })
            }
            // the sheet opens from where the strip is drawn, also when a scroll has moved the bars
            // (the bars are left where they are: the expanding sheet covers them, and the strip is
            // back in the same place when it closes). Measured on every press, since a moved bar
            // moves the strip without laying it out again.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) measureAnchor()
                    }
                }
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = expandLabel) {
                scope.launch { state.expand() }
            },
    ) {
        MiniSwipeArea(onPrevious, onNext) { mini() }
    }

    if (host != null) {
        // gone as soon as the mini player is back: the spring's last fraction of a pixel took half a
        // second, while the sheet's background hid the bottom navigation bar under the mini player
        val open by remember(state) { derivedStateOf { state.progress > SHEET_OUT || state.targetValue == PlayerValue.Expanded } }
        if (open) {
            // the collapse runs in this scope: the Back handler's own one ends once the player is no longer expanded
            OverlayLayer(host) {
                PlayerSheet(state, miniHeight, mini, full, collapse = { scope.launch { state.collapse() } }, bottomInset = host.systemBottomPx)
            }
        }
    }
}

/** Below this progress the sheet is back at the mini player: the mini player shows and the sheet goes. */
private const val SHEET_OUT = 0.001f

/** The full-screen part, drawn in the shell's overlay from the mini player's place to the top. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerSheet(
    state: ExpandablePlayerState,
    miniHeight: Dp,
    mini: @Composable () -> Unit,
    full: @Composable () -> Unit,
    collapse: () -> Unit,
    bottomInset: () -> Int,
) {
    val colors = DeskTheme.colors
    val scope = rememberCoroutineScope()
    val backProgress = remember { Animatable(0f) }
    val flight = remember { PlayerFlight() }
    SideEffect { flight.progress = { state.progress } }
    val label = stringResource(R.string.dk_player)
    // drawn no more from the frame the mini player is back (it leaves the overlay a frame later)
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (state.progress > SHEET_OUT || state.targetValue == PlayerValue.Expanded) 1f else 0f }) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = state.progress }
                .background(colors.scrim),
        )
        Box(
            Modifier
                .fillMaxSize()
                // placed, not translated in a layer: the glass follows placement, a layer's translation it would not see
                .offset { IntOffset(0, state.sheetTop().roundToInt()) }
                .graphicsLayer {
                    val p = state.progress
                    // the Back gesture's shrink rides down with the sheet instead of opening back up first
                    val back = backProgress.value * p
                    val scale = 1f - 0.1f * back
                    scaleX = scale
                    scaleY = scale
                    translationY = 48.dp.toPx() * back
                    // rounded while it travels, square at both ends; rounded again under the Back finger
                    shape = RoundedCornerShape((TRAVEL_CORNER_DP * 4f * p * (1f - p) + 28f * back).dp)
                    clip = true
                }
                // the strip's glass, turning into the page as the sheet rises
                .glass(DeskGlass.chrome(colors, GlassThickness.Regular))
                .onGloballyPositioned { flight.sheet = it }
                .anchoredDraggable(state.draggable, Orientation.Vertical, flingBehavior = playerFlingBehavior(state))
                .semantics { paneTitle = label },
        ) {
            Box(Modifier.matchParentSize().graphicsLayer { alpha = state.progress }.background(colors.background))
            // the strip's controls, fading out over the first quarter of the way; gone (not touched, not read
            // out) once they are, back just before the way down needs them
            CompositionLocalProvider(LocalPlayerFlight provides flight, LocalPlayerSide provides PlayerSide.Mini) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(miniHeight)
                        .graphicsLayer { alpha = (1f - state.progress / MINI_FADE).coerceIn(0f, 1f) }
                        .placedWhile { state.progress < MINI_GONE },
                ) { mini() }
            }
            CompositionLocalProvider(LocalPlayerFlight provides flight, LocalPlayerSide provides PlayerSide.Full) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = ((state.progress - FULL_FROM) / (1f - FULL_FROM)).coerceIn(0f, 1f) }
                        // the player's background reaches behind the system navigation bar, its controls stay above it
                        .bottomInsetPadding(bottomInset),
                ) { full() }
            }
            // the shared elements on their way between the strip and the full player, above both
            Box(Modifier.matchParentSize().drawBehind { flight.draw(this, state.progress) })
        }
    }

    PredictiveBackHandler(enabled = state.currentValue == PlayerValue.Expanded) { events ->
        backProgress.snapTo(0f)
        try {
            events.collect { event -> backProgress.snapTo(event.progress) }
            collapse()
            // the shrink rides down with the sheet (the layer above scales it by the progress) and lets go once
            // the sheet has landed on the strip, or a finger has caught it on the way
            scope.launch {
                snapshotFlow { state.draggable.isAnimationRunning }.first { it }
                snapshotFlow { state.draggable.isAnimationRunning }.first { !it }
                if (state.progress <= SHEET_OUT) backProgress.snapTo(0f)
                else backProgress.animateTo(0f, DeskMotion.spatial(state.tier, tween(DeskMotion.MEDIUM_MS, easing = DeskMotion.Emphasized), 0.001f))
            }
        } catch (e: CancellationException) {
            scope.launch { backProgress.animateTo(0f, DeskMotion.spatial(state.tier, tween(DeskMotion.MEDIUM_MS, easing = DeskMotion.Emphasized), 0.001f)) }
            throw e
        }
    }
}

/** The corner radius of the sheet half way up, in dp (the corners are square at both ends). */
private const val TRAVEL_CORNER_DP = 16f

/** The part of the way over which the strip's controls fade out. */
private const val MINI_FADE = 0.25f

/** From here on the strip's controls are not placed: not drawn, touched or read out. */
private const val MINI_GONE = 0.3f

/** The full player's controls come in from here to the end of the way. */
private const val FULL_FROM = 0.6f

/** Lays the content out always, but places it only while [visible]: not placed, it is not drawn, hit or read out. */
private fun Modifier.placedWhile(visible: () -> Boolean): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) { if (visible()) placeable.place(0, 0) }
}

/** Which copy of the player's content a [playerSharedElement] is in. */
internal enum class PlayerSide { Mini, Full }

internal val LocalPlayerFlight = staticCompositionLocalOf<PlayerFlight?> { null }
internal val LocalPlayerSide = staticCompositionLocalOf { PlayerSide.Mini }

/**
 * Inside an [ExpandablePlayer], the element in [mini] and the element in [full] marked with the same
 * [key] (the cover, the title) are one: as the player opens the element flies from its place in the
 * strip to its place in the full player, scaling on the way, the strip's look giving way to the full
 * player's over the first part; on the way down it flies back. Put it first in the element's modifiers.
 * [shape] is the element's own rounded shape, kept in flight. Outside an [ExpandablePlayer]'s sheet
 * nothing flies, and with reduced motion the player jumps as a whole.
 */
@Composable
fun Modifier.playerSharedElement(key: Any, shape: CornerBasedShape? = null): Modifier {
    val flight = LocalPlayerFlight.current ?: return this
    val side = LocalPlayerSide.current
    val density = LocalDensity.current
    val entry = remember(flight, key) { flight.entry(key) }
    DisposableEffect(entry, side) { onDispose { entry.clear(side) } }
    return this
        .onGloballyPositioned { coordinates ->
            val radius = shape?.topStart?.toPx(coordinates.size.toSize(), density) ?: 0f
            entry.place(side, coordinates, radius)
        }
        .drawWithContent {
            // the element is recorded for the flight on every draw; in flight the flight draws it, else it draws itself
            val picture = entry.record(side, this)
            if (!flight.flying(entry)) drawIntoCanvas { it.nativeCanvas.drawPicture(picture) }
        }
}

/** The elements flying between the strip and the full player, and the sheet they fly in. */
internal class PlayerFlight {
    /** One shared element: its place and picture on both sides. */
    class Entry {
        var mini: LayoutCoordinates? = null
        var full: LayoutCoordinates? = null
        var miniRadius = 0f
        var fullRadius = 0f
        val miniPicture = Picture()
        val fullPicture = Picture()
        private var miniRecorded = false
        private var fullRecorded = false
        val path = Path()

        fun place(side: PlayerSide, coordinates: LayoutCoordinates, radius: Float) {
            if (side == PlayerSide.Mini) {
                mini = coordinates
                miniRadius = radius
            } else {
                full = coordinates
                fullRadius = radius
            }
        }

        /** Records the element's drawing (a picture plays back on any canvas, a software one too) and returns it. */
        fun record(side: PlayerSide, scope: ContentDrawScope): Picture {
            val picture = if (side == PlayerSide.Mini) miniPicture else fullPicture
            val canvas = Canvas(picture.beginRecording(scope.size.width.roundToInt().coerceAtLeast(1), scope.size.height.roundToInt().coerceAtLeast(1)))
            val own = scope.drawContext.canvas
            scope.drawContext.canvas = canvas
            try {
                scope.drawContent()
            } finally {
                scope.drawContext.canvas = own
                picture.endRecording()
            }
            if (side == PlayerSide.Mini) miniRecorded = true else fullRecorded = true
            return picture
        }

        fun clear(side: PlayerSide) {
            if (side == PlayerSide.Mini) {
                mini = null
                miniRecorded = false
            } else {
                full = null
                fullRecorded = false
            }
        }

        /** Both sides are placed and recorded. */
        val ready: Boolean get() = mini?.isAttached == true && full?.isAttached == true && miniRecorded && fullRecorded
    }

    val entries = LinkedHashMap<Any, Entry>()
    var sheet: LayoutCoordinates? = null
    var progress: () -> Float = { 0f }
    private val fade = Paint()

    fun entry(key: Any): Entry = entries.getOrPut(key) { Entry() }

    /** Whether the flight draws [entry] now (on the way, with both sides known). */
    fun flying(entry: Entry): Boolean {
        val p = progress()
        return p > 0f && p < 1f && sheet?.isAttached == true && entry.ready
    }

    /** Draws every ready element at [progress] of its way; the strip's picture fades away over the first [CROSS], the full player's is under it. */
    fun draw(scope: DrawScope, progress: Float) {
        if (progress <= 0f || progress >= 1f) return
        val sheet = sheet?.takeIf { it.isAttached } ?: return
        for (entry in entries.values) {
            if (!entry.ready) continue
            val from = sheet.localBoundingBoxOf(entry.mini!!, clipBounds = false)
            val to = sheet.localBoundingBoxOf(entry.full!!, clipBounds = false)
            if (from.isEmpty || to.isEmpty) continue
            val bounds = lerp(from, to, progress)
            val radius = lerp(entry.miniRadius, entry.fullRadius, progress)
            val cross = (progress / CROSS).coerceIn(0f, 1f)
            entry.path.reset()
            entry.path.addRoundRect(RoundRect(0f, 0f, bounds.width, bounds.height, CornerRadius(radius)))
            with(scope) {
                withTransform({
                    translate(bounds.left, bounds.top)
                    clipPath(entry.path)
                }) {
                    // the full player's picture lies under the strip's from the start, so a cover (the same
                    // picture twice) never thins out while the strip's fades away
                    drawPicture(entry.fullPicture, bounds.size, 1f)
                    if (cross < 1f) drawPicture(entry.miniPicture, bounds.size, 1f - cross)
                }
            }
        }
    }

    /** [picture], fitted into [size] from its recorded size (not stretched: a title keeps its letters' shape), at [alpha]. */
    private fun DrawScope.drawPicture(picture: Picture, size: Size, alpha: Float) {
        val width = picture.width.toFloat()
        val height = picture.height.toFloat()
        if (width <= 0f || height <= 0f) return
        val fit = minOf(size.width / width, size.height / height)
        withTransform({
            translate((size.width - width * fit) / 2f, (size.height - height * fit) / 2f)
            scale(fit, fit, Offset.Zero)
        }) {
            drawIntoCanvas { canvas ->
                if (alpha < 1f) {
                    fade.alpha = alpha
                    canvas.saveLayer(Rect(0f, 0f, width, height), fade)
                }
                canvas.nativeCanvas.drawPicture(picture)
                if (alpha < 1f) canvas.restore()
            }
        }
    }

    private companion object {
        /** The part of the way over which the strip's picture of an element fades away, leaving the full player's. */
        const val CROSS = 0.25f
    }
}

/** Letting go of the player: half way decides, a fling decides sooner; settles on the gesture spring (at once with reduced motion). */
@Composable
private fun playerFlingBehavior(state: ExpandablePlayerState) =
    AnchoredDraggableDefaults.flingBehavior(
        state.draggable,
        positionalThreshold = { distance -> distance * 0.5f },
        animationSpec = DeskMotion.spatial(LocalMotionTier.current, DeskMotion.gesture(DeskMotion.PIXEL), DeskMotion.PIXEL),
    )

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
                            scope.launch { offset.animateTo(0f, DeskMotion.spatial(tier, tween(DeskMotion.MEDIUM_MS), 0.5f)) }
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
    val spring = DeskMotion.spatial(tier, tween<Float>(DeskMotion.MEDIUM_MS, easing = DeskMotion.Emphasized), 0.5f)
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

private fun thresholdHaptic(): Int = HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
