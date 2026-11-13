package desktopkit.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertCountEquals
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.input.InputMode as ComposeInputMode

/**
 * Icon buttons name themselves: after half a second's rest of the mouse, or as soon as the keyboard
 * focus reaches them; the next one along a toolbar shows at once (the design audit's high finding 4).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1000dp-h700dp-mdpi")
class TooltipTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @org.junit.Before
    fun forgetEarlierTooltips() = TooltipTiming.reset()

    private lateinit var inputModeManager: InputModeManager

    private fun toolbar() {
        compose.setContent {
            inputModeManager = LocalInputModeManager.current
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                Row {
                    DeskIconButton(DeskIcons.Delete, "Çöpe taşı", {}, shortcut = "Del")
                    DeskIconButton(DeskIcons.Info, "Özellikler", {})
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun advance(millis: Long) {
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
    }

    /** The button's own name is its content description; the tooltip adds a second node with the text. */
    private fun shown(text: String) = compose.onAllNodesWithText(text)

    @Test
    fun aRestingMouseShowsTheNameAndTheNeighbourFollowsAtOnce() {
        toolbar()
        compose.onNodeWithContentDescription("Çöpe taşı").performMouseInput { moveTo(center) }
        advance(300)
        shown("Çöpe taşı").assertCountEquals(0)
        advance(300)
        shown("Çöpe taşı").assertCountEquals(1)
        shown("Del").assertCountEquals(1)
        compose.onNodeWithContentDescription("Özellikler").performMouseInput { moveTo(center) }
        advance(150)
        shown("Özellikler").assertCountEquals(1)
    }

    @Test
    fun theKeyboardFocusShowsTheName() {
        toolbar()
        // a keyboard's focus: buttons take the focus in the keyboard's input mode (DeX, a keyboard cover)
        compose.runOnIdle { inputModeManager.requestInputMode(ComposeInputMode.Keyboard) }
        compose.onNodeWithContentDescription("Özellikler").performSemanticsAction(SemanticsActions.RequestFocus)
        advance(600)
        shown("Özellikler").assertCountEquals(1)
    }
}
