package desktopkit.components

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import desktopkit.input.showShortcutHints
import desktopkit.theme.DeskGlass
import desktopkit.theme.DeskMotion
import desktopkit.theme.DeskTheme
import desktopkit.theme.GlassMode
import desktopkit.theme.GlassThickness
import desktopkit.theme.LocalMotionTier
import desktopkit.theme.MotionTier
import desktopkit.theme.glass

/** One line of a menu bar drop-down or a context menu. */
@Immutable
sealed interface MenuEntry {
    data class Item(
        val label: String,
        val onClick: () -> Unit,
        val shortcut: String? = null,
        val icon: ImageVector? = null,
        val enabled: Boolean = true,
        val checked: Boolean? = null,
        val destructive: Boolean = false,
    ) : MenuEntry

    data object Separator : MenuEntry

    /**
     * A line that opens another menu beside it ("Open Recent ▸"): a chevron at its end, [entries] in a
     * menu of their own. Submenus nest to any depth. One without entries is shown disabled, and a
     * disabled one does not open. In a "⋯" sheet on a phone ([MoreSheet]) the line opens a page of
     * the sheet instead.
     */
    data class Submenu(
        val label: String,
        val entries: List<MenuEntry>,
        val icon: ImageVector? = null,
        val enabled: Boolean = true,
    ) : MenuEntry {
        /** Whether there is something to open: enabled and not empty. */
        internal val opens: Boolean get() = enabled && entries.isNotEmpty()
    }
}

/** A titled drop-down of the menu bar. Entries are built when the menu opens. */
class MenuSpec(val title: String, val entries: () -> List<MenuEntry>)

/**
 * Menu surface shared by the menu bar and context menus. It opens at [offset] from the
 * anchor's top-left corner and moves inside the window when it would overflow: near the bottom
 * edge a menu at a point flips above that point, like desktop menus, and a drop-down (offset at or
 * below the anchor's bottom) above the anchor.
 *
 * Like Android's own pop-up menus it grows in from its corner while fading in (150 ms) and fades
 * out where it is when closed (100 ms); with animations turned off in the system it simply appears.
 * On a phone it grows from the point it was opened at: from its bottom corner when it had to flip
 * above the finger, from under the finger when it was pushed in from the window's edge.
 * Inside [desktopkit.shell.DesktopShell] the menu is frosted glass over the screen
 * ([desktopkit.theme.DeskGlass]); elsewhere a solid raised surface with a shadow.
 *
 * It is a standard Compose [Popup] that takes focus and dismisses on an outside click, Esc or Back,
 * like Material's DropdownMenu and Android's PopupMenu: the click outside only closes the menu and
 * does not reach what is under it. A right click outside closes it and is handed to the window
 * below, so a context menu opens there in the same click (as on desktops).
 *
 * A [MenuEntry.Submenu] opens its menu beside its line, in a pop-up of its own that looks and moves
 * as this one: after the pointer has rested on the line ([SUBMENU_HOVER_MS]) or at once on a click,
 * a tap, Enter or the arrow key pointing at it. The arrow pointing back, Esc and Back close one
 * level; choosing an item or pressing outside closes them all. See Submenus.kt.
 */
@Composable
fun MenuPopup(
    visible: Boolean,
    onDismiss: () -> Unit,
    entries: List<MenuEntry>,
    offset: IntOffset = IntOffset.Zero,
    minWidth: Dp = 220.dp,
) {
    val target = visible && entries.isNotEmpty()
    // the last entries and position stay while the menu fades out; callers pass an empty list (and
    // often no offset) when closed, and the fading menu must not move there
    val shown = remember { EntriesHolder(entries, offset) }
    if (target) {
        shown.value = entries
        shown.offset = offset
    }
    val transition = remember { MutableTransitionState(false) }
    SideEffect { transition.targetState = target }
    val open by rememberUpdatedState(target)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    // a menu that is already closing ignores further dismiss requests and clicks
    val dismiss = remember { { if (open) currentOnDismiss() } }
    val anchorView = LocalView.current
    if (!target && !transition.currentState && !transition.targetState) return
    val direction = LocalLayoutDirection.current
    val hasSubmenus = shown.value.any { it is MenuEntry.Submenu }
    val provider = remember(shown.offset) { ClampedOffsetPositionProvider(shown.offset) }
    // which of this menu's submenus is open; a menu without submenus never opens one
    val level = remember { MenuLevel() }
    level.follow(shown.value)
    // closing takes the open submenu along: opened again while it still fades out, the menu starts without one
    if (!target) SideEffect { level.close() }
    val choose = remember {
        { item: MenuEntry.Item ->
            if (open) {
                currentOnDismiss()
                item.onClick()
            }
        }
    }
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = dismiss,
        // while it fades out it no longer holds the focus or catches clicks meant for the window below
        properties = PopupProperties(focusable = target),
    ) {
        val popupView = LocalView.current
        DisposableEffect(popupView, anchorView) {
            val root = popupView.rootView
            root.setOnTouchListener(OutsidePressHandler(anchorView, dismiss))
            val escape = EscapeListener(dismiss).also(root::addOnUnhandledKeyEventListener)
            onDispose {
                root.setOnTouchListener(null)
                root.removeOnUnhandledKeyEventListener(escape)
            }
        }
        MenuSurface(
            transition = transition,
            entries = shown.value,
            minWidth = minWidth,
            placed = provider,
            // wider windows keep the top-left corner, as before
            growsFromOrigin = LocalMotionTier.current == MotionTier.Gesture,
            level = level,
            choose = choose,
            onKey = { event ->
                val escape = event.type == KeyEventType.KeyDown && event.key == Key.Escape
                if (escape) dismiss()
                escape
            },
            // a menu with submenus reads the way they open: the direction of the place it was opened in
            direction = if (hasSubmenus) direction else null,
        )
    }
    if (hasSubmenus) {
        Submenus(shown.value, level, parent = provider, active = target, choose = choose, minWidth = minWidth)
    }
}

/**
 * The menu itself, in its pop-up: the glass (or raised) panel with its lines, growing in and fading
 * out with [transition]. Shared by [MenuPopup] and the submenus, so a submenu is a menu in every
 * respect. [placed] says where the pop-up was put (the corner it grows from); [level] holds which of
 * its submenus is open; [choose] runs an item and closes the whole chain; [onKey] gets the keys no
 * line used. A submenu is announced by [paneTitle] and, opened with a key or a click, gives the
 * keyboard focus to its first line ([focusFirst]; again when [focusKey] changes).
 *
 * [direction] is the layout direction of the place the menu was opened in, which decides the side
 * submenus open on. A pop-up's content takes its direction from the pop-up's view, which is that of
 * the app's locale; where the two differ (an app that turns part of its screen the other way), a
 * menu with submenus follows [direction], so its chevrons, its arrow keys and its submenus agree.
 * Null leaves the content as the pop-up gives it.
 */
@Composable
internal fun MenuSurface(
    transition: MutableTransitionState<Boolean>,
    entries: List<MenuEntry>,
    minWidth: Dp,
    placed: PlacedMenu,
    growsFromOrigin: Boolean,
    level: MenuLevel,
    choose: (MenuEntry.Item) -> Unit,
    onKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean,
    paneTitle: String? = null,
    focusFirst: Boolean = false,
    focusKey: Int = 0,
    direction: LayoutDirection? = null,
) {
    if (direction != null && direction != LocalLayoutDirection.current) {
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            MenuSurface(transition, entries, minWidth, placed, growsFromOrigin, level, choose, onKey, paneTitle, focusFirst, focusKey)
        }
        return
    }
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusCard.coerceAtMost(10.dp))
    val glassy = DeskGlass.source != null && DeskGlass.mode != GlassMode.Solid
    val tier = LocalMotionTier.current
    val reduced = tier == MotionTier.Reduced
    AnimatedVisibility(
        visibleState = transition,
        enter = if (reduced) EnterTransition.None else fadeIn(tween(DeskMotion.SHORT_MS + 50, easing = DeskMotion.StandardDecelerate)),
        exit = if (reduced) ExitTransition.None else fadeOut(tween(DeskMotion.SHORT_MS, easing = DeskMotion.StandardAccelerate)),
        label = "menuPopup",
    ) {
        // the growth is drawn here rather than with scaleIn: its origin is only known once the pop-up is
        // placed (flipped above, pushed in from an edge), and a layer reads it at draw time
        val scale by this.transition.animateFloat(
            transitionSpec = {
                when {
                    reduced -> snap()
                    targetState == EnterExitState.Visible -> tween(DeskMotion.SHORT_MS + 50, easing = DeskMotion.EmphasizedDecelerate)
                    else -> tween(DeskMotion.SHORT_MS, easing = DeskMotion.EmphasizedAccelerate)
                }
            },
            label = "menuScale",
        ) { state ->
            when {
                reduced -> 1f
                state == EnterExitState.PreEnter -> 0.92f
                state == EnterExitState.PostExit -> 0.96f
                else -> 1f
            }
        }
        val menuScroll = rememberScrollState()
        val hasSubmenus = entries.any { it is MenuEntry.Submenu }
        val firstLine = remember { FocusRequester() }
        val firstIndex = if (focusFirst) firstFocusableEntry(entries) else -1
        if (hasSubmenus) SubmenuHoverWait(level, entries)
        if (firstIndex >= 0) LaunchedEffect(focusKey) { runCatching { firstLine.requestFocus() } }
        Column(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = if (growsFromOrigin) placed.origin else TransformOrigin(0f, 0f)
                }
                .widthIn(min = minWidth, max = 420.dp)
                .width(IntrinsicSize.Max)
                // a shadow would show through glass; solid menus keep it
                .then(if (glassy) Modifier else Modifier.shadow(12.dp, shape))
                .glass(DeskGlass.raised(colors, GlassThickness.Regular), shape)
                .border(1.dp, colors.borderStrong, shape)
                .then(if (paneTitle == null) Modifier else Modifier.semantics { this.paneTitle = paneTitle })
                .verticalScrollbar(menuScroll)
                .verticalScroll(menuScroll)
                .onKeyEvent(onKey)
                .padding(4.dp),
        ) {
            entries.forEachIndexed { index, entry ->
                // only a menu with submenus follows the pointer over its lines and knows where they are
                val line = (if (hasSubmenus) Modifier.submenuLine(level, index, placed) else Modifier)
                    .then(if (index == firstIndex) Modifier.focusRequester(firstLine) else Modifier)
                when (entry) {
                    is MenuEntry.Item -> MenuItemRow(entry, line) { choose(entry) }
                    is MenuEntry.Submenu -> SubmenuRow(entry, index, level, line)
                    MenuEntry.Separator -> HorizontalRule(Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

/** Plain holder for the entries and position shown while the menu animates out; not state, it never triggers a redraw. */
private class EntriesHolder(var value: List<MenuEntry>, var offset: IntOffset)

/**
 * Presses outside the menu. The pop-up window takes focus, so Android sends it every press in the
 * app window (as with any focused pop-up); the Compose content only sees the ones on the menu, and
 * this listener on the pop-up's root view gets the rest before the pop-up's own outside-click check.
 *
 * Any outside press closes the menu and the rest of that gesture is swallowed, so a left click (or
 * tap) that closes the menu does nothing else. A right click is also replayed into [anchor]'s
 * window at the same screen point, so the element under the pointer opens its own menu there.
 */
private class OutsidePressHandler(private val anchor: View, private val dismiss: () -> Unit) : View.OnTouchListener {
    private var swallowing = false

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            swallowing = event.x < 0 || event.y < 0 || event.x >= view.width || event.y >= view.height
            if (swallowing) {
                dismiss()
                val secondary = event.buttonState and (MotionEvent.BUTTON_SECONDARY or MotionEvent.BUTTON_STYLUS_SECONDARY) != 0
                if (secondary && event.pointerCount == 1) replaySecondaryClick(event)
            }
            return swallowing
        }
        if (!swallowing) return false
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) swallowing = false
        return true
    }

    private fun replaySecondaryClick(event: MotionEvent) {
        val target = anchor.rootView
        val origin = IntArray(2)
        target.getLocationOnScreen(origin)
        val x = event.rawX - origin[0]
        val y = event.rawY - origin[1]
        if (x < 0 || y < 0 || x >= target.width || y >= target.height) return
        val down = event.copyAt(MotionEvent.ACTION_DOWN, event.buttonState, x, y)
        val up = event.copyAt(MotionEvent.ACTION_UP, 0, x, y)
        target.dispatchTouchEvent(down)
        target.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    private fun MotionEvent.copyAt(action: Int, buttons: Int, x: Float, y: Float): MotionEvent {
        val properties = arrayOf(MotionEvent.PointerProperties().also { getPointerProperties(0, it) })
        val coords = arrayOf(MotionEvent.PointerCoords().also { getPointerCoords(0, it); it.x = x; it.y = y })
        return MotionEvent.obtain(downTime, eventTime, action, 1, properties, coords, metaState, buttons,
            xPrecision, yPrecision, deviceId, edgeFlags, source, flags)
    }
}

/**
 * Esc when no menu item has the focus (the menu was opened with the mouse): the pop-up window has
 * the focus but no view in it does, so the key reaches only unhandled-key listeners.
 */
private class EscapeListener(private val dismiss: () -> Unit) : View.OnUnhandledKeyEventListener {
    override fun onUnhandledKeyEvent(view: View, event: KeyEvent): Boolean {
        val escape = event.keyCode == KeyEvent.KEYCODE_ESCAPE
        if (escape && event.action == KeyEvent.ACTION_DOWN) dismiss()
        return escape
    }
}

@Composable
internal fun MenuItemRow(item: MenuEntry.Item, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = DeskTheme.colors
    val content = when {
        !item.enabled -> colors.textTertiary
        item.destructive -> colors.danger
        else -> colors.text
    }
    MenuRow(
        modifier = modifier,
        state = state,
        background = if (item.enabled) animatedStateBackground(state).value else androidx.compose.ui.graphics.Color.Transparent,
        behaviour = Modifier
            .deskClickable(interaction, onClick, enabled = item.enabled, role = if (item.checked != null) Role.Checkbox else Role.Button)
            // TalkBack says whether a check item is on, not only that it is one
            .then(if (item.checked == null) Modifier else Modifier.semantics { toggleableState = ToggleableState(item.checked) }),
        icon = if (item.checked == true) DeskIcons.Check else item.icon,
        iconTint = if (item.enabled) colors.textSecondary else content,
        label = item.label,
        labelColor = content,
    ) {
        if (item.shortcut != null && showShortcutHints()) {
            Spacer(Modifier.width(24.dp))
            Text(item.shortcut, style = DeskTheme.type.mono, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The layout every line of a menu shares: the icon column, the label and what stands at the end
 * ([trailing]: an item's shortcut, a submenu's chevron). [modifier] comes first (where the line is,
 * who asks it to take the focus), [behaviour] is its click and its semantics.
 */
@Composable
internal fun MenuRow(
    modifier: Modifier,
    state: InteractionState,
    background: androidx.compose.ui.graphics.Color,
    behaviour: Modifier,
    icon: ImageVector?,
    iconTint: androidx.compose.ui.graphics.Color,
    label: String,
    labelColor: androidx.compose.ui.graphics.Color,
    trailing: @Composable RowScope.() -> Unit,
) {
    val dimens = DeskTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimens.rowHeight.coerceAtLeast(dimens.minTarget))
            .focusRing(state, shape)
            .background(background, shape)
            .then(behaviour)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(dimens.icon), contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(dimens.icon))
        }
        Spacer(Modifier.width(10.dp))
        Text(label, style = DeskTheme.type.body, color = labelColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f))
        trailing()
    }
}

/** The drop-down menus of the menu bar (File, Edit, View, …). Hovering another title while a menu is open switches to it. */
@Composable
fun RowScope.MenuBarMenus(menus: List<MenuSpec>) {
    var open by remember { mutableIntStateOf(-1) }
    menus.forEachIndexed { index, menu ->
        var titleHeight by remember { mutableIntStateOf(0) }
        Box(Modifier.onSizeChanged { titleHeight = it.height }) {
            MenuTitle(
                text = menu.title,
                open = open == index,
                onClick = { open = if (open == index) -1 else index },
                onHover = { if (open >= 0 && open != index) open = index },
            )
            MenuPopup(
                visible = open == index,
                onDismiss = { if (open == index) open = -1 },
                entries = if (open == index) menu.entries() else emptyList(),
                offset = IntOffset(0, titleHeight + 2),
            )
        }
    }
}

internal class ClampedOffsetPositionProvider(private val offset: IntOffset) : PopupPositionProvider, PlacedMenu {
    /**
     * Where the point the menu was asked for lies in the placed menu, 0..1 on each axis: the corner
     * the menu grows from (the top-left while nothing moved it). Known once the pop-up is placed.
     */
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
        val askedX = anchorBounds.left + offset.x
        val askedY = anchorBounds.top + offset.y
        var x = askedX
        var y = askedY
        if (x + popupContentSize.width > windowSize.width) x = windowSize.width - popupContentSize.width
        if (y + popupContentSize.height > windowSize.height) {
            // a drop-down under its anchor (a menu button) goes above the anchor, not over it; a menu
            // at a point inside the anchor (a right click) above that point
            val dropDown = offset.y >= anchorBounds.height
            val above = (if (dropDown) anchorBounds.top else anchorBounds.top + offset.y) - popupContentSize.height
            y = if (above >= 0) above else windowSize.height - popupContentSize.height
        }
        x = x.coerceAtLeast(0)
        y = y.coerceAtLeast(0)
        origin = TransformOrigin(
            if (popupContentSize.width > 0) ((askedX - x).toFloat() / popupContentSize.width).coerceIn(0f, 1f) else 0f,
            if (popupContentSize.height > 0) ((askedY - y).toFloat() / popupContentSize.height).coerceIn(0f, 1f) else 0f,
        )
        bounds = IntRect(IntOffset(x, y), popupContentSize)
        return IntOffset(x, y)
    }
}

/** Shows a menu while [content] stays in place; use with [MenuEntry] lists built on demand. */
@Composable
fun rememberMenuState(): MenuState = remember { MenuState() }

class MenuState {
    var offset by androidx.compose.runtime.mutableStateOf<IntOffset?>(null)
        private set

    /**
     * Counts the openings. A menu reopened elsewhere (a right click outside it on the same element)
     * is a new pop-up, so the old one is never drawn at the new place.
     */
    var generation by mutableIntStateOf(0)
        private set

    val isOpen: Boolean get() = offset != null

    fun open(at: IntOffset) {
        offset = at
        generation++
    }

    fun close() {
        offset = null
    }
}

@Composable
internal fun LaunchHover(hovered: Boolean, onHover: () -> Unit) {
    LaunchedEffect(hovered) { if (hovered) onHover() }
}

/** Title of a menu bar menu. */
@Composable
fun MenuTitle(text: String, open: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, onHover: () -> Unit = {}) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    LaunchHover(state.hovered, onHover)
    Box(
        modifier = modifier
            .heightIn(min = dimens.controlHeight.coerceAtLeast(dimens.minTarget))
            .focusRing(state, shape)
            .background(if (open) colors.hover else stateBackground(state), shape)
            .deskClickable(interaction, onClick)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = DeskTheme.type.body, color = if (open) colors.text else colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
