package desktopkit.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.LocalMotionTier
import desktopkit.theme.MotionTier
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A toast swiped away on a phone carries on the way it was going at the finger's speed; wider up it goes at once, as before. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class ToastSwipeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val toasts = ToastState()

    private fun toast(tier: MotionTier) {
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Touch) {
                CompositionLocalProvider(LocalMotionTier provides tier) {
                    Box(Modifier.fillMaxSize()) { ToastHost(toasts, Modifier.align(Alignment.BottomCenter)) }
                    LaunchedEffect(Unit) { toasts.show("Çöpe taşındı", duration = ToastDuration.Indefinite) }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun onAPhoneASwipedToastCarriesOnBeforeItIsDismissed() {
        toast(MotionTier.Gesture)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Çöpe taşındı").performTouchInput { swipe(center, center + Offset(-160f, 0f), durationMillis = 60) }
        compose.waitForIdle()
        assertNotNull("not dismissed while still on its way out", toasts.current)
        var last = compose.onNodeWithText("Çöpe taşındı").getBoundsInRoot().left
        var frames = 0
        var moved = 0
        while (toasts.current != null && frames < 90) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            frames++
            if (toasts.current != null) {
                val left = compose.onNodeWithText("Çöpe taşındı").getBoundsInRoot().left
                assertTrue("frame $frames: the toast came back ($last → $left)", left <= last)
                if (left < last) moved++
                last = left
            }
        }
        compose.mainClock.autoAdvance = true
        assertNull("the toast was dismissed once it was out", toasts.current)
        assertTrue("the toast moved on after the finger lifted ($moved frames)", moved >= 3)
    }

    @Test
    fun widerUpASwipedToastGoesAtOnce() {
        toast(MotionTier.Timed)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Çöpe taşındı").performTouchInput { swipe(center, center + Offset(-160f, 0f), durationMillis = 60) }
        compose.waitForIdle()
        assertNull(toasts.current)
        compose.mainClock.autoAdvance = true
    }
}
