package desktopkit.components

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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import desktopkit.theme.DeskTheme
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowUiAutomation

/**
 * Submenus of a pop-up menu: a line opens its menu beside it after the mouse has rested on it, at
 * once on a click, a tap or a key; levels nest; the arrow back, Esc and another line close one
 * level, an item or a press outside close them all. Every level is a window of its own: presses and
 * keys go in the way Android routes them (the first menu takes every press outside the submenus,
 * the last submenu opened has the keyboard), and the windows' places are read as in
 * [desktopkit.input.ContextMenuDismissTest].
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1000dp-h600dp-mdpi")
class SubmenuTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var open by mutableStateOf(true)
    private val chosen = mutableListOf<String>()

    private fun item(label: String, enabled: Boolean = true) = MenuEntry.Item(label, { chosen += label }, enabled = enabled)

    private val entries = listOf(
        item("Yeni"),
        MenuEntry.Submenu("Son açılanlar", listOf(item("Bulunamayan.ink", enabled = false), item("a.ink"), item("b.ink"), MenuEntry.Separator, item("Listeyi temizle"))),
        MenuEntry.Submenu("Parçalar", listOf(
            MenuEntry.Submenu("Düğüm", listOf(item("Düğüm başlığı"), item("Dikiş"))),
            MenuEntry.Submenu("Seçenek", listOf(item("Yapışkan seçenek"))),
            item("Yorum"),
        )),
        MenuEntry.Separator,
        MenuEntry.Submenu("Boş", emptyList()),
        MenuEntry.Submenu("Kapalı", listOf(item("Görünmez")), enabled = false),
        item("Kaydet"),
    )

    private fun menu(at: IntOffset = IntOffset(100, 80), inputMode: InputMode = InputMode.Pointer, rtl: Boolean = false) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                DesktopKitTheme(themeMode = ThemeMode.Light, inputMode = inputMode) {
                    Box(Modifier.fillMaxSize().background(DeskTheme.colors.background)) {
                        MenuPopup(visible = open, onDismiss = { open = false }, entries = if (open) entries else emptyList(), offset = at)
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Yeni").assertExists()
    }

    // ---------------------------------------------------------------- the windows

    private val global by lazy { Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null) }

    @Suppress("UNCHECKED_CAST")
    private fun <T> globalList(name: String): List<T> =
        (global.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(global) as List<T>).toList()

    private fun windows(): List<Pair<View, WindowManager.LayoutParams>> = globalList<View>("mViews").zip(globalList("mParams"))

    /** The menu windows in the order they opened: the first menu, its submenu, that one's submenu. */
    private fun menus(): List<Pair<View, WindowManager.LayoutParams>> {
        val decor = compose.activity.window.decorView
        return windows().filter { (view, _) -> view !== decor }
    }

    /** Where menu window [level] is in the app window (mdpi: px = dp). */
    private fun frame(level: Int): Rect {
        val (view, params) = menus()[level]
        return Rect(params.x.toFloat(), params.y.toFloat(), (params.x + view.width).toFloat(), (params.y + view.height).toFloat())
    }

    /** Where the line [text] is on screen; [level] is the menu window it is in. */
    private fun line(text: String, level: Int): Rect =
        compose.onNodeWithText(text).fetchSemanticsNode().boundsInWindow.translate(frame(level).topLeft)

    private fun mouse(action: Int, at: Offset, buttons: Int, downTime: Long): MotionEvent {
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE })
        val coords = arrayOf(MotionEvent.PointerCoords().apply { x = at.x; y = at.y; pressure = 1f; size = 1f })
        return MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, 1, properties, coords, 0, buttons,
            1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
    }

    /** A left click at [at] on screen, routed like Android's input dispatcher: to the topmost window that takes it. */
    private fun click(at: Offset) {
        compose.runOnUiThread {
            val down = SystemClock.uptimeMillis()
            ShadowUiAutomation.injectInputEvent(mouse(MotionEvent.ACTION_DOWN, at, MotionEvent.BUTTON_PRIMARY, down))
            ShadowUiAutomation.injectInputEvent(mouse(MotionEvent.ACTION_UP, at, 0, down))
        }
        compose.waitForIdle()
    }

    /** A finger's tap at [at] on screen, routed the same way. */
    private fun tap(at: Offset) {
        compose.runOnUiThread {
            val down = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, at.x, at.y, 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                ShadowUiAutomation.injectInputEvent(event)
                event.recycle()
            }
        }
        compose.waitForIdle()
    }

    /**
     * A key to the window that has the keyboard, as Android delivers it: the topmost window that
     * takes the focus gets it, its views first and then its unhandled-key listeners.
     */
    private fun key(code: Int) {
        compose.runOnUiThread {
            val decor = compose.activity.window.decorView
            val focused = menus().lastOrNull { (_, params) -> params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE == 0 }?.first ?: decor
            for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                val event = KeyEvent(action, code)
                if (!focused.dispatchKeyEvent(event)) {
                    View::class.java.getDeclaredMethod("dispatchUnhandledKeyEvent", KeyEvent::class.java)
                        .apply { isAccessible = true }
                        .invoke(focused, event)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun rest(text: String) {
        compose.onNodeWithText(text).performMouseInput { moveTo(center) }
    }

    private fun advance(millis: Long) {
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
    }

    private fun shown(text: String) = compose.onNodeWithText(text).assertExists()

    private fun gone(text: String) = compose.onNodeWithText(text).assertDoesNotExist()

    /**
     * A keyboard is in use: windows are not in touch mode, so lines take the focus. (The test
     * windows start in touch mode, as a phone's do.)
     */
    private fun keyboard() = InstrumentationRegistry.getInstrumentation().setInTouchMode(false)

    private fun focus(text: String) {
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        compose.onNodeWithText(text).assertIsFocused()
    }

    // ---------------------------------------------------------------- mouse

    @Test
    fun aRestingMouseOpensTheSubmenuAfterAShortWait() {
        menu()
        gone("a.ink")
        compose.mainClock.autoAdvance = false
        rest("Son açılanlar")
        advance(SUBMENU_HOVER_MS - 50)
        gone("a.ink")
        advance(100)
        shown("a.ink")
        assertEquals("the menu and its submenu", 2, menus().size)
        assertTrue("the menu stays open", open)
    }

    @Test
    fun aClickOpensItAtOnce() {
        menu()
        compose.mainClock.autoAdvance = false
        click(line("Son açılanlar", 0).center)
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        shown("a.ink")
        // a second click, after the resting pointer would have opened it anyway, does not close it
        advance(SUBMENU_HOVER_MS + 100)
        click(line("Son açılanlar", 0).center)
        advance(300)
        shown("a.ink")
        assertTrue(open)
    }

    @Test
    fun theSubmenuStaysWhileThePointerCrossesOtherLinesOnItsWayToIt() {
        menu()
        compose.mainClock.autoAdvance = false
        rest("Son açılanlar")
        advance(SUBMENU_HOVER_MS + 100)
        shown("a.ink")
        // down across "Parçalar" on the way to the submenu's lower lines: quicker than the wait
        compose.onNodeWithText("Parçalar").performMouseInput { moveTo(center) }
        advance(SUBMENU_HOVER_MS - 50)
        compose.onNodeWithText("Parçalar").performMouseInput { exit() }
        compose.onNodeWithText("Listeyi temizle").performMouseInput { moveTo(center) }
        advance(SUBMENU_HOVER_MS * 3)
        shown("Listeyi temizle")
        gone("Düğüm")
    }

    @Test
    fun aSlowPointerHeadingForTheSubmenuKeepsItOpenAndOneThatRestsElsewhereSwitches() {
        menu()
        compose.mainClock.autoAdvance = false
        rest("Son açılanlar")
        advance(SUBMENU_HOVER_MS + 100)
        shown("a.ink")
        // from the middle of "Son açılanlar" slowly toward the bottom of its submenu, across "Parçalar":
        // four moves, each well inside the wait, together three times as long as it
        val start = line("Son açılanlar", 0).center
        val target = Offset(frame(1).left, frame(1).bottom - 8f)
        val parcalar = line("Parçalar", 0)
        // where the straight way crosses the middle of "Parçalar"
        val t = (parcalar.center.y - start.y) / (target.y - start.y)
        val crossing = start + (target - start) * t
        for (step in 0..3) {
            val at = crossing + Offset(step * 2f, step * 1f)
            compose.onNodeWithText("Parçalar").performMouseInput { moveTo(at - parcalar.topLeft) }
            advance(SUBMENU_HOVER_MS - 50)
            shown("a.ink")
            gone("Düğüm")
        }
        // it stops there: "Parçalar" takes over
        advance(SUBMENU_HOVER_MS)
        shown("Düğüm")
        advance(300)
        gone("a.ink")
        assertEquals(2, menus().size)
    }

    @Test
    fun restingOnAPlainLineClosesTheSubmenuAndItsOwnSubmenus() {
        menu()
        click(line("Parçalar", 0).center)
        click(line("Düğüm", 1).center)
        shown("Dikiş")
        assertEquals(3, menus().size)
        compose.mainClock.autoAdvance = false
        rest("Kaydet")
        advance(SUBMENU_HOVER_MS + 50)
        advance(300)
        gone("Dikiş")
        gone("Düğüm")
        assertEquals(1, menus().size)
        assertTrue(open)
        // opened again, it starts without its own submenu
        compose.mainClock.autoAdvance = true
        click(line("Parçalar", 0).center)
        shown("Düğüm")
        gone("Dikiş")
    }

    // ---------------------------------------------------------------- touch

    @Test
    fun aTapOpensItAndASecondTapClosesIt() {
        menu(inputMode = InputMode.Touch)
        compose.onNodeWithText("Son açılanlar").performClick()
        compose.waitForIdle()
        shown("a.ink")
        compose.onNodeWithText("Son açılanlar").performClick()
        compose.waitForIdle()
        gone("a.ink")
        assertTrue("only the submenu closed", open)
        // touch lines are touch-sized, in the submenu too
        compose.onNodeWithText("Parçalar").performClick()
        compose.waitForIdle()
        assertTrue(compose.onNodeWithText("Yorum").fetchSemanticsNode().size.height >= 48)
    }

    // ---------------------------------------------------------------- keyboard

    @Test
    fun theArrowOpensItAndTheFirstEnabledLineHasTheFocus() {
        keyboard()
        menu()
        focus("Son açılanlar")
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        shown("a.ink")
        compose.onNodeWithText("a.ink").assertIsFocused()
        // down stays in the submenu
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithText("b.ink").assertIsFocused()
        // back: one level, and the focus is on the submenu's line again
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        gone("a.ink")
        compose.onNodeWithText("Son açılanlar").assertIsFocused()
        assertTrue(open)
    }

    @Test
    fun enterOpensItAndEscClosesOneLevelAtATime() {
        keyboard()
        menu()
        focus("Parçalar")
        key(KeyEvent.KEYCODE_ENTER)
        compose.onNodeWithText("Düğüm").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNodeWithText("Düğüm başlığı").assertIsFocused()
        assertEquals(3, menus().size)
        key(KeyEvent.KEYCODE_ESCAPE)
        gone("Düğüm başlığı")
        compose.onNodeWithText("Düğüm").assertIsFocused()
        assertEquals(2, menus().size)
        key(KeyEvent.KEYCODE_ESCAPE)
        gone("Düğüm")
        compose.onNodeWithText("Parçalar").assertIsFocused()
        assertTrue("Esc in a submenu closes only the submenu", open)
        key(KeyEvent.KEYCODE_ESCAPE)
        assertFalse("Esc in the first menu closes the menu, as before", open)
        assertTrue(menus().isEmpty())
    }

    @Test
    fun theArrowBackClosesASubmenuTheMouseOpened() {
        menu()
        click(line("Son açılanlar", 0).center)
        shown("a.ink")
        // nothing in the submenu has the focus; the key still reaches it
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        gone("a.ink")
        assertTrue(open)
    }

    @Test
    fun inARightToLeftLayoutTheArrowsChangePlaces() {
        keyboard()
        menu(rtl = true)
        focus("Son açılanlar")
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        gone("a.ink")
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        shown("a.ink")
        compose.onNodeWithText("a.ink").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        gone("a.ink")
    }

    // ---------------------------------------------------------------- closing

    @Test
    fun choosingAnItemClosesTheWholeChain() {
        menu()
        click(line("Parçalar", 0).center)
        click(line("Düğüm", 1).center)
        click(line("Dikiş", 2).center)
        assertEquals(listOf("Dikiş"), chosen)
        assertFalse(open)
        assertTrue("every menu window is gone", menus().isEmpty())
    }

    @Test
    fun anItemOfTheParentWorksWhileASubmenuIsOpen() {
        menu()
        click(line("Son açılanlar", 0).center)
        shown("a.ink")
        // the parent's own item under an open submenu: the press reaches the parent
        click(line("Kaydet", 0).center)
        assertEquals(listOf("Kaydet"), chosen)
        assertFalse(open)
        assertTrue(menus().isEmpty())
    }

    @Test
    fun aPressOutsideEveryMenuClosesThemAll() {
        menu()
        click(line("Parçalar", 0).center)
        click(line("Düğüm", 1).center)
        shown("Dikiş")
        // with a finger: Robolectric's mouse events carry no cursor position, which is what a view group finds the
        // child under a mouse press by, so a mouse press outside the menu would be handed to the menu's content
        tap(Offset(900f, 500f))
        assertFalse(open)
        assertTrue(menus().isEmpty())
        assertEquals("and does nothing else", emptyList<String>(), chosen)
    }

    @Test
    fun anEmptyOrDisabledSubmenuIsShownButDoesNotOpen() {
        menu()
        compose.onNodeWithText("Boş").assertIsNotEnabled()
        compose.onNodeWithText("Kapalı").assertIsNotEnabled()
        click(line("Kapalı", 0).center)
        gone("Görünmez")
        compose.mainClock.autoAdvance = false
        rest("Kapalı")
        advance(SUBMENU_HOVER_MS * 2)
        gone("Görünmez")
        assertEquals(1, menus().size)
        assertTrue(open)
    }

    // ---------------------------------------------------------------- where it opens

    @Test
    fun itOpensBesideItsLineOnTheEndSide() {
        menu()
        click(line("Parçalar", 0).center)
        click(line("Düğüm", 1).center)
        val first = frame(0)
        val second = frame(1)
        val third = frame(2)
        assertEquals("4 dp to the right of its menu", first.right + 4f, second.left)
        assertEquals("its first line level with the line that opened it", line("Parçalar", 0).top, line("Düğüm", 1).top)
        assertEquals(second.right + 4f, third.left)
        assertEquals(line("Düğüm", 1).top, line("Düğüm başlığı", 2).top)
    }

    @Test
    fun atTheWindowsEdgeItOpensOnTheOtherSide() {
        // the menu's right edge 20 px from the window's
        menu(at = IntOffset(760, 80))
        click(line("Parçalar", 0).center)
        val first = frame(0)
        val second = frame(1)
        assertEquals("to the left of its menu", first.left - 4f, second.right)
        assertTrue(second.left >= 0f)
        // the next level has room on the right again and takes it... or not: either way it is inside the window
        click(line("Düğüm", 1).center)
        val third = frame(2)
        assertTrue("inside the window: $third", third.left >= 0f && third.right <= 1000f)
        assertTrue("beside the second, not over it", third.right <= second.left || third.left >= second.right)
    }

    @Test
    fun nearTheBottomItMovesUpToStayInside() {
        menu(at = IntOffset(100, 380))
        click(line("Son açılanlar", 0).center)
        val second = frame(1)
        assertTrue("inside the window: $second", second.bottom <= 600f && second.top >= 0f)
        assertEquals(frame(0).right + 4f, second.left)
    }

    @Test
    fun inARightToLeftLayoutItOpensToTheLeftAndFlipsToTheRight() {
        menu(at = IntOffset(500, 80), rtl = true)
        click(line("Parçalar", 0).center)
        assertEquals("the end side is the left", frame(0).left - 4f, frame(1).right)
        click(line("Düğüm", 1).center)
        assertEquals(frame(1).left - 4f, frame(2).right)
    }

    @Test
    fun inARightToLeftLayoutItFlipsToTheRightAtTheLeftEdge() {
        menu(at = IntOffset(40, 80), rtl = true)
        click(line("Parçalar", 0).center)
        assertEquals(frame(0).right + 4f, frame(1).left)
    }

    // ---------------------------------------------------------------- TalkBack

    @Test
    fun talkBackHearsASubmenuAndWhetherItIsExpanded() {
        menu()
        val collapsed = compose.onNodeWithText("Son açılanlar").fetchSemanticsNode().config
        assertEquals("Submenu", collapsed.getOrNull(SemanticsProperties.StateDescription))
        assertNotNull("collapsed: it can be expanded", collapsed.getOrNull(SemanticsActions.Expand))
        assertNull(collapsed.getOrNull(SemanticsActions.Collapse))

        compose.onNodeWithText("Son açılanlar").performSemanticsAction(SemanticsActions.Expand)
        compose.waitForIdle()
        shown("a.ink")
        val expanded = compose.onNodeWithText("Son açılanlar").fetchSemanticsNode().config
        assertNotNull("expanded: it can be collapsed", expanded.getOrNull(SemanticsActions.Collapse))
        assertNull(expanded.getOrNull(SemanticsActions.Expand))
        // the submenu's window is named after its line
        val pane = compose.onNodeWithText("a.ink").fetchSemanticsNode().parent
        assertEquals("Son açılanlar", pane?.config?.getOrNull(SemanticsProperties.PaneTitle))

        compose.onNodeWithText("Son açılanlar").performSemanticsAction(SemanticsActions.Collapse)
        compose.waitForIdle()
        gone("a.ink")
        // one that cannot open offers neither
        val empty = compose.onNodeWithText("Boş").fetchSemanticsNode().config
        assertNull(empty.getOrNull(SemanticsActions.Expand))
        assertEquals("Submenu", empty.getOrNull(SemanticsProperties.StateDescription))
    }
}
