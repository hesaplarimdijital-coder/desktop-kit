package desktopkit.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import desktopkit.components.DeskIconButton
import desktopkit.components.DeskIcons
import desktopkit.components.IconMenuButton
import desktopkit.components.MenuEntry
import desktopkit.editor.CodeCompleter
import desktopkit.editor.CodeCompletionItem
import desktopkit.editor.CodeCompletions
import desktopkit.editor.CodeEditor
import desktopkit.editor.CodeEditorState
import desktopkit.editor.CodeLinks
import desktopkit.editor.CodeSearchBar
import desktopkit.editor.LineMarker
import desktopkit.editor.LineTokenizer
import desktopkit.editor.MarkerSeverity
import desktopkit.editor.rememberCodeEditorZoom
import desktopkit.editor.rememberCodeSearchState
import desktopkit.screens.PageHeader
import desktopkit.settings.DeskPreferences
import desktopkit.shell.rememberShellContentPadding
import desktopkit.theme.SyntaxRole

/**
 * The code editor with a script in a small made-up story language: sections (`== name ==`), choices (`*`, `+`),
 * jumps (`-> name`), logic in braces, variables, tags (`#`) and comments. Jumps to a section that does not exist
 * are errors in the gutter, a `TODO` in a comment is a warning; Ctrl+click on a jump goes to its section;
 * the completion offers the sections after `->` and the language's words; Ctrl+F searches, the zoom is kept.
 */
@Composable
fun EditorScreen(prefs: DeskPreferences) {
    val document = remember { CodeEditorState(STORY) }
    val search = rememberCodeSearchState()
    val zoom = rememberCodeEditorZoom(prefs, key = "sample")
    val padding = rememberShellContentPadding()
    // the findings of this "compiler", made again when the text changes
    val version = document.version
    val markers = remember(version) { findProblems(document) }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Editor", Modifier.padding(top = padding.calculateTopPadding()), subtitle = "A story script in a made-up language") {
            DeskIconButton(DeskIcons.Undo, "Undo", { document.undo() }, enabled = document.canUndo)
            DeskIconButton(DeskIcons.Search, "Find", { search.open() })
            IconMenuButton(DeskIcons.MoreHorizontal, "More", entries = {
                listOf(
                    MenuEntry.Item("Redo", { document.redo() }, enabled = document.canRedo),
                    MenuEntry.Item("Replace", { search.open(replace = true) }),
                    MenuEntry.Separator,
                    MenuEntry.Item("Move line up", { document.moveLinesUp() }),
                    MenuEntry.Item("Move line down", { document.moveLinesDown() }),
                    MenuEntry.Item("Duplicate line", { document.duplicateLines() }),
                    MenuEntry.Item("Delete line", { document.deleteLines() }),
                    MenuEntry.Item("Comment out", { document.toggleLineComment("//") }),
                    MenuEntry.Item("Suggest", { document.showCompletion() }),
                    MenuEntry.Separator,
                    MenuEntry.Item("Larger text", zoom::zoomIn),
                    MenuEntry.Item("Smaller text", zoom::zoomOut),
                    MenuEntry.Item("Text size of the theme", zoom::reset),
                )
            })
        }
        CodeSearchBar(search, Modifier.fillMaxWidth())
        CodeEditor(
            state = document,
            modifier = Modifier.weight(1f),
            tokenizer = StoryTokenizer,
            markers = markers,
            onMarkerClick = { marker -> document.revealLine(marker.line, focus = true) },
            links = StoryLinks,
            onLinkClick = { offset -> goToSection(document, offset) },
            contextMenu = { offset ->
                if (StoryLinks.linkAt(document, offset) == null) emptyList()
                else listOf(MenuEntry.Item("Go to section", { goToSection(document, offset) }))
            },
            zoom = zoom,
            proportional = true,
            autoCorrect = true,
            capitalizeSentences = true,
            placeholder = "Write the first line of your story",
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
            search = search,
            completer = StoryCompleter,
        )
    }
}

private val STORY = """
    // The tea garden: the first scene
    VAR tea = 2
    VAR town = "Kemer"

    == garden ==
    I went down to the garden early in the morning. The tables were still wet; the waiter boy was wiping them with a cloth and humming along with the song on the radio. # morning
    * "Could I have a tea?" -> order_tea
    * I walked towards the sea. -> shore
    + { tea > 0 } I finished my glass.
        ~ tea = tea - 1
        -> garden

    == order_tea ==
    // TODO: the waiter comes twice here
    Tea came (strong, { tea } sugars). -> garden

    == shore ==
    The waves were hitting [the stones]. -> harbour
""".trimIndent()

private val WORDS = listOf("VAR", "CONST", "INCLUDE", "END", "DONE")

/** The colours of the story language, one line at a time; nothing reaches across lines. */
private val StoryTokenizer = LineTokenizer { line, state, tokens ->
    val length = line.length
    var first = 0
    while (first < length && line[first].isWhitespace()) first++
    if (line.startsWith("==", first)) {
        tokens.add(first, length, SyntaxRole.Heading)
        return@LineTokenizer state
    }
    var i = first
    while (i < length) {
        val c = line[i]
        when {
            line.startsWith("//", i) -> {
                tokens.add(i, length, SyntaxRole.Comment)
                i = length
            }
            line.startsWith("->", i) -> {
                tokens.add(i, i + 2, SyntaxRole.Operator)
                val (from, to) = wordAfter(line, i + 2)
                tokens.add(from, to, if (line.substring(from, to) in WORDS) SyntaxRole.Keyword else SyntaxRole.Link)
                i = maxOf(to, i + 2)
            }
            c == '#' -> {
                tokens.add(i, length, SyntaxRole.Tag)
                i = length
            }
            c == '"' -> {
                val close = line.indexOf('"', i + 1)
                val end = if (close < 0) length else close + 1
                tokens.add(i, end, SyntaxRole.String)
                i = end
            }
            c.isDigit() -> {
                var end = i
                while (end < length && line[end].isDigit()) end++
                tokens.add(i, end, SyntaxRole.Number)
                i = end
            }
            (c == '*' || c == '+' || c == '~') && i == first -> {
                tokens.add(i, i + 1, SyntaxRole.Keyword)
                i++
            }
            c in "{}()[]" -> {
                tokens.add(i, i + 1, SyntaxRole.Punctuation)
                i++
            }
            c.isLetter() -> {
                var end = i
                while (end < length && (line[end].isLetterOrDigit() || line[end] == '_')) end++
                if (line.substring(i, end) in WORDS) tokens.add(i, end, SyntaxRole.Keyword)
                i = end
            }
            else -> i++
        }
    }
    state
}

/** The name after `->` and the spaces before it: its start and end in [line]. */
private fun wordAfter(line: CharSequence, from: Int): Pair<Int, Int> {
    var start = from
    while (start < line.length && line[start] == ' ') start++
    var end = start
    while (end < line.length && (line[end].isLetterOrDigit() || line[end] == '_')) end++
    return start to end
}

/** The sections of [document]: their names and lines. */
private fun sections(document: CodeEditorState): Map<String, Int> {
    val found = HashMap<String, Int>()
    for (line in 0 until document.lineCount) {
        val text = document.lineText(line).trim()
        if (text.startsWith("==")) found[text.trim('=', ' ')] = line
    }
    return found
}

/** A jump's target is a link: the name after `->`. */
private val StoryLinks = CodeLinks { document, offset ->
    val line = document.lineOf(offset)
    val text = document.lineText(line)
    val column = offset - document.lineStart(line)
    var arrow = text.indexOf("->")
    var link: TextRange? = null
    while (arrow >= 0 && link == null) {
        val (from, to) = wordAfter(text, arrow + 2)
        if (to > from && column in from..to && text.substring(from, to) !in WORDS) link = TextRange(document.lineStart(line) + from, document.lineStart(line) + to)
        arrow = text.indexOf("->", arrow + 2)
    }
    link
}

private fun goToSection(document: CodeEditorState, offset: Int) {
    val range = StoryLinks.linkAt(document, offset) ?: return
    val line = sections(document)[document.textIn(range.min, range.max)] ?: return
    document.revealLine(line, select = true, focus = true)
}

/** This sample's compiler: a jump to a section that is not there is an error, a TODO a warning. */
private fun findProblems(document: CodeEditorState): List<LineMarker> {
    val known = sections(document).keys
    val markers = ArrayList<LineMarker>()
    for (line in 0 until document.lineCount) {
        val text = document.lineText(line)
        if ("TODO" in text) markers += LineMarker(line, MarkerSeverity.Warning, "Something is left to do here")
        var arrow = text.indexOf("->")
        while (arrow >= 0) {
            val (from, to) = wordAfter(text, arrow + 2)
            val name = text.substring(from, to)
            if (name.isNotEmpty() && name !in WORDS && name !in known) markers += LineMarker(line, MarkerSeverity.Error, "There is no section called “$name”")
            arrow = text.indexOf("->", arrow + 2)
        }
    }
    return markers
}

/** After `->` the sections, elsewhere the language's words, for the word at the cursor. */
private val StoryCompleter = CodeCompleter { document, cursor ->
    var start = cursor
    while (start > 0 && document.textIn(start - 1, start).let { it[0].isLetterOrDigit() || it[0] == '_' }) start--
    val before = document.textIn(document.lineStart(document.lineOf(cursor)), start).trimEnd()
    val items = if (before.endsWith("->")) {
        sections(document).keys.sorted().map { CodeCompletionItem(it, "section", DeskIcons.Folder) } +
            listOf("END", "DONE").map { CodeCompletionItem(it, "word", DeskIcons.Command) }
    } else {
        WORDS.map { CodeCompletionItem(it, "word", DeskIcons.Command) }
    }
    CodeCompletions(start, cursor, items)
}
