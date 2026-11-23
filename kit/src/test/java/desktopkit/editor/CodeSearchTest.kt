package desktopkit.editor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

/** The search's matching and replacing: plain logic, no Android. */
class CodeSearchTest {

    private fun find(text: String, query: String, matchCase: Boolean = false, wholeWord: Boolean = false, regex: Boolean = false): List<String> {
        val pattern = checkNotNull(compileSearch(query, matchCase, wholeWord, regex))
        val found = findMatches(text, pattern)
        return found.toList().chunked(2).map { (start, end) -> text.substring(start, end) }
    }

    @Test
    fun caseIsIgnoredUnlessAskedFor() {
        val text = "çay Çay ÇAY çaydanlık"
        assertEquals(listOf("çay", "Çay", "ÇAY", "çay"), find(text, "çay"))
        assertEquals(listOf("Çay"), find(text, "Çay", matchCase = true))
    }

    @Test
    fun aWholeWordIsNotTouchedByLettersDigitsOrUnderscores() {
        val text = "cay cayi _cay cay2 (cay) şcay cay."
        val pattern = checkNotNull(compileSearch("cay", matchCase = false, wholeWord = true, regex = false))
        val found = findMatches(text, pattern).toList()
        assertEquals(listOf(0, 3, 20, 23, 30, 33), found)
    }

    @Test
    fun aRegularExpressionFindsItsMatchesAndAnInvalidOneNothing() {
        assertEquals(listOf("2", "15", "300"), find("cay = 2; seker = 15 + 300", "\\d+", regex = true))
        assertEquals(listOf("-> bahce", "-> END"), find("* a -> bahce\n* b -> END", "->\\s*\\w+", regex = true))
        // a line anchor works line by line
        assertEquals(listOf("==", "=="), find("== bahce ==\nx\n== sahil ==", "^==", regex = true))
        assertNull(compileSearch("(cay", matchCase = false, wholeWord = false, regex = true))
        // without the option the same text is looked for as it is
        assertEquals(listOf("(cay"), find("x (cay y", "(cay"))
        assertNull(compileSearch("", matchCase = false, wholeWord = false, regex = false))
    }

    @Test
    fun emptyMatchesAreLeftOut() {
        assertEquals(listOf("aa"), find("baab", "a*", regex = true))
    }

    @Test
    fun aReplacementFillsInTheGroupsOfAnExpression() {
        val text = "ad: ali@kemer"
        val pattern = checkNotNull(compileSearch("(\\w+)@(\\w+)", matchCase = false, wholeWord = false, regex = true))
        assertEquals("kemer'de ali", expandedReplacement(text, pattern, 4, 13, "$2'de $1", regex = true))
        // a group the expression does not have: the replacement as it is written
        assertEquals("$5", expandedReplacement(text, pattern, 4, 13, "$5", regex = true))
        // a match that is not there (the text moved on): nothing to replace
        assertNull(expandedReplacement(text, pattern, 3, 13, "x", regex = true))
        // literal text keeps its dollar signs
        assertEquals("$1", expandedReplacement(text, pattern, 4, 13, "$1", regex = false))
    }

    @Test
    fun replaceAllIsOneReplacementOfTheSpanAndMovesTheCursorAlong() {
        val text = "cay ile cay, sonra cay"
        val pattern = checkNotNull(compileSearch("cay", matchCase = false, wholeWord = false, regex = false))
        val result = checkNotNull(replaceAllIn(text, pattern, "çay", regex = false))
        assertEquals(0, result.start)
        assertEquals(text.length, result.end)
        assertEquals("çay ile çay, sonra çay", text.replaceRange(result.start, result.end, result.text))
        // a cursor between the second and the third match stays before the third
        assertEquals(text.indexOf(", sonra"), result.mapOffset(text.indexOf(", sonra")))
        val grown = checkNotNull(replaceAllIn(text, pattern, "demli çay", regex = false))
        val newText = text.replaceRange(grown.start, grown.end, grown.text)
        assertEquals(newText.indexOf(", sonra"), grown.mapOffset(text.indexOf(", sonra")))
        // inside a match: after its replacement
        assertEquals("demli çay".length, grown.mapOffset(1))
        assertNull(replaceAllIn(text, checkNotNull(compileSearch("kahve", false, false, false)), "x", regex = false))
    }

    @Test
    fun replaceAllWithGroupsAndADollarInLiteralText() {
        val text = "VAR a = 1\nVAR b = 2"
        val regex = checkNotNull(compileSearch("VAR (\\w+)", matchCase = true, wholeWord = false, regex = true))
        val result = checkNotNull(replaceAllIn(text, regex, "CONST $1", regex = true))
        assertEquals("CONST a = 1\nCONST b = 2", text.replaceRange(result.start, result.end, result.text))
        val literal = checkNotNull(compileSearch("a", matchCase = true, wholeWord = true, regex = false))
        val dollars = checkNotNull(replaceAllIn(text, literal, "$1", regex = false))
        assertEquals("VAR $1 = 1\nVAR b = 2", text.replaceRange(dollars.start, dollars.end, dollars.text))
    }

    @Test
    fun matchesMoveWithAnEditAndTheOnesItTouchesGo() {
        val matches = intArrayOf(0, 3, 10, 13, 20, 23)
        // three characters typed at 5: the first stays, the others move
        assertArrayEquals(intArrayOf(0, 3, 13, 16, 23, 26), shiftMatches(matches, 5, 0, 3))
        // typed right before a match and right after one: both stay matches
        assertArrayEquals(intArrayOf(0, 3, 11, 14, 21, 24), shiftMatches(matches, 10, 0, 1))
        assertArrayEquals(intArrayOf(0, 3, 10, 13, 21, 24), shiftMatches(matches, 13, 0, 1))
        // typed inside the second: it goes
        assertArrayEquals(intArrayOf(0, 3, 21, 24), shiftMatches(matches, 11, 0, 1))
        // a deletion over the first two
        assertArrayEquals(intArrayOf(9, 12), shiftMatches(matches, 1, 11, 0))
    }

    @Test
    fun theMatchesAroundAnOffset() {
        val matches = intArrayOf(0, 3, 10, 13, 20, 23)
        assertEquals(1, firstStartingAtOrAfter(matches, 4))
        assertEquals(1, firstStartingAtOrAfter(matches, 10))
        assertEquals(3, firstStartingAtOrAfter(matches, 21))
        assertEquals(1, lastEndingAtOrBefore(matches, 19))
        assertEquals(-1, lastEndingAtOrBefore(matches, 2))
        assertEquals(1, indexOf(matches, 10, 13))
        assertEquals(-1, indexOf(matches, 10, 12))
    }

    @Test
    fun aSearchStopsWhenItIsNoLongerWanted() {
        val text = "a".repeat(200_000)
        val job = Job()
        job.cancel()
        try {
            // backtracking without end: it would run for ages
            findMatches(text, checkNotNull(compileSearch("a*a*a*a*a*a*b", matchCase = true, wholeWord = false, regex = true)), job)
            fail("a cancelled search stops")
        } catch (_: CancellationException) {
            // stopped while the expression was still trying
        }
    }

    @Test
    fun anExpressionTooDeepForTheTextIsSaidToBeSoInsteadOfCrashing() {
        val text = "a".repeat(200_000)
        val deep = checkNotNull(compileSearch("(a|aa)+b", matchCase = true, wholeWord = false, regex = true))
        try {
            findMatches(text, deep)
            fail("the matcher recursed past the stack")
        } catch (_: TooComplexException) {
            // the bar says the expression is invalid
        }
        assertNull(replaceAllIn(text, deep, "x", regex = true))
    }
}
