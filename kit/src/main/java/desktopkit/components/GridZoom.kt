package desktopkit.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import desktopkit.R
import desktopkit.settings.DeskPreferences
import desktopkit.theme.DeskTheme
import desktopkit.theme.InputMode
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.input.pointer.isCtrlPressed as pointerCtrl
import androidx.compose.ui.input.pointer.isMetaPressed as pointerMeta

/**
 * How large a [DeskGrid]'s cells are, changed by the user: Ctrl with the mouse wheel (or a pinch on
 * the touchpad) over the grid, two fingers pinched on the screen, Ctrl with + and − on the keyboard
 * (Ctrl+0 goes back to [defaultCellWidth]), and TalkBack's actions. The width is the grid's least
 * cell width; the columns are as many as fit it. Make one with [rememberGridZoom].
 */
@Stable
class GridZoom @JvmOverloads constructor(
    val defaultCellWidth: Dp,
    initialCellWidth: Dp = defaultCellWidth,
    val minCellWidth: Dp = 96.dp,
    val maxCellWidth: Dp = 320.dp,
    private val onCommit: (Dp) -> Unit = {},
) {
    /** The grid's least cell width now. */
    var cellWidth: Dp by mutableStateOf(initialCellWidth.coerceIn(minCellWidth, maxCellWidth))
        private set

    /** One step larger (a quarter wider). */
    fun zoomIn() = set(snap(cellWidth * STEP))

    /** One step smaller. */
    fun zoomOut() = set(snap(cellWidth / STEP))

    /** Back to [defaultCellWidth]. */
    fun reset() = set(defaultCellWidth)

    /** During a pinch: follows the fingers, stored once they let go ([commit]). */
    internal fun follow(width: Dp) {
        cellWidth = width.coerceIn(minCellWidth, maxCellWidth)
    }

    internal fun commit() {
        cellWidth = snap(cellWidth)
        onCommit(cellWidth)
    }

    private fun set(width: Dp) {
        val next = width.coerceIn(minCellWidth, maxCellWidth)
        if (next == cellWidth) return
        cellWidth = next
        onCommit(next)
    }

    /** Whole multiples of 4 dp, as every size in the kit. */
    private fun snap(width: Dp): Dp = ((width.value / 4f).roundToInt() * 4).dp.coerceIn(minCellWidth, maxCellWidth)

    companion object {
        private const val STEP = 1.25f

        internal fun saver(default: Dp, min: Dp, max: Dp): Saver<GridZoom, Float> = Saver(
            save = { it.cellWidth.value },
            restore = { GridZoom(default, it.dp, min, max) },
        )
    }
}

/**
 * A [GridZoom] for a [DeskGrid]; with [prefs] and [key] (one key per grid of the app) the size the user
 * chose is kept in every window and after a restart.
 */
@Composable
fun rememberGridZoom(
    defaultCellWidth: Dp = if (DeskTheme.inputMode == InputMode.Touch) 152.dp else 136.dp,
    minCellWidth: Dp = 96.dp,
    maxCellWidth: Dp = 320.dp,
    prefs: DeskPreferences? = null,
    key: String? = null,
): GridZoom =
    if (prefs != null && key != null) {
        remember(prefs, key, defaultCellWidth, minCellWidth, maxCellWidth) {
            val stored = prefs.gridCellWidth(key)
            GridZoom(defaultCellWidth, if (stored.isNaN()) defaultCellWidth else stored.dp, minCellWidth, maxCellWidth) {
                prefs.updateGridCellWidth(key, it.value)
            }
        }
    } else {
        rememberSaveable(defaultCellWidth, minCellWidth, maxCellWidth, saver = GridZoom.saver(defaultCellWidth, minCellWidth, maxCellWidth)) {
            GridZoom(defaultCellWidth, defaultCellWidth, minCellWidth, maxCellWidth)
        }
    }

/** The gestures, keys and TalkBack actions of a [GridZoom], for the grid itself. */
@Composable
internal fun Modifier.gridZoom(zoom: GridZoom): Modifier {
    val larger = stringResource(R.string.dk_grid_larger)
    val smaller = stringResource(R.string.dk_grid_smaller)
    return this
        .pointerInput(zoom) {
            // Ctrl with the wheel: a step for each notch (a fine wheel's small deltas add up to one)
            var gathered = 0f
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type != PointerEventType.Scroll) continue
                    val mods = event.keyboardModifiers
                    if (!mods.pointerCtrl && !mods.pointerMeta) {
                        gathered = 0f
                        continue
                    }
                    event.changes.forEach { it.consume() }
                    gathered += event.changes.sumOf { it.scrollDelta.y.toDouble() }.toFloat()
                    while (abs(gathered) >= 1f) {
                        // the wheel turned away from the user (up) makes the cells larger
                        if (gathered < 0f) {
                            zoom.zoomIn()
                            gathered += 1f
                        } else {
                            zoom.zoomOut()
                            gathered -= 1f
                        }
                    }
                }
            }
        }
        .pointerInput(zoom) {
            // two fingers (or a touchpad's pinch) spread or pinched: the cells follow them
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var startSpan = 0f
                var startWidth = zoom.cellWidth
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty()) break
                    if (pressed.size >= 2) {
                        val span = (pressed[0].position - pressed[1].position).getDistance()
                        if (startSpan == 0f) {
                            startSpan = span
                            startWidth = zoom.cellWidth
                        } else if (startSpan > 0f) {
                            zoom.follow(startWidth * (span / startSpan))
                        }
                    }
                    // once a pinch began, nothing in the grid scrolls or opens until every finger is up
                    if (startSpan != 0f) event.changes.forEach { it.consume() }
                }
                if (startSpan != 0f) zoom.commit()
            }
        }
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            if (!(event.isCtrlPressed || event.isMetaPressed) || event.isAltPressed) return@onKeyEvent false
            when (event.key) {
                Key.Equals, Key.Plus, Key.NumPadAdd -> { zoom.zoomIn(); true }
                Key.Minus, Key.NumPadSubtract -> { zoom.zoomOut(); true }
                Key.Zero, Key.NumPad0 -> { zoom.reset(); true }
                else -> false
            }
        }
        .semantics {
            customActions = listOf(
                CustomAccessibilityAction(larger) { zoom.zoomIn(); true },
                CustomAccessibilityAction(smaller) { zoom.zoomOut(); true },
            )
        }
}
