package desktopkit.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.text.SpannableString
import android.text.Spanned
import android.text.style.CharacterStyle
import android.text.style.StyleSpan
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.BaseInputConnection
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.components.MenuEntry
import desktopkit.settings.DeskPreferences
import desktopkit.theme.DeskTheme
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.SyntaxRole
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The editor as a user meets it: typing through an input method (composing words included), the
 * hardware keys, undo and redo, the mouse, the gutter's markers, the menu, and documents switched
 * under it. The text of the view and of the state is compared after every scene: they are one.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w800dp-h600dp-mdpi")
class CodeEditorTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var document by mutableStateOf(CodeEditorState(sampleScript))
    private var readOnly by mutableStateOf(false)
    private var softWrap by mutableStateOf(true)
    private var markers by mutableStateOf(emptyList<LineMarker>())
    private var padding by mutableStateOf(PaddingValues(0.dp))
    private var zoom: CodeEditorZoom? = null
    private val clickedMarkers = mutableListOf<LineMarker>()
    private val clickedLinks = mutableListOf<Int>()
    private val menuOffsets = mutableListOf<Int>()
    private var jumped = 0

    private fun show(input: InputMode = InputMode.Pointer, extra: @Composable () -> Unit = {}) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = input) {
                Column(Modifier.fillMaxSize().background(DeskTheme.colors.background)) {
                    extra()
                    CodeEditor(
                        state = document,
                        modifier = Modifier.fillMaxSize().testTag("editor"),
                        tokenizer = sampleTokenizer,
                        markers = markers,
                        onMarkerClick = { clickedMarkers += it },
                        links = CodeLinks { state, offset -> wordAfterArrow(state, offset) },
                        onLinkClick = { clickedLinks += it },
                        contextMenu = { offset ->
                            menuOffsets += offset
                            listOf(MenuEntry.Item("Tanıma git", { jumped++ }))
                        },
                        zoom = zoom,
                        softWrap = softWrap,
                        readOnly = readOnly,
                        contentPadding = padding,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    /** The sample language's links: the name after "->". */
    private fun wordAfterArrow(state: CodeEditorState, offset: Int): TextRange? {
        val line = state.lineOf(offset)
        val text = state.lineText(line)
        val arrow = text.indexOf("->")
        if (arrow < 0) return null
        var from = arrow + 2
        while (from < text.length && text[from] == ' ') from++
        var to = from
        while (to < text.length && (text[to].isLetterOrDigit() || text[to] == '_')) to++
        val column = offset - state.lineStart(line)
        return if (to > from && column in from..to) TextRange(state.lineStart(line) + from, state.lineStart(line) + to) else null
    }

    private fun view(): CodeEditorView = compose.editorView()

    private fun onUi(block: (CodeEditorView) -> Unit) {
        val view = view()
        compose.runOnUiThread { block(view) }
        compose.waitForIdle()
    }

    private fun assertInStep() {
        val view = view()
        compose.runOnIdle { assertEquals("the view and the document hold the same text", view.text.toString(), document.text) }
    }

    /** The point of [offset] in the editor's view. */
    private fun pointOf(view: CodeEditorView, offset: Int): Offset {
        val layout = view.layout
        val row = layout.getLineForOffset(offset)
        return Offset(
            view.compoundPaddingLeft + layout.getPrimaryHorizontal(offset) - view.scrollX + 2f,
            (layout.getLineTop(row) + layout.getLineBottom(row)) / 2f - view.scrollY,
        )
    }

    private fun CodeEditorView.mouse(action: Int, at: Offset, buttons: Int = MotionEvent.BUTTON_PRIMARY, meta: Int = 0) {
        val now = android.os.SystemClock.uptimeMillis()
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE })
        val coords = arrayOf(MotionEvent.PointerCoords().apply { x = at.x; y = at.y; pressure = 1f; size = 1f })
        val event = MotionEvent.obtain(now, now, action, 1, properties, coords, meta, buttons, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
        if (action == MotionEvent.ACTION_HOVER_MOVE || action == MotionEvent.ACTION_SCROLL) dispatchGenericMotionEvent(event) else dispatchTouchEvent(event)
        event.recycle()
    }

    private fun CodeEditorView.click(at: Offset, buttons: Int = MotionEvent.BUTTON_PRIMARY, meta: Int = 0) {
        mouse(MotionEvent.ACTION_DOWN, at, buttons, meta)
        mouse(MotionEvent.ACTION_UP, at, 0, meta)
    }

    // ---------------------------------------------------------------- typing

    @Test
    fun typingThroughAnInputMethodReachesTheDocument() {
        show()
        val before = document.version
        onUi { view ->
            view.requestFocus()
            document.select(document.lineEnd(1))
            val keyboard = view.inputConnection()
            // a word composed letter by letter, then committed with the space after it
            keyboard.setComposingText("ş", 1)
            keyboard.setComposingText("şe", 1)
            keyboard.setComposingText("şeker", 1)
            keyboard.commitText("şeker ", 1)
            keyboard.commitText("ğüışöç", 1)
        }
        assertEquals("VAR cay = 2şeker ğüışöç", document.lineText(1))
        assertEquals(document.lineEnd(1), document.cursor)
        assertTrue(document.version > before)
        assertInStep()
    }

    @Test
    fun aComposedWordIsOneUndoStep() {
        show()
        onUi { view ->
            view.requestFocus()
            document.select(document.lineEnd(0))
            val keyboard = view.inputConnection()
            keyboard.setComposingText("m", 1)
            keyboard.setComposingText("me", 1)
            keyboard.setComposingText("mer", 1)
            keyboard.commitText("merhaba", 1)
        }
        assertEquals("// Çay bahçesi: ilk sahnemerhaba", document.lineText(0))
        onUi { document.undo() }
        assertEquals("// Çay bahçesi: ilk sahne", document.lineText(0))
        assertEquals(sampleScript, document.text)
        assertFalse(document.canUndo)
        onUi { document.redo() }
        assertEquals("// Çay bahçesi: ilk sahnemerhaba", document.lineText(0))
        assertInStep()
    }

    @Test
    fun anEditFromTheAppWhileAWordIsComposedLosesNothing() {
        show()
        onUi { view ->
            view.requestFocus()
            document.select(document.lineEnd(1))
            val keyboard = view.inputConnection()
            keyboard.setComposingText("yar", 1)
            assertTrue("a word is being composed", BaseInputConnection.getComposingSpanStart(view.editableText) >= 0)
            // the app's toolbar puts a snippet in at the cursor, and changes a line above
            document.insert(" -> son")
            document.replace(0, 2, "##")
            assertEquals("the composition was finished first", -1, BaseInputConnection.getComposingSpanStart(view.editableText))
            // the keyboard goes on typing where the cursor now is
            keyboard.commitText("!", 1)
        }
        assertEquals("## Çay bahçesi: ilk sahne", document.lineText(0))
        assertEquals("VAR cay = 2yar -> son!", document.lineText(1))
        assertInStep()
    }

    @Test
    fun enterKeepsTheIndentationAndTabIndents() {
        show()
        onUi { view ->
            view.requestFocus()
            document.select(document.lineEnd(9))
            assertEquals("    ~ cay = cay - 1", document.lineText(9))
            view.press(KeyEvent.KEYCODE_ENTER)
            view.inputConnection().commitText("x", 1)
            // the on-screen keyboard's Enter is text, not a key
            view.inputConnection().commitText("\n", 1)
            view.press(KeyEvent.KEYCODE_TAB)
        }
        assertEquals("    x", document.lineText(10))
        assertEquals("    \t", document.lineText(11))
        assertInStep()
    }

    @Test
    fun tabMovesTheSelectedLinesAndShiftTabMovesThemBack() {
        show()
        onUi { view ->
            view.requestFocus()
            document.select(document.lineStart(5) + 3, document.lineStart(7) + 2)
            view.press(KeyEvent.KEYCODE_TAB)
        }
        assertEquals("\tSabah erkenden", document.lineText(5).take(15))
        assertEquals("\t* \"Bir çay", document.lineText(6).take(11))
        assertEquals("\t* Denize", document.lineText(7).take(9))
        assertEquals("+ { cay > 0 } Bardağı bitirdim.", document.lineText(8))
        onUi { view -> view.press(KeyEvent.KEYCODE_TAB, KeyEvent.META_SHIFT_ON) }
        assertEquals(sampleScript, document.text)
        assertInStep()
    }

    @Test
    fun controlKeysUndoRedoAndSelectAll() {
        show()
        onUi { view ->
            view.requestFocus()
            document.select(0)
            view.inputConnection().commitText("bir ", 1)
            view.inputConnection().commitText("iki", 1)
            view.press(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON)
        }
        assertEquals("bir // Çay", document.text.take(10))
        onUi { view -> view.press(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON) }
        assertEquals(sampleScript, document.text)
        onUi { view -> view.press(KeyEvent.KEYCODE_Y, KeyEvent.META_CTRL_ON) }
        onUi { view -> view.press(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON) }
        assertEquals("bir iki// Çay", document.text.take(13))
        onUi { view -> view.press(KeyEvent.KEYCODE_A, KeyEvent.META_CTRL_ON) }
        assertEquals(TextRange(0, document.length), document.selection)
        assertInStep()
    }

    @Test
    fun pastedTextComesWithoutItsStyles() {
        show()
        onUi { view ->
            val styled = SpannableString("kalın\r\nmetin").apply { setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            view.context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("test", styled))
            view.requestFocus()
            document.select(0)
            view.press(KeyEvent.KEYCODE_V, KeyEvent.META_CTRL_ON)
            assertEquals("no span of the clipboard's reached the text", 0, view.editableText.getSpans(0, 12, StyleSpan::class.java).size)
        }
        assertEquals("kalın", document.lineText(0))
        assertEquals("metin// Çay bahçesi: ilk sahne", document.lineText(1))
        assertInStep()
    }

    @Test
    fun aReadOnlyEditorRefusesEveryEditOfTheUsersButNotTheApps() {
        readOnly = true
        show()
        onUi { view ->
            view.requestFocus()
            document.select(4)
            view.inputConnection().commitText("x", 1)
            view.press(KeyEvent.KEYCODE_DEL)
            view.press(KeyEvent.KEYCODE_ENTER)
            view.press(KeyEvent.KEYCODE_X, KeyEvent.META_CTRL_ON)
        }
        assertEquals(sampleScript, document.text)
        onUi { document.replace(0, 2, "##") }
        assertEquals("## Çay bahçesi: ilk sahne", document.lineText(0))
        assertInStep()
    }

    // ---------------------------------------------------------------- documents

    @Test
    fun eachDocumentComesBackAsItWasLeft() {
        val first = document
        val second = CodeEditorState((1..200).joinToString("\n") { "satır $it" })
        show()
        onUi { view ->
            view.requestFocus()
            first.select(first.lineEnd(2))
            view.inputConnection().commitText(" // not", 1)
        }
        document = second
        compose.waitForIdle()
        onUi { view ->
            assertEquals(second.text, view.text.toString())
            second.revealLine(150)
        }
        val scrolled = view().scrollY
        assertTrue("line 151 was scrolled to", scrolled > 0)
        onUi { view -> view.inputConnection().commitText("x", 1) }
        assertEquals("xsatır 151", second.lineText(150))
        // the first document was not touched by what was typed into the second
        assertEquals("VAR sehir = \"Kemer\" // not", first.lineText(2))
        document = first
        compose.waitForIdle()
        onUi { view ->
            assertEquals(first.text, view.text.toString())
            assertEquals(first.lineEnd(2), view.selectionEnd)
            first.undo()
        }
        assertEquals(sampleScript, first.text)
        document = second
        compose.waitForIdle()
        assertEquals("the second document's scroll position", scrolled, view().scrollY)
        onUi { second.undo() }
        assertEquals("satır 151", second.lineText(150))
        assertInStep()
    }

    @Test
    fun aDocumentEditedWhileNotShownShowsTheEdit() {
        val other = CodeEditorState("bir\niki")
        show()
        other.select(3)
        other.insert(" buçuk")
        other.revealLine(1, select = true)
        document = other
        compose.waitForIdle()
        onUi { view ->
            assertEquals("bir buçuk\niki", view.text.toString())
            assertEquals(other.lineStart(1), view.selectionStart)
            assertEquals(other.lineEnd(1), view.selectionEnd)
        }
        assertInStep()
    }

    @Test
    fun setTextShowsTheNewDocument() {
        show()
        onUi { document.setText("yeniden\nyüklendi") }
        onUi { view -> assertEquals("yeniden\nyüklendi", view.text.toString()) }
        assertFalse(document.canUndo)
        assertInStep()
    }

    // ---------------------------------------------------------------- colours

    private fun roles(view: CodeEditorView, line: Int): List<Triple<Int, Int, Int>> {
        val start = document.lineStart(line)
        val text = view.editableText
        return document.tokens.tokensOf(line).toList().chunked(3).map { Triple(it[0], it[1], it[2]) }.also { tokens ->
            // one span for each token, where the token is
            val spans = text.getSpans(start, document.lineEnd(line), CharacterStyle::class.java)
                .filter { it.javaClass.name.contains("RoleSpan") }
                .map { text.getSpanStart(it) - start to text.getSpanEnd(it) - start }.sortedBy { it.first }
            assertEquals("spans of line $line", tokens.map { it.first to it.second }, spans)
        }
    }

    @Test
    fun theSpansAreTheTokensInTheSamePassThatChangesTheText() {
        show()
        onUi { view ->
            assertEquals(listOf(Triple(0, 3, SyntaxRole.Keyword.ordinal), Triple(10, 11, SyntaxRole.Number.ordinal)), roles(view, 1))
            view.requestFocus()
            // a block comment opened on line 2: every line down to the comment's old end changes colour at once
            document.select(document.lineStart(1))
            view.inputConnection().commitText("/*", 1)
            for (line in 1..13) {
                val tokens = roles(view, line)
                if (document.lineText(line).isNotEmpty()) assertEquals("line $line is a comment", SyntaxRole.Comment.ordinal, tokens.first().third)
            }
            // and back
            view.press(KeyEvent.KEYCODE_DEL)
            view.press(KeyEvent.KEYCODE_DEL)
            assertEquals(listOf(Triple(0, 3, SyntaxRole.Keyword.ordinal), Triple(10, 11, SyntaxRole.Number.ordinal)), roles(view, 1))
            assertEquals(SyntaxRole.Heading.ordinal, roles(view, 4).single().third)
        }
        assertInStep()
    }

    // ---------------------------------------------------------------- pointer

    @Test
    fun aFingerScrollsTheTextAndLeavesTheCursorWhereItWas() {
        document = CodeEditorState((1..300).joinToString("\n") { "satır $it" })
        show(InputMode.Touch)
        onUi { view ->
            view.requestFocus()
            document.select(document.lineStart(2))
        }
        compose.onNodeWithTag("editor").performTouchInput { swipe(center, center - Offset(0f, 250f), durationMillis = 400) }
        compose.waitForIdle()
        val view = view()
        assertTrue("the text scrolled", view.scrollY > 100)
        assertEquals("scrolling is not moving the cursor", document.lineStart(2), document.cursor)
        // typing brings the cursor's line back into view
        onUi { it.inputConnection().commitText("x", 1) }
        compose.runOnIdle {
            val top = view.layout.getLineTop(view.layout.getLineForOffset(document.cursor))
            assertTrue("the cursor's line is in view again", top >= view.scrollY && top < view.scrollY + view.height)
        }
        assertInStep()
    }

    @Test
    fun theTextScrollsThroughTheContentPadding() {
        document = CodeEditorState((1..300).joinToString("\n") { "satır $it" })
        padding = PaddingValues(top = 56.dp, bottom = 80.dp)
        show()
        val view = view()
        compose.runOnIdle {
            // at rest the first line stands clear of the bar above it
            assertEquals(-(56 + 8), view.scrollY)
            view.scrollBy(0, 100_000)
            // and the last line can come up clear of the bar below
            val lastBottom = view.layout.getLineBottom(view.layout.lineCount - 1)
            assertEquals(lastBottom - view.height + 80 + 8, view.scrollY)
        }
        onUi { document.select(3) }
        compose.runOnIdle { assertEquals("the cursor's line is brought below the bar, not under it", -(56 + 8), view.scrollY) }
    }

    @Test
    fun aRightClickOpensTheKitsMenuWithTheAppsEntries() {
        show()
        val target = document.lineStart(6) + 4
        onUi { view -> view.click(pointOf(view, target), buttons = MotionEvent.BUTTON_SECONDARY) }
        compose.onNodeWithText("Kopyala").assertExists()
        compose.onNodeWithText("Tümünü seç").assertExists()
        assertEquals("the app was asked for the place that was clicked", target, menuOffsets.last())
        assertEquals("the cursor went to the click", target, document.cursor)
        compose.onNodeWithText("Tanıma git").performClick()
        compose.waitForIdle()
        assertEquals(1, jumped)
        compose.onNodeWithText("Kopyala").assertDoesNotExist()
    }

    @Test
    fun aControlClickOnALinkTellsTheApp() {
        show()
        val line = 6
        val link = document.lineStart(line) + document.lineText(line).indexOf("cay_soyle") + 2
        onUi { view ->
            // without the modifier a click is a click
            view.click(pointOf(view, link))
            assertTrue(clickedLinks.isEmpty())
            view.click(pointOf(view, link), meta = KeyEvent.META_CTRL_ON)
        }
        assertEquals(1, clickedLinks.size)
        assertEquals("cay_soyle", wordAfterArrow(document, clickedLinks.single())?.let { document.textIn(it.min, it.max) })
        onUi { view ->
            // Alt as well; and a click beside the link does nothing
            view.click(pointOf(view, link), meta = KeyEvent.META_ALT_ON)
            view.click(pointOf(view, document.lineStart(line) + 5), meta = KeyEvent.META_CTRL_ON)
        }
        assertEquals(2, clickedLinks.size)
    }

    @Test
    fun theWheelScrollsAndWithControlItZooms() {
        document = CodeEditorState((1..300).joinToString("\n") { "satır $it" })
        zoom = CodeEditorZoom()
        show()
        val view = view()
        val size = view.textSize
        onUi {
            val now = android.os.SystemClock.uptimeMillis()
            fun wheel(meta: Int, notches: Float) {
                val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE })
                val coords = arrayOf(MotionEvent.PointerCoords().apply { x = 200f; y = 200f; setAxisValue(MotionEvent.AXIS_VSCROLL, notches) })
                val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, properties, coords, meta, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
                it.dispatchGenericMotionEvent(event)
                event.recycle()
            }
            wheel(0, -2f)
            assertTrue("the wheel scrolled the text", it.scrollY > 0)
            wheel(KeyEvent.META_CTRL_ON, 1f)
        }
        assertEquals(1.1f, zoom!!.scale, 0.001f)
        compose.runOnIdle { assertEquals(size * 1.1f, view.textSize, 0.01f) }
        onUi { it.press(KeyEvent.KEYCODE_0, KeyEvent.META_CTRL_ON) }
        assertEquals(1f, zoom!!.scale, 0.001f)
        compose.runOnIdle { assertEquals(size, view.textSize, 0.01f) }
    }

    @Test
    fun theZoomIsKeptInThePreferences() {
        val store = compose.activity.getSharedPreferences("editor-zoom-test", 0).also { it.edit().clear().commit() }
        val prefs = DeskPreferences(store)
        fun zoom() = CodeEditorZoom(prefs.editorTextScale("script").let { if (it.isNaN()) 1f else it }) { prefs.updateEditorTextScale("script", it) }
        val first = zoom()
        first.zoomIn()
        first.zoomIn()
        assertEquals(1.2f, zoom().scale, 0.001f)
        repeat(30) { first.zoomOut() }
        assertEquals(first.minScale, zoom().scale, 0.001f)
    }

    // ---------------------------------------------------------------- markers

    @Test
    fun aMarkerIsAnElementWithItsMessage() {
        markers = listOf(LineMarker(6, MarkerSeverity.Error, "Bilinmeyen hedef"), LineMarker(8, MarkerSeverity.Warning, "Koşul hep doğru"))
        show(InputMode.Touch)
        compose.onNodeWithContentDescription("7. satırda hata: Bilinmeyen hedef").assertExists()
        compose.onNodeWithContentDescription("9. satırda uyarı: Koşul hep doğru").performClick()
        compose.waitForIdle()
        assertEquals(listOf(LineMarker(8, MarkerSeverity.Warning, "Koşul hep doğru")), clickedMarkers)
        // the tap shows the message beside the marker
        compose.onNodeWithText("Koşul hep doğru").assertExists()
        // markers that go away take their elements along
        markers = emptyList()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("7. satırda hata: Bilinmeyen hedef").assertDoesNotExist()
    }

    @Test
    fun onlyTheMarkersInViewAreElements() {
        document = CodeEditorState((1..400).joinToString("\n") { "satır $it" })
        markers = listOf(LineMarker(2, MarkerSeverity.Info, "yakın"), LineMarker(350, MarkerSeverity.Error, "uzak"))
        show()
        compose.onNodeWithContentDescription("3. satırda not: yakın").assertExists()
        compose.onNodeWithContentDescription("351. satırda hata: uzak").assertDoesNotExist()
        onUi { document.revealLine(350) }
        compose.onNodeWithContentDescription("351. satırda hata: uzak").assertExists()
        compose.onNodeWithContentDescription("3. satırda not: yakın").assertDoesNotExist()
    }

    @Test
    fun theGutterGrowsWithTheDigitsAndGoesWithoutNumbers() {
        document = CodeEditorState("bir")
        show()
        val view = view()
        val twoDigits = view.compoundPaddingLeft
        onUi { document.setText((1..1200).joinToString("\n") { "s" }) }
        compose.runOnIdle { assertTrue("four digits need a wider gutter", view.compoundPaddingLeft > twoDigits) }
        onUi { document.setText("bir") }
        compose.runOnIdle { assertEquals(twoDigits, view.compoundPaddingLeft) }
        assertNotEquals(0, twoDigits)
    }
}
