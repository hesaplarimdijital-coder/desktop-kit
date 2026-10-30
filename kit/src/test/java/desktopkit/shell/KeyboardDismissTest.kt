package desktopkit.shell

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import desktopkit.TestShell
import desktopkit.components.DeskButton
import desktopkit.components.DeskTextField
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * With a finger the keyboard steps aside when the user moves on: a scroll or a tap outside the text
 * fields takes the focus from the field. A tap on another field moves it there, a tap on a control
 * that keeps the keyboard leaves it where it is, and so does a mouse wheel.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class KeyboardDismissTest {

    // On a device the first finger on the glass puts the window in touch mode, where taking the focus
    // from a field leaves nothing focused. Robolectric's window starts in keyboard mode, where the
    // system hands the focus straight back to the first field, so it starts the way a finger leaves it.
    @get:Rule(order = 0)
    val touchMode = object : ExternalResource() {
        override fun before() = InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var text by mutableStateOf("Kış")
    private var other by mutableStateOf("")
    private var sent = 0
    private val list = LazyListState()
    private lateinit var host: ShellOverlayHost

    private fun screen() {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Touch) {
                TestShell(WidthClass.Compact, content = { _, _ ->
                    host = LocalShellOverlayHost.current!!
                    Column(Modifier.fillMaxSize()) {
                        DeskTextField(text, { text = it }, Modifier.fillMaxWidth().padding(16.dp).testTag("field"), hint = "Ara")
                        DeskTextField(other, { other = it }, Modifier.fillMaxWidth().padding(16.dp).testTag("other"), hint = "Not")
                        DeskButton("Kaydet", {}, Modifier.padding(16.dp).testTag("button"))
                        DeskButton("Gönder", { sent++ }, Modifier.padding(16.dp).keepsKeyboardOpen().testTag("send"))
                        LazyColumn(Modifier.fillMaxSize().testTag("list"), state = list) {
                            items(80) { Text("Satır $it", Modifier.padding(16.dp)) }
                        }
                    }
                })
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue("the window is in touch mode", compose.activity.window.decorView.isInTouchMode) }
        compose.onNodeWithTag("field").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("field").assertIsFocused()
        compose.runOnIdle { assertTrue("the shell knows a field has the focus", host.textFields.anyFocused) }
    }

    private fun center(tag: String): Offset = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow.center

    @Test
    fun aTapElsewhereClosesTheKeyboard() {
        screen()
        compose.runOnIdle {
            assertTrue("the field is where the shell looks", host.textFields.contains(center("field")))
            assertFalse("the button is not on a field", host.textFields.contains(center("button")))
        }
        compose.onNodeWithTag("button").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("field").assertIsNotFocused()
    }

    @Test
    fun aScrollClosesTheKeyboard() {
        screen()
        // in the middle of the list: its end lies under the bottom bar
        compose.onNodeWithTag("list").performTouchInput {
            swipe(center + Offset(0f, height / 4f), center - Offset(0f, height / 4f), durationMillis = 500)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("field").assertIsNotFocused()
    }

    @Test
    fun aTapOnAnotherFieldMovesTheFocusThere() {
        screen()
        compose.onNodeWithTag("other").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("other").assertIsFocused()
    }

    @Test
    fun aTapOnASendButtonKeepsTheKeyboard() {
        screen()
        compose.onNodeWithTag("send").performClick()
        compose.waitForIdle()
        assertEquals("the button did its job", 1, sent)
        compose.onNodeWithTag("field").assertIsFocused()
    }

    @Test
    fun aMouseWheelKeepsTheField() {
        // a mouse click is Compose's own business (it takes the focus from a field clicked outside)
        screen()
        compose.onNodeWithTag("list").performMouseInput {
            moveTo(center)
            scroll(6f)
        }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue("the wheel scrolled the list", list.firstVisibleItemIndex > 0) }
        compose.onNodeWithTag("field").assertIsFocused()
    }
}
