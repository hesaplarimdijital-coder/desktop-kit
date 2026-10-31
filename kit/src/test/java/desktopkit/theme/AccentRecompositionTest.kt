package desktopkit.theme

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** An accent change cross-fades the colours without recomposing what does not read them. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class AccentRecompositionTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var accent by mutableStateOf(Accent.Blue)

    @Test
    fun onlyWhatReadsTheColoursRecomposesWhileTheyCrossFade() {
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, accent = accent) {
                Column {
                    Plain()
                    Reader()
                }
            }
        }
        compose.waitForIdle()
        val plainBefore = Counts.plain
        val readerBefore = Counts.reader
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { accent = Accent.presets.first { it != Accent.Blue } }
        repeat(15) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue("the reader followed the cross-fade (${Counts.reader - readerBefore} compositions)", Counts.reader - readerBefore >= 3)
        assertEquals("what does not read the colours was not recomposed", plainBefore, Counts.plain)
    }
}

private object Counts {
    var plain = 0
    var reader = 0
}

/** Reads no colour: skipped while the colours change. */
@Composable
private fun Plain() {
    SideEffect { Counts.plain++ }
    Box(Modifier.size(10.dp))
}

/** Reads the colours: follows the cross-fade. */
@Composable
private fun Reader() {
    val accent = DeskTheme.colors.accent
    SideEffect { Counts.reader++ }
    Box(Modifier.size(10.dp))
    check(accent.alpha > 0f)
}
