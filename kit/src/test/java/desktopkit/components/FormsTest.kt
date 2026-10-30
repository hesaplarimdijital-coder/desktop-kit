package desktopkit.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The form, feedback and structure components of 2.0 (K1–K3): what they report and how they toggle. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "tr")
class FormsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun numberFieldReportsOnlyNumbersInRange() {
        var value = 100
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Pointer) {
                NumberField(value, { value = it }, Modifier.testTag("buffer"), min = 0, max = 5000, suffix = "ms")
            }
        }
        compose.onNodeWithTag("buffer").performTextReplacement("9000")
        assertEquals("out of range: not reported", 100, value)
        // the range under the field is what TalkBack hears as the error
        compose.onNodeWithTag("buffer").assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "0 ile 5.000 arasında olmalı"))

        compose.onNodeWithTag("buffer").performTextReplacement("4500")
        assertEquals(4500, value)
        compose.onNodeWithTag("buffer").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
    }

    @Test
    fun numberFieldKeepsItsPlaceWhenTheRangeShows() {
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Pointer) {
                Row {
                    NumberField(100, {}, Modifier.testTag("buffer"), min = 0, max = 5000, suffix = "ms")
                    Box(Modifier.size(20.dp).testTag("next"))
                }
            }
        }
        val field = compose.onNodeWithTag("buffer").getBoundsInRoot()
        val next = compose.onNodeWithTag("next").getBoundsInRoot()
        compose.onNodeWithTag("buffer").performTextReplacement("9000")
        // the line is wider than the field: it reaches toward the start, the field and its neighbour stay
        val fieldAfter = compose.onNodeWithTag("buffer").getBoundsInRoot()
        assertEquals(field.left, fieldAfter.left)
        assertEquals(field.right, fieldAfter.right)
        assertEquals(next.left, compose.onNodeWithTag("next").getBoundsInRoot().left)
    }

    @Test
    fun numberFieldKeepsWhatIsTypedWhileTheValueComesBackLate() {
        // the value reaches the field a keystroke late, as state passed through a Flow does
        var shown by mutableIntStateOf(1)
        val reported = mutableListOf<Int>()
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Pointer) {
                NumberField(shown, { reported += it }, Modifier.testTag("count"), min = 0, max = 99999)
            }
        }
        compose.onNodeWithTag("count").performTextReplacement("12")
        compose.onNodeWithTag("count").performTextReplacement("123")
        assertEquals(listOf(12, 123), reported)
        shown = 12
        compose.waitForIdle()
        compose.onNodeWithTag("count").assert(hasText("123"))
        shown = 123
        compose.waitForIdle()
        compose.onNodeWithTag("count").assert(hasText("123"))
        // a value set from outside replaces the text
        shown = 7
        compose.waitForIdle()
        compose.onNodeWithTag("count").assert(hasText("7"))
    }

    @Test
    fun optionalNumberFieldKeepsWhatIsTypedWhileTheValueComesBackLate() {
        var shown by mutableStateOf<Long?>(null)
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Pointer) {
                OptionalNumberField(shown, {}, Modifier.testTag("size"))
            }
        }
        compose.onNodeWithTag("size").performTextReplacement("5")
        compose.onNodeWithTag("size").performTextReplacement("50")
        shown = 5
        compose.waitForIdle()
        compose.onNodeWithTag("size").assert(hasText("50"))
        // a reset from outside
        shown = null
        compose.waitForIdle()
        compose.onNodeWithTag("size").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }

    @Test
    fun enterAndTheKeyboardsActionKeyRunTheAction() {
        var searches = 0
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Pointer) {
                DeskTextField("Kış", {}, Modifier.testTag("search"), imeAction = ImeAction.Search, onImeAction = { searches++ })
            }
        }
        compose.onNodeWithTag("search").requestFocus()
        compose.onNodeWithTag("search").performKeyInput { pressKey(Key.Enter) }
        assertEquals("a hardware Enter, once", 1, searches)
        compose.onNodeWithTag("search").performImeAction()
        assertEquals(2, searches)
    }

    @Test
    fun withoutAnActionTheKeyboardsKeyDoesItsOwnJob() {
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Touch) {
                Column {
                    DeskTextField("", {}, Modifier.testTag("first"), imeAction = ImeAction.Next)
                    DeskTextField("", {}, Modifier.testTag("second"))
                }
            }
        }
        compose.onNodeWithTag("first").requestFocus()
        compose.onNodeWithTag("first").performImeAction()
        // Next moved on to the following field
        compose.onNodeWithTag("second").assertIsFocused()
    }

    @Test
    fun aFingerReachesAFieldButtonAroundIt() {
        var text = "Mavi Hat"
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Touch) {
                DeskTextField(text, { text = it }, Modifier.testTag("field").width(240.dp), clearable = true)
            }
        }
        val field = compose.onNodeWithTag("field").getBoundsInRoot()
        val clear = compose.onNodeWithContentDescription("Temizle").getBoundsInRoot()
        assertEquals(32f, (clear.right - clear.left).value, 0.5f)
        // a touch 4 dp past the button's end edge, still within the 48 dp a finger is given
        compose.onNodeWithTag("field").performTouchInput {
            click(Offset((clear.right + 4.dp - field.left).toPx(), ((clear.top + clear.bottom) / 2 - field.top).toPx()))
        }
        assertEquals("", text)
    }

    @Test
    fun numberFieldStepsWithTheArrowKeys() {
        var value = 10
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Pointer) {
                NumberField(value, { value = it }, Modifier.testTag("count"), min = 0, max = 11, step = 1)
            }
        }
        compose.onNodeWithTag("count").requestFocus()
        compose.onNodeWithTag("count").performKeyInput { pressKey(Key.DirectionUp) }
        assertEquals(11, value)
        compose.onNodeWithTag("count").performKeyInput { pressKey(Key.DirectionUp) }
        assertEquals("stops at the maximum", 11, value)
        compose.onNodeWithTag("count").performKeyInput { pressKey(Key.DirectionDown) }
        assertEquals(10, value)
    }

    @Test
    fun textFieldClearsAndRevealsPasswords() {
        var text = "gizli"
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Touch) {
                Column {
                    DeskTextField(text, { text = it }, Modifier.testTag("password"), password = true)
                    DeskTextField("Mavi Hat", { text = it }, Modifier.testTag("search"), clearable = true)
                }
            }
        }
        compose.onNodeWithContentDescription("Parolayı göster").performClick()
        compose.onNodeWithContentDescription("Parolayı gizle").assertExists()
        compose.onNodeWithContentDescription("Temizle").performClick()
        assertEquals("", text)
    }

    @Test
    fun checkboxAndSwitchToggle() {
        var checked = false
        var switched = true
        var rowChecked = false
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Touch) {
                Column {
                    DeskCheckbox(checked, { checked = it }, Modifier.testTag("checkbox"))
                    DeskSwitch(switched, { switched = it }, Modifier.testTag("switch"))
                    SwitchRow("Ses düzeyini eşitle", checked = false, onCheckedChange = { rowChecked = it }, subtitle = "ReplayGain etiketleri okunur")
                }
            }
        }
        compose.onNodeWithTag("checkbox").assertIsOff().performClick()
        assertTrue(checked)
        compose.onNodeWithTag("switch").assertIsOn().performClick()
        assertFalse(switched)
        // the whole line is one switch: a tap on its text toggles it
        compose.onNodeWithText("Ses düzeyini eşitle", substring = true).performClick()
        assertTrue(rowChecked)
    }

    @Test
    fun threeStateCheckbox() {
        var clicks = 0
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Pointer) {
                CheckboxRow("Kayıplı (2 / 3)", ToggleableState.Indeterminate, onClick = { clicks++ })
            }
        }
        compose.onNodeWithText("Kayıplı (2 / 3)", substring = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Indeterminate))
            .performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun toastsWaitTheirTurn() = runTest {
        val toasts = DeskToastState()
        val first = async { toasts.show("Pencere sıraya eklendi", actionLabel = "Geri al") }
        runCurrent()
        assertEquals("Pencere sıraya eklendi", toasts.current?.message)
        assertEquals(ToastDuration.Long, toasts.current?.duration)
        val second = async { toasts.show("Kopyalandı") }
        runCurrent()
        assertEquals("the second waits for the first", "Pencere sıraya eklendi", toasts.current?.message)
        toasts.current!!.performAction()
        runCurrent()
        assertEquals(ToastResult.ActionPerformed, first.await())
        assertEquals("Kopyalandı", toasts.current?.message)
        assertEquals(ToastDuration.Short, toasts.current?.duration)
        toasts.dismissCurrent()
        assertEquals(ToastResult.Dismissed, second.await())
        assertNull(toasts.current)
    }

    @Test
    fun toastActionButton() {
        val toasts = DeskToastState()
        var result: ToastResult? = null
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Pointer) {
                ToastHost(toasts)
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    result = toasts.show("Çöpe taşındı", actionLabel = "Geri al", duration = ToastDuration.Indefinite)
                }
            }
        }
        compose.onNodeWithText("Geri al").performClick()
        compose.waitForIdle()
        assertEquals(ToastResult.ActionPerformed, result)
    }
}
