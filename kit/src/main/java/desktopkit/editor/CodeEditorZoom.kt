package desktopkit.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import desktopkit.settings.DeskPreferences
import kotlin.math.roundToInt

/**
 * How large a [CodeEditor]'s text is, changed by the user the way a [desktopkit.components.GridZoom]
 * changes a grid: Ctrl with the mouse wheel over the editor, two fingers pinched on the screen, Ctrl
 * with + and − on the keyboard (Ctrl+0 goes back to the theme's size). [scale] multiplies the theme's
 * code style (`DeskTheme.type.code`), line height included, in steps of a tenth. Make one with
 * [rememberCodeEditorZoom]; the app's own menu entries call [zoomIn], [zoomOut] and [reset].
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
