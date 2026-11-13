package desktopkit.shell

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.TestShell
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tapping the entry of the screen already shown tells that screen (it scrolls to its top), and only it. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class ReselectTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theCurrentTabHearsItsEntryAgain() {
        val heard = mutableListOf<String>()
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                TestShell(WidthClass.Compact, content = { route, _ ->
                    OnReselect { heard += route }
                    Text("ekran-$route")
                })
            }
        }
        compose.waitForIdle()
        // "Ana sayfa" is the start screen: its entry again
        compose.onNode(hasText("Ana sayfa") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertEquals(listOf("home"), heard)
        // another entry switches screens and tells nobody
        compose.onNodeWithText("Belgeler").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("ekran-documents").assertExists()
        assertEquals(listOf("home"), heard)
    }
}
