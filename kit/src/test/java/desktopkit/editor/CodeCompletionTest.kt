package desktopkit.editor

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.components.DeskIcons
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Completion as the user meets it: the list while typing a word, its keys, a tap, Ctrl+Space, the switch. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w900dp-h640dp-mdpi")
class CodeCompletionTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val document = CodeEditorState("== bahce ==\nSabah.\n")
    private var live by mutableStateOf(true)
    private var input by mutableStateOf(InputMode.Pointer)
    private var asked = 0

    /** The sample language's names: the sections and the words of the language, for the word at the cursor. */
    private val completer = CodeCompleter { state, cursor ->
        asked++
        var start = cursor
        while (start > 0 && CompletionSession.isWordChar(state.charAt(start - 1))) start--
        var end = cursor
        while (end < state.length && CompletionSession.isWordChar(state.charAt(end))) end++
        CodeCompletions(start, end, listOf(
            CodeCompletionItem("bahce", "bölüm", DeskIcons.Folder),
            CodeCompletionItem("cay_soyle", "bölüm", DeskIcons.Folder),
            CodeCompletionItem("sahil", "bölüm", DeskIcons.Folder),
            CodeCompletionItem("CONST", "sözcük"),
            CodeCompletionItem("END", "sözcük"),
        ))
    }

    private fun show() {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(inputMode = input) {
                CodeEditor(document, Modifier.fillMaxSize(), tokenizer = sampleTokenizer, completer = completer, liveCompletion = live)
            }
        }
        compose.waitForIdle()
    }

    private fun typeAtEndOfLine(line: Int, vararg pieces: String) {
        val view = compose.editorView()
        compose.runOnUiThread {
            view.requestFocus()
            document.select(document.lineEnd(line))
            for (piece in pieces) view.inputConnection().commitText(piece, 1)
        }
        compose.waitForIdle()
    }

    private fun shown(label: String, detail: String) = compose.onNodeWithContentDescription("$label, $detail")

    private fun rows() = compose.onAllNodes(hasContentDescription(", bölüm", substring = true) or hasContentDescription(", sözcük", substring = true))

    @Test
    fun typingAWordOpensTheListAndTheLettersNarrowIt() {
        show()
        typeAtEndOfLine(1, " -> ", "s")
        // "s" starts "sahil", starts a word in "cay_soyle", is in "CONST"
        rows().assertCountEquals(3)
        shown("sahil", "bölüm").assertExists()
        typeAtEndOfLine(1, "a")
        rows().assertCountEquals(1)
        shown("sahil", "bölüm").assertExists()
        assertEquals("the completer is asked once for the word", 1, asked)
        // a character that ends the word closes it
        typeAtEndOfLine(1, " ")
        rows().assertCountEquals(0)
        // a word nothing fits: asked once, not again for every letter
        typeAtEndOfLine(1, "x", "y", "z")
        rows().assertCountEquals(0)
        assertEquals(2, asked)
        // its letters deleted, the place is a new word
        val view = compose.editorView()
        compose.runOnUiThread { repeat(3) { view.press(KeyEvent.KEYCODE_DEL) } }
        typeAtEndOfLine(1, "b")
        shown("bahce", "bölüm").assertExists()
    }

    @Test
    fun enterChoosesTheHighlightedItemAsOneUndoStep() {
        show()
        typeAtEndOfLine(1, " -> ", "ca")
        val view = compose.editorView()
        compose.runOnUiThread { view.press(KeyEvent.KEYCODE_ENTER) }
        compose.waitForIdle()
        assertEquals("Sabah. -> cay_soyle", document.lineText(1))
        assertEquals("the cursor after the name", document.lineEnd(1), document.cursor)
        rows().assertCountEquals(0)
        compose.runOnIdle { document.undo() }
        assertEquals("Sabah. -> ca", document.lineText(1))
    }

    @Test
    fun theArrowsMoveAndTabChooses() {
        show()
        typeAtEndOfLine(1, " ", "e")
        // "e" begins "END", and is in "bahce", "cay_soyle"
        val view = compose.editorView()
        compose.runOnUiThread {
            view.press(KeyEvent.KEYCODE_DPAD_DOWN)
            view.press(KeyEvent.KEYCODE_TAB)
        }
        compose.waitForIdle()
        assertTrue("the second item went in: ${document.lineText(1)}", document.lineText(1) in setOf("Sabah. bahce", "Sabah. cay_soyle"))
        assertEquals("Tab chose instead of indenting", document.lineText(1).trimStart(), document.lineText(1))
    }

    @Test
    fun escapeClosesTheListForTheRestOfTheWord() {
        show()
        typeAtEndOfLine(1, " ", "s")
        rows().assertCountEquals(3)
        val view = compose.editorView()
        compose.runOnUiThread { view.press(KeyEvent.KEYCODE_ESCAPE) }
        compose.waitForIdle()
        rows().assertCountEquals(0)
        assertEquals("Esc typed nothing and moved nothing", "Sabah. s", document.lineText(1))
        typeAtEndOfLine(1, "a")
        rows().assertCountEquals(0)
        // the next word opens it again
        typeAtEndOfLine(1, " ", "b")
        shown("bahce", "bölüm").assertExists()
    }

    @Test
    fun aTapChoosesOnTouch() {
        input = InputMode.Touch
        show()
        typeAtEndOfLine(1, " ", "sa")
        shown("sahil", "bölüm").performClick()
        compose.waitForIdle()
        assertEquals("Sabah. sahil", document.lineText(1))
    }

    @Test
    fun withoutLiveCompletionOnlyCtrlSpaceOrTheAppOpensIt() {
        live = false
        show()
        typeAtEndOfLine(1, " ", "s")
        rows().assertCountEquals(0)
        assertEquals(0, asked)
        val view = compose.editorView()
        compose.runOnUiThread { view.press(KeyEvent.KEYCODE_SPACE, KeyEvent.META_CTRL_ON) }
        compose.waitForIdle()
        rows().assertCountEquals(3)
        assertEquals("Ctrl+Space typed nothing", "Sabah. s", document.lineText(1))
        compose.runOnUiThread { view.press(KeyEvent.KEYCODE_ESCAPE) }
        compose.waitForIdle()
        rows().assertCountEquals(0)
        // the app's own menu entry or key; at an empty word everything is offered
        typeAtEndOfLine(1, " ")
        compose.runOnUiThread { document.showCompletion() }
        compose.waitForIdle()
        rows().assertCountEquals(5)
    }

    @Test
    fun theWordAfterTheCursorIsReplacedToo() {
        show()
        val view = compose.editorView()
        compose.runOnUiThread {
            view.requestFocus()
            // the cursor inside "bahce" of the first line: "ba|hce"
            document.select(5)
            view.press(KeyEvent.KEYCODE_SPACE, KeyEvent.META_CTRL_ON)
        }
        compose.waitForIdle()
        shown("bahce", "bölüm").assertExists()
        compose.runOnUiThread { view.press(KeyEvent.KEYCODE_DPAD_DOWN) }
        compose.waitForIdle()
        compose.runOnUiThread { view.press(KeyEvent.KEYCODE_ENTER) }
        compose.waitForIdle()
        assertTrue("one name in the word's place: ${document.lineText(0)}", document.lineText(0).matches(Regex("== \\w+ ==")))
    }
}
