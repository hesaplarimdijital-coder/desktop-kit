package desktopkit.editor

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import desktopkit.TestShell
import desktopkit.components.DeskButton
import desktopkit.shell.LocalShellOverlayHost
import desktopkit.shell.ShellOverlayHost
import desktopkit.shell.WidthClass
import desktopkit.shell.rememberShellContentPadding
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
 * The editor in the shell on a phone, with the on-screen keyboard up: it is one of the shell's text fields, so
 * a finger that scrolls its text or taps in it leaves the keyboard open, and a tap elsewhere takes the focus
 * (and the keyboard) away, as for every field of the kit.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class CodeEditorKeyboardTest {

    // a finger's first touch puts a device in touch mode, where taking the focus leaves nothing focused
    @get:Rule(order = 0)
    val touchMode = object : ExternalResource() {
        override fun before() = InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val document = CodeEditorState((1..300).joinToString("\n") { "satır $it" })
    private lateinit var host: ShellOverlayHost
    private lateinit var window: View

    /** What the system sends when the on-screen keyboard opens ([bottom] px tall). */
    private fun keyboard(bottom: Int) {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, bottom))
            .setVisible(WindowInsetsCompat.Type.ime(), bottom > 0)
            .build()
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(window, insets) }
        compose.waitForIdle()
    }

    private fun screen() {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            window = LocalView.current
            DesktopKitTheme(inputMode = InputMode.Touch) {
                TestShell(WidthClass.Compact, content = { _, _ ->
                    host = LocalShellOverlayHost.current!!
                    Column(Modifier.fillMaxSize()) {
                        DeskButton("Derle", {}, Modifier.padding(16.dp).testTag("button"))
                        CodeEditor(document, Modifier.fillMaxWidth().weight(1f).testTag("editor"),
                            contentPadding = rememberShellContentPadding())
                    }
                })
            }
        }
        compose.waitForIdle()
        val view = compose.editorView()
        compose.runOnUiThread {
            view.requestFocus()
            document.select(document.lineStart(3))
        }
        keyboard(300)
        compose.runOnIdle {
            assertTrue(view.hasFocus())
            assertTrue("the shell counts the editor as a focused text field", host.textFields.anyFocused)
        }
    }

    @Test
    fun scrollingTheTextKeepsTheKeyboard() {
        screen()
        compose.onNodeWithTag("editor").performTouchInput {
            swipe(center + Offset(0f, height / 4f), center - Offset(0f, height / 4f), durationMillis = 500)
        }
        compose.waitForIdle()
        val view = compose.editorView()
        compose.runOnIdle {
            assertTrue("the text scrolled", view.scrollY > 0)
            assertTrue("the editor kept the focus, and so the keyboard", view.hasFocus())
            assertEquals("the cursor stayed where it was", document.lineStart(3), document.cursor)
        }
    }

    @Test
    fun aTapInTheTextKeepsTheKeyboard() {
        screen()
        compose.onNodeWithTag("editor").performTouchInput { click(center) }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(compose.editorView().hasFocus()) }
    }

    @Test
    fun aTapElsewhereTakesTheKeyboardAway() {
        screen()
        compose.onNodeWithTag("button").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertFalse("the editor gave up the focus", compose.editorView().hasFocus())
            assertFalse(host.textFields.anyFocused)
        }
    }
}
