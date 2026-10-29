package desktopkit.shell

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import desktopkit.LocalRootInsets
import desktopkit.components.ActionGroup
import desktopkit.components.ActionGroupList
import desktopkit.components.DeskToastState
import desktopkit.components.ToastHost
import desktopkit.components.GlassBottomSheet
import desktopkit.components.MenuBarMenus
import desktopkit.components.MenuEntry
import desktopkit.components.MenuSpec
import desktopkit.screens.OverflowScreen
import desktopkit.theme.DeskGlass
import desktopkit.theme.DeskMotion
import desktopkit.theme.DeskTheme
import desktopkit.theme.GlassMode
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
import kotlin.math.roundToInt

/** Navigation chrome around the current screen; screens show their own title only in [NavKind.Sidebar]. */
val LocalNavKind = compositionLocalOf { NavKind.Sidebar }

/**
 * How the shell animates switching screens.
 *
 * - [Default]: Android's fade-through between top-level screens on phones and tablets (bottom bar,
 *   rail): the old screen fades out in 90 ms, the new one fades in over 210 ms while growing from
 *   96 % (phone, on the gesture spring) or 98 % (tablet, on the emphasized curve) to full size. In the sidebar layout (large
 *   windows, DeX) a plain 150 ms cross-fade without movement, as on a desktop. Going back looks the
 *   same and, on Android 14 and later with `android:enableOnBackInvokedCallback="true"`, follows the
 *   predictive Back gesture: the transition plays along with the finger and can be cancelled.
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

    fun back(): Boolean = navController.popBackStack()
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
 *
 * On compact windows the chrome follows the content's scrolling (see [hideBarsOnScroll]); tablet and
 * DeX windows keep their chrome in place. Text is [largeWindowTextStep] sp larger on rail and sidebar
 * layouts (tablet, DeX); phones get slightly tighter sizes ([compactSizes]) so more fits on screen.
 * Motion follows the window ([desktopkit.theme.MotionTier]): springs on a phone, the 0.2.0 timings
 * from tablet width up, none with reduced motion.
 *
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
 * @param onSearch shows a search field (sidebar layout) or a search button (smaller windows) when set.
 * @param moreActions the app's own entries in the phone's "⋯" sheet ([MoreStyle.Sheet]), after the
 *   screens that do not fit the bottom bar.
 * @param floatingPlayer on tablet and DeX, the [bottomBar] floats over the content's bottom edge as a
 *   rounded glass bar (the content scrolls beneath it) instead of being stacked under the content.
 * @param glass false draws every bar, sheet and menu as a solid surface.
 * @param toasts short messages ([desktopkit.components.DeskToastState.show]) shown above the bottom bar
 *   and the player on a phone, above the player on tablet and DeX.
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
    toasts: DeskToastState? = null,
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
    toasts: DeskToastState?,
    content: @Composable (route: String, navigator: ShellNavigator) -> Unit,
) {
    val navigator = remember(navController) { ShellNavigator(navController) }
    val entry by navController.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route ?: spec.startRoute
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
    // The graph builder below must not capture changing values, or NavHost would rebuild the graph.
    val currentContent by rememberUpdatedState(content)
    val currentSpec by rememberUpdatedState(spec)
    val currentKind by rememberUpdatedState(navKind)
    val currentTransitions by rememberUpdatedState(transitions)
    val currentTier by rememberUpdatedState(tier)
    // read when a transition starts, so a new width class or setting applies to the next one
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
        remember { { shellEnter(currentTransitions, currentKind, currentTier) } }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
        remember { { shellExit(currentTransitions, currentKind, currentTier) } }
    val topBarHost = remember { CompactTopBarHost() }
    val scroll = remember { ShellScrollState() }
    val metrics = remember { ShellChromeMetrics() }
    val overlayHost = remember { ShellOverlayHost() }
    val glassSource = remember { HazeState() }
    val padding = remember(metrics, density) { shellPaddingValues(metrics, density) }
    // the root leaves the system navigation bar at the bottom to the shell, which draws its chrome behind it
    val rootInsets = LocalRootInsets.current
    DisposableEffect(rootInsets) {
        rootInsets?.let { it.shells++ }
        onDispose { rootInsets?.let { it.shells-- } }
    }
    val systemBottom: () -> Int = remember(rootInsets, density) { { rootInsets?.navigationBottom?.getBottom(density) ?: 0 } }
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
        overlayHost.onExpandStart = { scroll.showBars() }
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
    val select: (String) -> Unit = { route ->
        if (route == ShellSpec.MORE_ROUTE && spec.moreStyle == MoreStyle.Sheet && navKind == NavKind.BottomBar) {
            moreOpen = true
        } else {
            navigator.navigate(route)
        }
    }

    CompositionLocalProvider(
        LocalWidthClass provides widthClass,
        LocalCompactTopBarHost provides topBarHost,
        LocalGlassSource provides glassSource,
        LocalShellOverlayHost provides overlayHost,
        LocalShellContentPadding provides padding,
    ) {
        ShellLayout(
            navKind = navKind,
            modifier = modifier.fillMaxSize().background(colors.background),
            topBar = {
                if (navKind == NavKind.Sidebar) {
                    MenuBarChrome(
                        title = spec.appTitle,
                        brand = brand,
                        menus = { MenuBarMenus(menus) },
                        search = searchSlot,
                    )
                } else {
                    val title = if (currentRoute == ShellSpec.MORE_ROUTE) more.label else spec.item(currentRoute)?.label ?: spec.appTitle
                    // CompactTopBar registers nothing on wider windows; checked here as well
                    val screen = if (navKind == NavKind.BottomBar) topBarHost.current else null
                    PageTopBar(
                        title = screen?.title ?: title,
                        onSearch = if (screen?.showSearch == false) null else onSearch,
                        onBack = screen?.onBack,
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
                            modifier = Modifier
                                .onSizeChanged {
                                    scroll.maxOffsetPx = it.height.toFloat()
                                    metrics.navPx = it.height
                                }
                                // an expanding player pushes the bar out of the way
                                .graphicsLayer { translationY = overlayHost.expandProgress() * size.height },
                        )
                    }
                    NavKind.Rail -> NavRail(spec, currentRoute, navigator::navigate, brand = brand)
                    NavKind.Sidebar -> Sidebar(
                        spec = spec,
                        currentRoute = currentRoute,
                        onSelect = navigator::navigate,
                        collapsed = sidebarCollapsed,
                        onToggleCollapsed = { onSidebarCollapsedChange(!sidebarCollapsed) },
                        badges = badges,
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
                            .glassSource(glassSource, glassMode)
                            .background(colors.background)
                            .nestedScroll(scroll.connection),
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = spec.startRoute,
                            enterTransition = enter,
                            exitTransition = exit,
                            popEnterTransition = enter,
                            popExitTransition = exit,
                        ) {
                            spec.items.forEach { item ->
                                composable(item.route) {
                                    ShellScreenFrame(metrics, scroll) { currentContent(item.route, navigator) }
                                }
                            }
                            composable(ShellSpec.MORE_ROUTE) {
                                ShellScreenFrame(metrics, scroll) {
                                    OverflowScreen(currentSpec.overflow(currentKind), onSelect = navigator::navigate)
                                }
                            }
                        }
                    }
                }
            },
            overlay = {
                Box(Modifier.fillMaxSize().onGloballyPositioned { overlayHost.coordinates = it }) {
                    // toasts sit above the bars and the player, under sheets and the expanded player
                    if (toasts != null) {
                        val toastBottom: () -> Int = {
                            val system = systemBottom()
                            when {
                                navKind == NavKind.BottomBar ->
                                    metrics.navPx + metrics.playerPx + system - scroll.offsetPx.roundToInt().coerceIn(0, metrics.navPx)
                                floating -> metrics.playerPx + metrics.floatingMarginPx + system
                                else -> metrics.playerPx + system
                            }
                        }
                        ToastHost(toasts, Modifier.align(Alignment.BottomCenter).bottomInsetPadding(toastBottom))
                    }
                    overlayHost.layers.forEach { layer -> key(layer) { layer.content() } }
                }
            },
            navigationOffset = { if (navKind == NavKind.BottomBar) scroll.offsetPx.roundToInt() else 0 },
            floatingPlayer = floating,
            floatingMargin = floatingMargin,
            systemBottom = systemBottom,
            systemBand = { SystemBand(navKind, scroll) },
        )

        if (spec.moreStyle == MoreStyle.Sheet) {
            GlassBottomSheet(visible = moreOpen, onDismiss = { moreOpen = false }, title = more.label) {
                ActionGroupList(moreSheetGroups(spec, navKind, navigator) + moreActions, onDismiss = { moreOpen = false })
            }
        }
    }
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
                .glass(DeskGlass.chrome(colors, GlassThickness.Regular))
                .drawBehind {
                    val hidden = if (scroll.maxOffsetPx > 0f) (scroll.offsetPx / scroll.maxOffsetPx).coerceIn(0f, 1f) else 0f
                    if (hidden > 0f) drawRect(rule.copy(alpha = rule.alpha * hidden), size = Size(size.width, 1.dp.toPx()))
                },
        )
    } else {
        Box(Modifier.fillMaxSize().background(colors.chrome))
    }
}

/** The screens the phone's bottom bar leaves out, by sidebar section, as entries of the "⋯" sheet. */
internal fun moreSheetGroups(spec: ShellSpec, kind: NavKind, navigator: ShellNavigator): List<ActionGroup> {
    val left = spec.overflow(kind).map { it.route }.toSet()
    val sections = spec.sections.map { section -> section.title to section.items.filter { it.route in left } } +
        listOf(null to listOfNotNull(spec.footer).filter { it.route in left })
    return sections.filter { it.second.isNotEmpty() }.map { (title, items) ->
        ActionGroup(title, items.map { item -> MenuEntry.Item(item.label, { navigator.navigate(item.route) }, icon = item.icon) })
    }
}

private const val FADE_OUT_MS = 90
private const val FADE_IN_MS = 210
private const val CROSSFADE_MS = 150

/**
 * Fade-through (Material motion) between top-level screens; desktop cross-fade in the sidebar layout.
 * On a phone the scale-in is the gesture spring; the fades stay timed.
 */
internal fun shellEnter(transitions: ShellTransitions, kind: NavKind, tier: MotionTier = MotionTier.Timed): EnterTransition = when {
    transitions == ShellTransitions.None || tier == MotionTier.Reduced -> EnterTransition.None
    kind == NavKind.Sidebar -> fadeIn(tween(CROSSFADE_MS, easing = DeskMotion.Standard))
    kind == NavKind.BottomBar && tier == MotionTier.Gesture ->
        fadeIn(tween(FADE_IN_MS, delayMillis = FADE_OUT_MS, easing = DeskMotion.EmphasizedDecelerate)) +
            scaleIn(DeskMotion.gesture(), initialScale = 0.96f)
    else -> {
        val scale = if (kind == NavKind.BottomBar) 0.96f else 0.98f
        fadeIn(tween(FADE_IN_MS, delayMillis = FADE_OUT_MS, easing = DeskMotion.EmphasizedDecelerate)) +
            scaleIn(tween(FADE_IN_MS, delayMillis = FADE_OUT_MS, easing = DeskMotion.EmphasizedDecelerate), initialScale = scale)
    }
}

internal fun shellExit(transitions: ShellTransitions, kind: NavKind, tier: MotionTier = MotionTier.Timed): ExitTransition = when {
    transitions == ShellTransitions.None || tier == MotionTier.Reduced -> ExitTransition.None
    kind == NavKind.Sidebar -> fadeOut(tween(CROSSFADE_MS, easing = DeskMotion.Standard))
    else -> fadeOut(tween(FADE_OUT_MS, easing = DeskMotion.EmphasizedAccelerate))
}
