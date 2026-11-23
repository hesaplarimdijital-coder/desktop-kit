package desktopkit.editor

import android.text.InputType
import android.text.method.TextKeyListener
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.theme.DeskTheme
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The on-screen keyboard's word in progress and the cursor. A keyboard holds a word while it is typed, and again
 * whenever the cursor touches one (Gboard takes it back to offer corrections: `setComposingRegion`); its next
 * letters replace what it holds. A cursor moved by anything but the keyboard (a key, a tap, a click, the app, the
 * search) must end that word where it stands, or the keyboard writes over it from far away: on the device run
 * "Smoke" became "Imeword" after Ctrl+Home. The keyboard's own moves keep its word. And a hardware keyboard types
 * the case that is pressed, whatever the on-screen keyboard is asked to capitalise.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w800dp-h600dp-mdpi")
class CodeEditorCompositionTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val lines = listOf("Smoke line from keys.", "Once upon a time...", "", "* There were two choices.", "    -> END", "")
    private val document = CodeEditorState(lines.joinToString("\n"))

    /** The document with [line] changed to [text], and nothing else changed. */
    private fun withLine(line: Int, text: String) = lines.toMutableList().also { it[line] = text }.joinToString("\n")
    private val search = CodeSearchState()
    private lateinit var keyboard: InputConnection

    private fun show() {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                CodeEditor(
                    state = document,
                    modifier = Modifier.fillMaxSize().background(DeskTheme.colors.background),
                    search = search,
                    autoCorrect = true,
                    capitalizeSentences = true,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun view(): CodeEditorView = compose.editorView()

    private fun onUi(block: (CodeEditorView) -> Unit) {
        val view = view()
        compose.runOnUiThread { block(view) }
        compose.waitForIdle()
    }

    /** The keyboard holds the word from [start] to [end], the cursor at its end, as Gboard does after a move. */
    private fun holdWord(view: CodeEditorView, start: Int, end: Int) {
        view.requestFocus()
        document.select(end)
        keyboard = view.inputConnection()
        keyboard.setComposingRegion(start, end)
        assertEquals("the keyboard holds the word", start, BaseInputConnection.getComposingSpanStart(view.editableText))
    }

    /** The keyboard types a new word letter by letter where it believes the cursor is, and finishes it. */
    private fun typeWord(word: String) {
        for (length in 1..word.length) keyboard.setComposingText(word.take(length), 1)
        keyboard.finishComposingText()
    }

    private fun composingStart(view: CodeEditorView) = BaseInputConnection.getComposingSpanStart(view.editableText)

    /** Lets the main thread and the search's thread go on until [condition] (Robolectric runs main-thread messages only when idling). */
    private fun await(condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        compose.waitForIdle()
        while (!condition()) {
            check(System.currentTimeMillis() < until) { "not so after 5 s" }
            Thread.sleep(10)
            compose.waitForIdle()
        }
    }

    private fun pointOf(view: CodeEditorView, offset: Int): Offset {
        val layout = view.layout
        val row = layout.getLineForOffset(offset)
        return Offset(
            view.compoundPaddingLeft + layout.getPrimaryHorizontal(offset) - view.scrollX + 2f,
            (layout.getLineTop(row) + layout.getLineBottom(row)) / 2f - view.scrollY,
        )
    }

    private fun CodeEditorView.pointer(action: Int, at: Offset, tool: Int, source: Int, buttons: Int = 0, downTime: Long) {
        val now = android.os.SystemClock.uptimeMillis()
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = tool })
        val coords = arrayOf(MotionEvent.PointerCoords().apply { x = at.x; y = at.y; pressure = 1f; size = 1f })
        val event = MotionEvent.obtain(downTime, now, action, 1, properties, coords, 0, buttons, 1f, 1f, 0, 0, source, 0)
        dispatchTouchEvent(event)
        event.recycle()
    }

    private fun CodeEditorView.tap(at: Offset) {
        val down = android.os.SystemClock.uptimeMillis()
        pointer(MotionEvent.ACTION_DOWN, at, MotionEvent.TOOL_TYPE_FINGER, InputDevice.SOURCE_TOUCHSCREEN, downTime = down)
        pointer(MotionEvent.ACTION_UP, at, MotionEvent.TOOL_TYPE_FINGER, InputDevice.SOURCE_TOUCHSCREEN, downTime = down)
    }

    private fun CodeEditorView.click(at: Offset) {
        val down = android.os.SystemClock.uptimeMillis()
        pointer(MotionEvent.ACTION_DOWN, at, MotionEvent.TOOL_TYPE_MOUSE, InputDevice.SOURCE_MOUSE, MotionEvent.BUTTON_PRIMARY, down)
        pointer(MotionEvent.ACTION_UP, at, MotionEvent.TOOL_TYPE_MOUSE, InputDevice.SOURCE_MOUSE, 0, down)
    }

    private val ctrl = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON

    /**
     * The keyboard holds the word from [start] to [end] (the cursor at its end), [move] moves the cursor without the
     * keyboard, and the keyboard types [word]: the document becomes [expected], the word it held kept.
     */
    private fun holdMoveType(start: Int, end: Int, word: String, expected: String, move: (CodeEditorView) -> Unit) {
        show()
        var composing = 0
        onUi { view ->
            holdWord(view, start, end)
            move(view)
            composing = composingStart(view)
            typeWord(word)
        }
        assertEquals(expected, document.text)
        assertEquals("the move ended the word the keyboard held, where it stands", -1, composing)
    }

    @Test
    fun controlHomeEndsTheWordTheKeyboardHolds() {
        // the device run: the keyboard holds "keys", Ctrl+Home, the keyboard types a word
        holdMoveType(16, 20, "Imeword", withLine(0, "ImewordSmoke line from keys.")) { view -> view.press(KeyEvent.KEYCODE_MOVE_HOME, ctrl) }
    }

    @Test
    fun anArrowKeyEndsTheWordTheKeyboardHolds() {
        val once = document.lineStart(1)
        holdMoveType(once, once + 4, "bir", withLine(2, "bir")) { view -> view.press(KeyEvent.KEYCODE_DPAD_DOWN) }
    }

    @Test
    fun theEndKeyEndsTheWordTheKeyboardHolds() {
        val there = document.lineStart(3) + 2
        holdMoveType(there, there + 5, "iki", withLine(3, "* There were two choices.iki")) { view -> view.press(KeyEvent.KEYCODE_MOVE_END) }
    }

    @Test
    fun aLetterFromAHardwareKeyboardEndsTheWordTheKeyboardHolds() {
        // the letter goes in at the cursor, after the word, and the keyboard's next word after it
        holdMoveType(0, 5, "yeni", withLine(0, "Smokexyeni line from keys.")) { view -> view.press(KeyEvent.KEYCODE_X) }
    }

    @Test
    fun theAppSelectingEndsTheWordTheKeyboardHolds() {
        holdMoveType(0, 5, "bir ", withLine(1, "bir Once upon a time...")) { document.select(document.lineStart(1)) }
    }

    @Test
    fun goingToALineEndsTheWordTheKeyboardHolds() {
        holdMoveType(0, 5, "iki ", withLine(3, "* iki There were two choices.")) { document.goToLine(3, 2) }
    }

    @Test
    fun aLineRevealedForAnIssueEndsTheWordTheKeyboardHolds() {
        holdMoveType(0, 5, "üç", withLine(4, "üç    -> END")) { document.revealLine(4) }
    }

    @Test
    fun theSearchGoingToAMatchEndsTheWordTheKeyboardHolds() {
        show()
        onUi {
            search.query = "time"
            search.open()
        }
        await { search.matchCount == 1 }
        onUi { view ->
            holdWord(view, 0, 5)
            search.next()
            assertEquals("time", document.selectedText)
            assertEquals(-1, composingStart(view))
            // the keyboard types over the selected match, as it would over any selection
            keyboard.commitText("day", 1)
        }
        assertEquals(withLine(1, "Once upon a day..."), document.text)
    }

    @Test
    fun aTapEndsTheWordTheKeyboardHolds() {
        val target = document.lineStart(1) + 5
        holdMoveType(0, 5, "yeni", withLine(1, "Once yeniupon a time...")) { view ->
            view.tap(pointOf(view, target))
            assertEquals("the tap put the cursor there", target, document.cursor)
        }
    }

    @Test
    fun aClickEndsTheWordTheKeyboardHolds() {
        holdMoveType(0, 5, "eski", withLine(3, "eski* There were two choices.")) { view ->
            view.click(pointOf(view, document.lineStart(3)))
            assertEquals(document.lineStart(3), document.cursor)
        }
    }

    @Test
    fun aTapInTheGutterEndsTheWordTheKeyboardHolds() {
        // the cursor goes to the start of the line
        holdMoveType(0, 5, "sol", withLine(4, "sol    -> END")) { view ->
            view.tap(Offset(2f, pointOf(view, document.lineStart(4)).y))
        }
    }

    @Test
    fun aHardwareKeyboardTypesTheCaseThatIsPressed() {
        // the device run (tablet, no on-screen keyboard): "keptword" at a line's start became "Keptword". Capitalising
        // sentences is a hint for on-screen keyboards; a hardware keyboard types what is pressed
        show()
        onUi { view ->
            view.requestFocus()
            document.select(document.lineStart(2))
            for (key in listOf(KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_T)) view.press(key)
            // after a full stop too, and Shift still makes a capital
            document.select(document.lineEnd(1))
            view.press(KeyEvent.KEYCODE_SPACE)
            view.press(KeyEvent.KEYCODE_A)
            view.press(KeyEvent.KEYCODE_B, KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON)
        }
        assertEquals("kept", document.lineText(2))
        assertEquals("Once upon a time... aB", document.lineText(1))
        // the hardware keys go through a listener that never changes what is typed (no capitals of its own, no
        // automatic corrections): the platform's listener for the input type capitalised a line's first letter
        // on the tablet, where hardware keys reach it
        onUi { view -> assertSame(TextKeyListener.getInstance(false, TextKeyListener.Capitalize.NONE), view.keyListener) }
        // the on-screen keyboard is still asked to capitalise sentences and to correct
        onUi { view ->
            val info = EditorInfo()
            view.onCreateInputConnection(info)
            assertTrue(info.inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0)
            assertTrue(info.inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT != 0)
            assertTrue(info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0)
        }
    }

    @Test
    fun theKeyboardsOwnMovesKeepItsWord() {
        show()
        onUi { view ->
            holdWord(view, 0, 5)
            // the keyboard moves the cursor inside the word it holds, then types a correction over the word
            keyboard.setSelection(2, 2)
            assertEquals("its own move keeps its word", 0, composingStart(view))
            keyboard.setComposingText("Smoky", 1)
            keyboard.finishComposingText()
        }
        assertEquals("Smoky line from keys.", document.lineText(0))
        onUi { view ->
            // and inside one batch, as keyboards group their changes
            document.select(document.lineEnd(1))
            keyboard = view.inputConnection()
            keyboard.beginBatchEdit()
            keyboard.setComposingRegion(document.lineStart(1), document.lineStart(1) + 4)
            keyboard.setSelection(document.lineStart(1) + 4, document.lineStart(1) + 4)
            keyboard.setComposingText("Twice", 1)
            keyboard.endBatchEdit()
            keyboard.finishComposingText()
        }
        assertEquals("Twice upon a time...", document.lineText(1))
    }
}
