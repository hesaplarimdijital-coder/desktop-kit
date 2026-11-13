package desktopkit.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import desktopkit.R
import desktopkit.shell.LocalShellContentPadding
import desktopkit.shell.LocalShellScreen
import desktopkit.theme.DeskGlass
import desktopkit.theme.DeskMotion
import desktopkit.theme.DeskTheme
import desktopkit.theme.GlassThickness
import desktopkit.theme.InputMode
import desktopkit.theme.LocalMotionTier
import desktopkit.theme.MotionTier
import desktopkit.theme.glass
import desktopkit.theme.rememberDeskHaptics

/**
 * Pull down to refresh (DeskMail's request 14): a finger pulling a list down past its top brings a
 * disc down from under the top bar, its arc filling as the pull goes on; let go once the arc is whole
 * (past [threshold], with a tick) and [onRefresh] runs, and the disc turns while [refreshing], then
 * goes back up. It also comes down by itself while [refreshing] (a sync the app started). Wrap the
 * list with it: a `LazyColumn`, a [DenseTable], a [DeskGrid].
 *
 * A mouse, a touchpad and the keyboard leave it alone ([enabled] is for touch by default): they have the
 * app's "Sync" button and its key (F5). Inside a [desktopkit.shell.DesktopShell] the disc stops under
 * the phone's glass top bar, where the list lies under it.
 */
@Composable
fun PullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = DeskTheme.inputMode == InputMode.Touch,
    threshold: Dp = 80.dp,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val haptics = rememberDeskHaptics()
    val latestRefresh by rememberUpdatedState(onRefresh)
    val latestRefreshing by rememberUpdatedState(refreshing)
    val pull = remember(density, threshold) {
        PullState(
            thresholdPx = with(density) { threshold.toPx() },
            restPx = with(density) { (threshold * REST).toPx() },
            refreshing = { latestRefreshing },
            onRefresh = { latestRefresh() },
            onArmed = { haptics.threshold() },
        )
    }
    pull.enabled = enabled
    // down by itself while refreshing, up once done
    LaunchedEffect(refreshing, pull) { pull.settle() }
    // under the phone's top bar where the screen lays its list out beneath it; the shell pads the others itself
    val screen = LocalShellScreen.current
    val shellPadding = LocalShellContentPadding.current
    val top: () -> Dp = { if (screen != null && screen.handlers > 0) shellPadding.calculateTopPadding() else 0.dp }
    Box(modifier.nestedScroll(pull)) {
        content()
        RefreshDisc(pull, refreshing, Modifier.align(Alignment.TopCenter), top)
    }
}

/** Where the disc rests while refreshing, as a share of the threshold. */
private const val REST = 0.75f

/** One turn of the disc while refreshing. */
private const val TURN_MS = 1000

/** How much of a pull past the top moves the disc: it resists. */
private const val RESISTANCE = 0.5f

@Stable
private class PullState(
    val thresholdPx: Float,
    val restPx: Float,
    val refreshing: () -> Boolean,
    val onRefresh: () -> Unit,
    val onArmed: () -> Unit,
) : NestedScrollConnection {
    var enabled = true

    /** How far the disc has come down, in pixels. */
    var distance by mutableFloatStateOf(0f)
        private set

    /** The pull as a share of the threshold, 0…1. */
    val progress: Float get() = (distance / thresholdPx).coerceIn(0f, 1f)

    fun pulledTo(value: Float) {
        val wasArmed = distance >= thresholdPx
        distance = value.coerceIn(0f, thresholdPx * 2f)
        if (!wasArmed && distance >= thresholdPx && !refreshing()) onArmed()
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // pushing back up takes the disc up before the list moves
        if (source != NestedScrollSource.UserInput || available.y >= 0f || distance <= 0f) return Offset.Zero
        val taken = maxOf(available.y, -distance / RESISTANCE)
        pulledTo(distance + taken * RESISTANCE)
        return Offset(0f, taken)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        // the list is at its top: what is left of the pull brings the disc down
        if (!enabled || source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
        pulledTo(distance + available.y * RESISTANCE)
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (distance <= 0f) return Velocity.Zero
        if (distance >= thresholdPx && !refreshing()) {
            onRefresh()
            // to the resting place at once: the app says it is refreshing on its next frame
            animateTo(restPx)
        }
        settle()
        // the fling of a pull is the pull's: the list does not fling on
        return available
    }

    /** To the resting place while refreshing, else back up. */
    suspend fun settle() = animateTo(if (refreshing()) restPx else 0f)

    private suspend fun animateTo(target: Float) {
        if (distance == target) return
        animate(distance, target, animationSpec = DeskMotion.page()) { value, _ -> distance = value }
    }
}

@Composable
private fun RefreshDisc(pull: PullState, refreshing: Boolean, modifier: Modifier, top: () -> Dp) {
    val colors = DeskTheme.colors
    val tier = LocalMotionTier.current
    val label = stringResource(R.string.dk_refreshing)
    // the pull itself is read while drawing: only its start and end recompose
    val pulled by remember(pull) { derivedStateOf { pull.distance > 0f } }
    if (!pulled && !refreshing) return
    // turns while refreshing and stops with it (with reduced motion it rests)
    val spin = remember { Animatable(0f) }
    LaunchedEffect(refreshing, tier) {
        if (!refreshing || tier == MotionTier.Reduced) return@LaunchedEffect
        while (true) {
            // an endless animation as far as tests go (InfiniteAnimationPolicy): they stop it here
            withInfiniteAnimationFrameNanos { }
            spin.snapTo(0f)
            spin.animateTo(360f, tween(TURN_MS, easing = LinearEasing))
        }
    }
    val accent = colors.accent
    val track = colors.border
    val disc = 40.dp
    Box(
        modifier
            .graphicsLayer {
                // out from under the top bar, its edge first
                translationY = top().toPx() + pull.distance - disc.toPx()
                alpha = if (refreshing) 1f else (pull.progress * 1.5f).coerceAtMost(1f)
            }
            .size(disc)
            .glass(DeskGlass.raised(colors, GlassThickness.Regular), CircleShape)
            .border(1.dp, colors.borderStrong, CircleShape)
            .then(
                if (!refreshing) Modifier else Modifier.semantics {
                    contentDescription = label
                    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val stroke = 2.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val progress = pull.progress
            // the arc fills while the finger pulls and turns once refreshing
            val sweep = if (refreshing) 270f else 300f * progress
            rotate(if (refreshing) spin.value else progress * 180f) {
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                drawArc(accent, -90f, sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
    }
}
