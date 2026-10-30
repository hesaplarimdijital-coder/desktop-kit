package desktopkit.shell

import android.graphics.Rect
import android.os.Build
import android.view.View
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.max

/**
 * The caption bar of a desktop window (DeX, Android's desktop windowing) that the shell's menu bar
 * fills, in px: its [height] and the room the system's own elements take at its [left] and [right]
 * ends (the app chip, the window buttons).
 */
@Immutable
internal data class CaptionArea(val height: Int, val left: Int, val right: Int)

/** Provided to the menu bar while it lies in the caption bar. */
internal val LocalCaptionArea = staticCompositionLocalOf<CaptionArea?> { null }

/** Where the system draws its caption elements, from the window's insets (Android 15 and later). */
internal object CaptionElements {

    /** Stands in for the system in tests: the caption bar's height and its elements, in window px. */
    var testCaption: Pair<Int, List<Rect>>? = null

    /**
     * The window's caption bar as the system reports it to the window, before the decor view consumes
     * it: the system may keep an opaque caption bar's insets from the app's content.
     */
    fun area(view: View): CaptionArea? {
        val width = view.rootView.width
        testCaption?.let { (height, rects) -> return area(rects, height, width) }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return null
        val insets = view.rootWindowInsets ?: return null
        val type = android.view.WindowInsets.Type.captionBar()
        return area(insets.getBoundingRects(type), insets.getInsets(type).top, width)
    }

    /**
     * The caption area of a window [width] px wide whose caption bar is [height] px high, with the
     * system's elements at [rects]; null without a caption bar, or when the system does not say where
     * its elements are (the menu bar then stays below the caption bar, which the system draws).
     */
    fun area(rects: List<Rect>, height: Int, width: Int): CaptionArea? {
        if (height <= 0 || width <= 0 || rects.isEmpty()) return null
        var left = 0
        var right = 0
        rects.forEach { rect ->
            if (rect.centerX() < width / 2) left = max(left, rect.right) else right = max(right, width - rect.left)
        }
        return CaptionArea(height, left.coerceIn(0, width), right.coerceIn(0, width))
    }
}
