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

    @Test
    fun aPathOfPlacesLeadsThroughTheSubmenus() {
        // places on the first page count through the groups: Aç 0, Son açılanlar 1, Parçalar 2, Boş 3
        assertEquals(listOf(recent), resolveSheetPath(groups, listOf(1)))
        assertEquals(listOf(snippets, deep), resolveSheetPath(groups, listOf(2, 2)))
        assertEquals(emptyList<MenuEntry.Submenu>(), resolveSheetPath(groups, emptyList()))
    }

    @Test
    fun aPathThatNoLongerLeadsToASubmenuStopsThere() {
        assertEquals("an item", emptyList<MenuEntry.Submenu>(), resolveSheetPath(groups, listOf(0)))
        assertEquals("an empty submenu", emptyList<MenuEntry.Submenu>(), resolveSheetPath(groups, listOf(3)))
        assertEquals("out of range", emptyList<MenuEntry.Submenu>(), resolveSheetPath(groups, listOf(9)))
        assertEquals("the last page that is still there", listOf(snippets), resolveSheetPath(groups, listOf(2, 0)))
        assertEquals("the caller emptied its groups", emptyList<MenuEntry.Submenu>(), resolveSheetPath(emptyList(), listOf(2, 2)))
    }

    @Test
    fun backGoesUpOnePageAndRemembersWhereItCameFrom() {
        val pages = SheetPages()
        assertFalse("nowhere to go from the first page", pages.back())
        pages.open(2)
        pages.open(2)
        assertEquals(2, pages.depth)
        val parentScroll = pages.scrollOf(1)
        val leftScroll = pages.scrollOf(2)
        assertTrue(pages.back())
        assertEquals(listOf(2), pages.path)
        assertEquals("the line that opened the page left", 2, pages.cameFrom)
        assertTrue("the page gone back to is where it was", parentScroll === pages.scrollOf(1))
        assertFalse("the page left starts at its top the next time", leftScroll === pages.scrollOf(2))
        assertTrue(pages.back())
        assertFalse(pages.back())
        pages.open(1)
        pages.reset()
        assertEquals(0, pages.depth)
        assertEquals(null, pages.cameFrom)
    }
}
