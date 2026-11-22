package desktopkit.components

import android.view.KeyEvent
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import desktopkit.TestShell
import desktopkit.mediaSpec
import desktopkit.shell.LocalWidthClass
import desktopkit.shell.WidthClass
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowUiAutomation
import androidx.compose.ui.input.InputMode as ComposeInputMode

/**
 * Submenus in the phone's "⋯" sheet: a submenu's line opens its entries as a page of the same
 * sheet, with the submenu's label and a back button for a head; Back (the predictive gesture too),
 * Esc and the button go up one page before the sheet closes; the sheet is as high as its page, a
 * page gone back to is where it was, and with a keyboard the focus follows the pages.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class SheetSubmenuTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var open by mutableStateOf(false)
    private var wrap by mutableStateOf(false)
    private var density by mutableStateOf(InputMode.Touch)
    private val chosen = mutableListOf<String>()

    private fun item(label: String) = MenuEntry.Item(label, { chosen += label })

    /** Built anew on every composition, as apps do: the page must follow the entries, not hold on to them. */
    private fun groups() = listOf(
        ActionGroup(null, listOf(
            item("Aç"),
            MenuEntry.Submenu("Parçalar", icon = DeskIcons.Folder, entries = listOf(
                item("Düğüm"),
                MenuEntry.Item("Sözcük kaydır", { wrap = !wrap }, checked = wrap),
                MenuEntry.Separator,
                MenuEntry.Submenu("Derin", listOf(item("En alt"))),
            )),
        )),
        ActionGroup("Diğer", listOf(item("Kaydet"), MenuEntry.Submenu("Boş", emptyList()))),
    )

    private lateinit var inputModeManager: InputModeManager

    private fun sheet(groups: () -> List<ActionGroup> = ::groups) {
        compose.setContent {
            inputModeManager = LocalInputModeManager.current
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = density) {
                TestShell(WidthClass.Compact, content = { _, _ ->
                    MoreSheet(visible = open, onDismiss = { open = false }, groups = groups(), title = "İşlemler")
                })
            }
        }
        compose.waitForIdle()
        open = true
        compose.waitForIdle()
        compose.onNodeWithText("İşlemler").assertExists()
    }

    private fun tap(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun key(code: Int) {
        compose.runOnUiThread {
            ShadowUiAutomation.injectInputEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            ShadowUiAutomation.injectInputEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
        compose.waitForIdle()
    }

    private fun shown(text: String) = compose.onNodeWithText(text).assertExists()

    private fun gone(text: String) = compose.onNodeWithText(text).assertDoesNotExist()

    /** The sheet, by the name TalkBack hears for it. */
    private fun pane(title: String) = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title))

    @Test
    fun aSubmenuOpensAsAPageOfTheSameSheet() {
        sheet()
        tap("Parçalar")
        shown("Düğüm")
        gone("Aç")
        gone("İşlemler")
        assertTrue("the sheet stays open", open)
        // the page's head: the submenu's label as a heading, and a back button before it
        val head = compose.onNodeWithText("Parçalar").fetchSemanticsNode()
        assertTrue(head.config.contains(SemanticsProperties.Heading))
        compose.onNodeWithContentDescription("Back").assertExists()
        // TalkBack hears the sheet by the page's name
        pane("Parçalar").assertExists()
        // and a submenu on that page goes deeper
        tap("Derin")
        shown("En alt")
        gone("Düğüm")
        pane("Derin").assertExists()
    }

    @Test
    fun backGoesUpOnePageBeforeItClosesTheSheet() {
        sheet()
        tap("Parçalar")
        tap("Derin")
        shown("En alt")
        back()
        shown("Düğüm")
        gone("En alt")
        assertTrue(open)
        back()
        shown("Aç")
        shown("İşlemler")
        gone("Düğüm")
        assertTrue(open)
        back()
        assertFalse("on the first page Back closes the sheet, as before", open)
    }

    private fun gesture(progress: Float) = BackEventCompat(40f, 400f, progress, BackEventCompat.EDGE_LEFT)

    @Test
    fun thePredictiveBackGestureGoesUpOnePageAndOneGivenUpStaysOnIt() {
        sheet()
        tap("Parçalar")
        tap("Derin")
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(gesture(0f))
            dispatcher.dispatchOnBackProgressed(gesture(0.4f))
            dispatcher.dispatchOnBackCancelled()
        }
        compose.waitForIdle()
        shown("En alt")
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(gesture(0f))
            dispatcher.dispatchOnBackProgressed(gesture(0.6f))
            dispatcher.onBackPressed()
        }
        compose.waitForIdle()
        shown("Düğüm")
        gone("En alt")
        assertTrue("one page up, the sheet still open", open)
        // the three-button Back of Android 16 starts a gesture without an edge and completes it at once
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_NONE))
            dispatcher.onBackPressed()
        }
        compose.waitForIdle()
        shown("Aç")
        assertTrue(open)
    }

    @Test
    fun theBackButtonAndEscGoUpOnePage() {
        sheet()
        tap("Parçalar")
        tap("Derin")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        shown("Düğüm")
        gone("En alt")
        key(KeyEvent.KEYCODE_ESCAPE)
        shown("Aç")
        assertTrue("Esc on a page goes up, it does not close the sheet", open)
    }

    @Test
    fun withAKeyboardTheFocusGoesWithThePages() {
        // a keyboard is in use: lines take the focus (the test window starts in touch mode, as a phone's does)
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        sheet()
        compose.runOnIdle { inputModeManager.requestInputMode(ComposeInputMode.Keyboard) }
        compose.onNodeWithText("Parçalar").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        compose.onNodeWithText("Parçalar").assertIsFocused()
        key(KeyEvent.KEYCODE_ENTER)
        shown("Düğüm")
        // the line that opened the page is gone with its page: the focus goes on from the page's back button
        compose.onNodeWithContentDescription("Back").assertIsFocused()
        key(KeyEvent.KEYCODE_ESCAPE)
        shown("Aç")
        compose.onNodeWithText("Parçalar").assertIsFocused()
    }

    @Test
    fun aPagesTitleStartsWhereItsLinesLabelsDoInEitherDensity() {
        sheet()
        tap("Parçalar")
        fun left(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left
        assertEquals("touch", left("Düğüm"), left("Parçalar"), 0.5f)
        // a narrow DeX window: the phone's sheet with the pointer's sizes
        density = InputMode.Pointer
        compose.waitForIdle()
        assertEquals("pointer", left("Düğüm"), left("Parçalar"), 0.5f)
    }

    @Test
    fun anActionOnAPageClosesTheSheetAndASwitchStaysOnItsPage() {
        sheet()
        tap("Parçalar")
        tap("Sözcük kaydır")
        assertTrue(wrap)
        assertTrue(open)
        // the entries were built anew with the switch on; the page is still up and shows it
        shown("Düğüm")
        compose.onNodeWithText("Sözcük kaydır").assertIsOn()
        tap("Düğüm")
        assertEquals(listOf("Düğüm"), chosen)
        assertFalse(open)
    }

    @Test
    fun theSheetIsAsHighAsItsPage() {
        sheet()
        val first = pane("İşlemler").fetchSemanticsNode().size.height
        tap("Parçalar")
        tap("Derin")
        val deep = pane("Derin").fetchSemanticsNode().size.height
        assertTrue("one line and a head are lower than the first page: $deep < $first", deep < first)
        back()
        back()
        assertEquals("and back on the first page the sheet is as high as it was", first, pane("İşlemler").fetchSemanticsNode().size.height)
    }

    @Test
    fun aPageGoneBackToIsWhereItWasScrolled() {
        val long = {
            listOf(ActionGroup(null, (1..30).map { item("Öğe $it") } + MenuEntry.Submenu("En sondaki", listOf(item("İçindeki")))))
        }
        sheet(long)
        compose.onNodeWithText("En sondaki").performScrollTo()
        compose.waitForIdle()
        val before = compose.onNodeWithText("En sondaki").fetchSemanticsNode().boundsInRoot.top
        tap("En sondaki")
        shown("İçindeki")
        back()
        assertEquals("the line that opened the page is where it was", before, compose.onNodeWithText("En sondaki").fetchSemanticsNode().boundsInRoot.top)
        // the page opened again starts at its top
        tap("En sondaki")
        shown("İçindeki")
    }

    @Test
    fun anEmptySubmenuIsShownDisabled() {
        sheet()
        compose.onNodeWithText("Boş").assertIsNotEnabled()
        assertEquals("Submenu", compose.onNodeWithText("Parçalar").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
    }

    @Test
    fun aSheetOpenedAgainStartsOnItsFirstPage() {
        sheet()
        tap("Parçalar")
        shown("Düğüm")
        open = false
        compose.waitForIdle()
        gone("Düğüm")
        open = true
        compose.waitForIdle()
        shown("Aç")
        shown("İşlemler")
        gone("Düğüm")
    }

    @Test
    fun theShellsMoreSheetTurnsPagesToo() {
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                TestShell(
                    WidthClass.Compact,
                    startRoute = "music",
                    spec = mediaSpec,
                    moreActions = listOf(ActionGroup("Oynatma", listOf(MenuEntry.Submenu("Uyku zamanlayıcısı", listOf(item("15 dakika"), item("30 dakika")))))),
                ) { route, _ -> Text("ekran-$route") }
            }
        }
        compose.waitForIdle()
        tap("More")
        shown("Albümler")
        tap("Uyku zamanlayıcısı")
        shown("30 dakika")
        gone("Albümler")
        back()
        shown("Albümler")
        tap("Uyku zamanlayıcısı")
        tap("15 dakika")
        assertEquals(listOf("15 dakika"), chosen)
        gone("15 dakika")
    }

    @Test
    fun outsideAShellBackInTheSheetsOwnWindowGoesUpOnePage() {
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                CompositionLocalProvider(LocalWidthClass provides WidthClass.Compact) {
                    MoreSheet(visible = open, onDismiss = { open = false }, groups = groups(), title = "İşlemler")
                }
            }
        }
        open = true
        compose.waitForIdle()
        tap("Parçalar")
        shown("Düğüm")
        // the sheet is a pop-up window there, and Back reaches that window
        key(KeyEvent.KEYCODE_BACK)
        shown("Aç")
        assertTrue(open)
    }

    @Test
    fun onAWiderWindowTheSameEntriesAreAMenuWithASubmenu() {
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                TestShell(WidthClass.Expanded, content = { _, _ ->
                    MoreSheet(visible = open, onDismiss = { open = false }, groups = groups(), title = "İşlemler")
                })
            }
        }
        open = true
        compose.waitForIdle()
        tap("Parçalar")
        // beside its line, the first menu still there
        shown("Düğüm")
        shown("Aç")
        tap("Düğüm")
        assertEquals(listOf("Düğüm"), chosen)
        assertFalse(open)
    }
}
