package desktopkit.components

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.DesktopKitRoot
import desktopkit.input.TabNavigation
import desktopkit.settings.rememberDeskPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.input.InputMode as ComposeInputMode

/**
 * The table is one Tab stop and the arrows, Home, End and the page keys move among its rows (the
 * design audit's high finding 2); a mouse dragged from a row that is not selected draws a frame and
 * selects the rows it touches, Esc puts the selection back (DeskMail's request 22).
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w900dp-h600dp-mdpi")
class TableKeyboardAndFrameTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val names = List(60) { "Parça %02d".format(it + 1) }
    private var selection by mutableStateOf<Set<Any>>(emptySet())
    private lateinit var view: android.view.View
    private lateinit var inputModeManager: InputModeManager

    @Before
    fun forgetKeys() = TabNavigation.reset()

    private fun screen() {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            view = LocalView.current
            inputModeManager = LocalInputModeManager.current
            DesktopKitRoot(rememberDeskPreferences()) {
                Column {
                    DeskButton("Önce", {})
                    DenseTable(
                        columns = listOf(TableColumn<String>("Ad", ColumnWidth.Weight(1f)) { it }),
                        rows = names,
                        key = { it },
                        onRowClick = { _, _ -> },
                        selectedKeys = selection,
                        onSelectionChange = { selection = it },
                        modifier = Modifier.fillMaxWidth().height(320.dp),
                    )
                    DeskButton("Sonra", {})
                }
            }
        }
        compose.waitForIdle()
    }

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

    @Test
    fun theRowsAreOneTabStopAndTheKeysMoveAmongThem() {
        selection = setOf(names[2])
        screen()
        compose.runOnIdle { inputModeManager.requestInputMode(ComposeInputMode.Keyboard) }
        compose.onNodeWithText("Önce").requestFocus()
        // Tab comes into the table at its selected row
        press(KeyEvent.KEYCODE_TAB)
        assertEquals(names[2], focusedText())
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals(names[3], focusedText())
        press(KeyEvent.KEYCODE_MOVE_END)
        assertEquals("End reaches the last row, scrolled into view", names.last(), focusedText())
        press(KeyEvent.KEYCODE_MOVE_HOME)
        assertEquals(names.first(), focusedText())
        press(KeyEvent.KEYCODE_PAGE_DOWN)
        val paged = names.indexOf(focusedText())
        assert(paged > 2) { "Page Down moved a page: $paged" }
        // and Tab leaves the table at once
        press(KeyEvent.KEYCODE_TAB)
        assertEquals("Sonra", focusedText())
    }

    @Test
    fun tabLeavesALongTableAtOnceAndItsStarsAreNoStops() {
        // a star column of buttons and many rows: Tab used to search the rows after the focused one, laying each out in
        // turn (a freeze in a long table), and to stop at every star on the way
        val many = List(400) { "Kayıt %03d".format(it + 1) }
        val laidOut = mutableSetOf<String>()
        var starred by mutableStateOf(setOf<String>())
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            view = LocalView.current
            inputModeManager = LocalInputModeManager.current
            DesktopKitRoot(rememberDeskPreferences()) {
                Column {
                    DeskButton("Önce", {})
                    DenseTable(
                        columns = listOf(
                            TableColumn<String>("Ad", ColumnWidth.Weight(1f)) { it },
                            TableColumn<String>(
                                "Yıldız", ColumnWidth.Fixed(40.dp), sortable = false,
                                cell = { item ->
                                    laidOut += item
                                    Text(if (item in starred) "★" else "☆")
                                },
                                onCellClick = { item -> starred = starred + item },
                                cellClickLabel = { "Yıldızla" },
                            ) { if (it in starred) "1" else "0" },
                        ),
                        rows = many,
                        key = { it },
                        onRowClick = { _, _ -> },
                        modifier = Modifier.fillMaxWidth().height(320.dp),
                    )
                    DeskButton("Sonra", {})
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { inputModeManager.requestInputMode(ComposeInputMode.Keyboard) }
        compose.onNodeWithText("Önce").requestFocus()
        press(KeyEvent.KEYCODE_TAB)
        val row = focusedText()
        assertTrue("Tab came into the table: $row", row?.contains("Kayıt 001") == true)
        laidOut.clear()
        press(KeyEvent.KEYCODE_TAB)
        assertEquals("Tab left the table at once, past the stars", "Sonra", focusedText())
        assertTrue("no rows laid out to look for a stop: ${laidOut.size}", laidOut.size < 40)
        press(KeyEvent.KEYCODE_TAB, shift = true)
        assertEquals("Shift+Tab comes back at the row", row, focusedText())
        press(KeyEvent.KEYCODE_TAB, shift = true)
        assertEquals("and leaves at once the other way", "Önce", focusedText())
    }

    @Test
    fun aFrameSelectsTheRowsItTouchesAndEscPutsTheSelectionBack() {
        selection = setOf(names[10])
        screen()
        val start = compose.onNodeWithText(names[1])
        start.performMouseInput {
            moveTo(center)
            press()
        }
        compose.onNodeWithText(names[4]).performMouseInput { moveTo(center) }
        compose.waitForIdle()
        assertEquals((1..4).map { names[it] }.toSet(), selection)
        press(KeyEvent.KEYCODE_ESCAPE)
        assertEquals("Esc put back the selection from before the frame", setOf(names[10]), selection)
        compose.onNodeWithText(names[4]).performMouseInput { release() }
    }

    @Test
    fun aFrameWithoutCtrlOrShiftReplacesTheSelection() {
        selection = setOf(names[10])
        screen()
        compose.onNodeWithText(names[1]).performMouseInput {
            moveTo(center)
            press()
        }
        compose.onNodeWithText(names[2]).performMouseInput { moveTo(center + Offset(0f, 1f)) }
        compose.onNodeWithText(names[2]).performMouseInput { release() }
        compose.waitForIdle()
        assertEquals(setOf(names[1], names[2]), selection)
    }
}
