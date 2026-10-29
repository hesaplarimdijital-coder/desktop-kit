package desktopkit

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import desktopkit.input.rememberInputModeState
import desktopkit.input.trackInputMode
import desktopkit.settings.DeskPreferences
import desktopkit.shell.LocalWidthClass
import desktopkit.shell.WidthClass
import desktopkit.theme.Accent
import desktopkit.theme.DeskColors
import desktopkit.theme.DeskDimens
import desktopkit.theme.DeskMotion
import desktopkit.theme.LocalMotionTier
import desktopkit.theme.rememberReducedMotion
import desktopkit.theme.DeskFonts
import desktopkit.theme.DeskTheme
import desktopkit.theme.DeskType
import desktopkit.theme.InputMode
import desktopkit.theme.DesktopKitTheme

/**
 * Everything around an app's content in one call: the theme from [prefs], touch/pointer
 * density from the last input device (or the user's override), safe-area insets and the
 * window's [WidthClass] (also provided as [LocalWidthClass], with its motion tier as
 * [desktopkit.theme.LocalMotionTier]). The insets are `WindowInsets.safeDrawing`, the on-screen keyboard
 * included: with `enableEdgeToEdge()` and `adjustResize` the content ends just above the
 * keyboard. The insets are consumed here, so an `imePadding()` further down adds nothing.
 *
 * The navigation bar at the bottom (the three buttons, or the gesture handle) is left to a
 * [desktopkit.shell.DesktopShell] inside: the shell draws its chrome behind it (the phone's glass bottom
 * bar, the chrome colour on tablets) and keeps its own controls above it. Without a shell the root
 * pads it like the other insets.
 *
 * With [systemBarsAppearance] the status and navigation bar icons follow the kit's theme (dark icons
 * on the light theme) and the system draws no scrim behind three-button navigation, since the kit's
 * chrome already lies there.
 *
 * [fonts], [colors], [dimens] and [type] are passed to [DesktopKitTheme]. [accent] overrides the stored
 * accent without saving it, e.g. a colour taken from the playing track's artwork
 * (`Accent.custom("Artwork", color)`); changes cross-fade in 150 ms. Typical use in `setContent { }`:
 *
 * ```
 * val prefs = rememberDeskPreferences()
 * DesktopKitRoot(prefs) { widthClass ->
 *     DesktopShell(spec, widthClass, sidebarCollapsed = prefs.sidebarCollapsed,
 *         onSidebarCollapsedChange = prefs::updateSidebarCollapsed) { route, navigator -> … }
 * }
 * ```
 */
@Composable
fun DesktopKitRoot(
    prefs: DeskPreferences,
    modifier: Modifier = Modifier,
    fonts: DeskFonts = DeskFonts.Plex,
    colors: (DeskColors) -> DeskColors = { it },
    dimens: (DeskDimens) -> DeskDimens = { it },
    type: (InputMode, DeskFonts) -> DeskType = { mode, fonts -> DeskType.forMode(mode, fonts) },
    accent: Accent = prefs.accent,
    systemBarsAppearance: Boolean = true,
    content: @Composable (WidthClass) -> Unit,
) {
    val inputMode = rememberInputModeState()
    DesktopKitTheme(
        themeMode = prefs.themeMode,
        accent = accent,
        inputMode = inputMode.effective(prefs.density),
        fonts = fonts,
        colors = colors,
        dimens = dimens,
        type = type,
    ) {
        if (systemBarsAppearance) SystemBarsAppearance(dark = DeskTheme.colors.isDark)
        val safeDrawing = WindowInsets.safeDrawing
        // the keyboard covers the navigation bar: what is left of the bar above it
        val navigationBottom = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).exclude(WindowInsets.ime)
        val rootInsets = remember(navigationBottom) { RootInsets(navigationBottom) }
        val padding = remember(safeDrawing, rootInsets) { RootPadding(safeDrawing, rootInsets) }
        BoxWithConstraints(
            modifier
                .fillMaxSize()
                .background(DeskTheme.colors.background)
                .trackInputMode(inputMode)
                .windowInsetsPadding(padding),
        ) {
            val widthClass = WidthClass.fromWidth(maxWidth.value)
            val reduced = rememberReducedMotion()
            CompositionLocalProvider(
                LocalWidthClass provides widthClass,
                LocalMotionTier provides DeskMotion.tier(widthClass, reduced),
                LocalRootInsets provides rootInsets,
            ) { content(widthClass) }
        }
    }
}

/** Insets the root leaves to a [desktopkit.shell.DesktopShell] inside it. */
@Stable
internal class RootInsets(
    /** The bottom navigation bar (three buttons or the gesture handle), less the keyboard covering it. */
    val navigationBottom: WindowInsets,
) {
    /** How many shells draw behind [navigationBottom]; while one does, the root does not pad it. */
    var shells by mutableIntStateOf(0)
}

internal val LocalRootInsets = staticCompositionLocalOf<RootInsets?> { null }

/** `safeDrawing`, without the bottom navigation bar while a shell draws behind it. Read while measuring. */
private class RootPadding(private val safe: WindowInsets, private val root: RootInsets) : WindowInsets {
    override fun getLeft(density: Density, layoutDirection: LayoutDirection) = safe.getLeft(density, layoutDirection)
    override fun getTop(density: Density) = safe.getTop(density)
    override fun getRight(density: Density, layoutDirection: LayoutDirection) = safe.getRight(density, layoutDirection)
    override fun getBottom(density: Density): Int {
        val bottom = safe.getBottom(density)
        return if (root.shells > 0) (bottom - root.navigationBottom.getBottom(density)).coerceAtLeast(0) else bottom
    }

    override fun equals(other: Any?) = other is RootPadding && other.safe == safe && other.root === root
    override fun hashCode() = 31 * safe.hashCode() + root.hashCode()
}

/** Status and navigation bar icons that suit the kit's theme; no system scrim over the kit's chrome. */
@Composable
private fun SystemBarsAppearance(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view, dark) {
        view.context.findActivity()?.window?.let { window ->
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        applyBarAppearance(view, dark)
        onDispose { }
    }
}

private fun applyBarAppearance(view: View, dark: Boolean) {
    val window = view.context.findActivity()?.window ?: return
    WindowCompat.getInsetsController(window, view).apply {
        isAppearanceLightStatusBars = !dark
        isAppearanceLightNavigationBars = !dark
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
