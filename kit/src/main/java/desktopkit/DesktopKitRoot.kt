package desktopkit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
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
        BoxWithConstraints(
            modifier
                .fillMaxSize()
                .background(DeskTheme.colors.background)
                .trackInputMode(inputMode)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val widthClass = WidthClass.fromWidth(maxWidth.value)
            val reduced = rememberReducedMotion()
            CompositionLocalProvider(
                LocalWidthClass provides widthClass,
                LocalMotionTier provides DeskMotion.tier(widthClass, reduced),
            ) { content(widthClass) }
        }
    }
}
