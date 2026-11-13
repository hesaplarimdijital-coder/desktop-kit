package desktopkit.shell

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.TestShell
import desktopkit.components.DeskIcons
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

/** DeskMail's request 5: the screen's main action floats over the phone's bottom bar, tops the rail and the sidebar. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp")
class PrimaryActionTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var widthClass by mutableStateOf(WidthClass.Compact)
    private var clicks = 0

    private fun shell() {
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = if (widthClass == WidthClass.Compact) InputMode.Touch else InputMode.Pointer) {
                TestShell(widthClass, content = { route, _ ->
                    if (route == "home") ShellPrimaryAction("Yeni ileti", DeskIcons.Plus) { clicks++ }
                    Text("ekran-$route")
                })
            }
        }
        compose.waitForIdle()
    }

    private val button get() = compose.onNode((hasText("Yeni ileti") or hasContentDescription("Yeni ileti")) and hasClickAction())

    @Test
    fun onAPhoneItFloatsAboveTheBottomBarAndGoesWithItsScreen() {
        shell()
        val fab = button.fetchSemanticsNode().boundsInRoot
        val tab = compose.onNode(hasText("Ana sayfa") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertTrue("above the bar: ${fab.bottom} <= ${tab.top}", fab.bottom <= tab.top)
        assertTrue("at the end: ${fab.right} > ${tab.right}", fab.right > tab.right)
        button.performClick()
        compose.waitForIdle()
        assertEquals(1, clicks)
        // another screen has none
        compose.onNode(hasText("Belgeler") and hasClickAction()).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("ekran-documents").assertExists()
        button.assertDoesNotExist()
    }

    @Test
    fun onTheRailItIsAnIconAtTheTop() {
        widthClass = WidthClass.Medium
        shell()
        compose.onNode(hasContentDescription("Yeni ileti") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun inTheSidebarItIsAButtonWithItsLabel() {
        widthClass = WidthClass.Large
        shell()
        compose.onNode(hasText("Yeni ileti") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertEquals(1, clicks)
    }
}
