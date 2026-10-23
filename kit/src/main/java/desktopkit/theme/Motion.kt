package desktopkit.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Motion tokens of the kit, taken from Android's own motion system (Material 3), so the kit moves
 * like the system around it: panels, menus and screens use the emphasized curves, small state
 * changes (colour, press) the standard ones. Every duration follows the system animation scale;
 * with "Remove animations" on nothing moves.
 */
object DeskMotion {
    /** Things that move on screen and come to rest (screens, panels). */
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Entering elements: fast start, long soft landing. */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Leaving elements: they speed up and are gone. */
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Small utility changes: colour, opacity. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val StandardDecelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)
    val StandardAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    const val SHORT_MS = 100
    const val MEDIUM_MS = 200
    const val LONG_MS = 300
    const val EXTRA_LONG_MS = 400

    /** Colour of hover, press and selection backgrounds. */
    fun <T> color(): FiniteAnimationSpec<T> = tween(150, easing = Standard)

    /** Spatial spring for sizes and positions (indicator pills, panels); a hint of bounce, settles fast. */
    fun <T> spatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.85f, stiffness = 700f)

    /** Fast spring for small elements (press scale, thumbs). */
    fun <T> fastSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 1400f)

    /** Opacity and colour springs without overshoot. */
    fun <T> effects(): FiniteAnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1600f)
}
