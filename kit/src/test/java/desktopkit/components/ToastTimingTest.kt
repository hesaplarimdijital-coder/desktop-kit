package desktopkit.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A toast's time: one of its own (DeskMail's "undo send" lasts what the user set, request 21), and
 * standing still while a pointer rests on the toast, so reaching for "Undo" does not lose it.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1000dp-h700dp-mdpi")
class ToastTimingTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val toasts = ToastState()
    private lateinit var scope: CoroutineScope

    private fun host() {
        compose.setContent {
            scope = rememberCoroutineScope()
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                Box(Modifier.fillMaxSize()) { ToastHost(toasts, Modifier.align(Alignment.BottomCenter)) }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun advance(millis: Long) {
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
    }

    @Test
    fun aToastLastsTheTimeItIsGiven() {
        host()
        compose.runOnIdle { scope.launch { toasts.show("İleti gönderiliyor", actionLabel = "Geri al", durationMillis = 3000L) } }
        advance(2800)
        compose.onNodeWithText("İleti gönderiliyor").assertExists()
        advance(800)
        compose.onNodeWithText("İleti gönderiliyor").assertDoesNotExist()
    }

    @Test
    fun aRestingPointerHoldsItsTime() {
        host()
        compose.runOnIdle { scope.launch { toasts.show("Çöpe taşındı", actionLabel = "Geri al") } }
        advance(500)
        compose.onNodeWithText("Çöpe taşındı").performMouseInput { moveTo(center) }
        // far past its 10 s while the pointer rests on it
        advance(15_000)
        compose.onNodeWithText("Çöpe taşındı").assertExists()
        compose.onNodeWithText("Çöpe taşındı").performMouseInput { moveTo(androidx.compose.ui.geometry.Offset(-500f, -500f)) }
        // the rest of its time: about 9.5 s
        advance(8_000)
        compose.onNodeWithText("Çöpe taşındı").assertExists()
        advance(2_500)
        compose.onNodeWithText("Çöpe taşındı").assertDoesNotExist()
    }
}
