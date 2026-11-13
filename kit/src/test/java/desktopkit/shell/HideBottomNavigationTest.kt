package desktopkit.shell

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.TestShell
import desktopkit.mediaSpec
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

/**
 * A full-screen task on the phone (a form with its own Cancel / Save bar) takes the bottom
 * navigation away with [HideBottomNavigation]: the screen gets the height down to the strip and
 * keeps it while it scrolls; the bar comes back when the task goes. Tablet and DeX keep their
 * navigation.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HideBottomNavigationTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun shell(widthClass: WidthClass = WidthClass.Compact, content: @Composable () -> Unit) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                TestShell(widthClass, startRoute = "music", spec = mediaSpec) { _, _ -> content() }
            }
        }
        compose.waitForIdle()
    }

    private fun near(expected: Dp, actual: Dp, label: String) =
        assertTrue("$label: expected $expected, was $actual", abs(expected.value - actual.value) < 1f)

    /** "Radyo" is an entry of the media spec's bottom bar. */
    private fun navShown(): Boolean = compose.onAllNodesWithText("Radyo").fetchSemanticsNodes().isNotEmpty()

    /** A form: fields one under the other, scrolling, its own bar at the bottom, padded by the shell. */
    @Composable
    private fun Form(hide: Boolean, onPadding: (PaddingValues) -> Unit = {}) {
        HideBottomNavigation(enabled = hide)
        val padding = rememberShellContentPadding()
        onPadding(padding)
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("form")) {
                repeat(30) { Text("Alan $it", Modifier.padding(16.dp)) }
            }
            Text("Kaydet", Modifier.padding(16.dp).testTag("save"))
        }
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun theFormGetsTheHeightAndKeepsIt() {
        var padding: PaddingValues? = null
        shell { Form(hide = true) { padding = it } }
        assertEquals("the bottom navigation is hidden", false, navShown())
        // bottom: only the status strip (1 + 48 dp) is left; the bar's 1 + 64 dp are given to the form
        near(49.dp, padding!!.calculateBottomPadding(), "bottom padding")
        val strip = compose.onNodeWithText("Kopyalanıyor: Yedek.zip").getBoundsInRoot()
        val save = compose.onNodeWithTag("save").getBoundsInRoot()
        val root = compose.onRoot().getBoundsInRoot()
        // the strip's text lies inside its 48 dp, with no 65 dp bar under it
        assertTrue("the strip sits at the bottom: ${strip.bottom} of ${root.bottom}", root.bottom - strip.bottom < 24.dp)

        // scrolling moves nothing but the form: no bar slides in or out under its buttons
        compose.onNodeWithTag("form").performTouchInput { swipeUp(startY = bottom - 100f, endY = top + 100f, durationMillis = 600) }
        compose.waitForIdle()
        assertEquals(false, navShown())
        near(save.top, compose.onNodeWithTag("save").getBoundsInRoot().top, "Kaydet after scrolling down")
        near(strip.top, compose.onNodeWithText("Kopyalanıyor: Yedek.zip").getBoundsInRoot().top, "strip after scrolling down")
        compose.onNodeWithTag("form").performTouchInput { swipeDown(startY = top + 100f, endY = top + 400f, durationMillis = 600) }
        compose.waitForIdle()
        assertEquals(false, navShown())
        near(save.top, compose.onNodeWithTag("save").getBoundsInRoot().top, "Kaydet after scrolling up")
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun theBarComesBackWhenTheFormGoes() {
        var hide by mutableStateOf(true)
        var padding: PaddingValues? = null
        shell { Form(hide) { padding = it } }
        assertEquals(false, navShown())
        hide = false
        compose.waitForIdle()
        assertEquals("enabled = false shows the bar", true, navShown())
        near(114.dp, padding!!.calculateBottomPadding(), "bottom padding with the bar")
        hide = true
        compose.waitForIdle()
        assertEquals(false, navShown())
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun leavingCompositionShowsTheBar() {
        var form by mutableStateOf(true)
        shell {
            if (form) Form(hide = true) else Box(Modifier.fillMaxSize())
        }
        assertEquals(false, navShown())
        form = false
        compose.waitForIdle()
        assertEquals(true, navShown())
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun tabletAndDexKeepTheirNavigation() {
        shell(WidthClass.Large) { Form(hide = true) }
        compose.onNodeWithText("Radyo").assertExists()
    }
}
