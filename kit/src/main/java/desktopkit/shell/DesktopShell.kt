package desktopkit.shell

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import desktopkit.components.MenuBarMenus
import desktopkit.components.MenuSpec
import desktopkit.screens.OverflowScreen
import desktopkit.theme.DeskMotion
import desktopkit.theme.DeskTheme
import desktopkit.theme.LocalDeskDimens
import desktopkit.theme.LocalDeskType
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
 *   96 % (phone) or 98 % (tablet) to full size, on the emphasized curve. In the sidebar layout (large
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
 * The bottom navigation bar (compact windows) is hidden while the on-screen keyboard is open, so
 * it does not ride up on top of the keyboard; the content then reaches down to the keyboard. The
 * content ends above the keyboard because [desktopkit.DesktopKitRoot] pads the whole app with
 * `WindowInsets.safeDrawing` (IME included); a shell used without it needs the same padding around it.
 *
 * On compact windows the chrome follows the content's scrolling (see [hideBarsOnScroll]); tablet and
 * DeX windows keep their chrome in place. Text is [largeWindowTextStep] sp larger on rail and sidebar
 * layouts (tablet, DeX); phones get slightly tighter sizes ([compactSizes]) so more fits on screen.
 *
 * @param bottomBar optional strip under the content (a player, a status bar); it receives the current [NavKind].
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
    CompositionLocalProvider(LocalDeskType provides windowType, LocalDeskDimens provides windowDimens) {
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
    content: @Composable (route: String, navigator: ShellNavigator) -> Unit,
) {
    val navigator = remember(navController) { ShellNavigator(navController) }
    val entry by navController.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route ?: spec.startRoute
    val navKind = NavKind.forWidth(widthClass)
    val more = moreItem()
    // Not laid out at all while the keyboard is up; the root's safeDrawing padding already ends the
    // content above it. isImeVisible alone is not enough: it reads true until the first insets arrive
    // (and forever when the window never passes insets to Compose), so the keyboard must also take space.
    val imeVisible = WindowInsets.isImeVisible
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    val imeTakesSpace by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
    val showBottomNav = !(hideNavigationWithKeyboard && imeVisible && imeTakesSpace)
    // The graph builder below must not capture changing values, or NavHost would rebuild the graph.
    val currentContent by rememberUpdatedState(content)
    val currentSpec by rememberUpdatedState(spec)
    val currentKind by rememberUpdatedState(navKind)
    val currentTransitions by rememberUpdatedState(transitions)
    // read when a transition starts, so a new width class or setting applies to the next one
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
        remember { { shellEnter(currentTransitions, currentKind) } }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
        remember { { shellExit(currentTransitions, currentKind) } }
    val topBarHost = remember { CompactTopBarHost() }
    val scroll = remember { ShellScrollState() }
    SideEffect {
        scroll.enabled = navKind == NavKind.BottomBar
        scroll.hideOnScroll = hideBarsOnScroll && showBottomNav
    }
    // every screen starts with the bars shown and a flat top bar
    LaunchedEffect(currentRoute, navKind, showBottomNav) { scroll.reset() }

    val searchSlot: (@Composable () -> Unit)? = if (onSearch == null) null else {
        {
            SearchField(onClick = onSearch, shortcut = searchShortcut, hint = searchHint,
                modifier = Modifier.widthIn(min = 220.dp, max = 380.dp))
        }
    }

    CompositionLocalProvider(LocalWidthClass provides widthClass, LocalCompactTopBarHost provides topBarHost) {
        ShellLayout(
            navKind = navKind,
            modifier = modifier.fillMaxSize().background(DeskTheme.colors.background),
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
                    )
                }
            },
            navigation = {
                when (navKind) {
                    NavKind.BottomBar -> if (showBottomNav) {
                        BottomNavBar(
                            spec, currentRoute, navigator::navigate,
                            modifier = Modifier.onSizeChanged { scroll.maxOffsetPx = it.height.toFloat() },
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
            player = { bottomBar(navKind) },
            content = {
                CompositionLocalProvider(LocalNavKind provides navKind) {
                    Box(Modifier.fillMaxSize().nestedScroll(scroll.connection)) {
                        NavHost(
                            navController = navController,
                            startDestination = spec.startRoute,
                            enterTransition = enter,
                            exitTransition = exit,
                            popEnterTransition = enter,
                            popExitTransition = exit,
                        ) {
                            spec.items.forEach { item ->
                                composable(item.route) { currentContent(item.route, navigator) }
                            }
                            composable(ShellSpec.MORE_ROUTE) {
                                OverflowScreen(currentSpec.overflow(currentKind), onSelect = navigator::navigate)
                            }
                        }
                    }
                }
            },
            navigationOffset = { if (navKind == NavKind.BottomBar) scroll.offsetPx.roundToInt() else 0 },
        )
    }
}

private const val FADE_OUT_MS = 90
private const val FADE_IN_MS = 210
private const val CROSSFADE_MS = 150

/** Fade-through (Material motion) between top-level screens; desktop cross-fade in the sidebar layout. */
private fun shellEnter(transitions: ShellTransitions, kind: NavKind): EnterTransition = when {
    transitions == ShellTransitions.None -> EnterTransition.None
    kind == NavKind.Sidebar -> fadeIn(tween(CROSSFADE_MS, easing = DeskMotion.Standard))
    else -> {
        val scale = if (kind == NavKind.BottomBar) 0.96f else 0.98f
        fadeIn(tween(FADE_IN_MS, delayMillis = FADE_OUT_MS, easing = DeskMotion.EmphasizedDecelerate)) +
            scaleIn(tween(FADE_IN_MS, delayMillis = FADE_OUT_MS, easing = DeskMotion.EmphasizedDecelerate), initialScale = scale)
    }
}

private fun shellExit(transitions: ShellTransitions, kind: NavKind): ExitTransition = when {
    transitions == ShellTransitions.None -> ExitTransition.None
    kind == NavKind.Sidebar -> fadeOut(tween(CROSSFADE_MS, easing = DeskMotion.Standard))
    else -> fadeOut(tween(FADE_OUT_MS, easing = DeskMotion.EmphasizedAccelerate))
}
