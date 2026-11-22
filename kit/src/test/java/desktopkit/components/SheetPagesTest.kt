package desktopkit.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pages of a "⋯" sheet with submenus: the way down through them, and the way back. */
class SheetPagesTest {

    private val recent = MenuEntry.Submenu("Son açılanlar", listOf(MenuEntry.Item("a.ink", {})))
    private val empty = MenuEntry.Submenu("Boş", emptyList())

    private val deep = MenuEntry.Submenu("Derin", listOf(MenuEntry.Item("En alt", {})))
    private val snippets = MenuEntry.Submenu("Parçalar", listOf(MenuEntry.Item("Düğüm", {}), MenuEntry.Separator, deep))
    private val groups = listOf(
        ActionGroup(null, listOf(MenuEntry.Item("Aç", {}), recent)),
        ActionGroup("Boş grup", emptyList()),
        ActionGroup("Ink", listOf(snippets, empty)),
    )

    @Test
    fun aGroupOfItemsBecomesMenuEntriesAsBefore() {
        val entries = groups.toMenuEntries()
        assertEquals("a rule between the groups, the empty group left out", 5, entries.size)
        assertEquals(MenuEntry.Separator, entries[2])
        assertEquals(snippets, entries[3])
    }

    private fun submenus(path: List<SheetStep>, groups: List<ActionGroup> = this.groups) = resolveSheetPath(groups, path).submenus

    @Test
    fun aPathLeadsThroughTheSubmenus() {
        // places on the first page count through the groups: Aç 0, Son açılanlar 1, Parçalar 2, Boş 3
        assertEquals(listOf(recent), submenus(listOf(SheetStep(1, "Son açılanlar"))))
        assertEquals(listOf(snippets, deep), submenus(listOf(SheetStep(2, "Parçalar"), SheetStep(2, "Derin"))))
        assertEquals(emptyList<MenuEntry.Submenu>(), submenus(emptyList()))
    }

    @Test
    fun aPathThatNoLongerLeadsToASubmenuStopsThere() {
        assertEquals("an item", emptyList<MenuEntry.Submenu>(), submenus(listOf(SheetStep(0, "Aç"))))
        assertEquals("an empty submenu", emptyList<MenuEntry.Submenu>(), submenus(listOf(SheetStep(3, "Boş"))))
        assertEquals("out of range", emptyList<MenuEntry.Submenu>(), submenus(listOf(SheetStep(9, "Yok"))))
        assertEquals("the last page that is still there", listOf(snippets),
            submenus(listOf(SheetStep(2, "Parçalar"), SheetStep(0, "Düğüm"))))
        assertEquals("the caller emptied its groups", emptyList<MenuEntry.Submenu>(),
            submenus(listOf(SheetStep(2, "Parçalar"), SheetStep(2, "Derin")), emptyList()))
    }

    @Test
    fun aStepIsFoundAgainByItsLabelWhenTheEntriesMove() {
        // an entry came in at the top: "Son açılanlar" is one place further down, and "Parçalar" took its old place
        val moved = listOf(ActionGroup(null, listOf(MenuEntry.Item("Yeni", {}), snippets, recent)))
        val trail = resolveSheetPath(moved, listOf(SheetStep(1, "Son açılanlar")))
        assertEquals(listOf(recent), trail.submenus)
        assertEquals("the step where it is now", listOf(SheetStep(2, "Son açılanlar")), trail.steps)
    }

    @Test
    fun aSubmenuRenamedWhereItWasIsFoundAtItsPlace() {
        val renamed = MenuEntry.Submenu("Son açılanlar (1)", recent.entries)
        val trail = resolveSheetPath(listOf(ActionGroup(null, listOf(MenuEntry.Item("Aç", {}), renamed))), listOf(SheetStep(1, "Son açılanlar")))
        assertEquals(listOf(renamed), trail.submenus)
        assertEquals(listOf(SheetStep(1, "Son açılanlar (1)")), trail.steps)
    }

    @Test
    fun backGoesUpOnePageAndRemembersWhereItCameFrom() {
        val pages = SheetPages()
        assertFalse("nowhere to go from the first page", pages.back())
        pages.open(2, snippets)
        pages.open(2, deep)
        assertEquals(2, pages.depth)
        val parentScroll = pages.scrollOf(1)
        val leftScroll = pages.scrollOf(2)
        assertTrue(pages.back())
        assertEquals(listOf(SheetStep(2, "Parçalar")), pages.path)
        assertEquals("the line that opened the page left", 2, pages.cameFrom)
        assertTrue("the page gone back to is where it was", parentScroll === pages.scrollOf(1))
        assertFalse("the page left starts at its top the next time", leftScroll === pages.scrollOf(2))
        assertTrue(pages.back())
        assertFalse(pages.back())
        pages.open(1, recent)
        pages.reset()
        assertEquals(0, pages.depth)
        assertEquals(null, pages.cameFrom)
    }

    @Test
    fun thePathSettlesOnWhatIsOnScreen() {
        val pages = SheetPages()
        pages.open(2, snippets)
        pages.open(2, deep)
        val deepScroll = pages.scrollOf(2)
        // the entries changed: "Parçalar" moved to place 3 and its "Derin" went
        pages.settle(listOf(SheetStep(3, "Parçalar")))
        assertEquals(listOf(SheetStep(3, "Parçalar")), pages.path)
        assertFalse("a page that went starts at its top if it comes again", deepScroll === pages.scrollOf(2))
        // Back goes up from the page on screen, in one step
        assertTrue(pages.back())
        assertEquals(0, pages.depth)
        assertEquals(3, pages.cameFrom)
        assertFalse(pages.back())
    }

    @Test
    fun aSheetThatOpensStartsOnItsFirstPageAtItsTop() {
        val pages = SheetPages()
        pages.follow(visible = true)
        pages.open(2, snippets)
        val scroll = pages.scrollOf(0)
        // composed again while it is up (a switch on the page flipped): the page stays
        pages.follow(visible = true)
        assertEquals(1, pages.depth)
        // closed (it slides out on that page), then opened again
        pages.follow(visible = false)
        assertEquals(1, pages.depth)
        pages.follow(visible = true)
        assertEquals(0, pages.depth)
        assertFalse("the first page starts at its top again", scroll === pages.scrollOf(0))
    }
}
