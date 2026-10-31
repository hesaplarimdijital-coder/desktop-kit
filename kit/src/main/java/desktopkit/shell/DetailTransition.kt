package desktopkit.shell

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A list screen and the detail screen opened from it, with Android's own screen transition between
 * them and the predictive Back gesture:
 *
 * - opening ([detail] becomes non-null): on a phone the detail comes in from the end edge over the
 *   list, which sinks to 95 % and fades under it (depth, on the [desktopkit.theme.DeskMotion.open]
 *   spring); with a [detailSharedCard] the card carries the way and the detail only fades in after
 *   it. From tablet width up the 0.2.0 shared axis: the detail slides in a short way from the end
 *   edge while fading in, the list slides the other way and fades out (emphasized curve);
 * - closing ([detail] becomes null): the same, reversed;
 * - Back gesture (Android 14 and later with `android:enableOnBackInvokedCallback="true"`): while the
 *   finger moves, the detail shrinks toward 90 %, rounds its corners and leans toward the edge the
 *   swipe came from; letting go closes it with [onBack], swiping back cancels and it springs back.
 *   On older Android versions the Back button or gesture simply closes it.
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
    val backProgress = remember { Animatable(0f) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    val latestOnBack by rememberUpdatedState(onBack)
    val cards = remember { DetailSharedState() }
    val behind = colors.pressed
    val page = colors.background

    val container = modifier
        .fillMaxSize()
        // a shade behind the shrinking detail, so its edges show during the Back gesture
        .drawBehind {
            val p = backProgress.value
            drawRect(if (p > 0f) lerp(page, behind, p) else page)
        }
    val content: @Composable (SharedTransitionScope?) -> Unit = { shared ->
        AnimatedContent(
            targetState = detail,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                // a card registered by the list carries the opening and, remembered here, the closing too
                if (initialState == null && targetState != null) cards.openedWithCard = cards.registered > 0
                detailTransform(initialState, targetState, animate, tier, card = cards.openedWithCard)
            },
            contentKey = { it?.let(detailKey) },
            label = "detailTransition",
        ) { target ->
            // one object per side: a new one on every recomposition would recompose everything under it
            val side = if (target == null) DetailSide.List else DetailSide.Detail
            val scopes = remember(shared, this, animate, tier, side) {
                if (shared != null && animate && tier != MotionTier.Reduced) DetailSharedScopes(shared, this, tier, side, cards) else null
            }
            CompositionLocalProvider(LocalDetailSharedScopes provides scopes) {
                if (target == null) {
                    // opaque, so the shade left behind a closed detail never shows through the list
                    Box(Modifier.fillMaxSize().background(page)) { list() }
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val p = backProgress.value
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
                    ) { detailContent(target) }
                }
            }
        }
    }
    if (sharedElements) SharedTransitionLayout(container) { content(this) } else Box(container) { content(null) }

    // a newly opened detail always starts at full size
    LaunchedEffect(detail?.let(detailKey)) {
        if (detail != null && !backProgress.isRunning) backProgress.snapTo(0f)
    }

    // registered after the detail's own handlers (key changes with the detail), so the gesture comes here first
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
                // back to full size on the gesture spring: no bounce, so the scale never crosses 1
                scope.launch { backProgress.animateTo(0f, DeskMotion.spatial(tier, tween(DeskMotion.MEDIUM_MS, easing = DeskMotion.Emphasized), 0.001f)) }
                throw e
            }
        }
    }
}

private fun <T> detailTransform(initial: T?, target: T?, animate: Boolean, tier: MotionTier, card: Boolean): ContentTransform {
    if (!animate || tier == MotionTier.Reduced) return fadeIn(tween(0)) togetherWith fadeOut(tween(0))
    val opening = initial == null && target != null
    val closing = initial != null && target == null
    if (tier == MotionTier.Gesture && (opening || closing)) {
        // phone: depth. The detail comes in 12 % from the end edge on the opening spring and fades in
        // at once (no dead frames after the tap); the list sinks to 95 % and fades under it. With a
        // card the card carries the way and the detail only fades in after it. Back the same way
        // round, the list rising under the leaving detail.
        val slide = DeskMotion.open(IntOffset.VisibilityThreshold)
        val fadeIn = fadeIn(tween(DeskMotion.MEDIUM_MS, easing = DeskMotion.StandardDecelerate))
        val fadeOut = fadeOut(tween(DeskMotion.SHORT_MS + 20, easing = DeskMotion.StandardAccelerate))
        return if (opening) {
            val enter = if (card) fadeIn(tween(DeskMotion.MEDIUM_MS + 20, delayMillis = 60, easing = DeskMotion.StandardDecelerate))
            else slideInHorizontally(slide) { it * 12 / 100 } + fadeIn
            val exit = scaleOut(tween(DeskMotion.LONG_MS, easing = DeskMotion.Emphasized), targetScale = 0.95f) +
                fadeOut(tween(DeskMotion.SHORT_MS + 60, easing = DeskMotion.StandardAccelerate))
            enter togetherWith exit
        } else {
            val enter = scaleIn(tween(DeskMotion.LONG_MS, easing = DeskMotion.Emphasized), initialScale = 0.95f) + fadeIn
            val exit = if (card) fadeOut else slideOutHorizontally(slide) { it * 12 / 100 } + fadeOut
            (enter togetherWith exit).apply { targetContentZIndex = -1f }
        }
    }
    return when {
        opening ->
            (slideInHorizontally(tween(DeskMotion.LONG_MS + 50, easing = DeskMotion.EmphasizedDecelerate)) { it / 10 } +
                fadeIn(tween(DeskMotion.MEDIUM_MS, delayMillis = 50, easing = DeskMotion.StandardDecelerate)))
                .togetherWith(
                    slideOutHorizontally(tween(DeskMotion.LONG_MS, easing = DeskMotion.Emphasized)) { -it / 10 } +
                        fadeOut(tween(DeskMotion.SHORT_MS, easing = DeskMotion.StandardAccelerate)),
                )
        closing ->
            (slideInHorizontally(tween(DeskMotion.LONG_MS, easing = DeskMotion.EmphasizedDecelerate)) { -it / 10 } +
                fadeIn(tween(DeskMotion.MEDIUM_MS, delayMillis = 50, easing = DeskMotion.StandardDecelerate)))
                .togetherWith(
                    slideOutHorizontally(tween(DeskMotion.LONG_MS - 50, easing = DeskMotion.EmphasizedAccelerate)) { it / 10 } +
                        fadeOut(tween(DeskMotion.SHORT_MS + 50, easing = DeskMotion.StandardAccelerate)),
                )
        else -> fadeIn(tween(DeskMotion.MEDIUM_MS)) togetherWith fadeOut(tween(DeskMotion.SHORT_MS))
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
 * straightening from [shape] and its surface turning into the page, while the header's content
 * fades in over the card's; on the way back the header shrinks into the card's place. A
 * [detailSharedElement] cover and a [detailSharedBounds] title inside both still fly on their own,
 * above the card. Put it first in the modifiers of both. The card needs no background of its own
 * (it gets the surface colour while it flies), the header draws the page colour while it flies.
 * From tablet width up, outside a [DetailTransition] or with reduced motion nothing changes.
 */
@Composable
fun Modifier.detailSharedCard(key: Any, shape: CornerBasedShape = RoundedCornerShape(DeskTheme.dimens.radiusCard)): Modifier {
    val scopes = LocalDetailSharedScopes.current ?: return this
    if (scopes.tier != MotionTier.Gesture) return this
    val colors = DeskTheme.colors
    val list = scopes.side == DetailSide.List
    if (list) {
        DisposableEffect(scopes.cards) {
            scopes.cards.registered++
            onDispose { scopes.cards.registered-- }
        }
    }
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
    return with(scopes.transition) {
        this@detailSharedCard
            .sharedBounds(
                sharedContentState = rememberSharedContentState(key),
                animatedVisibilityScope = scopes.visibility,
                enter = fadeIn(tween(DeskMotion.MEDIUM_MS + 10, delayMillis = DeskMotion.SHORT_MS - 10, easing = DeskMotion.StandardDecelerate)),
                exit = fadeOut(tween(DeskMotion.SHORT_MS - 10, easing = DeskMotion.StandardAccelerate)),
                boundsTransform = boundsTransform(scopes.tier),
                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopStart),
                clipInOverlayDuringTransition = clip,
            )
            // a surface only while it flies: the card is as bare as before at rest
            .drawBehind {
                if (isTransitionActive) drawRoundRect(surface, cornerRadius = CornerRadius(cornerPx()))
            }
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
