package desktopkit.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.input.pointer.isCtrlPressed as pointerCtrl
import androidx.compose.ui.input.pointer.isMetaPressed as pointerMeta
import androidx.compose.ui.input.pointer.isShiftPressed as pointerShift
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import desktopkit.R
import desktopkit.input.contextMenuGesture
import desktopkit.theme.DeskMotion
import desktopkit.theme.DeskTheme
import desktopkit.theme.InputMode
import kotlin.math.roundToInt

/**
 * What a cell of a [DeskGrid] shows itself: the pointer over it, a press, the keyboard focus, the
 * selection and whether several items are being selected ([selecting]). [openMenu] opens the
 * item's menu, for a "⋯" button inside the cell.
 */
@Stable
interface GridCellScope {
    val hovered: Boolean
    val pressed: Boolean
    val focused: Boolean
    val selected: Boolean
    val selecting: Boolean
    fun openMenu()
}

private class CellScope(
    override val hovered: Boolean,
    override val pressed: Boolean,
    override val focused: Boolean,
    override val selected: Boolean,
    override val selecting: Boolean,
    private val onOpenMenu: () -> Unit,
) : GridCellScope {
    override fun openMenu() = onOpenMenu()
}

/**
 * A grid of covers (albums, films, photos) with the selection, menu and keyboard rules of
 * [DenseTable]: click selects, Ctrl+click adds or removes, Shift+click selects a range, Ctrl+A all,
 * Esc none, Shift with the arrow keys extends (Up and Down by a row); the arrow keys move between
 * cells; right click, long press or the Menu key open [itemMenu]. Single selection with
 * [selectedKey], multi-selection with [selectedKeys] and [onSelectionChange]; [toggleOnClick] for a
 * touch "select" mode. [loading] shows breathing placeholder covers while [items] is empty.
 *
 * Columns are as many as fit [minCellWidth]. Draw each cell with [cell], usually a [CoverCell].
 * [itemDrag] lets covers be dragged with a mouse to a [dropZone], as [DenseTable]'s rowDrag.
 */
@Composable
fun <T> DeskGrid(
    items: List<T>,
    key: (T) -> Any,
    onItemClick: (T, Int) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    minCellWidth: Dp = if (DeskTheme.inputMode == InputMode.Touch) 152.dp else 136.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = DeskTheme.dimens.pagePadding, vertical = DeskTheme.dimens.gapL),
    selectedKey: Any? = null,
    selectedKeys: Set<Any>? = null,
    onSelectionChange: ((Set<Any>) -> Unit)? = null,
    toggleOnClick: Boolean = false,
    itemMenu: ((T, Int) -> List<MenuEntry>)? = null,
    itemMenuLabel: String? = null,
    loading: Boolean = false,
    itemDrag: ((T, Int) -> DeskDragPayload?)? = null,
    cell: @Composable GridCellScope.(item: T) -> Unit,
) {
    val multi = onSelectionChange != null
    val selection = selectedKeys ?: emptySet()
    val tracker = remember { SelectionTracker() }
    val focusManager = LocalFocusManager.current
    val keysInOrder = { items.map(key) }
    fun update(result: TableSelection.Result) {
        tracker.anchor = result.anchor
        if (result.selection != selection) onSelectionChange?.invoke(result.selection)
    }
    val columns = {
        state.layoutInfo.visibleItemsInfo.maxOfOrNull { it.column }?.plus(1) ?: 1
    }
    val gridModifier = if (!multi) Modifier else Modifier
        .pointerInput(Unit) {
            // remembers the keys held at each press; the cell's click comes after it
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
            tracker.toggle = event.isCtrlPressed || event.isMetaPressed
            tracker.range = event.isShiftPressed
            false
        }
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            val command = event.isCtrlPressed || event.isMetaPressed
            val step = when (event.key) {
                Key.DirectionLeft -> -1
                Key.DirectionRight -> 1
                Key.DirectionUp -> -columns()
                Key.DirectionDown -> columns()
                else -> 0
            }
            when {
                event.key == Key.A && command && !event.isShiftPressed && !event.isAltPressed -> {
                    update(TableSelection.all(keysInOrder(), tracker.anchor))
                    true
                }
                event.key == Key.Escape && !command && selection.isNotEmpty() -> {
                    update(TableSelection.clear())
                    true
                }
                step != 0 && event.isShiftPressed && !command -> {
                    val focused = tracker.focused ?: return@onKeyEvent false
                    val result = TableSelection.extend(keysInOrder(), tracker.anchor, focused, step) ?: return@onKeyEvent true
                    update(result)
                    focusManager.moveFocus(
                        when (event.key) {
                            Key.DirectionLeft -> FocusDirection.Left
                            Key.DirectionRight -> FocusDirection.Right
                            Key.DirectionUp -> FocusDirection.Up
                            else -> FocusDirection.Down
                        },
                    )
                    true
                }
                else -> false
            }
        }
    val skeleton = loading && items.isEmpty()
    val pulse = if (skeleton) rememberSkeletonPulse() else null
    val loadingLabel = stringResource(R.string.dk_loading)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minCellWidth),
        modifier = modifier
            .fillMaxSize()
            .then(gridModifier)
            .then(if (!skeleton) Modifier else Modifier.semantics { contentDescription = loadingLabel }),
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        if (pulse != null) {
            items(12) { SkeletonCover(pulse) }
        }
        itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
            val itemKey = key(item)
            GridItem(
                modifier = Modifier.dragSource(enabled = itemDrag != null) { itemDrag?.invoke(item, index) },
                selected = itemKey in selection || (selectedKey != null && itemKey == selectedKey),
                selecting = multi && (toggleOnClick || selection.isNotEmpty()),
                menu = itemMenu?.let { menu -> { menu(item, index) } },
                menuLabel = itemMenuLabel,
                onMenuOpen = if (!multi) null else ({ update(TableSelection.menu(selection, tracker.anchor, itemKey)) }),
                onFocused = { focused -> if (focused) tracker.focused = itemKey else if (tracker.focused == itemKey) tracker.focused = null },
                onClick = {
                    if (!multi) {
                        onItemClick(item, index)
                    } else {
                        val toggle = toggleOnClick || tracker.toggle
                        val range = !toggleOnClick && tracker.range
                        update(TableSelection.click(keysInOrder(), selection, tracker.anchor, itemKey, toggle = toggle, range = range))
                        if (!toggle && !range) onItemClick(item, index)
                    }
                    tracker.toggle = false
                    tracker.range = false
                },
            ) { cell(item) }
        }
    }
}

@Composable
private fun GridItem(
    modifier: Modifier,
    selected: Boolean,
    selecting: Boolean,
    menu: (() -> List<MenuEntry>)?,
    menuLabel: String?,
    onMenuOpen: (() -> Unit)?,
    onFocused: (Boolean) -> Unit,
    onClick: () -> Unit,
    content: @Composable GridCellScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val menuState = rememberMenuState()
    var height by remember { mutableIntStateOf(0) }
    val latestMenu by rememberUpdatedState(menu)
    val latestOnMenuOpen by rememberUpdatedState(onMenuOpen)
    val open: (IntOffset) -> Unit = remember(menuState) {
        { at ->
            if (latestMenu != null) {
                latestOnMenuOpen?.invoke()
                menuState.open(at)
            }
        }
    }
    val moreLabel = menuLabel ?: stringResource(R.string.dk_more_options)
    Box(
        modifier
            .onSizeChanged { height = it.height }
            .onFocusChanged { onFocused(it.isFocused) }
            .contextMenuGesture(menu != null) { at -> open(IntOffset(at.x.roundToInt(), at.y.roundToInt())) }
            .onKeyEvent { event ->
                val opens = event.type == KeyEventType.KeyDown && menu != null &&
                    (event.key == Key.Menu || (event.key == Key.F10 && event.isShiftPressed))
                if (opens) open(IntOffset(0, height))
                opens
            }
            .deskClickable(interaction, onClick)
            .semantics {
                this.selected = selected
                if (menu != null) customActions = listOf(CustomAccessibilityAction(moreLabel) { open(IntOffset(0, height)); true })
            },
    ) {
        CellScope(state.hovered, state.pressed, state.focused, selected, selecting) { open(IntOffset(0, height)) }.content()
        if (menu != null) {
            key(menuState.generation) {
                MenuPopup(
                    visible = menuState.isOpen,
                    onDismiss = menuState::close,
                    entries = if (menuState.isOpen) menu() else emptyList(),
                    offset = menuState.offset ?: IntOffset.Zero,
                )
            }
        }
    }
}

/**
 * A cover with its title and a second line, as in the design study: the cover rounds its corners;
 * the pointer over it (or the keyboard focus, or the selection) rings it in the accent colour and,
 * with a mouse, darkens it and shows [onPlay] at the bottom and "⋯" at the top (the item's menu).
 * While items are being selected a check mark in the corner shows the choice.
 */
@Composable
fun GridCellScope.CoverCell(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onPlay: (() -> Unit)? = null,
    playLabel: String? = null,
    showMore: Boolean = true,
    cover: @Composable BoxScope.() -> Unit,
) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val pointer = DeskTheme.inputMode == InputMode.Pointer
    val radius = dimens.radiusCard
    val ring = selected || focused || (hovered && pointer)
    val ringAlpha by animateFloatAsState(if (ring) 1f else 0f, tween(DeskMotion.SHORT_MS), label = "coverRing")
    val overlay by animateFloatAsState(if (hovered && pointer) 1f else 0f, tween(DeskMotion.SHORT_MS), label = "coverOverlay")
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .border(2.dp, colors.accent.copy(alpha = colors.accent.alpha * ringAlpha), RoundedCornerShape(radius + 4.dp))
                .padding(4.dp)
                .pressScale(pressed, 0.97f),
        ) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(radius)).background(colors.surfaceRaised)) {
                cover()
                if (overlay > 0f) Box(Modifier.fillMaxSize().alpha(overlay).background(Color.Black.copy(alpha = 0.35f)))
            }
            if (overlay > 0f && onPlay != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .alpha(overlay)
                        .size(40.dp)
                        .background(colors.accent, CircleShape)
                        .deskClickable(remember { MutableInteractionSource() }, onPlay, onClickLabel = playLabel),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(DeskIcons.Play, contentDescription = playLabel, tint = colors.onAccent, modifier = Modifier.size(18.dp))
                }
            }
            if (overlay > 0f && showMore) {
                Box(Modifier.align(Alignment.TopEnd).padding(8.dp).alpha(overlay).background(colors.surfaceRaised.copy(alpha = 0.8f), RoundedCornerShape(dimens.radiusControl))) {
                    DeskIconButton(DeskIcons.MoreHorizontal, stringResource(R.string.dk_more_options), onClick = { openMenu() }, size = 28.dp)
                }
            }
            if (selecting) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(22.dp)
                        .background(if (selected) colors.accent else Color.Black.copy(alpha = 0.3f), CircleShape)
                        .border(1.5.dp, if (selected) colors.accent else Color.White.copy(alpha = 0.85f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(DeskIcons.Check, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.height(dimens.gapS - 4.dp))
        Text(title, style = DeskTheme.type.label.copy(fontWeight = FontWeight.Medium), color = if (selected) colors.accentText else colors.text,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
        if (subtitle != null) {
            Text(subtitle, style = DeskTheme.type.labelSmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

/** A cover while the grid loads. */
@Composable
private fun SkeletonCover(pulse: androidx.compose.runtime.State<Float>) {
    Column(Modifier.fillMaxWidth().padding(4.dp)) {
        SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(1f), pulse, RoundedCornerShape(DeskTheme.dimens.radiusCard))
        Spacer(Modifier.height(8.dp))
        SkeletonBlock(Modifier.fillMaxWidth(0.7f).height(10.dp), pulse)
        Spacer(Modifier.height(6.dp))
        SkeletonBlock(Modifier.fillMaxWidth(0.45f).height(8.dp), pulse)
    }
}
