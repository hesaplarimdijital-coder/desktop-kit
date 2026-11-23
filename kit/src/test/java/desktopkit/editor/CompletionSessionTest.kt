package desktopkit.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The completion list's logic on a document no editor shows: asking, narrowing, choosing, closing. Plain JVM. */
class CompletionSessionTest {

    private var asked = 0

    /** Offers its labels for the word at the cursor, the word's end included. */
    private fun completer(vararg labels: String) = CodeCompleter { state, cursor ->
        asked++
        var start = cursor
        while (start > 0 && CompletionSession.isWordChar(state.charAt(start - 1))) start--
        var end = cursor
        while (end < state.length && CompletionSession.isWordChar(state.charAt(end))) end++
        CodeCompletions(start, end, labels.map { CodeCompletionItem(it) })
    }

    private fun CompletionSession.labels() = shown.map { it.item.label }

    @Test
    fun theTypedPartNarrowsTheListBestFirst() {
        val state = CodeEditorState("-> ")
        val session = CompletionSession()
        val completer = completer("sonraki_sahne", "sahil", "dosya_sil", "masal", "Şarkı")
        state.select(3)
        state.insert("s")
        session.request(state, completer, state.cursor, typed = true)
        // the start of a label (the closer its length, the better), then the start of a word in it, then inside it;
        // ties keep the completer's order
        assertEquals(listOf("sahil", "Şarkı", "sonraki_sahne", "dosya_sil", "masal"), session.labels())
        state.insert("a")
        session.refilter(state, state.cursor, collapsed = true)
        assertEquals(listOf("sahil", "Şarkı", "sonraki_sahne", "masal"), session.labels())
        state.insert("rk")
        session.refilter(state, state.cursor, collapsed = true)
        assertEquals("Turkish letters fold: \"sark\" is \"Şark\"", listOf("Şarkı"), session.labels())
        assertEquals(1, asked)
    }

    @Test
    fun aCharacterThatEndsTheWordOrACursorThatLeavesItCloses() {
        val state = CodeEditorState("x")
        val session = CompletionSession()
        state.select(1)
        session.request(state, completer("xavier", "xenon"), state.cursor, typed = true)
        assertTrue(session.isOpen)
        state.insert(".")
        session.refilter(state, state.cursor, collapsed = true)
        assertFalse("a dot ends the word", session.isOpen)
        state.setText("a x")
        state.select(3)
        session.request(state, completer("xavier", "xenon"), state.cursor, typed = false)
        assertTrue(session.isOpen)
        session.refilter(state, 1, collapsed = true)
        assertFalse("the cursor went before the word", session.isOpen)
        session.request(state, completer("xavier", "xenon"), 3, typed = false)
        session.refilter(state, 3, collapsed = false)
        assertFalse("a selection closes it", session.isOpen)
    }

    @Test
    fun choosingReplacesTheWholeWordAsOneStep() {
        val state = CodeEditorState("VAR ab = 1")
        val session = CompletionSession()
        // the cursor inside "ab": "a|b"
        state.select(5)
        session.request(state, completer("abacus", "able"), state.cursor, typed = false)
        assertEquals("the typed part is \"a\": the shorter label first", listOf("able", "abacus"), session.labels())
        session.accept(state, session.highlighted)
        assertEquals("VAR able = 1", state.text)
        assertEquals("the cursor after the chosen text", 8, state.cursor)
        assertFalse(session.isOpen)
        assertTrue(state.undo())
        assertEquals("VAR ab = 1", state.text)
        assertFalse("one step", state.canUndo)
    }

    @Test
    fun aWordThatIsAlreadyTheOnlyItemClosesTheList() {
        // the device run: "Oak tree" and Enter, "tree" offered: the list took Enter and the line never broke
        val state = CodeEditorState("Oak ")
        val session = CompletionSession()
        state.select(4)
        state.insert("tre")
        session.request(state, completer("tree"), state.cursor, typed = true)
        assertEquals(listOf("tree"), session.labels())
        state.insert("e")
        session.refilter(state, state.cursor, collapsed = true)
        assertFalse("nothing is left to complete: the list is out of the way of Enter", session.isOpen)
    }

    @Test
    fun choosingTheItemThatIsAlreadyTheWordWouldChangeNothing() {
        val state = CodeEditorState("Oak tree")
        val session = CompletionSession()
        state.select(8)
        session.request(state, completer("tree", "treehouse"), state.cursor, typed = false)
        assertEquals(listOf("tree", "treehouse"), session.labels())
        assertTrue(session.changesNothing(state, 0))
        assertFalse(session.changesNothing(state, 1))
        // the case differs: choosing it changes the word
        state.setText("Oak Tree")
        state.select(8)
        session.request(state, completer("tree", "treehouse"), state.cursor, typed = false)
        assertFalse(session.changesNothing(state, 0))
    }

    @Test
    fun aWordNothingFitsIsNotAskedAboutAgain() {
        val state = CodeEditorState("")
        val session = CompletionSession()
        val completer = completer("bahce", "sahil")
        state.insert("q")
        session.request(state, completer, state.cursor, typed = true)
        assertFalse(session.isOpen)
        state.insert("w")
        session.request(state, completer, state.cursor, typed = true)
        assertEquals("asked once for the word", 1, asked)
        // its letters gone, the same place starts afresh
        state.replace(0, 2, "")
        session.request(state, completer, state.cursor, typed = true)
        state.insert("b")
        session.request(state, completer, state.cursor, typed = true)
        assertEquals(listOf("bahce"), session.labels())
        assertEquals(2, asked)
    }
}
