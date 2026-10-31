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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.lerp

/**
 * The colour tokens. Dynamic, not static: an accent or theme change cross-fades over 150 ms, and a
 * static local recomposed everything under the theme on each of those frames (a static change
 * turns skipping off for the whole subtree); a dynamic one recomposes only what reads the colours.
 */
val LocalDeskColors = compositionLocalOf { DeskPalette.dark(Accent.Blue) }
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
 * - [colors] / [dimens]: transform the computed tokens, e.g. `{ it.copy(radiusCard = 4.dp) }`;
 * - [type]: builds the text styles for an input mode and the fonts, e.g. one sp larger with a
 *   pointer only: `{ mode, fonts -> DeskType.forMode(mode, fonts, pointerStep = 1f) }`.
 *
 * A new accent (one taken from the playing track's artwork, say) or theme cross-fades over
 * [colorChangeMillis] (150 ms); glass tints follow, since they are made from these colours. Only
 * what reads [DeskTheme.colors] recomposes on those frames; Material components inside take the new
 * colour scheme at once. With reduced motion, or 0, colours switch at once.
 */
@Composable
fun DesktopKitTheme(
    themeMode: ThemeMode = ThemeMode.System,
    accent: Accent = Accent.Blue,
    inputMode: InputMode = InputMode.Pointer,
    fonts: DeskFonts = DeskFonts.Plex,
    colors: (DeskColors) -> DeskColors = { it },
    dimens: (DeskDimens) -> DeskDimens = { it },
    type: (InputMode, DeskFonts) -> DeskType = { mode, fonts -> DeskType.forMode(mode, fonts) },
    colorChangeMillis: Int = DeskMotion.COLOR_CHANGE_MS,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val target = colors(remember(themeMode, systemDark, accent) { DeskPalette.colors(themeMode, systemDark, accent) })
    val reduced = rememberReducedMotion()
    val palette = animatePalette(target, if (reduced) 0 else colorChangeMillis)
    val styles = remember(inputMode, fonts, type) { type(inputMode, fonts) }
    val sizes = dimens(DeskDimens.forMode(inputMode))
    // Material's colour scheme (and the text selection colours) take the new palette at once: Material
    // provides its scheme through a static local, and a new scheme on each frame of the cross-fade
    // recomposed everything under the theme. The kit's own components fade with DeskTheme.colors.
    val scheme = remember(target) { target.toMaterialScheme() }
    val selection = remember(target) {
        TextSelectionColors(handleColor = target.accent, backgroundColor = target.accent.copy(alpha = 0.35f))
    }
    val typography = remember(styles) { styles.toMaterialTypography() }
    CompositionLocalProvider(
        LocalDeskColors provides palette,
        LocalDeskType provides styles,
        LocalDeskDimens provides sizes,
        LocalInputMode provides inputMode,
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography) {
            CompositionLocalProvider(
                LocalContentColor provides palette.text,
                LocalTextSelectionColors provides selection,
                content = content,
            )
        }
    }
}

/**
 * [target], cross-faded from the previous palette over [durationMillis] whenever it changes. The
 * first composition shows [target] at once.
 */
@Composable
internal fun animatePalette(target: DeskColors, durationMillis: Int): DeskColors {
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(target, durationMillis) {
        if (target == to) return@LaunchedEffect
        // start from what is on screen now, also when a change interrupts another
        from = lerpColors(from, to, progress.value)
        to = target
        if (durationMillis <= 0) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis, easing = DeskMotion.Standard))
        }
    }
    val t = progress.value
    return if (t >= 1f || from == to) to else lerpColors(from, to, t)
}

/** Every colour token of [a] moved [t] of the way to [b]; [DeskColors.isDark] is [b]'s. */
fun lerpColors(a: DeskColors, b: DeskColors, t: Float): DeskColors = if (t >= 1f) b else if (t <= 0f) a.copy(isDark = b.isDark) else DeskColors(
    isDark = b.isDark,
    background = lerp(a.background, b.background, t),
    chrome = lerp(a.chrome, b.chrome, t),
    surface = lerp(a.surface, b.surface, t),
    surfaceRaised = lerp(a.surfaceRaised, b.surfaceRaised, t),
    hover = lerp(a.hover, b.hover, t),
    pressed = lerp(a.pressed, b.pressed, t),
    border = lerp(a.border, b.border, t),
    borderStrong = lerp(a.borderStrong, b.borderStrong, t),
    text = lerp(a.text, b.text, t),
    textSecondary = lerp(a.textSecondary, b.textSecondary, t),
    textTertiary = lerp(a.textTertiary, b.textTertiary, t),
    accent = lerp(a.accent, b.accent, t),
    onAccent = lerp(a.onAccent, b.onAccent, t),
    accentText = lerp(a.accentText, b.accentText, t),
    selection = lerp(a.selection, b.selection, t),
    onSelection = lerp(a.onSelection, b.onSelection, t),
    focusRing = lerp(a.focusRing, b.focusRing, t),
    success = lerp(a.success, b.success, t),
    warning = lerp(a.warning, b.warning, t),
    danger = lerp(a.danger, b.danger, t),
    brandSecondary = lerp(a.brandSecondary, b.brandSecondary, t),
    scrim = lerp(a.scrim, b.scrim, t),
)

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

internal fun DeskType.toMaterialTypography() = Typography(
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
