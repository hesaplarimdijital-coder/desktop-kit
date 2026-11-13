package desktopkit.shell

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.DesktopKitRoot
import desktopkit.TestShell
import desktopkit.settings.rememberDeskPreferences
import desktopkit.theme.DeskTheme
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

/** The design audit's medium finding 10: a phone on its side is wide like a tablet but short; it stays a phone. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w780dp-h360dp")
class HeightClassTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun thresholds() {
        assertEquals(HeightClass.Compact, HeightClass.fromHeight(360f))
        assertEquals(HeightClass.Compact, HeightClass.fromHeight(479.9f))
        assertEquals(HeightClass.Medium, HeightClass.fromHeight(480f))
        assertEquals(HeightClass.Expanded, HeightClass.fromHeight(900f))
    }

    private var height by mutableStateOf(HeightClass.Compact)
    private var body: TextUnit = TextUnit.Unspecified
    private var base: TextUnit = TextUnit.Unspecified
    private var tier: MotionTier? = null

    @Test
    fun aShortWindowKeepsThePhonesTextAndMotion() {
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                base = DeskTheme.type.body.fontSize
                CompositionLocalProvider(LocalHeightClass provides height) {
                    TestShell(WidthClass.Medium, content = { _, _ ->
                        body = DeskTheme.type.body.fontSize
                        tier = LocalMotionTier.current
                        Text("ekran")
                    })
                }
            }
        }
        compose.waitForIdle()
        assertEquals("no larger text on a phone on its side", base, body)
        assertEquals(MotionTier.Gesture, tier)
        // a tablet of the same width, taller: one step larger, the tablet's timings
        height = HeightClass.Medium
        compose.waitForIdle()
        assertTrue("$body > $base", body.value > base.value)
        assertEquals(MotionTier.Timed, tier)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun theKeyboardDoesNotMakeAPhoneShort() {
        var seen: HeightClass? = null
        compose.setContent {
            // what the window keeps of its height while the on-screen keyboard is up (adjustResize)
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                DesktopKitRoot(rememberDeskPreferences()) {
                    seen = LocalHeightClass.current
                    Text("ekran")
                }
            }
        }
        compose.waitForIdle()
        assertEquals("the window's height, not what the keyboard leaves of it", HeightClass.Medium, seen)
    }
}
