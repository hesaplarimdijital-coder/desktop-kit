package desktopkit.editor

/**
 * Where the lines of a text start. Lines end at '\n'; a text always has at least one line, and a
 * text that ends with a line break has an empty last line.
 *
 * An edit updates the index in place: the lines it touched are recounted and the starts after it
 * shifted, so the cost does not depend on where in the text the edit is, only on a block copy of
 * the starts behind it (a few microseconds for tens of thousands of lines).
 */
internal class LineIndex {
    private var starts = IntArray(64)

    /** How many lines the text has. */
    var count = 1
        private set

    /** The text's length. */
    var length = 0
        private set

    /** Counts the lines of [text] from scratch. */
    fun reset(text: CharSequence) {
        count = 1
        starts[0] = 0
        for (i in text.indices) if (text[i] == '\n') append(i + 1)
        length = text.length
    }

    private fun append(start: Int) {
        if (count == starts.size) starts = starts.copyOf(count * 2)
        starts[count++] = start
    }

    /** The offset of the first character of [line]. */
    fun start(line: Int): Int = starts[line.coerceIn(0, count - 1)]

    /** The offset after the last character of [line], before its line break. */
    fun end(line: Int): Int {
        val index = line.coerceIn(0, count - 1)
        return if (index + 1 < count) starts[index + 1] - 1 else length
    }

    /** The line [offset] is on; the offset just after a line break is on the next line. */
    fun lineOf(offset: Int): Int {
        var low = 0
        var high = count - 1
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            if (starts[mid] <= offset) low = mid else high = mid - 1
        }
        return low
    }

    /**
     * The text from [start] to [end] was replaced by [inserted]. Returns how many lines the edit
     * took away after the first line it touched and how many it put there, packed as
     * `removed shl 32 or added`; see [removedLines] and [addedLines].
     */
    fun replace(start: Int, end: Int, inserted: CharSequence): Long {
        val firstLine = lineOf(start)
        val lastLine = lineOf(end)
        val removed = lastLine - firstLine
        var added = 0
        for (i in inserted.indices) if (inserted[i] == '\n') added++
        val delta = inserted.length - (end - start)
        val tail = count - (lastLine + 1)
        val newCount = count - removed + added
        if (newCount > starts.size) starts = starts.copyOf(maxOf(newCount, starts.size * 2))
        // the starts behind the edit move to their new place first, then shift by the change in length
        System.arraycopy(starts, lastLine + 1, starts, firstLine + 1 + added, tail)
        if (delta != 0) for (i in firstLine + 1 + added until newCount) starts[i] += delta
        var line = firstLine + 1
        for (i in inserted.indices) if (inserted[i] == '\n') starts[line++] = start + i + 1
        count = newCount
        length += delta
        return removed.toLong() shl 32 or added.toLong()
    }

    companion object {
        fun removedLines(packed: Long): Int = (packed ushr 32).toInt()
        fun addedLines(packed: Long): Int = packed.toInt()
    }
}
