package desktopkit.shell

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.theme.DeskTheme
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * The phone's top bar turning to glass and back, frame by frame: the bar never darkens on the way
 * (a fade to transparent black flashed grey for a few frames on the S24).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h400dp-mdpi")
class TopBarFadeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var scrolled by mutableStateOf(false)

    private fun bar(theme: ThemeMode) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = theme, inputMode = InputMode.Touch) {
                Box(Modifier.fillMaxSize().background(DeskTheme.colors.background)) {
                    PageTopBar("Files", onSearch = null, modifier = Modifier.testTag("bar"), scrolled = scrolled, glass = true)
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    /** The brightness of an empty spot of the bar, frame by frame, while [change] plays out. */
    private fun frames(change: () -> Unit): List<Int> {
        val bounds = compose.onNodeWithTag("bar").fetchSemanticsNode().boundsInWindow
        val x = (bounds.right - 12f).roundToInt()
        val y = bounds.center.y.roundToInt()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        fun sample(): Int {
            compose.runOnIdle { window.draw(Canvas(bitmap)) }
            val pixel = bitmap.getPixel(x, y)
            return ((pixel shr 16 and 0xff) + (pixel shr 8 and 0xff) + (pixel and 0xff)) / 3
        }
        val seen = mutableListOf(sample())
        change()
        repeat(30) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            seen += sample()
        }
        return seen
    }

    private fun assertNoDarkFrame(seen: List<Int>) {
        val floor = minOf(seen.first(), seen.last()) - 6
        assertTrue("the bar darkened on the way: $seen", seen.all { it >= floor })
    }

    /** Both ways are fades: frames in between, not one jump (the way back used to switch in one frame). */
    private fun assertFades(seen: List<Int>) {
        assertTrue("the bar did not change: $seen", seen.first() != seen.last())
        assertTrue("the bar jumped instead of fading: $seen", seen.distinct().size >= 3)
    }

    @Test
    fun lightBarTurnsToGlassAndBackWithoutGrey() {
        bar(ThemeMode.Light)
        assertNoDarkFrame(frames { scrolled = true }.also(::assertFades))
        assertNoDarkFrame(frames { scrolled = false }.also(::assertFades))
    }

    @Test
    fun darkBarTurnsToGlassAndBackWithoutGrey() {
        bar(ThemeMode.Dark)
        assertNoDarkFrame(frames { scrolled = true }.also(::assertFades))
        assertNoDarkFrame(frames { scrolled = false }.also(::assertFades))
    }
}
