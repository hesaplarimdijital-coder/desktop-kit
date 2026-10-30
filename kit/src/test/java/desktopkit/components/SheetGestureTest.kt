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
 * handle), a short pull springs back, and an on/off action is a switch that leaves the sheet open.
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
