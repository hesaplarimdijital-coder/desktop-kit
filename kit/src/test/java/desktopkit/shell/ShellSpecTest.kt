package desktopkit.shell

import desktopkit.components.DeskIcons
import desktopkit.sampleSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellSpecTest {

    @Test
    fun widthThresholds() {
        assertEquals(WidthClass.Compact, WidthClass.fromWidth(411f))
        assertEquals(WidthClass.Compact, WidthClass.fromWidth(599.9f))
        assertEquals(WidthClass.Medium, WidthClass.fromWidth(600f))
        assertEquals(WidthClass.Expanded, WidthClass.fromWidth(840f))
        assertEquals(WidthClass.Large, WidthClass.fromWidth(1200f))
        assertEquals(WidthClass.ExtraLarge, WidthClass.fromWidth(1600f))
        assertEquals(WidthClass.ExtraLarge, WidthClass.fromWidth(2960f))
    }

    @Test
    fun navigationChrome() {
        assertEquals(NavKind.BottomBar, NavKind.forWidth(WidthClass.Compact))
        assertEquals(NavKind.Rail, NavKind.forWidth(WidthClass.Medium))
        assertEquals(NavKind.Rail, NavKind.forWidth(WidthClass.Expanded))
        assertEquals(NavKind.Sidebar, NavKind.forWidth(WidthClass.Large))
        assertEquals(NavKind.Sidebar, NavKind.forWidth(WidthClass.ExtraLarge))
    }

    @Test
    fun everyItemIsReachableFromEveryChrome() {
        for (kind in NavKind.entries) {
            val reachable = (sampleSpec.shown(kind) + sampleSpec.overflow(kind)).map { it.route }.toSet()
            val missing = sampleSpec.items.map { it.route }.filter { it !in reachable }
            assertEquals("$kind misses $missing", emptyList<String>(), missing)
        }
        assertTrue(sampleSpec.overflow(NavKind.Sidebar).isEmpty())
        assertEquals(6, sampleSpec.overflow(NavKind.BottomBar).size)
    }

    @Test
    fun defaults() {
        val spec = ShellSpec("App", listOf(NavSection(null, (1..9).map { NavItem("r$it", "R$it", DeskIcons.Home) })))
        assertEquals("r1", spec.startRoute)
        assertEquals(listOf("r1", "r2", "r3", "r4"), spec.bottomBarRoutes)
        assertEquals(7, spec.railRoutes.size)
    }

    @Test
    fun invalidSpecsAreRejected() {
        val home = NavItem("home", "Home", DeskIcons.Home)
        assertThrows(IllegalArgumentException::class.java) { ShellSpec("App", listOf(NavSection(null, listOf(home, home)))) }
        assertThrows(IllegalArgumentException::class.java) {
            ShellSpec("App", listOf(NavSection(null, listOf(home))), bottomBar = listOf("missing"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShellSpec("App", listOf(NavSection(null, listOf(NavItem(ShellSpec.MORE_ROUTE, "More", DeskIcons.Menu)))))
        }
    }
}
