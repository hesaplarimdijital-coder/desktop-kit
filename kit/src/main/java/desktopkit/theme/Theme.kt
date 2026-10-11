package desktopkit.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

val LocalDeskColors = staticCompositionLocalOf { DeskPalette.dark(Accent.Blue) }
val LocalDeskType = staticCompositionLocalOf { DeskType.Pointer }
val LocalDeskDimens = staticCompositionLocalOf { DeskDimens.Pointer }
val LocalInputMode = staticCompositionLocalOf { InputMode.Pointer }

/** Token access for composables: `DeskTheme.colors.text`, `DeskTheme.dimens.rowHeight`, … */
object DeskTheme {
    val colors: DeskColors
        @Composable @ReadOnlyComposable get() = LocalDeskColors.current
    val type: DeskType
        @Composable @ReadOnlyComposable get() = LocalDeskType.current
    val dimens: DeskDimens
        @Composable @ReadOnlyComposable get() = LocalDeskDimens.current
    val inputMode: InputMode
        @Composable @ReadOnlyComposable get() = LocalInputMode.current
}

/**
 * Desktop-class theme: light, dark and OLED black palettes, an accent colour and two
 * densities (touch and pointer). Material 3 components read a matching colour scheme, so they
 * blend in with the kit's own components.
 *
 * Adjust the look of one app without forking the kit:
 * - [accent]: a preset or `Accent.custom("Brand", Color(0xFF…))`;
 * - [fonts]: `DeskFonts.System` or your own families;
 * - [colors] / [dimens]: transform the computed tokens, e.g. `{ it.copy(radiusCard = 4.dp) }`.
 */
@Composable
fun DesktopKitTheme(
    themeMode: ThemeMode = ThemeMode.System,
    accent: Accent = Accent.Blue,
    inputMode: InputMode = InputMode.Pointer,
    fonts: DeskFonts = DeskFonts.Plex,
    colors: (DeskColors) -> DeskColors = { it },
    dimens: (DeskDimens) -> DeskDimens = { it },
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val palette = colors(remember(themeMode, systemDark, accent) { DeskPalette.colors(themeMode, systemDark, accent) })
    val type = remember(inputMode, fonts) { DeskType.forMode(inputMode, fonts) }
    val sizes = dimens(DeskDimens.forMode(inputMode))
    val scheme = remember(palette) { palette.toMaterialScheme() }
    val selection = remember(palette) {
        TextSelectionColors(handleColor = palette.accent, backgroundColor = palette.accent.copy(alpha = 0.35f))
    }
    CompositionLocalProvider(
        LocalDeskColors provides palette,
        LocalDeskType provides type,
        LocalDeskDimens provides sizes,
        LocalInputMode provides inputMode,
    ) {
        MaterialTheme(colorScheme = scheme, typography = type.toMaterialTypography()) {
            CompositionLocalProvider(
                LocalContentColor provides palette.text,
                LocalTextSelectionColors provides selection,
                content = content,
            )
        }
    }
}

private fun DeskColors.toMaterialScheme(): ColorScheme = if (isDark) darkColorScheme(
    primary = accent,
    onPrimary = onAccent,
    primaryContainer = selection,
    onPrimaryContainer = onSelection,
    secondary = brandSecondary,
    onSecondary = onAccent,
    background = background,
    onBackground = text,
    surface = surface,
    onSurface = text,
    surfaceVariant = surfaceRaised,
    onSurfaceVariant = textSecondary,
    surfaceContainer = surfaceRaised,
    surfaceContainerHigh = surfaceRaised,
    surfaceContainerHighest = hover,
    surfaceContainerLow = surface,
    surfaceContainerLowest = background,
    outline = borderStrong,
    outlineVariant = border,
    error = danger,
    scrim = scrim,
) else lightColorScheme(
    primary = accent,
    onPrimary = onAccent,
    primaryContainer = selection,
    onPrimaryContainer = onSelection,
    secondary = brandSecondary,
    onSecondary = onAccent,
    background = background,
    onBackground = text,
    surface = surface,
    onSurface = text,
    surfaceVariant = hover,
    onSurfaceVariant = textSecondary,
    surfaceContainer = surfaceRaised,
    surfaceContainerHigh = surfaceRaised,
    surfaceContainerHighest = hover,
    surfaceContainerLow = surface,
    surfaceContainerLowest = background,
    outline = borderStrong,
    outlineVariant = border,
    error = danger,
    scrim = scrim,
)

private fun DeskType.toMaterialTypography() = Typography(
    displaySmall = display,
    headlineSmall = headline,
    titleLarge = headline,
    titleMedium = title,
    titleSmall = bodyStrong,
    bodyLarge = body,
    bodyMedium = body,
    bodySmall = labelSmall,
    labelLarge = label,
    labelMedium = labelSmall,
    labelSmall = caption,
)
