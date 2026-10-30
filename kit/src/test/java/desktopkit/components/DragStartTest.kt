package desktopkit.components

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import desktopkit.theme.DeskTheme
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * K6 as in Samsung's My Files: a drag starts only after the pointer has rested on the element for
 * the touch and hold delay, never from a quick press and move; a mouse held still starts it as it
 * then moves, so a slow click stays a click. A row that can be dragged keeps showing its hover and
 * selection (the ready-made drag source drew it through a picture taken once).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w600dp-h400dp-mdpi")
class DragStartTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** How often a drag asked for its payload, i.e. tried to start (null keeps the platform out of it). */
    private var asked = 0

    private fun source(touch: Boolean = false, mode: InputMode = InputMode.Pointer) {
        compose.setContent {
            DesktopKitTheme(inputMode = mode) {
                Box(Modifier.size(200.dp).testTag("source").dragSource(touch = touch) { asked++; null })
            }
        }
        compose.waitForIdle()
        // time moves only when the test moves it
        compose.mainClock.autoAdvance = false
    }

    @Test
    fun aQuickPressAndMoveStartsNoDrag() {
        source()
        compose.onNodeWithTag("source").performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(80f, 0f))
            moveBy(Offset(80f, 0f))
            release()
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        assertEquals(0, asked)
    }

    @Test
    fun aMouseHeldStillThenMovedStartsTheDrag() {
        source()
        compose.onNodeWithTag("source").performMouseInput {
            moveTo(center)
            press()
        }
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        assertEquals("not before the delay", 0, asked)
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        assertEquals("held still: not until it moves", 0, asked)
        compose.onNodeWithTag("source").performMouseInput { moveBy(Offset(30f, 0f)) }
        compose.waitForIdle()
        assertEquals(1, asked)
        compose.onNodeWithTag("source").performMouseInput { release() }
    }

    @Test
    fun aSlowClickStaysAClick() {
        var clicks = 0
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Pointer) {
                Box(Modifier.size(200.dp).testTag("source").dragSource { asked++; null }.clickable { clicks++ })
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("source").performMouseInput {
            moveTo(center)
            press()
        }
        compose.mainClock.advanceTimeBy(1200)
        compose.waitForIdle()
        compose.onNodeWithTag("source").performMouseInput { release() }
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        assertEquals("no drag", 0, asked)
        assertEquals("the click ran", 1, clicks)
    }

    @Test
    fun aFingerDragsOnlyWhereAsked() {
        source(mode = InputMode.Touch)
        compose.onNodeWithTag("source").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1200)
        compose.waitForIdle()
        compose.onNodeWithTag("source").performTouchInput { up() }
        assertEquals(0, asked)
    }

    @Test
    fun aFingerHeldStillStartsTheDragWhereAsked() {
        source(touch = true, mode = InputMode.Touch)
        compose.onNodeWithTag("source").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1200)
        compose.waitForIdle()
        compose.onNodeWithTag("source").performTouchInput { up() }
        assertEquals(1, asked)
    }

    @Test
    fun aDraggableRowShowsTheSelectionMovingToIt() {
        var playing by mutableIntStateOf(0)
        var background = Color.Unspecified
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Light, inputMode = InputMode.Pointer) {
                background = DeskTheme.colors.background
                Column(Modifier.fillMaxSize().background(DeskTheme.colors.background)) {
                    repeat(2) { i ->
                        DeskListRow(
                            title = "Parça $i",
                            modifier = Modifier.testTag("row$i"),
                            selected = playing == i,
                            drag = { DeskDragPayload("track", listOf("t$i"), "Parça $i") },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        val before = (0..1).map { pixel("row$it") }
        assertNotEquals("the playing row is marked", before[0], before[1])

        playing = 1
        compose.waitForIdle()
        val after = (0..1).map { pixel("row$it") }
        assertEquals("the first row is plain now: ${after[0]} like the second was: ${before[1]}", before[1], after[0])
        assertEquals("the second row is marked now", before[0], after[1])
        assertNotEquals(colorInt(background), after[1])
    }

    /** The colour of an empty spot near the end of a row. */
    private fun pixel(tag: String): Int {
        val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        return bitmap.getPixel((bounds.right - 24f).roundToInt(), bounds.center.y.roundToInt())
    }

    private fun colorInt(color: Color): Int {
        val r = (color.red * 255).roundToInt()
        val g = (color.green * 255).roundToInt()
        val b = (color.blue * 255).roundToInt()
        return (0xff shl 24) or (r shl 16) or (g shl 8) or b
    }
}
