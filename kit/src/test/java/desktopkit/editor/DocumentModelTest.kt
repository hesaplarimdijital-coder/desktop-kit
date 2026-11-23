package desktopkit.editor

import desktopkit.theme.SyntaxRole
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The document's line index, token cache and undo history: plain logic, no Android. */
class DocumentModelTest {

    // ---------------------------------------------------------------- line index

    private fun starts(text: CharSequence): List<Int> = listOf(0) + text.indices.filter { text[it] == '\n' }.map { it + 1 }

    private fun LineIndex.starts(): List<Int> = (0 until count).map { start(it) }

    @Test
    fun lineIndexCountsLines() {
        val index = LineIndex()
        index.reset("")
        assertEquals(1, index.count)
        assertEquals(0, index.end(0))
        index.reset("bir\niki\n")
        assertEquals(3, index.count)
        assertEquals(listOf(0, 4, 8), index.starts())
        assertEquals(3, index.end(0))
        assertEquals(7, index.end(1))
        assertEquals(8, index.end(2))
        assertEquals(0, index.lineOf(3))
        assertEquals(1, index.lineOf(4))
        assertEquals(2, index.lineOf(8))
    }

    @Test
    fun lineIndexFollowsRandomEdits() {
        val random = Random(42)
        val text = StringBuilder("ilk satır\nikinci\n\nson")
        val index = LineIndex().apply { reset(text) }
        repeat(3000) {
            val start = random.nextInt(text.length + 1)
            val end = (start + random.nextInt(12)).coerceAtMost(text.length)
            val inserted = buildString { repeat(random.nextInt(10)) { append(if (random.nextInt(4) == 0) '\n' else 'a' + random.nextInt(26)) } }
            val linesBefore = index.count
            val packed = index.replace(start, end, inserted)
            text.replace(start, end, inserted)
            assertEquals(starts(text), index.starts())
            assertEquals(text.length, index.length)
            assertEquals(index.count, linesBefore - LineIndex.removedLines(packed) + LineIndex.addedLines(packed))
        }
    }

    // ---------------------------------------------------------------- tokens

    /** Words are names, a block comment reaches over lines through the state. */
    private val blockComments = LineTokenizer { line, state, tokens ->
        var inComment = state == 1
        var i = 0
        while (i < line.length) {
            if (inComment) {
                val end = line.indexOf("*/", i)
                if (end < 0) {
                    tokens.add(i, line.length, SyntaxRole.Comment)
                    return@LineTokenizer 1
                }
                tokens.add(i, end + 2, SyntaxRole.Comment)
                i = end + 2
                inComment = false
            } else if (line.startsWith("/*", i)) {
                inComment = true
                val end = line.indexOf("*/", i + 2)
                if (end < 0) {
                    tokens.add(i, line.length, SyntaxRole.Comment)
                    return@LineTokenizer 1
                }
                tokens.add(i, end + 2, SyntaxRole.Comment)
                i = end + 2
                inComment = false
            } else if (line[i].isLetter()) {
                var end = i
                while (end < line.length && line[end].isLetter()) end++
                tokens.add(i, end, SyntaxRole.Name)
                i = end
            } else {
                i++
            }
        }
        if (inComment) 1 else 0
    }

    private fun lineTexts(text: CharSequence): List<String> = text.split('\n')

    private fun allTokens(cache: TokenCache, text: CharSequence): List<List<Int>> {
        val lines = lineTexts(text)
        cache.ensure(lines.size - 1) { lines[it] }
        return lines.indices.map { cache.tokensOf(it).toList() }
    }

    private fun fresh(text: CharSequence): List<List<Int>> = allTokens(TokenCache().apply { tokenizer = blockComments; reset(lineTexts(text).size) }, text)

    @Test
    fun tokensComeOutAsTriplesRelativeToTheLine() {
        val cache = TokenCache().apply { tokenizer = blockComments; reset(2) }
        val lines = listOf("ab /* x", "y */ cd")
        cache.ensure(1) { lines[it] }
        assertArrayEquals(intArrayOf(0, 2, SyntaxRole.Name.ordinal, 3, 7, SyntaxRole.Comment.ordinal), cache.tokensOf(0))
        assertArrayEquals(intArrayOf(0, 4, SyntaxRole.Comment.ordinal, 5, 7, SyntaxRole.Name.ordinal), cache.tokensOf(1))
        assertEquals(SyntaxRole.Comment.ordinal, cache.roleAt(1, 2))
        assertEquals(-1, cache.roleAt(1, 4))
    }

    @Test
    fun aKeystrokeTokenizesOneLine() {
        val text = StringBuilder((0 until 2000).joinToString("\n") { "satir $it" })
        val index = LineIndex().apply { reset(text) }
        val cache = TokenCache().apply { tokenizer = blockComments; reset(index.count) }
        val line = { i: Int -> text.subSequence(index.start(i), index.end(i)) }
        cache.ensure(index.count - 1, line)
        val before = cache.tokenizedLines
        assertEquals(2000, before)
        // a letter typed in line 1000
        val at = index.start(1000) + 2
        val packed = index.replace(at, at, "x")
        text.insert(at, "x")
        cache.linesReplaced(index.lineOf(at), LineIndex.removedLines(packed), LineIndex.addedLines(packed))
        cache.ensure(index.count - 1, line)
        assertEquals(1, cache.tokenizedLines - before)
    }

    @Test
    fun onlyTheLinesLookedAtAreTokenized() {
        val text = (0 until 5000).joinToString("\n") { "satir $it" }
        val lines = lineTexts(text)
        val cache = TokenCache().apply { tokenizer = blockComments; reset(lines.size) }
        cache.ensure(60) { lines[it] }
        assertEquals(61, cache.tokenizedLines)
        assertTrue(cache.tokensOf(60).isNotEmpty())
        // a line never asked for has none yet
        assertSame(TokenCache.NONE, cache.tokensOf(61))
    }

    @Test
    fun anOpenedCommentRunsOnUntilItSettles() {
        val text = StringBuilder((0 until 100).joinToString("\n") { if (it == 50) "son */ kelime" else "satir" })
        val index = LineIndex().apply { reset(text) }
        val cache = TokenCache().apply { tokenizer = blockComments; reset(index.count) }
        val line = { i: Int -> text.subSequence(index.start(i), index.end(i)) }
        cache.ensure(99, line)
        val before = cache.tokenizedLines
        // "/*" typed at the start of line 10: lines 10 to 50 turn into a comment, line 51 ends as it did
        val at = index.start(10)
        val packed = index.replace(at, at, "/*")
        text.insert(at, "/*")
        cache.linesReplaced(10, LineIndex.removedLines(packed), LineIndex.addedLines(packed))
        cache.ensure(99, line)
        assertEquals(41, cache.tokenizedLines - before)
        assertEquals(SyntaxRole.Comment.ordinal, cache.roleAt(30, 1))
        assertEquals(SyntaxRole.Name.ordinal, cache.roleAt(60, 1))
        assertEquals(fresh(text), (0 until index.count).map { cache.tokensOf(it).toList() })
    }

    @Test
    fun aRemovedLineThatOpenedACommentUncolorsWhatFollowed() {
        // the edited line ends as before, but the line after it no longer starts inside a comment
        val text = StringBuilder("bir\n/* acik\niki\nuc */ dort")
        val index = LineIndex().apply { reset(text) }
        val cache = TokenCache().apply { tokenizer = blockComments; reset(index.count) }
        val line = { i: Int -> text.subSequence(index.start(i), index.end(i)) }
        cache.ensure(3, line)
        assertEquals(SyntaxRole.Comment.ordinal, cache.roleAt(2, 0))
        val start = index.end(0)
        val end = index.end(1)
        val packed = index.replace(start, end, "")
        text.delete(start, end)
        cache.linesReplaced(0, LineIndex.removedLines(packed), LineIndex.addedLines(packed))
        cache.ensure(index.count - 1, line)
        assertEquals(SyntaxRole.Name.ordinal, cache.roleAt(1, 0))
        assertEquals(fresh(text), (0 until index.count).map { cache.tokensOf(it).toList() })
    }

    @Test
    fun theCacheMatchesAFreshTokenizerAfterRandomEdits() {
        val random = Random(7)
        val pieces = listOf("kelime", " ", "\n", "/*", "*/", "x", "\n\n", " baska ")
        val text = StringBuilder("bas /* yorum\nicinde */ son\nduz satir")
        val index = LineIndex().apply { reset(text) }
        val cache = TokenCache().apply { tokenizer = blockComments; reset(index.count) }
        val line = { i: Int -> text.subSequence(index.start(i), index.end(i)) }
        repeat(1500) { round ->
            val start = random.nextInt(text.length + 1)
            val end = (start + random.nextInt(8) * random.nextInt(2)).coerceAtMost(text.length)
            val inserted = if (random.nextInt(5) == 0) "" else pieces[random.nextInt(pieces.size)]
            val packed = index.replace(start, end, inserted)
            text.replace(start, end, inserted)
            cache.linesReplaced(index.lineOf(start), LineIndex.removedLines(packed), LineIndex.addedLines(packed))
            // sometimes only the top of the document is looked at, as when the view is scrolled up
            val through = if (round % 3 == 0) index.count / 2 else index.count - 1
            cache.ensure(through, line)
            val expected = fresh(text)
            for (i in 0..through) assertEquals("round $round line $i of\n$text", expected[i], cache.tokensOf(i).toList())
        }
    }

    /** As in a mode whose state counts something: every line starting with "*" leaves the state one deeper for good. */
    private val counting = LineTokenizer { line, state, tokens ->
        if (line.startsWith("*")) {
            tokens.add(0, 1, SyntaxRole.Keyword)
            state + 1
        } else {
            if (state % 2 == 1) tokens.add(0, line.length, SyntaxRole.String)
            state
        }
    }

    @Test
    fun anEditThatChangesEveryLaterStateStopsAtTheViewAndIsCarriedOnInSlices() {
        val text = StringBuilder((0 until 5000).joinToString("\n") { "satir $it" })
        val index = LineIndex().apply { reset(text) }
        val cache = TokenCache().apply { tokenizer = counting; reset(index.count) }
        val line = { i: Int -> text.subSequence(index.start(i), index.end(i)) }
        // the whole document was tokenized between frames after it was opened
        assertTrue(cache.pending)
        while (cache.advance(Long.MAX_VALUE, line)) continue
        assertFalse(cache.pending)
        assertEquals(5000, cache.tokenizedLines)
        // a "*" line typed at the top: every later line now ends one deeper
        val packed = index.replace(0, 0, "* secim\n")
        text.insert(0, "* secim\n")
        cache.linesReplaced(0, LineIndex.removedLines(packed), LineIndex.addedLines(packed))
        val before = cache.tokenizedLines
        // the keystroke pays for the lines down to the view's end only
        cache.ensure(60, line)
        assertEquals(61, cache.tokenizedLines - before)
        assertEquals(SyntaxRole.String.ordinal, cache.roleAt(60, 2))
        // a line beyond is not shown in its old colours: it has none until it is done
        assertSame(TokenCache.NONE, cache.tokensOf(61))
        assertTrue(cache.pending)
        // a slice whose time is up does a few lines and says there is more
        assertTrue(cache.advance(0L, line))
        assertTrue(cache.tokenizedLines - before in 62..100)
        while (cache.advance(Long.MAX_VALUE, line)) continue
        assertEquals(5001, cache.tokenizedLines - before)
        assertEquals(SyntaxRole.String.ordinal, cache.roleAt(5000, 2))
        assertFalse(cache.pending)
    }

    @Test
    fun aSinkCutsTokensToTheLineAndDropsOverlaps() {
        val odd = LineTokenizer { line, _, tokens ->
            tokens.add(-3, 2, SyntaxRole.Keyword)
            tokens.add(1, 4, SyntaxRole.String)
            tokens.add(4, 4, SyntaxRole.Number)
            tokens.add(5, 99, SyntaxRole.Comment)
            line.length
        }
        val cache = TokenCache().apply { tokenizer = odd; reset(1) }
        cache.ensure(0) { "0123456" }
        assertArrayEquals(intArrayOf(0, 2, SyntaxRole.Keyword.ordinal, 2, 4, SyntaxRole.String.ordinal, 5, 7, SyntaxRole.Comment.ordinal), cache.tokensOf(0))
    }

    @Test
    fun aTokenWithoutARoleIsPlainText() {
        // a port of another editor's tokenizer reports every token, some of them with no colour of their own
        val plain = LineTokenizer { _, state, tokens ->
            tokens.add(0, 2, null)
            tokens.add(2, 4, SyntaxRole.Keyword)
            tokens.add(4, 6, null)
            state
        }
        val cache = TokenCache().apply { tokenizer = plain; reset(1) }
        cache.ensure(0) { "abcdef" }
        assertArrayEquals(intArrayOf(2, 4, SyntaxRole.Keyword.ordinal), cache.tokensOf(0))
        assertEquals(-1, cache.roleAt(0, 0))
        assertEquals(-1, cache.roleAt(0, 5))
    }

    @Test
    fun anUnchangedLineKeepsItsTokensObject() {
        // the editor tells by the object that a line's spans are still right
        val text = StringBuilder("bir iki\nuc")
        val index = LineIndex().apply { reset(text) }
        val cache = TokenCache().apply { tokenizer = blockComments; reset(index.count) }
        val line = { i: Int -> text.subSequence(index.start(i), index.end(i)) }
        cache.ensure(1, line)
        val first = cache.tokensOf(0)
        val second = cache.tokensOf(1)
        val packed = index.replace(7, 7, " ")
        text.insert(7, " ")
        cache.linesReplaced(0, LineIndex.removedLines(packed), LineIndex.addedLines(packed))
        cache.ensure(1, line)
        assertSame(first, cache.tokensOf(0))
        assertSame(second, cache.tokensOf(1))
    }

    // ---------------------------------------------------------------- history

    private class Clock(var now: Long = 0L)

    private fun EditHistory.type(offset: Int, text: String, removed: String = "") = record(offset, removed, text, 0L, 0L, typed = true)

    @Test
    fun typingAWordIsOneStepAndTheNextWordAnother() {
        val clock = Clock()
        val history = EditHistory { clock.now }
        "merhaba ".forEachIndexed { i, c -> history.type(i, c.toString()) }
        "dünya".forEachIndexed { i, c -> history.type(8 + i, c.toString()) }
        val second = history.undo()!!
        assertEquals("dünya", second.changes.single().inserted)
        val first = history.undo()!!
        assertEquals("merhaba ", first.changes.single().inserted)
        assertNull(history.undo())
    }

    @Test
    fun aPauseOrALineBreakStartsANewStep() {
        val clock = Clock()
        val history = EditHistory { clock.now }
        history.type(0, "a")
        clock.now += EditHistory.TYPING_PAUSE_MS + 1
        history.type(1, "b")
        history.type(2, "\n")
        history.type(3, "c")
        assertEquals("c", history.undo()!!.changes.single().inserted)
        assertEquals("\n", history.undo()!!.changes.single().inserted)
        assertEquals("b", history.undo()!!.changes.single().inserted)
        assertEquals("a", history.undo()!!.changes.single().inserted)
    }

    @Test
    fun theKeyboardsCorrectionsOfAComposedWordFoldIntoIt() {
        val history = EditHistory { 0L }
        // "m", "me" and "mer" as a keyboard composes them, then its suggestion replaces the word
        history.type(4, "m")
        history.type(4, "me", removed = "m")
        history.type(4, "mer", removed = "me")
        history.type(4, "merhaba ", removed = "mer")
        val entry = history.undo()!!
        assertEquals(4, entry.changes.single().offset)
        assertEquals("", entry.changes.single().removed)
        assertEquals("merhaba ", entry.changes.single().inserted)
        assertFalse(history.canUndo)
    }

    @Test
    fun backspacesAndDeletesGather() {
        val history = EditHistory { 0L }
        history.type(5, "", removed = "c")
        history.type(4, "", removed = "b")
        history.type(3, "", removed = "a")
        val backspaced = history.undo()!!.changes.single()
        assertEquals(3, backspaced.offset)
        assertEquals("abc", backspaced.removed)
        history.type(3, "", removed = "x")
        history.type(3, "", removed = "y")
        assertEquals("xy", history.undo()!!.changes.single().removed)
    }

    @Test
    fun programmaticEditsStandAloneAndAGroupIsOneStep() {
        val history = EditHistory { 0L }
        history.record(0, "", "a", 0L, 0L, typed = false)
        history.record(1, "", "b", 0L, 0L, typed = false)
        history.beginGroup(0L)
        history.record(0, "a", "x", 0L, 0L, typed = false)
        history.record(1, "b", "y", 0L, 0L, typed = false)
        history.endGroup(0L)
        assertEquals(2, history.undo()!!.changes.size)
        assertEquals("b", history.undo()!!.changes.single().inserted)
        assertTrue(history.canRedo)
        // a new edit forgets what was taken back
        history.record(1, "", "c", 0L, 0L, typed = false)
        assertFalse(history.canRedo)
    }
}
