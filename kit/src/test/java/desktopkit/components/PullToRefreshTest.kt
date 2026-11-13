package desktopkit.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.SampleTrackList
import desktopkit.TestShell
import desktopkit.shell.WidthClass
import desktopkit.shell.rememberShellContentPadding
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.LocalMotionTier
import desktopkit.theme.MotionTier
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** DeskMail's request 14: pull a list down to refresh it, with a finger only. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-mdpi")
class PullToRefreshTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var refreshing by mutableStateOf(false)
    private var refreshes = 0

    private fun list(inputMode: InputMode) {
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = inputMode) {
                PullToRefresh(refreshing = refreshing, onRefresh = { refreshes++; refreshing = true }) {
                    LazyColumn(Modifier.fillMaxSize().testTag("list")) {
                        items(30) { i -> Text("İleti $i", Modifier.fillMaxWidth().height(56.dp)) }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aLongPullRefreshesAndTheDiscStaysWhileItDoes() {
        list(InputMode.Touch)
        compose.onNodeWithTag("list").performTouchInput { swipeDown(startY = top + 10f, endY = top + 400f, durationMillis = 300) }
        compose.waitForIdle()
        assertEquals(1, refreshes)
        compose.onNodeWithContentDescription("Refreshing").assertExists()
        refreshing = false
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Refreshing").assertDoesNotExist()
    }

    @Test
    fun aShortPullDoesNot() {
        list(InputMode.Touch)
        compose.onNodeWithTag("list").performTouchInput { swipeDown(startY = top + 10f, endY = top + 110f, durationMillis = 300) }
        compose.waitForIdle()
        assertEquals(0, refreshes)
    }

    @Test
    fun inAShellTheDiscComesOutUnderTheBarOrAtTheListsOwnTop() {
        var topPad = 0.dp
        var header by mutableStateOf(false)
        refreshing = true
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                TestShell(WidthClass.Compact, content = { _, _ ->
                    val padding = rememberShellContentPadding()
                    topPad = padding.calculateTopPadding()
                    if (!header) {
                        // the list lies under the glass bar
                        PullToRefresh(refreshing, onRefresh = {}, modifier = Modifier.testTag("box")) { SampleTrackList() }
                    } else {
                        // a field above the list, below the bar
                        Column(Modifier.fillMaxSize().padding(top = topPad)) {
                            Box(Modifier.fillMaxWidth().height(48.dp))
                            PullToRefresh(refreshing, onRefresh = {}, modifier = Modifier.testTag("box")) {
                                LazyColumn(Modifier.fillMaxSize()) { items(30) { i -> Text("İleti $i", Modifier.fillMaxWidth().height(56.dp)) } }
                            }
                        }
                    }
                })
            }
        }
        compose.waitForIdle()
        val disc = { compose.onNodeWithContentDescription("Refreshing").fetchSemanticsNode().boundsInRoot }
        val box = { compose.onNodeWithTag("box").fetchSemanticsNode().boundsInRoot }
        // at rest the disc is 20 dp below where it comes out: three quarters of the 80 dp threshold, less its own 40 dp
        assertTrue("a bar above: $topPad", topPad > 0.dp)
        assertEquals(box().top + topPad.value + 20f, disc().top, 1f)
        header = true
        compose.waitForIdle()
        assertEquals("at the list's top, not a bar's height lower", box().top + 20f, disc().top, 1f)
    }

    @Test
    fun withReducedMotionTheDiscJumpsToItsPlace() {
        // the app is refreshing as the list comes: the disc comes down by itself
        refreshing = true
        compose.mainClock.autoAdvance = false
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                CompositionLocalProvider(LocalMotionTier provides MotionTier.Reduced) {
                    PullToRefresh(refreshing = refreshing, onRefresh = {}, modifier = Modifier.testTag("box")) {
                        LazyColumn(Modifier.fillMaxSize()) { items(30) { i -> Text("İleti $i", Modifier.fillMaxWidth().height(56.dp)) } }
                    }
                }
            }
        }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        val top = compose.onNodeWithTag("box").fetchSemanticsNode().boundsInRoot.top
        // a spring would still be on its way three frames in
        assertEquals("at rest at once", top + 20f, compose.onNodeWithContentDescription("Refreshing").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test
    fun theMouseAndTheKeyboardLeaveItAlone() {
        list(InputMode.Pointer)
        compose.onNodeWithTag("list").performTouchInput { swipeDown(startY = top + 10f, endY = top + 400f, durationMillis = 300) }
        compose.waitForIdle()
        assertEquals(0, refreshes)
    }
}
