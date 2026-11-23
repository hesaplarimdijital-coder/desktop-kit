package desktopkit.editor

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Find and replace as the user meets it: the bar above the editor, its keys, the editor's keys, the marks. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w900dp-h640dp-mdpi")
class CodeSearchBarTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val document = CodeEditorState(sampleScript)
    private val search = CodeSearchState()

    private fun show() {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Pointer) {
                Column(Modifier.fillMaxSize()) {
                    CodeSearchBar(search, Modifier.fillMaxWidth())
                    CodeEditor(document, Modifier.weight(1f), tokenizer = sampleTokenizer, search = search)
                }
            }
        }
        compose.waitForIdle()
    }

    private val findField get() = compose.onAllNodes(hasSetTextAction())[0]
    private val replaceField get() = compose.onAllNodes(hasSetTextAction())[1]

    /** Waits for the search, which runs off the main thread, to find [count] matches. */
    private fun awaitMatches(count: Int) = await { search.matchCount == count }

    /**
     * Lets the main thread and the search thread go on until [condition]: Robolectric runs the main thread's
     * messages (the search's result coming back) only when the test lets it idle.
     */
    private fun await(condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        compose.waitForIdle()
        while (!condition()) {
            check(System.currentTimeMillis() < until) { "not so after 5 s" }
            Thread.sleep(10)
            compose.waitForIdle()
        }
    }

    private fun occurrences(text: String, of: String) = Regex(Regex.escape(of), RegexOption.IGNORE_CASE).findAll(text).map { it.range.first }.toList()

    @Test
    fun openingWithASelectionSeedsTheQueryAndMarksEveryMatch() {
        show()
        val first = sampleScript.indexOf("cay")
        compose.runOnUiThread {
            document.select(first, first + 3)
            search.open()
        }
        val all = occurrences(sampleScript, "cay")
        awaitMatches(all.size)
        assertEquals("cay", search.query)
        assertEquals("the selection is the first match", 0, search.currentMatch)
        compose.onNodeWithText("1 / ${all.size}").assertExists()
        findField.assertIsFocused()
        val view = compose.editorView()
        compose.runOnIdle { assertEquals(all.size, view.searchMatchCount) }
    }

    @Test
    fun typingFindsFromTheCursorAndTheKeysStep() {
        show()
        val cursor = document.lineStart(8)
        compose.runOnUiThread {
            document.select(cursor)
            search.open()
        }
        findField.performTextInput("bahce")
        val all = occurrences(sampleScript, "bahce")
        awaitMatches(all.size)
        val firstAfter = all.first { it >= cursor }
        assertEquals("the first match from the cursor is selected", TextRange(firstAfter, firstAfter + 5), document.selection)
        findField.assertIsFocused()
        val index = all.indexOf(firstAfter)
        findField.performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(all[(index + 1) % all.size], document.selection.min) }
        findField.performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Enter) } }
        compose.runOnIdle { assertEquals(firstAfter, document.selection.min) }
        findField.performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.F3) } }
        compose.runOnIdle { assertEquals(all[(index - 1 + all.size) % all.size], document.selection.min) }
        compose.onNodeWithText("${(index - 1 + all.size) % all.size + 1} / ${all.size}").assertExists()
    }

    @Test
    fun escapeClosesAndTheEditorHasTheFocusAtTheMatch() {
        show()
        compose.runOnUiThread { search.open() }
        findField.performTextInput("sahil")
        awaitMatches(occurrences(sampleScript, "sahil").size)
        val match = document.selection
        findField.performKeyInput { pressKey(Key.Escape) }
        compose.waitForIdle()
        assertFalse(search.isOpen)
        val view = compose.editorView()
        compose.runOnIdle {
            assertTrue("the editor has the focus", view.hasFocus())
            assertEquals(match, document.selection)
            assertEquals("no marks once closed", 0, view.searchMatchCount)
        }
    }

    @Test
    fun replaceStepsOnAndReplaceAllIsOneUndoStep() {
        show()
        compose.runOnUiThread {
            document.select(0)
            search.open(replace = true)
        }
        findField.performTextInput("cay")
        val count = occurrences(sampleScript, "cay").size
        awaitMatches(count)
        replaceField.performTextInput("çay")
        compose.onNodeWithText("Değiştir").performClick()
        compose.waitForIdle()
        val once = document.text
        assertEquals(sampleScript.replaceFirst("cay", "çay"), once)
        assertEquals("the next match is selected", "cay", document.selectedText)
        awaitMatches(count - 1)
        compose.onNodeWithText("Tümünü değiştir").performClick()
        compose.waitForIdle()
        assertEquals(once.replace("cay", "çay").replace("CAY", "çay").replace("Cay", "çay"), document.text)
        awaitMatches(0)
        compose.onNodeWithText("Sonuç yok").assertExists()
        compose.runOnIdle { assertTrue(document.undo()) }
        assertEquals("one step takes every replacement back", once, document.text)
        compose.runOnIdle { assertTrue(document.undo()) }
        assertEquals(sampleScript, document.text)
    }

    @Test
    fun replacingWaitsForTheSearchOfTheQueryAsItIsNow() {
        // the review: while a new expression was still being searched, the old matches kept Replace all enabled, and
        // replacing ran the new expression over the whole text on the main thread
        show()
        compose.runOnUiThread {
            search.open(replace = true)
            search.query = "cay"
            search.replacement = "çay"
        }
        awaitMatches(7)
        assertTrue(search.canReplace)
        compose.runOnUiThread {
            // a new query: until its search is done, the matches are the old query's
            search.query = "sahil"
            assertFalse("the search of the new query has not finished", search.canReplace)
            search.replaceAll()
            search.replaceCurrent()
        }
        assertEquals("nothing was replaced meanwhile", sampleScript, document.text)
        awaitMatches(2)
        assertTrue(search.canReplace)
        compose.onNodeWithText("Tümünü değiştir").assertIsEnabled()
        compose.runOnUiThread { search.replaceAll() }
        assertFalse(document.text.contains("sahil"))
    }

    @Test
    fun anInvalidExpressionSaysSo() {
        show()
        compose.runOnUiThread {
            search.regex = true
            search.open()
        }
        findField.performTextInput("(cay")
        await { search.invalidPattern }
        compose.onNodeWithText("Geçersiz ifade").assertExists()
        assertEquals(0, search.matchCount)
        findField.performTextReplacement("\\(cay")
        await { !search.invalidPattern }
        assertFalse(search.invalidPattern)
        // the option buttons say whether they are on
        compose.onNodeWithContentDescription("Düzenli ifade").assertExists()
    }

    @Test
    fun theEditorsKeysOpenStepAndClose() {
        show()
        val view = compose.editorView()
        val start = sampleScript.indexOf("sahil")
        compose.runOnUiThread {
            view.requestFocus()
            document.select(start, start + 5)
            view.press(KeyEvent.KEYCODE_F, KeyEvent.META_CTRL_ON)
        }
        compose.waitForIdle()
        assertTrue(search.isOpen)
        assertEquals("sahil", search.query)
        val all = occurrences(sampleScript, "sahil")
        awaitMatches(all.size)
        compose.runOnUiThread {
            view.requestFocus()
            view.press(KeyEvent.KEYCODE_F3)
        }
        compose.waitForIdle()
        assertEquals(all[(all.indexOf(start) + 1) % all.size], document.selection.min)
        compose.runOnUiThread { view.press(KeyEvent.KEYCODE_ESCAPE) }
        compose.waitForIdle()
        assertFalse("Esc in the editor closes the search first", search.isOpen)
        compose.runOnIdle { assertTrue(view.hasFocus()) }
    }

    @Test
    fun theMarksMoveWithTheTextAtOnce() {
        show()
        compose.runOnUiThread {
            document.select(0)
            search.open()
        }
        findField.performTextInput("sahil")
        val count = occurrences(sampleScript, "sahil").size
        awaitMatches(count)
        val before = search.matches.copyOf()
        val view = compose.editorView()
        compose.runOnUiThread {
            view.requestFocus()
            document.select(0)
            view.inputConnection().commitText("// ", 1)
            // before the search runs again, the marks already moved with the text
            assertEquals(before.map { it + 3 }, search.matches.toList())
        }
        awaitMatches(count)
        assertEquals(before.map { it + 3 }, search.matches.toList())
    }
}
