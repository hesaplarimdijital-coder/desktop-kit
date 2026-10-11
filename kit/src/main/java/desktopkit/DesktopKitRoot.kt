package desktopkit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import desktopkit.input.rememberInputModeState
import desktopkit.input.trackInputMode
import desktopkit.settings.DeskPreferences
import desktopkit.shell.WidthClass
import desktopkit.theme.DeskColors
import desktopkit.theme.DeskDimens
import desktopkit.theme.DeskFonts
import desktopkit.theme.DeskTheme
import desktopkit.theme.DesktopKitTheme

/**
 * Everything around an app's content in one call: the theme from [prefs], touch/pointer
 * density from the last input device (or the user's override), safe-area insets and the
 * window's [WidthClass]. Typical use in `setContent { }`:
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
    content: @Composable (WidthClass) -> Unit,
) {
    val inputMode = rememberInputModeState()
    DesktopKitTheme(
        themeMode = prefs.themeMode,
        accent = prefs.accent,
        inputMode = inputMode.effective(prefs.density),
        fonts = fonts,
        colors = colors,
        dimens = dimens,
    ) {
        BoxWithConstraints(
            modifier
                .fillMaxSize()
                .background(DeskTheme.colors.background)
                .trackInputMode(inputMode)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            content(WidthClass.fromWidth(maxWidth.value))
        }
    }
}
