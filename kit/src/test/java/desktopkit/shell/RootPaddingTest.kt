package desktopkit.shell

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.DesktopKitRoot
import desktopkit.TestShell
import desktopkit.settings.rememberDeskPreferences
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A shell that appears in a window that already has a navigation bar (the app's first launch) sits
 * where it sits after any later layout: the root leaves the bar to the shell from the start.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class RootPaddingTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var view: View
    private var shell by mutableStateOf(false)

    /** The three-button navigation bar, [bottom] px tall. */
    private fun navigationBar(bottom: Int) {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, bottom))
            .setVisible(WindowInsetsCompat.Type.navigationBars(), bottom > 0)
            .build()
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(view, insets) }
        compose.waitForIdle()
    }

    @Test
    fun theShellSitsOnTheNavigationBarAtFirstLaunch() {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            view = LocalView.current
            DesktopKitRoot(rememberDeskPreferences(defaultTheme = ThemeMode.Light)) { widthClass ->
                if (shell) TestShell(widthClass) else Box(Modifier.fillMaxSize())
            }
        }
        compose.waitForIdle()
        val barPx = 72 // 48 dp on hdpi
        navigationBar(barPx)

        // the shell registers while the content is being measured, as on the first launch
        shell = true
        compose.waitForIdle()
        val first = compose.onNodeWithText("Müzik").getBoundsInRoot()

        // the window is laid out again with the same bar
        navigationBar(0)
        navigationBar(barPx)
        val later = compose.onNodeWithText("Müzik").getBoundsInRoot()
        assertEquals("the bottom bar moved once the window was laid out again", later.bottom.value, first.bottom.value, 0.5f)
        assertEquals(later.top.value, first.top.value, 0.5f)
    }
}
