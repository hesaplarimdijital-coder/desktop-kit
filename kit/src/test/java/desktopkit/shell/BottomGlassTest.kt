package desktopkit.shell

import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.SampleMiniPlayer
import desktopkit.SampleFullPlayer
import desktopkit.SampleTrackList
import desktopkit.TestShell
import desktopkit.components.ExpandablePlayer
import desktopkit.components.rememberExpandablePlayerState
import desktopkit.mediaSpec
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * On a phone the player strip, the bottom bar and the system band lie on one glass, placed from the
 * strip's top to the window's bottom and moved with the bars when a scroll hides them.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class BottomGlassTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun oneGlassFromTheStripToTheBottomMovingWithTheBars() {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                val player = rememberExpandablePlayerState()
                TestShell(
                    WidthClass.Compact,
                    startRoute = "music",
                    spec = mediaSpec,
                    bottomBar = {
                        ExpandablePlayer(player, mini = { SampleMiniPlayer(3) }, full = { SampleFullPlayer(3) }, modifier = Modifier.testTag("mini"))
                    },
                ) { _, _ -> SampleTrackList() }
            }
        }
        compose.waitForIdle()
        assertEquals("one glass", 1, compose.onAllNodesWithTag(BOTTOM_GLASS_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)
        val window = compose.onRoot().getBoundsInRoot()
        val strip = compose.onNodeWithTag("mini").getBoundsInRoot()
        val glass = compose.onNodeWithTag(BOTTOM_GLASS_TAG, useUnmergedTree = true).getBoundsInRoot()
        assertEquals("the glass starts at the strip", strip.top.value, glass.top.value, 1f)
        assertTrue("the glass reaches the window's bottom: ${glass.bottom} vs ${window.bottom}", glass.bottom >= window.bottom - 1.dp)
        // a scroll hides the bars: the strip goes down, the glass with it
        compose.onNodeWithTag("tracks").performTouchInput { swipeUp(startY = centerY + 300f, endY = top + 200f, durationMillis = 800) }
        compose.waitForIdle()
        val hiddenStrip = compose.onNodeWithTag("mini").getBoundsInRoot()
        val hiddenGlass = compose.onNodeWithTag(BOTTOM_GLASS_TAG, useUnmergedTree = true).getBoundsInRoot()
        assertTrue("the bars went down: ${strip.top} → ${hiddenStrip.top}", hiddenStrip.top > strip.top + 20.dp)
        assertEquals("the glass went with them", hiddenStrip.top.value, hiddenGlass.top.value, 1f)
    }

}
