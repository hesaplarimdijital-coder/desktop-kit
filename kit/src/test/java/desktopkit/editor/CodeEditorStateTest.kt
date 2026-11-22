package desktopkit.editor

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A document on its own, with no editor showing it: what an app does to the documents in its other tabs. */
class CodeEditorStateTest {

    @Test
    fun readsTextLinesAndPositions() {
        val state = CodeEditorState("bir\niki üç\n")
        assertEquals(3, state.lineCount)
        assertEquals("iki üç", state.lineText(1))
        assertEquals("", state.lineText(2))
        assertEquals(4, state.lineStart(1))
        assertEquals(10, state.lineEnd(1))
        assertEquals(1, state.lineOf(7))
        assertEquals(CodePosition(1, 3), state.positionOf(7))
        assertEquals(7, state.offsetOf(1, 3))
        // a column past the line's end is the line's end
        assertEquals(3, state.offsetOf(0, 99))
        assertEquals("iki", state.textIn(4, 7))
        assertEquals(11, state.length)
    }

    @Test
    fun lineBreaksBecomeLineFeeds() {
        val state = CodeEditorState("a\r\nb\rc")
        assertEquals("a\nb\nc", state.text)
        state.select(state.length)
        state.insert("\r\nd")
        assertEquals("a\nb\nc\nd", state.text)
        state.setText("x\r\ny")
        assertEquals("x\ny", state.text)
    }

    @Test
    fun insertReplacesTheSelectionAndLeavesTheCursorAfter() {
        val state = CodeEditorState("merhaba dünya")
        state.select(8, 13)
        assertEquals("dünya", state.selectedText)
        val before = state.version
        state.insert("Kemer")
        assertEquals("merhaba Kemer", state.text)
        assertEquals(TextRange(13), state.selection)
        assertTrue(state.version > before)
        state.select(0, 7)
        state.replaceSelection("selam", select = true)
        assertEquals(TextRange(0, 5), state.selection)
        assertEquals("selam Kemer", state.text)
    }

    @Test
    fun replaceKeepsTheCursorWithItsText() {
        val state = CodeEditorState("0123456789")
        state.select(8)
        state.replace(2, 4, "abcdef")
        assertEquals("01abcdef456789", state.text)
        assertEquals(12, state.cursor)
        state.select(3)
        state.replace(0, 6, "")
        assertEquals(0, state.cursor)
    }

    @Test
    fun undoAndRedoGoThroughEveryEdit() {
        val state = CodeEditorState("bir")
        assertFalse(state.canUndo)
        state.select(3)
        state.insert(" iki")
        state.replace(0, 3, "BIR")
        assertEquals("BIR iki", state.text)
        assertTrue(state.canUndo)
        assertTrue(state.undo())
        assertEquals("bir iki", state.text)
        assertTrue(state.undo())
        assertEquals("bir", state.text)
        assertEquals(TextRange(3), state.selection)
        assertFalse(state.canUndo)
        assertFalse(state.undo())
        assertTrue(state.canRedo)
        assertTrue(state.redo())
        assertTrue(state.redo())
        assertEquals("BIR iki", state.text)
        assertFalse(state.canRedo)
    }

    @Test
    fun anEditBlockIsOneStep() {
        val state = CodeEditorState("a a a")
        state.edit {
            state.replace(4, 5, "b")
            state.replace(2, 3, "b")
            state.replace(0, 1, "b")
        }
        assertEquals("b b b", state.text)
        assertTrue(state.undo())
        assertEquals("a a a", state.text)
        assertFalse(state.canUndo)
        assertTrue(state.redo())
        assertEquals("b b b", state.text)
    }

    @Test
    fun setTextForgetsTheHistory() {
        val state = CodeEditorState("eski")
        state.insert("x")
        state.setText("yeni metin")
        assertEquals("yeni metin", state.text)
        assertFalse(state.canUndo)
        assertEquals(TextRange.Zero, state.selection)
        assertEquals(1, state.lineCount)
    }

    @Test
    fun indentAndOutdentMoveTheSelectedLines() {
        val state = CodeEditorState("bir\niki\n\nüç\ndört")
        state.select(1, state.lineStart(3) + 1)
        state.indentSelection(CodeIndent.spaces(2))
        // an empty line gets no trailing spaces; the line the selection does not reach stays
        assertEquals("  bir\n  iki\n\n  üç\ndört", state.text)
        assertEquals(TextRange(0, state.lineEnd(3)), state.selection)
        state.outdentSelection(CodeIndent.spaces(2))
        assertEquals("bir\niki\n\nüç\ndört", state.text)
        assertTrue(state.undo())
        assertEquals("  bir\n  iki\n\n  üç\ndört", state.text)
        // one step each
        assertTrue(state.undo())
        assertEquals("bir\niki\n\nüç\ndört", state.text)
        assertFalse(state.canUndo)
    }

    @Test
    fun outdentTakesATabOrUpToTheIndentsSpaces() {
        val state = CodeEditorState("\tbir\n      iki\n üç")
        state.selectAll()
        state.outdentSelection()
        assertEquals("bir\n  iki\nüç", state.text)
    }

    @Test
    fun aSelectionEndingAtALinesStartDoesNotTouchThatLine() {
        val state = CodeEditorState("bir\niki\nüç")
        state.select(0, state.lineStart(2))
        assertEquals(0..1, state.selectedLines())
        state.select(2)
        assertEquals(0..0, state.selectedLines())
    }

    @Test
    fun scrollToLineOnADocumentNoEditorShowsIsItsFirstVisibleLine() {
        val state = CodeEditorState((1..100).joinToString("\n") { "satır $it" })
        assertEquals(0, state.firstVisibleLine)
        // the place saved before a restart, given back before any editor shows the document
        state.scrollToLine(40)
        assertEquals(40, state.firstVisibleLine)
        // lines added above keep the same text at the top
        state.replace(0, 0, "yeni\nyeni\n")
        assertEquals(42, state.firstVisibleLine)
        assertEquals("satır 41", state.lineText(state.firstVisibleLine))
        // the line at the top taken away: the place of the change comes to the top
        state.replace(state.lineStart(41), state.lineEnd(43) + 1, "")
        assertEquals(41, state.firstVisibleLine)
        state.scrollToLine(10_000)
        assertEquals(state.lineCount - 1, state.firstVisibleLine)
        state.setText("bir")
        assertEquals(0, state.firstVisibleLine)
    }

    @Test
    fun revealAndFocusWaitForAnEditor() {
        val state = CodeEditorState("bir\niki\nüç")
        state.revealLine(2, select = true, focus = true)
        assertEquals(TextRange(state.lineStart(2), state.lineEnd(2)), state.selection)
        assertEquals(TextRange(state.lineStart(2), state.lineEnd(2)), state.pendingReveal)
        assertTrue(state.pendingFocus)
    }
}
