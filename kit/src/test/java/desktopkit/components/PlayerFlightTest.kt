package desktopkit.components

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.SampleFullPlayer
import desktopkit.SampleMiniPlayer
import desktopkit.TestShell
import desktopkit.mediaSpec
import desktopkit.sampleArtwork
import desktopkit.sampleMoreActions
import desktopkit.shell.WidthClass
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
import kotlin.math.roundToInt

/**
 * The phone's player as one piece, frame by frame: the cover marked with [playerSharedElement] flies
 * from the strip to its place in the full player and back, drawn where the way has it; the strip's
 * controls in the sheet leave once faded; the strip shows the cover again at the end.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class PlayerFlightTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var player: ExpandablePlayerState

    private fun phone() {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                player = rememberExpandablePlayerState()
                TestShell(
                    WidthClass.Compact,
                    startRoute = "music",
                    spec = mediaSpec,
                    moreActions = sampleMoreActions,
                    bottomBar = {
                        ExpandablePlayer(player, mini = { SampleMiniPlayer(3) }, full = { SampleFullPlayer(3) }, modifier = Modifier.testTag("mini"))
                    },
                ) { route, _ -> Text("screen-$route") }
            }
        }
        compose.waitForIdle()
    }

    /** The placed covers with [tag] (a node that is laid out but not placed is neither drawn, hit nor read out). */
    private fun covers(tag: String): List<Rect> =
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().filter { it.layoutInfo.isPlaced }.map { it.boundsInWindow }

    /** The pixel at the middle of [rect] is the track's artwork colour. */
    private fun Bitmap.showsArtworkAt(rect: Rect): Boolean {
        val pixel = getPixel(rect.center.x.roundToInt(), rect.center.y.roundToInt())
        val artwork = sampleArtwork[3].toArgb()
        return listOf(16, 8, 0).all { shift -> abs((pixel shr shift and 0xff) - (artwork shr shift and 0xff)) <= 8 }
    }

    @Test
    fun theCoverFliesFromTheStripToTheFullPlayerAndBack() {
        phone()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        fun draw() = compose.runOnIdle { window.draw(Canvas(bitmap)) }
        val strip = covers("cover-mini").single()
        draw()
        assertTrue("the strip shows the cover", bitmap.showsArtworkAt(strip))

        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("mini").performClick()
        var flown = 0
        var gone = 0
        repeat(90) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            val p = player.progress
            if (p <= 0f || p >= 1f) return@repeat
            val full = covers("cover-full").singleOrNull() ?: return@repeat
            val minis = covers("cover-mini")
            // the strip's copy in the sheet rises with the sheet: where the strip has the cover, moved up by the way
            val inSheet = strip.translate(0f, -p * player.collapsedTop)
            if (p < 0.3f) {
                assertEquals("frame $it (p=$p): the sheet carries a copy of the strip while it is still placed", 2, minis.size)
                val placed = minis.minBy { it.top }
                assertEquals("frame $it (p=$p): the sheet's copy of the strip is where the sheet is", inSheet.top, placed.top, 1.5f)
            } else {
                assertEquals("frame $it (p=$p): the strip's controls in the sheet are gone once faded", 1, minis.size)
                gone++
            }
            val flying = lerp(inSheet, full, p)
            draw()
            assertTrue("frame $it (p=$p): the cover is drawn where the way has it, at $flying", bitmap.showsArtworkAt(flying))
            flown++
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue("the flight was checked in $flown frames, $gone of them past the strip's fade", flown >= 6 && gone >= 2)
        assertTrue(player.isExpanded)
        val full = covers("cover-full").single()
        draw()
        assertTrue("the full player shows the cover at its place", bitmap.showsArtworkAt(full))
        assertEquals("the strip's copy in the sheet is gone once the player is open", 1, covers("cover-mini").size)

        // and back down: the same way in reverse, the strip showing the cover again at the end
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        var back = 0
        repeat(90) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            val p = player.progress
            if (p <= 0f || p >= 1f) return@repeat
            val fullNow = covers("cover-full").singleOrNull() ?: return@repeat
            val flying = lerp(strip.translate(0f, -p * player.collapsedTop), fullNow, p)
            draw()
            assertTrue("frame $it (p=$p) on the way back: the cover is drawn where the way has it", bitmap.showsArtworkAt(flying))
            back++
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue("the way back was checked in $back frames", back >= 6)
        assertTrue(!player.isExpanded)
        draw()
        assertTrue("the strip shows the cover again", bitmap.showsArtworkAt(strip))
        assertEquals(1, covers("cover-mini").size)
    }
}
