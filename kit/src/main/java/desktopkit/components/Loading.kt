package desktopkit.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import desktopkit.R
import desktopkit.theme.DeskMotion
import desktopkit.theme.DeskTheme
import desktopkit.theme.InputMode
import desktopkit.theme.LocalMotionTier
import desktopkit.theme.MotionTier

/**
 * The slow breathing shared by the placeholders of one list: from full to 55 % opacity and back
 * every 1.6 s; still with reduced motion. Read it while drawing.
 */
@Composable
fun rememberSkeletonPulse(): State<Float> {
    if (LocalMotionTier.current == MotionTier.Reduced) return remember { mutableFloatStateOf(1f) }
    val transition = rememberInfiniteTransition(label = "skeleton")
    return transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(800, easing = DeskMotion.Standard), RepeatMode.Reverse),
        label = "skeletonPulse",
    )
}

/** A placeholder block in the border colour, breathing with [pulse]. */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    pulse: State<Float> = rememberSkeletonPulse(),
    shape: Shape = RoundedCornerShape(4.dp),
) {
    val color = DeskTheme.colors.border
    Box(modifier.graphicsLayer { alpha = pulse.value }.background(color, shape))
}

/** Lengths of the placeholder lines, so a column of them does not look ruled. */
private val lineWidths = floatArrayOf(0.62f, 0.48f, 0.74f, 0.55f, 0.68f, 0.43f, 0.58f, 0.7f)

/**
 * A list row while its data loads, the size of a [DeskListRow]: an artwork square ([leading]) and
 * a title line with a shorter line under it ([lines] = 2).
 */
@Composable
fun SkeletonListRow(
    modifier: Modifier = Modifier,
    index: Int = 0,
    leading: Boolean = true,
    lines: Int = 2,
    pulse: State<Float> = rememberSkeletonPulse(),
) {
    val dimens = DeskTheme.dimens
    val touch = DeskTheme.inputMode == InputMode.Touch
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = if (lines > 1) (if (touch) 56.dp else 44.dp) else dimens.rowHeight.coerceAtLeast(dimens.minTarget))
            .padding(horizontal = dimens.pagePadding, vertical = dimens.gapS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.gapM),
    ) {
        if (leading) SkeletonBlock(Modifier.size(if (touch) 40.dp else 32.dp), pulse, RoundedCornerShape(dimens.radiusSmall))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBlock(Modifier.fillMaxWidth(lineWidths[index % lineWidths.size]).height(10.dp), pulse)
            if (lines > 1) SkeletonBlock(Modifier.fillMaxWidth(lineWidths[(index + 3) % lineWidths.size] * 0.6f).height(8.dp), pulse)
        }
        SkeletonBlock(Modifier.width(36.dp).height(8.dp), pulse)
    }
}

/** [count] placeholder rows for a list that is loading; TalkBack hears "Loading". */
@Composable
fun SkeletonList(modifier: Modifier = Modifier, count: Int = 8, leading: Boolean = true, lines: Int = 2) {
    val pulse = rememberSkeletonPulse()
    val label = stringResource(R.string.dk_loading)
    Column(modifier.semantics {
        contentDescription = label
        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
    }) {
        repeat(count) { i -> SkeletonListRow(index = i, leading = leading, lines = lines, pulse = pulse) }
    }
}
