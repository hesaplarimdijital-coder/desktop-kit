package desktopkit.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import desktopkit.R
import desktopkit.components.BelowAnchorPositionProvider
import desktopkit.components.DeskIcons
import desktopkit.components.MenuEntry
import desktopkit.components.MenuPopup
import desktopkit.components.TooltipArea
import desktopkit.components.TooltipBubble
import desktopkit.components.collectState
import desktopkit.components.focusRing
import desktopkit.components.stateBackground
import desktopkit.theme.DeskTheme
import kotlinx.coroutines.delay

/** The leading button of the compact top bar: an arrow (go back) or a cross (close, cancel). */
enum class TopBarBackIcon { Back, Close }

/**
 * An icon button at the end of the compact top bar. [label] is the content description, the
 * mouse tooltip and the long-press tooltip; in the "more" menu it is the item's text.
 * [primary] draws the icon in the accent colour.
 */
@Immutable
data class TopBarAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val primary: Boolean = false,
)

/**
 * The [WidthClass] the surrounding [DesktopShell] (or [desktopkit.DesktopKitRoot]) lays out for.
 * Outside both it is [WidthClass.ExtraLarge], matching [LocalNavKind]'s default.
 */
val LocalWidthClass = compositionLocalOf { WidthClass.ExtraLarge }

/** The [WidthClass] the shell is using, for screens that lay out differently on a phone. */
@Composable
@ReadOnlyComposable
fun currentWidthClass(): WidthClass = LocalWidthClass.current

/**
 * Replaces the compact (phone) top bar's title, adds a back/close button and action icons while
 * this is in composition. No effect on Medium and wider layouts (DeX unchanged). The last one
 * composed wins; leaving composition restores the default.
 *
 * - [title]: shown instead of the route's label (null keeps it); single line, cross-fades when it changes.
 * - [onBack]: adds a 48 dp back (arrow) or close (cross, [backIcon]) button before the title, and
 *   the system Back gesture calls it while this is the active top bar. Nothing is registered on
 *   wider layouts, where Back keeps its usual meaning.
 * - [actions]: icon buttons before the search button; with more than three, the first two (primary
 *   ones first) stay and the rest go into a "more" (⋮) menu.
 * - [showSearch]: false hides the shell's search button.
 */
@Composable
fun CompactTopBar(
    title: String? = null,
    onBack: (() -> Unit)? = null,
    backIcon: TopBarBackIcon = TopBarBackIcon.Back,
    actions: List<TopBarAction> = emptyList(),
    showSearch: Boolean = true,
) {
    val host = LocalCompactTopBarHost.current ?: return
    if (LocalWidthClass.current != WidthClass.Compact) return
    val latestOnBack by rememberUpdatedState(onBack)
    val stableBack: () -> Unit = remember { { latestOnBack?.invoke() } }
    val override = TopBarOverride(title, if (onBack != null) stableBack else null, backIcon, actions, showSearch)
    val entry = remember(host) { CompactTopBarHost.Entry(override) }
    SideEffect { entry.value = override }
    DisposableEffect(host, entry) {
        host.add(entry)
        onDispose { host.remove(entry) }
    }
    val active by remember(host, entry) { derivedStateOf { host.isActive(entry) } }
    BackHandler(enabled = onBack != null && active) { latestOnBack?.invoke() }
}

/** What a [CompactTopBar] call asks the top bar to show. */
@Immutable
internal data class TopBarOverride(
    val title: String?,
    val onBack: (() -> Unit)?,
    val backIcon: TopBarBackIcon,
    val actions: List<TopBarAction>,
    val showSearch: Boolean,
)

/** The [CompactTopBar] calls in composition, oldest first; the newest one is shown. */
@Stable
internal class CompactTopBarHost {
    class Entry(initial: TopBarOverride) {
        var value by mutableStateOf(initial)
    }

    private val entries = mutableStateListOf<Entry>()

    val current: TopBarOverride? get() = entries.lastOrNull()?.value

    fun isActive(entry: Entry): Boolean = entries.lastOrNull() === entry

    fun add(entry: Entry) {
        entries.add(entry)
    }

    fun remove(entry: Entry) {
        entries.remove(entry)
    }
}

internal val LocalCompactTopBarHost = staticCompositionLocalOf<CompactTopBarHost?> { null }

/** Icons shown in the top bar at most, the "more" button included. */
internal const val MAX_TOP_BAR_ICONS = 3

/** Splits [actions] into the icons shown in the bar and those in the "more" menu, keeping their order. */
internal fun splitTopBarActions(actions: List<TopBarAction>): Pair<List<TopBarAction>, List<TopBarAction>> {
    if (actions.size <= MAX_TOP_BAR_ICONS) return actions to emptyList()
    val shown = actions.indices.sortedBy { if (actions[it].primary) 0 else 1 }.take(MAX_TOP_BAR_ICONS - 1).toSet()
    return actions.filterIndexed { i, _ -> i in shown } to actions.filterIndexed { i, _ -> i !in shown }
}

/** Icon button of the top bar: mouse tooltip, long-press tooltip, accent tint for primary actions. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TopBarIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    size: Dp = DeskTheme.dimens.minTarget.coerceAtLeast(DeskTheme.dimens.controlHeight),
) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = DeskTheme.colors
    val shape = RoundedCornerShape(DeskTheme.dimens.radiusControl)
    var longPressTip by remember { mutableStateOf(false) }
    LaunchedEffect(longPressTip) {
        if (longPressTip) {
            delay(1500)
            longPressTip = false
        }
    }
    val tint = if (primary) colors.accentText else colors.textSecondary
    TooltipArea(text = label, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(size)
                .focusRing(state, shape)
                .background(stateBackground(state), shape)
                .pointerHoverIcon(PointerIcon.Hand)
                .hoverable(interaction, enabled)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onClickLabel = label,
                    role = Role.Button,
                    onLongClick = { longPressTip = true },
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = if (enabled) tint else tint.copy(alpha = 0.4f),
                modifier = Modifier.size(DeskTheme.dimens.icon))
            if (longPressTip) {
                Popup(popupPositionProvider = remember { BelowAnchorPositionProvider() }, onDismissRequest = { longPressTip = false }) {
                    TooltipBubble(label, null)
                }
            }
        }
    }
}

/** The ⋮ button and its menu for the actions that do not fit. */
@Composable
internal fun TopBarOverflow(actions: List<TopBarAction>) {
    var open by remember { mutableStateOf(false) }
    var height by remember { mutableIntStateOf(0) }
    Box(Modifier.onSizeChanged { height = it.height }) {
        TopBarIconButton(DeskIcons.MoreVertical, stringResource(R.string.dk_more_options), onClick = { open = true })
        MenuPopup(
            visible = open,
            onDismiss = { open = false },
            entries = actions.map { MenuEntry.Item(it.label, it.onClick, icon = it.icon, enabled = it.enabled) },
            offset = IntOffset(0, height),
        )
    }
}
