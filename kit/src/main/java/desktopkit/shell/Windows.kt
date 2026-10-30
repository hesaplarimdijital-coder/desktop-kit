package desktopkit.shell

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * More than one window of the app: on DeX and on Android's desktop windowing each is a window of its
 * own, on a tablet the new one opens next to this one (split screen); on a phone it is a second
 * full-screen task, so offer it from [WidthClass.Medium] up. Each window is an activity with its
 * own [DesktopShell], back stacks and scroll positions; the kit's appearance preferences
 * ([desktopkit.settings.DeskPreferences]: theme, accent, density) follow each other, so a theme
 * picked in one window changes the others.
 *
 * The app's activity needs, in the manifest:
 * ```
 * <activity android:name=".MainActivity" android:resizeableActivity="true" …>
 *     <!-- Android 15+: the system's own window menus offer "New window" -->
 *     <property android:name="android.window.PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI" android:value="true" />
 * </activity>
 * ```
 * and no `singleTask` or `singleInstance` launch mode.
 */
object DeskWindows {
    /** The intent extra that carries the route a window opens at. */
    const val EXTRA_ROUTE = "desktopkit.route"

    /**
     * Opens [intent] (by default this activity again) in a new window, at [route] when given (read it
     * back with [startRoute]); [adjacent] puts it next to this one where windows are not
     * free-floating. Anything else the window should show goes in the intent's extras.
     */
    fun openNewWindow(
        activity: Activity,
        route: String? = null,
        intent: Intent = Intent(activity, activity.javaClass),
        adjacent: Boolean = true,
    ) {
        if (route != null) intent.putExtra(EXTRA_ROUTE, route)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        if (adjacent) intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        activity.startActivity(intent)
    }

    /**
     * The route a window was opened at by [openNewWindow], for [ShellSpec]'s `startRoute`; null for
     * the window the launcher opened.
     */
    fun startRoute(intent: Intent?): String? = intent?.getStringExtra(EXTRA_ROUTE)
}

/**
 * Opens a new window of this activity, at a route or at the app's start; null outside an activity
 * (previews, tests without one).
 */
@Composable
fun rememberNewWindow(): ((route: String?) -> Unit)? {
    val activity = LocalActivity.current ?: return null
    return remember(activity) { { route -> DeskWindows.openNewWindow(activity, route) } }
}
