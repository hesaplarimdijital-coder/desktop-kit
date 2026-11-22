package desktopkit.editor

/** One replacement: at [offset], [removed] gave way to [inserted]. */
internal class TextChange(var offset: Int, var removed: String, var inserted: String)

/**
 * The undo history of a document. Each entry is what one Undo takes back: a run of typing, one
 * programmatic edit, or everything made inside one group (a "replace all").
 *
 * Typing is gathered the way desktop editors do: characters typed one after another are one entry
 * until a pause of [TYPING_PAUSE_MS], a line break or the start of a new word; so are the changes an
 * on-screen keyboard makes to the word it is still composing (each suggestion replaces the last), and
 * a run of Backspace or Delete. Programmatic edits never merge.
 */
internal class EditHistory(private val clock: () -> Long = System::currentTimeMillis) {

    class Entry(
        val changes: ArrayList<TextChange>,
        val selectionBefore: Long,
        var selectionAfter: Long,
        /** Typing that later typing may join. */
        var open: Boolean,
        var time: Long,
    )

    private val undone = ArrayList<Entry>()
    private val done = ArrayList<Entry>()
    private var group: Entry? = null
    private var groupDepth = 0

    val canUndo: Boolean get() = done.isNotEmpty() || group?.changes?.isNotEmpty() == true
    val canRedo: Boolean get() = undone.isNotEmpty()

    fun clear() {
        done.clear()
        undone.clear()
        group = null
        groupDepth = 0
    }

    /** Everything recorded until the matching [endGroup] is one entry. Groups nest. */
    fun beginGroup(selection: Long) {
        if (groupDepth++ == 0) group = Entry(ArrayList(), selection, selection, open = false, time = clock())
    }

    fun endGroup(selection: Long) {
        if (groupDepth == 0 || --groupDepth > 0) return
        val entry = group ?: return
        group = null
        if (entry.changes.isEmpty()) return
        entry.selectionAfter = selection
        push(entry)
    }

    /**
     * Records that [removed] at [offset] became [inserted]. [typed] changes (the keyboard, a paste)
     * may join the entry before them; the others stand alone.
     */
    fun record(offset: Int, removed: String, inserted: String, selectionBefore: Long, selectionAfter: Long, typed: Boolean) {
        if (removed.isEmpty() && inserted.isEmpty()) return
        val now = clock()
        group?.let { entry ->
            entry.changes += TextChange(offset, removed, inserted)
            return
        }
        undone.clear()
        val last = done.lastOrNull()
        if (typed && last != null && last.open && now - last.time <= TYPING_PAUSE_MS && join(last.changes[0], offset, removed, inserted)) {
            last.selectionAfter = selectionAfter
            last.time = now
            return
        }
        done.lastOrNull()?.open = false
        push(Entry(arrayListOf(TextChange(offset, removed, inserted)), selectionBefore, selectionAfter, open = typed, time = now))
    }

    private fun push(entry: Entry) {
        undone.clear()
        done += entry
        if (done.size > MAX_ENTRIES) done.removeAt(0)
    }

    /** Folds a typed change into [open], the change of the entry being typed; false when it does not belong to it. */
    private fun join(open: TextChange, offset: Int, removed: String, inserted: String): Boolean {
        val insertedEnd = open.offset + open.inserted.length
        // more text at the end of what was typed: a run of typing
        if (removed.isEmpty() && offset == insertedEnd && open.inserted.isNotEmpty()) {
            if (inserted.contains('\n') || open.inserted.contains('\n')) return false
            // a new word starts a new entry, so Undo takes back a word at a time
            if (open.inserted.last().isWhitespace() && !inserted.first().isWhitespace()) return false
            open.inserted += inserted
            return true
        }
        // a change inside what was typed: the keyboard corrects the word it composes, Backspace takes a typed letter
        if (open.inserted.isNotEmpty() && offset >= open.offset && offset + removed.length <= insertedEnd && (removed.isNotEmpty() || offset < insertedEnd)) {
            if (inserted.contains('\n')) return false
            val at = offset - open.offset
            open.inserted = open.inserted.replaceRange(at, at + removed.length, inserted)
            return true
        }
        if (open.inserted.isEmpty() && inserted.isEmpty()) {
            // Backspace again: the text before the last deletion
            if (offset + removed.length == open.offset) {
                open.offset = offset
                open.removed = removed + open.removed
                return true
            }
            // Delete again: the text after it
            if (offset == open.offset) {
                open.removed += removed
                return true
            }
        }
        return false
    }

    /** The entry to take back, or null; the caller applies its changes in reverse. */
    fun undo(): Entry? {
        val entry = done.removeLastOrNull() ?: return null
        entry.open = false
        undone += entry
        return entry
    }

    /** The entry to make again, or null; the caller applies its changes in order. */
    fun redo(): Entry? {
        val entry = undone.removeLastOrNull() ?: return null
        done += entry
        return entry
    }

    companion object {
        const val TYPING_PAUSE_MS = 1500L
        const val MAX_ENTRIES = 1000
    }
}
