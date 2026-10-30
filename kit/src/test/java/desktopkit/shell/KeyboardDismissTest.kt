package desktopkit.shell

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.TestShell
import desktopkit.components.DeskButton
import desktopkit.components.DeskTextField
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * With a finger the keyboard steps aside when the user moves on: a scroll or a tap outside the text
 * fields takes the focus from the field. A tap on another field moves it there; a mouse click
 * elsewhere leaves it where it is.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class KeyboardDismissTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var text by mutableStateOf("Kış")
    private var other by mutableStateOf("")

    private fun screen() {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Touch) {
                TestShell(WidthClass.Compact, content = { _, _ ->
                    Column(Modifier.fillMaxSize()) {
                        DeskTextField(text, { text = it }, Modifier.fillMaxWidth().padding(16.dp).testTag("field"), hint = "Ara")
                        DeskTextField(other, { other = it }, Modifier.fillMaxWidth().padding(16.dp).testTag("other"), hint = "Not")
                        DeskButton("Kaydet", {}, Modifier.padding(16.dp).testTag("button"))
                        LazyColumn(Modifier.fillMaxSize().testTag("list")) {
                            items(80) { Text("Satır $it", Modifier.padding(16.dp)) }
                        }
                    }
                })
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("field").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("field").assertIsFocused()
    }

    @Test
    fun aTapElsewhereClosesTheKeyboard() {
        screen()
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
    fun aMouseClickElsewhereKeepsTheField() {
        screen()
        compose.onNodeWithTag("button").performMouseInput { click() }
        compose.waitForIdle()
        compose.onNodeWithTag("field").assertIsFocused()
    }
}
