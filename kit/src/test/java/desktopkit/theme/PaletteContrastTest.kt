package desktopkit.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every text colour reaches 4.5:1 on every surface it is drawn on, in the light, dark and OLED
 * themes, with every preset accent and with custom accents chosen to be hard (WCAG 2.2 AA).
 */
class PaletteContrastTest {

    private val customAccents = listOf(
        Accent.custom("Yellow", Color(0xFFFFFF00)),
        Accent.custom("Navy", Color(0xFF001F5B)),
        Accent.custom("Red", Color(0xFFFF0000)),
        Accent.custom("Mint", Color(0xFFE6FFF4)),
        Accent.custom("Black", Color(0xFF000000)),
        Accent.custom("Purple", Color(0xFF7C4DFF)),
    )

    private fun palettes() = listOf(ThemeMode.Light, ThemeMode.Dark, ThemeMode.Oled).flatMap { mode ->
        (Accent.presets + customAccents).map { accent -> "$mode/${accent.name}" to DeskPalette.colors(mode, systemDark = false, accent = accent) }
    }

    private fun check(failures: MutableList<String>, name: String, label: String, fg: Color, bg: Color, minimum: Double = 4.5) {
        val ratio = contrastRatio(fg, bg)
        if (ratio < minimum) failures += "%s %s: %.2f:1".format(name, label, ratio)
    }

    @Test
    fun textOnSurfaces() {
        val failures = mutableListOf<String>()
        for ((name, c) in palettes()) {
            val surfaces = mapOf("background" to c.background, "chrome" to c.chrome, "surface" to c.surface,
                "raised" to c.surfaceRaised, "hover" to c.hover)
            val texts = mapOf("text" to c.text, "secondary" to c.textSecondary, "tertiary" to c.textTertiary)
            for ((t, fg) in texts) for ((s, bg) in surfaces) check(failures, name, "$t on $s", fg, bg)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun accentAndSelection() {
        val failures = mutableListOf<String>()
        for ((name, c) in palettes()) {
            check(failures, name, "accent text on background", c.accentText, c.background)
            check(failures, name, "accent text on hover", c.accentText, c.hover)
            check(failures, name, "on accent", c.onAccent, c.accent)
            check(failures, name, "on selection", c.onSelection, c.selection)
            check(failures, name, "tertiary on selection", c.textTertiary, c.selection)
            check(failures, name, "danger on raised", c.danger, c.surfaceRaised)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun presetsKeepTheirExactColours() {
        for (accent in Accent.presets) {
            assertEquals(accent.base, DeskPalette.dark(accent).accent)
            assertEquals(accent.darkSelection, DeskPalette.dark(accent).selection)
            assertEquals(accent.onDarkSelection, DeskPalette.dark(accent).onSelection)
        }
    }

    @Test
    fun systemModeFollowsTheSystem() {
        assertTrue(DeskPalette.colors(ThemeMode.System, systemDark = true, accent = Accent.Blue).isDark)
        assertTrue(!DeskPalette.colors(ThemeMode.System, systemDark = false, accent = Accent.Blue).isDark)
        assertEquals(Color.Black, DeskPalette.colors(ThemeMode.Oled, systemDark = false, accent = Accent.Blue).background)
    }

    @Test
    fun accentNamesRoundTrip() {
        assertEquals(Accent.Green, Accent.fromName("Green"))
        assertEquals(Accent.Blue, Accent.fromName("Unknown"))
        val brand = Accent.custom("Brand", Color(0xFF00897B))
        assertEquals(brand, Accent.fromName("Brand", extra = listOf(brand)))
    }
}
