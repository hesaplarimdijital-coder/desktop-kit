package desktopkit.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.TestShell
import desktopkit.shell.WidthClass
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.LocalMotionTier
import desktopkit.theme.MotionTier
import desktopkit.theme.ThemeMode
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A toast or a sheet let go while the finger is still moving. A phone then gives the window the last
 * move and the lift together (the moves that waited for the next frame are handed over just before
 * the lift), and nothing of the app runs between the two.
 *
 * The coroutines of these tests wait in a queue, as they do on a device's main thread
 * ([StandardTestDispatcher]). The rule the kit's tests are built on runs each coroutine the moment it
 * is launched, an order no device has (Compose 1.12 deprecates that rule for one that queues): that
 * is why [ToastSwipeTest] and [SheetGestureTest] never showed what happens here.
 *
 * In 2.4.0 the toast, and the sheet held by its handle or its title, stayed where the finger left
 * them. Each move was applied in a coroutine of its own (`launch { snapTo() }`); Compose's draggable
 * starts `onDragStopped` before anything that waits in the queue has run, so the last move's
 * coroutine ran after the release had begun to carry the thing out (or back) and stopped that
 * ("Mutation interrupted"). Now the finger moves both at once ([FingerOffset]).
 *
 * Every gesture is made with one count of moves and with one move more. Under these tests what
 * moves under the finger may take only every other move of it, and 2.4.0 went wrong only when the
 * last move was one that it took: with one count or the other it is. That was measured in Nağmedar's
 * tests, where a probe printed each move its notice was given (after the first two, every other
 * one). Compose gives a node no move whose place within the node has not changed (HitPathTracker,
 * read in androidx's main branch); a thing that has followed the finger exactly, under the even
 * steps of a test, is there again at every second move. A person's finger does not move in even
 * steps, and on an emulator's touch screen, moved by raw touch events, the notice followed every
 * move.
 *
 * Each gesture also says that the finger had moved the thing when it let go: without that, a thing
 * that never left its place would pass for one that came back.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class LetGoInMotionTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>(effectContext = StandardTestDispatcher())

    /** What the test itself changed is told to the composition first; then [count] frames. */
    private fun frames(count: Int) {
        compose.waitForIdle()
        repeat(count) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
    }

    /** Frames until [done], [most] of them at the most. */
    private fun framesUntil(most: Int, done: () -> Boolean) {
        var ran = 0
        while (ran < most && !done()) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            ran++
        }
    }

    /**
     * A finger down, [moves] moves of [step] px a frame apart, and the lift with the last of them: no
     * time passes between the two, so they are handed to the window in one go.
     */
    private fun TouchInjectionScope.letGoInMotion(step: Offset, moves: Int) {
        down(center)
        repeat(moves) { moveBy(step) }
        up()
    }

    // ------------------------------------------------------------------ the toast

    private val toasts = ToastState()

    /** Long messages: a wide card. The second one is shown once the first has gone. */
    private val messages = listOf(
        "On iki dosya ve üç klasör çöpe taşındı, geri almak için dokun",
        "Yedi dosya ve iki klasör arşive taşındı, geri almak için dokun",
    )
    private var round by mutableIntStateOf(0)

    private fun toast(tier: MotionTier) {
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Touch) {
                CompositionLocalProvider(LocalMotionTier provides tier) {
                    Box(Modifier.fillMaxSize()) { ToastHost(toasts, Modifier.align(Alignment.BottomCenter)) }
                    val which = round
                    LaunchedEffect(which) { toasts.show(messages[which], duration = ToastDuration.Indefinite) }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        // shown from a coroutine, it slides up into place
        frames(90)
    }

    /** Where the toast's card (the row with the pane title) is, in dp from the window's left. */
    private fun cardLeft(): Float =
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle)).getBoundsInRoot().left.value

    /**
     * The toast flung aside in [moves] moves (60 px a frame: 3750 px/s, three times what a fling asks
     * for). Null when it left; else how far from its place it still is 90 frames later, in dp.
     */
    private fun toastFlung(moves: Int): Float? {
        val rest = cardLeft()
        compose.onNodeWithText(messages[round]).performTouchInput { letGoInMotion(Offset(-60f, 0f), moves) }
        // no frame has passed since the lift: it is where the finger had it
        val under = cardLeft() - rest
        assertTrue("the finger had moved the toast when it let go ($under dp from its place, $moves moves)", under < -20f)
        framesUntil(90) { toasts.current == null }
        return if (toasts.current == null) null else cardLeft() - rest
    }

    /**
     * The toast swiped a little and slowly in [moves] moves (9 px a frame: 560 px/s; neither far nor
     * fast enough to leave). How far from its place it is 120 frames later, in dp.
     */
    private fun toastNudged(moves: Int): Float {
        val rest = cardLeft()
        compose.onNodeWithText(messages[round]).performTouchInput { letGoInMotion(Offset(-9f, 0f), moves) }
        val under = cardLeft() - rest
        assertTrue("the finger had moved the toast when it let go ($under dp from its place, $moves moves)", under < -2f)
        frames(120)
        return cardLeft() - rest
    }

    @Test
    fun onAPhoneAToastFlungAwayLeaves() {
        toast(MotionTier.Gesture)
        val first = toastFlung(moves = 5)
        // the next toast, flung with one move more
        compose.runOnUiThread { round = 1 }
        frames(90)
        val second = toastFlung(moves = 6)
        assertEquals(
            "a flung toast leaves, whichever move the lift comes with (else: the dp from its place it stayed at)",
            listOf<Float?>(null, null),
            listOf(first, second),
        )
    }

    @Test
    fun onAPhoneAToastLetGoShortOfTheWayComesBack() {
        toastComesBack(MotionTier.Gesture)
    }

    @Test
    fun widerUpAToastLetGoShortOfTheWayComesBack() {
        toastComesBack(MotionTier.Timed)
    }

    private fun toastComesBack(tier: MotionTier) {
        toast(tier)
        for (moves in intArrayOf(8, 7)) {
            val aside = toastNudged(moves)
            assertNotNull("a short swipe leaves the toast on screen", toasts.current)
            assertEquals("the toast is back at its place ($tier, $moves moves)", 0f, aside, 0.5f)
        }
    }

    // ------------------------------------------------------------------ the sheet

    private var shown by mutableStateOf(true)
    private var dismissed = 0

    private fun sheet() {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Light, inputMode = InputMode.Touch) {
                TestShell(WidthClass.Compact) { _, _ ->
                    GlassBottomSheet(visible = shown, onDismiss = { dismissed++; shown = false }, title = "Paylaş") {
                        repeat(4) { DeskButton("Seçenek $it", {}, Modifier.testTag("row$it")) }
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        frames(90)
    }

    /** Where the sheet's first row is, in dp from the window's top. */
    private fun rowTop(): Float = compose.onNodeWithTag("row0").getBoundsInRoot().top.value

    /**
     * The sheet flung down in [moves] moves (60 px a frame: 3750 px/s, three times what a fling asks
     * for), held by its title or, with [byRow], by a row of its list: on the title the sheet's own
     * drag moves it, on the list it comes as nested scrolling. Null when it was dismissed; else how
     * far below its place it still is 90 frames later, in dp.
     */
    private fun sheetFlung(moves: Int, byRow: Boolean = false): Float? {
        val rest = rowTop()
        val before = dismissed
        val held = if (byRow) compose.onNodeWithTag("row1") else compose.onNodeWithText("Paylaş")
        held.performTouchInput { letGoInMotion(Offset(0f, 60f), moves) }
        // no frame has passed since the lift: it is where the finger had it
        val under = rowTop() - rest
        assertTrue("the finger had pulled the sheet when it let go ($under dp below its place, $moves moves)", under > 20f)
        framesUntil(90) { dismissed > before }
        return if (dismissed > before) null else rowTop() - rest
    }

    /** The sheet pulled down a little and slowly by its title in [moves] moves; how far below its place it is 120 frames later, in dp. */
    private fun sheetNudged(moves: Int): Float {
        val rest = rowTop()
        compose.onNodeWithText("Paylaş").performTouchInput { letGoInMotion(Offset(0f, 9f), moves) }
        val under = rowTop() - rest
        assertTrue("the finger had pulled the sheet when it let go ($under dp below its place, $moves moves)", under > 2f)
        frames(120)
        return rowTop() - rest
    }

    /** Flung down twice, the second time with one move more and after it was opened again: what each fling left. */
    private fun flungTwice(byRow: Boolean): List<Float?> {
        sheet()
        val first = sheetFlung(moves = 5, byRow = byRow)
        frames(30)
        compose.runOnUiThread { shown = true }
        frames(90)
        val second = sheetFlung(moves = 6, byRow = byRow)
        return listOf(first, second)
    }

    @Test
    fun aSheetFlungDownByItsTitleCloses() {
        assertEquals(
            "a sheet flung down by its title closes, whichever move the lift comes with (else: the dp below its place it stayed at)",
            listOf<Float?>(null, null),
            flungTwice(byRow = false),
        )
        assertEquals("each fling dismissed it once", 2, dismissed)
    }

    @Test
    fun aSheetFlungDownOnItsListCloses() {
        assertEquals(
            "a sheet flung down on its list closes, whichever move the lift comes with (else: the dp below its place it stayed at)",
            listOf<Float?>(null, null),
            flungTwice(byRow = true),
        )
        assertEquals("each fling dismissed it once", 2, dismissed)
    }

    @Test
    fun aSheetLetGoShortOfTheWayComesBack() {
        sheet()
        for (moves in intArrayOf(8, 7)) {
            val below = sheetNudged(moves)
            assertEquals("a short pull does not close the sheet", 0, dismissed)
            assertEquals("the sheet is back at its place ($moves moves)", 0f, below, 0.5f)
        }
    }
}
