package desktopkit.input

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.SampleFile
import desktopkit.components.ColumnWidth
import desktopkit.components.DenseTable
import desktopkit.components.MenuEntry
import desktopkit.components.TableColumn
import desktopkit.sampleFiles
import desktopkit.theme.DeskTheme
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowUiAutomation
import kotlin.math.roundToInt

/**
 * A row's context menu with a mouse, frame by frame. Clicks go in the way Android delivers them
 * (to the topmost window that takes them: the focused menu takes every press in the app window),
 * and every frame records where each menu window is.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1000dp-h600dp-mdpi")
class ContextMenuDismissTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val names = sampleFiles.map { it.name }
    private val clicked = mutableListOf<String>()
    private val chosen = mutableListOf<String>()

    /** One record per menu window per frame. */
    private data class Frame(val menu: View, val at: IntOffset)

    private val frames = mutableListOf<Frame>()

    private fun table() {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Light, inputMode = InputMode.Pointer) {
                Box(Modifier.fillMaxSize().background(DeskTheme.colors.background)) {
                    DenseTable(
                        columns = listOf(
                            TableColumn<SampleFile>("Ad", ColumnWidth.Weight(1f)) { it.name },
                            TableColumn<SampleFile>("Tür", ColumnWidth.Fixed(140.dp)) { it.kind },
                        ),
                        rows = sampleFiles,
                        key = { it.name },
                        onRowClick = { file, _ -> clicked += file.name },
                        rowMenu = { file, _ -> listOf(MenuEntry.Item("Aç: ${file.name}", { chosen += file.name }), MenuEntry.Item("Sil", {})) },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    /** A point on row [index], [x] pixels from the left (mdpi: 1 dp = 1 px). */
    private fun point(index: Int, x: Float): Offset =
        Offset(x, compose.onNodeWithText(names[index]).fetchSemanticsNode().boundsInWindow.center.y)

    private fun Offset.rounded() = IntOffset(x.roundToInt(), y.roundToInt())

    /** The menu windows (pop-up panels) that are on screen now. */
    private fun menus(): List<View> {
        val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
        val field = global.javaClass.getDeclaredField("mViews").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val views = (field.get(global) as List<View>).toList()
        return views.filter { (it.layoutParams as? WindowManager.LayoutParams)?.type == WindowManager.LayoutParams.TYPE_APPLICATION_PANEL }
    }

    private fun View.at(): IntOffset = (layoutParams as WindowManager.LayoutParams).let { IntOffset(it.x, it.y) }

    private fun record() {
        menus().forEach { frames += Frame(it, it.at()) }
    }

    private fun mouse(action: Int, at: Offset, buttons: Int, downTime: Long): MotionEvent {
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE })
        val coords = arrayOf(MotionEvent.PointerCoords().apply { x = at.x; y = at.y; pressure = 1f; size = 1f })
        return MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, 1, properties, coords, 0, buttons,
            1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
    }

    /** A mouse click at [at] in window (= screen) coordinates, routed like Android's input dispatcher. */
    private fun click(at: Offset, button: Int) {
        compose.runOnUiThread {
            val down = SystemClock.uptimeMillis()
            ShadowUiAutomation.injectInputEvent(mouse(MotionEvent.ACTION_DOWN, at, button, down))
            ShadowUiAutomation.injectInputEvent(mouse(MotionEvent.ACTION_UP, at, 0, down))
        }
    }

    private fun rightClick(at: Offset) = click(at, MotionEvent.BUTTON_SECONDARY)

    private fun leftClick(at: Offset) = click(at, MotionEvent.BUTTON_PRIMARY)

    /** Opens the menu of [row] with a right click at [at] and lets it settle; returns its window. */
    private fun openAt(at: Offset): View {
        rightClick(at)
        compose.waitForIdle()
        val menu = menus().single()
        assertEquals(at.rounded(), menu.at())
        return menu
    }

    /** Runs [action], then frame by frame until every animation is over, recording each frame. */
    private fun framesOf(action: () -> Unit) {
        compose.mainClock.autoAdvance = false
        frames.clear()
        record()
        action()
        record()
        repeat(40) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            record()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    /** Frames before a new window has been measured and placed (still at the corner) say nothing. */
    private fun placed(of: List<Frame>) = of.filter { it.at != IntOffset.Zero }

    @Test
    fun leftClickOutsideClosesTheMenuWhereItIsAndDoesNothingElse() {
        table()
        val a = point(1, 100f)
        val b = point(4, 650f)
        val menu = openAt(a)

        framesOf { leftClick(b) }

        assertTrue("the menu should be closed", menus().isEmpty())
        assertTrue("frames were recorded", frames.isNotEmpty())
        frames.forEach { frame ->
            assertTrue("only the old menu may be on screen while it fades out: $frame", frame.menu === menu)
            assertEquals("the closing menu stays where it opened", a.rounded(), frame.at)
        }
        assertTrue("no menu at the click", frames.none { it.at == b.rounded() })
        assertEquals("the click that closed the menu must not reach the row", emptyList<String>(), clicked)
    }

    @Test
    fun rightClickOutsideOpensOneMenuThereAndTheOldOneNeverMovesThere() {
        table()
        val a = point(1, 100f)
        val b = point(4, 650f)
        val old = openAt(a)

        framesOf { rightClick(b) }

        val now = menus().single()
        assertNotSame(old, now)
        assertEquals(b.rounded(), now.at())
        compose.onNodeWithText("Aç: ${names[4]}").assertExists()
        frames.filter { it.menu === old }.forEach { assertEquals("the old menu closes where it was", a.rounded(), it.at) }
        placed(frames.filter { it.menu !== old }).forEach { assertEquals("the new menu opens at the click", b.rounded(), it.at) }
        assertEquals(emptyList<String>(), clicked)
    }

    @Test
    fun rightClickElsewhereOnTheSameRowReopensThere() {
        table()
        val a = point(1, 100f)
        val b = point(1, 650f)
        val old = openAt(a)

        framesOf { rightClick(b) }

        val now = menus().single()
        assertNotSame(old, now)
        assertEquals(b.rounded(), now.at())
        frames.filter { it.menu === old }.forEach { assertEquals(a.rounded(), it.at) }
        placed(frames.filter { it.menu !== old }).forEach { assertEquals(b.rounded(), it.at) }
    }

    @Test
    fun escapeClosesTheMenu() {
        table()
        val menu = openAt(point(2, 100f))
        compose.runOnUiThread {
            deliverKey(menu, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE))
            deliverKey(menu, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE))
        }
        compose.waitForIdle()
        assertTrue(menus().isEmpty())
    }

    @Test
    fun choosingAnItemClosesTheMenu() {
        table()
        openAt(point(3, 100f))
        compose.onNodeWithText("Aç: ${names[3]}").performClick()
        compose.waitForIdle()
        assertEquals(listOf(names[3]), chosen)
        assertTrue(menus().isEmpty())
    }

    @Test
    fun longPressStillOpensTheMenu() {
        table()
        compose.onNodeWithText(names[2]).performTouchInput { longClick(center) }
        compose.waitForIdle()
        assertEquals(1, menus().size)
        compose.onNodeWithText("Aç: ${names[2]}").assertExists()
        assertEquals(emptyList<String>(), clicked)
    }

    /**
     * A key to a window as Android's ViewRootImpl delivers it: to the views first, then, if none
     * handled it, to the unhandled-key listeners (API 28+). With the mouse nothing in the menu has
     * the focus, so Esc takes the second way.
     */
    private fun deliverKey(root: View, event: KeyEvent) {
        if (root.dispatchKeyEvent(event)) return
        View::class.java.getDeclaredMethod("dispatchUnhandledKeyEvent", KeyEvent::class.java)
            .apply { isAccessible = true }
            .invoke(root, event)
    }
}
