package desktopkit.theme

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.components.DeskSwitch
import desktopkit.components.SegmentedChoice
import desktopkit.settings.DeskPreferences
import desktopkit.settings.rememberDeskPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Haptics follow the finger only (M6); the wallpaper's accent is offered and readable (M5). */
@RunWith(AndroidJUnit4::class)
class HapticsAndAccentTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private class Recorder : HapticFeedback {
        val types = mutableListOf<HapticFeedbackType>()
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            types += hapticFeedbackType
        }
    }

    private fun controls(input: InputMode, recorder: Recorder) {
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides recorder) {
                DesktopKitTheme(inputMode = input) {
                    Column {
                        DeskSwitch(checked = false, onCheckedChange = {}, modifier = Modifier.testTag("switch"))
                        SegmentedChoice(listOf(1 to "Açık", 2 to "Koyu"), selected = 1, onSelect = {})
                    }
                }
            }
        }
    }

    @Test
    fun fingersFeelTheControls() {
        val recorder = Recorder()
        controls(InputMode.Touch, recorder)
        compose.onNodeWithTag("switch").performClick()
        compose.onNodeWithText("Koyu").performClick()
        assertEquals(listOf(HapticFeedbackType.ToggleOn, HapticFeedbackType.SegmentTick), recorder.types)
    }

    @Test
    fun aMouseFeelsNothing() {
        val recorder = Recorder()
        controls(InputMode.Pointer, recorder)
        compose.onNodeWithTag("switch").performClick()
        compose.onNodeWithText("Koyu").performClick()
        assertTrue(recorder.types.isEmpty())
    }

    @Test
    fun wallpaperAccentIsOfferedAfterThePresets() {
        lateinit var prefs: DeskPreferences
        compose.setContent { prefs = rememberDeskPreferences(dynamicAccent = true) }
        compose.waitForIdle()
        val dynamic = prefs.accents.last()
        assertEquals(Accent.DYNAMIC, dynamic.name)
        assertEquals(Accent.presets, prefs.accents.dropLast(1))
        // fitted like any custom colour: light text on its selection, dark text on its fill
        for (colors in listOf(DeskPalette.dark(dynamic), DeskPalette.light(dynamic), DeskPalette.oled(dynamic))) {
            assertTrue(contrastRatio(colors.onAccent, colors.accent) >= 4.5)
            assertTrue(contrastRatio(colors.onSelection, colors.selection) >= 4.5)
        }
    }
}
