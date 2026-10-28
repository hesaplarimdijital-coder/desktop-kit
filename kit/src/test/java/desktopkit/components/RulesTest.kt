package desktopkit.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure rules behind the column chooser, the selection and the mini player's swipe. */
class RulesTest {

    private val ids = listOf("name", "artist", "album", "length")

    @Test
    fun normalizeKeepsStoredOrderAndAppendsNewColumns() {
        val (order, hidden) = TableColumnsLayout.normalize(ids, listOf("album", "gone", "name"), setOf("gone", "artist"))
        assertEquals(listOf("album", "name", "artist", "length"), order)
        assertEquals(setOf("artist"), hidden)
    }

    @Test
    fun aColumnAlwaysStaysVisible() {
        val (_, hidden) = TableColumnsLayout.normalize(ids, emptyList(), ids.toSet())
        assertEquals(setOf("artist", "album", "length"), hidden)
        val onlyName = setOf("artist", "album", "length")
        assertEquals(onlyName, TableColumnsLayout.toggle(ids, onlyName, "name"))
        assertEquals(setOf("album", "length"), TableColumnsLayout.toggle(ids, onlyName, "artist"))
    }

    @Test
    fun moveSkipsHiddenColumns() {
        // "album" hidden: moving "length" left swaps it with "artist"
        assertEquals(listOf("name", "length", "album", "artist"), TableColumnsLayout.move(ids, setOf("album"), "length", -1))
        assertEquals(listOf("artist", "name", "album", "length"), TableColumnsLayout.move(ids, emptySet(), "name", 1))
        assertEquals(ids, TableColumnsLayout.move(ids, emptySet(), "name", -1))
        assertEquals(ids, TableColumnsLayout.move(ids, emptySet(), "unknown", 1))
    }

    @Test
    fun columnsStateArrangesAndReportsChanges() {
        val columns = ids.map { id -> TableColumn<String>(id.uppercase(), ColumnWidth.Weight(1f), id = id) { it } }
        var stored: Pair<List<String>, Set<String>>? = null
        val state = TableColumnsState { order, hidden -> stored = order to hidden }
        assertEquals(listOf(0, 1, 2, 3), state.arrange(columns).map { it.index })
        state.toggle("artist", ids)
        state.move("length", -1, ids)
        assertEquals(listOf(0, 3, 2), state.arrange(columns).map { it.index })
        assertEquals(listOf("name", "artist", "length", "album") to setOf("artist"), stored)
        state.reset()
        assertEquals(listOf(0, 1, 2, 3), state.arrange(columns).map { it.index })
    }

    @Test
    fun escapeClearsTheSelection() {
        val result = TableSelection.clear()
        assertTrue(result.selection.isEmpty())
        assertEquals(null, result.anchor)
    }

    @Test
    fun swipeCommitsPastTheThresholdOrOnAFling() {
        fun decide(offset: Float, velocity: Float = 0f, prev: Boolean = true, next: Boolean = true, rtl: Boolean = false) =
            MiniSwipe.decide(offset, velocity, threshold = 100f, flingVelocity = 1000f, canPrevious = prev, canNext = next, rtl = rtl)
        assertEquals(MiniSwipeAction.None, decide(0f))
        assertEquals(MiniSwipeAction.None, decide(-60f))
        assertEquals(MiniSwipeAction.Next, decide(-100f))
        assertEquals(MiniSwipeAction.Previous, decide(120f))
        // a quick flick commits early, but not against its own direction or from almost nothing
        assertEquals(MiniSwipeAction.Next, decide(-40f, velocity = -1500f))
        assertEquals(MiniSwipeAction.None, decide(-40f, velocity = 1500f))
        assertEquals(MiniSwipeAction.None, decide(-10f, velocity = -1500f))
        // no handler, no skip; right-to-left mirrors the directions
        assertEquals(MiniSwipeAction.None, decide(-150f, next = false))
        assertEquals(MiniSwipeAction.Next, decide(150f, rtl = true))
        assertEquals(MiniSwipeAction.Previous, decide(-150f, rtl = true))
    }

    @Test
    fun swipeResistsTowardsADirectionWithoutHandler() {
        assertEquals(-90f, MiniSwipe.resist(-90f, canPrevious = true, canNext = true))
        assertEquals(-30f, MiniSwipe.resist(-90f, canPrevious = true, canNext = false))
        assertEquals(30f, MiniSwipe.resist(90f, canPrevious = false, canNext = true))
    }
}
