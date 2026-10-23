package desktopkit.screenshots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.R
import desktopkit.input.CommonShortcuts
import desktopkit.input.KeyChord
import desktopkit.input.Shortcut
import desktopkit.input.ShortcutGroup
import desktopkit.input.ShortcutRegistry
import desktopkit.input.ShortcutScope
import desktopkit.screens.PageHeader
import desktopkit.settings.AppearanceSection
import desktopkit.settings.DeskPreferences
import desktopkit.settings.InputSection
import desktopkit.settings.ShortcutListSection
import desktopkit.theme.Accent
import desktopkit.theme.DeskTheme
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The ready-made settings sections, with a custom brand accent next to the presets. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w900dp-h1100dp-mdpi")
class SettingsScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun settingsSections() = capture("settings-dark", ThemeMode.Dark, InputMode.Pointer)

    /** On a phone the long segmented choices move under their labels instead of squeezing them. */
    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun settingsCompact() = capture("settings-compact-light", ThemeMode.Light, InputMode.Touch)

    private fun capture(name: String, theme: ThemeMode, input: InputMode) {
        val brand = Accent.custom("Marka", Color(0xFF00897B))
        val prefs = DeskPreferences(
            compose.activity.getSharedPreferences("screenshot", Context.MODE_PRIVATE),
            accents = Accent.presets + brand,
            defaultAccent = brand,
        )
        val file = ShortcutGroup("file", R.string.dk_settings)
        val registry = ShortcutRegistry(
            groups = listOf(file, CommonShortcuts.ViewGroup, CommonShortcuts.HelpGroup),
            shortcuts = listOf(Shortcut("play", file, R.string.dk_more, KeyChord(Key.Spacebar), ShortcutScope("player"))) + CommonShortcuts.all,
        )
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = theme, accent = prefs.accent, inputMode = input) {
                LazyColumn(Modifier.fillMaxSize().background(DeskTheme.colors.background)) {
                    item { PageHeader("Ayarlar") }
                    item { AppearanceSection(prefs) }
                    item { InputSection(prefs) }
                    item { ShortcutListSection(registry, scopeLabel = { "Oynatıcıda" }) }
                }
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }
}
