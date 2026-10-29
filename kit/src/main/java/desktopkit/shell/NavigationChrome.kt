package desktopkit.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import desktopkit.R
import desktopkit.components.DeskIconButton
import desktopkit.components.DeskIcons
import desktopkit.components.HorizontalRule
import desktopkit.components.SectionCaption
import desktopkit.components.VerticalRule
import desktopkit.components.animateInteractionColor
import desktopkit.components.collectState
import desktopkit.components.deskClickable
import desktopkit.components.focusRing
import desktopkit.components.pressScale
import desktopkit.components.stateBackground
import desktopkit.theme.DeskMotion
import desktopkit.theme.DeskTheme

/** Item counts and badges shown next to sidebar entries ("4 812", "4 new"), by route. */
typealias NavBadges = Map<String, String>

/** The kit's "More" entry; it lists [ShellSpec.overflow] for the current chrome. */
@Composable
internal fun moreItem() = NavItem(ShellSpec.MORE_ROUTE, stringResource(R.string.dk_more), DeskIcons.Menu)

// ---------------------------------------------------------------- compact: bottom bar

@Composable
fun BottomNavBar(spec: ShellSpec, currentRoute: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = DeskTheme.colors
    val label = stringResource(R.string.dk_main_navigation)
    val items = spec.shown(NavKind.BottomBar) + if (spec.overflow(NavKind.BottomBar).isEmpty()) emptyList() else listOf(moreItem())
    Column(modifier.fillMaxWidth().background(colors.chrome)) {
        HorizontalRule()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DeskTheme.dimens.bottomBarHeight)
                .semantics { paneTitle = label }
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                BottomBarItem(item, selected = isSelected(item, currentRoute, items), onClick = { onSelect(item.route) })
            }
        }
    }
}

@Composable
private fun RowScope.BottomBarItem(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val pill = RoundedCornerShape(dimens.radiusPill)
    val iconTint by animateNavColor(if (selected) colors.onSelection else colors.textSecondary)
    val labelColor by animateNavColor(if (selected) colors.text else colors.textSecondary)
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .semantics { this.selected = selected }
            .deskClickable(interaction, onClick, role = Role.Tab),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NavIndicator(selected, state, pill, Modifier.width(56.dp).height(30.dp)) {
            Icon(item.icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(dimens.icon))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = item.shortLabel,
            style = if (selected) DeskTheme.type.caption.copy(fontWeight = FontWeight.Medium) else DeskTheme.type.caption,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The pill behind a navigation icon. Hover and press tint the whole pill; the selection pill grows
 * out from the middle with a soft spring when the item becomes selected and shrinks back when it is
 * left, as in Android's navigation bar and rail. A finger press also shrinks the icon slightly.
 */
@Composable
private fun NavIndicator(
    selected: Boolean,
    state: desktopkit.components.InteractionState,
    shape: RoundedCornerShape,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val colors = DeskTheme.colors
    val fraction by animateFloatAsState(if (selected) 1f else 0f, DeskMotion.spatial(), label = "navIndicator")
    val feedback by animateInteractionColor(
        if (state.pressed) colors.pressed else if (state.hovered && !selected) colors.hover else Color.Transparent,
        selected, tween(NAV_COLOR_MS),
    )
    val selection = colors.selection
    Box(
        modifier = modifier
            .pressScale(state.pressed, 0.94f)
            .focusRing(state, shape)
            .drawBehind {
                if (fraction > 0.001f) {
                    val width = size.width * (0.4f + 0.6f * fraction)
                    drawRoundRect(
                        color = selection.copy(alpha = selection.alpha * fraction.coerceIn(0f, 1f)),
                        topLeft = Offset((size.width - width) / 2f, 0f),
                        size = Size(width, size.height),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                }
            }
            .background(feedback, shape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ---------------------------------------------------------------- medium/expanded: rail

@Composable
fun NavRail(
    spec: ShellSpec,
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    brand: (@Composable (Modifier) -> Unit)? = null,
) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val label = stringResource(R.string.dk_main_navigation)
    val main = spec.railRoutes.mapNotNull(spec::item)
    val footer = listOfNotNull(if (spec.overflow(NavKind.Rail).isEmpty()) null else moreItem(), spec.footer)
    val all = main + footer
    Row(modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .width(dimens.railWidth)
                .fillMaxHeight()
                .background(colors.chrome)
                .semantics { paneTitle = label }
                .padding(vertical = dimens.gapS),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            brand?.invoke(Modifier.padding(vertical = dimens.gapS).size(28.dp))
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).selectableGroup(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                main.forEach { item -> RailItem(item, isSelected(item, currentRoute, all)) { onSelect(item.route) } }
            }
            footer.forEach { item -> RailItem(item, isSelected(item, currentRoute, all)) { onSelect(item.route) } }
        }
        VerticalRule(Modifier.fillMaxHeight())
    }
}

@Composable
private fun RailItem(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val pill = RoundedCornerShape(dimens.radiusPill)
    val iconTint by animateNavColor(if (selected) colors.onSelection else colors.textSecondary)
    val labelColor by animateNavColor(if (selected) colors.text else colors.textSecondary)
    Column(
        modifier = Modifier
            .width(dimens.railWidth)
            .heightIn(min = dimens.minTarget.coerceAtLeast(52.dp))
            .semantics { this.selected = selected }
            .deskClickable(interaction, onClick, role = Role.Tab)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NavIndicator(selected, state, pill, Modifier.width(48.dp).height(28.dp)) {
            Icon(item.icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(dimens.icon))
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = item.shortLabel,
            style = DeskTheme.type.caption,
            color = labelColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

// ---------------------------------------------------------------- large/extra-large: sidebar

@Composable
fun Sidebar(
    spec: ShellSpec,
    currentRoute: String?,
    onSelect: (String) -> Unit,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    badges: NavBadges,
    modifier: Modifier = Modifier,
) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val label = stringResource(R.string.dk_sidebar)
    // the panel slides open and shut on the emphasized curve; labels fade in once there is room for them
    val width by animateDpAsState(
        if (collapsed) dimens.sidebarCollapsedWidth else dimens.sidebarWidth,
        tween(DeskMotion.LONG_MS, easing = DeskMotion.Emphasized),
        label = "sidebarWidth",
    )
    val span = (dimens.sidebarWidth - dimens.sidebarCollapsedWidth).value
    val open = if (span <= 0f) 1f else ((width - dimens.sidebarCollapsedWidth).value / span).coerceIn(0f, 1f)
    val footer = spec.footer
    Row(modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .width(width)
                .fillMaxHeight()
                .clipToBounds()
                .background(colors.chrome)
                .semantics { paneTitle = label },
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = lerp(6.dp, dimens.gapS, open), vertical = dimens.gapS)
                    .selectableGroup(),
            ) {
                spec.sections.forEach { section ->
                    if (section.items.isEmpty()) return@forEach
                    val title = section.title
                    if (!collapsed && title != null) {
                        SectionCaption(title, Modifier.padding(start = 10.dp, top = dimens.gapM, bottom = 4.dp).alpha(labelAlpha(open)))
                    } else {
                        Spacer(Modifier.height(dimens.gapS))
                    }
                    section.items.forEach { item ->
                        SidebarItem(item, item.route == currentRoute, collapsed, badges[item.route], open) { onSelect(item.route) }
                    }
                }
            }
            HorizontalRule()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = if (collapsed) 6.dp else dimens.gapS, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!collapsed) {
                    Box(Modifier.weight(1f)) {
                        if (footer != null) {
                            SidebarItem(footer, currentRoute == footer.route, collapsed = false, badge = null, open = open) { onSelect(footer.route) }
                        }
                    }
                }
                DeskIconButton(
                    icon = if (collapsed) DeskIcons.ChevronRight else DeskIcons.ChevronLeft,
                    contentDescription = stringResource(if (collapsed) R.string.dk_sidebar_expand else R.string.dk_sidebar_collapse),
                    onClick = onToggleCollapsed,
                )
            }
            if (collapsed && footer != null) {
                Box(Modifier.padding(start = 6.dp, end = 6.dp, bottom = 6.dp)) {
                    SidebarItem(footer, currentRoute == footer.route, collapsed = true, badge = null, open = open) { onSelect(footer.route) }
                }
            }
        }
        VerticalRule(Modifier.fillMaxHeight())
    }
}

/** Labels stay hidden in the first half of the opening, so they never show squeezed. */
private fun labelAlpha(open: Float): Float = ((open - 0.5f) * 2f).coerceIn(0f, 1f)

/**
 * [open] runs from 0 (collapsed) to 1 (expanded) while the sidebar animates; the icon keeps its
 * place in a collapsed item and moves smoothly to its expanded position.
 */
@Composable
private fun SidebarItem(item: NavItem, selected: Boolean, collapsed: Boolean, badge: String?, open: Float = if (collapsed) 0f else 1f,
                        onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    val indicator by animateInteractionColor(stateBackground(state, selected), selected, tween(NAV_COLOR_MS))
    val iconTint by animateNavColor(if (selected) colors.accentText else colors.textSecondary)
    val labelColor by animateNavColor(if (selected) colors.onSelection else colors.text)
    // a collapsed item centres its icon; the same start padding keeps it still while the panel opens
    val collapsedStart: Dp = ((dimens.sidebarCollapsedWidth - 12.dp - dimens.icon) / 2).coerceAtLeast(0.dp)
    val showLabel = !collapsed && open > 0.5f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.navItemHeight.coerceAtLeast(dimens.minTarget))
            .padding(vertical = 1.dp)
            .focusRing(state, shape)
            .background(indicator, shape)
            .semantics {
                this.selected = selected
                if (collapsed) contentDescription = item.label
            }
            .deskClickable(interaction, onClick, role = Role.Tab)
            .padding(start = lerp(collapsedStart, 10.dp, open), end = if (showLabel) 10.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(item.icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(dimens.icon))
        if (showLabel) {
            Spacer(Modifier.width(10.dp))
            Row(Modifier.weight(1f).alpha(labelAlpha(open)), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.label,
                    style = if (selected) DeskTheme.type.bodyStrong else DeskTheme.type.body,
                    color = labelColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (badge != null) Text(badge, style = DeskTheme.type.caption, color = colors.textTertiary, maxLines = 1)
            }
        }
    }
}

// ---------------------------------------------------------------- top bars

/**
 * Compact and medium top bar: page title and, when [onSearch] is set, a search button. On compact
 * windows the shell passes what the current screen asked for with [CompactTopBar]: [onBack] adds a
 * back or close button before the title and [actions] add icon buttons before the search button
 * (more than three: two icons and a "more" menu). The title cross-fades when it changes.
 */
@Composable
fun PageTopBar(
    title: String,
    onSearch: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backIcon: TopBarBackIcon = TopBarBackIcon.Back,
    actions: List<TopBarAction> = emptyList(),
    scrolled: Boolean = false,
) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val latestOnBack by rememberUpdatedState(onBack)
    val (shown, overflow) = remember(actions) { splitTopBarActions(actions) }
    // content scrolled beneath: the bar takes the chrome colour and a rule fades in, as Android's top app bar does
    val background by animateColorAsState(if (scrolled) colors.chrome else colors.background, tween(DeskMotion.MEDIUM_MS), label = "topBar")
    val ruleAlpha by animateFloatAsState(if (scrolled) 1f else 0f, tween(DeskMotion.MEDIUM_MS), label = "topBarRule")
    val rule = colors.border
    Column(
        modifier
            .fillMaxWidth()
            .background(background)
            // drawn over the bottom edge, so the bar keeps its height whether or not the rule shows
            .drawBehind {
                if (ruleAlpha > 0f) {
                    val line = 1.dp.toPx()
                    drawRect(rule.copy(alpha = rule.alpha * ruleAlpha), topLeft = Offset(0f, size.height - line), size = Size(size.width, line))
                }
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = dimens.topBarHeight).padding(end = dimens.gapS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = TopBarHeading(title, if (onBack != null) backIcon else null),
                transitionSpec = { fadeIn(tween(TOP_BAR_FADE_MS)) togetherWith fadeOut(tween(TOP_BAR_FADE_MS)) },
                contentAlignment = Alignment.CenterStart,
                label = "topBarHeading",
                modifier = Modifier.weight(1f),
            ) { heading ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = if (heading.back != null) dimens.gapXs else dimens.pagePadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (heading.back != null) {
                        TopBarIconButton(
                            icon = if (heading.back == TopBarBackIcon.Close) DeskIcons.Close else DeskIcons.ArrowLeft,
                            label = stringResource(if (heading.back == TopBarBackIcon.Close) R.string.dk_close else R.string.dk_back),
                            onClick = { latestOnBack?.invoke() },
                            size = 48.dp,
                        )
                        Spacer(Modifier.width(dimens.gapXs))
                    }
                    Text(heading.title, style = DeskTheme.type.headline, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                }
            }
            shown.forEach { action ->
                TopBarIconButton(action.icon, action.label, action.onClick, enabled = action.enabled, primary = action.primary)
            }
            if (overflow.isNotEmpty()) TopBarOverflow(overflow)
            if (onSearch != null) DeskIconButton(DeskIcons.Search, stringResource(R.string.dk_search), onSearch)
        }
    }
}

@Immutable
private data class TopBarHeading(val title: String, val back: TopBarBackIcon?)

private const val TOP_BAR_FADE_MS = 150

/** Colour changes of navigation items (selection, hover, press) ease over [NAV_COLOR_MS]. */
private const val NAV_COLOR_MS = 150

@Composable
private fun animateNavColor(target: Color) = animateColorAsState(target, tween(NAV_COLOR_MS), label = "navColor")

/** Large and extra-large: brand, application menus and an optional search/command field. */
@Composable
fun MenuBarChrome(
    title: String,
    menus: @Composable RowScope.() -> Unit,
    search: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    brand: (@Composable (Modifier) -> Unit)? = null,
) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    Column(modifier.fillMaxWidth().background(colors.chrome)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = dimens.menuBarHeight).padding(start = 14.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (brand != null) {
                brand(Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(title, style = DeskTheme.type.bodyStrong, color = colors.text, maxLines = 1)
            Spacer(Modifier.width(14.dp))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, content = menus)
            search?.invoke()
        }
        HorizontalRule()
    }
}

/** Field-looking button that opens search or a command palette. */
@Composable
fun SearchField(onClick: () -> Unit, shortcut: String?, modifier: Modifier = Modifier, hint: String? = null) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val shape = RoundedCornerShape(7.dp)
    Row(
        modifier = modifier
            .heightIn(min = dimens.controlHeight.coerceAtLeast(dimens.minTarget))
            .focusRing(state, shape)
            .background(if (state.hovered) colors.hover else colors.surface, shape)
            .deskClickable(interaction, onClick, onClickLabel = stringResource(R.string.dk_search))
            .padding(start = 10.dp, end = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(DeskIcons.Search, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(dimens.icon))
        Spacer(Modifier.width(8.dp))
        Text(hint ?: stringResource(R.string.dk_search_hint), style = DeskTheme.type.body, color = colors.textTertiary, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (shortcut != null) KeyCap(shortcut)
    }
}

/** Keyboard key cap, e.g. "Ctrl+K", in the monospace face. */
@Composable
fun KeyCap(text: String, modifier: Modifier = Modifier) {
    val colors = DeskTheme.colors
    Box(
        modifier
            .background(colors.surfaceRaised, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(text, style = DeskTheme.type.mono, color = colors.textSecondary, maxLines = 1)
    }
}

/** "More" is selected while the current screen is one of those it lists. */
private fun isSelected(item: NavItem, currentRoute: String?, shown: List<NavItem>): Boolean =
    if (item.route == ShellSpec.MORE_ROUTE) {
        currentRoute == ShellSpec.MORE_ROUTE || shown.none { it.route == currentRoute }
    } else {
        item.route == currentRoute
    }
