package desktopkit.components

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.SampleFullPlayer
import desktopkit.SampleMiniPlayer
import desktopkit.SampleTrackList
import desktopkit.TestShell
import desktopkit.mediaSpec
import desktopkit.sampleMoreActions
import desktopkit.shell.WidthClass
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The phone's expandable player, its swipes, the "⋯" sheet and the dialog's close affordance. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class PlayerAndSheetTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var player: ExpandablePlayerState
    private var skips = 0
    private var backs = 0

    private fun phone(content: @Composable (String) -> Unit = { route -> Text("screen-$route") }) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                player = rememberExpandablePlayerState()
                TestShell(
                    WidthClass.Compact,
                    startRoute = "music",
                    spec = mediaSpec,
                    moreActions = sampleMoreActions,
                    bottomBar = {
                        ExpandablePlayer(
                            player,
                            mini = { SampleMiniPlayer(3) },
                            full = { SampleFullPlayer(3) },
                            modifier = Modifier.testTag("mini"),
                            onNext = { skips++ },
                            onPrevious = { backs++ },
                        )
                    },
                ) { route, _ -> content(route) }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun tapExpandsAndBackCollapses() {
        phone()
        compose.onNodeWithTag("mini").performClick()
        compose.waitForIdle()
        assertTrue(player.isExpanded)
        assertEquals(1f, player.progress)
        compose.onNodeWithTag("full-player").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertFalse(player.isExpanded)
        compose.onNodeWithTag("full-player").assertDoesNotExist()
    }

    @Test
    fun theBottomBarIsUncoveredOnceTheMiniPlayerIsBack() {
        phone()
        compose.onNodeWithTag("mini").performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        var miniFrames = 0
        var sheetFrames = 0
        repeat(120) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            if (player.progress <= 0.001f) {
                miniFrames++
                // the sheet's background covered the bottom bar for the spring's last half second;
                // now it leaves the overlay a frame after the mini player is back (and is not drawn then)
                if (compose.onAllNodesWithTag("full-player").fetchSemanticsNodes().isNotEmpty()) sheetFrames++
            }
        }
        compose.mainClock.autoAdvance = true
        assertTrue("the mini player never came back", miniFrames > 0)
        assertTrue("the sheet stayed $sheetFrames frames after the mini player was back", sheetFrames <= 1)
    }

    /**
     * The bars were hidden by a scroll, so the strip sits at the bottom edge: the player opens from
     * there and lands back on it, and the bars stay where they were (they used to snap back on the
     * tap, which jumped the strip up 64 dp, and the sheet still opened from the old place).
     */
    @Test
    fun opensFromAndReturnsToTheStripWithTheBarsHidden() {
        phone { SampleTrackList() }
        compose.onNodeWithTag("tracks").performTouchInput { swipeUp(startY = centerY + 300f, endY = top + 200f, durationMillis = 800) }
        compose.waitForIdle()
        val hidden = compose.onNodeWithTag("mini").getBoundsInRoot()
        compose.onNodeWithTag("mini").performClick()
        compose.waitForIdle()
        assertTrue(player.isExpanded)
        val anchor = with(compose.density) { player.collapsedTop.toDp() }
        assertEquals("the sheet opened from the strip: ${hidden.top} vs $anchor", hidden.top.value, anchor.value, 1f)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertFalse(player.isExpanded)
        val back = compose.onNodeWithTag("mini").getBoundsInRoot()
        assertEquals("the strip is where it was: ${hidden.top} → ${back.top}", hidden.top.value, back.top.value, 1f)
    }

    @Test
    fun dragSettlesOnTheNearerSide() {
        phone()
        val root = compose.onRoot().getBoundsInRoot()
        val height = (root.bottom - root.top).value
        // a short, slow drag up: back to the mini player
        compose.onNodeWithTag("mini").performTouchInput {
            swipe(center, center - Offset(0f, height * 0.15f * density), durationMillis = 1500)
        }
        compose.waitForIdle()
        assertFalse(player.isExpanded)
        // past half way: the full player
        compose.onNodeWithTag("mini").performTouchInput {
            swipe(center, center - Offset(0f, height * 0.7f * density), durationMillis = 1500)
        }
        compose.waitForIdle()
        assertTrue(player.isExpanded)
    }

    @Test
    fun sidewaysSwipeSkipsPastTheThreshold() {
        phone()
        compose.onNodeWithTag("mini").performTouchInput { swipe(center, center - Offset(30f, 0f), durationMillis = 800) }
        compose.waitForIdle()
        assertEquals(0, skips)
        compose.onNodeWithTag("mini").performTouchInput { swipe(centerRight - Offset(20f, 0f), centerLeft + Offset(20f, 0f), durationMillis = 400) }
        compose.waitForIdle()
        assertEquals(1, skips)
        compose.onNodeWithTag("mini").performTouchInput { swipe(centerLeft + Offset(20f, 0f), centerRight - Offset(20f, 0f), durationMillis = 400) }
        compose.waitForIdle()
        assertEquals(1, backs)
        assertFalse(player.isExpanded)
    }

    @Test
    fun moreTabOpensTheSheetWithScreensAndActions() {
        phone()
        compose.onNodeWithText("Daha fazla").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ekolayzır").assertIsDisplayed()
        compose.onNodeWithText("Sanatçılar").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("screen-artists").assertExists()
        compose.onNodeWithText("Ekolayzır").assertDoesNotExist()
        // Back closes the sheet without leaving the screen
        compose.onNodeWithText("Daha fazla").performClick()
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("Ekolayzır").assertDoesNotExist()
        compose.onNodeWithText("screen-artists").assertExists()
    }

    @Test
    fun dialogShowsEscWithAKeyboardAndACrossWithout() {
        var dismissed = 0
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                Box(Modifier.fillMaxSize()) {
                    androidx.compose.foundation.layout.Column {
                        DialogFrame({ dismissed++ }, "Klavyeli", dismissStyle = DismissStyle.EscKey) { Text("a") }
                        DialogFrame({ dismissed++ }, "Dokunmatik", dismissStyle = DismissStyle.CloseButton) { Text("b") }
                    }
                }
            }
        }
        compose.onNodeWithText("Esc").performClick()
        compose.onNodeWithContentDescription("Kapat").performClick()
        assertEquals(2, dismissed)
    }

    @Test
    fun dismissStyleFollowsTheHardwareKeyboard() {
        fun config(keyboard: Int, hidden: Int) = Configuration().apply {
            this.keyboard = keyboard
            hardKeyboardHidden = hidden
        }
        assertEquals(DismissStyle.EscKey, dismissStyleFor(config(Configuration.KEYBOARD_QWERTY, Configuration.HARDKEYBOARDHIDDEN_NO)))
        assertEquals(DismissStyle.CloseButton, dismissStyleFor(config(Configuration.KEYBOARD_QWERTY, Configuration.HARDKEYBOARDHIDDEN_YES)))
        assertEquals(DismissStyle.CloseButton, dismissStyleFor(config(Configuration.KEYBOARD_NOKEYS, Configuration.HARDKEYBOARDHIDDEN_YES)))
        assertEquals(DismissStyle.CloseButton, dismissStyleFor(config(Configuration.KEYBOARD_12KEY, Configuration.HARDKEYBOARDHIDDEN_NO)))
    }
}
