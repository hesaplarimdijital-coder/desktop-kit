package desktopkit.editor

import desktopkit.theme.SyntaxRole

/** Finds the partner of the bracket next to the cursor. */
internal object Brackets {
    private const val OPEN = "([{"
    private const val CLOSE = ")]}"

    /** No farther than this from the cursor: a stray bracket must not make every cursor move read the whole document. */
    const val SCAN_LIMIT = 20_000

    /**
     * The bracket beside [cursor] (the one before it first, then the one after) and its partner, as
     * [CodeEditorState.pack]ed offsets; -1 and -1 when there is no bracket there or no partner within
     * [SCAN_LIMIT] characters. Brackets inside comments and strings neither count nor match.
     */
    fun match(state: CodeEditorState, cursor: Int): Long {
        val length = state.lines.length
        for (at in intArrayOf(cursor - 1, cursor)) {
            if (at < 0 || at >= length) continue
            val char = state.charAt(at)
            val open = OPEN.indexOf(char)
            val close = CLOSE.indexOf(char)
            if ((open < 0 && close < 0) || quoted(state, at)) continue
            val partner = if (open >= 0) scan(state, at, 1, char, CLOSE[open], length) else scan(state, at, -1, char, OPEN[close], length)
            if (partner >= 0) return CodeEditorState.pack(at, partner)
        }
        return CodeEditorState.pack(-1, -1)
    }

    private fun scan(state: CodeEditorState, from: Int, step: Int, same: Char, other: Char, length: Int): Int {
        var depth = 0
        var at = from + step
        var steps = 0
        while (at in 0 until length && steps++ < SCAN_LIMIT) {
            val char = state.charAt(at)
            if ((char == same || char == other) && !quoted(state, at)) {
                if (char == same) depth++ else if (depth == 0) return at else depth--
            }
            at += step
        }
        return -1
    }

    /** Whether the character at [offset] is part of a comment or a string, as far as the lines are tokenized. */
    private fun quoted(state: CodeEditorState, offset: Int): Boolean {
        val line = state.lines.lineOf(offset)
        val role = state.tokens.roleAt(line, offset - state.lines.start(line))
        return role == SyntaxRole.Comment.ordinal || role == SyntaxRole.String.ordinal
    }
}
