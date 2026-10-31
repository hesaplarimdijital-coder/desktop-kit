package desktopkit.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import desktopkit.theme.DeskMotion
import desktopkit.theme.LocalMotionTier
import desktopkit.theme.MotionTier
import kotlinx.coroutines.delay

/**
 * The arrival of a list's rows on a phone: the first [ROWS] rows present when the list appears fade
 * in and rise a little, one after another ([STAGGER_MS] apart, each over [DURATION_MS]); rows that
 * come later (scrolled into view, added) are simply there. Wider up, and with reduced motion,
 * nothing moves.
 *
 * Take one with [rememberListAppear] where the list is, and mark each row with [listAppear]:
 *
 * ```
 * val appear = rememberListAppear(loaded)
 * LazyColumn { itemsIndexed(rows) { index, row -> Row(Modifier.listAppear(appear, index)) { … } } }
 * ```
 *
 * The keys of [rememberListAppear] say when the list is new: pass what changes when its rows arrive
 * (the loading flag, the album shown), and the rows arrive in turn after a skeleton or in a fresh
 * detail; leave them out and only the list's first appearance counts.
 */
@Stable
class ListAppear internal constructor(internal val enabled: Boolean) {
    /** Set once the window for the first rows has passed: rows remembered after it are simply there. */
    internal var settled = false

    companion object {
        /** How many rows arrive in turn; the rest are there at once. */
        const val ROWS = 8

        /** The delay between one row's arrival and the next's. */
        const val STAGGER_MS = 15

        /** Each row's fade and rise. */
        const val DURATION_MS = 180

        /** How long after the list appears a row still counts as one of the first. */
        internal const val WINDOW_MS = 120L
    }
}

/** The [ListAppear] of a list; new when [keys] change (see [ListAppear]). */
@Composable
fun rememberListAppear(vararg keys: Any?): ListAppear {
    val enabled = LocalMotionTier.current == MotionTier.Gesture
    val appear = remember(enabled, *keys) { ListAppear(enabled) }
    LaunchedEffect(appear) {
        delay(ListAppear.WINDOW_MS)
        appear.settled = true
    }
    return appear
}

/** The rise of a row as it arrives. */
private val RISE = 12.dp

/**
 * Marks the [index]th row of a list taken with [rememberListAppear]: one of the first [ListAppear.ROWS]
 * rows present when the list appears fades in and rises into place in its turn; any other row is
 * simply there. Put it first in the row's modifiers.
 */
@Composable
fun Modifier.listAppear(appear: ListAppear, index: Int): Modifier {
    if (!appear.enabled || index >= ListAppear.ROWS) return this
    // a row remembered once the window has passed starts, and stays, in place
    val progress = remember(appear) { Animatable(if (appear.settled) 1f else 0f) }
    LaunchedEffect(progress) {
        if (progress.value < 1f) {
            delay(index * ListAppear.STAGGER_MS.toLong())
            progress.animateTo(1f, tween(ListAppear.DURATION_MS, easing = DeskMotion.StandardDecelerate))
        }
    }
    return graphicsLayer {
        val value = progress.value
        alpha = value
        translationY = (1f - value) * RISE.toPx()
    }
}
