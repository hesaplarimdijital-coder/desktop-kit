package desktopkit.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope

/**
 * How far a finger has moved something from its place, in px: a toast swiped aside, a sheet pulled
 * down. The finger moves it at once ([moveBy], [moveTo]); let go, [settle] carries it out or back
 * until it has arrived or the finger takes it again.
 *
 * Not an `Animatable` moved with `launch { snapTo() }`, as from 2.0 to 2.4.0: a finger lifted in
 * motion gives the window its last move and the lift together, and Compose's `draggable` starts
 * `onDragStopped` before anything that waits in the queue has run. The last move's coroutine then
 * ran after the release had begun, and its `snapTo` stopped the animation that was to carry the
 * thing out or back: the toast or the sheet stayed where the finger left it. Here nothing of a move
 * waits in a queue.
 */
@Stable
internal class FingerOffset {
    /** Read it in a layer or a placement: nothing recomposes as the thing moves. */
    var value by mutableFloatStateOf(0f)
        private set

    /** What carries the thing now that the finger has let go of it. */
    private var settling: Job? = null

    /** The finger (or a reset) puts it [to] px from its place; what was carrying it lets go. */
    fun moveTo(to: Float) {
        settling?.cancel()
        settling = null
        value = to
    }

    /** The finger moved it [delta] px. */
    fun moveBy(delta: Float) = moveTo(value + delta)

    /**
     * Carries it [to] from where it is, at [velocity] px/s to begin with; [onFrame] is told each
     * place on the way. When the finger takes the thing again before it has arrived, the way is
     * cancelled and this call with it: what the caller had after it does not run.
     */
    suspend fun settle(to: Float, spec: AnimationSpec<Float>, velocity: Float = 0f, onFrame: (Float) -> Unit = {}) {
        settling?.cancel()
        coroutineScope {
            val carrying = coroutineContext[Job]
            settling = carrying
            try {
                animate(value, to, velocity, spec) { at, _ ->
                    value = at
                    onFrame(at)
                }
            } finally {
                if (settling === carrying) settling = null
            }
        }
    }
}
