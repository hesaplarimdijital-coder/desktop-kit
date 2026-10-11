package desktopkit.shell

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import desktopkit.components.MenuBarMenus
import desktopkit.components.MenuSpec
import desktopkit.screens.OverflowScreen
import desktopkit.theme.DeskTheme

/** Navigation chrome around the current screen; screens show their own title only in [NavKind.Sidebar]. */
val LocalNavKind = compositionLocalOf { NavKind.Sidebar }

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
 * @param bottomBar optional strip under the content (a player, a status bar); it receives the current [NavKind].
 * @param menus menu bar menus, shown in the sidebar layout; [desktopkit.input.ShortcutRegistry.menus] builds them.
 * @param onSearch shows a search field (sidebar layout) or a search button (smaller windows) when set.
 * @param content the screen for a route of [spec]. The "More" route is handled by the shell.
 */
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
    content: @Composable (route: String, navigator: ShellNavigator) -> Unit,
) {
    val navigator = remember(navController) { ShellNavigator(navController) }
    val entry by navController.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route ?: spec.startRoute
    val navKind = NavKind.forWidth(widthClass)
    val more = moreItem()
    // The graph builder below must not capture changing values, or NavHost would rebuild the graph.
    val currentContent by rememberUpdatedState(content)
    val currentSpec by rememberUpdatedState(spec)
    val currentKind by rememberUpdatedState(navKind)

    val searchSlot: (@Composable () -> Unit)? = if (onSearch == null) null else {
        {
            SearchField(onClick = onSearch, shortcut = searchShortcut, hint = searchHint,
                modifier = Modifier.widthIn(min = 220.dp, max = 380.dp))
        }
    }

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
                PageTopBar(title = title, onSearch = onSearch)
            }
        },
        navigation = {
            when (navKind) {
                NavKind.BottomBar -> BottomNavBar(spec, currentRoute, navigator::navigate)
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
                NavHost(
                    navController = navController,
                    startDestination = spec.startRoute,
                    enterTransition = { fadeIn(animationSpec = tween(120)) },
                    exitTransition = { fadeOut(animationSpec = tween(90)) },
                    popEnterTransition = { fadeIn(animationSpec = tween(120)) },
                    popExitTransition = { fadeOut(animationSpec = tween(90)) },
                ) {
                    spec.items.forEach { item ->
                        composable(item.route) { currentContent(item.route, navigator) }
                    }
                    composable(ShellSpec.MORE_ROUTE) {
                        OverflowScreen(currentSpec.overflow(currentKind), onSelect = navigator::navigate)
                    }
                }
            }
        },
    )
}
