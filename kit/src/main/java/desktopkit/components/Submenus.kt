package desktopkit.components

import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.toRect
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import desktopkit.R
import desktopkit.theme.DeskTheme
import kotlin.math.abs
import kotlinx.coroutines.delay

// Submenus of the kit's menus: a line of a menu that opens another menu beside it, to any depth.
//
// Every level is a pop-up window of its own, drawn by the same MenuSurface as the menu it belongs to.
// The windows are siblings (not one inside the other), so all of them are placed in the coordinates
// of the window the menu was opened in. A submenu's window takes the keyboard focus but is not
// touch-modal: its lines have the real focus (arrows, Enter, Tab work as in any menu), while a press
// or the pointer over its parent still reaches the parent, and a press outside every menu reaches
// the first one, which closes the chain as before.

/** How long the pointer rests on a line before the line takes over: its submenu opens, another's closes. */
internal const val SUBMENU_HOVER_MS = 200L

/** The gap between a menu and its submenu: two panels side by side, neither's glass over the other's. */
private val SUBMENU_GAP = 4.dp

/** A menu's own padding above its first line: a submenu is raised by it, so that line meets the parent's line. */
private val MENU_PADDING = 4.dp

/** How far above and below a submenu the pointer may aim and still be on its way there. */
private val AIM_SLACK = 16.dp

private const val NONE = -1

/** Where a menu's pop-up was put. */
@Stable
internal interface PlacedMenu {
    /** The menu in the window it was opened in (px); null until it is placed. */
    val bounds: IntRect?

    /** The point the menu grows from, 0..1 on each axis. */
    val origin: TransformOrigin
}

/** Where a submenu goes: [position] of its top-left corner, and whether that is on the end side of its parent. */
internal data class SubmenuPlacement(val position: IntOffset, val atEnd: Boolean)

/**
 * Places a submenu of [size] beside its line. [parent] is the menu the line is in and [lineTop] the
 * top of the line, in the same coordinates as [area], the part of the window menus may cover (inside
 * the system bars and the caption bar).
 *
 * It goes to the end side of the parent ([gap] away), on the start side when the end has no room
 * for it; when neither side has, on the side with more room and as far out as the window lets it,
 * over its parent (a phone). Its first line is level with the parent's line ([lift] is the menu's
 * padding above its first line); near the bottom it moves up to stay in [area], and never above its
 * top.
 */
internal fun placeSubmenu(
    parent: IntRect,
    lineTop: Int,
    size: IntSize,
    area: IntRect,
    rtl: Boolean,
    gap: Int,
    lift: Int,
): SubmenuPlacement {
    val right = parent.right + gap
    val left = parent.left - gap - size.width
    val fitsRight = right + size.width <= area.right
    val fitsLeft = left >= area.left
    val endIsRight = !rtl
    val toRight = when {
        endIsRight && fitsRight -> true
        !endIsRight && fitsLeft -> false
        fitsRight -> true
        fitsLeft -> false
        // neither: the side with more room, the end side when they are equal
        else -> {
            val roomRight = area.right - parent.right
            val roomLeft = parent.left - area.left
            if (roomRight == roomLeft) endIsRight else roomRight > roomLeft
        }
    }
    val widest = (area.right - size.width).coerceAtLeast(area.left)
    val x = (if (toRight) right else left).coerceIn(area.left, widest)
    val lowest = (area.bottom - size.height).coerceAtLeast(area.top)
    val y = (lineTop - lift).coerceIn(area.top, lowest)
    return SubmenuPlacement(IntOffset(x, y), atEnd = toRight == endIsRight)
}

/**
 * Whether a pointer that went from [from] to [to] is on its way to the submenu at [child]: the line
 * through the two points meets the submenu's near edge, give or take [slack] above and below it.
 * This is the "safe triangle" of desktop menus, with [from] as its tip.
 */
internal fun headsFor(from: Offset, to: Offset, child: Rect, slack: Float): Boolean {
    val edge = if (child.center.x >= from.x) child.left else child.right
    val way = edge - from.x
    val gone = to.x - from.x
    // no step toward the submenu: straight up or down, or away from it
    if (way == 0f || gone * way <= 0f) return false
    val meets = from.y + (to.y - from.y) * (way / gone)
    return meets >= child.top - slack && meets <= child.bottom + slack
}

/**
 * Whether a move from [from] to [to] brings the pointer nearer to the side of [child] that faces it:
 * toward the submenu, not away from it, nor only up or down.
 */
internal fun nearer(from: Offset, to: Offset, child: Rect): Boolean {
    val edge = if (child.center.x >= from.x) child.left else child.right
    return abs(edge - to.x) < abs(edge - from.x)
}

/** What a move of the pointer does to the wait before the line under it takes over ([SUBMENU_HOVER_MS]). */
internal enum class HoverWait { Keep, Restart, Cancel }

/**
 * Which line of a menu the mouse means, while one of the menu's submenus may be open.
 *
 * A line takes over (its submenu opens, the open one closes) once the pointer has rested on it for
 * [SUBMENU_HOVER_MS]: [moved] says when that wait starts again, [settle] which line it was for. So a
 * pointer crossing lines on its way down the menu opens nothing on the way, and one travelling
 * diagonally to the open submenu across its parent's other lines does not close it: each line it
 * crosses starts the wait anew, and so does every move that brings it nearer to the submenu ([nearer])
 * inside the safe triangle ([headsFor], from where it left the submenu's own line). A pointer that
 * goes back or trembles in place does not; once it rests on another line, or turns away and stays
 * there, that line takes over.
 */
internal class SubmenuHover {
    /** The line the pointer is on that has not taken over yet. */
    var pending = NONE
        private set

    /** Where the pointer last was on the open submenu's own line: the tip of the safe triangle. */
    private var tip: Offset? = null
    private var last: Offset? = null

    /**
     * The pointer is at [at] on line [line]. [open] is the line whose submenu is open ([NONE]: none)
     * and [child] where that submenu is, in the coordinates of [at]; [slack] as in [headsFor].
     */
    fun moved(line: Int, at: Offset, open: Int, child: Rect?, slack: Float): HoverWait {
        val previous = last
        last = at
        if (line == open) {
            tip = at
            val waited = pending != NONE
            pending = NONE
            return if (waited) HoverWait.Cancel else HoverWait.Keep
        }
        val from = tip
        val aiming = open != NONE && child != null && from != null && previous != null &&
            headsFor(from, at, child, slack) && nearer(previous, at, child)
        return when {
            pending != line -> {
                pending = line
                HoverWait.Restart
            }
            aiming -> HoverWait.Restart
            else -> HoverWait.Keep
        }
    }

    /** The pointer left the menu's lines (for the submenu, or for elsewhere): nothing takes over. */
    fun left(): HoverWait {
        val waited = pending != NONE
        pending = NONE
        return if (waited) HoverWait.Cancel else HoverWait.Keep
    }

    /** The wait is over: the line that takes over ([NONE] when the pointer has left since). */
    fun settle(): Int {
        val line = pending
        pending = NONE
        // the pointer is on that line now: a submenu it opens is aimed for from here
        if (line != NONE) tip = last
        return line
    }
}

/** The first line of [entries] that can take the keyboard focus: an enabled item or a submenu that opens; -1 when none can. */
internal fun firstFocusableEntry(entries: List<MenuEntry>): Int = entries.indexOfFirst { entry ->
    when (entry) {
        is MenuEntry.Item -> entry.enabled
        is MenuEntry.Submenu -> entry.opens
        MenuEntry.Separator -> false
    }
}

/**
 * Where the submenu last seen at [place] with [label] is in [entries] now (an app builds its entries
 * anew whenever something changes): by its label first, at its own place when it is still there,
 * else the first submenu with that label (something came in above it, or went); when no line has
 * that label any more, at its place (a submenu renamed where it was). -1 when neither is a submenu
 * that opens: it is gone.
 */
internal fun findSubmenu(entries: List<MenuEntry>, place: Int, label: String): Int {
    fun opens(index: Int) = (entries.getOrNull(index) as? MenuEntry.Submenu)?.opens == true
    fun labelled(index: Int) = (entries.getOrNull(index) as? MenuEntry.Submenu)?.label == label
    if (opens(place) && labelled(place)) return place
    return entries.indices.firstOrNull { opens(it) && labelled(it) } ?: if (opens(place)) place else NONE
}

/** What a key means to the submenus, where it was pressed. */
internal enum class SubmenuKey { Open, Close, None }

/**
 * The arrow pointing to the end side opens the submenu of the line it is pressed on ([onSubmenuLine]);
 * the arrow pointing back and Esc close the submenu they are pressed in ([inSubmenu]) and go back to
 * its line. In a right-to-left layout the arrows change places, as the menus do.
 */
internal fun submenuKey(key: Key, rtl: Boolean, onSubmenuLine: Boolean, inSubmenu: Boolean): SubmenuKey {
    val forward = if (rtl) Key.DirectionLeft else Key.DirectionRight
    val back = if (rtl) Key.DirectionRight else Key.DirectionLeft
    return when {
        onSubmenuLine && key == forward -> SubmenuKey.Open
        inSubmenu && (key == back || key == Key.Escape) -> SubmenuKey.Close
        else -> SubmenuKey.None
    }
}

/** One menu of a chain: which of its submenus is open, where its lines are, and which line the pointer means. */
@Stable
internal class MenuLevel {
    /** The entry whose submenu is open; -1 when none is. */
    var openIndex by mutableIntStateOf(NONE)
        private set

    /** Whether the submenu that is open takes the keyboard focus: a key, a click or a tap opened it, not a resting pointer. */
    var focusChild = false
        private set

    /** Where the open submenu was put, for the pointer on its way there. */
    var child: PlacedMenu? = null

    /** Counts the openings: a submenu opened again while it fades out takes the focus again. */
    var opened by mutableIntStateOf(0)
        private set

    /** Changes to start the wait of [SUBMENU_HOVER_MS] again; 0 while nothing waits. */
    var wait by mutableIntStateOf(0)
        private set

    /** The label of the open submenu, by which [follow] finds it again in entries built anew. */
    private var openLabel = ""

    private val hover = SubmenuHover()
    private var waits = 0
    private val lines = mutableStateMapOf<Int, IntRect>()
    private val corners = HashMap<Int, Offset>()
    private val focus = HashMap<Int, FocusRequester>()

    fun open(index: Int, label: String, focus: Boolean) {
        focusChild = focus
        openIndex = index
        openLabel = label
        opened++
        stopWaiting()
    }

    /**
     * The menu's [entries] as they are now, told while the menu is composed: the open submenu is
     * found again ([findSubmenu]: by its label, else at its place), so an entry that comes in above
     * it does not hand its place to another submenu, and it closes when it is gone.
     */
    fun follow(entries: List<MenuEntry>) {
        if (openIndex == NONE) return
        val found = findSubmenu(entries, openIndex, openLabel)
        if (found != openIndex) openIndex = found
        if (found != NONE) openLabel = (entries[found] as MenuEntry.Submenu).label
    }

    /** Closes the submenu of [index] (of whichever is open by default); another one that has opened since stays. */
    fun close(index: Int = openIndex) {
        if (index == openIndex) openIndex = NONE
    }

    /** Where line [index] is in its menu (px, as far as it is in view); null until it is placed. */
    fun line(index: Int): IntRect? = lines[index]

    /** The top-left corner of line [index] in its menu, in view or not: what the pointer's place on the line is counted from. */
    fun cornerOf(index: Int): Offset = corners[index] ?: Offset.Zero

    fun placed(index: Int, bounds: IntRect, corner: Offset) {
        if (lines[index] != bounds) lines[index] = bounds
        corners[index] = corner
    }

    /** The requester of line [index], which gets the focus back when its submenu closes with a key. */
    fun focusOf(index: Int): FocusRequester = focus.getOrPut(index) { FocusRequester() }

    fun returnFocus(index: Int) {
        runCatching { focus[index]?.requestFocus() }
    }

    fun pointerMoved(index: Int, at: Offset, slack: Float) {
        apply(hover.moved(index, at, openIndex, child?.bounds?.toRect(), slack))
    }

    fun pointerLeft() = apply(hover.left())

    /** The pointer has rested: the line under it takes over. */
    fun settle(entries: List<MenuEntry>) {
        wait = 0
        val index = hover.settle()
        if (index == NONE) return
        val entry = entries.getOrNull(index)
        if (entry is MenuEntry.Submenu && entry.opens) open(index, entry.label, focus = false) else close()
    }

    private fun apply(change: HoverWait) {
        when (change) {
            HoverWait.Restart -> wait = ++waits
            HoverWait.Cancel -> wait = 0
            HoverWait.Keep -> Unit
        }
    }

    private fun stopWaiting() {
        hover.left()
        wait = 0
    }
}

/** Runs the wait of [SUBMENU_HOVER_MS] of [level] in the menu's pop-up. */
@Composable
internal fun SubmenuHoverWait(level: MenuLevel, entries: List<MenuEntry>) {
    val latest by rememberUpdatedState(entries)
    val wait = level.wait
    LaunchedEffect(level, wait) {
        if (wait != 0) {
            delay(SUBMENU_HOVER_MS)
            level.settle(latest)
        }
    }
}

/**
 * On every line of a menu that has submenus: says where the line is (a submenu opens beside it)
 * and follows the mouse over it (the line it rests on takes over). Touch has no resting pointer; a
 * finger opens a submenu with a tap.
 */
internal fun Modifier.submenuLine(level: MenuLevel, index: Int, menu: PlacedMenu): Modifier = this
    .onGloballyPositioned { level.placed(index, it.boundsInRoot().roundToIntRect(), it.positionInRoot()) }
    .pointerInput(level, index, menu) {
        val slack = AIM_SLACK.toPx()
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: continue
                if (change.type != PointerType.Mouse) continue
                when (event.type) {
                    PointerEventType.Enter, PointerEventType.Move -> {
                        // in the window the menus are placed in, where the open submenu is known too
                        val menuAt = menu.bounds?.topLeft?.toOffset() ?: Offset.Zero
                        level.pointerMoved(index, menuAt + level.cornerOf(index) + change.position, slack)
                    }
                    PointerEventType.Exit -> level.pointerLeft()
                }
            }
        }
    }

/**
 * Whether the press behind a line's next click is a finger's or a pen's (true) or a mouse's (false).
 * Such a click toggles the submenu, as a finger has no other way to close it. A mouse's click, a
 * key's and TalkBack's always open it: a click never closes what the resting pointer has just
 * opened. What pressed decides, not the density, as in [DenseTable]'s selection.
 */
private class LinePress {
    var finger = false
}

/**
 * A line that opens a submenu: the label, the icon and a chevron at the end (pointing the other way
 * in a right-to-left layout). It stays lit while its submenu is open. TalkBack hears "submenu" and
 * whether it is expanded, and can expand and collapse it.
 */
@Composable
internal fun SubmenuRow(submenu: MenuEntry.Submenu, index: Int, level: MenuLevel, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val press = remember { LinePress() }
    val enabled = submenu.opens
    val open = level.openIndex == index
    val content = if (enabled) colors.text else colors.textTertiary
    val said = stringResource(R.string.dk_submenu)
    val openLabel = stringResource(R.string.dk_submenu_open)
    MenuRow(
        modifier = modifier.focusRequester(level.focusOf(index)),
        // lit like the line under the pointer for as long as its submenu is open
        state = state,
        background = if (enabled) animatedStateBackground(state.copy(hovered = state.hovered || open)).value else Color.Transparent,
        behaviour = Modifier
            // what pressed the line, seen before the click its release makes; forgotten once that click is done
            .pointerInput(press) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    press.finger = down.type != PointerType.Mouse
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                    } while (event.changes.any { it.pressed })
                    press.finger = false
                }
            }
            .onKeyEvent { event ->
                val opens = enabled && event.type == KeyEventType.KeyDown &&
                    submenuKey(event.key, rtl, onSubmenuLine = true, inSubmenu = false) == SubmenuKey.Open
                if (opens) level.open(index, submenu.label, focus = true)
                opens
            }
            .deskClickable(
                interaction,
                onClick = { if (open && press.finger) level.close(index) else level.open(index, submenu.label, focus = true) },
                enabled = enabled,
                onClickLabel = openLabel,
            )
            .semantics {
                stateDescription = said
                if (enabled) {
                    if (open) collapse { level.close(index); true } else expand { level.open(index, submenu.label, focus = true); true }
                }
            },
        icon = submenu.icon,
        iconTint = if (enabled) colors.textSecondary else content,
        label = submenu.label,
        labelColor = content,
    ) {
        Spacer(Modifier.width(dimens.gapS))
        Icon(if (rtl) DeskIcons.ChevronLeft else DeskIcons.ChevronRight, contentDescription = null,
            tint = if (enabled) colors.textSecondary else content, modifier = Modifier.size(dimens.icon))
    }
}

/** The pop-ups of the submenus of [entries], a menu placed at [parent]; [active] while that menu is open (not fading out). */
@Composable
internal fun Submenus(
    entries: List<MenuEntry>,
    level: MenuLevel,
    parent: PlacedMenu,
    active: Boolean,
    choose: (MenuEntry.Item) -> Unit,
    minWidth: Dp,
) {
    entries.forEachIndexed { index, entry ->
        if (entry is MenuEntry.Submenu) {
            key(index) { SubmenuPopup(entry, index, level, parent, visible = active && level.openIndex == index, choose, minWidth) }
        }
    }
}

/**
 * The menu of one submenu, beside its line: a pop-up like [MenuPopup]'s, growing from the corner at
 * its line. It closes when its line's menu says so ([visible]), fading out where it is while another
 * may already be opening; Esc, Back and the arrow pointing back close it and hand the focus back to
 * its line.
 */
@Composable
private fun SubmenuPopup(
    submenu: MenuEntry.Submenu,
    index: Int,
    parentLevel: MenuLevel,
    parent: PlacedMenu,
    visible: Boolean,
    choose: (MenuEntry.Item) -> Unit,
    minWidth: Dp,
) {
    val target = visible && submenu.opens
    // what it showed stays while it fades out
    val shown = remember { SubmenuHolder(submenu) }
    if (target) shown.value = submenu
    val transition = remember { MutableTransitionState(false) }
    SideEffect { transition.targetState = target }
    if (!target && !transition.currentState && !transition.targetState) return
    val level = remember { MenuLevel() }
    level.follow(shown.value.entries)
    // closing takes its own open submenu along, and it opens again without one
    if (!target) SideEffect { level.close() }
    // a submenu that is fading out no longer chooses anything (another line has taken over)
    val live by rememberUpdatedState(target)
    val latestChoose by rememberUpdatedState(choose)
    val chooseHere = remember { { item: MenuEntry.Item -> if (live) latestChoose(item) } }
    val density = LocalDensity.current
    val view = LocalView.current
    val insets = WindowInsets.safeDrawing
    val direction = LocalLayoutDirection.current
    val rtl = direction == LayoutDirection.Rtl
    val provider = remember(parentLevel, index, parent) { SubmenuPositionProvider(parent, parentLevel, index) }
    SideEffect {
        provider.gap = with(density) { SUBMENU_GAP.roundToPx() }
        provider.lift = with(density) { MENU_PADDING.roundToPx() }
        // menus stay out from under the status bar, the caption bar, the navigation bar and the keyboard
        provider.area = {
            val window = view.rootView
            if (window.width <= 0 || window.height <= 0) null else IntRect(
                insets.getLeft(density, direction),
                insets.getTop(density),
                window.width - insets.getRight(density, direction),
                window.height - insets.getBottom(density),
            )
        }
    }
    DisposableEffect(parentLevel, provider, target) {
        if (target) parentLevel.child = provider
        onDispose { if (parentLevel.child === provider) parentLevel.child = null }
    }
    val close = remember(parentLevel, index) {
        {
            parentLevel.close(index)
            parentLevel.returnFocus(index)
        }
    }
    Popup(
        popupPositionProvider = provider,
        // Back and Esc: one level
        onDismissRequest = close,
        properties = if (target) OpenSubmenu else ClosingSubmenu,
    ) {
        val popupView = LocalView.current
        DisposableEffect(popupView, rtl) {
            val root = popupView.rootView
            val back = SubmenuBackListener(rtl, close).also(root::addOnUnhandledKeyEventListener)
            onDispose { root.removeOnUnhandledKeyEventListener(back) }
        }
        MenuSurface(
            transition = transition,
            entries = shown.value.entries,
            minWidth = minWidth,
            placed = provider,
            growsFromOrigin = true,
            level = level,
            choose = chooseHere,
            onKey = { event ->
                val closes = event.type == KeyEventType.KeyDown &&
                    submenuKey(event.key, rtl, onSubmenuLine = false, inSubmenu = true) == SubmenuKey.Close
                if (closes) close()
                closes
            },
            paneTitle = shown.value.label,
            focusFirst = parentLevel.focusChild,
            focusKey = parentLevel.opened,
            direction = direction,
        )
    }
    if (shown.value.entries.any { it is MenuEntry.Submenu }) {
        Submenus(shown.value.entries, level, parent = provider, active = target, choose = chooseHere, minWidth = minWidth)
    }
}

/** Plain holder for the submenu shown while it fades out; not state. */
private class SubmenuHolder(var value: MenuEntry.Submenu)

/**
 * An open submenu's window: it takes the keyboard focus, but what happens outside it goes to the
 * windows below (its parent menu, and behind every menu the first one, which closes the chain).
 */
private val OpenSubmenu = PopupProperties(flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, dismissOnClickOutside = false)

/** While it fades out it holds neither the focus nor the presses. */
private val ClosingSubmenu = PopupProperties(flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, dismissOnClickOutside = false)

/**
 * The arrow pointing back when no line of the submenu has the focus (the mouse opened it): the key
 * reaches only unhandled-key listeners, as Esc does in [MenuPopup].
 */
private class SubmenuBackListener(private val rtl: Boolean, private val close: () -> Unit) : View.OnUnhandledKeyEventListener {
    override fun onUnhandledKeyEvent(view: View, event: KeyEvent): Boolean {
        val back = event.keyCode == if (rtl) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        if (back && event.action == KeyEvent.ACTION_DOWN) close()
        return back
    }
}

/** Puts a submenu beside line [index] of the menu at [parent] ([placeSubmenu]) and says where it put it. */
internal class SubmenuPositionProvider(
    private val parent: PlacedMenu,
    private val level: MenuLevel,
    private val index: Int,
) : PopupPositionProvider, PlacedMenu {
    var gap = 0
    var lift = 0

    /** The part of the window menus may cover; null (or unset): the size the pop-up is given. */
    var area: () -> IntRect? = { null }

    override var origin by mutableStateOf(TransformOrigin(0f, 0f))
        private set

    override var bounds by mutableStateOf<IntRect?>(null)
        private set

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        // until the parent is placed there is nowhere to go; the pop-up is asked again when it is
        val menu = parent.bounds ?: return IntOffset(anchorBounds.left, anchorBounds.top)
        val line = level.line(index)
        val lineTop = menu.top + (line?.top ?: 0)
        val room = area() ?: IntRect(0, 0, windowSize.width, windowSize.height)
        val placement = placeSubmenu(menu, lineTop, popupContentSize, room, layoutDirection == LayoutDirection.Rtl, gap, lift)
        val at = placement.position
        val toRight = placement.atEnd == (layoutDirection == LayoutDirection.Ltr)
        // it grows out of its line: from the corner next to the parent, at the line's height
        origin = TransformOrigin(
            if (toRight) 0f else 1f,
            if (popupContentSize.height > 0) ((lineTop - at.y).toFloat() / popupContentSize.height).coerceIn(0f, 1f) else 0f,
        )
        bounds = IntRect(at, popupContentSize)
        return at
    }
}
