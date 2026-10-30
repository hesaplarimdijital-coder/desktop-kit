package desktopkit.components

import desktopkit.theme.rememberDeskHaptics
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import desktopkit.R
import desktopkit.theme.DeskMotion
import desktopkit.theme.DeskTheme
import desktopkit.theme.InputMode
import kotlin.math.roundToInt

// ---------------------------------------------------------------- card and key–value row

/**
 * A card: the surface colour, a 1 dp border and the card radius, with an optional [title] in the
 * title style. With [onClick] the whole card is a button (hover, press and focus states).
 */
@Composable
fun DeskCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = DeskTheme.dimens.gapL + 4.dp, vertical = DeskTheme.dimens.gapS),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusCard)
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val background by animatedStateBackground(state, rest = colors.surface)
    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.pressScale(state.pressed, 0.98f).focusRing(state, shape) else Modifier)
            .background(if (onClick != null) background else colors.surface, shape)
            .border(1.dp, colors.border, shape)
            .then(if (onClick != null) Modifier.deskClickable(interaction, onClick) else Modifier)
            .padding(contentPadding),
    ) {
        if (title != null) {
            Text(title, style = DeskTheme.type.title, color = colors.text,
                modifier = Modifier.padding(top = dimens.gapS, bottom = dimens.gapXs).semantics { heading() })
        }
        content()
    }
}

/**
 * A label and its value side by side ("Format · FLAC 24/96"); [mono] sets the value in the
 * monospace face. [selectable] lets the value be selected and copied. On a phone the label column
 * is narrower.
 */
@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
    valueColor: Color = DeskTheme.colors.text,
    maxLines: Int = 3,
    labelWidth: Dp = if (DeskTheme.dimens.minTarget >= 48.dp) 112.dp else 128.dp,
    selectable: Boolean = false,
) {
    val colors = DeskTheme.colors
    Row(modifier.fillMaxWidth().padding(vertical = DeskTheme.dimens.gapXs), verticalAlignment = Alignment.Top) {
        Text(label, style = DeskTheme.type.body, color = colors.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(labelWidth).padding(end = DeskTheme.dimens.gapS))
        val text: @Composable () -> Unit = {
            Text(value, style = if (mono) DeskTheme.type.body.copy(fontFamily = DeskTheme.type.mono.fontFamily) else DeskTheme.type.body,
                color = valueColor, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.weight(1f)) { if (selectable) SelectionContainer { text() } else text() }
    }
}

// ---------------------------------------------------------------- list row and selection bar

/**
 * A list row for phones (and any list that is not a table): a [title] with an optional [subtitle],
 * a [leading] icon or [leadingContent] (artwork), trailing text ([trailing], in the monospace face,
 * with [trailingSub] under it) or [trailingContent], and a thin [progress] bar under the subtitle.
 *
 * Tapping runs [onClick], or opens [menu] with [openMenuOnTap] (rows whose only use is their
 * actions; not in [selectionMode]). A long press runs [onLongClick] (usually: start selecting) or,
 * without it, opens [menu]; a right click and the Menu key open [menu] too. In [selectionMode] a
 * check box leads the row and shows [selected]. [drag] lets the row be dragged with a mouse to a
 * [dropZone].
 */
@Composable
fun DeskListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: Color = DeskTheme.colors.textTertiary,
    leading: ImageVector? = null,
    leadingTint: Color = DeskTheme.colors.textSecondary,
    leadingContent: (@Composable () -> Unit)? = null,
    trailing: String? = null,
    trailingColor: Color = DeskTheme.colors.textSecondary,
    trailingSub: String? = null,
    trailingSubColor: Color = DeskTheme.colors.textTertiary,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    strong: Boolean = false,
    indent: Dp = 0.dp,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    progress: Float? = null,
    progressColor: Color = DeskTheme.colors.accent,
    divider: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    menu: (() -> List<MenuEntry>)? = null,
    drag: (() -> DeskDragPayload?)? = null,
    openMenuOnTap: Boolean = false,
) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val touch = DeskTheme.inputMode == InputMode.Touch
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    var menuAt by remember { mutableStateOf<IntOffset?>(null) }
    var rowHeight by remember { mutableIntStateOf(0) }
    val latestMenu by rememberUpdatedState(menu)
    val openMenuAt: (IntOffset) -> Unit = remember { { at -> menuAt = at } }
    val background by animatedStateBackground(state, selected)
    val moreLabel = stringResource(R.string.dk_more_options)
    val minHeight = when {
        subtitle != null || progress != null -> if (touch) 56.dp else 44.dp
        else -> dimens.rowHeight.coerceAtLeast(dimens.minTarget)
    }
    Box(modifier.fillMaxWidth().onSizeChanged { rowHeight = it.height }.dragSource(enabled = drag != null) { drag?.invoke() }) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .focusRing(state, RoundedCornerShape(dimens.radiusSmall))
                .background(background)
                .pointerHoverIcon(PointerIcon.Hand)
                .hoverable(interaction, enabled)
                .secondaryClick(enabled && menu != null, openMenuAt)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onLongClick = when {
                        onLongClick != null -> onLongClick
                        menu != null -> { { menuAt = IntOffset(0, rowHeight) } }
                        else -> null
                    },
                    onClick = if (openMenuOnTap && !selectionMode && menu != null) { { menuAt = IntOffset(0, rowHeight) } } else onClick,
                )
                .onKeyEvent { event ->
                    val opens = event.type == KeyEventType.KeyDown && latestMenu != null &&
                        (event.key == Key.Menu || (event.key == Key.F10 && event.isShiftPressed))
                    if (opens) menuAt = IntOffset(0, rowHeight)
                    opens
                }
                .semantics {
                    this.selected = selected
                    if (menu != null) customActions = listOf(CustomAccessibilityAction(moreLabel) { menuAt = IntOffset(0, rowHeight); true })
                }
                .padding(start = dimens.pagePadding + indent, end = dimens.pagePadding, top = dimens.gapS, bottom = dimens.gapS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.gapM),
        ) {
            if (selectionMode) DeskCheckbox(selected, onCheckedChange = null)
            if (leadingContent != null) {
                leadingContent()
            } else if (leading != null) {
                Icon(leading, contentDescription = null, tint = leadingTint, modifier = Modifier.size(dimens.icon))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = if (strong) DeskTheme.type.bodyStrong else DeskTheme.type.body,
                    color = if (!enabled) colors.textTertiary else if (selected) colors.onSelection else colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrEmpty()) {
                    Text(subtitle, style = DeskTheme.type.caption, color = subtitleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (progress != null) {
                    Spacer(Modifier.height(2.dp))
                    DeskProgressBar(progress.coerceIn(0f, 1f), Modifier.fillMaxWidth(), color = progressColor, height = 3.dp)
                }
            }
            if (trailing != null || trailingSub != null) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.widthIn(max = 120.dp)) {
                    if (trailing != null) Text(trailing, style = DeskTheme.type.mono, color = trailingColor, maxLines = 1)
                    if (trailingSub != null) Text(trailingSub, style = DeskTheme.type.caption, color = trailingSubColor, maxLines = 1)
                }
            }
            trailingContent?.invoke(this)
        }
        if (divider) HorizontalRule(Modifier.align(Alignment.BottomStart).padding(start = dimens.pagePadding + indent))
        if (menu != null) {
            val at = menuAt
            MenuPopup(
                visible = at != null,
                onDismiss = { menuAt = null },
                entries = if (at != null) menu() else emptyList(),
                offset = at ?: IntOffset.Zero,
            )
        }
    }
}

/** Runs [action] on a mouse right click or a touchpad's two-finger tap, with the position in the element. */
internal fun Modifier.secondaryClick(enabled: Boolean, action: (IntOffset) -> Unit): Modifier =
    if (!enabled) this else pointerInput(action) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!currentEvent.buttons.isSecondaryPressed) return@awaitEachGesture
            down.consume()
            action(IntOffset(down.position.x.roundToInt(), down.position.y.roundToInt()))
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
                if (event.changes.none { it.pressed }) break
            }
        }
    }

/** Runs [action] on a middle (wheel) click; a tab closes on it. */
internal fun Modifier.middleClick(enabled: Boolean, action: () -> Unit): Modifier =
    if (!enabled) this else pointerInput(action) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!currentEvent.buttons.isTertiaryPressed) return@awaitEachGesture
            down.consume()
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
                if (event.changes.none { it.pressed }) break
            }
            action()
        }
    }

/**
 * The bar of a list in selection mode: a close button that ends the selection, the count
 * ("3 selected") and the [actions] as icon buttons. On a phone it takes the place of the bottom
 * bar or sits above it.
 */
@Composable
fun SelectionBar(
    text: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    closeLabel: String = stringResource(R.string.dk_selection_clear),
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    Column(modifier.fillMaxWidth().background(colors.surfaceRaised)) {
        HorizontalRule()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = dimens.gapS, vertical = dimens.gapXs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.gapXs),
        ) {
            DeskIconButton(DeskIcons.Close, closeLabel, onClose)
            Text(text, style = DeskTheme.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { paneTitle = text })
            actions()
        }
    }
}

// ---------------------------------------------------------------- menu buttons

/**
 * A button that opens a menu under it ("USB DAC ⌄"): [entries] is built each time it opens. The
 * menu takes the keyboard (arrows, Enter, Esc).
 */
@Composable
fun MenuButton(
    text: String,
    entries: () -> List<MenuEntry>,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    var height by remember { mutableIntStateOf(0) }
    Box(modifier.onSizeChanged { height = it.height }) {
        DeskButton(text = text, onClick = { open = true }, icon = icon, enabled = enabled, trailingIcon = DeskIcons.ChevronDown, role = Role.DropdownList)
        MenuPopup(visible = open, onDismiss = { open = false }, entries = if (open) entries() else emptyList(), offset = IntOffset(0, height + 2))
    }
}

/** An icon button that opens a menu under it; [label] is its accessibility label and tooltip. */
@Composable
fun IconMenuButton(
    icon: ImageVector,
    label: String,
    entries: () -> List<MenuEntry>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    var height by remember { mutableIntStateOf(0) }
    Box(modifier.onSizeChanged { height = it.height }) {
        DeskIconButton(icon = icon, contentDescription = label, onClick = { open = true }, enabled = enabled, selected = open)
        MenuPopup(visible = open, onDismiss = { open = false }, entries = if (open) entries() else emptyList(), offset = IntOffset(0, height + 2))
    }
}

// ---------------------------------------------------------------- tabs

/**
 * A row of [DeskTab]s over a rule, scrolling sideways when they do not fit (search results,
 * conversations, the sections of a screen).
 */
@Composable
fun TabStrip(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = DeskTheme.dimens.pagePadding - 4.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val rule = DeskTheme.colors.border
    val label = stringResource(R.string.dk_tabs)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind { drawRect(rule, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx())) }
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding)
            .selectableGroup()
            .semantics { paneTitle = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

/**
 * One tab: an optional [icon], the [label], a [badge] count and a [busy] dot; the selected tab gets
 * an accent line along its bottom edge that grows out from the middle. With [onClose] a close
 * button follows the label (on DeX only while the tab is selected or under the pointer) and a
 * middle click closes it too. With [drag] the tab can be dragged with a mouse to a [dropZone], for
 * instance onto another window of the app to move it there.
 */
@Composable
fun DeskTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    badge: Int = 0,
    busy: Boolean = false,
    closeLabel: String = stringResource(R.string.dk_tab_close),
    onClose: (() -> Unit)? = null,
    drag: (() -> DeskDragPayload?)? = null,
) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val touch = DeskTheme.inputMode == InputMode.Touch
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    val background by animatedStateBackground(state)
    val underline by animateFloatAsState(if (selected) 1f else 0f, DeskMotion.spatial(), label = "tabUnderline")
    val textColor by animateInteractionColor(if (selected) colors.text else colors.textSecondary, selected)
    val accent = colors.accent
    // the focus within the tab counts: Tab moves it on into the close button, which must stay
    var focusWithin by remember { mutableStateOf(false) }
    val showClose = onClose != null && (touch || selected || state.hovered || focusWithin)
    val haptics = rememberDeskHaptics()
    val latestClose by rememberUpdatedState(onClose)
    val closeByMiddleClick: () -> Unit = remember { { latestClose?.invoke() } }
    Row(
        modifier = modifier
            .heightIn(min = if (touch) dimens.minTarget else 30.dp)
            .focusRing(state, shape)
            .background(background, shape)
            .drawBehind {
                if (underline > 0.001f) {
                    val inset = 12.dp.toPx()
                    val full = (size.width - 2 * inset).coerceAtLeast(0f)
                    val width = full * underline
                    val height = 2.dp.toPx()
                    drawRoundRect(
                        accent,
                        topLeft = Offset((size.width - width) / 2f, size.height - height),
                        size = Size(width, height),
                        cornerRadius = CornerRadius(height / 2),
                    )
                }
            }
            .middleClick(onClose != null, closeByMiddleClick)
            .dragSource(enabled = drag != null) { drag?.invoke() }
            .onFocusChanged { focusWithin = it.hasFocus }
            .deskClickable(interaction, {
                if (!selected) haptics.tick()
                onClick()
            }, role = Role.Tab)
            .semantics {
                this.selected = selected
                // TalkBack can close a tab whose button is hidden (DeX, not selected)
                if (onClose != null) customActions = listOf(CustomAccessibilityAction(closeLabel) { onClose(); true })
            }
            .padding(start = 12.dp, end = if (onClose != null) 4.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.gapS - 2.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(dimens.icon))
        Text(label, style = DeskTheme.type.label, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp))
        if (busy) Box(Modifier.size(6.dp).background(colors.accentText, CircleShape))
        CountBadge(badge)
        if (onClose != null) {
            Box(Modifier.size(if (touch) 32.dp else 24.dp), contentAlignment = Alignment.Center) {
                if (showClose) {
                    DeskIconButton(DeskIcons.Close, closeLabel, onClose, size = if (touch) 32.dp else 24.dp, iconSize = if (touch) 16.dp else 12.dp)
                }
            }
        }
    }
}
