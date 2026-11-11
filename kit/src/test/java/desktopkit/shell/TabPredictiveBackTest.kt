package desktopkit.shell

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.TestShell
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** With gesture navigation the Back between a phone's tabs follows the finger: the tab shrinks, the start tab is behind it. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class TabPredictiveBackTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun back(progress: Float) = BackEventCompat(40f, 400f, progress, BackEventCompat.EDGE_LEFT)

    /** The shell on the "documents" tab, come from the start tab, so that Back goes back to it. */
    private fun onTheDocumentsTab() {
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Touch) {
                TestShell(WidthClass.Compact, transitions = ShellTransitions.Default) { route, _ ->
                    Box(Modifier.fillMaxSize().testTag("screen-$route"))
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Belgeler").performClick()
        compose.waitForIdle()
    }

    @Test
    fun theTabShrinksWithTheFingerAndTheStartTabIsBehindIt() {
        onTheDocumentsTab()
        val full = compose.onNodeWithTag("screen-documents").getBoundsInRoot()
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(back(0f))
            dispatcher.dispatchOnBackProgressed(back(0.25f))
        }
        compose.waitForIdle()
        compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(back(0.5f)) }
        compose.waitForIdle()
        // half way: toward 90 %, the start tab really there behind it
        val half = compose.onNodeWithTag("screen-documents").getBoundsInRoot()
        val ratio = (half.right - half.left) / (full.right - full.left)
        assertTrue("width half way: $ratio of full", abs(ratio - 0.95f) < 0.02f)
        compose.onNodeWithTag("screen-home").assertExists()
        // given up: back to full size, still on the tab
        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
        val restored = compose.onNodeWithTag("screen-documents").getBoundsInRoot()
        assertEquals((full.right - full.left).value, (restored.right - restored.left).value, 0.5f)
        // a whole gesture goes back to the start tab
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(back(0f))
            dispatcher.dispatchOnBackProgressed(back(0.6f))
            dispatcher.onBackPressed()
        }
        compose.waitForIdle()
        compose.onNodeWithTag("screen-documents").assertDoesNotExist()
        compose.onNodeWithTag("screen-home").assertExists()
    }

    /**
     * The three-button Back: since Android 16 a press starts a predictive Back with no edge ([BackEventCompat.EDGE_NONE])
     * and completes it on the release, so Navigation seeks the predictive transitions and plays them through. The
     * button is no swipe: the tab fades through as a Back without the gesture does, which leaves it its full size
     * and place on the way out, instead of shrinking and leaning for 300 ms. (The tab stays composed until the
     * whole transition is over, invisible once its 90 ms fade is done.)
     */
    @Test
    fun theBackButtonFadesThroughInsteadOfShrinking() {
        onTheDocumentsTab()
        val full = compose.onNodeWithTag("screen-documents").getBoundsInRoot()
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.mainClock.autoAdvance = false
        // the press: Navigation seeks the transition to its start (a frame to see the Back, one or two to compose
        // the start tab as the target, the transitions chosen with it) and holds it there while the button is down
        compose.runOnUiThread { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_NONE)) }
        var pressedFrames = 0
        while (compose.onAllNodesWithTag("screen-home").fetchSemanticsNodes().isEmpty()) {
            assertTrue("the start tab is composed within a few frames of the press", pressedFrames < 6)
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            pressedFrames++
        }
        val pressed = compose.onNodeWithTag("screen-documents").getBoundsInRoot()
        assertEquals("width while pressed", (full.right - full.left).value, (pressed.right - pressed.left).value, 0.5f)
        // the release, some 48 ms later as a tap is: the rest of the transition plays
        repeat(3 - pressedFrames.coerceAtMost(3)) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.runOnUiThread { dispatcher.onBackPressed() }
        // on the way out the tab keeps its size and place (the gesture's exit has it at 98.4 % three frames in,
        // 95 % nine frames in, leaning to the right)
        for (frames in listOf(3, 6)) {
            repeat(frames) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
            }
            val fading = compose.onNodeWithTag("screen-documents").getBoundsInRoot()
            assertEquals("width on the way out", (full.right - full.left).value, (fading.right - fading.left).value, 0.5f)
            assertEquals("left edge on the way out", full.left.value, fading.left.value, 0.5f)
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("screen-documents").assertDoesNotExist()
        compose.onNodeWithTag("screen-home").assertExists()
    }

    /**
     * The share of reddish pixels in the middle of the window (a quarter in from each side, a third from top and
     * bottom): red, or red seen through a half transparent screen over the page.
     */
    private fun redInTheMiddle(): Float {
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        val left = bitmap.width / 4
        val top = bitmap.height / 3
        val width = bitmap.width / 2
        val height = bitmap.height / 3
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, left, top, width, height)
        val red = pixels.count { p ->
            val r = p shr 16 and 0xff
            r > 100 && r - (p shr 8 and 0xff) > 60 && r - (p and 0xff) > 60
        }
        return red.toFloat() / pixels.size
    }

    /**
     * The tab being left is whole in front of the start tab: on its own page-coloured surface, not lying on
     * the shell's background with the start tab showing through its text. The start tab is pure red, the
     * documents tab is text-like, a small blue square on no background of its own: half way through the
     * gesture the middle of the window (under the shrunk tab) must show no red, and all red once it has gone.
     */
    @Test
    fun theTabIsWholeInFrontOfTheStartTab() {
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Touch) {
                TestShell(WidthClass.Compact, transitions = ShellTransitions.Default) { route, _ ->
                    if (route == "home") {
                        Box(Modifier.fillMaxSize().background(Color.Red).testTag("screen-$route"))
                    } else {
                        Box(Modifier.fillMaxSize().testTag("screen-$route")) { Box(Modifier.size(80.dp).background(Color.Blue)) }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Belgeler").performClick()
        compose.waitForIdle()
        assertEquals("the documents tab covers the start tab", 0f, redInTheMiddle(), 0.01f)
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(back(0f))
            dispatcher.dispatchOnBackProgressed(back(0.25f))
        }
        compose.waitForIdle()
        // the finger holds half way: the transition is seeked there and rests, the start tab composed behind
        compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(back(0.5f)) }
        compose.waitForIdle()
        compose.onNodeWithTag("screen-home").assertExists()
        assertEquals("the start tab shows through the shrunk tab", 0f, redInTheMiddle(), 0.01f)
        compose.runOnUiThread { dispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag("screen-documents").assertDoesNotExist()
        assertEquals("the start tab is back", 1f, redInTheMiddle(), 0.01f)
    }
}
