package desktopkit.editor

import desktopkit.theme.SyntaxRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How the document model holds up at the size the editor promises (5,000 lines, 300,000 characters):
 * what a keystroke costs the line index, the token cache and the history, and what an edit that changes
 * the state of every later line costs. The work is asserted (lines tokenized, slices); the times are
 * printed for the record and bounded only loosely, against a regression by orders of magnitude.
 */
class DocumentSizeTest {

    private fun measure(repeat: Int, block: (Int) -> Unit): Double {
        // a few rounds first, so the JIT has compiled what is measured
        repeat(minOf(repeat, 200)) { block(it) }
        val start = System.nanoTime()
        repeat(repeat) { block(it) }
        return (System.nanoTime() - start) / 1000.0 / repeat
    }

    private fun report(what: String, value: Double, unit: String) = println("DocumentSizeTest: %-58s %10.1f %s".format(what, value, unit))

    @Test
    fun aKeystrokeCostsTheSameInALongDocument() {
        val text = StringBuilder(largeScript())
        assertEquals(5000, text.count { it == '\n' } + 1)
        assertTrue("about 300,000 characters", text.length in 300_000..360_000)
        val index = LineIndex()
        val resetMicros = measure(20) { index.reset(text) }
        report("line index of the whole document", resetMicros / 1000, "ms")

        val cache = TokenCache().apply { tokenizer = sampleTokenizer; reset(index.count) }
        val line = { i: Int -> text.subSequence(index.start(i), index.end(i)) }
        val start = System.nanoTime()
        cache.ensure(index.count - 1, line)
        val allMillis = (System.nanoTime() - start) / 1e6
        report("tokenizing all 5,000 lines (cold)", allMillis, "ms")

        // a letter typed and taken back in the middle, over and over: index, cache and history
        val history = EditHistory()
        var tokenized = 0L
        val keystroke = measure(2000) { round ->
            val at = index.start(2500) + 10
            val before = cache.tokenizedLines
            if (round % 2 == 0) {
                val packed = index.replace(at, at, "x")
                text.insert(at, "x")
                cache.linesReplaced(index.lineOf(at), LineIndex.removedLines(packed), LineIndex.addedLines(packed))
                history.record(at, "", "x", 0L, 0L, typed = true)
            } else {
                val packed = index.replace(at, at + 1, "")
                text.delete(at, at + 1)
                cache.linesReplaced(index.lineOf(at), LineIndex.removedLines(packed), LineIndex.addedLines(packed))
                history.record(at, "x", "", 0L, 0L, typed = true)
            }
            // what the editor asks for: the lines down to the end of a 60-line view and its margin
            cache.ensure(2500 + 100, line)
            tokenized += cache.tokenizedLines - before
        }
        report("one keystroke in the middle (index, tokens, history)", keystroke, "µs")
        assertEquals("a keystroke tokenizes its own line only", 2200L, tokenized)
        assertTrue("a keystroke stays far below a frame ($keystroke µs)", keystroke < 4_000)
    }

    @Test
    fun anEditThatChangesEveryLaterStatePaysForTheViewOnly() {
        val text = StringBuilder(largeScript())
        val index = LineIndex().apply { reset(text) }
        val cache = TokenCache().apply { tokenizer = nesting; reset(index.count) }
        val line = { i: Int -> text.subSequence(index.start(i), index.end(i)) }
        while (cache.advance(Long.MAX_VALUE, line)) continue
        val choice = "* [Yeni seçenek]\n"
        val atOnce = ArrayList<Long>()
        val slices = ArrayList<Int>()
        var longestSlice = 0L
        repeat(ROUNDS) { round ->
            // a choice line typed at the top of a view showing lines 0 to 60, then taken away again: either way
            // every later line ends in another state than before
            val packed = if (round % 2 == 0) {
                text.insert(0, choice)
                index.replace(0, 0, choice)
            } else {
                text.delete(0, choice.length)
                index.replace(0, choice.length, "")
            }
            cache.linesReplaced(0, LineIndex.removedLines(packed), LineIndex.addedLines(packed))
            val before = cache.tokenizedLines
            val start = System.nanoTime()
            cache.ensure(60 + 40, line)
            atOnce += System.nanoTime() - start
            assertEquals("the keystroke pays for the view and its margin", 101L, cache.tokenizedLines - before)
            // the rest in slices of 4 ms, as the editor does between frames
            var count = 0
            do {
                val sliceStart = System.nanoTime()
                val more = cache.advance(sliceStart + 4_000_000L, line)
                longestSlice = maxOf(longestSlice, System.nanoTime() - sliceStart)
                count++
            } while (more)
            slices += count
            assertEquals("every line is done in the end", index.count.toLong(), cache.tokenizedLines - before)
        }
        atOnce.sort()
        report("state-changing edit: lines 0-100 at once (median)", atOnce[atOnce.size / 2] / 1000.0, "µs")
        report("state-changing edit: lines 0-100 at once (fastest)", atOnce.first() / 1000.0, "µs")
        report("state-changing edit: slices for the other ~4,900 lines (median)", slices.sorted()[slices.size / 2].toDouble(), "")
        report("state-changing edit: the longest slice (budget 4 ms)", longestSlice / 1e6, "ms")
    }

    /** As Ace's ink mode: a choice line with brackets leaves the state one deeper for every line after it. */
    private val nesting = LineTokenizer { line, state, tokens ->
        val depth = if (line.startsWith("* [")) state + 1 else state
        sampleTokenizer.tokenizeLine(line, 0, tokens)
        depth
    }

    companion object {
        private const val ROUNDS = 12
        private val prose = listOf(
            "Sabah erkenden bahçeye indim. Masaların üstü hâlâ ıslaktı; garson çocuk bezle siliyor, bir yandan da radyodaki türküye eşlik ediyordu.",
            "Kıyıdan gelen rüzgâr tuz kokuyordu, uzaktan bir motorun sesi duyuluyordu. # sabah",
            "* \"Bir çay alabilir miyim?\" -> cay_soyle",
            "+ { cay > 0 } Bardağı bitirdim.",
            "    ~ cay = cay - 1",
            "== bahce ==",
            "VAR sehir = \"Kemer\" // nereye gidildiği",
            "",
        )

        /** 5,000 lines of a story script in the sample language, about 330,000 characters. */
        fun largeScript(): String = buildString {
            for (i in 0 until 5000) {
                if (i > 0) append('\n')
                val template = prose[i % prose.size]
                append(template)
                // long paragraphs, as a story is written
                if (i % prose.size < 2) append(" Satır ").append(i).append(": dalgalar taşlara vuruyor, martılar iskelenin direklerine konuyordu; kimse konuşmuyordu.")
            }
        }
    }
}
