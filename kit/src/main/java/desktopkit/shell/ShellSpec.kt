package desktopkit.shell

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One screen of the app's navigation. [shortLabel] is used where space is tight (bottom bar, rail).
 *
 * @param parent the route of the item this one sits under: a folder under another, the folders under
 *   a "Folders" screen. The sidebar indents an item under its parent; where the chrome does not show an
 *   item (a folder on a phone, opened from the "Folders" screen with [ShellNavigator.open]) the entry of
 *   its nearest shown parent is the marked one, not "More".
 * @param showIn the navigation chromes that list the item: its entry, or its place on the "More" screen.
 *   An item left out of one is still a screen the app opens ([ShellNavigator.navigate] or
 *   [ShellNavigator.open]): folders only in the sidebar, a "Folders" screen only in the bottom bar and the
 *   rail. Every chrome by default.
 */
@Immutable
data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val shortLabel: String = label,
    val parent: String? = null,
    val showIn: Set<NavKind> = ALL_KINDS,
) {
    private companion object {
        val ALL_KINDS: Set<NavKind> = NavKind.entries.toSet()
    }
}

/**
 * A titled group of the sidebar ("Library", "Tools"); a null [title] draws a plain gap. A [collapsible]
 * group (an account) opens and shuts with its title.
 */
@Immutable
data class NavSection(val title: String?, val items: List<NavItem>, val collapsible: Boolean = false)

/**
 * What the shell shows, in sidebar order. Every item stays reachable in every width class: the
 * ones that do not fit the bottom bar or the rail go to an automatic "More" screen.
 *
 * @param footer pinned at the bottom of the sidebar and the rail (usually Settings).
 * @param bottomBar routes shown in the compact bottom bar, at most 4 (a fifth "More" tab is added when needed).
 *   Three routes and [MoreStyle.Sheet] give the phone layout of a media app: three tabs and "⋯".
 * @param rail routes shown in the medium/expanded rail; by default the first 7 items.
 * @param startRoute the screen the shell opens at (a window opened at a route: [DeskWindows.startRoute]);
 *   by default, and for a route that is not in [sections] or [footer], the first item.
 * @param moreStyle what the phone's "More" tab opens: the "More" screen ([MoreStyle.Screen], as
 *   before) or a glass sheet ([MoreStyle.Sheet]) listing the other screens and the app's own actions
 *   (`DesktopShell(moreActions = …)`). The rail keeps the "More" screen.
 */
@Immutable
class ShellSpec(
    val appTitle: String,
    val sections: List<NavSection>,
    val footer: NavItem? = null,
    bottomBar: List<String>? = null,
    rail: List<String>? = null,
    startRoute: String? = null,
    val moreStyle: MoreStyle = MoreStyle.Screen,
) {
    val items: List<NavItem> = sections.flatMap { it.items } + listOfNotNull(footer)
    // a route the app no longer has (an old window's intent, DeskWindows) starts at the first item
    val startRoute: String = startRoute?.takeIf { route -> items.any { it.route == route } } ?: items.first().route
    val bottomBarRoutes: List<String> =
        (bottomBar ?: sections.flatMap { it.items }.filter { NavKind.BottomBar in it.showIn }.take(4).map { it.route }).take(4)
    val railRoutes: List<String> = rail ?: sections.flatMap { it.items }.filter { NavKind.Rail in it.showIn }.take(7).map { it.route }

    private val byRoute: Map<String, NavItem> = items.associateBy { it.route }

    init {
        val routes = items.map { it.route }
        require(routes.size == routes.toSet().size) { "Duplicate routes in ShellSpec: $routes" }
        require(MORE_ROUTE !in routes) { "$MORE_ROUTE is reserved for the More screen" }
        require(bottomBarRoutes.all { it in routes }) { "Unknown bottom bar route in $bottomBarRoutes" }
        require(railRoutes.all { it in routes }) { "Unknown rail route in $railRoutes" }
        require(bottomBarRoutes.all { NavKind.BottomBar in byRoute.getValue(it).showIn }) { "A bottom bar route is not shown in the bottom bar: $bottomBarRoutes" }
        require(railRoutes.all { NavKind.Rail in byRoute.getValue(it).showIn }) { "A rail route is not shown in the rail: $railRoutes" }
        for (item in items) {
            val parent = item.parent ?: continue
            require(parent in byRoute) { "Unknown parent $parent of ${item.route}" }
            var above: String? = parent
            val seen = mutableSetOf(item.route)
            while (above != null) {
                require(seen.add(above)) { "${item.route} is its own parent through $seen" }
                above = byRoute[above]?.parent
            }
        }
    }

    fun item(route: String?): NavItem? = route?.let(byRoute::get)

    /** Items shown directly by the chrome of [kind], without "More". */
    fun shown(kind: NavKind): List<NavItem> = when (kind) {
        NavKind.BottomBar -> bottomBarRoutes.mapNotNull(::item)
        NavKind.Rail -> railRoutes.mapNotNull(::item) + listOfNotNull(footer?.takeIf { NavKind.Rail in it.showIn })
        NavKind.Sidebar -> items.filter { NavKind.Sidebar in it.showIn }
    }

    /** Items reachable through "More" in [kind]; empty when everything fits. Items not shown in [kind] are left out. */
    fun overflow(kind: NavKind): List<NavItem> {
        val shown = shown(kind).map { it.route }.toSet()
        return items.filter { it.route !in shown && kind in it.showIn }
    }

    /**
     * The route whose entry the chrome of [kind] marks while [current] is open: [current] itself where it has
     * an entry, else its nearest parent that has one, else "More" (the screens it lists, the "More" screen),
     * else none.
     */
    fun marked(kind: NavKind, current: String?): String? {
        if (current == null) return null
        if (current == MORE_ROUTE) return MORE_ROUTE
        val shown = shown(kind).map { it.route }.toSet()
        var route: String? = current
        while (route != null) {
            if (route in shown) return route
            route = byRoute[route]?.parent
        }
        return if (overflow(kind).any { it.route == current }) MORE_ROUTE else null
    }

    /** How many of [item]'s parents the sidebar shows: how far it is indented there. */
    fun depth(item: NavItem): Int {
        var depth = 0
        var above = item.parent?.let(byRoute::get)
        while (above != null) {
            if (NavKind.Sidebar in above.showIn) depth++
            above = above.parent?.let(byRoute::get)
        }
        return depth
    }

    /** Whether [ancestor] is [route]'s parent, or a parent of its parent… */
    fun isUnder(route: String?, ancestor: String?): Boolean {
        if (route == null || ancestor == null) return false
        var above = byRoute[route]?.parent
        while (above != null) {
            if (above == ancestor) return true
            above = byRoute[above]?.parent
        }
        return false
    }

    companion object {
        const val MORE_ROUTE = "dk_more"
    }
}

/** What the phone's "More" tab opens; see [ShellSpec]. */
enum class MoreStyle {
    /** The "More" screen listing the screens that do not fit (0.2.0). */
    Screen,

    /** A "⋯" tab opening a glass sheet: the other screens, then the app's actions. */
    Sheet,
}
