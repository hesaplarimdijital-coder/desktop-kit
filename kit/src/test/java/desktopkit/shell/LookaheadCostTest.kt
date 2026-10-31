package desktopkit.shell

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.LocalMotionTier
import desktopkit.theme.MotionTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A list inside a [DetailTransition] scrolled at rest is measured once a frame: no lookahead pass while nothing moves. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class LookaheadCostTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var lookahead = 0
    private var main = 0

    private fun scroll(shared: Boolean): Pair<Int, Int> {
        compose.setContent {
            DesktopKitTheme {
                CompositionLocalProvider(LocalMotionTier provides MotionTier.Gesture) {
                    DetailTransition<String>(detail = null, onBack = {}, sharedElements = shared, list = {
                        LazyColumn(
                            Modifier
                                .fillMaxSize()
                                .testTag("list")
                                .layout { measurable, constraints ->
                                    if (isLookingAhead) lookahead++ else main++
                                    val placeable = measurable.measure(constraints)
                                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                                },
                        ) { items(200) { Text("Satır $it", Modifier.fillMaxWidth().height(48.dp)) } }
                    }) { Text(it) }
                }
            }
        }
        compose.waitForIdle()
        lookahead = 0
        main = 0
        compose.onNodeWithTag("list").performTouchInput { swipeUp(durationMillis = 600) }
        compose.waitForIdle()
        return lookahead to main
    }

    /** It was 57 lookahead measures for 57 main ones: every frame of the scroll measured the list twice. */
    @Test
    fun aListScrolledAtRestIsMeasuredOnceAFrame() {
        val (withLookahead, withMain) = scroll(shared = true)
        println("LOOKAHEAD-COST shared=true lookahead=$withLookahead main=$withMain")
        assertTrue("the scroll was measured: $withMain", withMain > 5)
        assertEquals("no lookahead pass while nothing moves", 0, withLookahead)
    }

    @Test
    fun withoutSharedElementsAsBefore() {
        val (withLookahead, withMain) = scroll(shared = false)
        println("LOOKAHEAD-COST shared=false lookahead=$withLookahead main=$withMain")
        assertEquals(0, withLookahead)
        assertTrue(withMain > 5)
    }
}
