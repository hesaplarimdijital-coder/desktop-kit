package desktopkit.shell

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
 * - opening ([detail] becomes non-null): the detail slides in a short way from the end edge while
 *   fading in, the list slides the other way and fades out (shared axis, emphasized curve);
 * - closing ([detail] becomes null): the same, reversed;
 * - Back gesture (Android 14 and later with `android:enableOnBackInvokedCallback="true"`): while the
 *   finger moves, the detail shrinks toward 90 %, rounds its corners and leans toward the edge the
 *   swipe came from; letting go closes it with [onBack], swiping back cancels and it springs back.
 *   On older Android versions the Back button or gesture simply closes it.
 *
 * On a phone the slides run on the gesture spring ([desktopkit.theme.MotionTier.Gesture]); from tablet
 * width up on the timed curves above; with reduced motion the detail simply appears. Works in every width class. The Back gesture is taken over while a detail is shown, also from a
 * [CompactTopBar] inside it (both normally call the same close function), unless [handleBack] is false.
 * [detailKey] tells details apart (the default compares the values); [animate] false switches
 * without motion, e.g. for screenshot tests.
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
    detailContent: @Composable (T) -> Unit,
) {
    val colors = DeskTheme.colors
    val tier = LocalMotionTier.current
    val scope = rememberCoroutineScope()
    val backProgress = remember { Animatable(0f) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    val latestOnBack by rememberUpdatedState(onBack)
    val behind = colors.pressed
    val page = colors.background

    AnimatedContent(
        targetState = detail,
        modifier = modifier
            .fillMaxSize()
            // a shade behind the shrinking detail, so its edges show during the Back gesture
            .drawBehind {
                val p = backProgress.value
                drawRect(if (p > 0f) lerp(page, behind, p) else page)
            },
        transitionSpec = { detailTransform(initialState, targetState, animate, tier) },
        contentKey = { it?.let(detailKey) },
        label = "detailTransition",
    ) { target ->
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
                scope.launch { backProgress.animateTo(0f, DeskMotion.spatial()) }
                throw e
            }
        }
    }
}

private fun <T> detailTransform(initial: T?, target: T?, animate: Boolean, tier: MotionTier): ContentTransform {
    if (!animate || tier == MotionTier.Reduced) return fadeIn(tween(0)) togetherWith fadeOut(tween(0))
    val opening = initial == null && target != null
    val closing = initial != null && target == null
    if (tier == MotionTier.Gesture && (opening || closing)) {
        // phone: the slide is the interruptible gesture spring, the fades stay short and timed
        val direction = if (opening) 1 else -1
        return (slideInHorizontally(DeskMotion.gesture()) { direction * it / 10 } +
            fadeIn(tween(DeskMotion.MEDIUM_MS, delayMillis = 50, easing = DeskMotion.StandardDecelerate)))
            .togetherWith(
                slideOutHorizontally(DeskMotion.gesture()) { -direction * it / 10 } +
                    fadeOut(tween(DeskMotion.SHORT_MS, easing = DeskMotion.StandardAccelerate)),
            )
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
