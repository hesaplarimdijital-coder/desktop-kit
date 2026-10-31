package desktopkit.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.components.DeskIconButton
import desktopkit.components.DeskIcons
import desktopkit.components.MenuButton
import desktopkit.components.MenuEntry
import desktopkit.shell.PageTopBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * With "Remove animations" on ([MotionTier.Reduced]) nothing eases: the first frame that differs is
 * already the end state. Compose does not read the system's animation scale, so the kit gates
 * every animation itself; these cover the ones that used to slip through (the top bar's glass and
 * rule, the press shrink, pop-up menus).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h600dp-mdpi")
class ReducedMotionTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var scrolled by mutableStateOf(false)

    @Test
    fun theTopBarTurnsToGlassInOneFrame() {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Light, inputMode = InputMode.Touch) {
                CompositionLocalProvider(LocalMotionTier provides MotionTier.Reduced) {
                    Box(Modifier.fillMaxSize().background(DeskTheme.colors.background)) {
                        PageTopBar("Files", onSearch = null, modifier = Modifier.testTag("bar"), scrolled = scrolled, glass = true)
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val bounds = compose.onNodeWithTag("bar").fetchSemanticsNode().boundsInWindow
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        fun ruleBrightness(): Int {
            compose.runOnIdle { window.draw(Canvas(bitmap)) }
            // the rule along the bar's bottom edge: fully there or not at all
            val pixel = bitmap.getPixel((bounds.right - 12f).roundToInt(), (bounds.bottom - 1f).roundToInt())
            return ((pixel shr 16 and 0xff) + (pixel shr 8 and 0xff) + (pixel and 0xff)) / 3
        }
        val before = ruleBrightness()
        scrolled = true
        val seen = mutableListOf<Int>()
        repeat(12) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            seen += ruleBrightness()
        }
        compose.mainClock.autoAdvance = true
        val after = seen.last()
        assertTrue("the rule appeared: $before → $after", after != before)
        assertTrue("every frame after the change shows the end state: $seen", seen.dropWhile { it == before }.all { it == after })
    }

    @Test
    fun thePressShrinkJumpsToItsSize() {
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                CompositionLocalProvider(LocalMotionTier provides MotionTier.Reduced) {
                    Box(Modifier.fillMaxSize().padding(40.dp)) {
                        DeskIconButton(DeskIcons.Play, "Oynat", {}, Modifier.testTag("button"))
                    }
                }
            }
        }
        compose.waitForIdle()
        val full = compose.onNodeWithTag("button").getBoundsInRoot().width
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("button").performTouchInput { down(center) }
        var first: Float? = null
        repeat(6) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            val width = compose.onNodeWithTag("button").getBoundsInRoot().width
            if (first == null && width < full - 0.5.dp) first = width.value
        }
        compose.onNodeWithTag("button").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        val shrunk = first ?: error("the button never shrank")
        // 92 % at once, not a frame in between
        assertEquals("the first smaller frame is the pressed size", full.value * 0.92f, shrunk, 1f)
    }

    @Test
    fun aMenuAppearsAtFullSize() {
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                CompositionLocalProvider(LocalMotionTier provides MotionTier.Reduced) {
                    Box(Modifier.fillMaxSize().padding(40.dp)) {
                        MenuButton("Kaynak", { listOf(MenuEntry.Item("USB DAC", {}), MenuEntry.Item("Hoparlör", {})) })
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Kaynak").performClick()
        // the first frame the menu is there at all
        var firstFrame: DpRect? = null
        repeat(6) {
            if (firstFrame == null) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                if (compose.onAllNodesWithText("USB DAC").fetchSemanticsNodes().isNotEmpty()) {
                    firstFrame = compose.onNodeWithText("USB DAC").getBoundsInRoot()
                }
            }
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val settled = compose.onNodeWithText("USB DAC").getBoundsInRoot()
        val first = firstFrame ?: error("the menu never appeared")
        assertEquals("the menu's first frame is its settled size", settled.width.value, first.width.value, 0.5f)
        assertEquals(settled.left.value, first.left.value, 0.5f)
    }
}
