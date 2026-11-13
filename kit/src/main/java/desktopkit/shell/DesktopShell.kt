package desktopkit.shell

import androidx.activity.BackEventCompat
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.createGraph
import androidx.navigation.get
import desktopkit.LocalRootInsets
import desktopkit.components.ActionGroup
import desktopkit.components.ActionGroupList
import desktopkit.components.DeskButton
import desktopkit.components.ToastState
import desktopkit.components.ToastHost
import desktopkit.components.GlassBottomSheet
import desktopkit.components.MenuBarMenus
import desktopkit.components.MenuEntry
import desktopkit.components.MenuSpec
import desktopkit.input.hasHardwareKeyboard
import desktopkit.screens.OverflowSections
import desktopkit.theme.DeskGlass
import desktopkit.theme.DeskMotion
import desktopkit.theme.DeskTheme
import desktopkit.theme.GlassHolds
import desktopkit.theme.GlassMode
import desktopkit.theme.LocalGlassHolds
import desktopkit.theme.effectiveGlassMode
import desktopkit.theme.GlassThickness
import desktopkit.theme.LocalDeskDimens
import desktopkit.theme.LocalDeskType
import desktopkit.theme.LocalGlassMode
import desktopkit.theme.LocalGlassSource
import desktopkit.theme.LocalMotionTier
import desktopkit.theme.MotionTier
import desktopkit.theme.glass
import desktopkit.theme.glassSource
import desktopkit.theme.rememberReducedMotion
import desktopkit.theme.rememberSystemGlassMode
import dev.chrisbanes.haze.HazeState
import desktopkit.theme.forCompactWindow
import desktopkit.theme.grow
import desktopkit.theme.toMaterialTypography
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.math.roundToInt

/** Navigation chrome around the current screen; screens show their own title only in [NavKind.Sidebar]. */
val LocalNavKind = compositionLocalOf { NavKind.Sidebar }

/**
 * How the shell animates switching screens.
 *
 * - [Default]: Android's fade-through between top-level screens on phones and tablets (bottom bar,
 *   rail): the old screen fades out in 90 ms, then the new one fades in over 210 ms while growing
 *   from 96 % (phone) or 98 % (tablet) to full size on the emphasized curve; the two never overlap.
 *   In the sidebar layout (large windows, DeX) a plain 150 ms cross-fade without movement, as on a
 *   desktop. Going back looks the same and, on Android 14 and later with
 *   `android:enableOnBackInvokedCallback="true"`, follows the predictive Back gesture: the
 *   transition plays along with the finger and can be cancelled.
 * - [None]: no animation, e.g. for screenshot tests.
 *
 * Durations follow the system's animation scale (Developer options, "Remove animations").
 */
enum class ShellTransitions { Default, None }

/** Navigation entry point handed to screens. */
@Stable
class ShellNavigator(private val navController: NavHostController) {

    /** Switches top-level screen, keeping each screen's own back stack and state. */
    fun navigate(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    /**
     * Opens [route] over the screen shown, as a page of it: Back comes back to that screen, and its tab's entry
     * stays marked when [route]'s [NavItem.parent] is that tab (a folder opened from the "Folders" screen on a
     * phone). The tab's entry tapped again goes back to the tab's own screen.
     */
    fun open(route: String) {
        navController.navigate(route) { launchSingleTop = true }
    }

    fun back(): Boolean = navController.popBackStack()

    /** Back to [route] when it is under the screen shown, in the same tab; false when it is not there. */
    internal fun backTo(route: String): Boolean = navController.popBackStack(route, inclusive = false)
}

/**
 * The adaptive shell: a bottom bar on compact windows, a rail on medium and expanded windows,
 * a menu bar with a collapsible sidebar on large and extra-large windows (DeX, external
 * monitors). The navigation graph lives in one slot of [ShellLayout], so resizing a window
 * across width classes keeps back stacks, scroll positions and text fields.
 *
 * On a phone the content fills the window and the bars lie over it, drawn as frosted glass
 * ([desktopkit.theme.DeskGlass]); a screen that lays its lists out under them asks for
 * [rememberShellContentPadding] and pads the list's ends with it. Screens that do not ask are
 * kept clear of the bars as in 0.2.0. The content area is the glass source of the bars, sheets
 * and menus ([desktopkit.theme.LocalGlassSource]).
 *
 * The bottom navigation bar (compact windows) is hidden while the on-screen keyboard is open, so
 * it does not ride up on top of the keyboard; the content then reaches down to the keyboard. The
 * content ends above the keyboard because [desktopkit.DesktopKitRoot] pads the whole app with
 * `WindowInsets.safeDrawing` (IME included); a shell used without it needs the same padding around it.
 * While the on-screen keyboard is up, a finger scrolling the content or tapping outside the text
 * fields takes the focus from the field, and the keyboard closes; a control that should leave it
 * open (a Send button beside a message field) or an app's own text field carries [keepsKeyboardOpen].
 * With a hardware keyboard typing, the field keeps the focus.
 *
 * On compact windows the chrome follows the content's scrolling (see [hideBarsOnScroll]); tablet and
 * DeX windows keep their chrome in place. Text is [largeWindowTextStep] sp larger on rail and sidebar
 * layouts (tablet, DeX); phones get slightly tighter sizes ([compactSizes]) so more fits on screen.
 * Motion follows the window ([desktopkit.theme.MotionTier]): springs on a phone, the 0.2.0 timings
 * from tablet width up, none with reduced motion.
 *
 * @param badges text next to the sidebar's entries ("4 812", "3 new"), by route.
 * @param iconBadges a count or a dot on the entries' icons in the bottom bar, the rail and the collapsed
 *   sidebar ([NavIconBadge]): unread mail on "Inbox" where there is no room for text.
 * @param bottomBar optional strip under the content (a player, a status bar); it receives the current
 *   [NavKind]. On a phone an [desktopkit.components.ExpandablePlayer] fits here.
 * @param hideNavigationWithKeyboard false keeps the bottom navigation bar above the open keyboard.
 * @param transitions how switching screens is animated; see [ShellTransitions].
 * @param hideBarsOnScroll on phones, scrolling the content down slides the bottom navigation bar and
 *   the [bottomBar] strip away and scrolling up brings them back; false keeps them in place.
 * @param largeWindowTextStep sp added to every text style on medium and wider windows (tablet, DeX),
 *   on top of what the theme's `type` hook built; 0 keeps the theme's sizes everywhere.
 * @param compactSizes on phones, a 64 dp bottom bar and 52 dp touch rows instead of 72 and 56 dp;
 *   targets stay at least 48 dp. False keeps the touch sizes as they are.
 * @param menus menu bar menus, shown in the sidebar layout; [desktopkit.input.ShortcutRegistry.menus] builds them.
 *   In a desktop window (DeX, Android 15+ desktop windowing) the menu bar fills the window's caption bar
 *   inside a [desktopkit.DesktopKitRoot]: the menus and the search field sit between the system's app chip
 *   and window buttons, and the rest of the bar still moves the window.
 * @param onSearch shows a search field (sidebar layout) or a search button (smaller windows) when set.
 * @param moreActions the app's own entries in the phone's "⋯" sheet ([MoreStyle.Sheet]), after the
 *   screens that do not fit the bottom bar.
 * @param floatingPlayer on tablet and DeX, the [bottomBar] floats over the content's bottom edge as a
 *   rounded glass bar (the content scrolls beneath it) instead of being stacked under the content.
 * @param glass false draws every bar, sheet and menu as a solid surface.
 * @param toasts short messages ([desktopkit.components.ToastState.show]) shown above the bottom bar
 *   and the player on a phone, above the player on tablet and DeX.
 * @param overlays drawn over the whole shell, bars included, with the shell's glass: the place for the
 *   [desktopkit.components.CommandPalette].
 * @param navDrop lets the sidebar's and the rail's entries take drops ([desktopkit.components.dragSource]).
 * @param navMenu the menu of an entry of the sidebar, the rail or the bottom bar, by route ("Mark all as read",
 *   "Empty the bin"): a right click, a long press or the Menu key opens it, TalkBack offers it as an action. An
 *   empty list: no menu for that entry.
 * @param content the screen for a route of [spec]. The "More" route is handled by the shell. On
 *   compact windows a screen can change the top bar with [CompactTopBar]; [LocalWidthClass] and
 *   [currentWidthClass] tell it the width class in use.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesktopShell(
    spec: ShellSpec,
    widthClass: WidthClass,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    sidebarCollapsed: Boolean = false,
    onSidebarCollapsedChange: (Boolean) -> Unit = {},
    badges: NavBadges = emptyMap(),
    iconBadges: NavIconBadges = emptyMap(),
    brand: (@Composable (Modifier) -> Unit)? = null,
    menus: List<MenuSpec> = emptyList(),
    onSearch: (() -> Unit)? = null,
    searchHint: String? = null,
    searchShortcut: String? = "Ctrl+K",
    bottomBar: @Composable (NavKind) -> Unit = {},
    hideNavigationWithKeyboard: Boolean = true,
    transitions: ShellTransitions = ShellTransitions.Default,
    hideBarsOnScroll: Boolean = true,
    largeWindowTextStep: Float = 1f,
    compactSizes: Boolean = true,
    moreActions: List<ActionGroup> = emptyList(),
    floatingPlayer: Boolean = false,
    glass: Boolean = true,
    toasts: ToastState? = null,
    overlays: @Composable () -> Unit = {},
    navDrop: NavDrop? = null,
    navMenu: ((route: String) -> List<MenuEntry>)? = null,
    content: @Composable (route: String, navigator: ShellNavigator) -> Unit,
) {
    // tablet and DeX read one size larger; phones get the tighter compact sizes
    val navKindForTheme = NavKind.forWidth(widthClass)
    val baseType = DeskTheme.type
    val baseDimens = DeskTheme.dimens
    val windowType = remember(baseType, navKindForTheme, largeWindowTextStep) {
        if (navKindForTheme == NavKind.BottomBar) baseType else baseType.grow(largeWindowTextStep)
    }
    val windowDimens = remember(baseDimens, navKindForTheme, compactSizes) {
        if (compactSizes && navKindForTheme == NavKind.BottomBar) baseDimens.forCompactWindow() else baseDimens
    }
    val reduced = rememberReducedMotion()
    val tier = DeskMotion.tier(widthClass, reduced)
    val systemGlass = rememberSystemGlassMode()
    val glassMode = if (!glass) GlassMode.Solid else LocalGlassMode.current ?: systemGlass
    CompositionLocalProvider(
        LocalDeskType provides windowType,
        LocalDeskDimens provides windowDimens,
        LocalMotionTier provides tier,
        LocalGlassMode provides glassMode,
    ) {
        MaterialTheme(colorScheme = MaterialTheme.colorScheme, typography = windowType.toMaterialTypography()) {
            ShellContent(
                spec = spec,
                widthClass = widthClass,
                modifier = modifier,
                navController = navController,
                sidebarCollapsed = sidebarCollapsed,
                onSidebarCollapsedChange = onSidebarCollapsedChange,
                badges = badges,
                iconBadges = iconBadges,
                brand = brand,
                menus = menus,
                onSearch = onSearch,
                searchHint = searchHint,
                searchShortcut = searchShortcut,
                bottomBar = bottomBar,
                hideNavigationWithKeyboard = hideNavigationWithKeyboard,
                transitions = transitions,
                hideBarsOnScroll = hideBarsOnScroll,
                moreActions = moreActions,
                floatingPlayer = floatingPlayer,
                tier = tier,
                glassMode = glassMode,
                toasts = toasts,
                overlays = overlays,
                navDrop = navDrop,
                navMenu = navMenu,
                content = content,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShellContent(
    spec: ShellSpec,
    widthClass: WidthClass,
    modifier: Modifier,
    navController: NavHostController,
    sidebarCollapsed: Boolean,
    onSidebarCollapsedChange: (Boolean) -> Unit,
    badges: NavBadges,
    iconBadges: NavIconBadges,
    brand: (@Composable (Modifier) -> Unit)?,
    menus: List<MenuSpec>,
    onSearch: (() -> Unit)?,
    searchHint: String?,
    searchShortcut: String?,
    bottomBar: @Composable (NavKind) -> Unit,
    hideNavigationWithKeyboard: Boolean,
    transitions: ShellTransitions,
    hideBarsOnScroll: Boolean,
    moreActions: List<ActionGroup>,
    floatingPlayer: Boolean,
    tier: MotionTier,
    glassMode: GlassMode,
    toasts: ToastState?,
    overlays: @Composable () -> Unit,
    navDrop: NavDrop?,
    navMenu: ((route: String) -> List<MenuEntry>)?,
    content: @Composable (route: String, navigator: ShellNavigator) -> Unit,
) {
    val navigator = remember(navController) { ShellNavigator(navController) }
    val entry by navController.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route ?: spec.startRoute
    // read again whenever the current entry changes
    val routeBelow = remember(entry) { navController.previousBackStackEntry?.destination?.route }
    val openedOverParent = spec.isUnder(currentRoute, routeBelow)
    val pageBack: (() -> Unit)? = if (openedOverParent) ({ navigator.back() }) else null
    val navKind = NavKind.forWidth(widthClass)
    val more = moreItem()
    val colors = DeskTheme.colors
    val density = LocalDensity.current
    // Not laid out at all while the keyboard is up; the root's safeDrawing padding already ends the
    // content above it. isImeVisible alone is not enough: it reads true until the first insets arrive
    // (and forever when the window never passes insets to Compose), so the keyboard must also take space.
    val imeVisible = WindowInsets.isImeVisible
    val ime = WindowInsets.ime
    val imeTakesSpace by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
    val showBottomNav = !(hideNavigationWithKeyboard && imeVisible && imeTakesSpace)
    // a finger closes the on-screen keyboard only while it is there (and no keyboard cover is typing)
    val keyboardUp by rememberUpdatedState(imeVisible && imeTakesSpace && !hasHardwareKeyboard())
    // The graph builder below must not capture changing values, or NavHost would rebuild the graph.
    val currentContent by rememberUpdatedState(content)
    val currentSpec by rememberUpdatedState(spec)
    val currentKind by rememberUpdatedState(navKind)
    val currentTransitions by rememberUpdatedState(transitions)
    val currentTier by rememberUpdatedState(tier)
    // read by each tab's page: a tab that is not the current route is being left (see LocalScreenLeaving)
    val latestRoute by rememberUpdatedState(currentRoute)
    val leavingTab: (String) -> Boolean = remember { { route -> latestRoute != route } }
    // read when a transition starts, so a new width class or setting applies to the next one
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
        remember { { shellEnter(currentTransitions, currentKind, currentTier) } }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
        remember { { shellExit(currentTransitions, currentKind, currentTier) } }
    // the predictive Back gesture between tabs (gesture navigation): the phone's own, wider up Navigation's as before;
    // the Back button, which Android 16 also starts a predictive Back for, keeps the fade-through everywhere
    val predictiveEnter: AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> EnterTransition =
        remember { { edge -> shellPredictiveEnter(currentTransitions, currentKind, currentTier, edge) ?: NavigationPredictiveEnter } }
    val predictiveExit: AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> ExitTransition =
        remember { { edge -> shellPredictiveExit(currentTransitions, currentKind, currentTier, edge) ?: NavigationPredictiveExit } }
    val topBarHost = remember { CompactTopBarHost() }
    val primaryHost = remember { PrimaryActionHost() }
    val primary = primaryHost.current
    val scroll = remember { ShellScrollState() }
    val metrics = remember { ShellChromeMetrics() }
    val overlayHost = remember { ShellOverlayHost() }
    val glassSource = remember { HazeState() }
    val glassHolds = remember { GlassHolds() }
    val padding = remember(metrics, density) { shellPaddingValues(metrics, density) }
    // One graph for the shell's life (a new start route aside): routes the spec gains are added to it and routes it
    // loses are taken out, so a new folder or account keeps the open screen, the back stacks and their state
    // (DeskMail's request 3). A new graph would send the shell back to its start and forget every screen's state.
    val tabPage = remember<@Composable AnimatedContentScope.(String) -> Unit> {
        { route ->
            TabPage(transition, leaving = { leavingTab(route) }, route = route) {
                ShellScreenFrame(metrics, scroll) {
                    if (route == ShellSpec.MORE_ROUTE) {
                        OverflowSections(moreSections(currentSpec, currentKind), onSelect = navigator::navigate)
                    } else {
                        currentContent(route, navigator)
                    }
                }
            }
        }
    }
    val graph = remember(navController, spec.startRoute) {
        navController.createGraph(startDestination = spec.startRoute) {
            spec.items.forEach { item -> composable(item.route) { tabPage(item.route) } }
            composable(ShellSpec.MORE_ROUTE) { tabPage(ShellSpec.MORE_ROUTE) }
        }
    }
    val routes = spec.items.map { it.route }
    // added here, in the composition, so a screen the app opens at once with the new spec is there already
    remember(graph, routes) {
        val composeNavigator = navController.navigatorProvider[ComposeNavigator::class]
        routes.filter { graph.findNode(it) == null }.forEach { route ->
            graph.addDestination(ComposeNavigator.Destination(composeNavigator) { _ -> tabPage(route) }.apply { this.route = route })
        }
        routes
    }
    LaunchedEffect(graph, routes) {
        val kept = routes.toSet() + ShellSpec.MORE_ROUTE + spec.startRoute
        val stale = graph.filter { it.route !in kept }
        if (stale.isEmpty()) return@LaunchedEffect
        val staleRoutes = stale.mapNotNull { it.route }.toSet()
        // a screen that is gone: its tab goes to the start screen first
        if (navController.currentBackStack.value.any { it.destination.route in staleRoutes }) navigator.navigate(spec.startRoute)
        stale.forEach { node ->
            node.route?.let { navController.clearBackStack(it) }
            graph.remove(node)
        }
    }
    // the root leaves the system navigation bar at the bottom to the shell, which draws its chrome behind it
    val rootInsets = LocalRootInsets.current
    DisposableEffect(rootInsets) {
        rootInsets?.let { it.shells++ }
        onDispose { rootInsets?.let { it.shells-- } }
    }
    val systemBottom: () -> Int = remember(rootInsets, density) { { rootInsets?.navigationBottom?.getBottom(density) ?: 0 } }
    // DeX and desktop windows: the menu bar fills the window's caption bar, between the system's elements
    val claimsCaption = navKind == NavKind.Sidebar
    DisposableEffect(rootInsets, claimsCaption) {
        if (claimsCaption) rootInsets?.let { it.captionClaims++ }
        onDispose { if (claimsCaption) rootInsets?.let { it.captionClaims-- } }
    }
    val captionArea = if (claimsCaption && rootInsets?.captionInUse(density) == true) rootInsets.caption else null
    val floating = floatingPlayer && navKind != NavKind.BottomBar
    val floatingMargin = 12.dp
    var moreOpen by remember { mutableStateOf(false) }
    SideEffect {
        scroll.enabled = navKind == NavKind.BottomBar
        scroll.hideOnScroll = hideBarsOnScroll && showBottomNav
        scroll.tier = tier
        metrics.underTop = navKind == NavKind.BottomBar
        metrics.underBottom = navKind == NavKind.BottomBar
        metrics.underFloatingPlayer = floating
        metrics.floatingMarginPx = with(density) { floatingMargin.roundToPx() }
        if (!showBottomNav || navKind != NavKind.BottomBar) metrics.navPx = 0
        metrics.systemBottomPx = systemBottom
        overlayHost.systemBottomPx = systemBottom
    }
    // every screen starts with the bars shown and a flat top bar
    LaunchedEffect(currentRoute, navKind, showBottomNav) { scroll.reset() }
    LaunchedEffect(navKind) { if (navKind != NavKind.BottomBar) moreOpen = false }

    val searchSlot: (@Composable () -> Unit)? = if (onSearch == null) null else {
        {
            SearchField(onClick = onSearch, shortcut = searchShortcut, hint = searchHint,
                modifier = Modifier.widthIn(min = 220.dp, max = 380.dp))
        }
    }
    val reselect = remember { ShellReselect() }
    // the current screen's entry again: the screen hears it (OnReselect) and goes to its top
    // the marked entry while a page opened in its tab is shown (ShellNavigator.open): back to the tab's own screen
    val marked = spec.marked(navKind, currentRoute)
    val go: (String) -> Unit = { route ->
        when {
            route == currentRoute -> reselect.events.tryEmit(route)
            route == marked && navigator.backTo(route) -> Unit
            else -> navigator.navigate(route)
        }
    }
    val select: (String) -> Unit = { route ->
        if (route == ShellSpec.MORE_ROUTE && spec.moreStyle == MoreStyle.Sheet && navKind == NavKind.BottomBar) {
            moreOpen = true
        } else {
            go(route)
        }
    }

    // on a phone one glass lies behind the player strip, the bottom bar and the system band
    val bottomGlass = remember { ShellBottomGlass { Modifier.glass(DeskGlass.chrome(DeskTheme.colors, GlassThickness.Regular)) } }
    val sharedGlass = if (navKind == NavKind.BottomBar) bottomGlass else null

    CompositionLocalProvider(
        LocalWidthClass provides widthClass,
        LocalCompactTopBarHost provides topBarHost,
        LocalGlassSource provides glassSource,
        LocalGlassHolds provides glassHolds,
        LocalShellOverlayHost provides overlayHost,
        LocalShellContentPadding provides padding,
        LocalShellBottomGlass provides sharedGlass,
        LocalShellReselect provides reselect,
        LocalPrimaryActionHost provides primaryHost,
    ) {
        ShellLayout(
            navKind = navKind,
            modifier = modifier.fillMaxSize().background(colors.background),
            topBar = {
                if (navKind == NavKind.Sidebar) {
                    CompositionLocalProvider(LocalCaptionArea provides captionArea) {
                        MenuBarChrome(
                            title = spec.appTitle,
                            brand = brand,
                            menus = { MenuBarMenus(menus) },
                            search = searchSlot,
                        )
                    }
                } else {
                    val title = if (currentRoute == ShellSpec.MORE_ROUTE) more.label else spec.item(currentRoute)?.label ?: spec.appTitle
                    // CompactTopBar registers nothing in the sidebar layout, and on the rail only with showOnRail (checked
                    // here as well: on the frame a window widens, the phone's calls are still there)
                    val screen = topBarHost.current?.takeIf { navKind == NavKind.BottomBar || it.showOnRail }
                    PageTopBar(
                        title = screen?.title ?: title,
                        onSearch = if (screen?.showSearch == false) null else onSearch,
                        // a page opened over its parent (ShellNavigator.open) has a Back button of its own
                        onBack = screen?.onBack ?: pageBack,
                        backIcon = screen?.backIcon ?: TopBarBackIcon.Back,
                        actions = screen?.actions.orEmpty(),
                        scrolled = navKind == NavKind.BottomBar && scroll.scrolled,
                        glass = navKind == NavKind.BottomBar,
                        modifier = Modifier.onSizeChanged { metrics.topPx = it.height },
                    )
                }
            },
            navigation = {
                when (navKind) {
                    NavKind.BottomBar -> if (showBottomNav) {
                        BottomNavBar(
                            spec, currentRoute, select,
                            hasMoreActions = moreActions.isNotEmpty(),
                            iconBadges = iconBadges,
                            navMenu = navMenu,
                            modifier = Modifier
                                .onSizeChanged {
                                    scroll.maxOffsetPx = it.height.toFloat()
                                    metrics.navPx = it.height
                                }
                                // an expanding player pushes the bar out of the way
                                .graphicsLayer { translationY = overlayHost.expandProgress() * size.height },
                        )
                    }
                    NavKind.Rail -> NavRail(
                        spec, currentRoute, go, brand = brand, navDrop = navDrop, iconBadges = iconBadges, navMenu = navMenu,
                        action = primary?.let { action -> { PrimaryActionButton(action, extended = false, height = 48.dp) } },
                    )
                    NavKind.Sidebar -> Sidebar(
                        spec = spec,
                        currentRoute = currentRoute,
                        onSelect = go,
                        collapsed = sidebarCollapsed,
                        onToggleCollapsed = { onSidebarCollapsedChange(!sidebarCollapsed) },
                        badges = badges,
                        navDrop = navDrop,
                        iconBadges = iconBadges,
                        navMenu = navMenu,
                        action = primary?.let { action ->
                            { collapsed ->
                                if (collapsed) {
                                    PrimaryActionButton(action, extended = false, height = 40.dp)
                                } else {
                                    DeskButton(action.label, action.onClick, Modifier.fillMaxWidth(), icon = action.icon, primary = true)
                                }
                            }
                        },
                    )
                }
            },
            player = {
                val shape = RoundedCornerShape(DeskTheme.dimens.radiusCard.coerceAtLeast(12.dp))
                Box(
                    Modifier
                        .onSizeChanged { metrics.playerPx = it.height }
                        .then(
                            if (!floating) Modifier else Modifier
                                .glass(DeskGlass.raised(colors, GlassThickness.Thin), shape)
                                .border(1.dp, colors.borderStrong, shape),
                        ),
                ) { bottomBar(navKind) }
            },
            content = {
                CompositionLocalProvider(LocalNavKind provides navKind) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            // not recorded while a screen pauses the blur (PauseGlassBlur: a WebView, a video)
                            .glassSource(glassSource, effectiveGlassMode(glassMode, paused = glassHolds.count > 0))
                            .background(colors.background)
                            .nestedScroll(scroll.connection)
                            .dismissKeyboardOnTouch(overlayHost.textFields, keyboardUp = { keyboardUp }),
                    ) {
                        NavHost(
                            navController = navController,
                            graph = graph,
                            enterTransition = enter,
                            exitTransition = exit,
                            popEnterTransition = enter,
                            popExitTransition = exit,
                            predictivePopEnterTransition = predictiveEnter,
                            predictivePopExitTransition = predictiveExit,
                        )
                    }
                }
            },
            overlay = {
                Box(Modifier.fillMaxSize().onGloballyPositioned { overlayHost.coordinates = it }) {
                    // the phone's floating action button (ShellPrimaryAction), above the bars and the player and with them
                    if (navKind == NavKind.BottomBar) {
                        val barsBottom: () -> Int = {
                            metrics.navPx + metrics.playerPx + systemBottom() - scroll.offsetPx.roundToInt().coerceIn(0, metrics.navPx)
                        }
                        PhoneActionButton(
                            action = primary,
                            shown = !(imeVisible && imeTakesSpace),
                            extended = !scroll.scrolled,
                            metrics = metrics,
                            expandProgress = { overlayHost.expandProgress() },
                            modifier = Modifier.align(Alignment.BottomEnd).bottomInsetPadding(barsBottom),
                        )
                    } else {
                        SideEffect { metrics.actionPx = 0 }
                    }
                    // toasts sit above the bars, the player and the action button, under sheets and the expanded player
                    if (toasts != null) {
                        val toastBottom: () -> Int = {
                            val system = systemBottom()
                            when {
                                navKind == NavKind.BottomBar ->
                                    metrics.navPx + metrics.playerPx + metrics.actionPx + system - scroll.offsetPx.roundToInt().coerceIn(0, metrics.navPx)
                                floating -> metrics.playerPx + metrics.floatingMarginPx + system
                                else -> metrics.playerPx + system
                            }
                        }
                        ToastHost(toasts, Modifier.align(Alignment.BottomCenter).bottomInsetPadding(toastBottom))
                    }
                    overlayHost.layers.forEach { layer -> key(layer) { layer.content() } }
                }
            },
            // while the keyboard is up the bar is gone and the player sits right on the keyboard: bars
            // hidden by an earlier scroll must not push it down behind the keyboard
            navigationOffset = { if (navKind == NavKind.BottomBar && showBottomNav) scroll.offsetPx.roundToInt() else 0 },
            floatingPlayer = floating,
            floatingMargin = floatingMargin,
            systemBottom = systemBottom,
            systemBand = { SystemBand(navKind, scroll) },
            floatingMaxWidth = 760.dp,
            bottomGlass = sharedGlass,
        )

        if (spec.moreStyle == MoreStyle.Sheet) {
            GlassBottomSheet(visible = moreOpen, onDismiss = { moreOpen = false }, title = more.label) {
                ActionGroupList(moreSheetGroups(spec, navKind, navigator) + moreActions, onDismiss = { moreOpen = false })
            }
        }
        CompositionLocalProvider(LocalNavKind provides navKind) { overlays() }
    }
}

/** How far the corners of a tab round on its way out with the Back gesture: the detail's. */
private val TAB_CORNER = 28.dp

/**
 * A tab's page on the phone, on its own page-coloured surface while a transition is on. The screens
 * used to lie on the shell's background: in the predictive Back gesture the tab being left shrank in
 * front of the start tab, and the start tab showed through its text. On the surface the tab is whole,
 * and its corners round with the gesture to [TAB_CORNER], as the detail's do. The rounding is read off
 * the scale the gesture's exit animates on this content ([shellPredictiveExit], toward 90 %), found by
 * the label Compose gives AnimatedVisibility's built-in scale ("Built-in scale", an internal name: were
 * it to change, the corners would stay square and nothing else would be lost); a fade-through, which
 * scales nothing on its way out, keeps the corners square. At rest, and wider up, there is no surface
 * and the content is as it was (the surface is the shell's colour, so the transitions look the same
 * where it is not needed). The content sits in the same box on every width, so a window resized across
 * width classes keeps the screen's state.
 */
@Composable
private fun TabPage(transition: Transition<EnterExitState>, leaving: () -> Boolean, route: String, content: @Composable () -> Unit) {
    // composed again on the frame the transition starts, which is the frame the other tab is composed
    val moving = LocalNavKind.current == NavKind.BottomBar && transition.currentState != transition.targetState
    val page = DeskTheme.colors.background
    val surface = if (!moving) Modifier else Modifier
        .graphicsLayer {
            val scale = if (transition.targetState == EnterExitState.PostExit) {
                transition.animations.firstOrNull { it.label.endsWith(" scale") }?.value as? Float
            } else {
                null
            }
            val p = if (scale == null) 0f else ((1f - scale) / 0.1f).coerceIn(0f, 1f)
            if (p > 0f) {
                shape = RoundedCornerShape(TAB_CORNER * p)
                clip = true
            }
        }
        .background(page)
    Box(Modifier.fillMaxSize().then(surface)) {
        CompositionLocalProvider(LocalTabRoute provides route) { ProvideScreenLeaving(leaving, content) }
    }
}

/** Taps on the current screen's own entry (bottom bar, rail, sidebar), by route. */
internal class ShellReselect {
    val events = MutableSharedFlow<String>(extraBufferCapacity = 1)
}

internal val LocalShellReselect = staticCompositionLocalOf<ShellReselect?> { null }

/** The route of the tab a screen belongs to. */
internal val LocalTabRoute = staticCompositionLocalOf<String?> { null }

/**
 * Runs [action] when the user taps the entry of the screen already shown (in the bottom bar, the
 * rail or the sidebar), as Android's own apps do: scroll a list back to its top, or go back to the
 * screen's start from a page opened in it.
 *
 * ```
 * val list = rememberLazyListState()
 * OnReselect { list.animateScrollToItem(0) }
 * ```
 *
 * Call it in the screen; it only hears its own tab's entry. Outside a [DesktopShell] nothing happens.
 */
@Composable
fun OnReselect(action: suspend () -> Unit) {
    val reselect = LocalShellReselect.current ?: return
    val route = LocalTabRoute.current ?: return
    val latest by rememberUpdatedState(action)
    LaunchedEffect(reselect, route) { reselect.events.collect { if (it == route) latest() } }
}

/**
 * What lies behind the system navigation bar (three buttons or the gesture handle): on a phone the
 * glass of the bottom bar, which stays when the bars slide away beneath it and then shows a rule along
 * its top; on tablets the chrome colour, continuing the rail or sidebar.
 */
@Composable
private fun SystemBand(navKind: NavKind, scroll: ShellScrollState) {
    val colors = DeskTheme.colors
    if (navKind == NavKind.BottomBar) {
        val rule = colors.border
        Box(
            Modifier
                .fillMaxSize()
                // the shell's one bottom glass lies behind the band; only without it the band has its own
                .glass(DeskGlass.chrome(colors, GlassThickness.Regular), enabled = LocalShellBottomGlass.current == null)
                .drawBehind {
                    val hidden = if (scroll.maxOffsetPx > 0f) (scroll.offsetPx / scroll.maxOffsetPx).coerceIn(0f, 1f) else 0f
                    if (hidden > 0f) drawRect(rule.copy(alpha = rule.alpha * hidden), size = Size(size.width, 1.dp.toPx()))
                },
        )
    } else {
        Box(Modifier.fillMaxSize().background(colors.chrome))
    }
}

/** The screens the chrome of [kind] leaves to "More", by sidebar section (the footer last, untitled). */
internal fun moreSections(spec: ShellSpec, kind: NavKind): List<NavSection> {
    val left = spec.overflow(kind).map { it.route }.toSet()
    return (spec.sections + NavSection(null, listOfNotNull(spec.footer)))
        .map { section -> NavSection(section.title, section.items.filter { it.route in left }) }
        .filter { it.items.isNotEmpty() }
}

/** The screens the phone's bottom bar leaves out, by sidebar section, as entries of the "⋯" sheet. */
internal fun moreSheetGroups(spec: ShellSpec, kind: NavKind, navigator: ShellNavigator): List<ActionGroup> =
    moreSections(spec, kind).map { section ->
        ActionGroup(section.title, section.items.map { item -> MenuEntry.Item(item.label, { navigator.navigate(item.route) }, icon = item.icon) })
    }

private const val FADE_OUT_MS = 90
private const val FADE_IN_MS = 210
private const val CROSSFADE_MS = 150

/**
 * Fade-through (Material motion) between top-level screens; desktop cross-fade in the sidebar layout.
 * The growth starts with the fade-in, on the phone too: on the gesture spring it used to start 90 ms
 * earlier, while the screen was still invisible, and was mostly over by the time it could be seen.
 */
internal fun shellEnter(transitions: ShellTransitions, kind: NavKind, tier: MotionTier = MotionTier.Timed): EnterTransition = when {
    transitions == ShellTransitions.None || tier == MotionTier.Reduced -> EnterTransition.None
    kind == NavKind.Sidebar -> fadeIn(tween(CROSSFADE_MS, easing = DeskMotion.Standard))
    else -> {
        val scale = if (kind == NavKind.BottomBar) 0.96f else 0.98f
        fadeIn(tween(FADE_IN_MS, delayMillis = FADE_OUT_MS, easing = DeskMotion.EmphasizedDecelerate)) +
            scaleIn(tween(FADE_IN_MS, delayMillis = FADE_OUT_MS, easing = DeskMotion.EmphasizedDecelerate), initialScale = scale)
    }
}

/** Navigation's own predictive pop (its defaults), which wider windows keep. */
private val NavigationPredictiveEnter: EnterTransition = fadeIn(spring(dampingRatio = 1f, stiffness = 1600f))
private val NavigationPredictiveExit: ExitTransition = scaleOut(targetScale = 0.7f)

/** How long the predictive Back gesture between tabs is seeked over on the phone (the finger's part and the way out). */
private const val PREDICTIVE_MS = 300

/**
 * The phone's predictive Back gesture between tabs, as the detail's: the screen being left shrinks
 * toward 90 % and leans away from the edge the swipe came from, with the finger, staying whole until
 * the last tenth of the way; the screen it goes back to rises from 94 % and brightens behind it.
 * Navigation seeks this with the finger, plays the rest once let go and runs it back on a cancel.
 * Null (Navigation's own) wider up, as before; with reduced motion nothing moves.
 *
 * The Back button is not a swipe: since Android 16 a press of the three-button Back (and of a hardware
 * Back key) starts a predictive Back too, with [BackEventCompat.EDGE_NONE] for its edge, and Navigation
 * takes it for a gesture, choosing these transitions and playing them through on the release. The button
 * keeps the fade-through of a Back without the gesture, on every width (wider up Navigation's own predictive
 * pop used to play on the button as well).
 */
internal fun shellPredictiveEnter(transitions: ShellTransitions, kind: NavKind, tier: MotionTier, swipeEdge: Int): EnterTransition? = when {
    swipeEdge == BackEventCompat.EDGE_NONE -> shellEnter(transitions, kind, tier)
    transitions == ShellTransitions.None || tier == MotionTier.Reduced -> EnterTransition.None
    tier != MotionTier.Gesture || kind != NavKind.BottomBar -> null
    else -> scaleIn(tween(PREDICTIVE_MS, easing = DeskMotion.Standard), initialScale = 0.94f) +
        fadeIn(tween(PREDICTIVE_MS, easing = DeskMotion.Standard))
}

internal fun shellPredictiveExit(transitions: ShellTransitions, kind: NavKind, tier: MotionTier, swipeEdge: Int): ExitTransition? = when {
    swipeEdge == BackEventCompat.EDGE_NONE -> shellExit(transitions, kind, tier)
    transitions == ShellTransitions.None || tier == MotionTier.Reduced -> ExitTransition.None
    tier != MotionTier.Gesture || kind != NavKind.BottomBar -> null
    else -> {
        val direction = if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1 else 1
        val linear = Easing { it }
        val wayOut = Easing { fraction -> ((fraction - 0.9f) / 0.1f).coerceIn(0f, 1f) }
        scaleOut(tween(PREDICTIVE_MS, easing = linear), targetScale = 0.9f) +
            slideOutHorizontally(tween(PREDICTIVE_MS, easing = linear)) { direction * it / 20 } +
            fadeOut(tween(PREDICTIVE_MS, easing = wayOut))
    }
}

internal fun shellExit(transitions: ShellTransitions, kind: NavKind, tier: MotionTier = MotionTier.Timed): ExitTransition = when {
    transitions == ShellTransitions.None || tier == MotionTier.Reduced -> ExitTransition.None
    kind == NavKind.Sidebar -> fadeOut(tween(CROSSFADE_MS, easing = DeskMotion.Standard))
    else -> fadeOut(tween(FADE_OUT_MS, easing = DeskMotion.EmphasizedAccelerate))
}
