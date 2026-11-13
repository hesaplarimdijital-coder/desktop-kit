package desktopkit.shell

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.LocalMotionTier
import desktopkit.theme.MotionTier
import desktopkit.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The list leaves composition while a detail covers it; what it remembered with rememberSaveable
 * (a scroll position, open sections, a typed filter) is there again when it comes back. 2.4.0 built
 * it anew: DeskMail's settings were back at the top after the log was opened and closed (request 9).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class DetailListStateTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun onAPhone() = theListFindsItsStateWhenTheDetailCloses(MotionTier.Gesture)

    @Test
    fun widerUp() = theListFindsItsStateWhenTheDetailCloses(MotionTier.Timed)

    private fun theListFindsItsStateWhenTheDetailCloses(tier: MotionTier) {
        run {
            var detail by mutableStateOf<String?>(null)
            compose.setContent {
                DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                    CompositionLocalProvider(LocalMotionTier provides tier) {
                        DetailTransition(detail = detail, onBack = { detail = null }, list = {
                            var count by rememberSaveable { mutableIntStateOf(0) }
                            Text("liste: $count", Modifier.testTag("list").clickable { count++ })
                        }) { shown -> Text("ayrıntı: $shown") }
                    }
                }
            }
            compose.waitForIdle()
            repeat(3) { compose.onNodeWithTag("list").performClick() }
            compose.onNodeWithTag("list").assertTextEquals("liste: 3")
            detail = "günlük"
            compose.waitForIdle()
            compose.onNodeWithText("ayrıntı: günlük").assertExists()
            compose.onNodeWithTag("list").assertDoesNotExist()
            detail = null
            compose.waitForIdle()
            compose.onNodeWithTag("list").assertTextEquals("liste: 3")
        }
    }
}
