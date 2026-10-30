package desktopkit.components

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.State
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
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
import desktopkit.R
import desktopkit.input.ContextMenuArea
import desktopkit.settings.DeskPreferences
import desktopkit.shell.WidthClass
import desktopkit.shell.currentWidthClass
import desktopkit.theme.DeskGlass
import desktopkit.theme.DeskTheme
import desktopkit.theme.GlassThickness
import desktopkit.theme.InputMode
import desktopkit.theme.glass
import desktopkit.theme.glassSource
import dev.chrisbanes.haze.HazeState
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
    /** Stable name of the column for [TableColumnsState] (order and visibility are stored by it); the title by default. */
    val id: String = title,
    val value: (T) -> String,
)

@Immutable
data class TableSort(val column: Int, val ascending: Boolean)

/**
 * Dense, desktop-style table: sticky header with sortable columns, 32 dp rows in
 * pointer mode (taller in touch mode), hover and selection states, keyboard focus per row.
 * [rowMenu] gives every row a context menu (right click, long press, Menu key).
 *
 * The rows scroll beneath the header, which turns to frosted glass once they do ([glassHeader]).
 * With [columnsState] the header's context menu (right click, long press, Menu key on a title) is
 * the column chooser: show or hide columns, move one left or right, reset; [rememberTableColumnsState]
 * keeps the choice in [desktopkit.settings.DeskPreferences].
 *
 * Single selection: the app keeps [selectedKey] and usually sets it in [onRowClick].
 *
 * Multi-selection is on when [onSelectionChange] is set; the app keeps [selectedKeys] (keys as
 * returned by [key]) and stores what [onSelectionChange] hands it:
 * - click: selects only that row and calls [onRowClick];
 * - Ctrl+click (Meta+click): adds or removes the row;
 * - Shift+click: selects the rows from the last clicked row to this one (Ctrl+Shift adds them);
 * - Ctrl+A on a focused row: selects every row; Shift+Up/Down extends from the focused row; Esc clears;
 * - [toggleOnClick]: every click or tap adds or removes its row, for an app's touch "select" mode;
 * - opening a row's menu (right click, long press) on a selected row keeps the selection, on any
 *   other row selects just that row;
 * - a tap with a finger or a pen only opens the row unless rows are being picked: in the select
 *   mode, or once some are selected, a tap adds or removes one. The pointer decides, not the
 *   layout: a mouse click selects in a touch layout too.
 * [onRowClick] runs for plain clicks and opening taps (not for Ctrl, Shift or [toggleOnClick] clicks).
 * [rowMenu] receives the row it was opened on; the selection it acts on is the app's
 * [selectedKeys], already updated when the menu opens.
 *
 * [rowDrag] lets rows be dragged with a mouse to a [dropZone] (the sidebar, a list, another window
 * of the app): it builds what travels for the row under the pointer, usually the whole selection
 * when that row is selected. [loading] shows breathing placeholder rows while [rows] is empty.
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
    columnsState: TableColumnsState? = null,
    glassHeader: Boolean = true,
    loading: Boolean = false,
    rowDrag: ((T, Int) -> DeskDragPayload?)? = null,
) {
    val dimens = DeskTheme.dimens
    val colors = DeskTheme.colors
    val density = LocalDensity.current
    val widthClass = currentWidthClass()
    // original indices are kept: sorting and the strong first column refer to them
    val arranged = columnsState?.arrange(columns) ?: columns.withIndex().toList()
    val shownColumns = arranged.filter { (_, column) -> column.hideBelow == null || widthClass >= column.hideBelow }
    val glassMode = DeskGlass.mode
    val rowsGlass = remember { HazeState() }
    // the rows start under the header; its height is known after the first layout
    val headerPx = remember { mutableIntStateOf(with(density) { (dimens.minTarget.coerceAtLeast(28.dp) + 1.dp).roundToPx() }) }
    val padding = remember(contentPadding, density) { HeaderPadding(contentPadding, headerPx, density) }
    val scrolled by remember(state) { derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0 } }
    val chooserLabels = ColumnChooserLabels(
        title = stringResource(R.string.dk_columns),
        moveLeft = stringResource(R.string.dk_columns_move_left),
        moveRight = stringResource(R.string.dk_columns_move_right),
        reset = stringResource(R.string.dk_columns_reset),
    )
    val multi = onSelectionChange != null
    val selection = selectedKeys ?: emptySet()
    val touch = DeskTheme.inputMode == InputMode.Touch
    val tracker = remember { SelectionTracker() }
    val focusManager = LocalFocusManager.current
    val keysInOrder = { rows.map(key) }
    // while the rows load: breathing placeholder rows under the header
    val skeleton = loading && rows.isEmpty()
    val pulse = if (skeleton) rememberSkeletonPulse() else null
    val loadingLabel = stringResource(R.string.dk_loading)
    fun update(result: TableSelection.Result) {
        tracker.anchor = result.anchor
        if (result.selection != selection) onSelectionChange?.invoke(result.selection)
    }
    Box(
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
                                    tracker.press(event)
                                }
                            }
                        }
                    }
                    .onPreviewKeyEvent { event ->
                        // Enter and Space click the focused row: remember the keys held with them too
                        tracker.toggle = event.isCtrlPressed || event.isMetaPressed
                        tracker.range = event.isShiftPressed
                        tracker.finger = false
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
                            event.key == Key.Escape && !command && selection.isNotEmpty() -> {
                                update(TableSelection.clear())
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
        LazyColumn(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .glassSource(rowsGlass, glassMode)
                .then(if (!skeleton) Modifier else Modifier.semantics { contentDescription = loadingLabel }),
            contentPadding = padding,
        ) {
            if (pulse != null) {
                items(8) { i -> SkeletonTableRow(shownColumns, i, pulse) }
            }
            itemsIndexed(rows, key = { _, item -> key(item) }) { index, item ->
                val itemKey = key(item)
                val onClick = {
                    // the press's pointer when there was one; TalkBack's double tap follows the layout
                    val finger = tracker.finger ?: touch
                    if (!multi) {
                        onRowClick(item, index)
                    } else if (finger && !toggleOnClick && selection.isEmpty() && !tracker.toggle && !tracker.range) {
                        // a tap opens; for a finger, selecting is for the select mode
                        onRowClick(item, index)
                    } else {
                        // while a finger is picking rows, a tap adds or removes one
                        val toggle = toggleOnClick || tracker.toggle || finger
                        val range = !toggleOnClick && tracker.range
                        update(TableSelection.click(keysInOrder(), selection, tracker.anchor, itemKey, toggle = toggle, range = range))
                        if (!toggle && !range) onRowClick(item, index)
                    }
                    tracker.clicked()
                }
                val row = @Composable {
                    TableRow(
                        columns = shownColumns,
                        item = item,
                        selected = itemKey in selection || (selectedKey != null && itemKey == selectedKey),
                        onClick = onClick,
                        modifier = (if (!multi) Modifier else Modifier.onFocusChanged { focus ->
                            if (focus.isFocused) tracker.focused = itemKey else if (tracker.focused == itemKey) tracker.focused = null
                        }).dragSource(enabled = rowDrag != null) { rowDrag?.invoke(item, index) },
                    )
                }
                if (rowMenu != null) {
                    ContextMenuArea(
                        entries = { rowMenu(item, index) },
                        accessibilityLabel = rowMenuLabel,
                        // the menu acts on the selection: a row outside it becomes the selection
                        onOpen = if (!multi) null else ({ update(TableSelection.menu(selection, tracker.anchor, itemKey)) }),
                    ) { row() }
                } else {
                    row()
                }
            }
        }
        // sticky header over the rows: glass once rows pass beneath it
        Column(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { headerPx.intValue = it.height }
                .glass(DeskGlass.chrome(colors, GlassThickness.Thin), source = rowsGlass, mode = glassMode, enabled = glassHeader && scrolled),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = dimens.minTarget.coerceAtLeast(28.dp))
                    .padding(horizontal = dimens.pagePadding - 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                shownColumns.forEach { (index, column) ->
                    if (columnsState != null) {
                        ContextMenuArea(
                            entries = { columnChooserEntries(columns, columnsState, column.id, chooserLabels) },
                            modifier = Modifier.cell(this, column.width),
                            accessibilityLabel = chooserLabels.title,
                        ) {
                            Row { HeaderCell(column, index, sort, onSortChange, Modifier.weight(1f)) }
                        }
                    } else {
                        HeaderCell(column, index, sort, onSortChange, Modifier.cell(this, column.width))
                    }
                }
            }
            HorizontalRule()
        }
    }
}

/** The caller's padding with the header's height added on top. */
private class HeaderPadding(
    private val base: PaddingValues,
    private val headerPx: androidx.compose.runtime.MutableIntState,
    private val density: androidx.compose.ui.unit.Density,
) : PaddingValues {
    override fun calculateTopPadding() = base.calculateTopPadding() + with(density) { headerPx.intValue.toDp() }
    override fun calculateBottomPadding() = base.calculateBottomPadding()
    override fun calculateLeftPadding(layoutDirection: androidx.compose.ui.unit.LayoutDirection) = base.calculateLeftPadding(layoutDirection)
    override fun calculateRightPadding(layoutDirection: androidx.compose.ui.unit.LayoutDirection) = base.calculateRightPadding(layoutDirection)
}

/** Keys held at the last press, the anchor of Shift ranges and the focused row; not state, nothing redraws from it. */
internal class SelectionTracker {
    var toggle = false
    var range = false

    /**
     * Whether the press behind the next click was a finger's or a pen's (true) or a mouse's or a
     * key's (false); null when the click came without one (TalkBack).
     */
    var finger: Boolean? = null
    var anchor: Any? = null
    var focused: Any? = null

    fun press(event: PointerEvent) {
        val down = event.changes.firstOrNull { it.pressed } ?: return
        finger = down.type != PointerType.Mouse
    }

    fun clicked() {
        toggle = false
        range = false
        finger = null
    }
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

    /** Esc: nothing selected, no anchor. */
    fun clear(): Result = Result(emptySet(), null)

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
private fun <T> HeaderCell(column: TableColumn<T>, index: Int, sort: TableSort?, onSortChange: ((TableSort) -> Unit)?, modifier: Modifier) {
    val colors = DeskTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val active = sort?.column == index
    val clickable = onSortChange != null && column.sortable
    val shape = RoundedCornerShape(4.dp)
    Row(
        modifier = modifier
            .heightIn(min = DeskTheme.dimens.minTarget)
            .focusRing(state, shape)
            .background(if (clickable) stateBackground(state) else Color.Transparent, shape)
            .then(
                if (clickable) Modifier.deskClickable(interaction, {
                    onSortChange(TableSort(index, if (active) !sort.ascending else true))
                }, role = Role.Button) else Modifier,
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (column.alignEnd) Spacer(Modifier.weight(1f))
        Text(column.title, style = DeskTheme.type.labelSmall, color = if (active) colors.text else colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (active) {
            Spacer(Modifier.width(4.dp))
            Icon(if (sort.ascending) DeskIcons.ChevronUp else DeskIcons.ChevronDown, contentDescription = null,
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

/** A table row while the rows load: one breathing bar per cell, of varying length. */
@Composable
private fun <T> SkeletonTableRow(columns: List<IndexedValue<TableColumn<T>>>, index: Int, pulse: State<Float>) {
    val dimens = DeskTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.pagePadding - 12.dp)
            .heightIn(min = dimens.rowHeight.coerceAtLeast(dimens.minTarget))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        columns.forEachIndexed { position, (_, column) ->
            Box(Modifier.cell(this, column.width).padding(horizontal = 8.dp), contentAlignment = if (column.alignEnd) Alignment.CenterEnd else Alignment.CenterStart) {
                val fraction = 0.4f + ((index * 7 + position * 3) % 5) * 0.1f
                SkeletonBlock(Modifier.fillMaxWidth(fraction).height(10.dp), pulse)
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

/** Texts of the column chooser menu. */
internal class ColumnChooserLabels(val title: String, val moveLeft: String, val moveRight: String, val reset: String)

/**
 * The column chooser as menu entries: every column with a check mark (the last visible one cannot
 * be hidden), then "move left/right" for [clickedId] and "reset".
 */
internal fun <T> columnChooserEntries(
    columns: List<TableColumn<T>>,
    state: TableColumnsState,
    clickedId: String,
    labels: ColumnChooserLabels,
): List<MenuEntry> {
    val ids = columns.map { it.id }
    val (order, hidden) = TableColumnsLayout.normalize(ids, state.order, state.hidden)
    val visibleCount = order.count { it !in hidden }
    val byId = columns.associateBy { it.id }
    val toggles = order.mapNotNull { id ->
        val column = byId[id] ?: return@mapNotNull null
        val visible = id !in hidden
        MenuEntry.Item(column.title, { state.toggle(id, ids) }, checked = visible, enabled = !(visible && visibleCount <= 1))
    }
    val visibleOrder = order.filter { it !in hidden }
    val position = visibleOrder.indexOf(clickedId)
    return toggles + MenuEntry.Separator + listOf(
        MenuEntry.Item(labels.moveLeft, { state.move(clickedId, -1, ids) }, icon = DeskIcons.ChevronLeft, enabled = position > 0),
        MenuEntry.Item(labels.moveRight, { state.move(clickedId, 1, ids) }, icon = DeskIcons.ChevronRight,
            enabled = position >= 0 && position < visibleOrder.size - 1),
        MenuEntry.Separator,
        MenuEntry.Item(labels.reset, { state.reset() }),
    )
}

/**
 * Which columns a [DenseTable] shows and in what order, by [TableColumn.id]. Columns the state
 * does not know yet (added in a later app version) are appended visible; stored ids that no
 * longer exist are ignored. At least one column always stays visible.
 */
@Stable
class TableColumnsState(
    initialOrder: List<String> = emptyList(),
    initialHidden: Set<String> = emptySet(),
    private val onChange: (order: List<String>, hidden: Set<String>) -> Unit = { _, _ -> },
) {
    var order: List<String> by mutableStateOf(initialOrder)
        private set
    var hidden: Set<String> by mutableStateOf(initialHidden)
        private set

    /** The shown columns of [columns] in the chosen order, with their original indices. */
    fun <T> arrange(columns: List<TableColumn<T>>): List<IndexedValue<TableColumn<T>>> {
        val ids = columns.map { it.id }
        val (order, hidden) = TableColumnsLayout.normalize(ids, order, hidden)
        return order.filter { it !in hidden }.map { id -> ids.indexOf(id).let { IndexedValue(it, columns[it]) } }
    }

    /** Shows or hides [id] among [ids] (the table's columns). */
    fun toggle(id: String, ids: List<String>) {
        val (order, hidden) = TableColumnsLayout.normalize(ids, order, hidden)
        store(order, TableColumnsLayout.toggle(order, hidden, id))
    }

    /** Moves [id] [delta] places among the visible columns (negative: towards the start). */
    fun move(id: String, delta: Int, ids: List<String>) {
        val (order, hidden) = TableColumnsLayout.normalize(ids, order, hidden)
        store(TableColumnsLayout.move(order, hidden, id, delta), hidden)
    }

    /** Back to the table's own order, every column visible. */
    fun reset() = store(emptyList(), emptySet())

    private fun store(order: List<String>, hidden: Set<String>) {
        this.order = order
        this.hidden = hidden
        onChange(order, hidden)
    }

    companion object {
        val Saver: Saver<TableColumnsState, List<String>> = Saver(
            save = { listOf(it.order.joinToString("\u001F"), it.hidden.joinToString("\u001F")) },
            restore = { TableColumnsState(it[0].split('\u001F').filter(String::isNotEmpty), it[1].split('\u001F').filter(String::isNotEmpty).toSet()) },
        )
    }
}

/**
 * A [TableColumnsState] for the table [key], stored in [prefs] so the choice survives restarts;
 * without [prefs] only in the saved instance state.
 */
@Composable
fun rememberTableColumnsState(key: String, prefs: DeskPreferences? = null): TableColumnsState {
    if (prefs != null) {
        return remember(prefs, key) {
            val stored = prefs.tableColumns(key)
            TableColumnsState(stored?.first.orEmpty(), stored?.second.orEmpty()) { order, hidden -> prefs.updateTableColumns(key, order, hidden) }
        }
    }
    return rememberSaveable(key, saver = TableColumnsState.Saver) { TableColumnsState() }
}

/** The column chooser's rules, free of Compose so they can be tested on their own. */
internal object TableColumnsLayout {

    /** Stored [order] limited to [ids], new ids appended; [hidden] limited to [ids], never every column. */
    fun normalize(ids: List<String>, order: List<String>, hidden: Set<String>): Pair<List<String>, Set<String>> {
        val known = order.filter { it in ids }.distinct()
        val full = known + ids.filter { it !in known }
        val hide = hidden.filter { it in ids }.toSet()
        return full to if (full.all { it in hide }) hide - full.first() else hide
    }

    /** Hides a visible [id] (unless it is the last visible one) or shows a hidden one. */
    fun toggle(order: List<String>, hidden: Set<String>, id: String): Set<String> = when {
        id in hidden -> hidden - id
        order.count { it !in hidden } <= 1 -> hidden
        else -> hidden + id
    }

    /** Swaps [id] with its [delta]-th visible neighbour; hidden columns keep their places. */
    fun move(order: List<String>, hidden: Set<String>, id: String, delta: Int): List<String> {
        val visible = order.filter { it !in hidden }
        val from = visible.indexOf(id)
        if (from < 0 || delta == 0) return order
        val to = (from + delta).coerceIn(0, visible.size - 1)
        if (to == from) return order
        val newVisible = visible.toMutableList().apply { add(to, removeAt(from)) }
        // put the visible ids back into the slots visible ids occupied
        var next = 0
        return order.map { if (it in hidden) it else newVisible[next++] }
    }
}
