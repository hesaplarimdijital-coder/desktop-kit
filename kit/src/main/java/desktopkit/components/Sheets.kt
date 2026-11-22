package desktopkit.components

import android.graphics.Rect
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import desktopkit.R
import desktopkit.input.showShortcutHints
import desktopkit.shell.LocalShellOverlayHost
import desktopkit.shell.ShellOverlayHost
import desktopkit.shell.WidthClass
import desktopkit.shell.bottomInsetPadding
import desktopkit.shell.currentWidthClass
import desktopkit.theme.DeskGlass
import desktopkit.theme.DeskMotion
import desktopkit.theme.DeskTheme
import desktopkit.theme.GlassThickness
import desktopkit.theme.LocalMotionTier
import desktopkit.theme.MotionTier
import desktopkit.theme.glass
import desktopkit.theme.rememberDeskHaptics
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A titled group of actions for [MoreSheet] and menus ("Play", "Edit", "Share"); a null [title] draws a plain gap.
 * The [actions] are items as a rule; a [MenuEntry.Submenu] among them opens a page of the sheet
 * (a submenu of the menu), a [MenuEntry.Separator] draws a rule.
 */
@Immutable
data class ActionGroup(val title: String?, val actions: List<MenuEntry>)

/** The groups as one menu, separated by rules; empty groups are left out. */
fun List<ActionGroup>.toMenuEntries(): List<MenuEntry> =
    filter { it.actions.isNotEmpty() }.flatMapIndexed { i, group -> (if (i > 0) listOf(MenuEntry.Separator) else emptyList()) + group.actions }

/**
 * The "⋯" actions of something (a track, an album, a screen): on a phone a glass bottom sheet
 * listing [groups] with their icons; on wider windows the same entries as a menu at [menuOffset]
 * from the caller's top-left corner. Every entry is reachable with the keyboard (Tab, arrows,
 * Enter); Esc, Back, a tap on the scrim or a swipe down close it.
 *
 * A [MenuEntry.Submenu] opens as a page of the same sheet on a phone, with the submenu's label for
 * a title and a back button; Back and Esc go up one page before they close the sheet. In the menu
 * it is a submenu beside its line.
 */
@Composable
fun MoreSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    groups: List<ActionGroup>,
    modifier: Modifier = Modifier,
    title: String? = null,
    menuOffset: IntOffset = IntOffset.Zero,
) {
    if (currentWidthClass() == WidthClass.Compact) {
        ActionSheet(visible = visible, onDismiss = onDismiss, groups = groups, modifier = modifier, title = title)
    } else {
        MenuPopup(visible = visible, onDismiss = onDismiss, entries = if (visible) groups.toMenuEntries() else emptyList(), offset = menuOffset)
    }
}

/** A "⋯" icon button that opens [MoreSheet] with [groups] (a menu under the button on wider windows). */
@Composable
fun MoreButton(
    groups: () -> List<ActionGroup>,
    modifier: Modifier = Modifier,
    title: String? = null,
    contentDescription: String = stringResource(R.string.dk_more_options),
) {
    var open by remember { mutableStateOf(false) }
    var height by remember { mutableIntStateOf(0) }
    Box(modifier.onSizeChanged { height = it.height }) {
        DeskIconButton(DeskIcons.MoreHorizontal, contentDescription, onClick = { open = true })
        MoreSheet(
            visible = open,
            onDismiss = { open = false },
            groups = if (open) groups() else emptyList(),
            title = title,
            menuOffset = IntOffset(0, height),
        )
    }
}

/**
 * A glass bottom sheet of [groups]: the "⋯" sheet of [MoreSheet] and of the shell. A sheet without
 * submenus is [GlassBottomSheet] with the list in it, as before. With submenus, the sheet turns
 * pages: a submenu's line opens its entries as a page of the same sheet (to any depth), the sheet
 * taking the page's height; the page's title is the submenu's label, with a back button before it.
 */
@Composable
internal fun ActionSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    groups: List<ActionGroup>,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    val pages = remember { SheetPages() }
    pages.follow(visible)
    val held = remember { GroupsHolder(groups) }
    if (visible) held.value = groups
    val paged = held.value.any { group -> group.actions.any { it is MenuEntry.Submenu } }
    // a sheet with pages goes on showing what it showed while it slides out: a caller that empties the groups as
    // it closes (MoreButton) would otherwise take the page away from under the finger
    val shown = if (paged) held.value else groups
    val trail = resolveSheetPath(shown, pages.path)
    val latestDismiss by rememberUpdatedState(onDismiss)
    SheetLayer(
        visible = visible,
        onDismiss = onDismiss,
        modifier = modifier,
        label = trail.lastOrNull()?.label ?: title,
        goBack = pages::back,
    ) { pull ->
        if (!paged) {
            SheetTitle(title)
            val scroll = rememberScrollState()
            Column(Modifier.nestedScroll(pull).verticalScrollbar(scroll).verticalScroll(scroll)) {
                ActionGroupList(shown, onDismiss)
            }
        } else {
            ActionSheetPages(shown, trail, pages, title, pull) { latestDismiss() }
        }
    }
}

/** Plain holder for the groups a paged sheet shows while it slides out; not state. */
private class GroupsHolder(var value: List<ActionGroup>)

/**
 * The pages of an action sheet: the way down through its submenus, as the place of each among the
 * entries of the page it is on (on the first page: among all the actions, group after group, as
 * [ActionGroupList] lists them). Places, not the submenus themselves: the app builds its entries
 * anew whenever something changes (a switch on a page flips), and the page follows.
 */
@Stable
internal class SheetPages {
    var path by mutableStateOf(emptyList<Int>())
        private set

    /** The line of the page gone back to (its place there) that opened the page just left: it gets the keyboard focus back. */
    var cameFrom: Int? = null
        private set

    private val scrolls = HashMap<Int, ScrollState>()

    val depth: Int get() = path.size

    /** Opens the submenu at [index] of the page that is up. */
    fun open(index: Int) {
        cameFrom = null
        path = path + index
    }

    /** One page up; false on the first page, where there is nowhere to go. */
    fun back(): Boolean {
        if (path.isEmpty()) return false
        // the page left starts at its top the next time; the one gone back to is where it was
        scrolls.remove(path.size)
        cameFrom = path.last()
        path = path.dropLast(1)
        return true
    }

    /** Whether the sheet was up when [follow] was last told; not state, nothing is drawn from it. */
    private var up = false

    /**
     * Follows whether the sheet is [visible]: a sheet that opens starts on its first page, at its top.
     * Told while the sheet is composed, so the first frame already shows that page; an effect would
     * come a frame late, and the page the sheet was left on would show and fade away as it rises.
     */
    fun follow(visible: Boolean) {
        if (visible && !up) reset()
        up = visible
    }

    fun reset() {
        cameFrom = null
        scrolls.clear()
        path = emptyList()
    }

    /** How far the page at [depth] is scrolled; kept while deeper pages are up. */
    fun scrollOf(depth: Int): ScrollState = scrolls.getOrPut(depth) { ScrollState(0) }
}

/** The actions of [groups] as the first page lists them: the empty groups left out, group after group. */
internal fun sheetEntries(groups: List<ActionGroup>): List<MenuEntry> = groups.filter { it.actions.isNotEmpty() }.flatMap { it.actions }

/**
 * The submenus [path] leads through, outermost first. It stops where the path no longer leads to a
 * submenu that opens (the app's entries changed under it): the sheet then shows the last page that
 * is still there.
 */
internal fun resolveSheetPath(groups: List<ActionGroup>, path: List<Int>): List<MenuEntry.Submenu> {
    var entries = sheetEntries(groups)
    val trail = ArrayList<MenuEntry.Submenu>(path.size)
    for (index in path) {
        val submenu = entries.getOrNull(index) as? MenuEntry.Submenu ?: break
        if (!submenu.opens) break
        trail += submenu
        entries = submenu.entries
    }
    return trail
}

/** The shell's fade-through, between the pages of a sheet: the page left is gone before the next one shows. */
private const val PAGE_OUT_MS = 90
private const val PAGE_IN_MS = 210

/**
 * The page of a paged action sheet that is up, changing with a fade-through along the reading
 * direction (a deeper page comes from the end side, going back from the start side) while the
 * sheet takes the new page's height. Every page has its own scroll position; the one of a page gone
 * back to is where it was left.
 */
@Composable
private fun ActionSheetPages(
    groups: List<ActionGroup>,
    trail: List<MenuEntry.Submenu>,
    pages: SheetPages,
    title: String?,
    pull: NestedScrollConnection,
    onDismiss: () -> Unit,
) {
    val tier = LocalMotionTier.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val shift = with(LocalDensity.current) { DeskTheme.dimens.gapXl.roundToPx() }
    // the page being left is no longer on the path while it fades out: what it showed is kept here
    val known = remember { HashMap<Int, MenuEntry.Submenu>() }
    trail.forEachIndexed { index, submenu -> known[index + 1] = submenu }
    AnimatedContent(
        targetState = trail.size,
        transitionSpec = {
            if (tier == MotionTier.Reduced) {
                (EnterTransition.None togetherWith ExitTransition.None).using(SizeTransform { _, _ -> snap() })
            } else {
                val way = if ((targetState > initialState) != rtl) 1 else -1
                val slide = DeskMotion.spatial(tier, tween<IntOffset>(PAGE_OUT_MS + PAGE_IN_MS, easing = DeskMotion.Emphasized), IntOffset.VisibilityThreshold)
                val enter = fadeIn(tween(PAGE_IN_MS, delayMillis = PAGE_OUT_MS, easing = DeskMotion.EmphasizedDecelerate)) +
                    slideInHorizontally(slide) { way * shift }
                val exit = fadeOut(tween(PAGE_OUT_MS, easing = DeskMotion.EmphasizedAccelerate)) + slideOutHorizontally(slide) { -way * shift }
                (enter togetherWith exit).using(
                    SizeTransform { _, _ -> DeskMotion.spatial(tier, tween(DeskMotion.LONG_MS, easing = DeskMotion.Emphasized), IntSize.VisibilityThreshold) },
                )
            }
        },
        label = "sheetPage",
    ) { depth ->
        val submenu = if (depth == 0) null else trail.getOrNull(depth - 1) ?: known[depth]
        val scroll = pages.scrollOf(depth)
        // only the page that is up takes part: the one fading out no longer opens or closes anything
        val current = depth == trail.size
        Column {
            if (submenu == null) {
                SheetTitle(title)
            } else {
                SheetPageHeader(submenu.label, takeFocus = current && pages.cameFrom == null, onBack = { if (current) pages.back() })
            }
            Column(Modifier.nestedScroll(pull).verticalScrollbar(scroll).verticalScroll(scroll)) {
                if (submenu == null) {
                    ActionGroupList(groups, onDismiss, pages.takeIf { current })
                } else {
                    SheetEntryList(submenu.entries, start = 0, pages = pages.takeIf { current }, onDismiss = onDismiss)
                }
            }
        }
    }
}

/** The title of a sheet, above its list. */
@Composable
private fun SheetTitle(title: String?) {
    if (title == null) return
    val dimens = DeskTheme.dimens
    Text(title, style = DeskTheme.type.title, color = DeskTheme.colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = dimens.gapL + 4.dp, vertical = dimens.gapS))
}

/**
 * The head of a submenu's page: a back button (pointing the other way in a right-to-left layout)
 * and the submenu's label. Opened going deeper, the button takes the keyboard focus ([takeFocus]).
 */
@Composable
private fun SheetPageHeader(label: String, takeFocus: Boolean, onBack: () -> Unit) {
    val dimens = DeskTheme.dimens
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // the line that opened the page went with the page before: with a keyboard the focus goes on from the back button
    val focus = remember { FocusRequester() }
    if (takeFocus) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(Modifier.fillMaxWidth().padding(start = dimens.gapS, end = dimens.gapL + 4.dp), verticalAlignment = Alignment.CenterVertically) {
        DeskIconButton(DeskIcons.ArrowLeft, stringResource(R.string.dk_back), onBack,
            modifier = Modifier.focusRequester(focus).then(if (rtl) Modifier.graphicsLayer { scaleX = -1f } else Modifier))
        // the label starts where the lines' labels do (past their icon column), in either density
        val button = dimens.minTarget.coerceAtLeast(dimens.controlHeight)
        Spacer(Modifier.width(dimens.gapM * 2 + dimens.iconLarge - button))
        Text(label, style = DeskTheme.type.title, color = DeskTheme.colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(vertical = dimens.gapS).semantics { heading() })
    }
}

/**
 * The rows of [MoreSheet]: group captions, then one row per action. With [pages] (in an action
 * sheet) a submenu's line opens its page; without them there is nowhere for it to open, and it is
 * shown disabled.
 */
@Composable
internal fun ActionGroupList(groups: List<ActionGroup>, onDismiss: () -> Unit, pages: SheetPages? = null) {
    val dimens = DeskTheme.dimens
    var start = 0
    groups.filter { it.actions.isNotEmpty() }.forEachIndexed { index, group ->
        if (group.title != null) {
            SectionCaption(group.title, Modifier.padding(start = dimens.gapL + 4.dp, top = if (index == 0) 0.dp else dimens.gapM, bottom = 4.dp))
        } else if (index > 0) {
            HorizontalRule(Modifier.padding(vertical = dimens.gapXs, horizontal = dimens.gapL))
        }
        SheetEntryList(group.actions, start, pages, onDismiss)
        start += group.actions.size
    }
}

/** The lines of one list of a sheet; [start] is the place of its first entry on the page (see [SheetPages]). */
@Composable
private fun SheetEntryList(entries: List<MenuEntry>, start: Int, pages: SheetPages?, onDismiss: () -> Unit) {
    val dimens = DeskTheme.dimens
    entries.forEachIndexed { offset, entry ->
        when (entry) {
            is MenuEntry.Item -> SheetActionRow(entry) {
                // a switch stays in view while it flips; any other action closes the sheet
                if (entry.checked == null) onDismiss()
                entry.onClick()
            }
            is MenuEntry.Submenu -> SheetSubmenuRow(entry, place = start + offset, pages = pages)
            MenuEntry.Separator -> HorizontalRule(Modifier.padding(vertical = dimens.gapXs, horizontal = dimens.gapL))
        }
    }
}

/**
 * A submenu in a sheet: a line like an action's with a chevron at its end, which opens the
 * submenu's page. Coming back from that page with the keyboard, the line has the focus again.
 */
@Composable
private fun SheetSubmenuRow(submenu: MenuEntry.Submenu, place: Int, pages: SheetPages?) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val enabled = submenu.opens && pages != null
    val content = if (enabled) colors.text else colors.textTertiary
    val said = stringResource(R.string.dk_submenu)
    val focus = remember { FocusRequester() }
    if (pages != null && pages.cameFrom == place) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.gapS)
            .heightIn(min = dimens.rowHeight.coerceAtLeast(dimens.minTarget))
            .focusRing(state, shape)
            .background(animatedStateBackground(state).value, shape)
            .focusRequester(focus)
            .deskClickable(interaction, { pages?.open(place) }, enabled = enabled, onClickLabel = stringResource(R.string.dk_submenu_open))
            .semantics { stateDescription = said }
            .padding(horizontal = dimens.gapM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // the icon column stays when a submenu has no icon, so the labels line up
        Box(Modifier.size(dimens.iconLarge), contentAlignment = Alignment.Center) {
            if (submenu.icon != null) Icon(submenu.icon, contentDescription = null, tint = if (enabled) colors.textSecondary else content,
                modifier = Modifier.size(dimens.icon))
        }
        Spacer(Modifier.width(dimens.gapM))
        Text(submenu.label, style = DeskTheme.type.body, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f))
        Spacer(Modifier.width(dimens.gapM))
        Icon(if (rtl) DeskIcons.ChevronLeft else DeskIcons.ChevronRight, contentDescription = null,
            tint = if (enabled) colors.textSecondary else content, modifier = Modifier.size(dimens.icon))
    }
}

/**
 * One action of [MoreSheet]: its icon, its label and (with a keyboard or a mouse) its shortcut. An
 * item with `checked` set is a switch at the end of the line, and the whole line flips it.
 */
@Composable
private fun SheetActionRow(item: MenuEntry.Item, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val haptics = rememberDeskHaptics()
    val shape = RoundedCornerShape(dimens.radiusControl)
    val content = when {
        !item.enabled -> colors.textTertiary
        item.destructive -> colors.danger
        else -> colors.text
    }
    val checked = item.checked
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.gapS)
            .heightIn(min = dimens.rowHeight.coerceAtLeast(dimens.minTarget))
            .focusRing(state, shape)
            .background(animatedStateBackground(state).value, shape)
            .then(
                if (checked == null) {
                    Modifier.deskClickable(interaction, onClick, enabled = item.enabled)
                } else {
                    Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .hoverable(interaction, item.enabled)
                        .focusMemory()
                        .toggleable(checked, interaction, indication = null, enabled = item.enabled, role = Role.Switch) { on ->
                            haptics.toggle(on)
                            onClick()
                        }
                },
            )
            .padding(horizontal = dimens.gapM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // the icon column stays when an item has no icon, so the labels line up
        Box(Modifier.size(dimens.iconLarge), contentAlignment = Alignment.Center) {
            if (item.icon != null) {
                Icon(item.icon, contentDescription = null, tint = if (item.destructive) colors.danger else colors.textSecondary,
                    modifier = Modifier.size(dimens.icon))
            }
        }
        Spacer(Modifier.width(dimens.gapM))
        Text(item.label, style = DeskTheme.type.body, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f))
        if (item.shortcut != null && showShortcutHints()) {
            Text(item.shortcut, style = DeskTheme.type.mono, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (checked != null) {
            Spacer(Modifier.width(dimens.gapM))
            DeskSwitch(checked, onCheckedChange = null, enabled = item.enabled, interactionSource = interaction)
        }
    }
}

/**
 * A modal bottom sheet of glass: slides up over a scrim, follows the finger when dragged down and
 * closes past a third of its height or on a downward fling (springs on a phone, nothing moves with
 * reduced motion). Esc, Back and a tap on the scrim call [onDismiss]. At most 640 dp wide, centred.
 *
 * Inside [desktopkit.shell.DesktopShell] it is drawn in the shell's window over everything (and
 * blurs the screen behind it); elsewhere in a pop-up window. There one sheet is open at a time: a
 * sheet opened while another is up closes that one ([onDismiss]), so sheets never pile up.
 *
 * The handle is also the sheet's close button for TalkBack, Switch Access and the keyboard ("Close
 * sheet"), and the scrim says what a tap on it does: the sheet can be closed without a gesture.
 */
@Composable
fun GlassBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SheetLayer(visible, onDismiss, modifier, label = title, goBack = { false }) { pull ->
        SheetTitle(title)
        val scroll = rememberScrollState()
        Column(Modifier.nestedScroll(pull).verticalScrollbar(scroll).verticalScroll(scroll), content = content)
    }
}

/**
 * The sheet behind [GlassBottomSheet] and [ActionSheet]: the scrim, the glass surface with its
 * handle, the way in and out, and [body] under the handle ([body] gets the connection that lets a
 * list at its top pull the sheet down). [label] names the sheet for TalkBack. Back and Esc first ask
 * [goBack] (a sheet with pages goes up one); when it has nowhere to go they close the sheet.
 */
@Composable
private fun SheetLayer(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier,
    label: String?,
    goBack: () -> Boolean,
    body: @Composable ColumnScope.(NestedScrollConnection) -> Unit,
) {
    val tier = LocalMotionTier.current
    val way = remember { SheetWay() }
    val progress = way.progress
    LaunchedEffect(visible) {
        if (visible) {
            way.drag.moveTo(0f)
            way.carriedOut = false
            progress.animateTo(1f, sheetWay(tier, tween(DeskMotion.LONG_MS, easing = DeskMotion.EmphasizedDecelerate)))
        } else if (way.carriedOut) {
            // pulled or flung out by the finger: it is already gone, nothing to animate (a sheet dismissed
            // part way down, from the scrim or with Back, still leaves from where it is)
            way.carriedOut = false
            progress.snapTo(0f)
        } else {
            progress.animateTo(0f, sheetWay(tier, tween(DeskMotion.MEDIUM_MS, easing = DeskMotion.EmphasizedAccelerate)))
        }
    }
    way.open = visible
    val showing = visible || progress.value > 0f || progress.isRunning
    if (!showing) return
    val latestDismiss by rememberUpdatedState(onDismiss)
    val latestGoBack by rememberUpdatedState(goBack)
    val back = remember { { if (!latestGoBack()) latestDismiss() } }
    val sheet: @Composable () -> Unit = {
        SheetSurface(way, tier, label, modifier, { latestDismiss() }, back, body)
    }
    val host = LocalShellOverlayHost.current
    if (host != null) {
        OverlayLayer(host) { SheetSurface(way, tier, label, modifier, { latestDismiss() }, back, body, host.systemBottomPx) }
        ReturnFocusOnClose(host, visible)
        BackHandler(enabled = visible) { back() }
        // one sheet at a time: opening this one closes the one that was up
        val token = remember { Any() }
        DisposableEffect(host, visible) {
            if (visible) host.sheetOpened(token) { latestDismiss() }
            onDispose { if (visible) host.sheetClosed(token) }
        }
    } else {
        val view = LocalView.current
        val frameTop = remember(view) {
            val frame = Rect().also { view.getWindowVisibleDisplayFrame(it) }
            val location = IntArray(2).also { view.rootView.getLocationOnScreen(it) }
            frame.top - location[1]
        }
        Popup(
            popupPositionProvider = remember(frameTop) { TopLeftPositionProvider(frameTop) },
            onDismissRequest = back,
            properties = PopupProperties(focusable = true),
        ) { sheet() }
    }
}

/**
 * The sheet's way in and out: on a phone one firm spring both ways (a sheet is a small thing; the
 * screen transitions' springs would be slow on it), the timed curve wider up, a jump with reduced motion.
 */
private fun sheetWay(tier: MotionTier, timed: FiniteAnimationSpec<Float>): FiniteAnimationSpec<Float> = when (tier) {
    MotionTier.Gesture -> spring(dampingRatio = 0.92f, stiffness = 800f, visibilityThreshold = 0.001f)
    MotionTier.Timed -> timed
    MotionTier.Reduced -> snap()
}

/** Where a sheet is: how far in ([progress], 0 to 1), how far the finger has pulled it down ([drag], px), and whether the finger took it out. */
private class SheetWay {
    val progress = Animatable(0f)
    val drag = FingerOffset()

    /** Set when a pull or fling carried the sheet out: the dismiss that follows has nothing left to animate. */
    var carriedOut = false

    /** Whether the sheet is meant to be up (not on its way out); read when the focus moves. */
    var open = false
}

/**
 * While a layer is [open], remembers the element that had the keyboard focus when it opened; when
 * the layer closes the focus goes back there, so Tab goes on from where it was (a layer in the
 * shell's window would otherwise leave the focus nowhere).
 */
@Composable
internal fun ReturnFocusOnClose(host: ShellOverlayHost, open: Boolean) {
    val scope = rememberCoroutineScope()
    DisposableEffect(host, open) {
        val previous = if (open) host.focusMemory.current() else null
        onDispose { if (previous != null) scope.launch { previous.restore() } }
    }
}

/** Draws [content] in the shell's overlay layer while this is in composition, with the caller's composition locals. */
@Composable
internal fun OverlayLayer(host: ShellOverlayHost, content: @Composable () -> Unit) {
    val locals by rememberUpdatedState(currentCompositionLocalContext)
    val latest by rememberUpdatedState(content)
    val layer = remember(host) {
        ShellOverlayHost.Layer { CompositionLocalProvider(locals) { latest() } }
    }
    DisposableEffect(host, layer) {
        host.layers.add(layer)
        onDispose { host.layers.remove(layer) }
    }
}

@Composable
private fun SheetSurface(
    way: SheetWay,
    tier: MotionTier,
    name: String?,
    modifier: Modifier,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    body: @Composable ColumnScope.(NestedScrollConnection) -> Unit,
    bottomInset: () -> Int = { 0 },
) {
    val progress = way.progress
    val drag = way.drag
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val density = LocalDensity.current
    var sheetHeight by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    val label = name ?: stringResource(R.string.dk_more_options)
    val closeLabel = stringResource(R.string.dk_close_sheet)
    val haptics = rememberDeskHaptics()
    val latestDismiss by rememberUpdatedState(onDismiss)
    // pulled down on the handle, or anywhere on the sheet while its list is at the top
    val pull = remember(way) { SheetPull(way) }
    val settleSpec = DeskMotion.spatial(tier, tween<Float>(DeskMotion.MEDIUM_MS, easing = DeskMotion.Emphasized), 0.5f)
    SideEffect {
        pull.flingPx = with(density) { 800.dp.toPx() }
        pull.settleSpec = settleSpec
        pull.carriesOut = tier == MotionTier.Gesture
        pull.onThreshold = { haptics.threshold() }
        pull.onDismiss = { latestDismiss() }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(Modifier.fillMaxSize()) {
        // the scrim darkens with the sheet and lightens while it is dragged down
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val dragged = if (sheetHeight > 0) (drag.value / sheetHeight).coerceIn(0f, 1f) else 0f
                    alpha = progress.value.coerceIn(0f, 1f) * (1f - dragged)
                }
                .background(colors.scrim)
                .pointerInputTap(onDismiss)
                // what a tap on the dimmed screen does, for TalkBack
                .semantics {
                    contentDescription = closeLabel
                    onClick(closeLabel) { latestDismiss(); true }
                },
        )
        Column(
            modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                // the pull reads the height here: nothing recomposes the surface when it is measured
                .onSizeChanged {
                    sheetHeight = it.height
                    pull.height = it.height
                }
                // placed, not translated in a layer: the glass blurs what is behind the sheet where it is now
                // (Haze reads positions at placement, a layer's translation it would not see)
                .offset {
                    val hidden = (1f - progress.value) * (if (sheetHeight > 0) sheetHeight.toFloat() else 2000f)
                    IntOffset(0, (hidden + drag.value).roundToInt())
                }
                .glass(DeskGlass.raised(colors, GlassThickness.Regular), shape)
                .semantics { paneTitle = label }
                .onPreviewKeyEvent { event ->
                    val esc = event.key == Key.Escape && event.type == KeyEventType.KeyDown
                    if (esc) onBack()
                    esc
                }
                // the keyboard's focus stays in the sheet while it is up: Tab and Shift+Tab do not reach the screen behind.
                // On a group around the sheet's own focus target, so a move from the sheet itself is held as well as one
                // from a control in it; not once it is closing, when the focus goes back where it was (ReturnFocusOnClose)
                .focusProperties { onExit = { if (way.open) cancelFocusChange() } }
                .focusGroup()
                .focusRequester(focus)
                .focusable()
                // a finger or a mouse button is down: only then may a scroll at the list's top pull
                // the sheet (a mouse wheel at the top leaves it where it is)
                .pointerInput(pull) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressed = event.changes.any { it.pressed }
                            if (pressed && !pull.pressed) pull.grab()
                            pull.pressed = pressed
                        }
                    }
                }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta -> pull.by(delta) },
                    onDragStopped = { velocity -> pull.release(velocity) },
                )
                .padding(bottom = dimens.gapM)
                // the glass reaches behind the system navigation bar, the last row stays above it
                .bottomInsetPadding(bottomInset),
        ) {
            // the handle says "drag me"; it is also the sheet's close button for TalkBack, Switch Access and the
            // keyboard (the bar looks and sits as it did)
            val handle = remember { MutableInteractionSource() }
            val handleState = handle.collectState()
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(width = 64.dp, height = 20.dp)
                        .focusRing(handleState, RoundedCornerShape(10.dp))
                        .deskClickable(handle, { latestDismiss() }, onClickLabel = closeLabel)
                        .semantics { contentDescription = closeLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.padding(top = 4.dp).size(width = 36.dp, height = 4.dp).background(colors.borderStrong, RoundedCornerShape(2.dp)))
                }
            }
            body(pull)
        }
    }
}

/**
 * The sheet pulled down by the finger. On the handle and the title the drag moves it directly; on
 * the list it comes as nested scrolling: what the list cannot scroll (it is at its top) pulls the
 * sheet down, and pushing up raises a pulled sheet before the list scrolls again. Letting go past a
 * third of the height or with a downward fling closes it: on a phone ([carriesOut]) the sheet carries
 * on down at the finger's speed and the dismiss comes the moment it is out; wider up the dismiss comes
 * at once and the sheet leaves from where it is, as in 2.2.0. Otherwise it springs back. On the way
 * past the closing point there is one haptic tick.
 */
private class SheetPull(private val way: SheetWay) : NestedScrollConnection {
    private val drag = way.drag
    var height = 0
    var flingPx = 0f
    var settleSpec: AnimationSpec<Float> = tween()
    var carriesOut = false
    var onThreshold: () -> Unit = {}
    var onDismiss: () -> Unit = {}
    var pressed = false

    /** Where the pull has the sheet: where it is drawn while the finger moves it, where it is going once let go. */
    private var offset = 0f
    private var armed = false

    /**
     * A finger or button went down: the pull starts from where the sheet is drawn (back at the top
     * after the sheet was reopened, or partway while it springs back).
     */
    fun grab() {
        offset = drag.value.coerceAtLeast(0f)
        armed = height > 0 && offset > height / 3f
    }

    /** Moves the sheet by [delta] px (down is positive), never above its place; returns what it used. */
    fun by(delta: Float): Float {
        val next = (offset + delta).coerceAtLeast(0f)
        val used = next - offset
        if (used == 0f) return 0f
        offset = next
        val past = height > 0 && next > height / 3f
        if (past != armed) {
            armed = past
            if (past) onThreshold()
        }
        // at once, not in a coroutine of its own: let go in motion, the last move would otherwise land
        // after the release and stop it (FingerOffset)
        drag.moveTo(next)
        return used
    }

    suspend fun release(velocity: Float) {
        armed = false
        val far = height > 0 && offset > height / 3f
        if (far || velocity > flingPx) {
            if (!carriesOut) {
                onDismiss()
                return
            }
            // out at the speed it was let go with and dismissed the moment it is out, whatever the spring
            // still does (a close that started from rest paused for a frame or two after a fling)
            val end = height.toFloat()
            offset = end
            var gone = false
            fun dismiss() {
                gone = true
                way.carriedOut = true
                onDismiss()
            }
            drag.settle(end, settleSpec, velocity.coerceAtLeast(0f)) { at ->
                if (!gone && at >= end - 0.5f) dismiss()
            }
            if (!gone) dismiss()
        } else {
            offset = 0f
            drag.settle(0f, settleSpec, velocity)
        }
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || !pressed || available.y >= 0f || offset <= 0f) return Offset.Zero
        return Offset(0f, by(available.y))
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || !pressed || available.y <= 0f) return Offset.Zero
        return Offset(0f, by(available.y))
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (offset <= 0f) return Velocity.Zero
        release(available.y)
        return available
    }
}

private fun Modifier.pointerInputTap(onTap: () -> Unit): Modifier = pointerInput(Unit) { detectTapGestures { onTap() } }

/** Puts a full-window pop-up at the top of the visible frame (below the status bar). */
private class TopLeftPositionProvider(private val top: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
        IntOffset(0, top.coerceAtLeast(0))
}
