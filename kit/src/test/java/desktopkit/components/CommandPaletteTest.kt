package desktopkit.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollToNode
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
 * (the commit before this one has that as a test). A pointer resting over the list leaves the choice to the arrows.
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
        // none of a group: no heading for it either
        assertEquals(listOf(PaletteRow.Heading(bestHeading)), PaletteSearch.rows(albums, "gece", bestHeading = bestHeading, perGroup = 0).filterIsInstance<PaletteRow.Heading>())
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
        // two groups over six: the albums' nine besides the best match, the tracks' ten
        val items = (1..10).map { n -> item("a$n", "Gece $n", "Albümler", onSelect = { ran = "a$n" }) } +
            (1..10).map { n -> item("t$n", "Gece şarkısı $n", "Parçalar", onSelect = { ran = "t$n" }) }
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Pointer) {
                Box(Modifier.fillMaxSize()) { CommandPalette(state, items = { items }) }
            }
        }
        val list = compose.onNode(hasScrollToIndexAction())
        compose.runOnIdle { state.open() }
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performTextInput("gece")
        compose.waitForIdle()
        compose.onNodeWithText("9 sonucun hepsini göster").assertExists()
        compose.onNodeWithText("Gece 8").assertDoesNotExist()
        // the best match, six albums, the line: seven rows down from the first; Enter on the line, held for a second (it
        // repeats after half of it): the palette stays open and nothing runs
        compose.onNode(hasSetTextAction()).performKeyInput {
            repeat(7) { pressKey(Key.DirectionDown) }
            pressKey(Key.Enter, 1_000L)
        }
        compose.waitForIdle()
        assertTrue("the palette stays open", state.isOpen)
        assertEquals("nothing ran", "", ran)
        compose.onNodeWithText("9 sonucun hepsini göster").assertDoesNotExist()
        compose.onNodeWithText("Gece 8").assertExists()
        // only the group whose line it was: the tracks still show their best, and their line
        list.performScrollToNode(hasText("10 sonucun hepsini göster"))
        compose.onNodeWithText("Gece şarkısı 7").assertDoesNotExist()
        // the line's place is the group's next album now, and it is the chosen one: Enter runs it
        compose.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        assertEquals("a8", ran)
        assertFalse(state.isOpen)

        // opened again, "gec" typed: the group shows its best again; a click on the line shows it whole
        compose.runOnIdle { state.open() }
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performTextInput("gec")
        compose.waitForIdle()
        compose.onNodeWithText("Gece 8").assertDoesNotExist()
        compose.onNodeWithText("9 sonucun hepsini göster").performClick()
        compose.waitForIdle()
        assertTrue(state.isOpen)
        compose.onNodeWithText("9 sonucun hepsini göster").assertDoesNotExist()
        compose.onNodeWithText("Gece 8").assertExists()
        // another query ("gece"): the group shows its best again, with its line
        compose.onNode(hasSetTextAction()).performTextInput("e")
        compose.waitForIdle()
        compose.onNodeWithText("9 sonucun hepsini göster").assertExists()
        compose.onNodeWithText("Gece 8").assertDoesNotExist()
    }

    /**
     * A tap on the line shows the group whole where the line was: the result that takes its place is in sight. (The line
     * was not chosen by the tap, so the list went back to the result chosen before it, away from what the tap showed.)
     */
    @Test
    fun aTappedLineShowsTheRestWhereItWas() {
        val state = CommandPaletteState()
        val items = (1..10).map { n -> item("a$n", "Gece $n", "Albümler") } + (1..10).map { n -> item("t$n", "Gece şarkısı $n", "Parçalar") }
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Touch) {
                Box(Modifier.fillMaxSize()) { CommandPalette(state, items = { items }) }
            }
        }
        compose.runOnIdle { state.open() }
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performTextInput("gece")
        compose.waitForIdle()
        // the tracks' line is out of sight: the list goes down to it, away from the best match, the chosen one
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("10 sonucun hepsini göster"))
        compose.onNodeWithText("Gece 1").assertDoesNotExist()
        compose.onNodeWithText("10 sonucun hepsini göster").performClick()
        compose.waitForIdle()
        assertTrue(state.isOpen)
        compose.onNodeWithText("10 sonucun hepsini göster").assertDoesNotExist()
        // the seventh track has the line's place, in sight
        compose.onNodeWithText("Gece şarkısı 7").assertIsDisplayed()
    }

    /**
     * 2.5.0: a pointer resting over the list leaves the choice to the arrows while they scroll the list under it (each
     * row that came under it took the choice back, so the arrows never got past the list's edge); a move of its own and
     * the pointer chooses again, and the list stays where it is.
     */
    @Test
    fun aRestingPointerLeavesTheChoiceToTheArrows() {
        val state = CommandPaletteState()
        // five groups of six: thirty rows and no line, nothing typed
        val items = (1..30).map { n -> item("s$n", "Satır %02d".format(n), "Grup ${(n + 5) / 6}") }
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Pointer) {
                Box(Modifier.fillMaxSize()) { CommandPalette(state, items = { items }) }
            }
        }
        compose.runOnIdle { state.open() }
        compose.waitForIdle()
        val field = compose.onNode(hasSetTextAction())
        val origin = field.fetchSemanticsNode().boundsInRoot.topLeft
        fun chosen(): String = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)).fetchSemanticsNodes()
            .single().config[SemanticsProperties.Text].first().text
        // the pointer comes to the third row and rests there: it chose the row
        val rest = compose.onNodeWithText("Satır 03").fetchSemanticsNode().boundsInRoot.center - origin
        field.performMouseInput {
            moveTo(rest - Offset(24f, 0f))
            moveTo(rest)
        }
        compose.waitForIdle()
        assertEquals("Satır 03", chosen())
        // twenty rows down, the list moving under the pointer
        repeat(20) {
            field.performKeyInput { pressKey(Key.DirectionDown) }
            compose.waitForIdle()
        }
        assertEquals("Satır 23", chosen())
        // the pointer moves to the next row: it is chosen, and the list stays where it is
        val next = compose.onNodeWithText("Satır 24").fetchSemanticsNode().boundsInRoot
        field.performMouseInput { moveTo(next.center - origin) }
        compose.waitForIdle()
        assertEquals("Satır 24", chosen())
        assertEquals(next, compose.onNodeWithText("Satır 24").fetchSemanticsNode().boundsInRoot)
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
