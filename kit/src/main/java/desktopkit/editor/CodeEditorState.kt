package desktopkit.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange

/** A place in a document: the zero-based [line] and the [column], counted in characters from the line's start. */
@Immutable
data class CodePosition(val line: Int, val column: Int)

/**
 * One document of a [CodeEditor]: its text, the cursor and selection, the scroll position and the
 * undo history. The app owns it, one for each open document, and keeps it for as long as the
 * document is open (in a view model, or with [rememberCodeEditorState] for a single one); showing
 * another document is giving the editor another state, and each comes back as it was left.
 *
 * Everything read here is snapshot state: a composable that reads [text], [selection], [canUndo] or
 * [version] is composed again when it changes. [version] is the cheap one; [text] copies the
 * document once after each change, so read it when the text is needed (to save, to compile), not to
 * notice that it changed.
 *
 * Offsets count characters from the start of the text; lines and columns are zero-based (the gutter
 * shows line + 1). Lines end with `\n`: [setText] and every edit turn `\r\n` and `\r` into it.
 *
 * Edits made here ([insert], [replace], [replaceSelection], the commands) go through the undo
 * history like typing, each as one step; [edit] makes several one step. They are safe while the
 * on-screen keyboard is composing a word: the composition is finished first. [setText] is for a
 * document loaded again from disk: it replaces everything and forgets the history.
 *
 * Use the state from the main thread only.
 */
@Stable
class CodeEditorState(initialText: String = "") {
    private val buffer = StringBuilder(normalizeLineBreaks(initialText))
    internal val lines = LineIndex().apply { reset(buffer) }
    internal val tokens = TokenCache().apply { reset(lines.count) }
    private val history = EditHistory()
    private var cachedText: String? = null
    private var replaying = false
    private var batchDepth = 0

    /** The editor showing this document, if one is. */
    internal var host: EditorHost? = null

    /** Told of every change of the text: where it starts, how many characters went and how many came (the search's matches move along). */
    internal val editObservers = ArrayList<(start: Int, removed: Int, inserted: Int) -> Unit>(1)

    /** What the editor is asked to do once it shows this document. */
    internal var pendingReveal: TextRange? = null
    internal var pendingFocus = false

    /**
     * Where the text is scrolled to, kept while an editor shows the document and for the next one that
     * does: [scrollAnchor] is the offset of the row at the top of the text (below the content padding),
     * [scrollAnchorDelta] how many pixels of that row are scrolled past. A row, not pixels, so the same
     * line comes back on a window of another width; [scrollX] is the sideways scroll of unwrapped text.
     */
    internal var scrollAnchor = 0
        private set
    internal var scrollAnchorDelta = 0
        private set
    internal var scrollX = 0

    private var versionState by mutableIntStateOf(0)
    private var selectionState by mutableStateOf(TextRange.Zero)
    private var firstVisibleLineState by mutableIntStateOf(0)

    /** Counts the changes of the text: read it to learn that the text changed, without copying it. */
    val version: Int get() = versionState

    /** The whole text. Copied once after each change; see [version]. */
    val text: String
        get() {
            versionState
            return cachedText ?: buffer.toString().also { cachedText = it }
        }

    val length: Int
        get() {
            versionState
            return buffer.length
        }

    val lineCount: Int
        get() {
            versionState
            return lines.count
        }

    /** The selection, from its anchor ([TextRange.start]) to the cursor ([TextRange.end]); collapsed when nothing is selected. */
    val selection: TextRange get() = selectionState

    /** The cursor's offset. */
    val cursor: Int get() = selectionState.end

    /** The cursor's line and column. */
    val cursorPosition: CodePosition get() = positionOf(cursor)

    /** The selected text, empty when nothing is selected. */
    val selectedText: String get() = textIn(selectionState.min, selectionState.max)

    /**
     * The line at the top of the editor's text (below its content padding), zero-based: where the
     * document is scrolled to, to keep with the cursor when the app saves its place. Follows the
     * scrolling while an editor shows the document; the last place otherwise. See [scrollToLine].
     */
    val firstVisibleLine: Int get() = firstVisibleLineState

    /** Whether there is something to [undo]. */
    var canUndo by mutableStateOf(false)
        private set

    /** Whether there is something to [redo]. */
    var canRedo by mutableStateOf(false)
        private set

    /** The offset of the first character of [line]. */
    fun lineStart(line: Int): Int {
        versionState
        return lines.start(line)
    }

    /** The offset after the last character of [line], before its line break. */
    fun lineEnd(line: Int): Int {
        versionState
        return lines.end(line)
    }

    /** The line [offset] is on. */
    fun lineOf(offset: Int): Int {
        versionState
        return lines.lineOf(offset.coerceIn(0, buffer.length))
    }

    /** The text of [line], without its line break. */
    fun lineText(line: Int): String {
        versionState
        return buffer.substring(lines.start(line), lines.end(line))
    }

    /** The line and column of [offset]. */
    fun positionOf(offset: Int): CodePosition {
        versionState
        val at = offset.coerceIn(0, buffer.length)
        val line = lines.lineOf(at)
        return CodePosition(line, at - lines.start(line))
    }

    /** The offset of [column] on [line]; a column past the line's end is the line's end. */
    fun offsetOf(line: Int, column: Int): Int {
        versionState
        val index = line.coerceIn(0, lines.count - 1)
        return (lines.start(index) + column.coerceAtLeast(0)).coerceAtMost(lines.end(index))
    }

    /** The text from [start] to [end]. */
    fun textIn(start: Int, end: Int): String {
        versionState
        val from = start.coerceIn(0, buffer.length)
        return buffer.substring(from, end.coerceIn(from, buffer.length))
    }

    /** The character at [offset]; for the editor's own scans, which must not copy the text. */
    internal fun charAt(offset: Int): Char = buffer[offset]

    /** The text as it is, without a copy: valid until the next edit. */
    internal val content: CharSequence get() = buffer

    // ------------------------------------------------------------------ edits

    /** Puts [text] in place of the selection (at the cursor when nothing is selected); the cursor ends up after it. */
    fun insert(text: String) = replaceSelection(text)

    /**
     * Puts [text] in place of the selection. With [select] the new text is selected, otherwise the
     * cursor stands after it.
     */
    @JvmOverloads
    fun replaceSelection(text: String, select: Boolean = false) {
        val start = selectionState.min
        val inserted = normalizeLineBreaks(text)
        change(start, selectionState.max, inserted)
        if (select) select(start, start + inserted.length) else select(start + inserted.length)
    }

    /**
     * Puts [text] in place of the characters from [start] to [end]. The cursor and the selection
     * stay with the text they were in.
     */
    fun replace(start: Int, end: Int, text: String) {
        val from = start.coerceIn(0, buffer.length)
        change(from, end.coerceIn(from, buffer.length), normalizeLineBreaks(text))
    }

    /** Everything [block] changes is one step of the undo history (a "replace all", a command over many lines). */
    fun edit(block: () -> Unit) {
        beginBatch()
        try {
            block()
        } finally {
            endBatch()
        }
    }

    /**
     * Replaces the whole document, for one read again from disk: the undo history is forgotten, the
     * cursor goes to the start. To change the text as an edit the user can undo, use [replace].
     */
    fun setText(text: String) {
        val content = normalizeLineBreaks(text)
        val oldLength = buffer.length
        history.clear()
        buffer.setLength(0)
        buffer.append(content)
        lines.reset(buffer)
        tokens.reset(lines.count)
        selectionState = TextRange.Zero
        scrollX = 0
        setScrollAnchor(0, 0)
        for (observer in editObservers) observer(0, oldLength, buffer.length)
        historyChanged()
        textChanged()
        host?.showText()
    }

    /** Takes back the last step; false when there is none. */
    fun undo(): Boolean {
        val entry = history.undo() ?: return false
        replay {
            for (i in entry.changes.indices.reversed()) {
                val change = entry.changes[i]
                change(change.offset, change.offset + change.inserted.length, change.removed)
            }
        }
        select(unpackStart(entry.selectionBefore), unpackEnd(entry.selectionBefore))
        revealCursor()
        return true
    }

    /** Makes the last step taken back again; false when there is none. */
    fun redo(): Boolean {
        val entry = history.redo() ?: return false
        replay {
            for (change in entry.changes) change(change.offset, change.offset + change.removed.length, change.inserted)
        }
        select(unpackStart(entry.selectionAfter), unpackEnd(entry.selectionAfter))
        revealCursor()
        return true
    }

    private inline fun replay(block: () -> Unit) {
        replaying = true
        host?.batch(true)
        try {
            block()
        } finally {
            host?.batch(false)
            replaying = false
            historyChanged()
        }
    }

    private fun beginBatch() {
        if (batchDepth++ == 0) {
            history.beginGroup(pack(selectionState))
            host?.batch(true)
        }
    }

    private fun endBatch() {
        if (--batchDepth == 0) {
            host?.batch(false)
            history.endGroup(pack(selectionState))
            historyChanged()
        }
    }

    /** Changes the text as a programmatic edit: through the editor when one shows the document, so it sees every change. */
    private fun change(start: Int, end: Int, text: String) {
        if (start == end && text.isEmpty()) return
        val host = host
        if (host != null) {
            // comes back through hostEdited
            host.replace(start, end, text)
        } else {
            val before = pack(selectionState)
            val removed = buffer.substring(start, end)
            selectionState = TextRange(shifted(selectionState.start, start, end, text.length), shifted(selectionState.end, start, end, text.length))
            commit(start, removed, text, typed = false, selectionBefore = before)
        }
    }

    /** Where [offset] is after the text from [start] to [end] became [newLength] characters. */
    private fun shifted(offset: Int, start: Int, end: Int, newLength: Int): Int = when {
        offset <= start -> offset
        offset >= end -> offset + newLength - (end - start)
        else -> start + newLength
    }

    /** The editor reports a change of its text: the user's ([typed]) or one this state asked for. */
    internal fun hostEdited(start: Int, removed: String, inserted: CharSequence, typed: Boolean, selectionBefore: Long) {
        commit(start, removed, inserted.toString(), typed, selectionBefore)
    }

    /** The editor reports where the cursor and the selection are. */
    internal fun hostSelected(start: Int, end: Int) {
        val range = TextRange(start.coerceIn(0, buffer.length), end.coerceIn(0, buffer.length))
        if (range != selectionState) selectionState = range
    }

    /** The one place the text changes: the buffer, the line index, the tokens, the history, the scroll anchor. */
    private fun commit(start: Int, removed: String, inserted: String, typed: Boolean, selectionBefore: Long) {
        val end = start + removed.length
        buffer.replace(start, end, inserted)
        val packed = lines.replace(start, end, inserted)
        tokens.linesReplaced(lines.lineOf(start), LineIndex.removedLines(packed), LineIndex.addedLines(packed))
        // the row at the top stays with its text; one whose text went is where the change is
        if (scrollAnchor > start) setScrollAnchor(if (scrollAnchor >= end) scrollAnchor + inserted.length - removed.length else start, scrollAnchorDelta)
        for (observer in editObservers) observer(start, removed.length, inserted.length)
        if (!replaying) {
            val after = start + inserted.length
            history.record(start, removed, inserted, selectionBefore, pack(TextRange(after)), typed)
            if (batchDepth == 0) historyChanged()
        }
        textChanged()
    }

    private fun textChanged() {
        cachedText = null
        versionState++
    }

    private fun historyChanged() {
        canUndo = history.canUndo
        canRedo = history.canRedo
    }

    /**
     * The editor's text and this document have come apart (they never should): the editor's text is
     * what the user sees, so it becomes the document. The history no longer fits and is dropped.
     */
    internal fun resyncFrom(text: CharSequence) {
        val oldLength = buffer.length
        history.clear()
        buffer.setLength(0)
        buffer.append(text)
        for (observer in editObservers) observer(0, oldLength, buffer.length)
        lines.reset(buffer)
        tokens.reset(lines.count)
        setScrollAnchor(scrollAnchor, scrollAnchorDelta)
        historyChanged()
        textChanged()
    }

    // ------------------------------------------------------------------ selection and view

    /** Selects from [start] to [end], the cursor at [end]; with one offset, puts the cursor there. */
    @JvmOverloads
    fun select(start: Int, end: Int = start) {
        val range = TextRange(start.coerceIn(0, buffer.length), end.coerceIn(0, buffer.length))
        val host = host
        if (host != null) host.select(range.start, range.end) else selectionState = range
    }

    fun selectAll() = select(0, buffer.length)

    /**
     * Scrolls the characters from [start] to [end] into view. With [select] they are selected (the
     * cursor at [end]); with [focus] the editor takes the keyboard focus. An editor that does not
     * show this document yet does it when it does.
     */
    @JvmOverloads
    fun reveal(start: Int, end: Int = start, select: Boolean = false, focus: Boolean = false) {
        val from = start.coerceIn(0, buffer.length)
        val to = end.coerceIn(0, buffer.length)
        if (select) select(from, to)
        val host = host
        if (host != null) host.reveal(from, to) else pendingReveal = TextRange(from, to)
        if (focus) requestFocus()
    }

    /** [reveal] for a whole line: with [select] the line is selected, without it the cursor goes to its start. */
    @JvmOverloads
    fun revealLine(line: Int, select: Boolean = false, focus: Boolean = false) {
        val index = line.coerceIn(0, lines.count - 1)
        if (!select) select(lines.start(index))
        reveal(lines.start(index), lines.end(index), select, focus)
    }

    /**
     * Scrolls so that [line] is the first line of the text (below the content padding), as far as the
     * end of the document allows; a document no editor shows opens there. For a place saved from
     * [firstVisibleLine], after a restart say. The cursor stays where it is.
     */
    fun scrollToLine(line: Int) {
        val start = lines.start(line.coerceIn(0, lines.count - 1))
        setScrollAnchor(start, 0)
        host?.scrollToAnchor()
    }

    /** The editor reports the row at the top of its text: its start [offset], and [delta] pixels of it scrolled past. */
    internal fun setScrollAnchor(offset: Int, delta: Int) {
        scrollAnchor = offset.coerceIn(0, buffer.length)
        scrollAnchorDelta = delta.coerceAtLeast(0)
        val line = lines.lineOf(scrollAnchor)
        if (line != firstVisibleLineState) firstVisibleLineState = line
    }

    /**
     * Opens the editor's completion list at the cursor, as Ctrl+Space does (for an app's menu entry or its own
     * key): when an editor shows this document and has a [CodeCompleter] with something to offer.
     */
    fun showCompletion() {
        host?.showCompletion()
    }

    /** Gives the editor the keyboard focus (and opens the on-screen keyboard when there is no other). */
    fun requestFocus() {
        val host = host
        if (host != null) host.focus() else pendingFocus = true
    }

    // ------------------------------------------------------------------ commands

    /**
     * The first and the last line the selection touches. A selection that ends at the very start of
     * a line does not touch that line.
     */
    internal fun selectedLines(): IntRange {
        val first = lines.lineOf(selectionState.min)
        var last = lines.lineOf(selectionState.max)
        if (last > first && selectionState.max == lines.start(last)) last--
        return first..last
    }

    /** Moves every line the selection touches one step of [indent] in; the lines stay selected. */
    @JvmOverloads
    fun indentSelection(indent: CodeIndent = CodeIndent.Tab) = indentSelection(indent, typed = false)

    /**
     * [typed]: the Tab key. With the cursor or the selection inside one line it types the indentation
     * there (spaces up to the next stop); only a selection over several lines moves the lines.
     */
    internal fun indentSelection(indent: CodeIndent, typed: Boolean) {
        val range = selectedLines()
        if (typed && range.first == range.last) {
            val column = selectionState.min - lines.start(range.first)
            val width = indent.unit.length
            replaceSelection(if (indent.unit == "\t") "\t" else " ".repeat(width - column % width))
            return
        }
        edit {
            // from the last line up, so the offsets of the lines still to do stay as they are
            for (line in range.last downTo range.first) {
                val start = lines.start(line)
                // an empty line gets no trailing white space
                if (lines.end(line) > start) change(start, start, indent.unit)
            }
        }
        select(lines.start(range.first), lines.end(range.last))
    }

    /**
     * Moves every line the selection touches one step of [indent] out: a leading tab goes, or as many
     * leading spaces as [indent] has (four for a tab). The lines stay selected when there are several.
     */
    @JvmOverloads
    fun outdentSelection(indent: CodeIndent = CodeIndent.Tab) {
        val range = selectedLines()
        val width = if (indent.unit == "\t") TAB_WIDTH else indent.unit.length
        val several = range.first != range.last
        edit {
            for (line in range.last downTo range.first) {
                val start = lines.start(line)
                val end = lines.end(line)
                var remove = 0
                if (start < end && buffer[start] == '\t') {
                    remove = 1
                } else {
                    while (remove < width && start + remove < end && buffer[start + remove] == ' ') remove++
                }
                if (remove > 0) change(start, start + remove, "")
            }
        }
        if (several) select(lines.start(range.first), lines.end(range.last))
    }

    /**
     * Moves the lines the selection touches (the cursor's line when nothing is selected) one line up, past the
     * line above them; the selection moves along. One undo step. False at the top of the document.
     */
    fun moveLinesUp(): Boolean {
        val range = selectedLines()
        if (range.first == 0) return false
        val aboveStart = lines.start(range.first - 1)
        val above = buffer.substring(aboveStart, lines.end(range.first - 1))
        val blockEnd = lines.end(range.last)
        val block = buffer.substring(lines.start(range.first), blockEnd)
        val selection = selectionState
        val shift = above.length + 1
        edit {
            change(aboveStart, blockEnd, block + "\n" + above)
            select(selection.start - shift, selection.end - shift)
        }
        revealCursor()
        return true
    }

    /** [moveLinesUp] the other way: the lines go past the line below them. False at the end of the document. */
    fun moveLinesDown(): Boolean {
        val range = selectedLines()
        if (range.last + 1 >= lines.count) return false
        val blockStart = lines.start(range.first)
        val block = buffer.substring(blockStart, lines.end(range.last))
        val belowEnd = lines.end(range.last + 1)
        val below = buffer.substring(lines.start(range.last + 1), belowEnd)
        val selection = selectionState
        val shift = below.length + 1
        edit {
            change(blockStart, belowEnd, below + "\n" + block)
            select(selection.start + shift, selection.end + shift)
        }
        revealCursor()
        return true
    }

    /** Puts a copy of the lines the selection touches under them; the selection moves to the copy. One undo step. */
    fun duplicateLines() {
        val range = selectedLines()
        val blockEnd = lines.end(range.last)
        val block = buffer.substring(lines.start(range.first), blockEnd)
        val selection = selectionState
        val shift = block.length + 1
        edit {
            change(blockEnd, blockEnd, "\n" + block)
            select(selection.start + shift, selection.end + shift)
        }
        revealCursor()
    }

    /**
     * Deletes the lines the selection touches, their line breaks with them; the cursor goes to the line that takes
     * their place, in the same column as far as it reaches. One undo step.
     */
    fun deleteLines() {
        val range = selectedLines()
        val column = selectionState.end - lines.start(lines.lineOf(selectionState.end))
        var start = lines.start(range.first)
        val end = if (range.last + 1 < lines.count) lines.start(range.last + 1) else lines.end(range.last)
        // the last line goes with the line break before it
        if (range.last + 1 >= lines.count && range.first > 0) start--
        edit {
            change(start, end, "")
            val line = range.first.coerceAtMost(lines.count - 1)
            select(lines.start(line) + column.coerceAtMost(lines.end(line) - lines.start(line)))
        }
        revealCursor()
    }

    /**
     * Comments the lines the selection touches out with [prefix] (`//`, `#`, `--`) and a space, at the
     * indentation of the least indented of them; when every one of them already starts with [prefix] after its
     * indentation, takes it (and one space after it) away instead. Blank lines are left as they are, unless only
     * blank lines are touched. One undo step; the selection stays on its text.
     */
    fun toggleLineComment(prefix: String) {
        require(prefix.isNotEmpty()) { "a comment needs a prefix" }
        val range = selectedLines()
        val written = range.filter { line -> (lines.start(line) until lines.end(line)).any { !buffer[it].isWhitespace() } }
        val targets = written.ifEmpty { range.toList() }
        val commented = written.isNotEmpty() && written.all { line -> buffer.startsWith(prefix, lines.start(line) + indentation(line)) }
        val column = targets.minOf { indentation(it) }
        edit {
            // from the last line up, so the offsets of the lines still to do stay as they are
            for (line in targets.asReversed()) {
                val start = lines.start(line)
                if (commented) {
                    val at = start + indentation(line)
                    var end = at + prefix.length
                    if (end < lines.end(line) && buffer[end] == ' ') end++
                    change(at, end, "")
                } else {
                    change(start + column, start + column, "$prefix ")
                }
            }
        }
    }

    /** How many tabs and spaces [line] starts with. */
    private fun indentation(line: Int): Int {
        val start = lines.start(line)
        val end = lines.end(line)
        var at = start
        while (at < end && (buffer[at] == ' ' || buffer[at] == '\t')) at++
        return at - start
    }

    /**
     * Selects the lines the selection touches, their line breaks with them; when it already is whole lines, one
     * line more. Pressed again and again it selects line after line, as in desktop editors.
     */
    fun selectLines() {
        val range = selectedLines()
        val start = lines.start(range.first)
        fun endOf(line: Int) = if (line + 1 < lines.count) lines.start(line + 1) else lines.end(line)
        var end = endOf(range.last)
        if (selectionState.min == start && selectionState.max == end && range.last + 1 < lines.count) end = endOf(range.last + 1)
        select(start, end)
    }

    /**
     * Puts the cursor on [line] (zero-based) at [column] (as far as the line reaches), scrolls it into view and
     * gives the editor the keyboard focus: the end of an app's "go to line".
     */
    @JvmOverloads
    fun goToLine(line: Int, column: Int = 0) {
        val offset = offsetOf(line, column)
        select(offset)
        reveal(offset, offset, focus = true)
    }

    private fun revealCursor() {
        host?.reveal(selectionState.end, selectionState.end)
    }

    internal companion object {
        /** How many columns a tab character counts for when lines are moved out. */
        const val TAB_WIDTH = 4

        fun pack(range: TextRange): Long = range.start.toLong() shl 32 or (range.end.toLong() and 0xFFFFFFFFL)
        fun pack(start: Int, end: Int): Long = start.toLong() shl 32 or (end.toLong() and 0xFFFFFFFFL)
        fun unpackStart(packed: Long): Int = (packed shr 32).toInt()
        fun unpackEnd(packed: Long): Int = packed.toInt()

        /** `\r\n` and `\r` as `\n`; [text] itself when it has neither. */
        fun normalizeLineBreaks(text: String): String =
            if (text.indexOf('\r') < 0) text else text.replace("\r\n", "\n").replace('\r', '\n')
    }
}

/** What a [CodeEditorState] needs of the editor that shows it. */
internal interface EditorHost {
    /** Replaces text in the editor; the editor reports it back as an edit that is not typed. */
    fun replace(start: Int, end: Int, text: String)
    fun select(start: Int, end: Int)

    /** Shows the state's text anew: all of it was replaced. */
    fun showText()

    /** Scrolls to the state's scroll anchor (the app chose a line to show at the top). */
    fun scrollToAnchor()
    fun reveal(start: Int, end: Int)
    fun focus()

    /** Several edits follow ([begin]) or are over: the editor lays out and tells the keyboard once. */
    fun batch(begin: Boolean)

    /** Opens the completion list at the cursor. */
    fun showCompletion()
}

/**
 * A [CodeEditorState] remembered in the composition, for a screen with a single document. It is not
 * saved with the instance state (a document can be far larger than that allows): an app that must
 * survive its process being stopped keeps its documents itself.
 */
@Composable
fun rememberCodeEditorState(initialText: String = ""): CodeEditorState = remember { CodeEditorState(initialText) }
