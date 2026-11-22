package desktopkit.editor

import desktopkit.theme.SyntaxRole

/**
 * Colours a language for [CodeEditor], one line at a time. The editor calls [tokenizeLine] for each
 * line from the top of the document, handing every line the state the line before ended in, and
 * keeps the results: after an edit it tokenizes again only from the changed line down to the first
 * line that ends in the same state as before.
 *
 * So anything that reaches across lines (an open block comment, a multi-line string) is told through
 * the state, a number of the tokenizer's own choosing; 0 is the state at the start of the document.
 * A tokenizer must be a pure function of the line and the state it is given: no memory of its own.
 *
 * ```
 * val ini = LineTokenizer { line, state, tokens ->
 *     val text = line.trimStart()
 *     val indent = line.length - text.length
 *     when {
 *         text.startsWith(";") -> tokens.add(indent, line.length, SyntaxRole.Comment)
 *         text.startsWith("[") -> tokens.add(indent, line.length, SyntaxRole.Heading)
 *         "=" in line -> tokens.add(indent, line.indexOf('='), SyntaxRole.Name)
 *     }
 *     state
 * }
 * ```
 */
fun interface LineTokenizer {
    /**
     * Reports the tokens of [line] (one line, without its line break) to [tokens] and returns the
     * state at the line's end. [state] is what the previous line returned, 0 for the first line.
     */
    fun tokenizeLine(line: CharSequence, state: Int, tokens: TokenSink): Int
}

/** Where a [LineTokenizer] reports the tokens of the line it is given. */
class TokenSink internal constructor() {
    private var data = IntArray(48)
    private var size = 0
    private var lineLength = 0

    /**
     * The characters from [start] to [end] (exclusive, counted from the line's start) are [role];
     * a null role is plain text, the same as reporting nothing for them. Tokens are reported in
     * order and do not overlap; text between them is plain. A token that reaches outside the line is
     * cut to it, an empty or overlapping one is left out.
     */
    fun add(start: Int, end: Int, role: SyntaxRole?) {
        if (role == null) return
        val from = start.coerceAtLeast(if (size == 0) 0 else data[size - 2])
        val to = end.coerceAtMost(lineLength)
        if (to <= from) return
        if (size + 3 > data.size) data = data.copyOf(data.size * 2)
        data[size++] = from
        data[size++] = to
        data[size++] = role.ordinal
    }

    internal fun begin(length: Int) {
        size = 0
        lineLength = length
    }

    /** The reported tokens as `start, end, role` triples; [previous] itself when they are the same. */
    internal fun finish(previous: IntArray?): IntArray {
        if (size == 0) return TokenCache.NONE
        if (previous != null && previous.size == size) {
            var same = true
            for (i in 0 until size) if (previous[i] != data[i]) { same = false; break }
            if (same) return previous
        }
        return data.copyOf(size)
    }
}

/**
 * The tokens of every line of a document, kept between edits. Each line holds its tokens (relative
 * to the line's start, so lines moving up and down keep theirs) and the state it ended in.
 *
 * Lines are tokenized on demand, [ensure] up to the last line the editor is about to draw: opening
 * a long document costs nothing up front for the lines not in view ([frontier] is where the looking
 * stopped). After an edit the work starts at the first changed line and stops at the first line past
 * the edit whose end state is what it was, so a keystroke usually tokenizes one line; when the state
 * does not settle, it stops at the line asked for all the same. What is left either way is carried
 * on by [advance], in slices between frames.
 */
internal class TokenCache {
    private var tokens = arrayOfNulls<IntArray>(64)
    private var endStates = IntArray(64)
    private var count = 1
    private val sink = TokenSink()

    /** The first line never tokenized; the lines from here on are done when [ensure] reaches them. */
    private var frontier = 0

    /** Among the lines before [frontier]: the first whose tokens may be stale after an edit, or [CLEAN]. */
    private var dirtyFrom = CLEAN

    /** Behind this line an unchanged end state means the lines after it are still right. */
    private var dirtyTo = 0

    /** How many lines [ensure] tokenized since the cache was made; for tests and measurements. */
    var tokenizedLines = 0L
        private set

    var tokenizer: LineTokenizer? = null
        set(value) {
            if (field === value) return
            field = value
            invalidateAll()
        }

    /** Forgets everything: a new document of [lineCount] lines. */
    fun reset(lineCount: Int) {
        count = lineCount
        if (tokens.size < count) {
            tokens = arrayOfNulls(count + count / 2)
            endStates = IntArray(count + count / 2)
        }
        invalidateAll()
    }

    private fun invalidateAll() {
        tokens.fill(null)
        frontier = 0
        dirtyFrom = CLEAN
        dirtyTo = 0
    }

    /**
     * The line [line] was edited, the [removed] lines after it are gone and [added] new lines follow
     * it in their place.
     */
    fun linesReplaced(line: Int, removed: Int, added: Int) {
        val newCount = count - removed + added
        if (newCount > tokens.size) {
            tokens = tokens.copyOf(maxOf(newCount, tokens.size * 2))
            endStates = endStates.copyOf(tokens.size)
        }
        // the state the first line after the edit was tokenized from: if the edit ends in it again, that line and
        // those after it are still right (the edited line's own old state says so only when no line went)
        val carried = if (line + removed < frontier) endStates[line + removed] else UNKNOWN
        val tailFrom = line + 1 + removed
        val tail = count - tailFrom
        System.arraycopy(tokens, tailFrom, tokens, line + 1 + added, tail)
        System.arraycopy(endStates, tailFrom, endStates, line + 1 + added, tail)
        // the edited line keeps its old tokens until it is tokenized again, to tell an unchanged result
        for (i in line + 1..line + added) tokens[i] = null
        for (i in newCount until count) tokens[i] = null
        count = newCount
        if (line >= frontier) return
        if (frontier <= line + removed) {
            // the edit reaches into the lines never tokenized: they start at the edited line now
            frontier = line
            if (dirtyFrom != CLEAN && dirtyFrom >= frontier) dirtyFrom = CLEAN
            dirtyTo = minOf(dirtyTo, frontier)
            return
        }
        val shift = added - removed
        frontier += shift
        for (i in line until line + added) endStates[i] = UNKNOWN
        endStates[line + added] = carried
        val editEnd = line + added + 1
        if (dirtyFrom == CLEAN) {
            dirtyFrom = line
            dirtyTo = editEnd
        } else {
            if (dirtyFrom > line + removed) dirtyFrom += shift
            dirtyFrom = minOf(dirtyFrom, line)
            if (dirtyTo > line + removed) dirtyTo += shift
            dirtyTo = maxOf(dirtyTo, editEnd)
        }
    }

    /** Makes the tokens of every line up to [throughLine] current; [lineText] gives a line without its line break. */
    fun ensure(throughLine: Int, lineText: (Int) -> CharSequence) {
        val tokenizer = tokenizer ?: return
        val last = minOf(throughLine, count - 1)
        if (dirtyFrom != CLEAN) {
            var line = dirtyFrom
            var state = if (line == 0) 0 else endStates[line - 1]
            var settled = false
            while (line <= last && line < frontier) {
                val oldState = endStates[line]
                state = tokenize(tokenizer, line, state, lineText)
                line++
                if (line >= dirtyTo && oldState == state) {
                    settled = true
                    break
                }
            }
            if (settled || line >= frontier) {
                dirtyFrom = CLEAN
            } else {
                dirtyFrom = line
                dirtyTo = maxOf(dirtyTo, line + 1)
            }
        }
        if (frontier <= last) {
            var state = if (frontier == 0) 0 else endStates[frontier - 1]
            for (line in frontier..last) state = tokenize(tokenizer, line, state, lineText)
            frontier = last + 1
        }
    }

    /** Whether lines are left to do: stale after an edit, or never looked at. */
    val pending: Boolean get() = tokenizer != null && (dirtyFrom != CLEAN || frontier < count)

    /**
     * Carries the work on past what [ensure] was asked for, a few lines at a time until [deadlineNanos]
     * (of [System.nanoTime]); returns whether more is left. The editor calls it between frames: an
     * edit that changes the state of every line after it (a language whose state counts something)
     * must not make typing wait for the whole document, yet a jump to the end should find it done.
     */
    fun advance(deadlineNanos: Long, lineText: (Int) -> CharSequence): Boolean {
        while (pending) {
            val from = if (dirtyFrom != CLEAN) dirtyFrom else frontier
            ensure(from + SLICE_LINES - 1, lineText)
            if (System.nanoTime() >= deadlineNanos) break
        }
        return pending
    }

    private fun tokenize(tokenizer: LineTokenizer, line: Int, state: Int, lineText: (Int) -> CharSequence): Int {
        val text = lineText(line)
        sink.begin(text.length)
        val end = tokenizer.tokenizeLine(text, state, sink)
        tokens[line] = sink.finish(tokens[line])
        // UNKNOWN is the cache's own mark
        endStates[line] = if (end == UNKNOWN) UNKNOWN + 1 else end
        tokenizedLines++
        return endStates[line]
    }

    /** The tokens of [line] as `start, end, role` triples; [NONE] for a line with none or one not yet [ensure]d. */
    fun tokensOf(line: Int): IntArray =
        if (line in 0 until minOf(count, frontier) && (dirtyFrom == CLEAN || line < dirtyFrom)) tokens[line] ?: NONE else NONE

    /** The role at [column] of [line] as its ordinal, or -1 for plain text. */
    fun roleAt(line: Int, column: Int): Int {
        val lineTokens = tokensOf(line)
        var i = 0
        while (i < lineTokens.size) {
            if (column < lineTokens[i]) return -1
            if (column < lineTokens[i + 1]) return lineTokens[i + 2]
            i += 3
        }
        return -1
    }

    companion object {
        /** A line without tokens. */
        val NONE = IntArray(0)
        private const val CLEAN = Int.MAX_VALUE

        /** Lines tokenized between two looks at the clock in [advance]. */
        private const val SLICE_LINES = 16

        /** No state: a new line, never tokenized. A tokenizer's own states can be any other number. */
        private const val UNKNOWN = Int.MIN_VALUE
    }
}
