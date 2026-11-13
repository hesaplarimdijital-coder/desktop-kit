package desktopkit.components

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.DesktopKitRoot
import desktopkit.TestShell
import desktopkit.input.TabNavigation
import desktopkit.sampleArtwork
import desktopkit.settings.rememberDeskPreferences
import desktopkit.shell.WidthClass
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.input.InputMode as ComposeInputMode

/**
 * The design audit's high finding 2, the rest of it: a tab strip and a cover grid are one stop of the Tab
 * key each, the arrows move inside them; a sheet keeps the focus while it is up.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w900dp-h700dp-mdpi")
class FocusStopsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var view: android.view.View
    private lateinit var inputModeManager: InputModeManager

    @Before
    fun forgetKeys() = TabNavigation.reset()

    private fun press(keyCode: Int, shift: Boolean = false) {
        val meta = if (shift) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
        compose.runOnUiThread {
            view.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
            view.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0, meta))
        }
        compose.waitForIdle()
    }

    private fun focusedText(): String? = compose.onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull()?.let { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.joinToString()
    }

    private fun root(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            view = LocalView.current
            inputModeManager = LocalInputModeManager.current
            DesktopKitRoot(rememberDeskPreferences()) { content() }
        }
        compose.waitForIdle()
        compose.runOnIdle { inputModeManager.requestInputMode(ComposeInputMode.Keyboard) }
    }

    @Test
    fun aTabStripIsOneStopAndTheArrowsMoveInIt() {
        var selected by mutableStateOf(1)
        root {
            Column {
                DeskButton("Önce", {})
                TabStrip {
                    listOf("Birinci", "İkinci", "Üçüncü").forEachIndexed { i, name ->
                        DeskTab(name, selected = i == selected, onClick = { selected = i })
                    }
                }
                DeskButton("Sonra", {})
            }
        }
        compose.onNodeWithText("Önce").requestFocus()
        press(KeyEvent.KEYCODE_TAB)
        assertEquals("Tab comes in at the selected tab", "İkinci", focusedText())
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals("Üçüncü", focusedText())
        press(KeyEvent.KEYCODE_MOVE_HOME)
        assertEquals("Birinci", focusedText())
        press(KeyEvent.KEYCODE_TAB)
        assertEquals("Tab leaves the strip", "Sonra", focusedText())
        press(KeyEvent.KEYCODE_TAB, shift = true)
        assertEquals("and comes back at the selected tab", "İkinci", focusedText())
    }

    @Test
    fun aCoverGridIsOneStop() {
        val albums = (1..24).map { "Albüm $it" }
        root {
            Column {
                DeskButton("Önce", {})
                DeskGrid(albums, key = { it }, onItemClick = { _, _ -> }, modifier = Modifier.fillMaxWidth().height(480.dp)) { title ->
                    CoverCell(title, subtitle = "Sanatçı") { Box(Modifier.fillMaxSize().background(sampleArtwork[0])) }
                }
                DeskButton("Sonra", {})
            }
        }
        compose.onNodeWithText("Önce").requestFocus()
        press(KeyEvent.KEYCODE_TAB)
        val first = focusedText()
        assertEquals(true, first?.contains("Albüm 1"))
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertNotEquals(first, focusedText())
        assertEquals(true, focusedText()?.contains("Albüm 2"))
        // Tab goes through the focused cover's own buttons (Play, "⋯"), then out of the grid: no other cover
        val reached = mutableListOf<String?>()
        repeat(4) {
            if (focusedText() != "Sonra") {
                press(KeyEvent.KEYCODE_TAB)
                reached += focusedText()
            }
        }
        assertEquals("Tab left the grid: $reached", "Sonra", focusedText())
        assertEquals("no other cover on the way: $reached", emptyList<String>(), reached.filterNotNull().filter { "Albüm" in it })
    }

    @Test
    fun aSheetKeepsTheFocus() {
        var open by mutableStateOf(false)
        compose.setContent {
            view = LocalView.current
            inputModeManager = LocalInputModeManager.current
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                TestShell(WidthClass.Compact, content = { _, _ ->
                    Column {
                        DeskButton("Arkada", {})
                        Text("ekran")
                    }
                    GlassBottomSheet(visible = open, onDismiss = { open = false }, title = "Sayfa") {
                        DeskButton("Bir", {})
                        DeskButton("İki", {})
                    }
                })
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { inputModeManager.requestInputMode(ComposeInputMode.Keyboard) }
        open = true
        compose.waitForIdle()
        repeat(6) {
            press(KeyEvent.KEYCODE_TAB)
            assertNotEquals("Tab stays in the sheet", "Arkada", focusedText())
        }
    }
}
