package desktopkit.shell

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.DesktopKitRoot
import desktopkit.TestShell
import desktopkit.screenshots.ShellScreenshotTest
import desktopkit.settings.rememberDeskPreferences
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * On a phone the bottom navigation bar steps aside while the on-screen keyboard is open, and the
 * content (with the status strip under it) still ends above the keyboard.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class ShellImeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var view: View

    /** What the system sends when the keyboard opens ([bottom] px tall) or closes (0). */
    private fun keyboard(bottom: Int) {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, bottom))
            .setVisible(WindowInsetsCompat.Type.ime(), bottom > 0)
            .build()
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(view, insets) }
        compose.waitForIdle()
    }

    @Test
    fun bottomBarHidesWhileTheKeyboardIsOpen() {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            view = LocalView.current
            DesktopKitRoot(rememberDeskPreferences(defaultTheme = ThemeMode.Dark)) { widthClass ->
                TestShell(widthClass, content = { _, _ -> Box(Modifier.fillMaxSize().testTag("content")) })
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Müzik").assertExists() // a bottom bar entry
        val root = compose.onRoot().getBoundsInRoot()

        val keyboardPx = 600
        keyboard(keyboardPx)
        compose.onNodeWithText("Müzik").assertDoesNotExist()
        val keyboardTop: Dp = root.bottom - with(compose.density) { keyboardPx.toDp() }
        val content = compose.onNodeWithTag("content").getBoundsInRoot()
        val status = compose.onNodeWithText("Kopyalanıyor: Yedek.zip").getBoundsInRoot()
        assertTrue("content ends at ${content.bottom}, keyboard starts at $keyboardTop", content.bottom <= keyboardTop)
        assertTrue("status strip ends at ${status.bottom}, keyboard starts at $keyboardTop", status.bottom <= keyboardTop)
        // the inset is applied once: the status strip (1 dp rule + 48 dp) sits right on top of the keyboard
        val gap = keyboardTop - (content.bottom + 49.dp)
        assertTrue("gap between status strip and keyboard: $gap", abs(gap.value) < 1f)
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save("compact-dark-keyboard", bitmap)

        keyboard(0)
        compose.onNodeWithText("Müzik").assertExists()
    }

    @Test
    fun thePlayerStaysAboveTheKeyboardAfterTheBarsSlidAway() {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            view = LocalView.current
            DesktopKitRoot(rememberDeskPreferences(defaultTheme = ThemeMode.Dark)) { widthClass ->
                TestShell(widthClass, content = { _, _ ->
                    LazyColumn(Modifier.fillMaxSize().testTag("list"), contentPadding = rememberShellContentPadding()) {
                        items(80) { Text("Satır $it", Modifier.padding(16.dp)) }
                    }
                })
            }
        }
        compose.waitForIdle()
        val root = compose.onRoot().getBoundsInRoot()
        val shown = compose.onNodeWithText("Kopyalanıyor: Yedek.zip").getBoundsInRoot()
        // scrolling down slides the bars and the strip above them away
        compose.onNodeWithTag("list").performTouchInput { swipeUp(durationMillis = 600) }
        compose.waitForIdle()
        val slid = compose.onNodeWithText("Kopyalanıyor: Yedek.zip").getBoundsInRoot()
        assertTrue("the strip slid down: ${shown.top} → ${slid.top}", slid.top > shown.top + 20.dp)

        val keyboardPx = 600
        keyboard(keyboardPx)
        val keyboardTop: Dp = root.bottom - with(compose.density) { keyboardPx.toDp() }
        val status = compose.onNodeWithText("Kopyalanıyor: Yedek.zip").getBoundsInRoot()
        assertTrue("status strip ends at ${status.bottom}, keyboard starts at $keyboardTop", status.bottom <= keyboardTop + 1.dp)
        assertTrue("status strip sits on the keyboard: ${status.bottom} vs $keyboardTop", status.bottom >= keyboardTop - 1.dp)
    }
}
