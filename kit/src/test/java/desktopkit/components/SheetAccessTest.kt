package desktopkit.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.TestShell
import desktopkit.shell.WidthClass
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A sheet closes without a gesture (TalkBack, Switch Access, a keyboard: the handle is its close
 * button), and sheets never pile up: one opened over another closes that one, unless it is opened from
 * that one's content.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class SheetAccessTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var first by mutableStateOf(false)
    private var second by mutableStateOf(false)

    private fun shell() {
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                TestShell(WidthClass.Compact, content = { _, _ ->
                    GlassBottomSheet(visible = first, onDismiss = { first = false }, title = "Birinci") { Text("birinci-içerik") }
                    GlassBottomSheet(visible = second, onDismiss = { second = false }, title = "İkinci") { Text("ikinci-içerik") }
                })
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theHandleClosesTheSheet() {
        shell()
        first = true
        compose.waitForIdle()
        compose.onNodeWithText("birinci-içerik").assertExists()
        compose.onAllNodesWithContentDescription("Close sheet").onFirst().performClick()
        compose.waitForIdle()
        assertFalse(first)
        compose.onNodeWithText("birinci-içerik").assertDoesNotExist()
    }

    @Test
    fun aSheetOpenedOverAnotherClosesIt() {
        shell()
        first = true
        compose.waitForIdle()
        second = true
        compose.waitForIdle()
        assertFalse("the first sheet closed", first)
        compose.onNodeWithText("ikinci-içerik").assertExists()
        compose.onNodeWithText("birinci-içerik").assertDoesNotExist()
    }

    /**
     * A sheet opened from another's content (a row's "⋯" in a sheet, a menu's sheet on a phone) leaves that one open:
     * closing it would take the new one away with its content (found in Nağmedar 1.0.8, its inspector's sheet).
     */
    @Test
    fun aSheetOpenedFromAnothersContentLeavesItOpen() {
        var inner by mutableStateOf(false)
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                TestShell(WidthClass.Compact, content = { _, _ ->
                    GlassBottomSheet(visible = first, onDismiss = { first = false }, title = "Birinci") {
                        DeskButton("Menüyü aç", { inner = true })
                        GlassBottomSheet(visible = inner, onDismiss = { inner = false }, title = "Menü") { Text("menü-içeriği") }
                    }
                    GlassBottomSheet(visible = second, onDismiss = { second = false }, title = "İkinci") { Text("ikinci-içerik") }
                })
            }
        }
        first = true
        compose.waitForIdle()
        compose.onNodeWithText("Menüyü aç").performClick()
        compose.waitForIdle()
        assertTrue("the sheet it was opened from stays", first)
        compose.onNodeWithText("menü-içeriği").assertExists()
        inner = false
        compose.waitForIdle()
        assertTrue(first)
        compose.onNodeWithText("Menüyü aç").assertExists()
        // a sheet opened from the screen still closes it
        second = true
        compose.waitForIdle()
        assertFalse(first)
        compose.onNodeWithText("ikinci-içerik").assertExists()
    }
}
