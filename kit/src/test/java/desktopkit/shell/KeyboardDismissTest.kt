package desktopkit.shell

import android.content.res.Configuration
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
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
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
 * With a finger the on-screen keyboard steps aside when the user moves on: a scroll or a tap outside
 * the text fields takes the focus from the field. A tap on another field moves it there without a
 * step in between, a tap on a control that keeps the keyboard leaves it where it is, and so do a
 * scroll inside a long field and a mouse wheel. Without the on-screen keyboard (none shown, or a
 * keyboard cover typing) nothing changes.
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
    private var notes by mutableStateOf((1..12).joinToString("\n") { "Satır $it" })
    private var sent = 0
    private val list = LazyListState()
    private lateinit var host: ShellOverlayHost
    private lateinit var view: View

    /** Whether the window's focus went nowhere (as clearing it does) since the watch began. */
    private var focusLeft = false

    /** What the system sends when the on-screen keyboard opens ([bottom] px tall) or closes (0). */
    private fun keyboard(bottom: Int) {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, bottom))
            .setVisible(WindowInsetsCompat.Type.ime(), bottom > 0)
            .build()
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(view, insets) }
        compose.waitForIdle()
    }

    private fun screen(onScreenKeyboard: Boolean = true, keyboardCover: Boolean = false) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        val configuration = Configuration(compose.activity.resources.configuration).apply {
            if (keyboardCover) {
                keyboard = Configuration.KEYBOARD_QWERTY
                hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_NO
            }
        }
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                DesktopKitTheme(inputMode = InputMode.Touch) {
                    TestShell(WidthClass.Compact, content = { _, _ ->
                        host = LocalShellOverlayHost.current!!
                        Column(Modifier.fillMaxSize()) {
                            DeskTextField(text, { text = it }, Modifier.fillMaxWidth().padding(16.dp).testTag("field"), hint = "Ara")
                            DeskTextField(other, { other = it }, Modifier.fillMaxWidth().padding(16.dp).testTag("other"), hint = "Not")
                            DeskTextField(notes, { notes = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("notes"),
                                singleLine = false, maxLines = 3)
                            DeskButton("Kaydet", {}, Modifier.padding(16.dp).testTag("button"))
                            DeskButton("Gönder", { sent++ }, Modifier.padding(horizontal = 16.dp).keepsKeyboardOpen().testTag("send"))
                            LazyColumn(Modifier.fillMaxSize().testTag("list"), state = list) {
                                items(80) { Text("Satır $it", Modifier.padding(16.dp)) }
                            }
                        }
                    })
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue("the window is in touch mode", compose.activity.window.decorView.isInTouchMode) }
        compose.onNodeWithTag("field").performClick()
        compose.waitForIdle()
        if (onScreenKeyboard) keyboard(300)
        compose.onNodeWithTag("field").assertIsFocused()
        compose.runOnIdle { assertTrue("the shell knows a field has the focus", host.textFields.anyFocused) }
    }

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
        swipeUp("list")
        compose.onNodeWithTag("field").assertIsNotFocused()
    }

    @Test
    fun aTapOnAnotherFieldMovesTheFocusThere() {
        screen()
        // taking the focus first and handing it to the other field would close and reopen the keyboard
        compose.runOnUiThread {
            view.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus -> if (newFocus == null) focusLeft = true }
        }
        compose.onNodeWithTag("other").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("other").assertIsFocused()
        assertFalse("the focus went straight to the other field", focusLeft)
    }

    @Test
    fun aScrollInsideALongFieldKeepsTheKeyboard() {
        screen()
        compose.onNodeWithTag("notes").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("notes").assertIsFocused()
        swipeUp("notes")
        compose.onNodeWithTag("notes").assertIsFocused()
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

    @Test
    fun withoutTheOnScreenKeyboardTheFieldKeepsTheFocus() {
        screen(onScreenKeyboard = false)
        compose.onNodeWithTag("button").performClick()
        compose.waitForIdle()
        swipeUp("list")
        compose.onNodeWithTag("field").assertIsFocused()
    }

    @Test
    fun withAKeyboardCoverTypingGoesOn() {
        // the Tab S11 Ultra's Book Cover: scrolling the results must not take the search field's focus
        screen(keyboardCover = true)
        swipeUp("list")
        compose.onNodeWithTag("button").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("field").assertIsFocused()
    }

    private fun center(tag: String): Offset = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow.center

    /** A finger's swipe up in the middle of [tag] (a list's ends lie under the bars). */
    private fun swipeUp(tag: String) {
        compose.onNodeWithTag(tag).performTouchInput {
            swipe(center + Offset(0f, height / 4f), center - Offset(0f, height / 4f), durationMillis = 500)
        }
        compose.waitForIdle()
    }
}
