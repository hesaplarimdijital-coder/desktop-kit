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
import androidx.compose.ui.test.performClick
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

/**
 * The command palette (K4): Turkish-aware matching, ranking, and the keyboard.
 *
 * 2.5.0: a group that shows its best six ends with a line that counts its matches and shows them all. In 2.4.1 the
 * seventh match of a group was not shown, nothing said there was one, and without filter chips nothing led to it
 * (the commit before this one has that as a test).
 */
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

    private val bestHeading = "En iyi eşleşme"

    /** PaletteSearch.rows, the public ranking, is as it was: the best six of a group and no other kind of row. */
    @Test
    fun aGroupShowsSixOfItsMatches() {
        val albums = (1..10).map { item("a$it", "Gece $it", "Albümler") }
        val rows = PaletteSearch.rows(albums, "gece", bestHeading = bestHeading)
        // the best match, then six of the other nine: the two headings and seven results, and no other row
        assertEquals(listOf("a1", "a2", "a3", "a4", "a5", "a6", "a7"), rows.filterIsInstance<PaletteRow.Result>().map { it.item.id })
        assertEquals(2 + 7, rows.size)
        // a filter keeps one group whole: the two headings, the best match and the other nine
        assertEquals(2 + 1 + 9, PaletteSearch.rows(albums, "gece", filter = "Albümler", bestHeading = bestHeading).size)
    }

    private fun lines(items: List<PaletteItem>, query: String, filter: String? = null, expanded: Set<String> = emptySet()) =
        PaletteSearch.lines(items, query, filter, bestHeading, perGroup = 6, expanded = expanded)

    private fun results(lines: List<PaletteLine>) = lines.mapNotNull { ((it as? PaletteLine.Row)?.row as? PaletteRow.Result)?.item?.id }

    @Test
    fun aGroupThatShowsItsBestCountsItsMatchesAndShowsThemAll() {
        val albums = (1..10).map { item("a$it", "Gece $it", "Albümler") }
        val tracks = (1..3).map { item("t$it", "Gece şarkısı $it", "Parçalar") }
        val shown = lines(albums + tracks, "gece")
        // the best match apart, nine albums: six of them, then the line that counts the nine; three tracks: no line
        assertEquals(listOf(PaletteLine.More("Albümler", 9)), shown.filterIsInstance<PaletteLine.More>())
        assertEquals(listOf("a1", "a2", "a3", "a4", "a5", "a6", "a7", "t1", "t2", "t3"), results(shown))
        val line = shown.indexOf(PaletteLine.More("Albümler", 9))
        assertEquals("the line ends its group", PaletteLine.Row(PaletteRow.Heading("Parçalar")), shown[line + 1])
        assertEquals("a7", ((shown[line - 1] as PaletteLine.Row).row as PaletteRow.Result).item.id)
        // the group shown whole: its nine and no line; the others as they were
        val whole = lines(albums + tracks, "gece", expanded = setOf("Albümler"))
        assertTrue(whole.none { it is PaletteLine.More })
        assertEquals((1..10).map { "a$it" } + listOf("t1", "t2", "t3"), results(whole))
        // a filter keeps one group whole, as before; no line there
        assertEquals((1..10).map { "a$it" }, results(lines(albums + tracks, "gece", filter = "Albümler")))
        assertTrue(lines(albums + tracks, "gece", filter = "Albümler").none { it is PaletteLine.More })
        // nothing typed: no best match; ten albums, six shown, the line counts the ten
        assertEquals(listOf(PaletteLine.More("Albümler", 10)), lines(albums + tracks, "").filterIsInstance<PaletteLine.More>())
    }

    @Test
    fun theLineUnderAGroupShowsItWholeAndThePaletteStaysOpen() {
        val state = CommandPaletteState()
        var ran = ""
        val items = (1..10).map { n -> item("a$n", "Gece $n", "Albümler", onSelect = { ran = "a$n" }) }
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Pointer) {
                Box(Modifier.fillMaxSize()) { CommandPalette(state, items = { items }) }
            }
        }
        compose.runOnIdle { state.open() }
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performTextInput("gece")
        compose.waitForIdle()
        compose.onNodeWithText("9 sonucun hepsini göster").assertExists()
        compose.onNodeWithText("Gece 8").assertDoesNotExist()
        // the best match, six albums, the line: seven rows down from the first; Enter on the line
        compose.onNode(hasSetTextAction()).performKeyInput {
            repeat(7) { pressKey(Key.DirectionDown) }
            pressKey(Key.Enter)
        }
        compose.waitForIdle()
        assertTrue("the palette stays open", state.isOpen)
        assertEquals("nothing ran", "", ran)
        compose.onNodeWithText("9 sonucun hepsini göster").assertDoesNotExist()
        compose.onNodeWithText("Gece 8").assertExists()
        // the line's place is the group's next album now, and it is the chosen one: Enter runs it
        compose.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        assertEquals("a8", ran)
        assertFalse(state.isOpen)

        // opened again: the group shows its best again; a click on the line shows it whole
        compose.runOnIdle { state.open() }
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performTextInput("gece")
        compose.waitForIdle()
        compose.onNodeWithText("Gece 8").assertDoesNotExist()
        compose.onNodeWithText("9 sonucun hepsini göster").performClick()
        compose.waitForIdle()
        assertTrue(state.isOpen)
        compose.onNodeWithText("9 sonucun hepsini göster").assertDoesNotExist()
        // another query: its groups show their best again
        compose.onNode(hasSetTextAction()).performTextInput(" 1")
        compose.waitForIdle()
        compose.onNodeWithText("9 sonucun hepsini göster").assertDoesNotExist()
        compose.onNodeWithText("Gece 10").assertExists()
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
