package desktopkit.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.absolutePadding
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import desktopkit.R
import desktopkit.components.DeskIconButton
import desktopkit.components.DeskDragPayload
import desktopkit.components.DeskIcons
import desktopkit.components.dropZone
import desktopkit.components.HorizontalRule
import desktopkit.components.SectionCaption
import desktopkit.components.VerticalRule
import desktopkit.components.animateInteractionColor
import desktopkit.components.collectState
import desktopkit.components.deskClickable
import desktopkit.components.focusRing
import desktopkit.components.pressScale
import desktopkit.components.stateBackground
import desktopkit.theme.DeskGlass
import desktopkit.theme.DeskMotion
import desktopkit.theme.DeskTheme
import desktopkit.theme.GlassThickness
import desktopkit.theme.LocalMotionTier
import desktopkit.theme.MotionTier
import desktopkit.theme.glass
import desktopkit.input.showShortcutHints

/** Item counts and badges shown next to sidebar entries ("4 812", "4 new"), by route. */
typealias NavBadges = Map<String, String>

/**
 * A mark on a navigation entry's icon in the phone's bottom bar, the rail and the collapsed sidebar:
 * a count of new things ([count] above 0; "99+" past 99) or, with a [count] of 0, a dot. TalkBack
 * reads [description] after the entry's name ("Inbox, 4 unread"); without one it says the count
 * ("4 new") or "new". In the open sidebar the entry's text badge ([NavBadges]) says it instead.
 */
@Immutable
data class NavIconBadge(val count: Int = 0, val description: String? = null)

/** [NavIconBadge]s by route. */
typealias NavIconBadges = Map<String, NavIconBadge>

/** [item]'s badge; "More" gets a dot when a screen it holds has one. */
private fun badgeOf(item: NavItem, badges: NavIconBadges, spec: ShellSpec, kind: NavKind): NavIconBadge? =
    badges[item.route] ?: if (item.route == ShellSpec.MORE_ROUTE && spec.overflow(kind).any { badges[it.route] != null }) NavIconBadge() else null

/** What TalkBack says after an entry's name for [badge]. */
@Composable
private fun badgeDescription(badge: NavIconBadge): String = badge.description
    ?: if (badge.count > 0) pluralStringResource(R.plurals.dk_badge_new, badge.count, badge.count) else stringResource(R.string.dk_badge_dot)

/**
 * The [badge] at the top end corner of a navigation icon of [iconSize]: a small accent pill with
 * the count, or a dot. Drawn over the icon (it takes no room), outlined in [ring] so it stands
 * apart from the icon under it.
 */
@Composable
private fun BoxScope.IconBadge(badge: NavIconBadge?, iconSize: Dp, ring: Color) {
    if (badge == null) return
    val colors = DeskTheme.colors
    val half = iconSize / 2
    if (badge.count > 0) {
        val text = if (badge.count > 99) "99+" else badge.count.toString()
        Box(
            Modifier
                .align(Alignment.Center)
                // its start edge a little inside the icon's end, its middle at the icon's top
                .offset(x = half + 4.dp, y = -half)
                .wrapContentSize(unbounded = true)
                .heightIn(min = 16.dp)
                .defaultMinSize(minWidth = 16.dp)
                .border(1.5.dp, ring, RoundedCornerShape(50))
                .background(colors.accent, RoundedCornerShape(50))
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = DeskTheme.type.caption.copy(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold),
                color = colors.onAccent, maxLines = 1)
        }
    } else {
        Box(
            Modifier
                .align(Alignment.Center)
                .offset(x = half, y = -half + 1.dp)
                .size(9.dp)
                .border(1.5.dp, ring, CircleShape)
                .background(colors.accent, CircleShape),
        )
    }
}

/**
 * Lets the sidebar's and the rail's entries take drops: tracks and albums dragged onto
 * "Playlists", files onto "Bin". [kinds] says which drag kinds ([desktopkit.components.DeskDragPayload.kind])
 * each entry takes, by route; entries left out take none. [onDrop] gets the route and what was
 * dropped and says whether it was taken. While a fitting drag is in the air its entries are tinted;
 * the one under the pointer is highlighted.
 */
@Immutable
class NavDrop(
    val kinds: Map<String, Set<String>>,
    val onDrop: (route: String, payload: DeskDragPayload) -> Boolean,
)

/** The drop target of a navigation entry, when [navDrop] takes drops on [route]. */
@Composable
private fun Modifier.navDropZone(navDrop: NavDrop?, route: String, shape: androidx.compose.ui.graphics.Shape): Modifier {
    val kinds = navDrop?.kinds?.get(route)
    return if (navDrop == null || kinds.isNullOrEmpty()) this
    else dropZone(kinds, onDrop = { payload -> navDrop.onDrop(route, payload) }, shape = shape)
}

/** The kit's "More" entry; it lists [ShellSpec.overflow] for the current chrome. */
@Composable
internal fun moreItem() = NavItem(ShellSpec.MORE_ROUTE, stringResource(R.string.dk_more), DeskIcons.Menu)

/** The "⋯" entry of [MoreStyle.Sheet]. */
@Composable
internal fun moreSheetItem() = NavItem(ShellSpec.MORE_ROUTE, stringResource(R.string.dk_more), DeskIcons.MoreHorizontal)

// ---------------------------------------------------------------- compact: bottom bar

/**
 * The phone's bottom bar: the [ShellSpec.bottomBarRoutes] and, when other screens (or, with
 * [MoreStyle.Sheet], [hasMoreActions]) are left, a "More" tab ("⋯" with [MoreStyle.Sheet]) that
 * calls [onSelect] with [ShellSpec.MORE_ROUTE]. Glass inside [DesktopShell], chrome colour elsewhere.
 */
@Composable
fun BottomNavBar(
    spec: ShellSpec,
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    hasMoreActions: Boolean = false,
    iconBadges: NavIconBadges = emptyMap(),
) {
    val colors = DeskTheme.colors
    val label = stringResource(R.string.dk_main_navigation)
    val sheet = spec.moreStyle == MoreStyle.Sheet
    val needsMore = spec.overflow(NavKind.BottomBar).isNotEmpty() || (sheet && hasMoreActions)
    val items = spec.shown(NavKind.BottomBar) + if (!needsMore) emptyList() else listOf(if (sheet) moreSheetItem() else moreItem())
    // in the phone's shell the bar lies on the shell's one bottom glass
    Column(modifier.fillMaxWidth().glass(DeskGlass.chrome(colors, GlassThickness.Regular), enabled = LocalShellBottomGlass.current == null)) {
        HorizontalRule()
        // the bar's height, or more with large text (the labels keep their lines whole)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .semantics { paneTitle = label }
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                BottomBarItem(item, selected = isSelected(item, currentRoute, items), badge = badgeOf(item, iconBadges, spec, NavKind.BottomBar),
                    onClick = { onSelect(item.route) })
            }
        }
    }
}

@Composable
private fun RowScope.BottomBarItem(item: NavItem, selected: Boolean, badge: NavIconBadge?, onClick: () -> Unit) {
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
            .heightIn(min = dimens.bottomBarHeight)
            .fillMaxHeight()
            .semantics { this.selected = selected }
            .deskClickable(interaction, onClick, role = Role.Tab),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NavIndicator(selected, state, pill, Modifier.width(56.dp).height(30.dp)) {
            Icon(item.icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(dimens.icon))
            IconBadge(badge, dimens.icon, ring = colors.chrome)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = item.shortLabel,
            style = if (selected) DeskTheme.type.caption.copy(fontWeight = FontWeight.Medium) else DeskTheme.type.caption,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.badgeSemantics(item.shortLabel, badge),
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
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = DeskTheme.colors
    val fraction by animateFloatAsState(if (selected) 1f else 0f, DeskMotion.fade(LocalMotionTier.current, DeskMotion.spatial()), label = "navIndicator")
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
    navDrop: NavDrop? = null,
    iconBadges: NavIconBadges = emptyMap(),
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
                main.forEach { item -> RailItem(item, isSelected(item, currentRoute, all), navDrop, badgeOf(item, iconBadges, spec, NavKind.Rail)) { onSelect(item.route) } }
            }
            footer.forEach { item -> RailItem(item, isSelected(item, currentRoute, all), navDrop, badgeOf(item, iconBadges, spec, NavKind.Rail)) { onSelect(item.route) } }
        }
        VerticalRule(Modifier.fillMaxHeight())
    }
}

@Composable
private fun RailItem(item: NavItem, selected: Boolean, navDrop: NavDrop?, badge: NavIconBadge?, onClick: () -> Unit) {
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
            .navDropZone(navDrop, item.route, pill)
            .semantics { this.selected = selected }
            .deskClickable(interaction, onClick, role = Role.Tab)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NavIndicator(selected, state, pill, Modifier.width(48.dp).height(28.dp)) {
            Icon(item.icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(dimens.icon))
            IconBadge(badge, dimens.icon, ring = colors.chrome)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = item.shortLabel,
            style = DeskTheme.type.caption,
            color = labelColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp).badgeSemantics(item.shortLabel, badge),
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
    navDrop: NavDrop? = null,
    iconBadges: NavIconBadges = emptyMap(),
) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val label = stringResource(R.string.dk_sidebar)
    // the panel slides open and shut on the emphasized curve; labels fade in once there is room for them
    val width by animateDpAsState(
        if (collapsed) dimens.sidebarCollapsedWidth else dimens.sidebarWidth,
        DeskMotion.fade(LocalMotionTier.current, tween(DeskMotion.LONG_MS, easing = DeskMotion.Emphasized)),
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
                        SidebarItem(item, item.route == currentRoute, collapsed, badges[item.route], open, navDrop, iconBadges[item.route]) { onSelect(item.route) }
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
                            SidebarItem(footer, currentRoute == footer.route, collapsed = false, badge = badges[footer.route], open = open,
                                navDrop = navDrop, iconBadge = iconBadges[footer.route]) { onSelect(footer.route) }
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
                    SidebarItem(footer, currentRoute == footer.route, collapsed = true, badge = badges[footer.route], open = open,
                        navDrop = navDrop, iconBadge = iconBadges[footer.route]) { onSelect(footer.route) }
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
                        navDrop: NavDrop? = null, iconBadge: NavIconBadge? = null, onClick: () -> Unit) {
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
    // the icon's mark only while the sidebar is collapsed: open, the entry's text badge says it
    val shownIconBadge = if (collapsed) iconBadge else null
    val iconBadgeText = shownIconBadge?.let { badgeDescription(it) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.navItemHeight.coerceAtLeast(dimens.minTarget))
            .padding(vertical = 1.dp)
            .navDropZone(navDrop, item.route, shape)
            .focusRing(state, shape)
            .background(indicator, shape)
            .semantics {
                this.selected = selected
                if (collapsed) contentDescription = if (iconBadgeText != null) "${item.label}, $iconBadgeText" else item.label
            }
            .deskClickable(interaction, onClick, role = Role.Tab)
            .padding(start = lerp(collapsedStart, 10.dp, open), end = if (showLabel) 10.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(item.icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(dimens.icon))
            IconBadge(shownIconBadge, dimens.icon, ring = colors.chrome)
        }
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
                if (badge != null) Text(badge, style = DeskTheme.type.caption, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ---------------------------------------------------------------- top bars

/**
 * Compact and medium top bar: page title and, when [onSearch] is set, a search button. With
 * [glass] (the phone shell, where the content lies under the bar) it turns to glass once [scrolled].
 * On compact
 * windows the shell passes what the current screen asked for with [CompactTopBar]: [onBack] adds a
 * back or close button before the title and [actions] add icon buttons before the search button
 * (more than three: two icons and a "more" menu). A new title fades in once the old one is gone.
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
    glass: Boolean = false,
) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val tier = LocalMotionTier.current
    val latestOnBack by rememberUpdatedState(onBack)
    val (shown, overflow) = remember(actions) { splitTopBarActions(actions) }
    // content scrolled beneath: the bar takes the chrome colour (or turns to glass over content lying
    // under it) and a rule fades in, as Android's top app bar does. Toward glass only the opacity of
    // the background changes: a fade to Color.Transparent (transparent black) passes through grey.
    val rest = if (glass) colors.background else Color.Transparent
    val background by animateColorAsState(
        when {
            !scrolled -> colors.background
            glass -> colors.background.copy(alpha = 0f)
            else -> colors.chrome
        },
        DeskMotion.fade(tier, tween(DeskMotion.MEDIUM_MS)), label = "topBar",
    )
    val ruleAlpha by animateFloatAsState(if (scrolled) 1f else 0f, DeskMotion.fade(tier, tween(DeskMotion.MEDIUM_MS)), label = "topBarRule")
    val rule = colors.border
    // the glass stays until the page colour has faded back over it, so the way back is a fade too
    // (it used to switch to the flat colour in one frame under a fade nobody could see)
    val glassOn = glass && (scrolled || background.alpha < 0.999f)
    Column(
        modifier
            .fillMaxWidth()
            .glass(DeskGlass.chrome(colors, GlassThickness.Regular), enabled = glassOn, rest = rest)
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
            // The back button slides in or out before the title, and the title moves along with it. The title
            // only fades when its words change: 2.4.0 faded the whole heading out and in when only the button
            // came or went ("Gelen" › "← Gelen" on opening a message, DeskMail's request 18).
            val withBack = onBack != null
            // the button keeps its icon while it slides away
            val lastBack = remember { arrayOf(backIcon) }
            if (withBack) lastBack[0] = backIcon
            val lastBackIcon = lastBack[0]
            val slide = DeskMotion.fade(tier, DeskMotion.open<IntSize>(IntSize.VisibilityThreshold))
            val inset by animateDpAsState(
                if (withBack) 0.dp else (dimens.pagePadding - dimens.gapXs).coerceAtLeast(0.dp),
                DeskMotion.fade(tier, DeskMotion.open(Dp.VisibilityThreshold)),
                label = "topBarTitleInset",
            )
            Row(Modifier.weight(1f).padding(start = dimens.gapXs), verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(
                    visible = withBack,
                    enter = expandHorizontally(slide, expandFrom = Alignment.Start) + fadeIn(DeskMotion.fade(tier, tween(TOP_BAR_FADE_MS))),
                    exit = shrinkHorizontally(slide, shrinkTowards = Alignment.Start) + fadeOut(DeskMotion.fade(tier, tween(TOP_BAR_FADE_OUT_MS))),
                    label = "topBarBack",
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TopBarIconButton(
                            icon = if (lastBackIcon == TopBarBackIcon.Close) DeskIcons.Close else DeskIcons.ArrowLeft,
                            label = stringResource(if (lastBackIcon == TopBarBackIcon.Close) R.string.dk_close else R.string.dk_back),
                            onClick = { latestOnBack?.invoke() },
                            size = 48.dp,
                        )
                        Spacer(Modifier.width(dimens.gapXs))
                    }
                }
                AnimatedContent(
                    targetState = title,
                    // the old title is gone before the new one fades in: two titles never overlap
                    transitionSpec = {
                        if (tier == MotionTier.Reduced) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        else fadeIn(tween(TOP_BAR_FADE_MS, delayMillis = TOP_BAR_FADE_OUT_MS)) togetherWith fadeOut(tween(TOP_BAR_FADE_OUT_MS))
                    },
                    contentAlignment = Alignment.CenterStart,
                    label = "topBarTitle",
                    modifier = Modifier.weight(1f).padding(start = inset),
                ) { heading ->
                    Text(heading, style = DeskTheme.type.headline, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth())
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

private const val TOP_BAR_FADE_MS = 150
private const val TOP_BAR_FADE_OUT_MS = 90

/** Colour changes of navigation items (selection, hover, press) ease over [NAV_COLOR_MS]. */
private const val NAV_COLOR_MS = 150

@Composable
private fun animateNavColor(target: Color) =
    animateColorAsState(target, DeskMotion.fade(LocalMotionTier.current, tween(NAV_COLOR_MS)), label = "navColor")

/**
 * Large and extra-large: brand, application menus and an optional search/command field.
 *
 * Inside a [DesktopShell] in a desktop window the bar lies in the window's caption bar: as high as
 * the caption bar, clear of the system's app chip (which then names the app instead of [title] and
 * [brand]) and window buttons. The menus and the search field take their touches from the system;
 * the empty rest of the bar moves the window and a double click maximizes it, as on any title bar.
 */
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
    val caption = LocalCaptionArea.current
    val density = LocalDensity.current
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val start = 14.dp
    val end = 10.dp
    val height = if (caption == null) dimens.menuBarHeight else maxOf(dimens.menuBarHeight, with(density) { caption.height.toDp() })
    val left = with(density) { (caption?.left ?: 0).toDp() } + if (ltr) start else end
    val right = with(density) { (caption?.right ?: 0).toDp() } + if (ltr) end else start
    // the app chip at the caption bar's start already shows the app's icon and name
    val chip = caption != null && (if (ltr) caption.left else caption.right) > 0
    // in the caption bar, touches on these go to the app instead of moving the window
    val interactive = if (caption == null) Modifier else Modifier.systemGestureExclusion()
    Column(modifier.fillMaxWidth().background(colors.chrome)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = height).absolutePadding(left = left, right = right),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!chip) {
                if (brand != null) {
                    brand(Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(title, style = DeskTheme.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(14.dp))
            }
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Row(interactive, verticalAlignment = Alignment.CenterVertically, content = menus)
            }
            if (search != null) Box(interactive) { search() }
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
            // the field's edge, as on the kit's text fields (the light theme's bars are as white as it)
            .then(if (state.focused) Modifier else Modifier.border(1.dp, colors.borderStrong, shape))
            .deskClickable(interaction, onClick, onClickLabel = stringResource(R.string.dk_search))
            .padding(start = 10.dp, end = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(DeskIcons.Search, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(dimens.icon))
        Spacer(Modifier.width(8.dp))
        Text(hint ?: stringResource(R.string.dk_search_hint), style = DeskTheme.type.body, color = colors.textTertiary, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        // the key only where a keyboard or a mouse can press it (a tablet's sidebar layout may have neither)
        if (shortcut != null && showShortcutHints()) KeyCap(shortcut)
    }
}

/** Keyboard key cap, e.g. "Ctrl+K", in the monospace face. */
@Composable
fun KeyCap(text: String, modifier: Modifier = Modifier) {
    val colors = DeskTheme.colors
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier
            .background(colors.surfaceRaised, shape)
            // the light theme's raised surface is as white as the bars: the outline shows the key
            .border(1.dp, colors.border, shape)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(text, style = DeskTheme.type.mono, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * TalkBack reads an entry with a [badge] as its [label] and the badge ("Inbox, 4 unread"); the text
 * stays as it is for everything else.
 */
@Composable
private fun Modifier.badgeSemantics(label: String, badge: NavIconBadge?): Modifier {
    if (badge == null) return this
    val said = "$label, ${badgeDescription(badge)}"
    return semantics { contentDescription = said }
}

/** "More" is selected while the current screen is one of those it lists. */
private fun isSelected(item: NavItem, currentRoute: String?, shown: List<NavItem>): Boolean =
    if (item.route == ShellSpec.MORE_ROUTE) {
        currentRoute == ShellSpec.MORE_ROUTE || shown.none { it.route == currentRoute }
    } else {
        item.route == currentRoute
    }
