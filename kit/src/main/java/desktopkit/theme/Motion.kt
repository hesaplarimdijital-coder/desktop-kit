package desktopkit.theme

import android.animation.ValueAnimator
import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import desktopkit.shell.WidthClass

/**
 * Motion tokens of the kit, taken from Android's own motion system (Material 3), so the kit moves
 * like the system around it: panels, menus and screens use the emphasized curves, small state
 * changes (colour, press) the standard ones. Every duration follows the system animation scale;
 * with "Remove animations" on nothing moves.
 *
 * Two tiers ([MotionTier]): on a phone, things that move in space or follow a finger use
 * interruptible springs that pick up the fling velocity ([gesture]); on tablet and DeX windows the
 * timed curves of 0.2.0 stay as they were. Fades and colour changes are short tweens everywhere.
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

    /** Accent and theme colour changes (an accent taken from artwork cross-fades in this time). */
    const val COLOR_CHANGE_MS = 150

    /** Colour of hover, press and selection backgrounds. */
    fun <T> color(): FiniteAnimationSpec<T> = tween(150, easing = Standard)

    /** Spatial spring for sizes and positions (indicator pills, panels); a hint of bounce, settles fast. */
    fun <T> spatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.85f, stiffness = 700f)

    /** Fast spring for small elements (press scale, thumbs). */
    fun <T> fastSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 1400f)

    /** Opacity and colour springs without overshoot. */
    fun <T> effects(): FiniteAnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1600f)

    /**
     * The phone's spring for everything a finger moves or flings: bars, sheets, the player, screen
     * scale. No bounce, medium-low stiffness, so a new touch can catch it anywhere and a fling hands
     * over its speed.
     */
    fun <T> gesture(): SpringSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    /** The tier for a window: springs on a phone, the timed curves from tablet width up, nothing with [reduced] motion. */
    fun tier(widthClass: WidthClass, reduced: Boolean = false): MotionTier = when {
        reduced -> MotionTier.Reduced
        widthClass == WidthClass.Compact -> MotionTier.Gesture
        else -> MotionTier.Timed
    }

    /**
     * A spatial animation (position, size, scale) for [tier]: the [gesture] spring on a phone,
     * [timed] (the 0.2.0 curve) on wider windows, a jump to the end with reduced motion.
     */
    fun <T> spatial(tier: MotionTier, timed: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> = when (tier) {
        MotionTier.Gesture -> gesture()
        MotionTier.Timed -> timed
        MotionTier.Reduced -> snap()
    }

    /** A fade or colour change: [spec] in both tiers, a jump with reduced motion. */
    fun <T> fade(tier: MotionTier, spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
        if (tier == MotionTier.Reduced) snap() else spec
}

/** How the kit moves in a window; see [DeskMotion.tier]. */
enum class MotionTier {
    /** Phone: interruptible, velocity-aware springs for spatial and finger-driven motion. */
    Gesture,

    /** Tablet and DeX: the timed Material curves (90 ms fade-out, 210 ms scale-in, 150 ms cross-fade, 300 ms panels). */
    Timed,

    /** "Remove animations" or an animator duration scale of 0: everything jumps to its end. */
    Reduced,
}

/** The motion tier of the surrounding [desktopkit.shell.DesktopShell] (or [desktopkit.DesktopKitRoot]). */
val LocalMotionTier = compositionLocalOf { MotionTier.Timed }

/**
 * True while the system asks for no animation: "Remove animations" in the accessibility settings,
 * or the animator duration scale set to 0 in the developer options. Follows the setting live.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    var reduced by remember { mutableStateOf(SystemMotion.reduced(context)) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = SystemMotion.reduced(context)
            }
        }
        val resolver = context.contentResolver
        runCatching { resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer) }
        onDispose { runCatching { resolver.unregisterContentObserver(observer) } }
    }
    return reduced
}

/** The system's animation switches, readable outside Compose (View code, animators). */
object SystemMotion {
    /** "Remove animations" is on, or the animator duration scale is 0. */
    fun reduced(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()) return true
        val scale = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        return scale == 0f
    }
}
