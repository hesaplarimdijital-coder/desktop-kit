package desktopkit.shell

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

/** One top-level screen of the app. [shortLabel] is used where space is tight (bottom bar, rail). */
@Immutable
data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val shortLabel: String = label,
)

/** A titled group of the sidebar ("Library", "Tools"); a null [title] draws a plain gap. */
@Immutable
data class NavSection(val title: String?, val items: List<NavItem>)

/**
 * What the shell shows, in sidebar order. Every item stays reachable in every width class: the
 * ones that do not fit the bottom bar or the rail go to an automatic "More" screen.
 *
 * @param footer pinned at the bottom of the sidebar and the rail (usually Settings).
 * @param bottomBar routes shown in the compact bottom bar, at most 4 (a fifth "More" tab is added when needed).
 * @param rail routes shown in the medium/expanded rail; by default the first 7 items.
 */
@Immutable
class ShellSpec(
    val appTitle: String,
    val sections: List<NavSection>,
    val footer: NavItem? = null,
    bottomBar: List<String>? = null,
    rail: List<String>? = null,
    startRoute: String? = null,
) {
    val items: List<NavItem> = sections.flatMap { it.items } + listOfNotNull(footer)
    val startRoute: String = startRoute ?: items.first().route
    val bottomBarRoutes: List<String> = (bottomBar ?: sections.flatMap { it.items }.take(4).map { it.route }).take(4)
    val railRoutes: List<String> = rail ?: sections.flatMap { it.items }.take(7).map { it.route }

    init {
        val routes = items.map { it.route }
        require(routes.size == routes.toSet().size) { "Duplicate routes in ShellSpec: $routes" }
        require(MORE_ROUTE !in routes) { "$MORE_ROUTE is reserved for the More screen" }
        require(bottomBarRoutes.all { it in routes }) { "Unknown bottom bar route in $bottomBarRoutes" }
        require(railRoutes.all { it in routes }) { "Unknown rail route in $railRoutes" }
    }

    fun item(route: String?): NavItem? = items.firstOrNull { it.route == route }

    /** Items shown directly by the chrome of [kind], without "More". */
    fun shown(kind: NavKind): List<NavItem> = when (kind) {
        NavKind.BottomBar -> bottomBarRoutes.mapNotNull(::item)
        NavKind.Rail -> railRoutes.mapNotNull(::item) + listOfNotNull(footer)
        NavKind.Sidebar -> items
    }

    /** Items reachable through "More" in [kind]; empty when everything fits. */
    fun overflow(kind: NavKind): List<NavItem> {
        val shown = shown(kind).map { it.route }.toSet()
        return items.filter { it.route !in shown }
    }

    companion object {
        const val MORE_ROUTE = "dk_more"
    }
}
