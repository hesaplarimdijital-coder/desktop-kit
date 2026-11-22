package desktopkit.editor

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import desktopkit.components.PaletteMatcher
import desktopkit.components.highlight
import desktopkit.theme.DeskTheme

/** One suggestion of a [CodeCompleter]: [label] in the list, [detail] beside it, [icon] for its kind; [insertText] goes into the text. */
@Immutable
data class CodeCompletionItem(
    val label: String,
    val detail: String? = null,
    val icon: ImageVector? = null,
    val insertText: String = label,
)

/**
 * What a [CodeCompleter] offers at a place: the [items], and the range from [start] to [end] that the chosen
 * one replaces, normally the word at the cursor (it may reach past the cursor).
 */
@Immutable
class CodeCompletions(val start: Int, val end: Int, val items: List<CodeCompletionItem>)

/**
 * The app's completion for a [CodeEditor]: what may be typed at [cursor] of [state]; null (or no items) when there
 * is nothing to offer. Called on the main thread when the user starts typing a word (with live completion) and on
 * Ctrl+Space or [CodeEditorState.showCompletion]. The editor narrows the list itself as the typing goes on, with the
 * part of the range typed so far, as the command palette matches (case and Turkish letters folded, the start of
 * the label first, then word starts, then letters in order): return everything that fits the place, not only what
 * starts with the typed letters. Keep it quick (cache what it reads, rebuild it when the document's version changes).
 */
fun interface CodeCompleter {
    fun complete(state: CodeEditorState, cursor: Int): CodeCompletions?
}

/** An item of the open list: the item, and the characters of its label that matched the typed part. */
@Immutable
internal class ShownItem(val item: CodeCompletionItem, val matched: List<IntRange>)

/**
 * The completion list of one [CodeEditor] while it is open: where it applies, the items and the one highlighted,
 * and where the cursor is on screen. The list closes when the cursor leaves the word, the typed part matches no
 * item, the editor loses the focus, Esc is pressed or an item is chosen.
 */
@Stable
internal class CompletionSession {
    var shown by mutableStateOf(emptyList<ShownItem>())
        private set
    var highlighted by mutableIntStateOf(0)

    /** The cursor's line in the editor's view: the left edge of the cursor, the line's top and bottom, in pixels. */
    var anchor by mutableStateOf(IntArray(3))

    val isOpen: Boolean get() = shown.isNotEmpty()

    private var start = 0

    /** Characters after the cursor that a chosen item replaces as well (the rest of the word). */
    private var afterCursor = 0
    private var items: List<CodeCompletionItem> = emptyList()

    /**
     * The start of the word whose list is done: closed with Esc, or nothing offered for it. Typing on in that word
     * neither opens the list again nor asks the completer again.
     */
    private var dismissedWord = -1

    /**
     * Asks [completer] for the items at [cursor]; opens the list when some match what is typed. [typed]: the user
     * typed into a word (live completion), which a list closed with Esc in the same word does not answer.
     */
    fun request(state: CodeEditorState, completer: CodeCompleter, cursor: Int, typed: Boolean) {
        if (typed) {
            val word = wordStart(state, cursor)
            if (word == cursor) {
                // no word at the cursor (its letters were deleted, a space typed): the next one starts afresh
                dismissedWord = -1
                return
            }
            if (word == dismissedWord) return
        }
        dismissedWord = -1
        val offered = completer.complete(state, cursor)
        if (offered == null || offered.items.isEmpty()) {
            close()
        } else {
            start = offered.start.coerceIn(0, cursor)
            afterCursor = (offered.end - cursor).coerceAtLeast(0)
            items = offered.items
            refilter(state, cursor, collapsed = true)
        }
        // a word nothing fits: more letters narrow the list further, so the completer is not asked again for it
        if (typed && !isOpen) dismissedWord = wordStart(state, cursor)
    }

    private fun wordStart(state: CodeEditorState, cursor: Int): Int {
        var word = cursor
        while (word > 0 && isWordChar(state.charAt(word - 1))) word--
        return word
    }

    /** The text changed or the cursor moved to [cursor]: narrows the list to what is typed, or closes it once the cursor left the word. */
    fun refilter(state: CodeEditorState, cursor: Int, collapsed: Boolean) {
        if (items.isEmpty()) return
        if (!collapsed || cursor < start || cursor > start + MAX_TYPED || cursor > state.length) {
            close()
            return
        }
        val typed = state.textIn(start, cursor)
        if (typed.any { !isWordChar(it) }) {
            close()
            return
        }
        val matches = items.mapIndexedNotNull { index, item ->
            PaletteMatcher.match(typed, item.label)?.let { Triple(index, item, it) }
        }
        // the best first; items the completer put first stay first among equals
        val next = matches.sortedWith(compareByDescending<Triple<Int, CodeCompletionItem, PaletteMatcher.Match>> { it.third.score }.thenBy { it.first })
            .map { ShownItem(it.second, it.third.ranges) }
        if (next.isEmpty()) {
            close()
            return
        }
        val previous = shown.getOrNull(highlighted)?.item
        shown = next
        highlighted = next.indexOfFirst { it.item == previous }.coerceAtLeast(0)
    }

    /** Puts the [index]th item in place of the word, as one step of the undo history. */
    fun accept(state: CodeEditorState, index: Int) {
        val item = shown.getOrNull(index)?.item ?: return
        val from = start
        val to = (state.cursor + afterCursor).coerceAtMost(state.length)
        close()
        state.edit {
            state.replace(from, to, item.insertText)
            state.select(from + item.insertText.length)
        }
    }

    fun close() {
        if (shown.isNotEmpty()) shown = emptyList()
        items = emptyList()
        highlighted = 0
    }

    /** The list's keys while it is open: arrows and pages move, Enter and Tab choose, Esc closes. */
    fun key(state: CodeEditorState, event: KeyEvent): Boolean {
        if (!isOpen || event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return false
        val count = shown.size
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN -> highlighted = (highlighted + 1) % count
            KeyEvent.KEYCODE_DPAD_UP -> highlighted = (highlighted - 1 + count) % count
            KeyEvent.KEYCODE_PAGE_DOWN -> highlighted = (highlighted + PAGE).coerceAtMost(count - 1)
            KeyEvent.KEYCODE_PAGE_UP -> highlighted = (highlighted - PAGE).coerceAtLeast(0)
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_TAB -> {
                if (event.isShiftPressed) return false
                accept(state, highlighted)
            }
            KeyEvent.KEYCODE_ESCAPE -> {
                dismissedWord = start
                close()
            }
            else -> return false
        }
        return true
    }

    companion object {
        /** Rows moved by Page Up and Page Down. */
        const val PAGE = 5

        /** A word longer than this is no longer completed. */
        const val MAX_TYPED = 200

        fun isWordChar(char: Char): Boolean = char.isLetterOrDigit() || char == '_'
    }
}

/**
 * The open completion list, inside the editor's box (so it stays within the editor, which ends above the
 * on-screen keyboard): under the cursor's line when it fits there, above it otherwise, kept within the
 * editor's edges. A tap or a click chooses an item, the mouse highlights the one under it.
 */
@Composable
internal fun CompletionList(session: CompletionSession, boxWidth: Int, boxHeight: Int, onAccept: (Int) -> Unit) {
    val shown = session.shown
    if (shown.isEmpty() || boxWidth == 0) return
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val density = LocalDensity.current
    val touch = dimens.minTarget >= 48.dp
    val rowHeight: Dp = if (touch) dimens.minTarget else dimens.controlHeight
    val rows = shown.size.coerceAtMost(if (touch) TOUCH_ROWS else POINTER_ROWS)
    val inner = dimens.gapXs
    val shape = RoundedCornerShape(dimens.radiusCard)
    val edge = with(density) { dimens.gapS.roundToPx() }
    val width = with(density) { LIST_WIDTH.roundToPx() }.coerceAtMost(boxWidth - 2 * edge)
    val height = with(density) { (rowHeight * rows + inner * 2).roundToPx() }
    val (cursorX, lineTop, lineBottom) = session.anchor.let { Triple(it[0], it[1], it[2]) }
    val below = lineBottom + height <= boxHeight - edge
    val y = when {
        below -> lineBottom
        lineTop - height >= edge -> lineTop - height
        // room on neither side: where there is more, pressed against the edge
        else -> if (boxHeight - lineBottom > lineTop) (boxHeight - edge - height).coerceAtLeast(0) else edge
    }
    val x = cursorX.coerceIn(edge, (boxWidth - edge - width).coerceAtLeast(edge))
    val list = rememberLazyListState()
    LaunchedEffect(session.highlighted, shown) {
        val first = list.firstVisibleItemIndex
        val index = session.highlighted
        if (index < first) list.scrollToItem(index) else if (index >= first + rows) list.scrollToItem(index - rows + 1)
    }
    Box(
        Modifier
            .absoluteOffset { IntOffset(x, y) }
            .width(with(density) { width.toDp() })
            .height(with(density) { height.toDp() })
            .shadow(12.dp, shape)
            .background(colors.surfaceRaised, shape)
            .border(1.dp, colors.borderStrong, shape)
            .padding(inner),
    ) {
        LazyColumn(state = list) {
            itemsIndexed(shown) { index, entry ->
                CompletionRow(entry, index == session.highlighted, rowHeight,
                    onHover = { session.highlighted = index }, onClick = { onAccept(index) })
            }
        }
    }
}

@Composable
private fun CompletionRow(entry: ShownItem, highlighted: Boolean, height: Dp, onHover: () -> Unit, onClick: () -> Unit) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val type = DeskTheme.type
    val item = entry.item
    val foreground = if (highlighted) colors.onSelection else colors.text
    val description = if (item.detail == null) item.label else "${item.label}, ${item.detail}"
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .background(if (highlighted) colors.selection else colors.surfaceRaised, RoundedCornerShape(dimens.radiusSmall))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Enter || event.type == PointerEventType.Move) onHover()
                    }
                }
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
                selected = highlighted
                onClick { onClick(); true }
            }
            .padding(horizontal = dimens.gapS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.gapS),
    ) {
        if (item.icon != null) {
            Icon(item.icon, contentDescription = null, tint = if (highlighted) colors.onSelection else colors.textSecondary, modifier = Modifier.size(dimens.icon))
        }
        Text(
            highlight(item.label, entry.matched, SpanStyle(fontWeight = FontWeight.Bold)),
            style = type.body.copy(fontFamily = type.mono.fontFamily),
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (item.detail != null) {
            Text(item.detail, style = type.labelSmall, color = if (highlighted) colors.onSelection else colors.textTertiary, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

/** How wide the list is at most. */
private val LIST_WIDTH = 360.dp

/** How many rows show before the list scrolls. */
private const val POINTER_ROWS = 8
private const val TOUCH_ROWS = 5
