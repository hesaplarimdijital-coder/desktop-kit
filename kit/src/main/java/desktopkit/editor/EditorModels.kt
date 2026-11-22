package desktopkit.editor

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextRange

/** How serious a [LineMarker] is; the gutter shows it in the theme's danger, warning or accent colour. */
enum class MarkerSeverity { Error, Warning, Info }

/**
 * Something to say about a line of a [CodeEditor]'s document (a compiler's error, a warning, a
 * note): a mark in the gutter beside the zero-based [line], and the [message] as a tooltip on it.
 */
@Immutable
data class LineMarker(val line: Int, val severity: MarkerSeverity, val message: String)

/** The colour a [CodeDecoration] is drawn in: one of the theme's roles. */
enum class DecorationTone { Accent, Danger, Warning, Success, Neutral }

/** How a [CodeDecoration] marks its text. */
enum class DecorationKind {
    /** A tinted background behind the characters. */
    Background,

    /** A line under the characters. */
    Underline,

    /** A tinted background across the whole line (or lines) the range touches. */
    Line,
}

/**
 * A range of a [CodeEditor]'s document the app wants marked: a problem's exact place, the line being
 * run, the occurrences of a name. [start] and [end] are offsets into the text; a decoration made for
 * an earlier text is drawn where its offsets now fall, so make them again after the text changed.
 */
@Immutable
data class CodeDecoration(val start: Int, val end: Int, val tone: DecorationTone, val kind: DecorationKind = DecorationKind.Background)

/**
 * Tells a [CodeEditor] where its document has links: text that leads somewhere (a name to its
 * definition, an include to its file). Asked for the offset under the pointer while Ctrl or Alt is
 * held, and for the click.
 */
fun interface CodeLinks {
    /** The range of the link at [offset], or null when there is none. */
    fun linkAt(state: CodeEditorState, offset: Int): TextRange?
}

/** How a [CodeEditor] indents: what the Tab key puts in. */
@Immutable
class CodeIndent private constructor(internal val unit: String) {
    override fun equals(other: Any?): Boolean = other is CodeIndent && other.unit == unit
    override fun hashCode(): Int = unit.hashCode()
    override fun toString(): String = if (unit == "\t") "CodeIndent.Tab" else "CodeIndent.spaces(${unit.length})"

    companion object {
        /** A tab character. */
        val Tab = CodeIndent("\t")

        /** [count] spaces (1 to 16). */
        fun spaces(count: Int): CodeIndent = CodeIndent(" ".repeat(count.coerceIn(1, 16)))
    }
}
