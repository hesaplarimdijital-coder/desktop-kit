package desktopkit.components

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.State
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
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
import kotlinx.coroutines.launch
import desktopkit.input.TabNavigation
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.heading
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
    /**
     * Where a value too long for the cell is cut: at its end (the default) or, with
     * [TextOverflow.MiddleEllipsis], in its middle, so a file name keeps its extension and a path its
     * last folder ("Kayıt_2026-11-12…tam_kayit.flac").
     */
    val ellipsis: TextOverflow = TextOverflow.Ellipsis,
    /**
     * Draws the cell instead of [value]'s text: an icon (a star, a paperclip), a badge, a progress bar.
     * [value] still sorts the column and is what TalkBack reads unless the cell says otherwise.
     */
    val cell: (@Composable (T) -> Unit)? = null,
    /**
     * Makes the cell a button of its own that does not open the row: a star toggled in place.
     * [cellClickLabel] names what it does for TalkBack ("Star", "Remove star").
     */
    val onCellClick: ((T) -> Unit)? = null,
    val cellClickLabel: ((T) -> String)? = null,
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
 * - Back (the system's gesture or button) clears a selection being made, as Esc does: on a touch
 *   layout or in the select mode any selection, with a mouse several rows (one clicked row is just
 *   selected, and Back goes on). With [clearSelectionOnBack] false Back is left to the app, and a
 *   clear the app does not take (it keeps the rows) hands Back on to it. Compose asks the most
 *   recently composed back handler first and the table's is composed with the table: inside a
 *   [desktopkit.shell.DetailTransition] detail the detail closes first, and an app's own handler on
 *   the same screen may come before or after it. When the app's must come first, turn this off and
 *   clear the selection in it.
 * [onRowClick] runs for plain clicks and opening taps (not for Ctrl, Shift or [toggleOnClick] clicks).
 * [rowMenu] receives the row it was opened on; the selection it acts on is the app's
 * [selectedKeys], already updated when the menu opens.
 *
 * [rowDrag] lets rows be dragged with a mouse to a [dropZone] (the sidebar, a list, another window
 * of the app): it builds what travels for the row under the pointer, usually the whole selection
 * when that row is selected. [loading] shows breathing placeholder rows while [rows] is empty.
 *
 * [rowEmphasis] sets rows apart in bold and in the text colour: an unread message. [emptyContent] is
 * shown under the header when there are no rows and nothing is loading, usually a
 * [desktopkit.screens.MessageScreen] saying why and what to do (no device connected, nothing found,
 * the folder could not be read); without it an empty table is only its header.
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
    clearSelectionOnBack: Boolean = true,
    rowEmphasis: ((T) -> Boolean)? = null,
    emptyContent: (@Composable () -> Unit)? = null,
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
    val scope = rememberCoroutineScope()
    // read through these in the pointer and key handlers, which outlive a composition
    val keysRef = rememberUpdatedState(remember(rows) { rows.map(key) })
    val keySetRef = rememberUpdatedState(remember(keysRef.value) { keysRef.value.toHashSet() })
    val selectedKeyRef = rememberUpdatedState(selectedKey)
    val onSelectionChangeRef = rememberUpdatedState(onSelectionChange)
    val keysInOrder = { keysRef.value }
    // each composed row's focus, so the arrow keys can move it to a row that is scrolled into view
    val rowFocus = remember { HashMap<Any, FocusRequester>() }
    val latestSelection by rememberUpdatedState(selection)
    val latestMulti by rememberUpdatedState(multi)
    val latestDrag by rememberUpdatedState(rowDrag != null)
    val marquee = remember { Marquee() }
    val marqueeColor = colors.accent
    // while the rows load: breathing placeholder rows under the header
    val skeleton = loading && rows.isEmpty()
    val pulse = if (skeleton) rememberSkeletonPulse() else null
    val loadingLabel = stringResource(R.string.dk_loading)
    fun update(result: TableSelection.Result) {
        tracker.anchor = result.anchor
        if (result.selection != latestSelection) onSelectionChangeRef.value?.invoke(result.selection)
    }

    /**
     * The one row Tab reaches: the focused one, else the anchor of the selection or the selected
     * one, when it is in view; else the first row in view. Read when the focus moves, not composed.
     */
    fun rovingKey(): Any? {
        val keySet = keySetRef.value
        val active = tracker.focused ?: tracker.anchor ?: selectedKeyRef.value ?: latestSelection.firstOrNull()
        val visible = state.layoutInfo.visibleItemsInfo
        return if (active != null && active in keySet && visible.any { it.key == active }) active
        else visible.firstOrNull { it.key in keySet }?.key
    }

    /** Moves the keyboard focus to the row at [index], scrolling it into view. */
    fun focusRow(index: Int) {
        val keys = keysRef.value
        val keySet = keySetRef.value
        val target = keys.getOrNull(index.coerceIn(0, (keys.size - 1).coerceAtLeast(0))) ?: return
        val at = keys.indexOf(target)
        tracker.focused = target
        scope.launch {
            val info = state.layoutInfo
            val shown = info.visibleItemsInfo.firstOrNull { it.key == target }
            val limit = info.viewportSize.height - info.beforeContentPadding - info.afterContentPadding
            when {
                shown == null -> {
                    val below = at > (info.visibleItemsInfo.firstOrNull { it.key in keySet }?.index ?: 0)
                    val rowSize = info.visibleItemsInfo.firstOrNull { it.key in keySet }?.size ?: 0
                    state.scrollToItem(at)
                    // going down, the row comes in at the bottom, as a list moved by the keys does
                    if (below && rowSize > 0) state.scrollBy(-(limit - rowSize).coerceAtLeast(0).toFloat())
                }
                shown.offset < 0 -> state.scrollBy(shown.offset.toFloat())
                shown.offset + shown.size > limit -> state.scrollBy((shown.offset + shown.size - limit).toFloat())
            }
            withFrameNanos { }
            runCatching { rowFocus[target]?.requestFocus() }
        }
    }

    /** The rows that fit in view, for Page Up and Page Down. */
    fun pageRows(): Int = state.layoutInfo.visibleItemsInfo.count { it.key in keySetRef.value }.coerceAtLeast(2) - 1

    /** The row index under [y] (in the table), -1 above the first row in view's predecessor, past the end below the last. */
    fun indexAt(y: Float): Int {
        val keys = keysRef.value
        val keySet = keySetRef.value
        val info = state.layoutInfo
        val at = y - info.beforeContentPadding
        val rowsInView = info.visibleItemsInfo.filter { it.key in keySet }
        val hit = rowsInView.firstOrNull { at >= it.offset && at < it.offset + it.size }
        return when {
            hit != null -> keys.indexOf(hit.key)
            rowsInView.isEmpty() -> keys.size
            at < rowsInView.first().offset -> keys.indexOf(rowsInView.first().key) - 1
            else -> keys.indexOf(rowsInView.last().key) + 1
        }
    }

    if (multi && clearSelectionOnBack) ClearSelectionOnBack(selection, touch, toggleOnClick) { update(TableSelection.clear()) }
    Box(
        modifier
            .fillMaxSize()
            // before the frame: a press on the scrollbar is the scrollbar's
            .verticalScrollbar(state)
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
                    // the selection frame (DeskMail's request 22): a mouse dragged from a row that is not selected, or
                    // from below the rows, draws a frame and the rows it touches are selected
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            if (down.type != PointerType.Mouse || !currentEvent.buttons.isPrimaryPressed || down.isConsumed) return@awaitEachGesture
                            if (!latestMulti || down.position.y < headerPx.intValue) return@awaitEachGesture
                            val startIndex = indexAt(down.position.y)
                            val startKey = keysRef.value.getOrNull(startIndex)
                            // a selected row drags the selection instead (rowDrag)
                            if (latestDrag && startKey != null && startKey in latestSelection) return@awaitEachGesture
                            val mods = currentEvent.keyboardModifiers
                            val toggle = mods.pointerCtrl || mods.pointerMeta
                            val add = mods.pointerShift
                            val slop = viewConfiguration.touchSlop
                            // a click stays a click; the frame starts once the mouse has moved
                            while (true) {
                                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                                if (!change.pressed) return@awaitEachGesture
                                if ((change.position - down.position).getDistance() > slop) {
                                    change.consume()
                                    break
                                }
                            }
                            val before = latestSelection
                            val rowInfo = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == startKey }
                            marquee.start(startIndex, down.position, (down.position.y - state.layoutInfo.beforeContentPadding) - (rowInfo?.offset ?: 0))
                            // Esc reaches the table: the focus goes to the row the frame started on
                            (startKey ?: rovingKey())?.let { first ->
                                tracker.focused = first
                                runCatching { rowFocus[first]?.requestFocus() }
                            }
                            val edge = 32.dp.toPx()
                            val scrolling = scope.launch {
                                // near the rows' top or bottom edge the list scrolls along, and the frame with it
                                while (true) {
                                    withFrameNanos { }
                                    val y = marquee.pointer.y
                                    val top = headerPx.intValue.toFloat()
                                    val bottom = size.height.toFloat()
                                    val speed = when {
                                        y < top + edge -> -(top + edge - y).coerceAtMost(edge) / 2f
                                        y > bottom - edge -> (y - (bottom - edge)).coerceAtMost(edge) / 2f
                                        else -> 0f
                                    }
                                    if (speed != 0f && !marquee.cancelled) {
                                        state.dispatchRawDelta(speed)
                                        marquee.select(indexAt(y), keysRef.value, before, toggle, add, ::update)
                                    }
                                }
                            }
                            try {
                                while (true) {
                                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                                    change.consume()
                                    if (!change.pressed) break
                                    marquee.pointer = change.position
                                    if (!marquee.cancelled) marquee.select(indexAt(change.position.y), keysRef.value, before, toggle, add, ::update)
                                }
                            } finally {
                                scrolling.cancel()
                                marquee.end()
                            }
                        }
                    }
                    .drawWithContent {
                        drawContent()
                        if (marquee.active && !marquee.cancelled) {
                            val rect = marquee.rect(state.layoutInfo, keysRef.value, headerPx.intValue.toFloat(), size.height)
                            drawRect(marqueeColor.copy(alpha = 0.12f), rect.topLeft, rect.size)
                            drawRect(marqueeColor, rect.topLeft, rect.size, style = Stroke(1.dp.toPx()))
                        }
                    },
            )
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
                val focusedIndex = tracker.focused?.let { keysRef.value.indexOf(it) } ?: -1
                when {
                    multi && event.key == Key.A && command && !event.isShiftPressed && !event.isAltPressed -> {
                        update(TableSelection.all(keysInOrder(), tracker.anchor))
                        true
                    }
                    event.key == Key.Escape && marquee.active -> {
                        // the frame is dropped and the selection is as it was before it
                        marquee.cancel()?.let { update(TableSelection.Result(it, tracker.anchor)) }
                        true
                    }
                    multi && event.key == Key.Escape && !command && selection.isNotEmpty() -> {
                        update(TableSelection.clear())
                        true
                    }
                    multi && (event.key == Key.DirectionUp || event.key == Key.DirectionDown) && event.isShiftPressed && !command -> {
                        val focused = tracker.focused ?: return@onKeyEvent false
                        val down = event.key == Key.DirectionDown
                        val result = TableSelection.extend(keysInOrder(), tracker.anchor, focused, if (down) 1 else -1)
                            ?: return@onKeyEvent true
                        update(result)
                        focusRow(focusedIndex + if (down) 1 else -1)
                        true
                    }
                    // the rows are one Tab stop: the arrows, Home, End and the page keys move the focus among them
                    focusedIndex < 0 || event.isAltPressed || command -> false
                    event.key == Key.DirectionDown -> { focusRow(focusedIndex + 1); true }
                    event.key == Key.DirectionUp -> { focusRow(focusedIndex - 1); true }
                    event.key == Key.MoveHome -> { focusRow(0); true }
                    event.key == Key.MoveEnd -> { focusRow(keysRef.value.size - 1); true }
                    event.key == Key.PageDown -> { focusRow(focusedIndex + pageRows()); true }
                    event.key == Key.PageUp -> { focusRow(focusedIndex - pageRows()); true }
                    else -> false
                }
            },
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
            if (rows.isEmpty() && !skeleton && emptyContent != null) {
                item(key = "desktopkit.empty") {
                    Box(Modifier.fillParentMaxWidth().fillParentMaxHeight(0.8f), contentAlignment = Alignment.Center) { emptyContent() }
                }
            }
            itemsIndexed(rows, key = { _, item -> key(item) }) { index, item ->
                val itemKey = key(item)
                val requester = remember { FocusRequester() }
                DisposableEffect(itemKey, requester) {
                    rowFocus[itemKey] = requester
                    onDispose { if (rowFocus[itemKey] === requester) rowFocus.remove(itemKey) }
                }
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
                        emphasized = rowEmphasis?.invoke(item) == true,
                        onClick = onClick,
                        modifier = Modifier
                            .focusRequester(requester)
                            // one Tab stop: Tab reaches only the current row (the arrows reach every one)
                            .focusProperties { canFocus = !TabNavigation.active || itemKey == rovingKey() }
                            .onFocusChanged { focus ->
                                if (focus.isFocused) tracker.focused = itemKey else if (tracker.focused == itemKey) tracker.focused = null
                            }
                            // with a mouse a selected row drags at once (the rest a finger needs is not asked of it); a
                            // press on a row that is not selected draws the selection frame instead
                            .dragSource(
                                enabled = rowDrag != null,
                                acrossWindows = true,
                                touch = false,
                                pointerHold = false,
                                canStart = { !multi || itemKey in latestSelection },
                            ) { rowDrag?.invoke(item, index) },
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

/**
 * A selection frame being drawn with the mouse: where it started (the row and the point within it, so
 * the frame stays with the rows as they scroll) and where the pointer is.
 */
private class Marquee {
    var active by mutableStateOf(false)
        private set
    var cancelled by mutableStateOf(false)
        private set
    var pointer by mutableStateOf(Offset.Zero)
    private var startIndex = 0
    private var startX = 0f
    private var startInRow = 0f
    private var before: Set<Any> = emptySet()

    fun start(index: Int, at: Offset, inRow: Float) {
        startIndex = index
        startX = at.x
        startInRow = inRow
        pointer = at
        cancelled = false
        active = true
    }

    /** Selects the rows between the start and [index]: alone, toggled into [base] ([toggle]) or added to it ([add]). */
    fun select(index: Int, keys: List<Any>, base: Set<Any>, toggle: Boolean, add: Boolean, update: (TableSelection.Result) -> Unit) {
        before = base
        val first = minOf(startIndex, index).coerceAtLeast(0)
        val last = maxOf(startIndex, index).coerceAtMost(keys.size - 1)
        val covered = if (first > last) emptySet() else keys.subList(first, last + 1).toCollection(LinkedHashSet())
        val result = when {
            toggle -> (base - covered) + (covered - base)
            add -> base + covered
            else -> covered
        }
        update(TableSelection.Result(result, keys.getOrNull(startIndex.coerceIn(0, (keys.size - 1).coerceAtLeast(0)))))
    }

    /** Esc: the frame is dropped; the selection it started from, to put back. */
    fun cancel(): Set<Any>? {
        if (!active || cancelled) return null
        cancelled = true
        return before
    }

    fun end() {
        active = false
        cancelled = false
    }

    /** The frame in the table's coordinates: its start follows its row, clamped to the rows' area. */
    fun rect(info: LazyListLayoutInfo, keys: List<Any>, top: Float, bottom: Float): Rect {
        val key = keys.getOrNull(startIndex)
        val row = info.visibleItemsInfo.firstOrNull { it.key == key }
        val keySet = keys.toHashSet()
        val rowsInView = info.visibleItemsInfo.filter { it.key in keySet }
        val startY = when {
            row != null -> info.beforeContentPadding + row.offset + startInRow
            rowsInView.isNotEmpty() && startIndex < keys.indexOf(rowsInView.first().key) -> top
            else -> bottom
        }.coerceIn(top, bottom)
        val y = pointer.y.coerceIn(top, bottom)
        return Rect(minOf(startX, pointer.x), minOf(startY, y), maxOf(startX, pointer.x), maxOf(startY, y))
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
/**
 * Back leaves a selection being made, as in Android's action mode: on a touch layout or in the select
 * mode any selection, with a mouse several (one row a mouse clicked is just selected). A clear the app
 * does not take (it keeps the same rows) hands Back on to it. Nothing where there is no back dispatcher
 * (a window of a service, a screenshot test).
 */
@Composable
internal fun ClearSelectionOnBack(selection: Set<Any>, touch: Boolean, toggleOnClick: Boolean, clear: () -> Unit) {
    if (LocalOnBackPressedDispatcherOwner.current == null) return
    var refused by remember { mutableStateOf<Set<Any>?>(null) }
    if (selection.isEmpty() && refused != null) SideEffect { refused = null }
    val selecting = selection.isNotEmpty() && (toggleOnClick || touch || selection.size > 1)
    BackHandler(enabled = selecting && selection != refused) {
        refused = selection
        clear()
    }
}

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
    // TalkBack hears which way the column sorts, and what a tap does
    val sortedState = if (active) stringResource(if (sort.ascending) R.string.dk_sorted_ascending else R.string.dk_sorted_descending) else null
    val sortLabel = stringResource(R.string.dk_sort_by, column.title)
    Row(
        modifier = modifier
            .heightIn(min = DeskTheme.dimens.minTarget)
            .focusRing(state, shape)
            .background(if (clickable) stateBackground(state) else Color.Transparent, shape)
            .semantics {
                heading()
                if (sortedState != null) stateDescription = sortedState
            }
            .then(
                if (clickable) Modifier.deskClickable(interaction, {
                    onSortChange(TableSort(index, if (active) !sort.ascending else true))
                }, role = Role.Button, onClickLabel = sortLabel) else Modifier,
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
private fun <T> TableRow(
    columns: List<IndexedValue<TableColumn<T>>>,
    item: T,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    emphasized: Boolean = false,
) {
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
            val cellClick = column.onCellClick
            Box(
                Modifier
                    .cell(this, column.width)
                    .then(if (cellClick == null) Modifier else Modifier.cellButton(column.cellClickLabel?.invoke(item)) { cellClick(item) })
                    .padding(horizontal = 8.dp),
                contentAlignment = if (column.alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                val custom = column.cell
                if (custom != null) {
                    custom(item)
                } else {
                    val base = column.textStyle ?: if (column.mono) DeskTheme.type.mono else if (index == 0) DeskTheme.type.bodyStrong else DeskTheme.type.body
                    Text(
                        text = column.value(item),
                        // an emphasized row (unread) is bold and in the text colour throughout
                        style = if (emphasized) base.copy(fontWeight = FontWeight.SemiBold) else base,
                        color = when {
                            selected -> colors.onSelection
                            index == 0 || emphasized -> colors.text
                            else -> colors.textSecondary
                        },
                        maxLines = 1,
                        overflow = column.ellipsis,
                        textAlign = if (column.alignEnd) TextAlign.End else TextAlign.Start,
                    )
                }
            }
        }
    }
}

/** A cell that is a button of its own (a star), with hover and press states and a hand pointer. */
@Composable
private fun Modifier.cellButton(label: String?, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(DeskTheme.dimens.radiusSmall)
    return this
        .focusRing(state, shape)
        .background(stateBackground(state), shape)
        .deskClickable(interaction, onClick, onClickLabel = label)
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
