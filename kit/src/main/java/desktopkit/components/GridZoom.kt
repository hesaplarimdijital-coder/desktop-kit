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
import androidx.compose.ui.input.key.KeyEvent
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
import desktopkit.input.typedCharacter
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
 *
 * Until the user chooses a size the grid follows [defaultCellWidth], which may change (the default is larger for a
 * finger than for a mouse); a size the user chose stays when it does, until Ctrl+0 ([reset]).
 */
@Stable
class GridZoom(
    defaultCellWidth: Dp,
    chosenCellWidth: Dp? = null,
    val minCellWidth: Dp = 96.dp,
    val maxCellWidth: Dp = 320.dp,
    private val onCommit: (Dp?) -> Unit = {},
) {
    /** The width the grid has while the user has chosen none. */
    var defaultCellWidth: Dp by mutableStateOf(defaultCellWidth)

    /** The width the user chose, or null: the grid has [defaultCellWidth]. */
    var chosenCellWidth: Dp? by mutableStateOf(chosenCellWidth?.coerceIn(minCellWidth, maxCellWidth))
        private set

    /** The grid's least cell width now. */
    val cellWidth: Dp get() = (chosenCellWidth ?: defaultCellWidth).coerceIn(minCellWidth, maxCellWidth)

    /** One step larger (a quarter wider). */
    fun zoomIn() = set(snap(cellWidth * STEP))

    /** One step smaller. */
    fun zoomOut() = set(snap(cellWidth / STEP))

    /** Back to [defaultCellWidth], which the grid follows again. */
    fun reset() {
        if (chosenCellWidth == null) return
        chosenCellWidth = null
        onCommit(null)
    }

    /** During a pinch: follows the fingers, stored once they let go ([commit]). */
    internal fun follow(width: Dp) {
        chosenCellWidth = width.coerceIn(minCellWidth, maxCellWidth)
    }

    internal fun commit() {
        val width = chosenCellWidth ?: return
        val snapped = snap(width)
        chosenCellWidth = snapped
        onCommit(snapped)
    }

    private fun set(width: Dp) {
        val next = width.coerceIn(minCellWidth, maxCellWidth)
        if (next == chosenCellWidth) return
        chosenCellWidth = next
        onCommit(next)
    }

    /** Whole multiples of 4 dp, as every size in the kit. */
    private fun snap(width: Dp): Dp = ((width.value / 4f).roundToInt() * 4).dp.coerceIn(minCellWidth, maxCellWidth)

    companion object {
        private const val STEP = 1.25f

        /** Keeps the chosen width (NaN for none); the default comes from the composition again. */
        internal fun saver(default: Dp, min: Dp, max: Dp): Saver<GridZoom, Float> = Saver(
            save = { it.chosenCellWidth?.value ?: Float.NaN },
            restore = { GridZoom(default, if (it.isNaN()) null else it.dp, min, max) },
        )
    }
}

/**
 * A [GridZoom] for a [DeskGrid]; with [prefs] and [key] (one key per grid of the app) the size the user
 * chose is kept in every window and after a restart. [defaultCellWidth] may change from one composition to the next
 * (by default it is larger for a finger): the grid follows it until the user chooses a size.
 */
@Composable
fun rememberGridZoom(
    defaultCellWidth: Dp = if (DeskTheme.inputMode == InputMode.Touch) 152.dp else 136.dp,
    minCellWidth: Dp = 96.dp,
    maxCellWidth: Dp = 320.dp,
    prefs: DeskPreferences? = null,
    key: String? = null,
): GridZoom {
    val zoom = if (prefs != null && key != null) {
        remember(prefs, key, minCellWidth, maxCellWidth) {
            val stored = prefs.gridCellWidth(key)
            GridZoom(defaultCellWidth, if (stored.isNaN()) null else stored.dp, minCellWidth, maxCellWidth) {
                prefs.updateGridCellWidth(key, it?.value ?: Float.NaN)
            }
        }
    } else {
        rememberSaveable(minCellWidth, maxCellWidth, saver = GridZoom.saver(defaultCellWidth, minCellWidth, maxCellWidth)) {
            GridZoom(defaultCellWidth, null, minCellWidth, maxCellWidth)
        }
    }
    // a new default (the user touched the screen after the mouse) is followed; a size the user chose stays
    zoom.defaultCellWidth = defaultCellWidth
    return zoom
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
            when (zoomKey(event)) {
                1 -> { zoom.zoomIn(); true }
                -1 -> { zoom.zoomOut(); true }
                0 -> { zoom.reset(); true }
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

/**
 * Which way a key held with Ctrl zooms: 1 larger (+ or =), -1 smaller (−), 0 back to the default (0), null not at all.
 * The keypad's keys go by the key; the others by the character they type, as a browser's zoom does: on a Turkish Q
 * keyboard − is the key a US one has = on, and + is Shift+4.
 */
private fun zoomKey(event: KeyEvent): Int? {
    when (event.key) {
        Key.NumPadAdd -> return 1
        Key.NumPadSubtract -> return -1
        Key.NumPad0 -> return 0
    }
    return when (typedCharacter(event)) {
        '+', '=' -> 1
        '-' -> -1
        '0' -> 0
        null -> when (event.key) {
            // a key that types nothing (an event made by hand): its US meaning
            Key.Equals, Key.Plus -> 1
            Key.Minus -> -1
            Key.Zero -> 0
            else -> null
        }
        else -> null
    }
}
