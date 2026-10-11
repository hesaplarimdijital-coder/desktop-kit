package desktopkit.shell

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.TestShell
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellStateTest {

    @get:Rule
    val compose = createComposeRule()

    /** Resizing the window across all five width classes keeps the screen and its state. */
    @Test
    fun stateSurvivesWidthChanges() {
        var widthClass by mutableStateOf(WidthClass.Compact)
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                TestShell(widthClass, startRoute = "music", content = { route, _ -> CounterScreen(route) })
            }
        }
        repeat(3) { compose.onNodeWithTag("counter-music").performClick() }
        compose.onNodeWithTag("counter-music").assertTextEquals("music: 3")

        for (next in listOf(WidthClass.Medium, WidthClass.Expanded, WidthClass.Large, WidthClass.ExtraLarge, WidthClass.Compact)) {
            widthClass = next
            compose.waitForIdle()
            compose.onNodeWithTag("counter-music").assertTextEquals("music: 3")
        }
    }

    /** On a compact window, screens outside the bottom bar are reached through "More". */
    @Test
    fun moreListsTheOverflow() {
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Light, inputMode = InputMode.Touch) {
                TestShell(WidthClass.Compact, content = { route, _ -> Text("screen-$route") })
            }
        }
        compose.onNodeWithText("More").performClick()
        compose.onNodeWithText("İndirilenler").performClick()
        compose.onNodeWithText("screen-downloads").assertExists()
    }
}

@Composable
private fun CounterScreen(route: String) {
    var count by rememberSaveable { mutableIntStateOf(0) }
    Text("$route: $count", Modifier.testTag("counter-$route").clickable { count++ })
}
