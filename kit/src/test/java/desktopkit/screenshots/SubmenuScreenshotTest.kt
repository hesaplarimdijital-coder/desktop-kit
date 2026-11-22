package desktopkit.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.SampleScreen
import desktopkit.SampleTrackList
import desktopkit.TestShell
import desktopkit.components.ActionGroup
import desktopkit.components.DeskIcons
import desktopkit.components.MenuButton
import desktopkit.components.MenuEntry
import desktopkit.mediaSpec
import desktopkit.sampleNestedMenus
import desktopkit.sampleSnippetEntries
import desktopkit.shell.WidthClass
import desktopkit.theme.DeskTheme
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Submenus: a menu bar menu with a submenu and a second level open (mouse, DeX), the same from a
 * button on a tablet (touch), a menu on a phone, where the submenu has to go over its parent, and
 * the phone's "⋯" sheet on a submenu's page. Menus are windows of their own: each is drawn onto the
 * picture of the app window where it stands.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SubmenuScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val global by lazy { Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null) }

    @Suppress("UNCHECKED_CAST")
    private fun <T> globalList(name: String): List<T> =
        (global.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(global) as List<T>).toList()

    /** The app window with every menu window over it, in the order they opened. */
    private fun screen(name: String) {
        compose.waitForIdle()
        val decor = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle {
            val canvas = Canvas(bitmap)
            decor.draw(canvas)
            globalList<View>("mViews").zip(globalList<WindowManager.LayoutParams>("mParams")).forEach { (view, params) ->
                if (view !== decor) {
                    canvas.save()
                    canvas.translate(params.x.toFloat(), params.y.toFloat())
                    view.draw(canvas)
                    canvas.restore()
                }
            }
        }
        ShellScreenshotTest.save(name, bitmap)
    }

    private fun tap(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    private fun menuBar(name: String, theme: ThemeMode) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = theme, inputMode = InputMode.Pointer) {
                Box(Modifier.fillMaxSize().background(DeskTheme.colors.background)) {
                    TestShell(WidthClass.Large, startRoute = "documents", menus = sampleNestedMenus())
                }
            }
        }
        compose.waitForIdle()
        tap("Ink")
        tap("Temel yapı")
        tap("Yönlendirme")
        screen(name)
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun menuBarLight() = menuBar("submenu-dex-light", ThemeMode.Light)

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun menuBarDark() = menuBar("submenu-dex-dark", ThemeMode.Dark)

    private fun button(name: String, theme: ThemeMode, widthClass: WidthClass) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = theme, inputMode = InputMode.Touch) {
                Box(Modifier.fillMaxSize().background(DeskTheme.colors.background)) {
                    TestShell(widthClass, startRoute = "documents") { route, _ ->
                        Column(Modifier.fillMaxSize()) {
                            MenuButton("Parça ekle", entries = { sampleSnippetEntries() }, modifier = Modifier.padding(start = 16.dp, top = 8.dp), icon = DeskIcons.Plus)
                            SampleScreen(route)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        tap("Parça ekle")
        tap("Temel yapı")
        tap("Yönlendirme")
        screen(name)
    }

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun tabletTouchLight() = button("submenu-tablet-touch-light", ThemeMode.Light, WidthClass.Expanded)

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun tabletTouchDark() = button("submenu-tablet-touch-dark", ThemeMode.Dark, WidthClass.Expanded)

    /** On a phone there is no room beside a menu: the submenu goes over it, as far to the end as the window lets it. */
    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phoneMenu() = button("submenu-phone-menu-dark", ThemeMode.Dark, WidthClass.Compact)

    private fun sheet(name: String, theme: ThemeMode) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = theme, inputMode = InputMode.Touch) {
                TestShell(
                    WidthClass.Compact,
                    startRoute = "music",
                    spec = mediaSpec,
                    moreActions = listOf(ActionGroup("Ink", listOf(
                        MenuEntry.Submenu("Parça ekle", sampleSnippetEntries(), icon = DeskIcons.Plus),
                        MenuEntry.Item("Sözcük sayısı", {}, icon = DeskIcons.Info),
                    ))),
                ) { _, _ -> SampleTrackList() }
            }
        }
        compose.waitForIdle()
        tap("Daha fazla")
        screen("$name-first")
        tap("Parça ekle")
        tap("Temel yapı")
        screen(name)
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phoneSheetPageDark() = sheet("submenu-phone-sheet-dark", ThemeMode.Dark)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phoneSheetPageLight() = sheet("submenu-phone-sheet-light", ThemeMode.Light)
}
