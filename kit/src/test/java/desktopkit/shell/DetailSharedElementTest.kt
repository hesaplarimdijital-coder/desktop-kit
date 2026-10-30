package desktopkit.shell

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.LocalMotionTier
import desktopkit.theme.MotionTier
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** M3: a cover marked on both sides flies from the list row to the detail. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class DetailSharedElementTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun coverFliesFromTheRowToTheDetail() {
        var detail by mutableStateOf<String?>(null)
        compose.setContent {
            DesktopKitTheme {
                androidx.compose.runtime.CompositionLocalProvider(LocalMotionTier provides MotionTier.Timed) {
                    DetailTransition(
                        detail = detail,
                        onBack = { detail = null },
                        list = {
                            Column {
                                Box(Modifier.detailSharedElement("cover").size(40.dp).background(Color.Red).testTag("small"))
                                Text("Gece Yarısı Treni", Modifier.detailSharedBounds("title"))
                            }
                        },
                    ) {
                        Column {
                            Box(Modifier.detailSharedElement("cover").size(232.dp).background(Color.Red).testTag("large"))
                            Text("Gece Yarısı Treni", Modifier.detailSharedBounds("title"))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { detail = "album" }
        // frame by frame: the cover grows from the row's 40 dp to the detail's 232 dp
        val widths = mutableListOf<Float>()
        repeat(30) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            if (compose.onAllNodesWithTag("large", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
                val bounds = compose.onNodeWithTag("large", useUnmergedTree = true).getBoundsInRoot()
                widths += (bounds.right - bounds.left).value
            }
        }
        assertTrue("the cover passes through sizes in between: $widths", widths.any { it > 44f && it < 228f })
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val landed = compose.onNodeWithTag("large", useUnmergedTree = true).getBoundsInRoot()
        assertTrue("cover lands at 232 dp: ${landed.right - landed.left}", (landed.right - landed.left) >= 231.dp)
        compose.onNodeWithText("Gece Yarısı Treni").assertExists()
    }
}
