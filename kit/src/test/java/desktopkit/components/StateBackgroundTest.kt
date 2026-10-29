package desktopkit.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.theme.DeskColors
import desktopkit.theme.DeskTheme
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * [animatedStateBackground] frame by frame: with a mouse hover and press jump to their colour in the
 * frame they happen (no fade that trails behind the pointer), with touch they ease, and a fade to or
 * from transparent never passes through another colour.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w400dp-h300dp-mdpi")
class StateBackgroundTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** The background of every composition, in order: one entry per frame that changed it. */
    private val shown = mutableListOf<Color>()
    private var selected by mutableStateOf(false)
    private lateinit var colors: DeskColors

    private fun control(mode: InputMode) {
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Light, inputMode = mode) {
                colors = DeskTheme.colors
                val interaction = remember { MutableInteractionSource() }
                val background = animatedStateBackground(interaction.collectState(), selected).value
                SideEffect { shown += background }
                Box(Modifier.size(80.dp).testTag(TAG).background(background).deskClickable(interaction, {}))
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    /**
     * Runs frames until the background leaves [from] and returns the colour of that first frame,
     * then runs the rest of the change (400 ms) and returns every colour shown on the way.
     */
    private fun change(from: Color, action: () -> Unit): Pair<Color, List<Color>> {
        shown.clear()
        action()
        var frames = 0
        while (shown.none { it != from } && frames < 10) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            frames++
        }
        val first = shown.first { it != from }
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        return first to shown.filter { it != from }
    }

    private fun hoverIn() = compose.onNodeWithTag(TAG).performMouseInput { moveTo(center) }

    private fun hoverOut() = compose.onNodeWithTag(TAG).performMouseInput { moveTo(bottomRight + Offset(60f, 60f)) }

    @Test
    fun pointerHoverEnterAndExitAreInstant() {
        control(InputMode.Pointer)
        val (enter, enterPath) = change(Color.Transparent) { hoverIn() }
        assertEquals(colors.hover, enter)
        assertEquals(listOf(colors.hover), enterPath.distinct())

        val (exit, exitPath) = change(colors.hover) { hoverOut() }
        assertEquals(Color.Transparent, exit)
        assertEquals(listOf(Color.Transparent), exitPath.distinct())
    }

    @Test
    fun pointerPressIsInstant() {
        control(InputMode.Pointer)
        change(Color.Transparent) { hoverIn() }
        val (press, path) = change(colors.hover) { compose.onNodeWithTag(TAG).performMouseInput { press() } }
        assertEquals(colors.pressed, press)
        assertEquals(listOf(colors.pressed), path.distinct())
    }

    @Test
    fun pointerSelectionStillEases() {
        control(InputMode.Pointer)
        val (first, path) = change(Color.Transparent) { selected = true }
        assertTrue("selection should fade in, first frame was $first", first != colors.selection)
        assertEquals(colors.selection, path.last())
        // fading out keeps the selection's hue: only the opacity drops, no grey on the way
        val (_, out) = change(colors.selection) { selected = false }
        assertEquals(Color.Transparent, out.last())
        out.dropLast(1).forEach { assertSameHue(colors.selection, it) }
    }

    @Test
    fun touchPressEases() {
        control(InputMode.Touch)
        val (first, path) = change(Color.Transparent) { compose.onNodeWithTag(TAG).performTouchInput { down(center) } }
        assertTrue("press should fade in with touch, first frame was $first", first != colors.pressed)
        assertTrue("expected frames between transparent and pressed: $path", path.size > 2)
        assertEquals(colors.pressed, path.last())
        // from transparent the fade changes only the opacity: no dark frame on the way in
        path.forEach { assertSameHue(colors.pressed, it) }
    }

    @Test
    fun touchHoverEases() {
        control(InputMode.Touch)
        val (first, path) = change(Color.Transparent) { hoverIn() }
        assertTrue("hover should fade in with touch, first frame was $first", first != colors.hover)
        assertEquals(colors.hover, path.last())
        val (_, out) = change(colors.hover) { hoverOut() }
        assertEquals(Color.Transparent, out.last())
        out.dropLast(1).forEach { assertSameHue(colors.hover, it) }
    }

    private fun assertSameHue(expected: Color, actual: Color) {
        val close = abs(expected.red - actual.red) < 0.02f &&
            abs(expected.green - actual.green) < 0.02f &&
            abs(expected.blue - actual.blue) < 0.02f
        assertTrue("$actual should be $expected with another opacity", close)
    }

    private companion object {
        const val TAG = "control"
    }
}
