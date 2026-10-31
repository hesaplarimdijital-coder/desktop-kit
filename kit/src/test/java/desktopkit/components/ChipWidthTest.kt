package desktopkit.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4

import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.LocalMotionTier
import desktopkit.theme.MotionTier
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** On a phone a chip with an icon keeps its width when selected: the check mark takes the icon's place. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class ChipWidthTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var selected by mutableStateOf(false)

    @Test
    fun aPhonesChipWithAnIconKeepsItsWidthFrameByFrame() {
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Touch) {
                CompositionLocalProvider(LocalMotionTier provides MotionTier.Gesture) {
                    DeskChip("Albümler", selected, { selected = !selected }, Modifier.testTag("chip"), icon = DeskIcons.Music)
                }
            }
        }
        compose.waitForIdle()
        fun width() = compose.onNodeWithTag("chip").getBoundsInRoot().let { (it.right - it.left).value }
        val rest = width()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { selected = true }
        repeat(20) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            assertEquals("frame $it: the chip kept its width", rest, width(), 0.5f)
        }
        compose.runOnIdle { selected = false }
        repeat(20) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            assertEquals("frame $it on the way back: the chip kept its width", rest, width(), 0.5f)
        }
        compose.mainClock.autoAdvance = true
    }
}
