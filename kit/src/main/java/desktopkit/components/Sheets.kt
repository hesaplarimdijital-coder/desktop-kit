package desktopkit.components

import android.graphics.Rect
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** A titled group of actions for [MoreSheet] and menus ("Play", "Edit", "Share"); a null [title] draws a plain gap. */
@Immutable
data class ActionGroup(val title: String?, val actions: List<MenuEntry.Item>)

/** The groups as one menu, separated by rules; empty groups are left out. */
fun List<ActionGroup>.toMenuEntries(): List<MenuEntry> =
    filter { it.actions.isNotEmpty() }.flatMapIndexed { i, group -> (if (i > 0) listOf(MenuEntry.Separator) else emptyList()) + group.actions }

/**
 * The "⋯" actions of something (a track, an album, a screen): on a phone a glass bottom sheet
 * listing [groups] with their icons; on wider windows the same entries as a menu at [menuOffset]
 * from the caller's top-left corner. Every entry is reachable with the keyboard (Tab, arrows,
 * Enter); Esc, Back, a tap on the scrim or a swipe down close it.
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
        GlassBottomSheet(visible = visible, onDismiss = onDismiss, modifier = modifier, title = title) {
            ActionGroupList(groups, onDismiss)
        }
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

/** The rows of [MoreSheet]: group captions, then one row per action. */
@Composable
internal fun ActionGroupList(groups: List<ActionGroup>, onDismiss: () -> Unit) {
    val dimens = DeskTheme.dimens
    groups.filter { it.actions.isNotEmpty() }.forEachIndexed { index, group ->
        if (group.title != null) {
            SectionCaption(group.title, Modifier.padding(start = dimens.gapL + 4.dp, top = if (index == 0) 0.dp else dimens.gapM, bottom = 4.dp))
        } else if (index > 0) {
            HorizontalRule(Modifier.padding(vertical = dimens.gapXs, horizontal = dimens.gapL))
        }
        group.actions.forEach { action ->
            SheetActionRow(action) {
                // a switch stays in view while it flips; any other action closes the sheet
                if (action.checked == null) onDismiss()
                action.onClick()
            }
        }
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
 * blurs the screen behind it); elsewhere in a pop-up window.
 */
@Composable
fun GlassBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tier = LocalMotionTier.current
    val progress = remember { Animatable(0f) }
    val drag = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            drag.snapTo(0f)
            progress.animateTo(1f, sheetWay(tier, tween(DeskMotion.LONG_MS, easing = DeskMotion.EmphasizedDecelerate)))
        } else if (drag.value > 0f) {
            // pulled or flung out by the finger: it is already gone, nothing to animate
            progress.snapTo(0f)
        } else {
            progress.animateTo(0f, sheetWay(tier, tween(DeskMotion.MEDIUM_MS, easing = DeskMotion.EmphasizedAccelerate)))
        }
    }
    val showing = visible || progress.value > 0f || progress.isRunning
    if (!showing) return
    val latestDismiss by rememberUpdatedState(onDismiss)
    val sheet: @Composable () -> Unit = {
        SheetSurface(progress, drag, tier, title, modifier, { latestDismiss() }, content)
    }
    val host = LocalShellOverlayHost.current
    if (host != null) {
        OverlayLayer(host) { SheetSurface(progress, drag, tier, title, modifier, { latestDismiss() }, content, host.systemBottomPx) }
        ReturnFocusOnClose(host, visible)
        BackHandler(enabled = visible) { latestDismiss() }
    } else {
        val view = LocalView.current
        val frameTop = remember(view) {
            val frame = Rect().also { view.getWindowVisibleDisplayFrame(it) }
            val location = IntArray(2).also { view.rootView.getLocationOnScreen(it) }
            frame.top - location[1]
        }
        Popup(
            popupPositionProvider = remember(frameTop) { TopLeftPositionProvider(frameTop) },
            onDismissRequest = { latestDismiss() },
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
    progress: Animatable<Float, AnimationVector1D>,
    drag: Animatable<Float, AnimationVector1D>,
    tier: MotionTier,
    title: String?,
    modifier: Modifier,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    bottomInset: () -> Int = { 0 },
) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var sheetHeight by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    val label = title ?: stringResource(R.string.dk_more_options)
    val haptics = rememberDeskHaptics()
    val latestDismiss by rememberUpdatedState(onDismiss)
    // pulled down on the handle, or anywhere on the sheet while its list is at the top
    val pull = remember(drag) { SheetPull(drag) }
    val settleSpec = DeskMotion.spatial(tier, tween<Float>(DeskMotion.MEDIUM_MS, easing = DeskMotion.Emphasized), 0.5f)
    SideEffect {
        pull.scope = scope
        pull.flingPx = with(density) { 800.dp.toPx() }
        pull.settleSpec = settleSpec
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
                .pointerInputTap(onDismiss),
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
                    if (esc) onDismiss()
                    esc
                }
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
            // the handle says "drag me"
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).background(colors.borderStrong, RoundedCornerShape(2.dp)))
            }
            if (title != null) {
                Text(title, style = DeskTheme.type.title, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = dimens.gapL + 4.dp, vertical = dimens.gapS))
            }
            Column(Modifier.nestedScroll(pull).verticalScroll(rememberScrollState()), content = content)
        }
    }
}

/**
 * The sheet pulled down by the finger. On the handle and the title the drag moves it directly; on
 * the list it comes as nested scrolling: what the list cannot scroll (it is at its top) pulls the
 * sheet down, and pushing up raises a pulled sheet before the list scrolls again. Letting go past a
 * third of the height or with a downward fling closes it: the sheet carries on down at the finger's
 * speed and the dismiss comes once it is gone; otherwise it springs back. On the way past the
 * closing point there is one haptic tick.
 */
private class SheetPull(private val drag: Animatable<Float, AnimationVector1D>) : NestedScrollConnection {
    lateinit var scope: CoroutineScope
    var height = 0
    var flingPx = 0f
    var settleSpec: AnimationSpec<Float> = tween()
    var onThreshold: () -> Unit = {}
    var onDismiss: () -> Unit = {}
    var pressed = false

    /** Where the sheet is: set at once, while the animatable catches up on the next frame. */
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
        scope.launch { drag.snapTo(next) }
        return used
    }

    suspend fun release(velocity: Float) {
        armed = false
        val far = height > 0 && offset > height / 3f
        if (far || velocity > flingPx) {
            // out at the speed it was let go with, then dismissed (a close that started from rest
            // paused for a frame or two after a fling)
            val end = height.toFloat()
            offset = end
            drag.animateTo(end, settleSpec, initialVelocity = velocity.coerceAtLeast(0f))
            onDismiss()
        } else {
            offset = 0f
            drag.animateTo(0f, settleSpec, initialVelocity = velocity)
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
