package desktopkit.shell

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.LocalMotionTier
import desktopkit.theme.MotionTier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/** M3: a cover marked on both sides flies from the list row to the detail, and back with its corners. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class DetailSharedElementTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun coverFliesFromTheRowToTheDetail() {
        var detail by mutableStateOf<String?>(null)
        compose.setContent {
            DesktopKitTheme {
                androidx.compose.runtime.CompositionLocalProvider(LocalMotionTier provides MotionTier.Timed) {
                    DetailTransition(
                        detail = detail,
                        onBack = { detail = null },
                        list = {
                            Column {
                                Box(Modifier.detailSharedElement("cover").size(40.dp).background(Color.Red).testTag("small"))
                                Text("Gece Yarısı Treni", Modifier.detailSharedBounds("title"))
                            }
                        },
                    ) {
                        Column {
                            Box(Modifier.detailSharedElement("cover").size(232.dp).background(Color.Red).testTag("large"))
                            Text("Gece Yarısı Treni", Modifier.detailSharedBounds("title"))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { detail = "album" }
        // frame by frame: the cover grows from the row's 40 dp to the detail's 232 dp
        val widths = mutableListOf<Float>()
        repeat(30) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            if (compose.onAllNodesWithTag("large", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
                val bounds = compose.onNodeWithTag("large", useUnmergedTree = true).getBoundsInRoot()
                widths += (bounds.right - bounds.left).value
            }
        }
        assertTrue("the cover passes through sizes in between: $widths", widths.any { it > 44f && it < 228f })
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val landed = compose.onNodeWithTag("large", useUnmergedTree = true).getBoundsInRoot()
        assertTrue("cover lands at 232 dp: ${landed.right - landed.left}", (landed.right - landed.left) >= 231.dp)
        compose.onNodeWithText("Gece Yarısı Treni").assertExists()
    }

    @Test
    fun coverKeepsItsRoundedCornersFlyingBack() {
        var detail by mutableStateOf<String?>("album")
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme {
                androidx.compose.runtime.CompositionLocalProvider(LocalMotionTier provides MotionTier.Timed) {
                    DetailTransition(
                        detail = detail,
                        onBack = { detail = null },
                        list = {
                            // as inside a CoverCell: the cell's shape comes from the cell, no clipping
                            // parent to lean on in flight
                            androidx.compose.runtime.CompositionLocalProvider(LocalSharedElementShape provides RoundedCornerShape(16.dp)) {
                                Box(Modifier.detailSharedElement("cover").size(96.dp).background(Color.Red).testTag("small"))
                            }
                        },
                    ) {
                        Box(Modifier.detailSharedElement("cover", shape = RoundedCornerShape(16.dp)).size(232.dp)
                            .background(Color.Red).testTag("large"))
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { detail = null }
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        var checked = 0
        repeat(30) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            val nodes = compose.onAllNodesWithTag("small", useUnmergedTree = true).fetchSemanticsNodes()
            if (nodes.isEmpty()) return@repeat
            val bounds = nodes.first().boundsInWindow
            if (bounds.width < 40f) return@repeat
            compose.runOnIdle { window.draw(Canvas(bitmap)) }
            val corner = bitmap.getPixel(bounds.left.roundToInt() + 1, bounds.top.roundToInt() + 1)
            val middle = bitmap.getPixel(bounds.center.x.roundToInt(), bounds.center.y.roundToInt())
            assertTrue("frame $it: the middle of the cover is red (${Integer.toHexString(middle)})", red(middle))
            assertFalse("frame $it: the corner of the flying cover is square (${Integer.toHexString(corner)})", red(corner))
            checked++
        }
        assertTrue("no frame of the flight was checked", checked > 0)
        compose.mainClock.autoAdvance = true
    }

    /** A card grows into the detail's header on a phone, through sizes in between, and lands back in the list. */
    @Test
    fun theCardGrowsIntoTheHeaderAndBack() {
        var detail by mutableStateOf<String?>(null)
        compose.setContent {
            DesktopKitTheme {
                androidx.compose.runtime.CompositionLocalProvider(LocalMotionTier provides MotionTier.Gesture) {
                    DetailTransition(
                        detail = detail,
                        onBack = { detail = null },
                        list = {
                            Column {
                                Box(Modifier.detailSharedCard("card").size(160.dp).background(Color.Red).testTag("card"))
                            }
                        },
                    ) {
                        Column {
                            Box(Modifier.detailSharedCard("card").fillMaxWidth().height(300.dp).background(Color.Blue).testTag("header"))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { detail = "album" }
        val widths = mutableListOf<Float>()
        repeat(40) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            if (compose.onAllNodesWithTag("header", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
                val bounds = compose.onNodeWithTag("header", useUnmergedTree = true).getBoundsInRoot()
                widths += (bounds.right - bounds.left).value
            }
        }
        assertTrue("the header passes through widths between the card's and its own: $widths", widths.any { it > 170f && it < 400f })
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val open = compose.onNodeWithTag("header", useUnmergedTree = true).getBoundsInRoot()
        assertTrue("the header lands at full width: ${open.right - open.left}", (open.right - open.left) >= 400.dp)
        compose.runOnIdle { detail = null }
        compose.waitForIdle()
        val back = compose.onNodeWithTag("card", useUnmergedTree = true).getBoundsInRoot()
        assertTrue("the card is back at its size: ${back.right - back.left}", (back.right - back.left) <= 161.dp && (back.right - back.left) >= 159.dp)
    }

    /** Back half way through the opening: the header turns around where it is, it never jumps to its end first. */
    @Test
    fun closingWhileOpeningTurnsAroundWhereItIs() {
        var detail by mutableStateOf<String?>(null)
        compose.setContent {
            DesktopKitTheme {
                androidx.compose.runtime.CompositionLocalProvider(LocalMotionTier provides MotionTier.Gesture) {
                    DetailTransition(
                        detail = detail,
                        onBack = { detail = null },
                        list = {
                            Column {
                                Box(Modifier.detailSharedCard("card").size(160.dp).background(Color.Red).testTag("card"))
                            }
                        },
                    ) {
                        Column {
                            Box(Modifier.detailSharedCard("card").fillMaxWidth().height(300.dp).background(Color.Blue).testTag("header"))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { detail = "album" }
        // frame by frame (a run of frames without an idle in between leaves the recomposer asleep):
        // the header appears, then it is a few frames into its growth
        var waited = 0
        while (compose.onAllNodesWithTag("header", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            assertTrue("the header never appeared", ++waited < 10)
        }
        val before = mutableListOf<Float>()
        repeat(4) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            before += compose.onNodeWithTag("header", useUnmergedTree = true).getBoundsInRoot().let { (it.right - it.left).value }
        }
        assertTrue("the header is still on its way at ${before.last()} dp", before.last() < 400f)
        compose.runOnIdle { detail = null }
        val after = mutableListOf<Float>()
        repeat(40) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            if (compose.onAllNodesWithTag("header", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
                after += compose.onNodeWithTag("header", useUnmergedTree = true).getBoundsInRoot().let { (it.right - it.left).value }
            }
        }
        compose.mainClock.autoAdvance = true
        // in the frame the transition is over the detail stands at its own size, faded out, for the composition that
        // removes it: not drawn, not a jump
        val gone = compose.onAllNodesWithTag("header", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        val drawn = if (gone && after.size > 1) after.dropLast(1) else after
        val largestStep = drawn.zipWithNext { a, b -> b - a }.plus(drawn.first() - before.last()).max()
        assertTrue("the header came back: $after", gone && drawn.last() < 170f)
        // the opening grows the header by 40 dp a frame at most; a jump to its full width is 250
        assertTrue("the header jumped $largestStep dp wider in one frame on the way back (before $before, after $after)", largestStep < 60f)
    }

    private fun red(pixel: Int): Boolean {
        val r = pixel shr 16 and 0xff
        val g = pixel shr 8 and 0xff
        val b = pixel and 0xff
        return r - maxOf(g, b) > 100
    }
}
