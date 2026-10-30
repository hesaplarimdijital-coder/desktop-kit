package desktopkit.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The command palette (K4): Turkish-aware matching, ranking, and the keyboard. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class CommandPaletteTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun turkishLettersFold() {
        assertEquals("indirilenler", PaletteMatcher.fold("İndirilenler"))
        assertEquals("isik gunes cag", PaletteMatcher.fold("Işık Güneş Çağ"))
        assertEquals("Fold keeps the length", "Kâtibim".length, PaletteMatcher.fold("Kâtibim").length)
        assertNotNull(PaletteMatcher.match("indir", "İndirilenler"))
        assertNotNull(PaletteMatcher.match("katibim", "Kâtibim"))
        assertNotNull(PaletteMatcher.match("ISIK", "ışık"))
    }

    @Test
    fun startsBeatWordsBeatInsidesBeatLetters() {
        val start = PaletteMatcher.match("gece", "Gece Yarısı Treni")!!
        val word = PaletteMatcher.match("gece", "Bu gece burada")!!
        val inside = PaletteMatcher.match("ece", "Bu gece burada")!!
        val letters = PaletteMatcher.match("gyt", "Gece Yarısı Treni")!!
        assertTrue(start.score > word.score)
        assertTrue(word.score > inside.score)
        assertTrue(inside.score > letters.score)
        assertEquals(listOf(0..3), start.ranges)
        assertEquals(listOf(3..6), word.ranges)
        // the letters land on the word starts: G, Y, T
        assertEquals(listOf(0..0, 5..5, 12..12), letters.ranges)
        assertNull(PaletteMatcher.match("xyz", "Gece Yarısı Treni"))
    }

    private fun item(id: String, title: String, group: String, onSelect: () -> Unit = {}, newWindow: (() -> Unit)? = null, keywords: List<String> = emptyList()) =
        PaletteItem(id, title, group, onSelect, keywords = keywords, onOpenInNewWindow = newWindow)

    @Test
    fun bestMatchFirstThenGroups() {
        val items = listOf(
            item("1", "Bu gece", "Parçalar"),
            item("2", "Gece Yarısı Treni", "Albümler"),
            item("3", "Kenar çubuğunu göster", "Görünüm", keywords = listOf("gece modu")),
        )
        val rows = PaletteSearch.rows(items, "gece", bestHeading = "En iyi eşleşme")
        assertEquals(PaletteRow.Heading("En iyi eşleşme"), rows[0])
        assertEquals("2", (rows[1] as PaletteRow.Result).item.id)
        val titles = rows.filterIsInstance<PaletteRow.Heading>().map { it.title }
        assertEquals(listOf("En iyi eşleşme", "Parçalar", "Görünüm"), titles)
        // a filter keeps one group, without the best-match heading's duplicate
        val albums = PaletteSearch.rows(items, "gece", filter = "Albümler", bestHeading = "En iyi eşleşme")
        assertEquals(2, albums.size)
        assertTrue(PaletteSearch.rows(items, "").none { it is PaletteRow.Heading && it.title == "En iyi eşleşme" })
    }

    @Test
    fun keyboardChoosesAndRuns() {
        val state = CommandPaletteState()
        var ran = ""
        val items = listOf(
            item("albums", "Gece Yarısı Treni", "Albümler", onSelect = { ran = "albums" }, newWindow = { ran = "window" }),
            item("sidebar", "Kenar çubuğunu göster", "Görünüm", onSelect = { ran = "sidebar" }),
        )
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Pointer) {
                Box(Modifier.fillMaxSize()) { CommandPalette(state, items = { items }) }
            }
        }
        compose.runOnIdle { state.open() }
        compose.waitForIdle()
        compose.onNodeWithText("Kenar çubuğunu göster").assertExists()
        // no query: the first row is chosen; Down moves to the second; Enter runs it and closes
        compose.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.DirectionDown); pressKey(Key.Enter) }
        compose.waitForIdle()
        assertEquals("sidebar", ran)
        assertFalse(state.isOpen)

        compose.runOnIdle { state.open() }
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performTextInput("gyt")
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Enter) } }
        compose.waitForIdle()
        assertEquals("Ctrl+Enter opens in a new window", "window", ran)

        compose.runOnIdle { state.open() }
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Escape) }
        compose.waitForIdle()
        assertFalse(state.isOpen)
    }
}
