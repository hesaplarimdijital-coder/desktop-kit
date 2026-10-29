package desktopkit.theme

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Appearance chosen by the user. [System] follows the device's dark mode. */
enum class ThemeMode { System, Light, Dark, Oled }

/**
 * Accent colour. Four tested presets ship with the kit; [Accent.custom] builds one from any
 * colour and adjusts it until every text pair keeps WCAG AA contrast (4.5:1).
 */
@Immutable
class Accent(
    val name: String,
    val base: Color,
    darkSelection: Color? = null,
    oledSelection: Color? = null,
    onDarkSelection: Color? = null,
) {
    /** Selected row / navigation background on the dark theme. */
    val darkSelection: Color = darkSelection ?: mixSrgb(base, Color(0xFF111418), 0.16f)
    val oledSelection: Color = oledSelection ?: mixSrgb(base, Color.Black, 0.14f)
    val onDarkSelection: Color = onDarkSelection ?: mixSrgb(base, Color.White, 0.3f)

    override fun equals(other: Any?): Boolean = other is Accent && other.name == name && other.base == base
    override fun hashCode(): Int = name.hashCode() * 31 + base.hashCode()
    override fun toString(): String = "Accent($name)"

    companion object {
        val Blue = Accent("Blue", Color(0xFF8AB0FF), Color(0xFF1C2A49), Color(0xFF142039), Color(0xFFDCE6FF))
        val Amber = Accent("Amber", Color(0xFFF2B84B), Color(0xFF33290F), Color(0xFF2C230F), Color(0xFFF8E3BD))
        val Green = Accent("Green", Color(0xFF59D49A), Color(0xFF12301F), Color(0xFF102B1F), Color(0xFFCFF3E2))
        val Coral = Accent("Coral", Color(0xFFFF7A6E), Color(0xFF3A1E1B), Color(0xFF2E1816), Color(0xFFFFD9D4))

        val presets: List<Accent> = listOf(Blue, Amber, Green, Coral)

        /** Any brand colour; it is lightened for the dark themes and darkened for the light one as needed. */
        fun custom(name: String, color: Color): Accent = Accent(name, color.copy(alpha = 1f))

        /** The name [dynamic] accents carry; stored in the preferences like any other accent. */
        const val DYNAMIC = "System"

        /**
         * The device's own accent colour, taken from the wallpaper (Android 12 and later): the system's
         * light accent tone, fitted to each theme by the same contrast rules as [custom]. It changes
         * with the wallpaper; [desktopkit.settings.rememberDeskPreferences] with `dynamicAccent = true`
         * offers it next to the presets and reads it again after a configuration change.
         */
        fun dynamic(context: Context): Accent = Accent(DYNAMIC, Color(context.getColor(android.R.color.system_accent1_200)))

        /** Looks up a stored accent name; unknown names fall back to [default]. */
        fun fromName(name: String?, default: Accent = Blue, extra: List<Accent> = emptyList()): Accent =
            (presets + extra).firstOrNull { it.name == name } ?: default
    }
}

/**
 * Colour tokens. Every text/background pair used by the components keeps a contrast of at
 * least 4.5:1 in the resting and hover states (checked by PaletteContrastTest).
 */
@Immutable
data class DeskColors(
    val isDark: Boolean,
    /** Window background behind the content. */
    val background: Color,
    /** Menu bar, navigation surfaces. */
    val chrome: Color,
    /** Cards, panels, list containers. */
    val surface: Color,
    /** Menus, tooltips, dialogs. */
    val surfaceRaised: Color,
    val hover: Color,
    val pressed: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    /** Accent used as a fill (selected indicator, primary button). */
    val accent: Color,
    val onAccent: Color,
    /** Accent used for text and icons on [background]. */
    val accentText: Color,
    /** Selected row / navigation item background. */
    val selection: Color,
    val onSelection: Color,
    val focusRing: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    /** Second brand colour (Material "secondary"). */
    val brandSecondary: Color,
    val scrim: Color,
)

object DeskPalette {

    fun colors(mode: ThemeMode, systemDark: Boolean, accent: Accent): DeskColors = when (mode) {
        ThemeMode.Light -> light(accent)
        ThemeMode.Dark -> dark(accent)
        ThemeMode.Oled -> oled(accent)
        ThemeMode.System -> if (systemDark) dark(accent) else light(accent)
    }

    private val DarkOnAccent = Color(0xFF0E1420)

    fun dark(accent: Accent): DeskColors {
        val hover = Color(0xFF262C34)
        val textTertiary = Color(0xFF959FAB)
        // grey text stays readable on a selected row, also with very light custom accents
        val selection = ensureContrast(accent.darkSelection, textTertiary, lighten = false)
        // readable as text on hover and as a fill under dark text
        val tone = ensureContrast(ensureContrast(accent.base, hover, lighten = true), DarkOnAccent, lighten = true)
        return DeskColors(
            isDark = true,
            background = Color(0xFF111418),
            chrome = Color(0xFF0C0F12),
            surface = Color(0xFF171B20),
            surfaceRaised = Color(0xFF1F252C),
            hover = hover,
            pressed = Color(0xFF2A3038),
            border = Color(0xFF20262D),
            borderStrong = Color(0xFF2A3038),
            text = Color(0xFFE6E9ED),
            textSecondary = Color(0xFFC3CAD3),
            textTertiary = textTertiary,
            accent = tone,
            onAccent = DarkOnAccent,
            accentText = tone,
            selection = selection,
            onSelection = ensureContrast(accent.onDarkSelection, selection, lighten = true),
            focusRing = tone,
            success = Color(0xFF59D49A),
            warning = Color(0xFFF2B84B),
            danger = Color(0xFFFF7A6E),
            brandSecondary = Color(0xFFFF7A6E),
            scrim = Color(0x99000000),
        )
    }

    fun oled(accent: Accent) = dark(accent).copy(
        background = Color(0xFF000000),
        chrome = Color(0xFF000000),
        surface = Color(0xFF0A0C0F),
        surfaceRaised = Color(0xFF161A1F),
        hover = Color(0xFF1C2127),
        pressed = Color(0xFF242A31),
        border = Color(0xFF1A1F25),
        borderStrong = Color(0xFF262C34),
        selection = ensureContrast(accent.oledSelection, Color(0xFF959FAB), lighten = false),
        scrim = Color(0xB3000000),
    )

    fun light(accent: Accent): DeskColors {
        val hover = Color(0xFFEDF0F4)
        // the deep tone carries white text and is read as text on hover
        val deep = ensureContrast(ensureContrast(mixSrgb(accent.base, Color.Black, 0.55f), Color.White, lighten = false), hover, lighten = false)
        val selection = mixSrgb(accent.base, Color.White, 0.22f)
        val textTertiary = Color(0xFF58626D)
        return DeskColors(
            isDark = false,
            background = Color(0xFFFAFBFC),
            chrome = Color(0xFFFFFFFF),
            surface = Color(0xFFFFFFFF),
            surfaceRaised = Color(0xFFFFFFFF),
            hover = hover,
            pressed = Color(0xFFE2E5EA),
            border = Color(0xFFE2E5EA),
            borderStrong = Color(0xFFD6DAE0),
            text = Color(0xFF12161B),
            textSecondary = Color(0xFF4A5360),
            textTertiary = textTertiary,
            accent = deep,
            onAccent = Color(0xFFFFFFFF),
            accentText = deep,
            // a very saturated custom accent can make the selection too dark for tertiary text
            selection = ensureContrast(selection, textTertiary, lighten = true),
            onSelection = Color(0xFF0E1420),
            focusRing = deep,
            success = Color(0xFF187A4F),
            warning = Color(0xFF8A5A12),
            danger = Color(0xFFB4382B),
            brandSecondary = Color(0xFFE0574B),
            scrim = Color(0x6612161B),
        )
    }
}

/** CSS `color-mix(in srgb, a fraction, b)`: mixes in gamma-encoded sRGB. */
fun mixSrgb(a: Color, b: Color, fraction: Float): Color = Color(
    red = a.red * fraction + b.red * (1 - fraction),
    green = a.green * fraction + b.green * (1 - fraction),
    blue = a.blue * fraction + b.blue * (1 - fraction),
    alpha = 1f,
)

/** WCAG contrast ratio of two opaque colours. */
fun contrastRatio(a: Color, b: Color): Double {
    val la = a.luminance().toDouble()
    val lb = b.luminance().toDouble()
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/**
 * Moves [color] toward white ([lighten]) or black until it reaches [minimum] contrast with
 * [against]. Colours that already pass are returned unchanged, so the presets keep their exact values.
 */
fun ensureContrast(color: Color, against: Color, lighten: Boolean, minimum: Double = 4.6): Color {
    if (contrastRatio(color, against) >= minimum) return color
    val target = if (lighten) Color.White else Color.Black
    var step = 0.05f
    while (step <= 1f) {
        val candidate = mixSrgb(target, color, step)
        if (contrastRatio(candidate, against) >= minimum) return candidate
        step += 0.05f
    }
    return target
}
