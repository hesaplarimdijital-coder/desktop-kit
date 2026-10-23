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

/**
 * How the phone (compact) chrome follows the content's scrolling, as Android's own apps do:
 * - scrolling down slides the bottom navigation bar (and the strip above it) out of the way, so the
 *   list gets the whole height; scrolling up brings them back. When the finger lifts half way, they
 *   settle fully shown or fully hidden;
 * - once the content has moved away from its top, the top bar takes the chrome colour and a thin
 *   rule appears under it, so the title stays apart from the content passing beneath it.
 *
 * The shell creates one and resets it whenever the screen changes.
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
            if (enabled && hideOnScroll) settle()
            return Velocity.Zero
        }
    }

    /** Finishes a half-way bar: fully hidden past half its height, fully shown otherwise. */
    suspend fun settle() {
        val start = offsetPx
        if (start <= 0f || start >= maxOffsetPx) return
        val target = if (start > maxOffsetPx / 2f) maxOffsetPx else 0f
        animate(start, target, animationSpec = tween(DeskMotion.MEDIUM_MS + 50, easing = DeskMotion.Emphasized)) { value, _ ->
            offsetPx = value
        }
    }

    /** A new screen starts with the chrome shown and the top bar flat. */
    fun reset() {
        offsetPx = 0f
        scrolled = false
    }
}
