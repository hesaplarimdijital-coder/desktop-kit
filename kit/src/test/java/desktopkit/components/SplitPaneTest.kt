package desktopkit.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
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

/**
 * The divider keeps to the widths the panes allow: 2.4.0 kept counting a drag past the minimum,
 * and dragging back did nothing until the count came back inside (DeskMail's request 13).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1000dp-h700dp-mdpi")
class SplitPaneTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun aDragPastTheEdgeComesBackAtOnce() {
        var committed = 0f
        val stored = SplitState(400f) { committed = it }
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                Box(Modifier.fillMaxSize()) {
                    HorizontalSplitPane(
                        state = stored,
                        start = { Box(Modifier.fillMaxSize().testTag("start")) },
                        end = { Box(Modifier.fillMaxSize()) },
                        minStart = 240.dp,
                        minEnd = 280.dp,
                    )
                }
            }
        }
        compose.waitForIdle()
        val divider = compose.onNodeWithContentDescription("Resize the panes")
        // 300 dp to the left: the pane stops at its 240 dp minimum, and the state with it
        divider.performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(-20f, 0f))
            moveBy(androidx.compose.ui.geometry.Offset(-280f, 0f))
            up()
        }
        compose.waitForIdle()
        assertEquals(240f, stored.startWidthDp, 0.5f)
        assertEquals("the drag's end stores the width", 240f, committed, 0.5f)
        val atMin = compose.onNodeWithTag("start").getBoundsInRoot().let { it.right - it.left }
        assertEquals(240f, atMin.value, 1f)
        // 40 dp back to the right: the divider moves at once
        divider.performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(20f, 0f))
            moveBy(androidx.compose.ui.geometry.Offset(40f, 0f))
            up()
        }
        compose.waitForIdle()
        val after = compose.onNodeWithTag("start").getBoundsInRoot().let { it.right - it.left }
        assertTrue("the divider did not move back: $atMin → $after", after.value > atMin.value + 30f)
    }
}
