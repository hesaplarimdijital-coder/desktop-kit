package desktopkit.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
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
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertEquals
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
    fun theMouseAndTheKeyboardLeaveItAlone() {
        list(InputMode.Pointer)
        compose.onNodeWithTag("list").performTouchInput { swipeDown(startY = top + 10f, endY = top + 400f, durationMillis = 300) }
        compose.waitForIdle()
        assertEquals(0, refreshes)
    }
}
