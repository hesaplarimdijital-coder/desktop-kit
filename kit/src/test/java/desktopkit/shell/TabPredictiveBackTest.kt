package desktopkit.shell

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import kotlin.math.abs

/** With gesture navigation the Back between a phone's tabs follows the finger: the tab shrinks, the start tab is behind it. */
@RunWith(AndroidJUnit4::class)
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
        // the press: Navigation seeks the transition to its start (a frame to see the Back, one to compose the
        // start tab as the target, the transitions chosen with it) and holds it there while the button is down
        compose.runOnUiThread { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_NONE)) }
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.onNodeWithTag("screen-home").assertExists()
        val pressed = compose.onNodeWithTag("screen-documents").getBoundsInRoot()
        assertEquals("width while pressed", (full.right - full.left).value, (pressed.right - pressed.left).value, 0.5f)
        // the release, 48 ms later as a tap is: the rest of the transition plays
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
}
