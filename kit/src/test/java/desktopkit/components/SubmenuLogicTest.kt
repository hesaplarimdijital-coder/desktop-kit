package desktopkit.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules of submenus that need no screen: where a submenu goes beside its line, which line the
 * mouse means while it travels, and what the keys do.
 */
class SubmenuLogicTest {

    // ---------------------------------------------------------------- where a submenu goes

    private val window = IntRect(0, 0, 1000, 600)
    private val child = IntSize(220, 200)

    /** A 220 px menu whose line of interest starts 80 px down; menus are 4 px apart and padded by 4 px. */
    private fun place(parentLeft: Int, lineTop: Int = 180, area: IntRect = window, rtl: Boolean = false, size: IntSize = child) =
        placeSubmenu(IntRect(parentLeft, 100, parentLeft + 220, 400), lineTop, size, area, rtl, gap = 4, lift = 4)

    @Test
    fun aSubmenuOpensOnTheEndSideLevelWithItsLine() {
        val placed = place(parentLeft = 100)
        assertEquals("4 px to the right of the menu; its first line level with the parent's", IntOffset(324, 176), placed.position)
        assertTrue(placed.atEnd)
    }

    @Test
    fun withoutRoomAtTheEndItOpensOnTheStartSide() {
        // the menu ends at 920: 80 px are left to its right
        val placed = place(parentLeft = 700)
        assertEquals(IntOffset(700 - 4 - 220, 176), placed.position)
        assertFalse(placed.atEnd)
    }

    @Test
    fun inARightToLeftLayoutTheEndSideIsTheLeft() {
        val placed = place(parentLeft = 500, rtl = true)
        assertEquals(IntOffset(500 - 4 - 220, 176), placed.position)
        assertTrue(placed.atEnd)
        // and it flips to the right when the left has no room
        val flipped = place(parentLeft = 100, rtl = true)
        assertEquals(IntOffset(324, 176), flipped.position)
        assertFalse(flipped.atEnd)
    }

    @Test
    fun nearTheBottomItMovesUpToStayInTheWindow() {
        val placed = place(parentLeft = 100, lineTop = 500)
        assertEquals("its bottom edge on the window's", 600 - 200, placed.position.y)
        assertEquals(324, placed.position.x)
    }

    @Test
    fun itNeverGoesUnderTheBarsOfTheWindow() {
        // a 48 px caption bar at the top, a 60 px navigation bar at the bottom
        val area = IntRect(0, 48, 1000, 540)
        assertEquals("not above the caption bar", 48, place(parentLeft = 100, lineTop = 50, area = area).position.y)
        assertEquals("not below the navigation bar", 540 - 200, place(parentLeft = 100, lineTop = 500, area = area).position.y)
        // taller than the room there is: it starts at the top of the room (and scrolls)
        val tall = place(parentLeft = 100, lineTop = 300, area = area, size = IntSize(220, 700))
        assertEquals(48, tall.position.y)
    }

    @Test
    fun onAPhoneItGoesOverItsParentOnTheSideWithMoreRoom() {
        // a 411 px window: neither side of a 220 px menu at 20 has room for another 220 px
        val phone = IntRect(0, 0, 411, 891)
        val placed = placeSubmenu(IntRect(20, 100, 240, 400), 180, child, phone, rtl = false, gap = 4, lift = 4)
        assertEquals("as far to the end as the window lets it", 411 - 220, placed.position.x)
        assertTrue(placed.atEnd)
        // a menu at the end edge: more room at the start
        val atEdge = placeSubmenu(IntRect(180, 100, 400, 400), 180, child, phone, rtl = false, gap = 4, lift = 4)
        assertEquals(0, atEdge.position.x)
        assertFalse(atEdge.atEnd)
    }

    // ---------------------------------------------------------------- the pointer on its way

    /** The open submenu, to the right of a menu whose lines are 32 px high from y = 100. */
    private val submenu = Rect(324f, 96f, 544f, 296f)
    private val slack = 16f

    @Test
    fun aPointerMovingTowardTheSubmenuIsOnItsWay() {
        val from = Offset(150f, 116f)
        assertTrue("straight across", headsFor(from, Offset(200f, 116f), submenu, slack))
        assertTrue("down and across, to a line of the submenu", headsFor(from, Offset(200f, 150f), submenu, slack))
        assertFalse("straight down the menu", headsFor(from, Offset(150f, 180f), submenu, slack))
        assertFalse("away from the submenu", headsFor(from, Offset(100f, 130f), submenu, slack))
        assertFalse("too steep: it would pass under the submenu", headsFor(from, Offset(170f, 180f), submenu, slack))
        assertFalse("it has not moved", headsFor(from, from, submenu, slack))
    }

    @Test
    fun aSubmenuOnTheLeftIsAimedForToTheLeft() {
        val left = Rect(0f, 96f, 96f, 296f)
        val from = Offset(250f, 116f)
        assertTrue(headsFor(from, Offset(200f, 140f), left, slack))
        assertFalse(headsFor(from, Offset(300f, 140f), left, slack))
    }

    @Test
    fun aLineTakesOverOnceThePointerRestsOnIt() {
        val hover = SubmenuHover()
        // nothing is open; the pointer comes onto line 2
        assertEquals(HoverWait.Restart, hover.moved(2, Offset(150f, 180f), open = -1, child = null, slack = slack))
        assertEquals("moving on the same line does not start the wait again", HoverWait.Keep,
            hover.moved(2, Offset(160f, 182f), open = -1, child = null, slack = slack))
        assertEquals(2, hover.settle())
        assertEquals("nothing waits any more", -1, hover.settle())
    }

    @Test
    fun crossingLinesStartsTheWaitAgainOnEachLine() {
        val hover = SubmenuHover()
        assertEquals(HoverWait.Restart, hover.moved(1, Offset(150f, 140f), -1, null, slack))
        assertEquals(HoverWait.Restart, hover.moved(2, Offset(150f, 170f), -1, null, slack))
        assertEquals(HoverWait.Restart, hover.moved(3, Offset(150f, 200f), -1, null, slack))
        assertEquals("the line it ends on", 3, hover.settle())
    }

    @Test
    fun onItsWayToTheOpenSubmenuEveryMoveStartsTheWaitAgain() {
        val hover = SubmenuHover()
        // the submenu of line 0 is open and the pointer is on that line
        assertEquals(HoverWait.Keep, hover.moved(0, Offset(150f, 116f), open = 0, child = submenu, slack = slack))
        // diagonally down across line 1, toward the submenu: slowly, move after move
        assertEquals(HoverWait.Restart, hover.moved(1, Offset(170f, 134f), 0, submenu, slack))
        assertEquals(HoverWait.Restart, hover.moved(1, Offset(190f, 142f), 0, submenu, slack))
        assertEquals(HoverWait.Restart, hover.moved(1, Offset(210f, 150f), 0, submenu, slack))
        // it turns away: straight down the menu, still on line 1; the wait is not started again
        assertEquals(HoverWait.Keep, hover.moved(1, Offset(140f, 160f), 0, submenu, slack))
        assertEquals("line 1 takes over once the pointer rests there", 1, hover.settle())
    }

    @Test
    fun backOnTheSubmenusOwnLineNothingWaits() {
        val hover = SubmenuHover()
        hover.moved(0, Offset(150f, 116f), 0, submenu, slack)
        assertEquals(HoverWait.Restart, hover.moved(1, Offset(150f, 140f), 0, submenu, slack))
        assertEquals(HoverWait.Cancel, hover.moved(0, Offset(150f, 120f), 0, submenu, slack))
        assertEquals(-1, hover.settle())
    }

    @Test
    fun leavingTheMenuForTheSubmenuCancelsTheWait() {
        val hover = SubmenuHover()
        hover.moved(0, Offset(150f, 116f), 0, submenu, slack)
        hover.moved(1, Offset(200f, 140f), 0, submenu, slack)
        assertEquals(HoverWait.Cancel, hover.left())
        assertEquals("the submenu stays open", -1, hover.settle())
        assertEquals("and nothing was waiting the second time", HoverWait.Keep, hover.left())
    }

    // ---------------------------------------------------------------- a menu's state

    private val recent = MenuEntry.Submenu("Son açılanlar", listOf(MenuEntry.Item("a.ink", {})))
    private val empty = MenuEntry.Submenu("Boş", emptyList())
    private val off = MenuEntry.Submenu("Kapalı", listOf(MenuEntry.Item("x", {})), enabled = false)

    @Test
    fun anEmptyOrDisabledSubmenuDoesNotOpen() {
        assertTrue(recent.opens)
        assertFalse(empty.opens)
        assertFalse(off.opens)
    }

    @Test
    fun theRestingPointerOpensASubmenuAndClosesItOnAnotherLine() {
        val entries = listOf(recent, MenuEntry.Item("Kaydet", {}), empty)
        val level = MenuLevel()
        level.pointerMoved(0, Offset(10f, 10f), slack)
        assertTrue("the wait is on", level.wait != 0)
        assertEquals("not before the wait is over", -1, level.openIndex)
        level.settle(entries)
        assertEquals(0, level.openIndex)
        assertFalse("a resting pointer does not move the keyboard focus", level.focusChild)
        // on to a plain item: the submenu closes once the pointer rests there
        level.pointerMoved(1, Offset(10f, 50f), slack)
        level.settle(entries)
        assertEquals(-1, level.openIndex)
        // resting on a submenu that cannot open opens nothing
        level.pointerMoved(2, Offset(10f, 90f), slack)
        level.settle(entries)
        assertEquals(-1, level.openIndex)
    }

    @Test
    fun aClickOpensAtOnceAndStopsTheWait() {
        val level = MenuLevel()
        level.pointerMoved(0, Offset(10f, 10f), slack)
        level.open(0, focus = true)
        assertEquals(0, level.openIndex)
        assertTrue(level.focusChild)
        assertEquals("nothing waits", 0, level.wait)
        // closing another line's submenu leaves this one open
        level.close(3)
        assertEquals(0, level.openIndex)
        level.close()
        assertEquals(-1, level.openIndex)
    }

    @Test
    fun theFocusGoesToTheFirstLineThatCanTakeIt() {
        assertEquals(2, firstFocusableEntry(listOf(MenuEntry.Item("Kapalı", {}, enabled = false), MenuEntry.Separator, MenuEntry.Item("Aç", {}))))
        assertEquals("a submenu that opens can", 1, firstFocusableEntry(listOf(empty, recent)))
        assertEquals(-1, firstFocusableEntry(listOf(MenuEntry.Separator, off)))
    }

    // ---------------------------------------------------------------- keys

    @Test
    fun theArrowPointingAtTheSubmenuOpensItAndTheOneBackCloses() {
        assertEquals(SubmenuKey.Open, submenuKey(Key.DirectionRight, rtl = false, onSubmenuLine = true, inSubmenu = false))
        assertEquals("only on a submenu's line", SubmenuKey.None, submenuKey(Key.DirectionRight, rtl = false, onSubmenuLine = false, inSubmenu = true))
        assertEquals(SubmenuKey.Close, submenuKey(Key.DirectionLeft, rtl = false, onSubmenuLine = false, inSubmenu = true))
        assertEquals(SubmenuKey.Close, submenuKey(Key.Escape, rtl = false, onSubmenuLine = false, inSubmenu = true))
        assertEquals("the first menu has nothing to go back to", SubmenuKey.None, submenuKey(Key.DirectionLeft, rtl = false, onSubmenuLine = false, inSubmenu = false))
        assertEquals("up and down stay in their menu", SubmenuKey.None, submenuKey(Key.DirectionDown, rtl = false, onSubmenuLine = true, inSubmenu = true))
        // a submenu's line inside a submenu: forward opens deeper, back closes the one it is in
        assertEquals(SubmenuKey.Open, submenuKey(Key.DirectionRight, rtl = false, onSubmenuLine = true, inSubmenu = true))
        assertEquals(SubmenuKey.Close, submenuKey(Key.DirectionLeft, rtl = false, onSubmenuLine = true, inSubmenu = true))
    }

    @Test
    fun rightToLeftSwapsTheArrows() {
        assertEquals(SubmenuKey.Open, submenuKey(Key.DirectionLeft, rtl = true, onSubmenuLine = true, inSubmenu = false))
        assertEquals(SubmenuKey.None, submenuKey(Key.DirectionRight, rtl = true, onSubmenuLine = true, inSubmenu = false))
        assertEquals(SubmenuKey.Close, submenuKey(Key.DirectionRight, rtl = true, onSubmenuLine = false, inSubmenu = true))
    }
}
