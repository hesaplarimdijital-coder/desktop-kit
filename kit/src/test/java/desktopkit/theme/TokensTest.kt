package desktopkit.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
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
}
