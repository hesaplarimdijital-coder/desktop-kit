package desktopkit.components

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.R
import desktopkit.SampleFile
import desktopkit.TestShell
import desktopkit.input.CommonShortcuts
import desktopkit.input.KeyChord
import desktopkit.input.Shortcut
import desktopkit.input.ShortcutGroup
import desktopkit.input.ShortcutRegistry
import desktopkit.sampleFiles
import desktopkit.sampleNestedMenus
import desktopkit.sampleSpec
import desktopkit.shell.DesktopShell
import desktopkit.shell.ShellTransitions
import desktopkit.shell.WidthClass
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A submenu wherever an app hands the kit a list of menu entries: the menu bar (and its menus
 * behind "≡" on the rail), the shortcut table's extra entries, a navigation entry's menu
 * (`navMenu`), a table row's context menu. Each shows the submenu and runs an item in it. (The "⋯"
 * sheet and the shell's actions: [SheetSubmenuTest]. The table's column menu is the kit's own, and
 * the phone top bar's "⋮" lists `TopBarAction`s, not entries: neither takes an app's submenu.)
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class SubmenuCallSitesTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val chosen = mutableListOf<String>()

    private fun shell(widthClass: WidthClass, input: InputMode) {
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Light, inputMode = input) {
                TestShell(widthClass, menus = sampleNestedMenus { chosen += it })
            }
        }
        compose.waitForIdle()
    }

    private fun tap(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    private fun shown(text: String) = compose.onNodeWithText(text).assertExists()

    private fun gone(text: String) = compose.onNodeWithText(text).assertDoesNotExist()

    @Test
    fun aMenuBarMenuOpensItsSubmenuAndRunsAnItemInIt() {
        shell(WidthClass.Large, InputMode.Pointer)
        tap("Dosya")
        tap("Son açılanlar")
        shown("Liman.ink")
        tap("Kervan.ink")
        assertEquals(listOf("Kervan.ink"), chosen)
        gone("Son açılanlar")
    }

    @Test
    fun movingToAnotherTitleOfTheMenuBarClosesTheSubmenusWithTheirMenu() {
        shell(WidthClass.Large, InputMode.Pointer)
        tap("Dosya")
        tap("Son açılanlar")
        shown("Kervan.ink")
        compose.onNodeWithText("Ink").performMouseInput { moveTo(center) }
        compose.waitForIdle()
        shown("Temel yapı")
        gone("Kervan.ink")
        gone("Son açılanlar")
    }

    @Test
    fun theMenusBehindTheRailsButtonHaveTheirSubmenus() {
        shell(WidthClass.Expanded, InputMode.Touch)
        compose.onNodeWithContentDescription("Menüler").performClick()
        compose.waitForIdle()
        tap("Ink")
        tap("Temel yapı")
        tap("Yönlendirme")
        tap("Tünel")
        assertEquals(listOf("Tünel"), chosen)
        gone("Temel yapı")
    }

    @Test
    fun theShortcutTablesExtraEntriesMayBeSubmenus() {
        val file = ShortcutGroup("file", R.string.dk_settings)
        val registry = ShortcutRegistry(
            groups = listOf(file, CommonShortcuts.ViewGroup),
            shortcuts = listOf(Shortcut("open", file, R.string.dk_more, KeyChord(Key.O, ctrl = true))) + CommonShortcuts.all,
        )
        val recent = MenuEntry.Submenu("Son açılanlar", listOf(MenuEntry.Item("Kervan.ink", {})))
        val menus = registry.menus(
            ApplicationProvider.getApplicationContext<Context>().resources,
            perform = {},
            extra = { group -> if (group == file) listOf(MenuEntry.Separator, recent) else emptyList() },
        )
        assertEquals("after the group's shortcuts", recent, menus[0].entries().last())
        assertTrue(menus[1].entries().none { it is MenuEntry.Submenu })
    }

    @Test
    fun aNavigationEntrysMenuOpensItsSubmenu() {
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Light, inputMode = InputMode.Touch) {
                DesktopShell(
                    spec = sampleSpec,
                    widthClass = WidthClass.Large,
                    transitions = ShellTransitions.None,
                    navMenu = { route ->
                        if (route != "documents") emptyList() else listOf(
                            MenuEntry.Item("Yeni pencerede aç", { chosen += "pencere" }),
                            MenuEntry.Submenu("Sırala", listOf("Ada göre", "Tarihe göre").map { MenuEntry.Item(it, { chosen += it }) }),
                        )
                    },
                ) { route, _ -> Text("ekran $route") }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Belgeler").performTouchInput { longClick(center) }
        compose.waitForIdle()
        tap("Sırala")
        tap("Tarihe göre")
        assertEquals(listOf("Tarihe göre"), chosen)
        gone("Sırala")
    }

    @Test
    fun aRowsContextMenuOpensItsSubmenu() {
        val names = sampleFiles.map { it.name }
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Light, inputMode = InputMode.Touch) {
                Box(Modifier.fillMaxSize()) {
                    DenseTable(
                        columns = listOf(TableColumn<SampleFile>("Ad", ColumnWidth.Weight(1f)) { it.name }),
                        rows = sampleFiles,
                        key = { it.name },
                        onRowClick = { _, _ -> },
                        rowMenu = { file, _ ->
                            listOf(
                                MenuEntry.Item("Aç", { chosen += "aç ${file.name}" }),
                                MenuEntry.Submenu("Taşı", listOf("Belgeler", "Arşiv").map { MenuEntry.Item(it, { chosen += "${file.name} → $it" }) }),
                            )
                        },
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText(names[2]).performTouchInput { longClick(center) }
        compose.waitForIdle()
        tap("Taşı")
        tap("Arşiv")
        assertEquals(listOf("${names[2]} → Arşiv"), chosen)
        gone("Taşı")
    }
}
