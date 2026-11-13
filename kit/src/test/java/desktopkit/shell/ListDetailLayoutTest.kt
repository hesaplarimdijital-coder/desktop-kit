package desktopkit.shell

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
 * The list and the detail side by side from tablet width up, one at a time below it, and their
 * state carried across (the design audit's high finding 1: DetailTransition swapped whole screens
 * at every width).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1000dp-h700dp-mdpi")
class ListDetailLayoutTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var widthClass by mutableStateOf(WidthClass.Expanded)
    private var chosen by mutableStateOf<String?>(null)

    private fun screen() {
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                val tier = if (widthClass == WidthClass.Compact) MotionTier.Gesture else MotionTier.Timed
                CompositionLocalProvider(LocalWidthClass provides widthClass, LocalMotionTier provides tier) {
                    ListDetailLayout(
                        detail = chosen,
                        onBack = { chosen = null },
                        list = {
                            Column {
                                listOf("Gelen", "Gönderilmiş").forEach { name ->
                                    Text("satır $name", Modifier.clickable { chosen = name })
                                }
                            }
                        },
                        placeholder = { Text("Bir ileti seç") },
                    ) { name ->
                        var count by rememberSaveable { mutableIntStateOf(0) }
                        Text("ayrıntı $name: $count", Modifier.testTag("detail").clickable { count++ })
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun wideTheListAndTheDetailAreSideBySide() {
        screen()
        compose.onNodeWithText("satır Gelen").assertExists()
        compose.onNodeWithText("Bir ileti seç").assertExists()
        compose.onNodeWithText("satır Gelen").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("satır Gönderilmiş").assertExists()
        compose.onNodeWithTag("detail").assertTextEquals("ayrıntı Gelen: 0")
        compose.onNodeWithText("Bir ileti seç").assertDoesNotExist()
    }

    @Test
    fun narrowOneAtATimeAndTheDetailKeepsItsStateAcross() {
        widthClass = WidthClass.Compact
        screen()
        compose.onNodeWithText("satır Gelen").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("satır Gönderilmiş").assertDoesNotExist()
        repeat(2) { compose.onNodeWithTag("detail").performClick() }
        compose.onNodeWithTag("detail").assertTextEquals("ayrıntı Gelen: 2")
        // the window widens (DeX, a tablet turned): the list comes beside the same detail
        widthClass = WidthClass.Expanded
        compose.waitForIdle()
        compose.onNodeWithText("satır Gönderilmiş").assertExists()
        compose.onNodeWithTag("detail").assertTextEquals("ayrıntı Gelen: 2")
        // and narrows again: the detail alone, still the same
        widthClass = WidthClass.Medium
        compose.waitForIdle()
        compose.onNodeWithTag("detail").assertTextEquals("ayrıntı Gelen: 2")
        compose.onNodeWithText("satır Gönderilmiş").assertDoesNotExist()
    }
}
