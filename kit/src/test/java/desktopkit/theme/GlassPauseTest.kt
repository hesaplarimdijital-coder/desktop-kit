package desktopkit.theme

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.TestShell
import desktopkit.shell.WidthClass
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A screen with a WebView or a video pauses the shell's blur while it is shown: the glass draws its
 * translucent tint and the content is not recorded (DeskMail's request 20).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class GlassPauseTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun aPausedBlurDrawsTheTint() {
        assertEquals(GlassMode.Translucent, effectiveGlassMode(GlassMode.Blur, paused = true))
        assertEquals(GlassMode.Blur, effectiveGlassMode(GlassMode.Blur, paused = false))
        // battery saver's solid surfaces stay solid
        assertEquals(GlassMode.Solid, effectiveGlassMode(GlassMode.Solid, paused = true))
    }

    @Test
    fun theScreenPausesTheBlurOnlyWhileItIsShown() {
        var web by mutableStateOf(true)
        var holds: GlassHolds? = null
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                TestShell(WidthClass.Compact, startRoute = "documents", content = { _, _ ->
                    holds = LocalGlassHolds.current
                    if (web) PauseGlassBlur()
                    Text("ileti")
                })
            }
        }
        compose.waitForIdle()
        assertEquals(1, holds!!.count)
        web = false
        compose.waitForIdle()
        assertEquals(0, holds!!.count)
    }
}
