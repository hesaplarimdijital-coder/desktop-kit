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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A sheet closes without a gesture (TalkBack, Switch Access, a keyboard: the handle is its close
 * button), and sheets never pile up: one opened over another closes that one.
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
}
