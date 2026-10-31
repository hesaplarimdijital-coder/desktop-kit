package desktopkit.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.TestShell
import desktopkit.shell.WidthClass
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The phone's bottom sheets: pulled down anywhere on their list they close (not only on the
 * handle), slowly past a third of their height or with a fling; a short pull springs back, and an
 * on/off action is a switch that leaves the sheet open.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class SheetGestureTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var open by mutableStateOf(true)
    private var dismissed = 0

    private fun sheet() {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Light, inputMode = InputMode.Touch) {
                TestShell(WidthClass.Compact) { _, _ ->
                    GlassBottomSheet(visible = open, onDismiss = { dismissed++; open = false }, title = "Paylaş") {
                        repeat(4) { DeskButton("Seçenek $it", {}, Modifier.testTag("row$it")) }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aPullDownOnTheListClosesTheSheet() {
        sheet()
        compose.onNodeWithTag("row1").performTouchInput { swipe(center, center + Offset(0f, 700f), durationMillis = 250) }
        compose.waitForIdle()
        assertEquals(1, dismissed)
    }

    /** A fling carries the sheet out at its own speed, frame by frame; the dismiss comes once it is gone. */
    @Test
    fun aFlingCarriesTheSheetOutBeforeItIsDismissed() {
        sheet()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("row1").performTouchInput { swipe(center, center + Offset(0f, 150f), durationMillis = 60) }
        compose.waitForIdle()
        assertEquals("not dismissed while still on its way out", 0, dismissed)
        var last = compose.onNodeWithTag("row1").getBoundsInRoot().top
        var frames = 0
        var moved = 0
        var still = 0
        while (dismissed == 0 && frames < 90) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            frames++
            if (dismissed == 0) {
                val top = compose.onNodeWithTag("row1").getBoundsInRoot().top
                assertTrue("frame $frames: the sheet came back up ($last → $top)", top >= last)
                // the animation's first frame is its starting point; after that no pause of two frames
                if (top > last) {
                    moved++
                    still = 0
                } else {
                    still++
                    assertTrue("frame $frames: the sheet stood still at $top", still <= 1)
                }
                last = top
            }
        }
        compose.mainClock.autoAdvance = true
        assertEquals(1, dismissed)
        assertTrue("the sheet moved on its own after the finger lifted ($moved frames)", moved >= 3)
    }

    @Test
    fun aSlowPullPastAThirdClosesTheSheet() {
        // far slower than a fling: only the distance can close it
        sheet()
        compose.onNodeWithTag("row1").performTouchInput { swipe(center, center + Offset(0f, 500f), durationMillis = 2500) }
        compose.waitForIdle()
        assertEquals(1, dismissed)
    }

    @Test
    fun aShortSlowPullSpringsBack() {
        sheet()
        val before = compose.onNodeWithTag("row1").getBoundsInRoot()
        compose.onNodeWithTag("row1").performTouchInput { swipe(center, center + Offset(0f, 40f), durationMillis = 1000) }
        compose.waitForIdle()
        assertEquals(0, dismissed)
        val after = compose.onNodeWithTag("row1").getBoundsInRoot()
        assertEquals(before.top.value, after.top.value, 0.5f)
    }

    @Test
    fun anOnOffActionIsASwitchThatLeavesTheSheetOpen() {
        var follow by mutableStateOf(false)
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Light, inputMode = InputMode.Touch) {
                TestShell(WidthClass.Compact) { _, _ ->
                    MoreSheet(
                        visible = open,
                        onDismiss = { dismissed++; open = false },
                        groups = listOf(
                            ActionGroup(
                                "Uygulama",
                                listOf(
                                    MenuEntry.Item("Kapak rengini izle", { follow = !follow }, checked = follow),
                                    MenuEntry.Item("Hakkında", {}),
                                ),
                            ),
                        ),
                    )
                }
            }
        }
        compose.waitForIdle()
        val toggle = compose.onNodeWithText("Kapak rengini izle")
        toggle.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        toggle.assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
        toggle.performClick()
        compose.waitForIdle()
        assertTrue(follow)
        assertEquals("a switch keeps the sheet open", 0, dismissed)
        compose.onNodeWithText("Kapak rengini izle")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
        // any other action closes it
        compose.onNodeWithText("Hakkında").performClick()
        compose.waitForIdle()
        assertEquals(1, dismissed)
    }
}
