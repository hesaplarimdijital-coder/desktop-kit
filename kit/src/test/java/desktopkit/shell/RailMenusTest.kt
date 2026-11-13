package desktopkit.shell

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The design audit's medium finding 15: where there is no menu bar (the rail), its menus are behind a "≡". */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1000dp-h700dp")
class RailMenusTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theMenusAreReachableOnTheRail() {
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                TestShell(WidthClass.Expanded, content = { route, _ -> Text("ekran-$route") })
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Menus").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Düzen").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Düzen…").assertExists()
    }
}
