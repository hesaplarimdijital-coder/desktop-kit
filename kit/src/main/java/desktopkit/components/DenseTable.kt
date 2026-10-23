package desktopkit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import desktopkit.input.ContextMenuArea
import desktopkit.shell.WidthClass
import desktopkit.shell.currentWidthClass
import desktopkit.theme.DeskTheme
import androidx.compose.ui.input.pointer.isCtrlPressed as pointerCtrl
import androidx.compose.ui.input.pointer.isMetaPressed as pointerMeta
import androidx.compose.ui.input.pointer.isShiftPressed as pointerShift

/** Width of a table column: a share of the free space or a fixed size. */
sealed interface ColumnWidth {
    data class Weight(val weight: Float) : ColumnWidth
    data class Fixed(val width: Dp) : ColumnWidth
}

@Immutable
class TableColumn<T>(
    val title: String,
    val width: ColumnWidth,
    val alignEnd: Boolean = false,
    /** Monospace for durations, formats and sizes. */
    val mono: Boolean = false,
    val sortable: Boolean = true,
    /** The cells' text style, when neither [mono] nor the default (strong first column, body after) fits: a serif title column, say. */
    val textStyle: TextStyle? = null,
    /**
     * Hides the column in windows narrower than this class, e.g. `WidthClass.Medium` keeps a "Kind"
     * column off phones so the name gets the room. Null (the default) shows it everywhere.
     */
    val hideBelow: WidthClass? = null,
    val value: (T) -> String,
)

@Immutable
data class TableSort(val column: Int, val ascending: Boolean)

/**
 * Dense, desktop-style table: sticky header with sortable columns, 32 dp rows in
 * pointer mode (taller in touch mode), hover and selection states, keyboard focus per row.
 * [rowMenu] gives every row a context menu (right click, long press, Menu key).
 *
 * Single selection: the app keeps [selectedKey] and usually sets it in [onRowClick].
 *
 * Multi-selection is on when [onSelectionChange] is set; the app keeps [selectedKeys] (keys as
 * returned by [key]) and stores what [onSelectionChange] hands it:
 * - click: selects only that row and calls [onRowClick];
 * - Ctrl+click (Meta+click): adds or removes the row;
 * - Shift+click: selects the rows from the last clicked row to this one (Ctrl+Shift adds them);
 * - Ctrl+A on a focused row: selects every row; Shift+Up/Down extends from the focused row;
 * - [toggleOnClick]: every click or tap adds or removes its row, for an app's touch "select" mode;
 * - opening a row's menu on a selected row keeps the selection, on any other row selects just that row.
 * [onRowClick] runs for plain clicks only (not for Ctrl, Shift or [toggleOnClick] clicks).
 * [rowMenu] receives the row it was opened on; the selection it acts on is the app's
 * [selectedKeys], already updated when the menu opens.
 */
@Composable
fun <T> DenseTable(
    columns: List<TableColumn<T>>,
    rows: List<T>,
    key: (T) -> Any,
    onRowClick: (T, Int) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    selectedKey: Any? = null,
    sort: TableSort? = null,
    onSortChange: ((TableSort) -> Unit)? = null,
    rowMenu: ((T, Int) -> List<MenuEntry>)? = null,
    rowMenuLabel: String? = null,
    contentPadding: PaddingValues = PaddingValues(bottom = 8.dp),
    selectedKeys: Set<Any>? = null,
    onSelectionChange: ((Set<Any>) -> Unit)? = null,
    toggleOnClick: Boolean = false,
) {
    val dimens = DeskTheme.dimens
    val widthClass = currentWidthClass()
    // original indices are kept: sorting and the strong first column refer to them
    val shownColumns = columns.withIndex().filter { (_, column) -> column.hideBelow == null || widthClass >= column.hideBelow }
    val multi = onSelectionChange != null
    val selection = selectedKeys ?: emptySet()
    val tracker = remember { SelectionTracker() }
    val focusManager = LocalFocusManager.current
    val keysInOrder = { rows.map(key) }
    fun update(result: TableSelection.Result) {
        tracker.anchor = result.anchor
        if (result.selection != selection) onSelectionChange?.invoke(result.selection)
    }
    Column(
        modifier
            .fillMaxSize()
            .then(
                if (!multi) Modifier else Modifier
                    .pointerInput(Unit) {
                        // remembers the keys held at each press; the row's click comes after it
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.type == PointerEventType.Press) {
                                    val mods = event.keyboardModifiers
                                    tracker.toggle = mods.pointerCtrl || mods.pointerMeta
                                    tracker.range = mods.pointerShift
                                }
                            }
                        }
                    }
                    .onPreviewKeyEvent { event ->
                        // Enter and Space click the focused row: remember the keys held with them too
                        tracker.toggle = event.isCtrlPressed || event.isMetaPressed
                        tracker.range = event.isShiftPressed
                        false
                    }
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        val command = event.isCtrlPressed || event.isMetaPressed
                        when {
                            event.key == Key.A && command && !event.isShiftPressed && !event.isAltPressed -> {
                                update(TableSelection.all(keysInOrder(), tracker.anchor))
                                true
                            }
                            (event.key == Key.DirectionUp || event.key == Key.DirectionDown) && event.isShiftPressed && !command -> {
                                val focused = tracker.focused ?: return@onKeyEvent false
                                val down = event.key == Key.DirectionDown
                                val result = TableSelection.extend(keysInOrder(), tracker.anchor, focused, if (down) 1 else -1)
                                    ?: return@onKeyEvent true
                                update(result)
                                focusManager.moveFocus(if (down) FocusDirection.Down else FocusDirection.Up)
                                true
                            }
                            else -> false
                        }
                    },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = dimens.minTarget.coerceAtLeast(28.dp))
                .padding(horizontal = dimens.pagePadding - 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            shownColumns.forEach { (index, column) ->
                HeaderCell(column, index, sort, onSortChange)
            }
        }
        HorizontalRule()
        LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
            itemsIndexed(rows, key = { _, item -> key(item) }) { index, item ->
                val itemKey = key(item)
                val onClick = {
                    if (!multi) {
                        onRowClick(item, index)
                    } else {
                        val toggle = toggleOnClick || tracker.toggle
                        val range = !toggleOnClick && tracker.range
                        update(TableSelection.click(keysInOrder(), selection, tracker.anchor, itemKey, toggle = toggle, range = range))
                        if (!toggle && !range) onRowClick(item, index)
                    }
                    tracker.toggle = false
                    tracker.range = false
                }
                val row = @Composable {
                    TableRow(
                        columns = shownColumns,
                        item = item,
                        selected = itemKey in selection || (selectedKey != null && itemKey == selectedKey),
                        onClick = onClick,
                        modifier = if (!multi) Modifier else Modifier.onFocusChanged { focus ->
                            if (focus.isFocused) tracker.focused = itemKey else if (tracker.focused == itemKey) tracker.focused = null
                        },
                    )
                }
                if (rowMenu != null) {
                    ContextMenuArea(
                        entries = { rowMenu(item, index) },
                        accessibilityLabel = rowMenuLabel,
                        onOpen = if (!multi) null else ({ update(TableSelection.menu(selection, tracker.anchor, itemKey)) }),
                    ) { row() }
                } else {
                    row()
                }
            }
        }
    }
}

/** Keys held at the last press, the anchor of Shift ranges and the focused row; not state, nothing redraws from it. */
private class SelectionTracker {
    var toggle = false
    var range = false
    var anchor: Any? = null
    var focused: Any? = null
}

/** The multi-selection rules of [DenseTable], free of Compose so they can be tested on their own. */
internal object TableSelection {

    /** The new selection and the row Shift ranges start from. */
    data class Result(val selection: Set<Any>, val anchor: Any?)

    /** A click on [clicked]: [toggle] for Ctrl (or a toggling tap), [range] for Shift. */
    fun click(keys: List<Any>, selection: Set<Any>, anchor: Any?, clicked: Any, toggle: Boolean, range: Boolean): Result {
        if (range) {
            val span = span(keys, anchor, clicked)
            if (span != null) return Result(if (toggle) selection + span else span, anchor)
        }
        return when {
            toggle -> Result(if (clicked in selection) selection - clicked else selection + clicked, clicked)
            else -> Result(setOf(clicked), clicked)
        }
    }

    /** Ctrl+A: every row; keeps the anchor. */
    fun all(keys: List<Any>, anchor: Any?): Result = Result(keys.toCollection(LinkedHashSet()), anchor)

    /** Shift+Up/Down from [focused]: the rows from the anchor (or the focused row) to the next row; null at either end. */
    fun extend(keys: List<Any>, anchor: Any?, focused: Any, delta: Int): Result? {
        val from = keys.indexOf(focused)
        if (from < 0) return null
        val to = from + delta
        if (to !in keys.indices) return null
        val start = anchor?.takeIf { it in keys } ?: focused
        return Result(span(keys, start, keys[to]) ?: setOf(keys[to]), start)
    }

    /** Opening a row's menu: a selected row keeps the selection, any other row becomes the only one. */
    fun menu(selection: Set<Any>, anchor: Any?, row: Any): Result =
        if (row in selection) Result(selection, anchor) else Result(setOf(row), row)

    private fun span(keys: List<Any>, from: Any?, to: Any): Set<Any>? {
        val a = keys.indexOf(from ?: return null)
        val b = keys.indexOf(to)
        if (a < 0 || b < 0) return null
        return keys.subList(minOf(a, b), maxOf(a, b) + 1).toCollection(LinkedHashSet())
    }
}

@Composable
private fun <T> RowScope.HeaderCell(column: TableColumn<T>, index: Int, sort: TableSort?, onSortChange: ((TableSort) -> Unit)?) {
    val colors = DeskTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val active = sort?.column == index
    val clickable = onSortChange != null && column.sortable
    val shape = RoundedCornerShape(4.dp)
    Row(
        modifier = Modifier
            .cell(this, column.width)
            .heightIn(min = DeskTheme.dimens.minTarget)
            .focusRing(state, shape)
            .background(if (clickable) stateBackground(state) else colors.background, shape)
            .then(
                if (clickable) Modifier.deskClickable(interaction, {
                    onSortChange?.invoke(TableSort(index, if (active) !(sort?.ascending ?: true) else true))
                }, role = Role.Button) else Modifier,
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (column.alignEnd) Spacer(Modifier.weight(1f))
        Text(column.title, style = DeskTheme.type.labelSmall, color = if (active) colors.text else colors.textTertiary, maxLines = 1)
        if (active) {
            Spacer(Modifier.width(4.dp))
            Icon(if (sort?.ascending != false) DeskIcons.ChevronUp else DeskIcons.ChevronDown, contentDescription = null,
                tint = colors.textSecondary, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun <T> TableRow(columns: List<IndexedValue<TableColumn<T>>>, item: T, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    val background by animatedStateBackground(state, selected)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.pagePadding - 12.dp)
            .heightIn(min = dimens.rowHeight.coerceAtLeast(dimens.minTarget))
            .focusRing(state, shape)
            .background(background, shape)
            .semantics { this.selected = selected }
            .deskClickable(interaction, onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        columns.forEach { (index, column) ->
            Box(Modifier.cell(this, column.width).padding(horizontal = 8.dp), contentAlignment = if (column.alignEnd) Alignment.CenterEnd else Alignment.CenterStart) {
                Text(
                    text = column.value(item),
                    style = column.textStyle ?: if (column.mono) DeskTheme.type.mono else if (index == 0) DeskTheme.type.bodyStrong else DeskTheme.type.body,
                    color = when {
                        selected -> colors.onSelection
                        index == 0 -> colors.text
                        else -> colors.textSecondary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (column.alignEnd) TextAlign.End else TextAlign.Start,
                )
            }
        }
    }
}

/** Column width inside a table row: weight needs the RowScope receiver. */
private fun Modifier.cell(scope: RowScope, width: ColumnWidth): Modifier = with(scope) {
    when (width) {
        is ColumnWidth.Weight -> this@cell.weight(width.weight)
        is ColumnWidth.Fixed -> this@cell.width(width.width)
    }
}
