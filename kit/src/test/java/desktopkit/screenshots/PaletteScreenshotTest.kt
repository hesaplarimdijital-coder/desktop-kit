package desktopkit.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.TestShell
import desktopkit.components.CommandPalette
import desktopkit.components.CommandPaletteState
import desktopkit.components.DeskIcons
import desktopkit.components.PaletteItem
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

/** The command palette (K4) over the shell: a glass panel on DeX, the whole window on a phone. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PaletteScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val items = listOf(
        PaletteItem("album", "Gece Yarısı Treni", "Albümler", {}, subtitle = "Albüm · Deniz Aras · 10 parça", actionLabel = "oynat",
            onOpenInNewWindow = {}, leading = { Box(Modifier.size(32.dp).background(Color(0xFF2E3D5C), RoundedCornerShape(4.dp))) }),
        PaletteItem("film", "Gece Bekçisi", "Filmler", {}, subtitle = "2021 · 4K Dolby Vision", icon = DeskIcons.Movies, hint = "Kaldığın yer 1:12:05"),
        PaletteItem("line", "“Bu gece yarısından önce limanda olmalıyız.”", "Söylenenlerde", {}, subtitle = "Liman Işıkları · 0:47:31",
            icon = DeskIcons.Subtitle, hint = "bu sahneden oynat"),
        PaletteItem("night", "Gece modunu aç", "Komutlar", {}, icon = DeskIcons.Command, shortcut = "Ctrl+Shift+N"),
        PaletteItem("eq", "Ekolayzır", "Komutlar", {}, icon = DeskIcons.Equalizer, keywords = listOf("gece dinleme")),
    )

    private fun capture(name: String, widthClass: WidthClass, input: InputMode) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = input) {
                Box(Modifier.fillMaxSize().background(DeskTheme.colors.background)) {
                    val palette = remember { CommandPaletteState().apply { open("gece") } }
                    TestShell(widthClass, startRoute = "documents", overlays = {
                        CommandPalette(palette, items = { items }, filters = listOf("Komutlar", "Albümler", "Filmler", "Söylenenlerde"))
                    })
                }
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun paletteOnDex() = capture("palette-dark-dex", WidthClass.Large, InputMode.Pointer)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun paletteOnPhone() = capture("palette-dark-phone", WidthClass.Compact, InputMode.Touch)
}
