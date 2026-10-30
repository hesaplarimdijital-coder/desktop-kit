package desktopkit.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.DesktopKitRoot
import desktopkit.TestShell
import desktopkit.sampleArtwork
import desktopkit.settings.rememberDeskPreferences
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import androidx.compose.ui.input.InputMode as ComposeInputMode

/** Where the keyboard focus goes in tabs, the command palette and sheets, and what TalkBack can do there. */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class KeyboardFocusTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var inputModeManager: InputModeManager

    /** Clickable elements take the focus from a keyboard, as on DeX. */
    private fun keyboard() = compose.runOnIdle { inputModeManager.requestInputMode(ComposeInputMode.Keyboard) }

    @Test
    fun tabMovesIntoACloseButtonAndOnToTheNextTab() {
        var closed = ""
        compose.setContent {
            inputModeManager = LocalInputModeManager.current
            DesktopKitTheme(inputMode = InputMode.Pointer) {
                TabStrip {
                    DeskTab("Albümler", selected = true, onClick = {})
                    DeskTab("Arama: Kış", selected = false, onClick = {}, Modifier.testTag("search"), onClose = { closed = "search" })
                    DeskTab("İndirilenler", selected = false, onClick = {}, Modifier.testTag("downloads"))
                }
            }
        }
        keyboard()
        compose.onNodeWithTag("search").requestFocus()
        compose.onNodeWithTag("search").performKeyInput { pressKey(Key.Tab) }
        compose.waitForIdle()
        // the close button of an unselected tab shows while the focus is in the tab, also on itself
        compose.onNode(hasContentDescription("Sekmeyi kapat") and isFocused()).assertExists()
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.Tab) }
        compose.waitForIdle()
        compose.onNodeWithTag("downloads").assertIsFocused()
        assertEquals("", closed)
    }

    @Test
    fun talkBackClosesATabWhoseButtonIsHidden() {
        var closed = false
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Pointer) {
                TabStrip {
                    DeskTab("Arama: Kış", selected = false, onClick = {}, Modifier.testTag("search"), onClose = { closed = true })
                }
            }
        }
        val actions = compose.onNodeWithTag("search").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { actions.single { it.label == "Sekmeyi kapat" }.action() }
        assertTrue(closed)
    }

    private val items = listOf(
        PaletteItem("albums", "Albümler", "Ekranlar", {}),
        PaletteItem("files", "Dosyalar", "Ekranlar", {}),
    )

    @Test
    fun thePaletteKeepsTheFocusAndGivesItBack() {
        val palette = CommandPaletteState()
        compose.setContent {
            inputModeManager = LocalInputModeManager.current
            DesktopKitRoot(rememberDeskPreferences()) { widthClass ->
                TestShell(
                    widthClass,
                    overlays = { CommandPalette(palette, items = { items }) },
                    content = { _, _ ->
                        Column {
                            DeskButton("Kaydet", {}, Modifier.testTag("save"))
                            DeskButton("Vazgeç", {})
                        }
                    },
                )
            }
        }
        keyboard()
        compose.onNodeWithTag("save").requestFocus()
        compose.runOnIdle { palette.open() }
        compose.waitForIdle()
        val field = compose.onNode(hasSetTextAction() and isFocused())
        field.assertExists()
        // with no filters Tab stays in the field; the rows never take the focus
        field.performKeyInput { pressKey(Key.Tab); pressKey(Key.Tab) }
        compose.waitForIdle()
        compose.onNode(hasSetTextAction() and isFocused()).assertExists()
        assertTrue(palette.isOpen)
        compose.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Escape) }
        compose.waitForIdle()
        // closed: the button that had the focus has it again
        compose.onNodeWithTag("save").assertIsFocused()
    }

    @Test
    fun aSheetGivesTheFocusBack() {
        var open by mutableStateOf(false)
        compose.setContent {
            inputModeManager = LocalInputModeManager.current
            DesktopKitRoot(rememberDeskPreferences()) { widthClass ->
                TestShell(widthClass, content = { _, _ ->
                    Box(Modifier.fillMaxSize()) {
                        DeskButton("Paylaş", {}, Modifier.testTag("share"))
                        GlassBottomSheet(visible = open, onDismiss = { open = false }, title = "Paylaş") {
                            DeskButton("Bağlantıyı kopyala", {})
                        }
                    }
                })
            }
        }
        keyboard()
        compose.onNodeWithTag("share").requestFocus()
        open = true
        compose.waitForIdle()
        compose.onNodeWithTag("share").assertIsNotFocused()
        open = false
        compose.waitForIdle()
        compose.onNodeWithTag("share").assertIsFocused()
    }

    @Test
    @Config(qualifiers = "tr-w1000dp-h700dp-mdpi")
    fun talkBackPlaysACover() {
        var played = ""
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Pointer) {
                DeskGrid(listOf("Kış Bahçesi", "Mavi Hat"), key = { it }, onItemClick = { _, _ -> }) { title ->
                    CoverCell(title, subtitle = "Deniz Aras", onPlay = { played = title }, playLabel = "$title oynat") {
                        Box(Modifier.fillMaxSize().background(sampleArtwork[0]))
                    }
                }
            }
        }
        compose.waitForIdle()
        val actions = compose.onNodeWithText("Mavi Hat").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { actions.single { it.label == "Mavi Hat oynat" }.action() }
        assertEquals("Mavi Hat", played)
    }

    @Test
    @Config(qualifiers = "tr-w1000dp-h700dp-mdpi")
    fun aCoverCellsPlayHasADefaultName() {
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Touch) {
                DeskGrid(listOf("Kış Bahçesi"), key = { it }, onItemClick = { _, _ -> }, itemMenu = { _, _ -> emptyList() }) { title ->
                    CoverCell(title, onPlay = {}) { Text("") }
                }
            }
        }
        compose.waitForIdle()
        val labels = compose.onNodeWithText("Kış Bahçesi").fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        // the menu's action and Play's, side by side
        assertEquals(listOf("Diğer seçenekler", "Oynat"), labels)
    }
}
