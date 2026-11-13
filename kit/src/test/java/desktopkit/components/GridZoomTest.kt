package desktopkit.components

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.sampleArtwork
import desktopkit.settings.DeskPreferences
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.input.InputMode as ComposeInputMode

/** The covers made larger or smaller by the user (the design audit: a grid had one size for every screen and eye). */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1000dp-h700dp-mdpi")
class GridZoomTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val albums = (1..40).map { "Albüm $it" }
    private lateinit var zoom: GridZoom
    private lateinit var state: LazyGridState
    private lateinit var view: View
    private lateinit var inputModeManager: InputModeManager

    private fun grid(inputMode: InputMode = InputMode.Pointer, prefs: DeskPreferences? = null) {
        state = LazyGridState()
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            view = LocalView.current
            inputModeManager = LocalInputModeManager.current
            DesktopKitTheme(inputMode = inputMode) {
                zoom = rememberGridZoom(defaultCellWidth = 136.dp, prefs = prefs, key = prefs?.let { "albums" })
                DeskGrid(albums, key = { it }, onItemClick = { _, _ -> }, state = state, zoom = zoom,
                    modifier = Modifier.testTag("grid")) { title ->
                    CoverCell(title, subtitle = "Sanatçı") { Box(Modifier.fillMaxSize().background(sampleArtwork[0])) }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun columns() = state.layoutInfo.visibleItemsInfo.maxOf { it.column } + 1

    /** A mouse wheel turned [notches] away from the user (negative: toward), over [at]. */
    private fun wheel(at: Offset, notches: Float, ctrl: Boolean) {
        val properties = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        }
        val coords = MotionEvent.PointerCoords().apply {
            x = at.x
            y = at.y
            setAxisValue(MotionEvent.AXIS_VSCROLL, notches)
        }
        val now = SystemClock.uptimeMillis()
        val meta = if (ctrl) KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON else 0
        val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, arrayOf(properties), arrayOf(coords),
            meta, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
        compose.runOnUiThread { view.dispatchGenericMotionEvent(event) }
        event.recycle()
        compose.waitForIdle()
    }

    @Test
    fun ctrlAndTheWheelMakeTheCoversLargerAndSmaller() {
        grid()
        val before = columns()
        val center = compose.onNodeWithTag("grid").fetchSemanticsNode().boundsInRoot.center
        wheel(center, 1f, ctrl = true)
        assertTrue("larger: ${zoom.cellWidth}", zoom.cellWidth > 136.dp)
        assertTrue("fewer columns: ${columns()} < $before", columns() < before)
        assertEquals("the grid did not scroll", 0, state.firstVisibleItemIndex)
        wheel(center, -1f, ctrl = true)
        wheel(center, -1f, ctrl = true)
        assertTrue("smaller: ${zoom.cellWidth}", zoom.cellWidth < 136.dp)
        // without Ctrl the wheel scrolls, as ever
        val width = zoom.cellWidth
        wheel(center, -3f, ctrl = false)
        assertEquals(width, zoom.cellWidth)
    }

    @Test
    fun twoFingersSpreadTheCovers() {
        grid(InputMode.Touch)
        compose.onNodeWithTag("grid").performTouchInput {
            pinch(center - Offset(50f, 0f), center - Offset(75f, 0f), center + Offset(50f, 0f), center + Offset(75f, 0f))
        }
        compose.waitForIdle()
        val width = zoom.cellWidth.value
        assertTrue("half as wide again: $width", width in 196f..212f)
        assertEquals("a whole multiple of 4 dp once the fingers are up", 0f, width % 4f)
    }

    @Test
    fun ctrlPlusMinusAndZeroOnTheKeyboard() {
        grid()
        compose.runOnIdle { inputModeManager.requestInputMode(ComposeInputMode.Keyboard) }
        compose.onNodeWithText("Albüm 1").requestFocus()
        compose.onNodeWithText("Albüm 1").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Minus) } }
        compose.waitForIdle()
        assertEquals(108.dp, zoom.cellWidth)
        compose.onNodeWithText("Albüm 1").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Zero) } }
        compose.waitForIdle()
        assertEquals(136.dp, zoom.cellWidth)
        compose.onNodeWithText("Albüm 1").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Equals) } }
        compose.waitForIdle()
        assertEquals(172.dp, zoom.cellWidth)
    }

    @Test
    fun theKeysGoByTheCharacterTheyType() {
        // this machine's keyboard is a US one: Shift+− types "_", Shift+= types "+", Shift+0 types ")"
        grid()
        compose.runOnIdle { inputModeManager.requestInputMode(ComposeInputMode.Keyboard) }
        compose.onNodeWithText("Albüm 1").requestFocus()
        val cell = compose.onNodeWithText("Albüm 1")
        cell.performKeyInput { withKeyDown(Key.CtrlLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.Minus) } } }
        compose.waitForIdle()
        assertEquals("an underscore is not a minus", 136.dp, zoom.cellWidth)
        cell.performKeyInput { withKeyDown(Key.CtrlLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.Equals) } } }
        compose.waitForIdle()
        assertEquals("a plus, as typed", 172.dp, zoom.cellWidth)
        cell.performKeyInput { withKeyDown(Key.CtrlLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.Zero) } } }
        compose.waitForIdle()
        assertEquals("a bracket is not a zero", 172.dp, zoom.cellWidth)
        cell.performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.NumPadSubtract) } }
        compose.waitForIdle()
        assertEquals("the keypad's minus", 136.dp, zoom.cellWidth)
    }

    @Test
    fun aChosenSizeOutlivesANewDefault() {
        // the default is larger for a finger: touching the screen after the mouse changes it
        var default by mutableStateOf(136.dp)
        compose.setContent {
            DesktopKitTheme {
                zoom = rememberGridZoom(defaultCellWidth = default)
            }
        }
        compose.runOnIdle { default = 152.dp }
        compose.runOnIdle { assertEquals("nothing chosen: the default", 152.dp, zoom.cellWidth) }
        compose.runOnIdle { zoom.zoomIn() }
        compose.runOnIdle { assertEquals(192.dp, zoom.cellWidth) }
        compose.runOnIdle { default = 136.dp }
        compose.runOnIdle { assertEquals("the size the user chose stays", 192.dp, zoom.cellWidth) }
        compose.runOnIdle { zoom.reset() }
        compose.runOnIdle { assertEquals("and Ctrl+0 goes back to the default of now", 136.dp, zoom.cellWidth) }
    }

    @Test
    fun theChosenSizeIsKept() {
        val shared = ApplicationProvider.getApplicationContext<android.content.Context>().getSharedPreferences("zoom", 0)
        grid(prefs = DeskPreferences(shared))
        compose.runOnIdle { zoom.zoomIn() }
        assertEquals(172f, DeskPreferences(shared).gridCellWidth("albums"))
        // back to the default: nothing is kept, and the grid follows the default again
        compose.runOnIdle { zoom.reset() }
        assertTrue(DeskPreferences(shared).gridCellWidth("albums").isNaN())
    }
}
