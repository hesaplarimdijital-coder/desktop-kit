package desktopkit.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The multi-selection rules of DenseTable. */
class TableSelectionTest {

    private val keys: List<Any> = listOf("a", "b", "c", "d", "e")

    private fun click(selection: Set<Any>, anchor: Any?, clicked: Any, toggle: Boolean = false, range: Boolean = false) =
        TableSelection.click(keys, selection, anchor, clicked, toggle, range)

    @Test
    fun plainClickSelectsOnlyThatRow() {
        val result = click(setOf("a", "b"), "a", "d")
        assertEquals(setOf("d"), result.selection)
        assertEquals("d", result.anchor)
    }

    @Test
    fun ctrlClickTogglesAndMovesTheAnchor() {
        val added = click(setOf("a"), "a", "c", toggle = true)
        assertEquals(setOf("a", "c"), added.selection)
        assertEquals("c", added.anchor)
        val removed = click(added.selection, added.anchor, "a", toggle = true)
        assertEquals(setOf("c"), removed.selection)
        assertEquals("a", removed.anchor)
    }

    @Test
    fun shiftClickSelectsTheRangeFromTheAnchorEitherWay() {
        assertEquals(setOf("b", "c", "d"), click(setOf("b"), "b", "d", range = true).selection)
        assertEquals(setOf("b", "c", "d"), click(setOf("d"), "d", "b", range = true).selection)
        // the anchor stays, so a second Shift+click re-spans from it
        val first = click(setOf("b"), "b", "e", range = true)
        assertEquals("b", first.anchor)
        assertEquals(setOf("a", "b"), click(first.selection, first.anchor, "a", range = true).selection)
    }

    @Test
    fun ctrlShiftClickAddsTheRange() {
        val result = click(setOf("e"), "a", "b", toggle = true, range = true)
        assertEquals(setOf("e", "a", "b"), result.selection)
    }

    @Test
    fun shiftClickWithoutAnchorActsLikeAClick() {
        assertEquals(setOf("c"), click(emptySet(), null, "c", range = true).selection)
        // an anchor that is no longer in the rows (filtered out) is ignored as well
        assertEquals(setOf("c"), click(setOf("x"), "x", "c", range = true).selection)
    }

    @Test
    fun selectAllKeepsTheRowOrderAndAnchor() {
        val result = TableSelection.all(keys, "c")
        assertEquals(keys, result.selection.toList())
        assertEquals("c", result.anchor)
    }

    @Test
    fun shiftArrowsExtendFromTheAnchor() {
        val down = TableSelection.extend(keys, anchor = null, focused = "b", delta = 1)!!
        assertEquals(setOf("b", "c"), down.selection)
        assertEquals("b", down.anchor)
        val further = TableSelection.extend(keys, down.anchor, focused = "c", delta = 1)!!
        assertEquals(setOf("b", "c", "d"), further.selection)
        val back = TableSelection.extend(keys, further.anchor, focused = "d", delta = -1)!!
        assertEquals(setOf("b", "c"), back.selection)
        val above = TableSelection.extend(keys, "b", focused = "b", delta = -1)!!
        assertEquals(setOf("a", "b"), above.selection)
    }

    @Test
    fun shiftArrowsStopAtTheEnds() {
        assertNull(TableSelection.extend(keys, null, focused = "a", delta = -1))
        assertNull(TableSelection.extend(keys, null, focused = "e", delta = 1))
        assertNull(TableSelection.extend(keys, null, focused = "missing", delta = 1))
    }

    @Test
    fun menuKeepsASelectionThatContainsTheRow() {
        val kept = TableSelection.menu(setOf("a", "b"), "a", "b")
        assertEquals(setOf("a", "b"), kept.selection)
        assertEquals("a", kept.anchor)
        val replaced = TableSelection.menu(setOf("a", "b"), "a", "d")
        assertEquals(setOf("d"), replaced.selection)
        assertEquals("d", replaced.anchor)
    }
}
