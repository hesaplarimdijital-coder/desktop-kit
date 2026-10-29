package desktopkit.shell

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.DesktopKitRoot
import desktopkit.TestShell
import desktopkit.screenshots.ShellScreenshotTest
import desktopkit.settings.rememberDeskPreferences
import desktopkit.theme.Accent
import desktopkit.theme.DeskPalette
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Three-button navigation (M7): the shell's chrome continues behind the Back, Home and Recents
 * buttons instead of leaving a strip of window background with the system's scrim on it, and the
 * buttons take the theme's colour.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SystemBarsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var view: View

    private fun px(dp: Dp): Int = with(compose.density) { dp.roundToPx() }

    /** What the system sends: the status bar, the three-button navigation bar and, if open, the keyboard. */
    private fun systemBars(status: Dp, navigation: Dp, keyboard: Int = 0) {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, px(status), 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, px(navigation)))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, keyboard))
            .setVisible(WindowInsetsCompat.Type.ime(), keyboard > 0)
            .build()
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(view, insets) }
        compose.waitForIdle()
    }

    private fun shell(theme: ThemeMode = ThemeMode.Dark) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            view = LocalView.current
            DesktopKitRoot(rememberDeskPreferences(defaultTheme = theme)) { widthClass ->
                TestShell(widthClass, content = { _, _ -> Box(Modifier.fillMaxSize().testTag("content")) })
            }
        }
        compose.waitForIdle()
    }

    private fun window(name: String): Bitmap {
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
        return bitmap
    }

    @Test
    @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phoneBarsReachBehindTheButtons() {
        shell()
        systemBars(status = 24.dp, navigation = 48.dp)
        val root = compose.onRoot().getBoundsInRoot()
        // the bottom bar's tabs stay above the buttons
        val tab = compose.onNodeWithText("Müzik").getBoundsInRoot()
        assertTrue("tab ends at ${tab.bottom}, buttons start at ${root.bottom - 48.dp}", tab.bottom <= root.bottom - 48.dp)
        // a screen that does not pad itself ends where the status strip (49 dp) and the bottom bar (64 dp) start
        val content = compose.onNodeWithTag("content").getBoundsInRoot()
        val gap = root.bottom - content.bottom - (48.dp + 64.dp + 49.dp)
        assertTrue("content ends ${gap} away from the bars", abs(gap.value) < 1f)
        // behind the buttons: the bar's chrome, not the window background
        val bitmap = window("compact-dark-three-button-navigation")
        val behindButtons = bitmap.getPixel(bitmap.width / 2, bitmap.height - px(12.dp))
        assertNotEquals(DeskPalette.dark(Accent.Blue).background.toArgb(), behindButtons)
    }

    @Test
    @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun keyboardCoversTheButtons() {
        shell()
        systemBars(status = 24.dp, navigation = 48.dp)
        val root = compose.onRoot().getBoundsInRoot()
        val keyboardPx = 600
        systemBars(status = 24.dp, navigation = 48.dp, keyboard = keyboardPx)
        compose.onNodeWithText("Müzik").assertDoesNotExist()
        val keyboardTop: Dp = root.bottom - with(compose.density) { keyboardPx.toDp() }
        val content = compose.onNodeWithTag("content").getBoundsInRoot()
        // the status strip (1 dp rule + 48 dp) sits right on the keyboard: no band for the hidden buttons
        val gap = keyboardTop - (content.bottom + 49.dp)
        assertTrue("gap between status strip and keyboard: $gap", abs(gap.value) < 1f)
    }

    @Test
    @Config(qualifiers = "tr-w700dp-h1000dp-mdpi")
    fun tabletChromeFillsTheNavigationBar() {
        shell()
        systemBars(status = 24.dp, navigation = 48.dp)
        val root = compose.onRoot().getBoundsInRoot()
        // the status strip ends above the navigation bar
        val strip = compose.onNodeWithText("Kopyalanıyor: Yedek.zip").getBoundsInRoot()
        assertTrue("strip ends at ${strip.bottom}", strip.bottom <= root.bottom - 48.dp)
        val bitmap = window("medium-dark-three-button-navigation")
        val behindButtons = bitmap.getPixel(bitmap.width / 2, bitmap.height - px(12.dp))
        assertEquals(DeskPalette.dark(Accent.Blue).chrome.toArgb(), behindButtons)
    }

    @Test
    @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun buttonsFollowTheTheme() {
        shell(ThemeMode.Light)
        val window = compose.activity.window
        compose.runOnIdle {
            assertFalse("no system scrim over the kit's chrome", window.isNavigationBarContrastEnforced)
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            assertTrue("dark buttons on the light theme", controller.isAppearanceLightNavigationBars)
            assertTrue("dark status icons on the light theme", controller.isAppearanceLightStatusBars)
        }
    }

    @Test
    @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun lightButtonsOnTheDarkTheme() {
        shell(ThemeMode.Dark)
        val window = compose.activity.window
        compose.runOnIdle {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            assertFalse(controller.isAppearanceLightNavigationBars)
            assertFalse(controller.isAppearanceLightStatusBars)
        }
    }
}
