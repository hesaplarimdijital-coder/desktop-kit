package desktopkit.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How the user is interacting right now. The shell switches to [Pointer] when a mouse or
 * touchpad drives the pointer (DeX, keyboard cover) and back to [Touch] on the next finger
 * input.
 */
enum class InputMode { Touch, Pointer }

/** User override of the density; [Auto] follows the last input device. */
enum class DensityPreference { Auto, Touch, Pointer }

/** Size tokens for one input density. Pointer targets may be smaller than 48 dp but never below 24 dp. */
@Immutable
data class DeskDimens(
    val minTarget: Dp,
    val controlHeight: Dp,
    val rowHeight: Dp,
    val navItemHeight: Dp,
    val icon: Dp,
    val iconLarge: Dp,
    val menuBarHeight: Dp,
    val topBarHeight: Dp,
    val bottomBarHeight: Dp,
    val railWidth: Dp,
    val sidebarWidth: Dp,
    val sidebarCollapsedWidth: Dp,
    /** Height of a compact bottom strip (mini player, status bar). */
    val miniPlayerHeight: Dp,
    val radiusSmall: Dp,
    val radiusControl: Dp,
    val radiusCard: Dp,
    val radiusPill: Dp,
    val gapXs: Dp,
    val gapS: Dp,
    val gapM: Dp,
    val gapL: Dp,
    val gapXl: Dp,
    val pagePadding: Dp,
) {
    companion object {
        val Pointer = DeskDimens(
            minTarget = 24.dp,
            controlHeight = 28.dp,
            rowHeight = 32.dp,
            navItemHeight = 32.dp,
            icon = 16.dp,
            iconLarge = 20.dp,
            menuBarHeight = 40.dp,
            topBarHeight = 48.dp,
            bottomBarHeight = 56.dp,
            railWidth = 72.dp,
            sidebarWidth = 240.dp,
            sidebarCollapsedWidth = 56.dp,
            miniPlayerHeight = 64.dp,
            radiusSmall = 4.dp,
            radiusControl = 6.dp,
            radiusCard = 10.dp,
            radiusPill = 14.dp,
            gapXs = 4.dp,
            gapS = 8.dp,
            gapM = 12.dp,
            gapL = 16.dp,
            gapXl = 24.dp,
            pagePadding = 20.dp,
        )

        val Touch = DeskDimens(
            minTarget = 48.dp,
            controlHeight = 44.dp,
            rowHeight = 56.dp,
            navItemHeight = 48.dp,
            icon = 22.dp,
            iconLarge = 26.dp,
            menuBarHeight = 48.dp,
            topBarHeight = 56.dp,
            bottomBarHeight = 72.dp,
            railWidth = 88.dp,
            sidebarWidth = 280.dp,
            sidebarCollapsedWidth = 72.dp,
            miniPlayerHeight = 72.dp,
            radiusSmall = 6.dp,
            radiusControl = 12.dp,
            radiusCard = 16.dp,
            radiusPill = 24.dp,
            gapXs = 4.dp,
            gapS = 8.dp,
            gapM = 12.dp,
            gapL = 16.dp,
            gapXl = 24.dp,
            pagePadding = 16.dp,
        )

        fun forMode(mode: InputMode) = if (mode == InputMode.Pointer) Pointer else Touch
    }
}

/**
 * The sizes for a compact (phone) window: a 64 dp bottom bar (the height of Android's current
 * navigation bar) and 52 dp touch rows, so more of a small screen goes to content. Targets stay
 * at least 48 dp. Pointer sizes are already small and stay as they are. The shell applies this on
 * compact windows only (`DesktopShell(compactSizes = true)`); tablet and DeX keep [DeskDimens.Touch]
 * and [DeskDimens.Pointer].
 */
fun DeskDimens.forCompactWindow(): DeskDimens =
    if (minTarget < 48.dp) this else copy(
        bottomBarHeight = bottomBarHeight.coerceAtMost(64.dp),
        rowHeight = rowHeight.coerceAtMost(52.dp).coerceAtLeast(minTarget),
    )
