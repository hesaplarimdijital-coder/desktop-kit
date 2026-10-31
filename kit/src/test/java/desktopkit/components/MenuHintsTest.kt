package desktopkit.components

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.TestShell
import desktopkit.input.showShortcutHints
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
        val provider = ClampedOffsetPositionProvider(IntOffset(0, button.height + 2))
        val position = provider.calculatePosition(button, window, LayoutDirection.Ltr, menu)
        assertEquals(button.bottom + 2, position.y)
        assertEquals("it grows from its top-left corner", TransformOrigin(0f, 0f), provider.origin)
    }

    /** The corner a menu grows from follows where it was moved to: its bottom when flipped above, under the finger when pushed in from the edge. */
    @Test
    fun aMovedMenuGrowsFromThePointItWasAskedFor() {
        val button = IntRect(20, 900, 180, 948)
        val flipped = ClampedOffsetPositionProvider(IntOffset(0, button.height + 2))
        flipped.calculatePosition(button, window, LayoutDirection.Ltr, menu)
        assertEquals("flipped above: from its bottom-left corner", TransformOrigin(0f, 1f), flipped.origin)
        // a right click 300 px from the left in a 400 px window: the 220 px menu is pushed left by 120 px
        val row = IntRect(0, 100, 400, 180)
        val pushed = ClampedOffsetPositionProvider(IntOffset(300, 20))
        val position = pushed.calculatePosition(row, window, LayoutDirection.Ltr, menu)
        assertEquals(180, position.x)
        assertEquals("pushed in: from under the finger", 120f / 220f, pushed.origin.pivotFractionX, 0.001f)
        assertEquals(0f, pushed.origin.pivotFractionY, 0.001f)
    }

    private fun configuration(keyboard: Boolean) = Configuration(compose.activity.resources.configuration).apply {
        this.keyboard = if (keyboard) Configuration.KEYBOARD_QWERTY else Configuration.KEYBOARD_NOKEYS
        hardKeyboardHidden = if (keyboard) Configuration.HARDKEYBOARDHIDDEN_NO else Configuration.HARDKEYBOARDHIDDEN_YES
    }

    private fun sheet(keyboard: Boolean) {
        val configuration = configuration(keyboard)
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

    @Test
    fun hintsFollowAMouseOrAKeyboard() {
        var fingerOnly: Boolean? = null
        var mouse: Boolean? = null
        var keyboardCover: Boolean? = null
        val plain = configuration(keyboard = false)
        val cover = configuration(keyboard = true)
        compose.setContent {
            CompositionLocalProvider(LocalConfiguration provides plain) {
                DesktopKitTheme(inputMode = InputMode.Touch) { fingerOnly = showShortcutHints() }
                DesktopKitTheme(inputMode = InputMode.Pointer) { mouse = showShortcutHints() }
            }
            CompositionLocalProvider(LocalConfiguration provides cover) {
                DesktopKitTheme(inputMode = InputMode.Touch) { keyboardCover = showShortcutHints() }
            }
        }
        compose.waitForIdle()
        assertEquals("a finger alone", false, fingerOnly)
        assertEquals("a mouse", true, mouse)
        assertEquals("a keyboard cover on a touch layout", true, keyboardCover)
    }
}
