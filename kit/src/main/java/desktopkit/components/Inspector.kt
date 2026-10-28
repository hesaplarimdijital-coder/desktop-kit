package desktopkit.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import desktopkit.R
import desktopkit.settings.DeskPreferences
import desktopkit.shell.WidthClass
import desktopkit.shell.currentWidthClass
import desktopkit.theme.DeskMotion
import desktopkit.theme.DeskTheme
import desktopkit.theme.LocalMotionTier
import desktopkit.theme.MotionTier

/**
 * Width and visibility of a workstation's inspector pane, and its selected tab. Width changes from
 * the divider are kept while dragging and stored when the drag ends ([onCommit]), e.g. in
 * [DeskPreferences] via [rememberInspectorState].
 */
@Stable
class InspectorState(
    initialWidthDp: Float,
    initialCollapsed: Boolean = false,
    initialTab: Int = 0,
    private val onCommit: (widthDp: Float, collapsed: Boolean) -> Unit = { _, _ -> },
) {
    var widthDp by mutableFloatStateOf(initialWidthDp)
        private set
    var collapsed by mutableStateOf(initialCollapsed)
        private set
    var selectedTab by mutableIntStateOf(initialTab)

    /** Moves the divider: the pane gets [deltaDp] wider (negative: narrower), within [min]…[max]. */
    fun resizeBy(deltaDp: Float, min: Dp, max: Dp) {
        widthDp = (widthDp + deltaDp).coerceIn(min.value, max.value)
    }

    /** Stores the current width and visibility. */
    fun commit() = onCommit(widthDp, collapsed)

    fun updateCollapsed(value: Boolean) {
        collapsed = value
        commit()
    }

    fun toggle() = updateCollapsed(!collapsed)
}

/**
 * An [InspectorState] whose width and visibility live in [prefs] (shared by every workstation of
 * the app), or only in the saved instance state when [prefs] is null.
 */
@Composable
fun rememberInspectorState(prefs: DeskPreferences? = null, defaultWidth: Dp = 320.dp): InspectorState {
    if (prefs != null) {
        return remember(prefs) {
            val stored = prefs.inspectorWidth
            InspectorState(if (stored.isNaN()) defaultWidth.value else stored, prefs.inspectorCollapsed) { width, collapsed ->
                prefs.updateInspectorWidth(width)
                prefs.updateInspectorCollapsed(collapsed)
            }
        }
    }
    return rememberSaveable(saver = InspectorSaver) { InspectorState(defaultWidth.value) }
}

private val InspectorSaver: Saver<InspectorState, List<Any>> = Saver(
    save = { listOf(it.widthDp, it.collapsed, it.selectedTab) },
    restore = { InspectorState(it[0] as Float, it[1] as Boolean, it[2] as Int) },
)

/**
 * The workstation layout of tablet and DeX windows ("MusicBee"): the shell's sidebar on the
 * left (already there), [main] in the middle and an inspector pane on the right ([inspector],
 * usually an [InspectorPane]). The divider between them can be dragged, or moved with the arrow
 * keys when focused; the width is remembered by [state]. A collapsed inspector slides shut (300 ms,
 * like the sidebar). Below [showFrom] (phones, narrow windows) only [main] is shown.
 */
@Composable
fun WorkstationLayout(
    state: InspectorState,
    main: @Composable () -> Unit,
    inspector: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    minMain: Dp = 360.dp,
    minInspector: Dp = 240.dp,
    maxInspector: Dp = 560.dp,
    showFrom: WidthClass = WidthClass.Expanded,
) {
    val shown = currentWidthClass() >= showFrom
    val tier = LocalMotionTier.current
    val target = if (!shown || state.collapsed) 0.dp else state.widthDp.dp.coerceIn(minInspector, maxInspector)
    val width by animateDpAsState(
        target,
        if (tier == MotionTier.Reduced) tween(0) else tween(DeskMotion.LONG_MS, easing = DeskMotion.Emphasized),
        label = "inspectorWidth",
    )
    val dividerLabel = stringResource(R.string.dk_resize_inspector)
    Layout(
        modifier = modifier.fillMaxSize(),
        content = {
            Box(Modifier.layoutId("main")) { main() }
            Box(Modifier.layoutId("divider")) {
                if (shown && !state.collapsed) {
                    PaneDivider(
                        label = dividerLabel,
                        value = state.widthDp,
                        onDragDp = { delta -> state.resizeBy(delta, minInspector, maxInspector) },
                        onDragEnd = { state.commit() },
                    )
                }
            }
            Box(Modifier.layoutId("inspector").clipToBounds()) {
                if (width > 0.dp) inspector()
            }
        },
    ) { measurables, constraints ->
        val totalWidth = constraints.maxWidth
        val height = constraints.maxHeight
        val divider = measurables.first { it.layoutId == "divider" }.measure(Constraints(maxWidth = totalWidth, minHeight = height, maxHeight = height))
        // the main pane keeps at least minMain; the inspector gives way first
        val wanted = width.roundToPx()
        val inspectorWidth = wanted.coerceAtMost((totalWidth - divider.width - minMain.roundToPx()).coerceAtLeast(0))
        val mainWidth = (totalWidth - divider.width - inspectorWidth).coerceAtLeast(0)
        val mainPlaceable = measurables.first { it.layoutId == "main" }.measure(Constraints.fixed(mainWidth, height))
        val inspectorPlaceable = measurables.first { it.layoutId == "inspector" }
            .measure(Constraints(minWidth = inspectorWidth, maxWidth = inspectorWidth, minHeight = height, maxHeight = height))
        layout(totalWidth, height) {
            mainPlaceable.placeRelative(0, 0)
            divider.placeRelative(mainWidth, 0)
            inspectorPlaceable.placeRelative(mainWidth + divider.width, 0)
        }
    }
}

/** The draggable rule between the main pane and the inspector. */
@Composable
private fun PaneDivider(label: String, value: Float, onDragDp: (Float) -> Unit, onDragEnd: () -> Unit) {
    val colors = DeskTheme.colors
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val interaction = remember { MutableInteractionSource() }
    val visual = interaction.collectState()
    var held by remember { mutableStateOf(false) }
    val handleWidth = if (DeskTheme.dimens.minTarget > 24.dp) 16.dp else 8.dp
    // dragging towards the start edge makes the inspector (at the end) wider
    val sign = if (rtl) 1f else -1f
    Box(
        Modifier
            .width(handleWidth)
            .fillMaxHeight()
            .pointerHoverIcon(PointerIcon(android.view.PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW))
            .hoverable(interaction)
            .focusable(interactionSource = interaction)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..10000f)
                setProgress { target -> onDragDp(target - value); onDragEnd(); true }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    // the arrow moves the divider; the inspector sits on the end side
                    Key.DirectionLeft -> onDragDp(if (rtl) -16f else 16f)
                    Key.DirectionRight -> onDragDp(if (rtl) 16f else -16f)
                    else -> return@onKeyEvent false
                }
                onDragEnd()
                true
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    held = true
                    try {
                        do {
                            val event = awaitPointerEvent()
                        } while (event.changes.any { it.pressed })
                    } finally {
                        held = false
                    }
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(onDragEnd = onDragEnd, onDragCancel = onDragEnd) { change, amount ->
                    change.consume()
                    onDragDp(sign * with(density) { amount.toDp().value })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val active = visual.hovered || visual.focused || held
        Box(
            Modifier
                .width(if (active) 3.dp else 1.dp)
                .fillMaxHeight()
                .background(if (active) colors.accent else colors.border),
        )
    }
}

/** One tab of an [InspectorPane]. */
@Immutable
data class InspectorTab(val id: String, val label: String, val icon: ImageVector? = null)

/**
 * The inspector of a [WorkstationLayout]: a row of [tabs] (selected one underlined in the accent
 * colour; ←/→ move between them when one has focus), an optional collapse button and the selected
 * tab's [content], cross-faded in 150 ms. Nağmedar puts four tabs here: tags and spectrum summary,
 * signal path, lyrics, queue.
 */
@Composable
fun InspectorPane(
    tabs: List<InspectorTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onCollapse: (() -> Unit)? = null,
    content: @Composable (InspectorTab) -> Unit,
) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val label = stringResource(R.string.dk_inspector)
    val index = selected.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    Column(modifier.fillMaxSize().background(colors.surface).semantics { paneTitle = label }) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = dimens.minTarget.coerceAtLeast(36.dp)).padding(start = dimens.gapXs, end = dimens.gapXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .selectableGroup()
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown || tabs.isEmpty()) return@onKeyEvent false
                        when (event.key) {
                            Key.DirectionRight -> onSelect((index + 1) % tabs.size)
                            Key.DirectionLeft -> onSelect((index - 1 + tabs.size) % tabs.size)
                            else -> return@onKeyEvent false
                        }
                        true
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { i, tab -> InspectorTabButton(tab, i == index) { onSelect(i) } }
            }
            if (onCollapse != null) {
                DeskIconButton(DeskIcons.ChevronRight, stringResource(R.string.dk_inspector_hide), onCollapse)
            }
        }
        HorizontalRule()
        val tab = tabs.getOrNull(index)
        if (tab != null) {
            Crossfade(targetState = tab, animationSpec = tween(150), label = "inspectorTab", modifier = Modifier.fillMaxSize()) { shownTab ->
                Box(Modifier.fillMaxSize()) { content(shownTab) }
            }
        }
    }
}

@Composable
private fun InspectorTabButton(tab: InspectorTab, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    Column(
        Modifier
            .heightIn(min = dimens.minTarget.coerceAtLeast(32.dp))
            .focusRing(state, shape)
            .background(animatedStateBackground(state).value, shape)
            .semantics { this.selected = selected }
            .deskClickable(interaction, onClick, role = Role.Tab)
            .padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.padding(top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (tab.icon != null) {
                Icon(tab.icon, contentDescription = null, tint = if (selected) colors.accentText else colors.textSecondary,
                    modifier = Modifier.size(dimens.icon))
                Spacer(Modifier.width(6.dp))
            }
            Text(tab.label, style = if (selected) DeskTheme.type.bodyStrong else DeskTheme.type.body,
                color = if (selected) colors.text else colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.width(24.dp).height(2.dp).background(if (selected) colors.accent else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(1.dp)))
    }
}

/** A toolbar button that shows or hides the inspector of [state]. */
@Composable
fun InspectorToggleButton(state: InspectorState, modifier: Modifier = Modifier) {
    DeskIconButton(
        icon = DeskIcons.SidebarToggle,
        contentDescription = stringResource(if (state.collapsed) R.string.dk_inspector_show else R.string.dk_inspector_hide),
        onClick = state::toggle,
        modifier = modifier,
        selected = !state.collapsed,
    )
}
