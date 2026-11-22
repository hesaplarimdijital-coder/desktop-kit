package desktopkit.editor

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isSpecified
import desktopkit.R
import desktopkit.components.zoomKey
import desktopkit.settings.DeskPreferences
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.input.pointer.isCtrlPressed as pointerCtrl
import androidx.compose.ui.input.pointer.isMetaPressed as pointerMeta

/**
 * How large a [CodeEditor]'s text is, changed by the user the way a [desktopkit.components.GridZoom]
 * changes a grid: Ctrl with the mouse wheel over the editor, two fingers pinched on the screen, Ctrl
 * with + and − on the keyboard (Ctrl+0 goes back to the theme's size). [scale] multiplies the theme's
 * code style (`DeskTheme.type.code`), line height included, in steps of a tenth. Make one with
 * [rememberCodeEditorZoom]; the app's own menu entries call [zoomIn], [zoomOut] and [reset].
 *
 * Other text that is to zoom the same way (a preview beside the editor, a manual) takes the same
 * gestures with [Modifier.textZoom] and its size with [zoomed].
 */
@Stable
class CodeEditorZoom @JvmOverloads constructor(
    scale: Float = 1f,
    val minScale: Float = 0.7f,
    val maxScale: Float = 2.5f,
    private val onCommit: (Float) -> Unit = {},
) {
    /** The text's size as a multiple of the theme's; 1 is the theme's own. */
    var scale: Float by mutableFloatStateOf(snap(scale))
        private set

    /** A tenth larger. */
    fun zoomIn() = set(scale + STEP)

    /** A tenth smaller. */
    fun zoomOut() = set(scale - STEP)

    /** Back to the theme's size. */
    fun reset() = set(1f)

    /** During a pinch: follows the fingers from [start], stored once they let go ([commit]). */
    internal fun follow(start: Float, factor: Float) {
        scale = (start * factor).coerceIn(minScale, maxScale)
    }

    internal fun commit() {
        val snapped = snap(scale)
        scale = snapped
        onCommit(snapped)
    }

    private fun set(value: Float) {
        val next = snap(value)
        if (next == scale) return
        scale = next
        onCommit(next)
    }

    /** Whole tenths, so the sizes a user steps through are the same from every side. */
    private fun snap(value: Float): Float = ((value * 10f).roundToInt() / 10f).coerceIn(minScale, maxScale)

    companion object {
        private const val STEP = 0.1f

        internal fun saver(min: Float, max: Float): Saver<CodeEditorZoom, Float> = Saver(save = { it.scale }, restore = { CodeEditorZoom(it, min, max) })
    }
}

/**
 * A [CodeEditorZoom] for a [CodeEditor]; with [prefs] and [key] (one key for each kind of editor of
 * the app) the size the user chose is kept in every window and after a restart.
 */
@Composable
fun rememberCodeEditorZoom(prefs: DeskPreferences? = null, key: String? = null, minScale: Float = 0.7f, maxScale: Float = 2.5f): CodeEditorZoom =
    if (prefs != null && key != null) {
        remember(prefs, key, minScale, maxScale) {
            val stored = prefs.editorTextScale(key)
            CodeEditorZoom(if (stored.isNaN()) 1f else stored, minScale, maxScale) { prefs.updateEditorTextScale(key, it) }
        }
    } else {
        rememberSaveable(minScale, maxScale, saver = CodeEditorZoom.saver(minScale, maxScale)) { CodeEditorZoom(1f, minScale, maxScale) }
    }

/**
 * The editor's zoom gestures for text that is not a [CodeEditor] (a preview, a manual, a log), so it grows
 * and shrinks exactly as the editor's text does: over the element, Ctrl (or Meta) with the mouse wheel and
 * two fingers pinched; while the keyboard focus is inside it, Ctrl with + (or =), − and 0, by the character
 * the key types; for TalkBack, "Larger text" and "Smaller text". The text inside reads the size with
 * [zoomed]. Give the element and the editor one [CodeEditorZoom] (or two with the same key) to zoom them
 * together.
 *
 * ```
 * val zoom = rememberCodeEditorZoom(prefs, key = "manual")
 * LazyColumn(Modifier.fillMaxSize().textZoom(zoom)) {
 *     items(paragraphs) { Text(it, style = DeskTheme.type.body.zoomed(zoom)) }
 * }
 * ```
 */
@Composable
fun Modifier.textZoom(zoom: CodeEditorZoom): Modifier {
    val larger = stringResource(R.string.dk_editor_larger)
    val smaller = stringResource(R.string.dk_editor_smaller)
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
                        // the wheel turned away from the user (up) makes the text larger
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
            // two fingers (or a touchpad's pinch) spread or pinched: the text follows them
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var startSpan = 0f
                var startScale = zoom.scale
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty()) break
                    if (pressed.size >= 2) {
                        val span = (pressed[0].position - pressed[1].position).getDistance()
                        if (startSpan == 0f) {
                            startSpan = span
                            startScale = zoom.scale
                        } else if (startSpan > 0f) {
                            zoom.follow(startScale, span / startSpan)
                        }
                    }
                    // once a pinch began, nothing inside scrolls or opens until every finger is up
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

/** This style at [zoom]'s size: the font size, the line height and the letter spacing times [CodeEditorZoom.scale]. */
fun TextStyle.zoomed(zoom: CodeEditorZoom): TextStyle {
    val scale = zoom.scale
    if (scale == 1f) return this
    return copy(
        fontSize = if (fontSize.isSpecified) fontSize * scale else fontSize,
        lineHeight = if (lineHeight.isSpecified) lineHeight * scale else lineHeight,
        letterSpacing = if (letterSpacing.isSpecified) letterSpacing * scale else letterSpacing,
    )
}
