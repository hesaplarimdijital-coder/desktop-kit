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

    @Test
    fun theTabShrinksWithTheFingerAndTheStartTabIsBehindIt() {
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
}
