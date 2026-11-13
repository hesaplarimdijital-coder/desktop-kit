package desktopkit.shell

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
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

/**
 * A count or a dot on the navigation icons where there is no room for text: the phone's bottom bar,
 * the rail and the collapsed sidebar (DeskMail's request 1: the unread count of "Inbox" was nowhere
 * on a phone). TalkBack reads it after the entry's name.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class NavIconBadgeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var widthClass by mutableStateOf(WidthClass.Compact)
    private var collapsed by mutableStateOf(false)

    private fun shell() {
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                TestShell(
                    widthClass,
                    sidebarCollapsed = collapsed,
                    iconBadges = mapOf("home" to NavIconBadge(4, "4 okunmamış"), "network" to NavIconBadge(120), "settings" to NavIconBadge()),
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theBottomBarShowsTheCountAndSaysIt() {
        shell()
        compose.onNodeWithContentDescription("Ana sayfa, 4 okunmamış", substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("4", useUnmergedTree = true).assertExists()
        // "Ağ" is behind "More": More carries a dot and says so
        compose.onNodeWithContentDescription("More, New", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test
    fun theRailShowsIt() {
        widthClass = WidthClass.Medium
        shell()
        compose.onNodeWithContentDescription("Ana sayfa, 4 okunmamış", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test
    fun theCollapsedSidebarShowsItTheOpenOneDoesNot() {
        widthClass = WidthClass.Large
        collapsed = true
        shell()
        compose.onNodeWithContentDescription("Ana sayfa, 4 okunmamış").assertExists()
        compose.onNodeWithText("99+", useUnmergedTree = true).assertExists()
        collapsed = false
        compose.waitForIdle()
        compose.onNodeWithText("99+", useUnmergedTree = true).assertDoesNotExist()
    }
}
