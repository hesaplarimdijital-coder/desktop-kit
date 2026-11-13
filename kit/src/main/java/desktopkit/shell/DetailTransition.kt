package desktopkit.shell

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import desktopkit.theme.DeskMotion
import desktopkit.theme.DeskTheme
import desktopkit.theme.LocalMotionTier
import desktopkit.theme.MotionTier
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * A list screen and the detail screen opened from it, with Android's own screen transition between
 * them and the predictive Back gesture:
 *
 * - opening ([detail] becomes non-null): on a phone as Android's own list → detail: the detail,
 *   opaque from its first frame, slides in from the end edge over the list on the
 *   [desktopkit.theme.DeskMotion.open] spring; the list behind it is not faded, it sinks to 95 %
 *   and darkens under a scrim, so its text never shows through the detail. With a
 *   [detailSharedCard] the card carries the way and the rest fades through: the list is gone
 *   before the detail starts to appear. From tablet width up the 0.2.0 shared axis: the detail
 *   slides in a short way from the end edge, the list slides the other way; the list fades out
 *   in the first third, only then the detail fades in (emphasized curve);
 * - closing ([detail] becomes null): the same, reversed;
 * - Back gesture (Android 14 and later with `android:enableOnBackInvokedCallback="true"`): while the
 *   finger moves, the detail shrinks toward 90 %, rounds its corners and leans away from the edge
 *   the swipe came from, with the finger. On a phone the list is really there behind it, dimmed,
 *   rising with the finger; letting go closes it with [onBack]: the detail slides out the way it
 *   was leaning from wherever the finger left it and the list's scrim lightens; swiping back
 *   cancels and everything springs back. The detail stays opaque all the way. With a three-button Back only
 *   the way out plays. From tablet width up the 2.2.0 gesture stays: the detail shrinks over a
 *   shade and, let go of, closes with the shared axis.
 *
 * No frame has both screens part transparent: the text of one is never drawn over the other's.
 *
 * With reduced motion the detail simply appears. Works in every width class. The Back gesture is taken over while a detail is shown, also from a
 * [CompactTopBar] inside it (both normally call the same close function), unless [handleBack] is false.
 * [detailKey] tells details apart (the default compares the values); [animate] false switches
 * without motion, e.g. for screenshot tests.
 *
 * Shared elements: mark the same thing in the list and in the detail with [detailSharedElement]
 * (a cover: it flies from the row to its place in the detail and back), [detailSharedBounds]
 * (a title: it moves and scales instead of reflowing) or [detailSharedCard] (a whole card that
 * grows into the detail's header), with the same key on both sides. They need a lookahead layout
 * pass over the list and the detail; [sharedElements] false leaves it out for screens that mark
 * nothing.
 */
@Composable
fun <T : Any> DetailTransition(
    detail: T?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    handleBack: Boolean = true,
    animate: Boolean = true,
    detailKey: (T) -> Any = { it },
    list: @Composable () -> Unit,
    // after list, so that 1.0's positional calls still compile
    sharedElements: Boolean = true,
    detailContent: @Composable (T) -> Unit,
) {
    val colors = DeskTheme.colors
    val tier = LocalMotionTier.current
    val scope = rememberCoroutineScope()
    val latestOnBack by rememberUpdatedState(onBack)
    val latestDetail by rememberUpdatedState(detail)
    val cards = remember { DetailSharedState() }
    // the list leaves composition once a detail covers it; its remembered state (scroll position, open
    // sections, a typed filter) waits here for its return, as Navigation keeps a screen's
    val saveable = rememberSaveableStateHolder()
    val page = colors.background
    val behind = colors.pressed
    val scrim = colors.scrim
    val animated = animate && tier != MotionTier.Reduced
    // the phone's transition is seekable, so the Back gesture can play the closing along with the finger; wider
    // up the 2.2.0 transition stays as it was, the gesture shrinking the detail over a shade
    val phone = tier == MotionTier.Gesture
    val seekable = remember { SeekableTransitionState<T?>(detail) }
    val transition = if (phone) rememberTransition(seekable, label = "detailTransition") else updateTransition(detail, label = "detailTransition")
    // a closing driven by the Back gesture: the detail shrinks with the fraction, the list rises behind it (phone)
    var gestureClose by remember { mutableStateOf(false) }
    // the Back gesture's progress, shrinking the detail over the shade (wider up)
    val backProgress = remember { Animatable(0f) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    // the run back after a cancelled gesture; a change of the detail takes over from it
    var undoing by remember { mutableStateOf<Job?>(null) }
    // after a run back the content that was on its way lets its shared elements go before the transition stands
    // still: in between, its partners would wait for an overlay that no longer draws them and vanish for a frame
    var dropped by remember { mutableStateOf(false) }

    val container = modifier
        .fillMaxSize()
        // wider up, a shade behind the shrinking detail, so its edges show during the Back gesture
        .drawBehind {
            val p = if (phone) 0f else backProgress.value
            drawRect(if (p > 0f) lerp(page, behind, p) else page)
        }
    val content: @Composable (SharedTransitionScope?) -> Unit = { shared ->
        transition.AnimatedContent(
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                // a card registered by the list carries the opening and, remembered here, the closing too
                if (initialState == null && targetState != null) cards.openedWithCard = cards.registered > 0
                detailTransform(initialState, targetState, animated, tier, card = cards.openedWithCard, gesture = phone && gestureClose, swipeEdge = swipeEdge)
            },
            contentKey = { it?.let(detailKey) },
        ) { target ->
            // one object per side: a new one on every recomposition would recompose everything under it
            val side = if (target == null) DetailSide.List else DetailSide.Detail
            val scopes = remember(shared, this, animated, side) {
                if (shared != null && animated) DetailSharedScopes(shared, this, tier, side, cards) else null
            }
            val held = phone && dropped && target?.let(detailKey) != seekable.currentState?.let(detailKey)
            CompositionLocalProvider(
                LocalDetailSharedScopes provides (if (held) null else scopes),
                // the list's cards register at rest too: the opening decides on its first frame whether a card carries it
                LocalDetailCards provides (if (target == null && animated && tier == MotionTier.Gesture) cards else null),
            ) {
                if (target == null) {
                    // on the phone the list behind a detail is not faded: it sinks a little and darkens under a scrim
                    // (each 0 at rest, 1 all the way behind the detail), on the detail's own spring so the scrim
                    // follows its edge. During the Back gesture the list rises with the finger and stays dimmed, so
                    // the shrunk detail stands out against it; the scrim lightens on the way out, once let go. The
                    // scrim is drawn over the whole window, the margins left by the sinking list included.
                    val behindDetail: (EnterExitState) -> Float = { state -> if (state == EnterExitState.Visible || !(phone && animated)) 0f else 1f }
                    val depth = this.transition.animateFloat(
                        transitionSpec = { if (gestureClose) tween(GESTURE_MS, easing = DeskMotion.Standard) else DeskMotion.open(FRACTION) },
                        label = "listDepth",
                    ) { behindDetail(it) }
                    val dim = this.transition.animateFloat(
                        transitionSpec = { if (gestureClose) tween(GESTURE_MS, easing = AfterTheFinger) else DeskMotion.open(FRACTION) },
                        label = "listScrim",
                    ) { behindDetail(it) }
                    // opaque, so the shade left behind a closed detail never shows through the list
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(page)
                            .drawWithContent {
                                drawContent()
                                val d = dim.value
                                if (d > 0f) drawRect(scrim, alpha = SCRIM * d)
                            },
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val d = depth.value
                                    if (d > 0f) {
                                        val scale = 1f - (if (gestureClose) 0.06f else 0.05f) * d
                                        scaleX = scale
                                        scaleY = scale
                                    }
                                },
                        ) { saveable.SaveableStateProvider(LIST_STATE_KEY) { list() } }
                    }
                } else {
                    // the detail as the app has it now: the transition holds the object it was opened with, and an
                    // app that re-emits its detail under the same key (a count, a progress) expects the new one shown
                    val shown = latestDetail.takeIf { it != null && detailKey(it) == detailKey(target) } ?: target
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // the Back gesture: toward 90 %, rounded, leaning away from the edge the swipe came
                                // from, with the finger (and, on the phone, on to the end once the finger lets go)
                                val p = if (phone) (if (gestureClose) (seekable.fraction / GESTURE_END).coerceIn(0f, 1f) else 0f) else backProgress.value
                                if (p > 0f) {
                                    val scale = 1f - 0.1f * p
                                    scaleX = scale
                                    scaleY = scale
                                    val lean = 24.dp.toPx() * p
                                    translationX = if (swipeEdge == BackEventCompat.EDGE_RIGHT) -lean else lean
                                    shape = RoundedCornerShape((28 * p).dp)
                                    clip = true
                                }
                            }
                            .background(page),
                    ) { detailContent(shown) }
                }
            }
        }
    }
    // on the phone the lookahead pass of shared elements is paid only while something moves: a list scrolled at rest inside a
    // SharedTransitionLayout was measured twice on every frame. At rest the content sits in a plain Box and moves
    // into the layout (state and all) when a transition or a Back gesture starts, and back out once it is over.
    val latestContent by rememberUpdatedState(content)
    val body = remember { movableContentOf { shared: SharedTransitionScope? -> latestContent(shared) } }
    val moving by remember(transition) {
        derivedStateOf {
            latestDetail?.let(detailKey) != transition.currentState?.let(detailKey) ||
                transition.targetState?.let(detailKey) != transition.currentState?.let(detailKey) ||
                transition.isRunning || gestureClose || dropped || undoing?.isActive == true
        }
    }
    // (on the phone: its transition changes target one frame after the detail does, which gives the list's elements
    // the frame they need inside the layout; wider up the target changes in the same composition, and the layout
    // stays there all the time, as before)
    val sharedLayout = sharedElements && (if (phone) animated && moving else true)
    if (sharedLayout) SharedTransitionLayout(container) { body(this) } else Box(container) { body(null) }

    if (phone) {
        /**
         * The transition turned around: run back to where it came from, the content that was on its way
         * lets its shared elements go (a composition), then the transition stands still at its current state.
         */
        suspend fun undo(spec: FiniteAnimationSpec<Float>) {
            try {
                seekable.runBack(spec)
                dropped = true
                withFrameNanos { }
                withFrameNanos { }
                gestureClose = false
                seekable.snapTo(seekable.currentState)
                // the content that was on its way leaves with this composition; only then may the next transition
                // register shared elements again
                withFrameNanos { }
            } finally {
                dropped = false
                gestureClose = false
            }
        }

        // the app's detail drives the transition; a Back gesture that was let go finishes from where the finger left it
        LaunchedEffect(detail?.let(detailKey)) {
            undoing?.cancel()
            undoing = null
            val target = detail
            when {
                !animated -> seekable.snapTo(target)
                gestureClose && target == null -> {
                    // let go early, the finger's part of the way is finished first, quickly, then the way out
                    seekable.seekOn(GESTURE_END, (COMMIT_MS * (GESTURE_END - seekable.fraction) / GESTURE_END).toInt())
                    seekable.animateTo(null, tween(COMMIT_MS, easing = DeskMotion.Standard))
                    gestureClose = false
                }
                // a change of mind on the way (Back right after the tap, the tap again right after Back): the way is
                // run back from where it is
                seekable.targetState != seekable.currentState && seekable.currentState?.let(detailKey) == target?.let(detailKey) ->
                    undo(DeskMotion.spatial(tier, tween(COMMIT_MS, easing = DeskMotion.Standard), FRACTION))
                else -> {
                    gestureClose = false
                    seekable.animateTo(target)
                }
            }
        }

        // registered after the detail's own handlers (key changes with the detail), so the gesture comes here first
        key(detail?.let(detailKey)) {
            PredictiveBackHandler(enabled = handleBack && detail != null) { events ->
                try {
                    events.collectLatest { event ->
                        swipeEdge = event.swipeEdge
                        // (a gesture while the opening is still on its way leaves it be: letting go runs the opening back)
                        if (animated && seekable.currentState != null) {
                            gestureClose = true
                            // the finger seeks the closing as far as GESTURE_END; the rest is the way out
                            seekable.seekTo(event.progress * GESTURE_END, null)
                        }
                    }
                    latestOnBack()
                    // an app that keeps its detail: the gesture is undone
                    if (animated) {
                        undoing = scope.launch {
                            withFrameNanos { }
                            if (latestDetail != null && gestureClose) undo(DeskMotion.gesture(FRACTION))
                        }
                    }
                } catch (e: CancellationException) {
                    if (animated) undoing = scope.launch { undo(DeskMotion.gesture(FRACTION)) }
                    throw e
                }
            }
        }
    } else {
        // a newly opened detail always starts at full size
        LaunchedEffect(detail?.let(detailKey)) {
            if (detail != null && !backProgress.isRunning) backProgress.snapTo(0f)
        }

        key(detail?.let(detailKey)) {
            PredictiveBackHandler(enabled = handleBack && detail != null) { events ->
                backProgress.snapTo(0f)
                try {
                    events.collect { event ->
                        swipeEdge = event.swipeEdge
                        backProgress.snapTo(event.progress)
                    }
                    latestOnBack()
                    // the detail leaves from where the finger let it go; the shade is cleared once it has gone
                    scope.launch {
                        delay(DeskMotion.LONG_MS + 100L)
                        backProgress.snapTo(0f)
                    }
                } catch (e: CancellationException) {
                    // back to full size as in 2.2.0 (a jump with reduced motion)
                    scope.launch { backProgress.animateTo(0f, DeskMotion.fade(tier, DeskMotion.spatial())) }
                    throw e
                }
            }
        }
    }
}

/** The key the list's saved state is kept under while a detail covers it. */
private const val LIST_STATE_KEY = "desktopkit.DetailTransition.list"

/** How far the finger seeks the closing; the rest ([1 - GESTURE_END]) is the detail's way out once let go. */
private const val GESTURE_END = 0.85f

/** Over the Back gesture's closing: nothing while the finger seeks it, then the way out, evenly. */
private val AfterTheFinger = Easing { fraction -> ((fraction - GESTURE_END) / (1f - GESTURE_END)).coerceIn(0f, 1f) }

/**
 * A transition turned around on its way: its fraction runs back to 0 on [spec], the target held.
 * Retargeted at its own current state instead, a seekable transition drops the shared elements'
 * match for a frame: the detail's header jumped to its full size and left from there. Run back,
 * the shared elements fly back along their path and the content's entering is undone. The caller
 * then stands the transition at its current state (see `undo` in [DetailTransition]).
 */
private suspend fun <S> SeekableTransitionState<S>.runBack(spec: FiniteAnimationSpec<Float>) {
    val held = targetState
    if (held == currentState || fraction <= 0f) return
    val way = TargetBasedAnimation(spec, Float.VectorConverter, fraction, 0f)
    val start = withFrameNanos { it }
    while (true) {
        val played = withFrameNanos { it } - start
        seekTo(way.getValueFromNanos(played).coerceIn(0f, 1f), held)
        if (way.isFinishedFromNanos(played)) break
    }
}

/**
 * The fraction run on to [to] over [millis] (frame by frame, the target held), for the finger's part of
 * the way a Back gesture let go of early: the way out then starts where the finger would have taken it.
 */
private suspend fun <S> SeekableTransitionState<S>.seekOn(to: Float, millis: Int) {
    val held = targetState
    if (fraction >= to || millis <= 0) return
    val way = TargetBasedAnimation(tween<Float>(millis, easing = DeskMotion.StandardAccelerate), Float.VectorConverter, fraction, to)
    val start = withFrameNanos { it }
    while (true) {
        val played = withFrameNanos { it } - start
        seekTo(way.getValueFromNanos(played).coerceIn(0f, 1f), held)
        if (way.isFinishedFromNanos(played)) break
    }
}

/** The way out after the finger lets go, from wherever it was. */
private const val COMMIT_MS = 220

/** The visibility threshold of a spring over a transition's fraction. */
private const val FRACTION = 0.001f

/** The length the Back gesture's closing transition is seeked over (the finger's part and the way out). */
private const val GESTURE_MS = 300

/** How much of the theme's scrim darkens the list all the way behind a detail on a phone. */
private const val SCRIM = 0.7f

/**
 * Fade-through timing on a phone (with a [detailSharedCard]) and between two details: the screen going
 * away is gone after [FADE_OUT_MS], only then the one coming in starts to appear, over [FADE_IN_MS].
 * The card's own content (see [detailSharedCard]) fades on the same timing.
 */
private const val FADE_OUT_MS = DeskMotion.SHORT_MS - 10
private const val FADE_IN_MS = DeskMotion.MEDIUM_MS + 10

private fun <T> AnimatedContentTransitionScope<T?>.detailTransform(
    initial: T?,
    target: T?,
    animate: Boolean,
    tier: MotionTier,
    card: Boolean,
    gesture: Boolean,
    swipeEdge: Int,
): ContentTransform {
    if (!animate) return fadeIn(tween(0)) togetherWith fadeOut(tween(0))
    val opening = initial == null && target != null
    val closing = initial != null && target == null
    // (on the phone the list never fades on the way to or from a plain detail: its depth, a slight
    // sinking and a scrim, is its own animation, see DetailTransition; it is held until the detail
    // has arrived or gone)
    if (closing && gesture) {
        // the Back gesture seeks this: the list rises behind the shrinking detail from the first
        // frame, dimmed; the detail stays whole and opaque until GESTURE_END and only then (once the
        // finger has let go) slides out away from the edge the swipe came from, with the finger, all
        // the way out of the window, while the list's scrim lightens (the way out's curve is the
        // commit's, see DetailTransition: a slide this long on an accelerating curve jumped at its end)
        val direction = if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1 else 1
        val exit = slideOutHorizontally(tween(GESTURE_MS, easing = AfterTheFinger)) { direction * it }
        return (EnterTransition.None togetherWith exit).apply { targetContentZIndex = -1f }
    }
    if (tier == MotionTier.Gesture && (opening || closing)) {
        // phone: as Android's own list -> detail. The detail, opaque from its first frame, slides in
        // from the end edge over the list on the opening spring; the list is not faded, it sinks and
        // darkens under it. Back the same way round. With a card the card carries the way and the rest
        // fades through: the side going away is gone before the other starts to appear.
        val slide = DeskMotion.open(IntOffset.VisibilityThreshold)
        val fadeIn = fadeIn(tween(FADE_IN_MS, delayMillis = FADE_OUT_MS, easing = DeskMotion.StandardDecelerate))
        val fadeOut = fadeOut(tween(FADE_OUT_MS, easing = DeskMotion.StandardAccelerate))
        return if (opening) {
            if (card) fadeIn togetherWith (fadeOut + ExitTransition.KeepUntilTransitionsFinished)
            else slideInHorizontally(slide) { it } togetherWith ExitTransition.KeepUntilTransitionsFinished
        } else {
            val transform = if (card) fadeIn togetherWith fadeOut else EnterTransition.None togetherWith slideOutHorizontally(slide) { it }
            transform.apply { targetContentZIndex = -1f }
        }
    }
    // tablet and DeX: the shared axis, its fades in turn (Material's: the screen going away is gone
    // after the first third, only then the other fades in); the slides and the lengths as in 0.2.0
    return when {
        opening ->
            (slideInHorizontally(tween(DeskMotion.LONG_MS + 50, easing = DeskMotion.EmphasizedDecelerate)) { it / 10 } +
                fadeIn(tween(DeskMotion.MEDIUM_MS, delayMillis = DeskMotion.SHORT_MS, easing = DeskMotion.StandardDecelerate)))
                .togetherWith(
                    slideOutHorizontally(tween(DeskMotion.LONG_MS, easing = DeskMotion.Emphasized)) { -it / 10 } +
                        fadeOut(tween(DeskMotion.SHORT_MS, easing = DeskMotion.StandardAccelerate)),
                )
        closing ->
            (slideInHorizontally(tween(DeskMotion.LONG_MS, easing = DeskMotion.EmphasizedDecelerate)) { -it / 10 } +
                fadeIn(tween(DeskMotion.MEDIUM_MS, delayMillis = DeskMotion.SHORT_MS, easing = DeskMotion.StandardDecelerate)))
                .togetherWith(
                    slideOutHorizontally(tween(DeskMotion.LONG_MS - 50, easing = DeskMotion.EmphasizedAccelerate)) { it / 10 } +
                        fadeOut(tween(DeskMotion.SHORT_MS, easing = DeskMotion.StandardAccelerate)),
                )
        // one detail for another: fade-through
        else -> fadeIn(tween(DeskMotion.MEDIUM_MS, delayMillis = DeskMotion.SHORT_MS)) togetherWith fadeOut(tween(DeskMotion.SHORT_MS))
    }
}

/** The scopes a shared element needs: the [DetailTransition]'s layout and the side (list or detail) it is on. */
internal class DetailSharedScopes(
    val transition: SharedTransitionScope,
    val visibility: AnimatedVisibilityScope,
    val tier: MotionTier,
    val side: DetailSide,
    val cards: DetailSharedState,
)

internal enum class DetailSide { List, Detail }

/** What the two sides of a [DetailTransition] tell each other: whether the list has a [detailSharedCard]. */
internal class DetailSharedState {
    /** Cards composed in the list right now. */
    var registered = 0

    /** Whether the open detail was opened with a card: its closing is the card's way back. */
    var openedWithCard = false
}

internal val LocalDetailSharedScopes = staticCompositionLocalOf<DetailSharedScopes?> { null }

/** The list side's card count of a phone's [DetailTransition], also while no transition runs. */
internal val LocalDetailCards = staticCompositionLocalOf<DetailSharedState?> { null }

/** Bounds of a shared element move on the opening spring on a phone, on the emphasized curve wider up. */
private fun boundsTransform(tier: MotionTier) = BoundsTransform { _, _ ->
    if (tier == MotionTier.Gesture) DeskMotion.open(Rect.VisibilityThreshold)
    else tween(DeskMotion.LONG_MS + 50, easing = DeskMotion.Emphasized)
}

/**
 * The shape a [desktopkit.components.CoverCell] gives its cover. A [detailSharedElement] inside the
 * cover takes it, so the cover keeps its rounded corners while it flies (in flight the element is
 * drawn above everything, outside the cell that rounds it in place).
 */
internal val LocalSharedElementShape = staticCompositionLocalOf<Shape?> { null }

/**
 * Inside a [DetailTransition], this element and the element with the same [key] on the other side
 * (the list's row and the detail) are one: opening the detail moves it from the row to its place in
 * the detail, closing moves it back. For things that look the same at both sizes: a cover, an
 * icon. Put it first in the element's modifiers, before its size, so all of it flies. [shape]
 * rounds the element, in place and in flight; inside a [desktopkit.components.CoverCell] it is the
 * cell's own shape. Outside a [DetailTransition], or with reduced motion, nothing flies.
 */
@Composable
fun Modifier.detailSharedElement(key: Any, shape: Shape? = LocalSharedElementShape.current): Modifier {
    val clip = if (shape == null) Modifier else Modifier.clip(shape)
    val scopes = LocalDetailSharedScopes.current ?: return this.then(clip)
    return with(scopes.transition) {
        this@detailSharedElement.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = scopes.visibility,
            boundsTransform = boundsTransform(scopes.tier),
            // above a card flying with it
            zIndexInOverlay = 1f,
        )
    }.then(clip)
}

/**
 * Like [detailSharedElement] for things that differ in size between the two sides, such as a title
 * set larger in the detail: the bounds move and the content scales with them instead of reflowing,
 * cross-fading from one side's look to the other's.
 */
@Composable
fun Modifier.detailSharedBounds(key: Any): Modifier {
    val scopes = LocalDetailSharedScopes.current ?: return this
    return with(scopes.transition) {
        this@detailSharedBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = scopes.visibility,
            boundsTransform = boundsTransform(scopes.tier),
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.Fit, Alignment.CenterStart),
            zIndexInOverlay = 1f,
        )
    }
}

/**
 * Inside a [DetailTransition] on a phone, the card of a list item (a [desktopkit.components.CoverCell]'s
 * `modifier`) and the header of its detail (the block holding the big cover and the title) marked
 * with the same [key] open into each other: the card grows into the header, its corners
 * straightening from [shape] and its surface turning into the page; the card's content fades out
 * before the header's fades in, and the card stays opaque all the way (the header is drawn over the
 * card's surface); on the way back the header shrinks into the card's place. A
 * [detailSharedElement] cover and a [detailSharedBounds] title inside both still fly on their own,
 * above the card. Put it first in the modifiers of both. The card needs no background of its own
 * (it gets the surface colour while it flies), the header draws the page colour while it flies.
 * From tablet width up, outside a [DetailTransition] or with reduced motion nothing changes.
 */
@Composable
fun Modifier.detailSharedCard(key: Any, shape: CornerBasedShape = RoundedCornerShape(DeskTheme.dimens.radiusCard)): Modifier {
    val cards = LocalDetailCards.current
    if (cards != null) {
        DisposableEffect(cards) {
            cards.registered++
            onDispose { cards.registered-- }
        }
    }
    val scopes = LocalDetailSharedScopes.current ?: return this
    if (scopes.tier != MotionTier.Gesture) return this
    val colors = DeskTheme.colors
    val list = scopes.side == DetailSide.List
    val density = LocalDensity.current
    // the corners: the card's at rest in the list, none at rest in the detail, moving with the transition
    val rested = with(density) { shape.topStart.toPx(Size(100f, 100f), this) }
    val corner by scopes.visibility.transition.animateDp(
        transitionSpec = { tween(DeskMotion.LONG_MS, easing = DeskMotion.Emphasized) },
        label = "cardCorners",
    ) { state ->
        val atRest = state == EnterExitState.Visible
        with(density) { if (atRest == list) rested.toDp() else 0.dp }
    }
    val cornerPx: () -> Float = { with(density) { corner.toPx() } }
    val surface = if (list) colors.surface else colors.background
    val clip = remember(scopes) { CornerClip(cornerPx) }
    // the content fades through: the side going away is gone within FADE_OUT_MS, only then the other appears
    val shown = scopes.visibility.transition.animateFloat(
        transitionSpec = {
            if (targetState == EnterExitState.Visible) tween(FADE_IN_MS, delayMillis = FADE_OUT_MS, easing = DeskMotion.StandardDecelerate)
            else tween(FADE_OUT_MS, easing = DeskMotion.StandardAccelerate)
        },
        label = "cardContent",
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
    return with(scopes.transition) {
        val state = rememberSharedContentState(key)
        val flying = { isTransitionActive && state.isMatchFound }
        // a surface only while this card flies (the others in the list stay as bare as before)
        val surfaceWhileFlying = Modifier.drawBehind {
            if (flying()) drawRoundRect(surface, cornerRadius = CornerRadius(cornerPx()))
        }
        val fading = Modifier.graphicsLayer { alpha = if (flying()) shown.value else 1f }
        this@detailSharedCard
            .sharedBounds(
                sharedContentState = state,
                animatedVisibilityScope = scopes.visibility,
                // both sides are drawn in full while they fly, the header above the card: the card's surface stays
                // opaque under everything, only the contents fade (below)
                enter = EnterTransition.None,
                exit = ExitTransition.None,
                boundsTransform = boundsTransform(scopes.tier),
                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopStart),
                zIndexInOverlay = if (list) 0f else 0.5f,
                clipInOverlayDuringTransition = clip,
            )
            // the card: an opaque surface under its fading content; the header: its page-coloured surface fading
            // in and out with its content, over the card
            .then(if (list) surfaceWhileFlying.then(fading) else fading.then(surfaceWhileFlying))
    }
}

/** The overlay clip of a flying card: a rounded rectangle whose radius [cornerPx] gives per draw. */
private class CornerClip(private val cornerPx: () -> Float) : SharedTransitionScope.OverlayClip {
    private val path = Path()

    override fun getClipPath(sharedContentState: SharedTransitionScope.SharedContentState, bounds: Rect, layoutDirection: LayoutDirection, density: Density): Path? {
        path.reset()
        path.addRoundRect(RoundRect(bounds, CornerRadius(cornerPx())))
        return path
    }
}
