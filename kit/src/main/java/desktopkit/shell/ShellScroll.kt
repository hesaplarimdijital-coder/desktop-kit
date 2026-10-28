package desktopkit.shell

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import desktopkit.theme.DeskMotion
import desktopkit.theme.MotionTier

/**
 * How the phone (compact) chrome follows the content's scrolling, as Android's own apps do:
 * - scrolling down slides the bottom navigation bar (and the strip above it) out of the way, so the
 *   list gets the whole height; scrolling up brings them back. When the finger lifts half way, they
 *   settle fully shown or fully hidden, on a spring that takes over the fling's speed;
 * - once the content has moved away from its top, the top bar takes the chrome colour (glass) and
 *   a thin rule appears under it, so the title stays apart from the content passing beneath it.
 *
 * [offsetPx] is only read while drawing (a layer translation), so a scrolled pixel never measures
 * or recomposes the screen. The shell creates one and resets it whenever the screen changes.
 */
@Stable
internal class ShellScrollState {
    /** Pixels of the bottom chrome pushed below the window: 0 shown, [maxOffsetPx] hidden. */
    var offsetPx by mutableFloatStateOf(0f)
        private set

    /** Height of the bottom navigation bar; how far it can move. */
    var maxOffsetPx by mutableFloatStateOf(0f)

    /** The content has scrolled away from its top. */
    var scrolled by mutableStateOf(false)
        private set

    /** False on wider windows: nothing moves and the top bar stays as it is. */
    var enabled by mutableStateOf(true)

    /** Hides the bottom chrome while scrolling down; false keeps it in place (the top bar still reacts). */
    var hideOnScroll by mutableStateOf(true)

    /** How the settle moves: a spring on phones, nothing with reduced motion. */
    var tier: MotionTier = MotionTier.Gesture

    val connection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (!enabled) return Offset.Zero
            if (consumed.y < 0f) scrolled = true
            // a pull downwards that the content could not take: it is at its top
            if (available.y > 0f) scrolled = false
            if (hideOnScroll && consumed.y != 0f) {
                offsetPx = (offsetPx - consumed.y).coerceIn(0f, maxOffsetPx)
            }
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            // the bars move against the content; what the list could not use (it hit an end) carries on into them
            if (enabled && hideOnScroll) settle(-available.y)
            return Velocity.Zero
        }
    }

    /**
     * Finishes a half-way bar: fully hidden past half its height, fully shown otherwise. [velocity]
     * (px/s, positive = downwards) starts the spring at the speed the content was moving.
     */
    suspend fun settle(velocity: Float = 0f) {
        val start = offsetPx
        val target = settleTarget(start, maxOffsetPx) ?: return
        when (tier) {
            MotionTier.Reduced -> offsetPx = target
            MotionTier.Gesture -> animate(start, target, initialVelocity = velocity, animationSpec = DeskMotion.gesture()) { value, _ ->
                offsetPx = value.coerceIn(0f, maxOffsetPx)
            }
            MotionTier.Timed -> animate(start, target, animationSpec = tween(DeskMotion.MEDIUM_MS + 50, easing = DeskMotion.Emphasized)) { value, _ ->
                offsetPx = value
            }
        }
    }

    /** Brings the bottom chrome back at once (an expanding player starts from it). */
    fun showBars() {
        offsetPx = 0f
    }

    /** A new screen starts with the chrome shown and the top bar flat. */
    fun reset() {
        offsetPx = 0f
        scrolled = false
    }

    companion object {
        /** Where a bar left at [offset] of [max] settles; null when it is already at rest. Half-way rule of 0.2.0. */
        fun settleTarget(offset: Float, max: Float): Float? {
            if (offset <= 0f || offset >= max) return null
            return if (offset > max / 2f) max else 0f
        }
    }
}
