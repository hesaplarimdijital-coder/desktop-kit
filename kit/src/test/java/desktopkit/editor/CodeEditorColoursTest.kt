package desktopkit.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.text.style.CharacterStyle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The colours stay with the text when a replacement covers coloured tokens and the first line it touches
 * tokenizes to the same tokens as before: the platform's text drops the spans inside a replaced range, so
 * the editor must set them again even though the tokens did not change. (Found by the review of PR #19.)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w800dp-h600dp-mdpi")
class CodeEditorColoursTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var document by mutableStateOf(CodeEditorState("VAR x = 1\nVAR y = 2\nEND"))
    private val search = CodeSearchState()

    private fun show() {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                CodeEditor(document, Modifier.fillMaxSize(), tokenizer = sampleTokenizer, search = search)
            }
        }
        compose.waitForIdle()
    }

    private fun onUi(block: (CodeEditorView) -> Unit) {
        val view = compose.editorView()
        compose.runOnUiThread { block(view) }
        compose.waitForIdle()
    }

    /** The colour spans on [line] of the view's text, against the tokens the document has for it. */
    private fun assertColoursMatchTokens(view: CodeEditorView, line: Int) {
        val start = document.lineStart(line)
        val text = view.editableText
        val tokens = document.tokens.tokensOf(line).toList().chunked(3).map { it[0] to it[1] }
        val spans = text.getSpans(start, document.lineEnd(line), CharacterStyle::class.java)
            .filter { it.javaClass.name.contains("RoleSpan") }
            .map { text.getSpanStart(it) - start to text.getSpanEnd(it) - start }.sortedBy { it.first }
        assertEquals("colour spans of line $line (\"${document.lineText(line)}\") against its tokens", tokens, spans)
    }

    private fun assertEveryLineColoured(view: CodeEditorView) {
        for (line in 0 until document.lineCount) assertColoursMatchTokens(view, line)
    }

    @Test
    fun aLineMovedUpPastALineOfTheSameShapeKeepsItsColours() {
        show()
        onUi { view ->
            assertEveryLineColoured(view)
            view.requestFocus()
            document.select(document.lineStart(1))
            document.moveLinesUp()
        }
        assertEquals("VAR y = 2\nVAR x = 1\nEND", document.text)
        onUi { view -> assertEveryLineColoured(view) }
    }

    @Test
    fun undoingAndRedoingAMoveKeepsTheColours() {
        show()
        onUi { view ->
            view.requestFocus()
            document.select(document.lineStart(1))
            document.moveLinesUp()
            document.undo()
        }
        assertEquals("VAR x = 1\nVAR y = 2\nEND", document.text)
        onUi { view ->
            assertEveryLineColoured(view)
            document.redo()
        }
        assertEquals("VAR y = 2\nVAR x = 1\nEND", document.text)
        onUi { view -> assertEveryLineColoured(view) }
    }

    @Test
    fun replaceAllKeepsTheColoursOfTheLineItStartsIn() {
        document = CodeEditorState("VAR x = 1\nVAR x = 2\nEND")
        show()
        onUi { view ->
            view.requestFocus()
            search.query = "x"
            search.replacement = "z"
            search.replaceAll()
        }
        assertEquals("VAR z = 1\nVAR z = 2\nEND", document.text)
        onUi { view ->
            assertEveryLineColoured(view)
            // and its undo
            document.undo()
        }
        assertEquals("VAR x = 1\nVAR x = 2\nEND", document.text)
        onUi { view -> assertEveryLineColoured(view) }
    }

    @Test
    fun pastingOverASelectionKeepsTheColours() {
        document = CodeEditorState("VAR x = 1 + 2\nEND")
        show()
        onUi { view ->
            view.requestFocus()
            // "x = 1" becomes "y = 3": the tokens of the line are the same before and after
            val clipboard = view.context.getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText("kod", "y = 3"))
            document.select(4, 9)
            view.onTextContextMenuItem(android.R.id.paste)
        }
        assertEquals("VAR y = 3 + 2\nEND", document.text)
        onUi { view -> assertEveryLineColoured(view) }
    }
}
