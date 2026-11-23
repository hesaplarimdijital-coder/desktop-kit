package desktopkit.editor

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import desktopkit.components.DeskButton
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
import org.robolectric.annotation.GraphicsMode

/**
 * The editor among other elements, with a keyboard: it is one Tab stop, Tab types inside it, and
 * Esc followed by Tab or Shift+Tab leaves it for the element after or before it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w800dp-h600dp-mdpi")
class CodeEditorFocusTest {

    // A key pressed on a device takes the window out of touch mode, where a button can have the keyboard
    // focus. The keys of these tests go to the view directly, so the window starts the way a key leaves it.
    @get:Rule(order = 0)
    val keyboardMode = object : ExternalResource() {
        override fun before() = InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val document = CodeEditorState("bir\niki")

    private fun show() {
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Pointer) {
                Column(Modifier.fillMaxSize()) {
                    DeskButton("Derle", {}, Modifier.testTag("before"))
                    CodeEditor(document, Modifier.fillMaxWidth().height(300.dp).testTag("editor"))
                    DeskButton("Çalıştır", {}, Modifier.testTag("after"))
                }
            }
        }
        compose.waitForIdle()
    }

    private fun keys(vararg presses: Pair<Int, Int>) {
        val view = compose.editorView()
        compose.runOnUiThread { for ((key, meta) in presses) view.press(key, meta) }
        compose.waitForIdle()
    }

    @Test
    fun tabFromTheElementBeforeEntersTheEditor() {
        show()
        compose.onNodeWithTag("before").requestFocus()
        compose.waitForIdle()
        val view = compose.editorView()
        compose.runOnUiThread { view.requestFocus() }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(view.hasFocus()) }
        keys(KeyEvent.KEYCODE_TAB to 0)
        assertEquals("Tab types in the editor", "\tbir\niki", document.text)
        compose.runOnIdle { assertTrue("and the focus stays", view.hasFocus()) }
    }

    @Test
    fun escapeThenTabLeavesForTheNextElement() {
        show()
        val view = compose.editorView()
        compose.runOnUiThread { view.requestFocus() }
        keys(KeyEvent.KEYCODE_ESCAPE to 0, KeyEvent.KEYCODE_TAB to 0)
        assertEquals("Tab typed nothing", "bir\niki", document.text)
        compose.onNodeWithTag("after").assertIsFocused()
        compose.runOnIdle { assertFalse(view.hasFocus()) }
    }

    @Test
    fun escapeThenShiftTabLeavesForTheElementBefore() {
        show()
        val view = compose.editorView()
        compose.runOnUiThread { view.requestFocus() }
        keys(KeyEvent.KEYCODE_ESCAPE to 0, KeyEvent.KEYCODE_TAB to KeyEvent.META_SHIFT_ON)
        compose.onNodeWithTag("before").assertIsFocused()
        compose.runOnIdle { assertFalse(view.hasFocus()) }
    }

    @Test
    fun anotherKeyAfterEscapeCallsTheLeavingOff() {
        show()
        val view = compose.editorView()
        compose.runOnUiThread { view.requestFocus() }
        keys(KeyEvent.KEYCODE_ESCAPE to 0, KeyEvent.KEYCODE_DPAD_RIGHT to 0, KeyEvent.KEYCODE_TAB to 0)
        assertEquals("b\tir\niki", document.text)
        compose.runOnIdle { assertTrue(view.hasFocus()) }
    }
}
