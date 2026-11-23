package desktopkit.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every syntax role reaches 4.5:1 on the editor's background, on the current line's tint and on every tint
 * the editor lays under text, in the light, dark and OLED themes, with every preset accent and with custom
 * accents chosen to be hard.
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

    /**
     * The tints the editor lays under text: the search's matches (the current one too: it is told apart by a frame,
     * not by a stronger fill), ranges an app marks, marked and decorated lines (across the gutter, so the line
     * numbers too), and the selection, alone and laid over one another as the editor draws them. Every role, the
     * plain text and the line numbers reach 4.5:1 on each, in every theme with every accent. (Found by the review of
     * PR #19: the current match was a 55 % accent fill, and accent-coloured tokens on it read at 2.5:1.)
     */
    @Test
    fun everyRoleReadsOnEveryTintUnderIt() {
        val failures = mutableListOf<String>()
        for ((name, colors) in palettes()) {
            val syntax = DeskSyntax.from(colors)
            val texts = SyntaxRole.entries.map { it.name to syntax[it].color } + ("plain" to syntax.plain) + ("line number" to syntax.lineNumber)
            val tones = listOf("accent" to colors.accentText, "danger" to colors.danger, "warning" to colors.warning,
                "success" to colors.success, "neutral" to colors.textTertiary)
            fun Color.over(alpha: Float, under: Color) = copy(alpha = alpha).compositeOver(under)
            val surfaces = buildList {
                for ((pageName, page) in listOf("page" to syntax.background, "current line" to syntax.currentLine)) {
                    val unders = listOf(pageName to page) + tones.map { (tone, color) -> "$tone line on $pageName" to color.over(SyntaxTints.LINE, page) }
                    for ((underName, under) in unders) {
                        add(underName to under)
                        add("search match on $underName" to colors.accentText.over(SyntaxTints.MATCH, under))
                        for ((tone, color) in tones) add("$tone range on $underName" to color.over(SyntaxTints.RANGE, under))
                    }
                }
                add("selection" to colors.accent.over(SyntaxTints.SELECTION, syntax.background))
                for ((tone, color) in tones) {
                    add("selection on a $tone line" to colors.accent.over(SyntaxTints.SELECTION, color.over(SyntaxTints.LINE, syntax.background)))
                }
            }
            for ((role, color) in texts) {
                for ((surface, background) in surfaces) {
                    val ratio = contrastRatio(color, background)
                    if (ratio < 4.5) failures += "%s %s on %s: %.2f:1".format(name, role, surface, ratio)
                }
            }
        }
        assertTrue("${failures.size} failures:\n" + failures.take(40).joinToString("\n"), failures.isEmpty())
    }

    /** The current match's frame is a graphic: 3:1 against the match's tint and the page around it. */
    @Test
    fun theCurrentMatchsFrameCanBeSeen() {
        val failures = mutableListOf<String>()
        for ((name, colors) in palettes()) {
            val syntax = DeskSyntax.from(colors)
            for (page in listOf(syntax.background, syntax.currentLine)) {
                for (surface in listOf(page, colors.accentText.copy(alpha = SyntaxTints.MATCH).compositeOver(page))) {
                    val ratio = contrastRatio(colors.accentText, surface)
                    if (ratio < 3.0) failures += "%s: %.2f:1".format(name, ratio)
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
        assertEquals(Color(0xFF175E3D), light[SyntaxRole.String].color)
        assertEquals(Color(0xFF724A0D), light[SyntaxRole.Number].color)
        assertEquals(Color(0xFF0A5C67), light[SyntaxRole.Function].color)
        assertEquals(Color(0xFF603EAF), light[SyntaxRole.Type].color)
        // and with the other presets: chosen so that no accent's tints move them
        for (accent in Accent.presets) {
            val other = DeskSyntax.from(DeskPalette.light(accent))
            assertEquals(accent.name, light[SyntaxRole.String].color, other[SyntaxRole.String].color)
            assertEquals(accent.name, light[SyntaxRole.Type].color, other[SyntaxRole.Type].color)
        }
        val oled = DeskSyntax.from(DeskPalette.oled(Accent.Blue))
        assertEquals(Color.Black, oled.background)
        assertEquals(dark[SyntaxRole.String].color, oled[SyntaxRole.String].color)
    }

    /**
     * Keywords and links are the accent: the accent itself, or (where a tint under them needs it) the accent moved
     * a little toward white on a dark theme and toward black on the light one, never another hue.
     */
    @Test
    fun keywordsAndLinksTakeTheAccent() {
        for (accent in Accent.presets) {
            for (colors in listOf(DeskPalette.dark(accent), DeskPalette.light(accent))) {
                val syntax = DeskSyntax.from(colors)
                val keyword = syntax[SyntaxRole.Keyword].color
                assertEquals(keyword, syntax[SyntaxRole.Link].color)
                val toward = if (colors.isDark) Color.White else Color.Black
                val moved = (0..30).firstOrNull { step ->
                    val candidate = mixSrgb(toward, colors.accentText, step / 100f)
                    listOf(candidate.red - keyword.red, candidate.green - keyword.green, candidate.blue - keyword.blue).all { kotlin.math.abs(it) < 0.006f }
                }
                assertTrue("${accent.name} ${if (colors.isDark) "dark" else "light"}: $keyword is the accent ${colors.accentText}", moved != null)
            }
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
