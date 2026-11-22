package desktopkit.editor

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.settings.DeskPreferences
import desktopkit.theme.DeskTheme
import desktopkit.theme.DesktopKitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Text that is not the editor zooms with the editor's gestures (a manual, a preview beside the editor). */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w800dp-h600dp-mdpi")
class TextZoomTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var zoom: CodeEditorZoom
    private lateinit var view: View
    private val list = LazyListState()

    private fun manual(prefs: DeskPreferences? = null) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            view = LocalView.current
            DesktopKitTheme {
                zoom = rememberCodeEditorZoom(prefs, key = prefs?.let { "manual" })
                LazyColumn(Modifier.fillMaxSize().textZoom(zoom).testTag("manual"), state = list) {
                    items(80) { index ->
                        Text("Paragraf $index", Modifier.focusable().testTag("p$index"), style = DeskTheme.type.body.zoomed(zoom))
                    }
                }
            }
        }
        compose.waitForIdle()
    }

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
    fun ctrlAndTheWheelZoomTheTextInsteadOfScrollingIt() {
        manual()
        val center = compose.onNodeWithTag("manual").fetchSemanticsNode().boundsInRoot.center
        wheel(center, 1f, ctrl = true)
        assertEquals(1.1f, zoom.scale, 0.001f)
        assertEquals("the text did not scroll", 0, list.firstVisibleItemIndex)
        wheel(center, -1f, ctrl = true)
        wheel(center, -1f, ctrl = true)
        assertEquals(0.9f, zoom.scale, 0.001f)
        // without Ctrl the wheel scrolls, as ever
        wheel(center, -3f, ctrl = false)
        assertEquals(0.9f, zoom.scale, 0.001f)
        assertTrue("scrolled", list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0)
    }

    @Test
    fun twoFingersSpreadTheText() {
        manual()
        compose.onNodeWithTag("manual").performTouchInput {
            pinch(center - Offset(50f, 0f), center - Offset(75f, 0f), center + Offset(50f, 0f), center + Offset(75f, 0f))
        }
        compose.waitForIdle()
        assertEquals("half as large again, in whole tenths once the fingers are up", 1.5f, zoom.scale, 0.001f)
    }

    @Test
    fun ctrlPlusMinusAndZeroOnTheKeyboard() {
        manual()
        val paragraph = compose.onNodeWithTag("p0")
        paragraph.requestFocus()
        paragraph.performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Equals) } }
        compose.runOnIdle { assertEquals(1.1f, zoom.scale, 0.001f) }
        paragraph.performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Minus) } }
        paragraph.performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Minus) } }
        compose.runOnIdle { assertEquals(0.9f, zoom.scale, 0.001f) }
        paragraph.performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Zero) } }
        compose.runOnIdle { assertEquals(1f, zoom.scale, 0.001f) }
    }

    @Test
    fun talkBackOffersLargerAndSmallerText() {
        manual()
        val actions = compose.onNodeWithTag("manual").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { actions.single { it.label == "Yazıyı büyüt" }.action() }
        compose.runOnIdle { assertEquals(1.1f, zoom.scale, 0.001f) }
        compose.runOnIdle { actions.single { it.label == "Yazıyı küçült" }.action() }
        compose.runOnIdle { assertEquals(1f, zoom.scale, 0.001f) }
    }

    @Test
    fun theSizeIsKeptUnderItsKey() {
        val shared = ApplicationProvider.getApplicationContext<android.content.Context>().getSharedPreferences("text-zoom", 0)
        shared.edit().clear().commit()
        manual(DeskPreferences(shared))
        compose.runOnIdle { zoom.zoomIn() }
        assertEquals(1.1f, DeskPreferences(shared).editorTextScale("manual"), 0.001f)
    }

    @Test
    fun aZoomedStyleGrowsWithItsLines() {
        val style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 1.sp)
        val zoomed = style.zoomed(CodeEditorZoom(1.5f))
        assertEquals(19.5f, zoomed.fontSize.value, 0.001f)
        assertEquals(27f, zoomed.lineHeight.value, 0.001f)
        assertEquals(1.5f, zoomed.letterSpacing.value, 0.001f)
        assertEquals(style, style.zoomed(CodeEditorZoom()))
    }
}
