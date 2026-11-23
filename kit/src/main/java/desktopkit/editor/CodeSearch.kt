package desktopkit.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Find and replace in a [CodeEditor]: what is looked for, the matches and which of them is the current
 * one. Give it to the editor (`CodeEditor(state, search = search)`), which runs the search on its
 * document and marks every match, the current one stronger; show it with [CodeSearchBar] where the app
 * wants it, normally docked at the editor's top. One search serves the editor whatever document it
 * shows, as in desktop editors: the query stays when the user opens another file.
 *
 * [open] shows the bar with the selection as the query; typing in it selects the first match from where
 * the cursor was. [next] and [previous] step from the selection (Enter and Shift+Enter, F3 and Shift+F3),
 * [replaceCurrent] and [replaceAll] change the matches (all of them as one undo step), [close] hides the
 * bar and leaves the editor with the keyboard focus at the current match. The search runs off the main
 * thread and again after every edit; the matches move with the text in between.
 *
 * Matching ignores case unless [matchCase] (as Java does: "i" and "ı" both match "I"); [wholeWord]
 * matches only where no letter, digit or underscore touches the match; with [regex] the query is a Java
 * regular expression and the replacement may name its groups (`$1`, `${name}`; `\$` for a dollar sign).
 */
@Stable
class CodeSearchState {
    /** What is looked for. */
    var query: String by mutableStateOf("")

    /** What [replaceCurrent] and [replaceAll] put in a match's place. */
    var replacement: String by mutableStateOf("")
    var matchCase: Boolean by mutableStateOf(false)
    var wholeWord: Boolean by mutableStateOf(false)
    var regex: Boolean by mutableStateOf(false)

    /** Whether the bar shows (and the matches are marked). */
    var isOpen: Boolean by mutableStateOf(false)
        private set

    /** Whether the bar shows the replace row. */
    var replaceVisible: Boolean by mutableStateOf(false)

    /** True while [regex] is on and [query] is not a valid expression. */
    var invalidPattern: Boolean by mutableStateOf(false)
        private set

    /** The matches as `start, end` pairs in order, offsets into the document's text. */
    internal var matches: IntArray by mutableStateOf(NO_MATCHES)
        private set

    /** How many matches there are. */
    val matchCount: Int get() = matches.size / 2

    /** The index of the match that is selected in the editor, -1 when the selection is no match. */
    val currentMatch: Int by derivedStateOf {
        val selection = document?.selection
        if (selection == null || selection.collapsed) -1 else indexOf(matches, selection.min, selection.max)
    }

    /** Counts the requests to focus the find field ([open] while open focuses it again). */
    internal var focusRequests by mutableIntStateOf(0)
        private set

    /** The document the editor shows and searches. */
    internal var document: CodeEditorState? by mutableStateOf(null)
        private set

    /** Where incremental search starts: typing finds the first match from here. */
    private var origin = 0

    /** The query and options whose search finished last: [matches] are theirs. */
    private var searchedQuery: Any? by mutableStateOf(null)

    /**
     * Whether there is something to replace: the bar is open and the search of the query and options as they are
     * now has finished with matches. While a new query is still searched the matches are the old one's, and
     * [replaceCurrent] and [replaceAll] do nothing (the bar's buttons show it).
     */
    val canReplace: Boolean
        get() = isOpen && !invalidPattern && matches.isNotEmpty() && searchedQuery == Inputs(query, matchCase, wholeWord, regex, true, 0)
    private val moveWithEdits: (Int, Int, Int) -> Unit = { start, removed, inserted -> shift(start, removed, inserted) }

    /**
     * Shows the bar and puts the keyboard focus into its find field. A selection within one line becomes
     * the query (with [regex] on, as a literal expression); with [replace] the replace row shows too.
     */
    @JvmOverloads
    fun open(replace: Boolean = replaceVisible) {
        val document = document
        if (document != null) {
            val selected = document.selectedText
            if (selected.isNotEmpty() && '\n' !in selected && selected.length <= SEED_LIMIT) {
                query = if (regex) Pattern.quote(selected) else selected
            }
            origin = document.selection.min
        }
        replaceVisible = replace
        isOpen = true
        focusRequests++
    }

    /** Hides the bar; the editor takes the keyboard focus with the current match (if any) selected. */
    fun close() {
        if (!isOpen) return
        isOpen = false
        document?.requestFocus()
    }

    /** Selects the first match after the selection (or the cursor), from the top again after the last. */
    fun next() = step(forward = true)

    /** Selects the last match before the selection (or the cursor), from the end again before the first. */
    fun previous() = step(forward = false)

    private fun step(forward: Boolean) {
        val document = document ?: return
        val found = matches
        if (found.isEmpty()) return
        val selection = document.selection
        val count = found.size / 2
        // from a selected match, its end (or start) is where the next (or previous) one is looked for
        val index = if (forward) {
            firstStartingAtOrAfter(found, selection.max).let { if (it == count) 0 else it }
        } else {
            lastEndingAtOrBefore(found, selection.min).let { if (it < 0) count - 1 else it }
        }
        select(document, index)
    }

    private fun select(document: CodeEditorState, index: Int) {
        val start = matches[index * 2]
        val end = matches[index * 2 + 1]
        origin = start
        document.reveal(start, end, select = true)
    }

    /**
     * Replaces the current match and selects the next one. Without a current match it selects the next
     * match first, as desktop editors do: the user sees what will be replaced. Nothing while the query's
     * search has not finished ([canReplace]).
     */
    fun replaceCurrent() {
        val document = document ?: return
        if (!canReplace) return
        val index = currentMatch
        if (index < 0) {
            next()
            return
        }
        val pattern = compile() ?: return
        val start = matches[index * 2]
        val end = matches[index * 2 + 1]
        val replaced = expandedReplacement(document.content, pattern, start, end, replacement, regex) ?: return
        document.replace(start, end, replaced)
        // the replaced match left the list (it moved with the edit); the one after it is next
        val after = firstStartingAtOrAfter(matches, start + replaced.length)
        if (matches.isNotEmpty()) select(document, if (after == matchCount) 0 else after) else document.select(start + replaced.length)
    }

    /** Replaces every match, as one step of the undo history; nothing while the query's search has not finished ([canReplace]). */
    fun replaceAll() {
        val document = document ?: return
        if (!canReplace) return
        val pattern = compile() ?: return
        val text = document.text
        // null: no match, or an expression too complex for this text (the bar already says so)
        val result = replaceAllIn(text, pattern, replacement, regex) ?: return
        val cursor = document.cursor
        document.edit {
            document.replace(result.start, result.end, result.text)
            document.select(result.mapOffset(cursor))
        }
    }

    // ------------------------------------------------------------------ the editor's side

    internal fun attach(document: CodeEditorState) {
        this.document?.editObservers?.remove(moveWithEdits)
        this.document = document
        document.editObservers += moveWithEdits
        origin = document.selection.min
    }

    internal fun detach(document: CodeEditorState) {
        document.editObservers -= moveWithEdits
        if (this.document === document) this.document = null
    }

    /**
     * Searches [document] whenever the query, an option or the text changes, until cancelled. A new query
     * selects its first match from [origin]; an edit only moves the matches.
     */
    internal suspend fun run(document: CodeEditorState) {
        snapshotFlow { Inputs(query, matchCase, wholeWord, regex, isOpen, document.version) }
            .collectLatest { inputs ->
                if (!inputs.open || inputs.query.isEmpty()) {
                    invalidPattern = false
                    matches = NO_MATCHES
                    searchedQuery = null
                    return@collectLatest
                }
                val pattern = compile()
                invalidPattern = pattern == null
                if (pattern == null) {
                    matches = NO_MATCHES
                    return@collectLatest
                }
                val key = inputs.copy(version = 0)
                val edited = key == searchedQuery
                // a long document is searched again once the typing pauses; the matches move with the text meanwhile
                if (edited && document.length > LARGE_DOCUMENT) delay(EDIT_PAUSE_MS)
                val text = document.text
                val found = try {
                    withContext(Dispatchers.Default) { findMatches(text, pattern, coroutineContext) }
                } catch (_: TooComplexException) {
                    // an expression Java's matcher cannot follow through this text: said as an invalid one
                    invalidPattern = true
                    matches = NO_MATCHES
                    return@collectLatest
                }
                matches = found
                searchedQuery = key
                if (!edited && found.isNotEmpty()) {
                    val index = firstStartingAtOrAfter(found, origin)
                    select(document, if (index * 2 == found.size) 0 else index)
                }
            }
    }

    private data class Inputs(val query: String, val matchCase: Boolean, val wholeWord: Boolean, val regex: Boolean, val open: Boolean, val version: Int)

    private fun compile(): Pattern? = compileSearch(query, matchCase, wholeWord, regex)

    /** The document changed: the matches before the change stay, the ones after it move, the ones it touched go. */
    private fun shift(start: Int, removed: Int, inserted: Int) {
        val current = matches
        if (current.isEmpty()) return
        matches = shiftMatches(current, start, removed, inserted)
    }

    internal companion object {
        val NO_MATCHES = IntArray(0)

        /** A selection longer than this does not become the query. */
        const val SEED_LIMIT = 200

        /** More characters than this and an edit waits for a pause before the search runs again. */
        const val LARGE_DOCUMENT = 100_000
        const val EDIT_PAUSE_MS = 150L

        /** At most this many matches are marked. */
        const val MAX_MATCHES = 100_000
    }
}

/** A [CodeSearchState] remembered in the composition: the query and options stay while the screen does. */
@Composable
fun rememberCodeSearchState(): CodeSearchState = remember { CodeSearchState() }

// ---------------------------------------------------------------------- matching, pure functions

/** The pattern for [query] and its options; null when [regex] is on and the query is not a valid expression. */
internal fun compileSearch(query: String, matchCase: Boolean, wholeWord: Boolean, regex: Boolean): Pattern? {
    if (query.isEmpty()) return null
    var source = if (regex) query else Pattern.quote(query)
    // a word is letters, digits and the underscore, in any script
    if (wholeWord) source = "(?<![\\p{L}\\p{N}_])(?:$source)(?![\\p{L}\\p{N}_])"
    var flags = Pattern.MULTILINE
    if (!matchCase) flags = flags or Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
    return try {
        Pattern.compile(source, flags)
    } catch (_: PatternSyntaxException) {
        null
    }
}

/**
 * The matches of [pattern] in [text] as `start, end` pairs, empty matches left out, at most
 * [CodeSearchState.MAX_MATCHES]. Stops with a [CancellationException] when [context] is cancelled, also
 * in the middle of an expression that takes long (the text checks it while it is read).
 */
internal fun findMatches(text: CharSequence, pattern: Pattern, context: CoroutineContext? = null): IntArray {
    val matcher = pattern.matcher(if (context == null) text else CancellableText(text, context))
    var out = IntArray(64)
    var size = 0
    tooComplexIfDeep {
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            if (end == start) continue
            if (size + 2 > out.size) out = out.copyOf(out.size * 2)
            out[size++] = start
            out[size++] = end
            if (size / 2 >= CodeSearchState.MAX_MATCHES) break
        }
    }
    return out.copyOf(size)
}

/** An expression whose matching recursed deeper than the stack allows (`(a|aa)+` over a long run of a's). */
internal class TooComplexException : RuntimeException("the expression is too complex for this text")

/** Runs [block], turning a stack overflow of the regular expression matcher into a [TooComplexException]. */
private inline fun <T> tooComplexIfDeep(block: () -> T): T = try {
    block()
} catch (_: StackOverflowError) {
    throw TooComplexException()
}

/** A text that checks [context] for cancellation while a matcher reads it. */
private class CancellableText(private val text: CharSequence, private val context: CoroutineContext) : CharSequence {
    private var reads = 0
    override val length: Int get() = text.length

    override fun get(index: Int): Char {
        if (++reads and 0xFFFF == 0) context.ensureActive()
        return text[index]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = text.subSequence(startIndex, endIndex)
    override fun toString(): String = text.toString()
}

/** What the match from [start] to [end] is replaced with: [replacement] itself, or with [regex] its groups filled in. */
internal fun expandedReplacement(text: CharSequence, pattern: Pattern, start: Int, end: Int, replacement: String, regex: Boolean): String? {
    if (!regex) return replacement
    val matcher = pattern.matcher(text)
    val found = try {
        tooComplexIfDeep { matcher.find(start) }
    } catch (_: TooComplexException) {
        false
    }
    if (!found || matcher.start() != start || matcher.end() != end) return null
    val out = StringBuilder()
    // a fresh matcher copies the text before the match first
    appendReplacement(matcher, out, replacement)
    return out.substring(start)
}

/**
 * [Matcher.appendReplacement] with [replacement]'s groups filled in; a replacement that names a group the
 * expression does not have is put in as it is written.
 */
private fun appendReplacement(matcher: Matcher, out: StringBuilder, replacement: String) {
    val length = out.length
    try {
        matcher.appendReplacement(out, replacement)
    } catch (_: IllegalArgumentException) {
        out.setLength(length)
        matcher.appendReplacement(out, Matcher.quoteReplacement(replacement))
    } catch (_: IndexOutOfBoundsException) {
        out.setLength(length)
        matcher.appendReplacement(out, Matcher.quoteReplacement(replacement))
    }
}

/** A replace-all as one replacement: the text from [start] to [end] gives way to [text]; [mapOffset] moves an offset along. */
internal class ReplaceAllResult(val start: Int, val end: Int, val text: String, private val starts: IntArray, private val ends: IntArray, private val lengths: IntArray) {
    /** Where [offset] of the old text is in the new: moved by the replacements before it; inside a match, after its replacement. */
    fun mapOffset(offset: Int): Int {
        var delta = 0
        for (i in starts.indices) {
            if (ends[i] <= offset) {
                delta += lengths[i] - (ends[i] - starts[i])
            } else {
                if (starts[i] < offset) return starts[i] + delta + lengths[i]
                break
            }
        }
        return offset + delta
    }
}

/** Every match of [pattern] in [text] replaced, as one replacement of the span from the first match to the last; null without a match. */
internal fun replaceAllIn(text: String, pattern: Pattern, replacement: String, regex: Boolean): ReplaceAllResult? {
    val matcher = pattern.matcher(text)
    val literal = if (regex) replacement else Matcher.quoteReplacement(replacement)
    val starts = ArrayList<Int>()
    val ends = ArrayList<Int>()
    val lengths = ArrayList<Int>()
    val out = StringBuilder()
    var lastEnd = 0
    while (try { tooComplexIfDeep { matcher.find() } } catch (_: TooComplexException) { return null }) {
        val start = matcher.start()
        val end = matcher.end()
        if (end == start) continue
        val before = out.length
        // copies the text since the last match, then the replacement
        appendReplacement(matcher, out, literal)
        starts += start
        ends += end
        lengths += out.length - before - (start - lastEnd)
        lastEnd = end
    }
    if (starts.isEmpty()) return null
    val first = starts[0]
    return ReplaceAllResult(first, lastEnd, out.substring(first), starts.toIntArray(), ends.toIntArray(), lengths.toIntArray())
}

/** [matches] after the text from [start] gave [removed] characters' place to [inserted]: the ones the change touched go. */
internal fun shiftMatches(matches: IntArray, start: Int, removed: Int, inserted: Int): IntArray {
    val end = start + removed
    val delta = inserted - removed
    val out = IntArray(matches.size)
    var size = 0
    var i = 0
    while (i < matches.size) {
        val matchStart = matches[i]
        val matchEnd = matches[i + 1]
        if (matchEnd <= start) {
            out[size++] = matchStart
            out[size++] = matchEnd
        } else if (matchStart >= end) {
            out[size++] = matchStart + delta
            out[size++] = matchEnd + delta
        }
        i += 2
    }
    return if (size == matches.size) out else out.copyOf(size)
}

/** The index of the match from [start] to [end], or -1. */
internal fun indexOf(matches: IntArray, start: Int, end: Int): Int {
    val index = firstStartingAtOrAfter(matches, start)
    return if (index * 2 < matches.size && matches[index * 2] == start && matches[index * 2 + 1] == end) index else -1
}

/** The index of the first match that starts at [offset] or after it; the count of matches when none does. */
internal fun firstStartingAtOrAfter(matches: IntArray, offset: Int): Int {
    var low = 0
    var high = matches.size / 2
    while (low < high) {
        val mid = (low + high) ushr 1
        if (matches[mid * 2] < offset) low = mid + 1 else high = mid
    }
    return low
}

/** The index of the last match that ends at [offset] or before it; -1 when none does. */
internal fun lastEndingAtOrBefore(matches: IntArray, offset: Int): Int {
    var low = 0
    var high = matches.size / 2
    while (low < high) {
        val mid = (low + high) ushr 1
        if (matches[mid * 2 + 1] <= offset) low = mid + 1 else high = mid
    }
    return low - 1
}
