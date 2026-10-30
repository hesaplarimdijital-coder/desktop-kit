package desktopkit.shell

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.settings.DeskPreferences
import desktopkit.settings.rememberDeskPreferences
import desktopkit.theme.Accent
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** M2: more than one window of the app. */
@RunWith(AndroidJUnit4::class)
class WindowsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun aNewWindowIsANewTaskAtItsRoute() {
        val activity = compose.activity
        compose.runOnUiThread { DeskWindows.openNewWindow(activity, route = "albums") }
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(ComponentActivity::class.java.name, started.component?.className)
        assertEquals("albums", DeskWindows.startRoute(started))
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_MULTIPLE_TASK != 0)
        assertTrue("next to this one in split screen", started.flags and Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT != 0)
        assertNull("the launcher's window starts at the app's start", DeskWindows.startRoute(activity.intent))
    }

    @Test
    fun appearanceFollowsTheOtherWindow() {
        lateinit var first: DeskPreferences
        lateinit var second: DeskPreferences
        // two windows of the app: two preference holders over the same file
        compose.setContent {
            first = rememberDeskPreferences()
            second = rememberDeskPreferences()
        }
        compose.runOnIdle { first.updateThemeMode(ThemeMode.Dark) }
        compose.runOnIdle { assertEquals(ThemeMode.Dark, second.themeMode) }
        compose.runOnIdle { second.updateAccent(Accent.presets[2]) }
        compose.runOnIdle { assertEquals(Accent.presets[2], first.accent) }
        // the sidebar stays as it is in each window
        compose.runOnIdle { first.updateSidebarCollapsed(true) }
        compose.runOnIdle { assertEquals(false, second.sidebarCollapsed) }
    }
}
