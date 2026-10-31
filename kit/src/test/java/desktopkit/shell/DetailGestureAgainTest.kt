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
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The detail transition after the review: a Back gesture given up and then made again still flies the
 * card; a detail re-emitted under its key shows its latest object; from tablet width up the 2.2.0
 * gesture stays (the detail shrinks over the shade, the list is not behind it).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class DetailGestureAgainTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun back(progress: Float) = BackEventCompat(40f, 400f, progress, BackEventCompat.EDGE_LEFT)

    private fun frame() {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun headerWidth(): Float? =
        compose.onAllNodesWithTag("header", useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
            ?.let { it.boundsInWindow.width / compose.density.density }

    @Test
    fun aSecondGestureAfterACancelledOneStillFliesTheCard() {
        var detail by mutableStateOf<String?>(null)
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
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
        compose.runOnIdle { detail = "album" }
        compose.waitForIdle()
        assertTrue("the header is open at full width: ${headerWidth()}", headerWidth()!! >= 400f)
        val dispatcher = compose.activity.onBackPressedDispatcher
        // a gesture half way, given up: everything springs back. (Two events, as a finger sends: the shared
        // elements match on the first one's composition, and the seek that moves them is the next one's.)
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(back(0f))
            dispatcher.dispatchOnBackProgressed(back(0.3f))
        }
        compose.waitForIdle()
        compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(back(0.5f)) }
        compose.waitForIdle()
        assertTrue("half way the header is on its way back to the card: ${headerWidth()}", headerWidth()!! < 380f)
        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
        assertEquals("album", detail)
        assertTrue("given up, the header is back at full width: ${headerWidth()}", headerWidth()!! >= 400f)
        compose.onNodeWithText("Liste").assertDoesNotExist()

        // the same gesture again, let go half way: the header shrinks into the card through sizes in between
        // (a run back that left its flags set would have the card leave without flying)
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(back(0f))
            dispatcher.dispatchOnBackProgressed(back(0.3f))
        }
        repeat(2) { frame() }
        compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(back(0.5f)) }
        repeat(2) { frame() }
        assertTrue("half way again the header is on its way: ${headerWidth()}", headerWidth()!! < 380f)
        compose.runOnUiThread { dispatcher.onBackPressed() }
        val widths = mutableListOf<Float>()
        repeat(60) {
            frame()
            headerWidth()?.let { widths += it }
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertNull(detail)
        assertTrue("the header shrank towards the card on the way out: $widths", widths.any { it < 300f })
        assertTrue("the header is gone", headerWidth() == null)
        // and the card draws itself again where it is
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        val card = compose.onNodeWithTag("card", useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
        val pixel = bitmap.getPixel(card.center.x.roundToInt(), card.center.y.roundToInt())
        assertTrue("the card is red once the detail is gone (${Integer.toHexString(pixel)})", (pixel shr 16 and 0xff) - maxOf(pixel shr 8 and 0xff, pixel and 0xff) > 100)
    }

    private data class Track(val id: String, val plays: Int)

    @Test
    fun aDetailReEmittedUnderItsKeyShowsItsLatestObject() {
        var detail by mutableStateOf<Track?>(null)
        compose.setContent {
            DesktopKitTheme {
                androidx.compose.runtime.CompositionLocalProvider(LocalMotionTier provides MotionTier.Gesture) {
                    DetailTransition(detail = detail, onBack = { detail = null }, detailKey = { it.id }, list = { Text("Liste") }) { track ->
                        Text("${track.id}: ${track.plays}", Modifier.testTag("detail"))
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { detail = Track("album", 1) }
        compose.waitForIdle()
        compose.onNodeWithText("album: 1").assertIsDisplayed()
        // the same detail, a count further on: no transition, the new object shown
        compose.runOnIdle { detail = Track("album", 2) }
        compose.waitForIdle()
        compose.onNodeWithText("album: 2").assertIsDisplayed()
        compose.onNodeWithText("Liste").assertDoesNotExist()
        compose.runOnIdle { detail = null }
        compose.waitForIdle()
        compose.onNodeWithText("Liste").assertIsDisplayed()
    }

    @Test
    fun widerUpTheBackGestureShrinksTheDetailOverTheShadeAsBefore() {
        var detail by mutableStateOf<String?>("album")
        compose.setContent {
            DesktopKitTheme {
                androidx.compose.runtime.CompositionLocalProvider(LocalMotionTier provides MotionTier.Timed) {
                    DetailTransition(detail = detail, onBack = { detail = null }, list = { Text("Liste") }) {
                        Box(Modifier.fillMaxSize().testTag("detail"))
                    }
                }
            }
        }
        compose.waitForIdle()
        val full = compose.onNodeWithTag("detail").getBoundsInRoot()
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(back(0f))
            dispatcher.dispatchOnBackProgressed(back(0.5f))
        }
        compose.waitForIdle()
        // half way: shrunk toward 90 % as in 2.2.0, and the list is not behind it (a shade is)
        val half = compose.onNodeWithTag("detail").getBoundsInRoot()
        val ratio = (half.right - half.left) / (full.right - full.left)
        assertTrue("width at half way: $ratio of full", abs(ratio - 0.95f) < 0.01f)
        compose.onNodeWithText("Liste").assertDoesNotExist()
        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
        val restored = compose.onNodeWithTag("detail").getBoundsInRoot()
        assertEquals(full.right - full.left, restored.right - restored.left)
        assertEquals("album", detail)
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(back(0f))
            dispatcher.dispatchOnBackProgressed(back(1f))
            dispatcher.onBackPressed()
        }
        compose.waitForIdle()
        assertNull(detail)
        compose.onNodeWithText("Liste").assertIsDisplayed()
    }
}
