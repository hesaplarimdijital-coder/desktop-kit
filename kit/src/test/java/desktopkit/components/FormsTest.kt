package desktopkit.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
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
        compose.onNodeWithTag("buffer").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        compose.onNodeWithText("0 ile 5.000 arasında olmalı").assertExists()

        compose.onNodeWithTag("buffer").performTextReplacement("4500")
        assertEquals(4500, value)
        compose.onNodeWithText("0 ile 5.000 arasında olmalı").assertDoesNotExist()
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
