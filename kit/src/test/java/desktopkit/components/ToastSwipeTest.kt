package desktopkit.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
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

    /** The toast's card (the glass row with the pane title). */
    private fun card() = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle))

    @Test
    fun onAPhoneASwipedToastCarriesOnBeforeItIsDismissed() {
        toast(MotionTier.Gesture)
        compose.mainClock.autoAdvance = false
        // the fling the sheet's test commits with: 150 px in 60 ms, some of the way still to go
        val rest = card().getBoundsInRoot()
        val cardWidth = rest.right - rest.left
        compose.onNodeWithText("Çöpe taşındı").performTouchInput { swipe(center, center + Offset(-150f, 0f), durationMillis = 60) }
        compose.waitForIdle()
        assertNotNull("not dismissed while still on its way out", toasts.current)
        var last = card().getBoundsInRoot().left
        var frames = 0
        var moved = 0
        while (toasts.current != null && frames < 90) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            frames++
            if (toasts.current != null) {
                val left = card().getBoundsInRoot().left
                // in view it only goes on (out of view the spring may bounce a little before the dismiss lands)
                if (left > rest.left - cardWidth + 2.dp) {
                    assertTrue("frame $frames: the toast came back ($last → $left)", left <= last)
                    if (left < last) moved++
                }
                last = left
            }
        }
        compose.mainClock.autoAdvance = true
        assertNull("the toast was dismissed once it was out", toasts.current)
        assertTrue("the toast moved on after the finger lifted ($moved frames)", moved >= 1)
    }

    @Test
    fun widerUpASwipedToastGoesAtOnce() {
        toast(MotionTier.Timed)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Çöpe taşındı").performTouchInput { swipe(center, center + Offset(-150f, 0f), durationMillis = 60) }
        compose.waitForIdle()
        // the dismiss lands with the next dispatch: one frame, no way out in between
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertNull(toasts.current)
        compose.mainClock.autoAdvance = true
    }
}
