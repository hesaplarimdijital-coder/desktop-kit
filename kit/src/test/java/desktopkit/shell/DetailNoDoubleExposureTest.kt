package desktopkit.shell

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.LocalMotionTier
import desktopkit.theme.MotionTier
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * No frame of a detail transition draws one screen through the other (the double exposure of 2.3.0,
 * where the detail's text was drawn over the half faded list's). The list is pure red, the detail pure
 * blue, the theme dark: a pixel with both red and blue in it is one screen seen through the other; a
 * blue pixel that is not pure blue is the detail part transparent; a red one with any green or blue in
 * it is the list faded over the page (the list's scrim is black, it only darkens the red). Every frame
 * of an opening, a closing and the Back gesture is checked; a few pixels along the edges of a shrunk
 * or sinking screen are blended by antialiasing, hence the tolerance.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class DetailNoDoubleExposureTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private class Frame(val label: String, val total: Int, val blue: Int, val red: Int, val mixed: Int, val fadedBlue: Int, val fadedRed: Int) {
        override fun toString() = "$label: blue $blue (faded $fadedBlue), red $red (faded $fadedRed), mixed $mixed of $total"
    }

    private var buffer: Bitmap? = null
    private var pixels = IntArray(0)

    private fun frame(label: String): Frame {
        val window = compose.activity.window.decorView
        val bitmap = buffer?.takeIf { it.width == window.width && it.height == window.height }
            ?: Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888).also {
                buffer = it
                pixels = IntArray(it.width * it.height)
            }
        compose.runOnIdle {
            bitmap.eraseColor(0)
            window.draw(Canvas(bitmap))
        }
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var blue = 0
        var red = 0
        var mixed = 0
        var fadedBlue = 0
        var fadedRed = 0
        for (p in pixels) {
            val r = p shr 16 and 0xff
            val g = p shr 8 and 0xff
            val b = p and 0xff
            val reddish = r > 48
            val bluish = b > 48
            when {
                reddish && bluish -> mixed++
                bluish -> {
                    blue++
                    if (b < 250 || g > 2 || r > 2) fadedBlue++
                }
                reddish -> {
                    red++
                    if (g > 2 || b > 2) fadedRed++
                }
            }
        }
        return Frame(label, pixels.size, blue, red, mixed, fadedBlue, fadedRed)
    }

    private fun step(label: String, frames: MutableList<Frame>) {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        frames += frame(label)
    }

    private fun show(tier: MotionTier, detail: () -> String?, close: () -> Unit, card: Boolean = false) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark) {
                CompositionLocalProvider(LocalMotionTier provides tier) {
                    DetailTransition(
                        detail = detail(),
                        onBack = close,
                        list = {
                            Box(Modifier.fillMaxSize().background(Color.Red)) {
                                if (card) Box(Modifier.detailSharedCard("card").size(160.dp).background(Color.Green))
                            }
                        },
                    ) {
                        Column(Modifier.fillMaxSize().background(Color.Blue)) {
                            if (card) Box(Modifier.detailSharedCard("card").fillMaxWidth().height(300.dp).background(Color.Green))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** At most this many pixels of a frame may be blended: the edges of a scaled screen, not a screen seen through another. */
    private fun tolerance(frame: Frame) = frame.total / 100

    private fun assertNoDoubleExposure(frames: List<Frame>) {
        for (f in frames) assertTrue("one screen is seen through the other: $f", f.mixed <= tolerance(f))
    }

    /** The phone's plain detail: both screens opaque in every frame, the detail over the list. */
    private fun assertBothOpaque(frames: List<Frame>) {
        assertNoDoubleExposure(frames)
        for (f in frames) {
            assertTrue("the detail is part transparent: $f", f.fadedBlue <= tolerance(f))
            assertTrue("the list is faded: $f", f.fadedRed <= tolerance(f))
        }
    }

    /** Some frame shows a good part of both screens (so the transition was really caught on its way). */
    private fun assertCaughtOnItsWay(frames: List<Frame>) {
        assertTrue(
            "no frame shows both screens: ${frames.joinToString("\n")}",
            frames.any { it.blue > it.total / 10 && it.red > it.total / 10 },
        )
    }

    @Test
    fun onAPhoneTheDetailSlidesInOpaqueOverTheListAndOutAgain() {
        var detail by mutableStateOf<String?>(null)
        show(MotionTier.Gesture, { detail }, { detail = null })
        compose.mainClock.autoAdvance = false
        val opening = mutableListOf<Frame>()
        compose.runOnIdle { detail = "raf" }
        repeat(36) { step("opening frame $it", opening) }
        assertBothOpaque(opening)
        assertCaughtOnItsWay(opening)
        assertTrue("the detail covers the window once open: ${opening.last()}", opening.last().blue > opening.last().total * 3 / 4)

        val closing = mutableListOf<Frame>()
        compose.runOnIdle { detail = null }
        repeat(36) { step("closing frame $it", closing) }
        assertBothOpaque(closing)
        assertCaughtOnItsWay(closing)
        assertEquals("the detail is gone once closed: ${closing.last()}", 0, closing.last().blue)
        compose.mainClock.autoAdvance = true
    }

    private fun back(progress: Float) = BackEventCompat(40f, 600f, progress, BackEventCompat.EDGE_LEFT)

    @Test
    fun onAPhoneTheBackGestureShrinksTheOpaqueDetailOverTheDimmedList() {
        var detail by mutableStateOf<String?>("raf")
        show(MotionTier.Gesture, { detail }, { detail = null })
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.mainClock.autoAdvance = false
        val frames = mutableListOf<Frame>()
        compose.runOnUiThread { dispatcher.dispatchOnBackStarted(back(0f)) }
        for (p in listOf(0.05f, 0.1f, 0.2f, 0.3f, 0.45f, 0.6f, 0.75f, 0.9f)) {
            compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(back(p)) }
            step("finger at $p", frames)
            step("finger at $p, a frame on", frames)
        }
        assertBothOpaque(frames)
        assertCaughtOnItsWay(frames)
        // let go: the detail slides out, still opaque, the list rising behind it
        compose.runOnUiThread { dispatcher.onBackPressed() }
        val out = mutableListOf<Frame>()
        repeat(30) { step("let go, frame $it", out) }
        assertBothOpaque(out)
        assertNull(detail)
        assertEquals("the detail is gone once closed: ${out.last()}", 0, out.last().blue)
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun onAPhoneABackGestureGivenUpSpringsBackOpaque() {
        var detail by mutableStateOf<String?>("raf")
        show(MotionTier.Gesture, { detail }, { detail = null })
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.mainClock.autoAdvance = false
        val frames = mutableListOf<Frame>()
        compose.runOnUiThread { dispatcher.dispatchOnBackStarted(back(0f)) }
        for (p in listOf(0.2f, 0.5f)) {
            compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(back(p)) }
            step("finger at $p", frames)
        }
        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        repeat(30) { step("given up, frame $it", frames) }
        assertBothOpaque(frames)
        assertEquals("raf", detail)
        compose.mainClock.autoAdvance = true
    }

    /** Back right after the tap: the opening runs back, both screens still opaque. */
    @Test
    fun onAPhoneAnOpeningTurnedAroundStaysOpaque() {
        var detail by mutableStateOf<String?>(null)
        show(MotionTier.Gesture, { detail }, { detail = null })
        compose.mainClock.autoAdvance = false
        val frames = mutableListOf<Frame>()
        compose.runOnIdle { detail = "raf" }
        repeat(6) { step("opening frame $it", frames) }
        compose.runOnIdle { detail = null }
        repeat(30) { step("back frame $it", frames) }
        assertBothOpaque(frames)
        assertCaughtOnItsWay(frames)
        assertEquals("the detail is gone: ${frames.last()}", 0, frames.last().blue)
        compose.mainClock.autoAdvance = true
    }

    /** With a card the rest of the two screens fades through: the list is gone before the detail appears, and back. */
    @Test
    fun onAPhoneWithACardTheScreensFadeThroughNotOverEachOther() {
        var detail by mutableStateOf<String?>(null)
        show(MotionTier.Gesture, { detail }, { detail = null }, card = true)
        compose.mainClock.autoAdvance = false
        val frames = mutableListOf<Frame>()
        compose.runOnIdle { detail = "album" }
        repeat(36) { step("opening frame $it", frames) }
        assertTrue("the detail is open: ${frames.last()}", frames.last().blue > frames.last().total / 2)
        compose.runOnIdle { detail = null }
        repeat(36) { step("closing frame $it", frames) }
        assertNoDoubleExposure(frames)
        assertEquals("the detail is gone: ${frames.last()}", 0, frames.last().blue)
        compose.mainClock.autoAdvance = true
    }

    /** Tablets and DeX: the shared axis's fades in turn, the list gone before the detail fades in, and back. */
    @Test
    fun widerUpTheSharedAxisFadesInTurn() {
        var detail by mutableStateOf<String?>(null)
        show(MotionTier.Timed, { detail }, { detail = null })
        compose.mainClock.autoAdvance = false
        val frames = mutableListOf<Frame>()
        compose.runOnIdle { detail = "raf" }
        repeat(30) { step("opening frame $it", frames) }
        assertTrue("the detail is open: ${frames.last()}", frames.last().blue > frames.last().total * 3 / 4)
        compose.runOnIdle { detail = null }
        repeat(30) { step("closing frame $it", frames) }
        assertNoDoubleExposure(frames)
        assertEquals("the detail is gone: ${frames.last()}", 0, frames.last().blue)
        compose.mainClock.autoAdvance = true
    }
}
