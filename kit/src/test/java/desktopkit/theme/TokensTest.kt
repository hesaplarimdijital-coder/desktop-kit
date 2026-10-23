package desktopkit.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Text is at least 11 sp; targets are at least 24 dp with a pointer and 48 dp on touch. */
class TokensTest {

    private fun DeskType.styles(): List<Pair<String, TextStyle>> = listOf(
        "display" to display, "headline" to headline, "title" to title, "body" to body, "bodyStrong" to bodyStrong,
        "label" to label, "labelSmall" to labelSmall, "caption" to caption, "mono" to mono,
    )

    @Test
    fun textIsAtLeast11sp() {
        for ((density, type) in listOf("pointer" to DeskType.Pointer, "touch" to DeskType.Touch)) {
            for ((name, style) in type.styles()) {
                assertTrue("$density $name is ${style.fontSize}", style.fontSize.value >= 11f)
            }
        }
    }

    @Test
    fun targetsMeetTheirMinimum() {
        assertTrue(DeskDimens.Pointer.minTarget >= 24.dp)
        assertTrue(DeskDimens.Touch.minTarget >= 48.dp)
        assertTrue(DeskDimens.Touch.navItemHeight >= 48.dp)
        assertTrue(DeskDimens.Pointer.rowHeight >= DeskDimens.Pointer.minTarget)
    }

    @Test
    fun touchIsLargerThanPointer() {
        assertTrue(DeskType.Touch.body.fontSize.value > DeskType.Pointer.body.fontSize.value)
        assertTrue(DeskDimens.Touch.rowHeight > DeskDimens.Pointer.rowHeight)
    }

    @Test
    fun customFontsKeepTheSizes() {
        val system = DeskType.forMode(InputMode.Pointer, DeskFonts.System)
        assertTrue(system.body.fontSize == DeskType.Pointer.body.fontSize)
        assertTrue(system.body.fontFamily == DeskFonts.System.sans)
    }

    @Test
    fun headingsUseTheHeadingFamily() {
        val plex = DeskType.pointer(DeskFonts.Plex)
        assertTrue(plex.title.fontFamily == DeskFonts.Plex.sans) // unchanged unless asked
        val serif = DeskFonts.Plex.copy(heading = androidx.compose.ui.text.font.FontFamily.Serif)
        for (type in listOf(DeskType.pointer(serif), DeskType.touch(serif))) {
            assertTrue(type.display.fontFamily == androidx.compose.ui.text.font.FontFamily.Serif)
            assertTrue(type.headline.fontFamily == androidx.compose.ui.text.font.FontFamily.Serif)
            assertTrue(type.title.fontFamily == androidx.compose.ui.text.font.FontFamily.Serif)
            assertTrue(type.body.fontFamily == DeskFonts.Plex.sans)
            assertTrue(type.label.fontFamily == DeskFonts.Plex.sans)
            assertTrue(type.mono.fontFamily == DeskFonts.Plex.mono)
        }
    }

    @Test
    fun stepEnlargesEveryPointerStyleOnly() {
        val base = DeskType.forMode(InputMode.Pointer)
        val larger = DeskType.forMode(InputMode.Pointer, pointerStep = 1f)
        for ((pair, bigger) in base.styles().zip(larger.styles())) {
            val (name, style) = pair
            val grown = bigger.second
            assertEquals("pointer $name size", style.fontSize.value + 1f, grown.fontSize.value, 0.001f)
            // the line height keeps its ratio to the size
            assertEquals("pointer $name line height", style.lineHeight.value / style.fontSize.value,
                grown.lineHeight.value / grown.fontSize.value, 0.001f)
            assertTrue("pointer $name line height grows", grown.lineHeight.value > style.lineHeight.value)
        }
        assertEquals(DeskType.Touch, DeskType.forMode(InputMode.Touch, pointerStep = 1f))
        assertEquals(DeskType.touch(step = 2f), DeskType.forMode(InputMode.Touch, touchStep = 2f))
        assertEquals(DeskType.Pointer, DeskType.pointer(step = 0f))
    }

    @Test
    fun growAddsToEveryStyleAndKeepsLineHeightRatio() {
        val base = DeskType.Touch
        val grown = base.grow(1f)
        for ((pair, bigger) in base.styles().zip(grown.styles())) {
            val (name, style) = pair
            assertEquals("$name size", style.fontSize.value + 1f, bigger.second.fontSize.value, 0.001f)
            assertEquals("$name ratio", style.lineHeight.value / style.fontSize.value,
                bigger.second.lineHeight.value / bigger.second.fontSize.value, 0.001f)
        }
        assertEquals(base, base.grow(0f))
        // the same as building the set one step larger
        assertEquals(DeskType.touch(step = 1f).body.fontSize, grown.body.fontSize)
    }

    @Test
    fun compactWindowSizesKeepTouchTargets() {
        val compact = DeskDimens.Touch.forCompactWindow()
        assertEquals(64.dp, compact.bottomBarHeight)
        assertEquals(52.dp, compact.rowHeight)
        assertTrue(compact.rowHeight >= compact.minTarget)
        assertTrue(compact.minTarget >= 48.dp)
        assertEquals(DeskDimens.Pointer, DeskDimens.Pointer.forCompactWindow())
    }
}
