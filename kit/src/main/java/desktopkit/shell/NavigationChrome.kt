package desktopkit.shell

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import desktopkit.R
import desktopkit.components.DeskIconButton
import desktopkit.components.DeskIcons
import desktopkit.components.HorizontalRule
import desktopkit.components.SectionCaption
import desktopkit.components.VerticalRule
import desktopkit.components.collectState
import desktopkit.components.deskClickable
import desktopkit.components.focusRing
import desktopkit.components.stateBackground
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
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .semantics { this.selected = selected }
            .deskClickable(interaction, onClick, role = Role.Tab),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(30.dp)
                .focusRing(state, pill)
                .background(stateBackground(state, selected), pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, contentDescription = null, tint = if (selected) colors.onSelection else colors.textSecondary,
                modifier = Modifier.size(dimens.icon))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = item.shortLabel,
            style = DeskTheme.type.caption,
            color = if (selected) colors.text else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
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
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(28.dp)
                .focusRing(state, pill)
                .background(stateBackground(state, selected), pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, contentDescription = null, tint = if (selected) colors.onSelection else colors.textSecondary,
                modifier = Modifier.size(dimens.icon))
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = item.shortLabel,
            style = DeskTheme.type.caption,
            color = if (selected) colors.text else colors.textSecondary,
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
    val width = if (collapsed) dimens.sidebarCollapsedWidth else dimens.sidebarWidth
    val footer = spec.footer
    Row(modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .width(width)
                .fillMaxHeight()
                .background(colors.chrome)
                .semantics { paneTitle = label },
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = if (collapsed) 6.dp else dimens.gapS, vertical = dimens.gapS)
                    .selectableGroup(),
            ) {
                spec.sections.forEach { section ->
                    if (section.items.isEmpty()) return@forEach
                    val title = section.title
                    if (!collapsed && title != null) {
                        SectionCaption(title, Modifier.padding(start = 10.dp, top = dimens.gapM, bottom = 4.dp))
                    } else {
                        Spacer(Modifier.height(dimens.gapS))
                    }
                    section.items.forEach { item ->
                        SidebarItem(item, item.route == currentRoute, collapsed, badges[item.route]) { onSelect(item.route) }
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
                            SidebarItem(footer, currentRoute == footer.route, collapsed = false, badge = null) { onSelect(footer.route) }
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
                    SidebarItem(footer, currentRoute == footer.route, collapsed = true, badge = null) { onSelect(footer.route) }
                }
            }
        }
        VerticalRule(Modifier.fillMaxHeight())
    }
}

@Composable
private fun SidebarItem(item: NavItem, selected: Boolean, collapsed: Boolean, badge: String?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.navItemHeight.coerceAtLeast(dimens.minTarget))
            .padding(vertical = 1.dp)
            .focusRing(state, shape)
            .background(stateBackground(state, selected), shape)
            .semantics {
                this.selected = selected
                if (collapsed) contentDescription = item.label
            }
            .deskClickable(interaction, onClick, role = Role.Tab)
            .padding(horizontal = if (collapsed) 0.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.Start,
    ) {
        Icon(item.icon, contentDescription = null, tint = if (selected) colors.accentText else colors.textSecondary,
            modifier = Modifier.size(dimens.icon))
        if (!collapsed) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = item.label,
                style = if (selected) DeskTheme.type.bodyStrong else DeskTheme.type.body,
                color = if (selected) colors.onSelection else colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (badge != null) Text(badge, style = DeskTheme.type.caption, color = colors.textTertiary, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------- top bars

/** Compact and medium top bar: page title and, when [onSearch] is set, a search button. */
@Composable
fun PageTopBar(title: String, onSearch: (() -> Unit)?, modifier: Modifier = Modifier) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    Column(modifier.fillMaxWidth().background(colors.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = dimens.topBarHeight).padding(start = dimens.pagePadding, end = dimens.gapS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = DeskTheme.type.headline, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            if (onSearch != null) DeskIconButton(DeskIcons.Search, stringResource(R.string.dk_search), onSearch)
        }
    }
}

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
