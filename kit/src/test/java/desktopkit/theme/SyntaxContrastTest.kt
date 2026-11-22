package desktopkit.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every syntax role reaches 4.5:1 on the editor's background and on the current line's tint, in the
 * light, dark and OLED themes, with every preset accent and with custom accents chosen to be hard.
 */
class SyntaxContrastTest {

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

    @Test
    fun everyRoleReadsOnThePageAndOnTheCurrentLine() {
        val failures = mutableListOf<String>()
        for ((name, colors) in palettes()) {
            val syntax = DeskSyntax.from(colors)
            val texts = SyntaxRole.entries.map { it.name to syntax[it].color } + ("plain" to syntax.plain) + ("line number" to syntax.lineNumber)
            for ((role, color) in texts) {
                for ((surface, background) in listOf("background" to syntax.background, "current line" to syntax.currentLine)) {
                    val ratio = contrastRatio(color, background)
                    if (ratio < 4.5) failures += "%s %s on %s: %.2f:1".format(name, role, surface, ratio)
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** The marks drawn in the gutter and the bracket frame are graphics: 3:1 (WCAG 1.4.11). */
    @Test
    fun gutterMarksCanBeSeen() {
        val failures = mutableListOf<String>()
        for ((name, colors) in palettes()) {
            val syntax = DeskSyntax.from(colors)
            val marks = listOf("danger" to colors.danger, "warning" to colors.warning, "accent" to colors.accentText, "bracket" to colors.controlBorder)
            for ((mark, color) in marks) {
                for (background in listOf(syntax.background, syntax.currentLine)) {
                    val ratio = contrastRatio(color, background)
                    if (ratio < 3.0) failures += "%s %s: %.2f:1".format(name, mark, ratio)
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** The calm look: the presets' hues are the chosen ones, not ones the contrast fix had to move. */
    @Test
    fun thePresetThemesKeepTheChosenHues() {
        val dark = DeskSyntax.from(DeskPalette.dark(Accent.Blue))
        assertEquals(Color(0xFF8FD3A8), dark[SyntaxRole.String].color)
        assertEquals(Color(0xFFE2BB73), dark[SyntaxRole.Number].color)
        assertEquals(Color(0xFF7CCBD6), dark[SyntaxRole.Function].color)
        assertEquals(Color(0xFFC3A9F4), dark[SyntaxRole.Type].color)
        val light = DeskSyntax.from(DeskPalette.light(Accent.Blue))
        assertEquals(Color(0xFF1B6E47), light[SyntaxRole.String].color)
        assertEquals(Color(0xFF85560F), light[SyntaxRole.Number].color)
        assertEquals(Color(0xFF0B6672), light[SyntaxRole.Function].color)
        assertEquals(Color(0xFF6541B8), light[SyntaxRole.Type].color)
        val oled = DeskSyntax.from(DeskPalette.oled(Accent.Blue))
        assertEquals(Color.Black, oled.background)
        assertEquals(dark[SyntaxRole.String].color, oled[SyntaxRole.String].color)
    }

    @Test
    fun keywordsAndLinksTakeTheAccent() {
        for (accent in Accent.presets) {
            val colors = DeskPalette.dark(accent)
            val syntax = DeskSyntax.from(colors)
            assertEquals(colors.accentText, syntax[SyntaxRole.Keyword].color)
            assertEquals(colors.accentText, syntax[SyntaxRole.Link].color)
        }
    }

    @Test
    fun anAppChangesSingleRoles() {
        val base = DeskSyntax.from(DeskPalette.dark(Accent.Blue))
        val changed = base.with(SyntaxRole.Tag, SyntaxStyle(base.plain, italic = true))
        assertEquals(SyntaxStyle(base.plain, italic = true), changed[SyntaxRole.Tag])
        assertEquals(base[SyntaxRole.Keyword], changed[SyntaxRole.Keyword])
        assertNotEquals(base, changed)
        assertEquals(base, DeskSyntax.from(DeskPalette.dark(Accent.Blue)))
    }

    @Test
    fun theCodeStyleIsMonoAtTheBodySizeOnTallerLines() {
        for (type in listOf(DeskType.Pointer, DeskType.Touch, DeskType.pointer(step = 1f), DeskType.Touch.grow(1f))) {
            assertEquals(type.mono.fontFamily, type.code.fontFamily)
            assertEquals(type.body.fontSize, type.code.fontSize)
            assertEquals(type.body.fontSize.value * 1.5f, type.code.lineHeight.value, 0.01f)
        }
    }
}
