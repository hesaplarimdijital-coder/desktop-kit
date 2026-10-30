package desktopkit.components

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.TestShell
import desktopkit.shell.WidthClass
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Where a menu opens near the bottom of the window, and when menus show keyboard shortcuts: with a
 * mouse or a hardware keyboard, not on a phone held in the hand.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class MenuHintsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val window = IntSize(400, 1000)
    private val menu = IntSize(220, 200)

    @Test
    fun aDropDownNearTheBottomOpensAboveItsButton() {
        // a 48 px button 52 px above the window's bottom; the menu was asked for under it
        val button = IntRect(20, 900, 180, 948)
        val position = ClampedOffsetPositionProvider(IntOffset(0, button.height + 2))
            .calculatePosition(button, window, LayoutDirection.Ltr, menu)
        assertEquals("the menu ends at the button's top", button.top - menu.height, position.y)
        assertEquals(button.left, position.x)
    }

    @Test
    fun aMenuAtAPointNearTheBottomOpensAboveThePoint() {
        // a right click 20 px into a row near the bottom
        val row = IntRect(0, 880, 400, 960)
        val position = ClampedOffsetPositionProvider(IntOffset(150, 20))
            .calculatePosition(row, window, LayoutDirection.Ltr, menu)
        assertEquals(row.top + 20 - menu.height, position.y)
        assertEquals(150, position.x)
    }

    @Test
    fun aMenuThatFitsOpensUnderItsButton() {
        val button = IntRect(20, 100, 180, 148)
        val position = ClampedOffsetPositionProvider(IntOffset(0, button.height + 2))
            .calculatePosition(button, window, LayoutDirection.Ltr, menu)
        assertEquals(button.bottom + 2, position.y)
    }

    private fun sheet(keyboard: Boolean) {
        val configuration = Configuration(compose.activity.resources.configuration).apply {
            this.keyboard = if (keyboard) Configuration.KEYBOARD_QWERTY else Configuration.KEYBOARD_NOKEYS
            hardKeyboardHidden = if (keyboard) Configuration.HARDKEYBOARDHIDDEN_NO else Configuration.HARDKEYBOARDHIDDEN_YES
        }
        compose.setContent {
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                DesktopKitTheme(inputMode = InputMode.Touch) {
                    TestShell(WidthClass.Compact) { _, _ ->
                        MoreSheet(
                            visible = true,
                            onDismiss = {},
                            groups = listOf(ActionGroup(null, listOf(MenuEntry.Item("Ara", {}, shortcut = "Ctrl+K")))),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aPhoneInTheHandShowsNoShortcuts() {
        sheet(keyboard = false)
        compose.onNodeWithText("Ara").assertExists()
        compose.onNodeWithText("Ctrl+K").assertDoesNotExist()
    }

    @Test
    fun aKeyboardBringsTheShortcutsBack() {
        sheet(keyboard = true)
        compose.onNodeWithText("Ctrl+K").assertExists()
    }
}
