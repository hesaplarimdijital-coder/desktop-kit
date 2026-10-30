package desktopkit.components

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.SampleFile
import desktopkit.sampleFiles
import desktopkit.settings.DeskPreferences
import desktopkit.screenshots.ShellScreenshotTest
import desktopkit.theme.DeskTheme
import desktopkit.theme.DeskType
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.input.InputMode as ComposeInputMode

/** DenseTable's multi-selection driven by real clicks and keys; also draws table-multi-dark.png. */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w900dp-h420dp-mdpi")
class DenseTableSelectionTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var selection by mutableStateOf<Set<Any>>(emptySet())
    private var toggleOnClick by mutableStateOf(false)
    private val clicked = mutableListOf<String>()
    private lateinit var inputModeManager: InputModeManager

    private val names = sampleFiles.map { it.name }

    private fun table(theme: ThemeMode = ThemeMode.Dark) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            inputModeManager = LocalInputModeManager.current
            // one sp larger with a pointer, as the type hook allows
            DesktopKitTheme(themeMode = theme, inputMode = InputMode.Pointer,
                type = { mode, fonts -> DeskType.forMode(mode, fonts, pointerStep = 1f) }) {
                Box(Modifier.fillMaxSize().background(DeskTheme.colors.background)) {
                    DenseTable(
                        columns = listOf(
                            TableColumn<SampleFile>("Ad", ColumnWidth.Weight(1f)) { it.name },
                            TableColumn<SampleFile>("Tür", ColumnWidth.Fixed(140.dp)) { it.kind },
                            TableColumn<SampleFile>("Boyut", ColumnWidth.Fixed(100.dp), alignEnd = true, mono = true) { it.size },
                        ),
                        rows = sampleFiles,
                        key = { it.name },
                        onRowClick = { file, _ -> clicked += file.name },
                        selectedKeys = selection,
                        onSelectionChange = { selection = it },
                        toggleOnClick = toggleOnClick,
                        rowMenu = { _, _ -> listOf(MenuEntry.Item("Sil", {})) },
                        rowMenuLabel = "İşlemler",
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun row(index: Int) = compose.onNodeWithText(names[index])

    private fun focus(index: Int) {
        compose.runOnIdle { inputModeManager.requestInputMode(ComposeInputMode.Keyboard) }
        row(index).requestFocus()
    }

    @Test
    fun clickSelectsOnlyThatRow() {
        // a mouse click (a finger's tap opens the row without selecting it: TouchSelectionTest)
        table()
        row(1).performMouseInput { click() }
        assertEquals(setOf(names[1]), selection)
        row(3).performMouseInput { click() }
        assertEquals(setOf(names[3]), selection)
        assertEquals(listOf(names[1], names[3]), clicked)
    }

    @Test
    fun toggleOnClickAddsAndRemoves() {
        toggleOnClick = true
        table()
        row(0).performClick()
        row(2).performClick()
        assertEquals(setOf(names[0], names[2]), selection)
        row(0).performClick()
        assertEquals(setOf(names[2]), selection)
        assertEquals(emptyList<String>(), clicked) // toggling taps do not open anything
    }

    @Test
    fun ctrlASelectsEveryRow() {
        table()
        focus(2)
        row(2).performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.A) } }
        assertEquals(names.toSet(), selection)
    }

    @Test
    fun shiftArrowsExtendTheSelection() {
        table()
        focus(1)
        row(1).performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionDown) } }
        compose.waitForIdle()
        row(2).performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionDown) } }
        assertEquals(setOf(names[1], names[2], names[3]), selection)
    }

    @Test
    fun menuOnASelectedRowKeepsTheSelection() {
        selection = setOf(names[0], names[1])
        table()
        row(1).performMouseInput { rightClick(center) }
        compose.waitForIdle()
        assertEquals(setOf(names[0], names[1]), selection)
    }

    @Test
    fun menuOnAnotherRowSelectsJustThatRow() {
        selection = setOf(names[0], names[1])
        table()
        row(4).performMouseInput { rightClick(center) }
        compose.waitForIdle()
        assertEquals(setOf(names[4]), selection)
    }

    @Test
    fun escapeClearsTheSelection() {
        selection = setOf(names[0], names[1])
        table()
        focus(1)
        row(1).performKeyInput { pressKey(Key.Escape) }
        assertEquals(emptySet<Any>(), selection)
    }

    /** The header's context menu is the column chooser; the choice is stored in DeskPreferences. */
    @Test
    fun columnChooserHidesAColumnAndRemembersIt() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = DeskPreferences(context.getSharedPreferences("columns-test", android.content.Context.MODE_PRIVATE))
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                Box(Modifier.fillMaxSize().background(DeskTheme.colors.background)) {
                    DenseTable(
                        columns = listOf(
                            TableColumn<SampleFile>("Ad", ColumnWidth.Weight(1f), id = "name") { it.name },
                            TableColumn<SampleFile>("Tür", ColumnWidth.Fixed(140.dp), id = "kind") { it.kind },
                            TableColumn<SampleFile>("Boyut", ColumnWidth.Fixed(100.dp), alignEnd = true, mono = true, id = "size") { it.size },
                        ),
                        rows = sampleFiles,
                        key = { it.name },
                        onRowClick = { _, _ -> },
                        columnsState = rememberTableColumnsState("files", prefs),
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Klasör").assertExists() // a "Tür" cell
        compose.onNodeWithText("Tür").performMouseInput { rightClick(center) }
        compose.waitForIdle()
        compose.onNode(hasText("Tür") and hasAnyAncestor(isPopup())).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Klasör").assertDoesNotExist()
        assertEquals(listOf("name", "kind", "size") to setOf("kind"), prefs.tableColumns("files"))
    }

    @Test
    fun screenshot() {
        selection = setOf(names[1], names[2], names[4])
        table()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save("table-multi-dark", bitmap)
    }
}
