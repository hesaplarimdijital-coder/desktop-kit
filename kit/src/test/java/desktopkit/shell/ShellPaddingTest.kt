package desktopkit.shell

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.SampleTrackList
import desktopkit.TestShell
import desktopkit.mediaSpec
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * On a phone the content lies under the bars: a screen that asks for the shell's padding gets the
 * whole window and the bars' heights; one that does not is kept clear of the bars as in 0.2.0.
 * Hiding the bars while scrolling moves them in the draw phase only.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class ShellPaddingTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun shell(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                TestShell(WidthClass.Compact, startRoute = "music", spec = mediaSpec) { _, _ -> content() }
            }
        }
        compose.waitForIdle()
    }

    private fun near(expected: Dp, actual: Dp, label: String) =
        assertTrue("$label: expected $expected, was $actual", abs(expected.value - actual.value) < 1f)

    @Test
    fun screenUnderTheBarsGetsTheWindowAndThePadding() {
        var padding: PaddingValues? = null
        shell {
            padding = rememberShellContentPadding()
            Box(Modifier.fillMaxSize().testTag("screen"))
        }
        val root = compose.onRoot().getBoundsInRoot()
        val screen = compose.onNodeWithTag("screen").getBoundsInRoot()
        near(root.top, screen.top, "screen top")
        near(root.bottom, screen.bottom, "screen bottom")
        val values = padding!!
        // top bar 56 dp; bottom: the status strip (1 + 48 dp) and the bottom bar (1 + 64 dp)
        near(56.dp, values.calculateTopPadding(), "top padding")
        near(114.dp, values.calculateBottomPadding(), "bottom padding")
        assertEquals(0.dp, values.calculateLeftPadding(LayoutDirection.Ltr))
    }

    @Test
    fun screensThatDoNotAskStayClearOfTheBars() {
        shell { Box(Modifier.fillMaxSize().testTag("screen")) { Text("eski ekran") } }
        val root = compose.onRoot().getBoundsInRoot()
        val screen = compose.onNodeWithTag("screen").getBoundsInRoot()
        near(root.top + 56.dp, screen.top, "screen top")
        near(root.bottom - 114.dp, screen.bottom, "screen bottom")
    }

    @Test
    fun scrollingMovesTheBarsWithoutMeasuringTheScreen() {
        var measures = 0
        shell {
            Box(
                Modifier.fillMaxSize().layout { measurable, constraints ->
                    measures++
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                },
            ) { SampleTrackList() }
        }
        val before = measures
        val barTop = compose.onNodeWithText("Radyo").getBoundsInRoot().top
        compose.onNodeWithTag("tracks").performTouchInput { swipeUp(startY = bottom - 300f, endY = top + 300f, durationMillis = 600) }
        compose.waitForIdle()
        val movedTop = compose.onNodeWithText("Radyo").getBoundsInRoot().top
        assertTrue("the bottom bar slid down: $barTop → $movedTop", movedTop > barTop + 20.dp)
        assertEquals("the screen was measured again while scrolling", before, measures)
    }
}
