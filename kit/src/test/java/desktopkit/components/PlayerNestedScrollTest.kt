package desktopkit.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.SampleMiniPlayer
import desktopkit.TestShell
import desktopkit.shell.WidthClass
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The full player's scrollable content scrolls first; at its top a downward drag pulls the player down. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class PlayerNestedScrollTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var player: ExpandablePlayerState
    private lateinit var list: LazyListState

    private fun open() {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Touch) {
                player = rememberExpandablePlayerState(PlayerValue.Expanded)
                TestShell(
                    WidthClass.Compact,
                    bottomBar = {
                        ExpandablePlayer(player, mini = { SampleMiniPlayer(0) }, full = {
                            list = rememberLazyListState()
                            LazyColumn(Modifier.fillMaxSize().testTag("lyrics"), state = list) {
                                items(60) { Text("Dize $it", Modifier.fillMaxWidth().height(48.dp)) }
                            }
                        })
                    },
                ) { _, _ -> Text("ekran") }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theContentScrollsFirstThenThePlayerFollowsTheFinger() {
        open()
        assertTrue(player.isExpanded)
        // up: the lyrics scroll, the player stays
        compose.onNodeWithTag("lyrics").performTouchInput { swipe(center, center + Offset(0f, -600f), durationMillis = 800) }
        compose.waitForIdle()
        assertTrue("the lyrics scrolled: ${list.firstVisibleItemIndex}", list.firstVisibleItemIndex > 0)
        assertTrue(player.isExpanded)
        // down a little, slowly: the lyrics scroll back first, the player stays open
        compose.onNodeWithTag("lyrics").performTouchInput { swipe(center, center + Offset(0f, 300f), durationMillis = 800) }
        compose.waitForIdle()
        assertTrue(player.isExpanded)
        assertEquals("the player did not move", 1f, player.progress, 0.001f)
        // back to the top, then on down: the player follows the finger and closes past half way
        compose.onNodeWithTag("lyrics").performScrollToIndex(0)
        compose.waitForIdle()
        compose.onNodeWithTag("lyrics").performTouchInput { swipe(Offset(centerX, top + 20f), Offset(centerX, bottom - 20f), durationMillis = 1200) }
        compose.waitForIdle()
        assertFalse("pulled down past half way at the top of the lyrics, the player closed", player.isExpanded)
    }

    @Test
    fun aShortPullAtTheTopSettlesBackOpen() {
        open()
        compose.onNodeWithTag("lyrics").performTouchInput { swipe(Offset(centerX, top + 20f), Offset(centerX, top + 220f), durationMillis = 1200) }
        compose.waitForIdle()
        assertTrue(player.isExpanded)
        assertEquals(1f, player.progress, 0.001f)
    }
}
