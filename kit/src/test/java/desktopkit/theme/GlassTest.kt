package desktopkit.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Glass keeps text readable over the worst content (white under the dark themes, black under the
 * light one), and the device conditions pick the right mode.
 */
class GlassTest {

    private val accents = Accent.presets + listOf(Accent.custom("Yellow", Color(0xFFFFFF00)), Accent.custom("White", Color.White),
        Accent.custom("Black", Color.Black))

    private fun palettes() = listOf(ThemeMode.Light, ThemeMode.Dark, ThemeMode.Oled).flatMap { mode ->
        accents.map { accent -> "$mode/${accent.name}" to DeskPalette.colors(mode, systemDark = false, accent = accent) }
    }

    @Test
    fun textOnGlassOverTheWorstContent() {
        val failures = mutableListOf<String>()
        for ((name, c) in palettes()) {
            val worst = if (c.isDark) Color.White else Color.Black
            for ((base, material) in listOf("chrome" to DeskGlass::chrome, "raised" to DeskGlass::raised)) {
                for (thickness in listOf(GlassThickness.Thin, GlassThickness.Regular)) {
                    val surface = DeskGlass.composite(material(c, thickness), GlassMode.Blur, worst)
                    fun check(label: String, fg: Color, minimum: Double) {
                        val ratio = contrastRatio(fg, surface)
                        if (ratio < minimum) failures += "%s %s %s %s: %.2f:1".format(name, base, thickness, label, ratio)
                    }
                    check("text", c.text, 4.5)
                    check("secondary", c.textSecondary, 3.0)
                }
                // without blur the tint is nearly opaque: every text colour stays at AA
                val translucent = DeskGlass.composite(material(c, GlassThickness.Regular), GlassMode.Translucent, worst)
                for ((label, fg) in listOf("text" to c.text, "secondary" to c.textSecondary, "tertiary" to c.textTertiary)) {
                    val ratio = contrastRatio(fg, translucent)
                    if (ratio < 4.5) failures += "%s %s translucent %s: %.2f:1".format(name, base, label, ratio)
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun solidIsTheOpaqueSurface() {
        val c = DeskPalette.dark(Accent.Blue)
        assertEquals(c.chrome, DeskGlass.composite(DeskGlass.chrome(c), GlassMode.Solid, Color.White))
        assertEquals(c.surfaceRaised, DeskGlass.composite(DeskGlass.raised(c), GlassMode.Solid, Color.White))
    }

    @Test
    fun thickerGlassIsMoreOpaque() {
        for ((_, c) in palettes()) {
            val alphas = GlassThickness.entries.map { DeskGlass.chrome(c, it).tint.alpha }
            assertEquals(alphas.sorted(), alphas)
        }
    }

    @Test
    fun modeFollowsTheDevice() {
        assertEquals(GlassMode.Blur, DeskGlass.modeFor(sdk = 34, powerSave = false, reducedMotion = false, hardwareAccelerated = true))
        assertEquals(GlassMode.Translucent, DeskGlass.modeFor(sdk = 30, powerSave = false, reducedMotion = false, hardwareAccelerated = true))
        assertEquals(GlassMode.Translucent, DeskGlass.modeFor(sdk = 34, powerSave = false, reducedMotion = false, hardwareAccelerated = false))
        assertEquals(GlassMode.Solid, DeskGlass.modeFor(sdk = 34, powerSave = true, reducedMotion = false, hardwareAccelerated = true))
        assertEquals(GlassMode.Solid, DeskGlass.modeFor(sdk = 34, powerSave = false, reducedMotion = true, hardwareAccelerated = true))
        assertEquals(GlassMode.Solid, DeskGlass.modeFor(sdk = 26, powerSave = true, reducedMotion = false, hardwareAccelerated = true))
    }

    @Test
    fun paletteCrossFadeEndsAtTheTarget() {
        val blue = DeskPalette.dark(Accent.Blue)
        val coral = DeskPalette.dark(Accent.Coral)
        assertEquals(coral, lerpColors(blue, coral, 1f))
        assertEquals(blue, lerpColors(blue, coral, 0f))
        val half = lerpColors(blue, coral, 0.5f)
        assertTrue(half.accent != blue.accent && half.accent != coral.accent)
        // glass made from the half-way palette sits between the two tints as well
        val tint = DeskGlass.chrome(half).tint
        assertTrue(tint != DeskGlass.chrome(blue).tint && tint != DeskGlass.chrome(coral).tint)
    }
}
