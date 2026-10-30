package desktopkit

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import desktopkit.input.rememberInputModeState
import desktopkit.input.trackInputMode
import desktopkit.settings.DeskPreferences
import desktopkit.shell.CaptionArea
import desktopkit.shell.CaptionElements
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
 * In a desktop window (DeX, Android's desktop windowing) the shell's menu bar fills the window's
 * caption bar: the root makes the caption bar transparent (Android 15 and later) and leaves it to the
 * shell, which keeps its menus and search clear of the system's window buttons.
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
        val captionTop = WindowInsets.captionBar.only(WindowInsetsSides.Top)
        // safeDrawing without the caption bar, for a shell whose menu bar fills it
        val belowCaption = WindowInsets.statusBars.union(WindowInsets.navigationBars).union(WindowInsets.displayCutout)
            .union(WindowInsets.ime)
        val rootInsets = remember(navigationBottom, captionTop) { RootInsets(navigationBottom, captionTop) }
        // read here, not while measuring: a shell registers while the content is being measured, and
        // a change made during that pass did not measure the padding again (the phone's first launch
        // kept a navigation-bar-high gap under the shell until something else relaid the window)
        val density = LocalDensity.current
        val shellDrawsNavigation = rootInsets.shells > 0
        val captionInUse = rootInsets.captionInUse(density)
        val padding = remember(safeDrawing, belowCaption, navigationBottom, shellDrawsNavigation, captionInUse) {
            RootPadding(safeDrawing, belowCaption, navigationBottom, shellDrawsNavigation, captionInUse)
        }
        // the window's caption bar and the system's elements on it, read again whenever the window is
        // laid out (resized, the caption bar shown or hidden) or the caption bar reaches the content
        val view = LocalView.current
        val captionPx = captionTop.getTop(density)
        DisposableEffect(view, rootInsets, captionPx) {
            val update = { rootInsets.caption = CaptionElements.area(view) }
            val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> update() }
            view.addOnLayoutChangeListener(listener)
            update()
            onDispose { view.removeOnLayoutChangeListener(listener) }
        }
        CaptionAppearance(transparent = rootInsets.captionClaims > 0 && rootInsets.caption != null, dark = DeskTheme.colors.isDark)
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
    /** The caption bar of a desktop window, as far as it reaches the app's content. */
    private val captionTop: WindowInsets = WindowInsets(0, 0, 0, 0),
) {
    /** How many shells draw behind [navigationBottom]; while one does, the root does not pad it. */
    var shells by mutableIntStateOf(0)

    /** How many shells draw their menu bar in the caption bar; while one does, it is transparent. */
    var captionClaims by mutableIntStateOf(0)

    /** The window's caption bar, when it has one and the system says where its elements are. */
    var caption by mutableStateOf<CaptionArea?>(null)

    /**
     * Whether a shell's menu bar lies in the caption bar: claimed, transparent and reaching the
     * content (the system keeps an opaque caption bar's insets from the content).
     */
    fun captionInUse(density: Density): Boolean = captionClaims > 0 && caption != null && captionTop.getTop(density) > 0
}

internal val LocalRootInsets = staticCompositionLocalOf<RootInsets?> { null }

/**
 * `safeDrawing`, without the bottom navigation bar while a shell draws behind it
 * ([shellDrawsNavigation]) and without the caption bar while a shell's menu bar fills it
 * ([captionInUse]). A new one is made whenever either changes, so the padding is measured again.
 */
private class RootPadding(
    private val safe: WindowInsets,
    private val belowCaption: WindowInsets,
    private val navigationBottom: WindowInsets,
    private val shellDrawsNavigation: Boolean,
    private val captionInUse: Boolean,
) : WindowInsets {
    override fun getLeft(density: Density, layoutDirection: LayoutDirection) = safe.getLeft(density, layoutDirection)
    override fun getTop(density: Density) = if (captionInUse) belowCaption.getTop(density) else safe.getTop(density)
    override fun getRight(density: Density, layoutDirection: LayoutDirection) = safe.getRight(density, layoutDirection)
    override fun getBottom(density: Density): Int {
        val bottom = safe.getBottom(density)
        return if (shellDrawsNavigation) (bottom - navigationBottom.getBottom(density)).coerceAtLeast(0) else bottom
    }

    override fun equals(other: Any?) =
        other is RootPadding && other.safe == safe && other.belowCaption == belowCaption &&
            other.navigationBottom == navigationBottom && other.shellDrawsNavigation == shellDrawsNavigation &&
            other.captionInUse == captionInUse

    override fun hashCode(): Int {
        var result = safe.hashCode()
        result = 31 * result + belowCaption.hashCode()
        result = 31 * result + navigationBottom.hashCode()
        result = 31 * result + shellDrawsNavigation.hashCode()
        return 31 * result + captionInUse.hashCode()
    }
}

/**
 * A transparent caption bar while a shell's menu bar fills it (Android 15 and later), with window
 * buttons that suit the theme. The system then leaves touches on the menus and the search field,
 * which exclude themselves from its gestures, to the app; the rest of the bar still moves the window.
 */
@Composable
private fun CaptionAppearance(transparent: Boolean, dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode || Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
    DisposableEffect(view, transparent, dark) {
        val controller = view.context.findActivity()?.window?.insetsController
        val mask = WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND or
            WindowInsetsController.APPEARANCE_LIGHT_CAPTION_BARS
        if (transparent) {
            val light = if (dark) 0 else WindowInsetsController.APPEARANCE_LIGHT_CAPTION_BARS
            controller?.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND or light, mask)
        }
        onDispose { if (transparent) controller?.setSystemBarsAppearance(0, mask) }
    }
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
