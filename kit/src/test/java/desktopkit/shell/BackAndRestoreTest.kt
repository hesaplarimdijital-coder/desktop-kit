package desktopkit.shell

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.DesktopKitRoot
import desktopkit.TestShell
import desktopkit.settings.rememberDeskPreferences
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.LocalMotionTier
import desktopkit.theme.MotionTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.math.abs

/** T3: the predictive Back gesture on a detail, and the shell's state across a recreated activity (rotation). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class BackAndRestoreTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun back(progress: Float) = BackEventCompat(40f, 400f, progress, BackEventCompat.EDGE_LEFT)

    @Test
    fun theDetailFollowsTheBackGestureAndCancelRestoresIt() {
        var detail by mutableStateOf<String?>("album")
        compose.setContent {
            DesktopKitTheme {
                androidx.compose.runtime.CompositionLocalProvider(LocalMotionTier provides MotionTier.Gesture) {
                    DetailTransition(detail = detail, onBack = { detail = null }, list = { Text("Liste") }) {
                        Box(Modifier.fillMaxSize().testTag("detail"))
                    }
                }
            }
        }
        compose.waitForIdle()
        val full = compose.onNodeWithTag("detail").getBoundsInRoot()
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(back(0f))
            dispatcher.dispatchOnBackProgressed(back(0.5f))
        }
        compose.waitForIdle()
        // half way: the detail has shrunk toward 90 % (95 % now) and leans toward the edge the swipe came from
        val half = compose.onNodeWithTag("detail").getBoundsInRoot()
        val ratio = (half.right - half.left) / (full.right - full.left)
        assertTrue("width at half way: $ratio of full", abs(ratio - 0.95f) < 0.01f)
        // the swipe was abandoned: back to full size, still open
        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
        val restored = compose.onNodeWithTag("detail").getBoundsInRoot()
        assertEquals(full.right - full.left, restored.right - restored.left)
        assertEquals("album", detail)
        // a whole gesture closes it
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(back(0f))
            dispatcher.dispatchOnBackProgressed(back(1f))
            dispatcher.onBackPressed()
        }
        compose.waitForIdle()
        assertNull(detail)
        compose.onNodeWithText("Liste").assertExists()
    }

    @Test
    fun theOpenScreenAndItsStateSurviveARecreatedActivity() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            DesktopKitRoot(rememberDeskPreferences()) { widthClass ->
                TestShell(widthClass, content = { route, _ -> SavedCounter(route) })
            }
        }
        compose.onNodeWithText("Belgeler").performClick()
        compose.onNodeWithTag("counter").performClick()
        compose.onNodeWithTag("counter").performClick()
        compose.onNodeWithText("documents: 2").assertExists()
        // what a rotation does to an activity that does not handle the change itself
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("documents: 2").assertExists()
    }
}

@Composable
private fun SavedCounter(route: String) {
    var count by rememberSaveable { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize()) {
        Text("$route: $count", Modifier.testTag("counter").clickable { count++ })
    }
}
