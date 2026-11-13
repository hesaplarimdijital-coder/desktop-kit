package desktopkit.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Takes the phone's bottom navigation bar away while this is in composition, for a full-screen task
 * with its own buttons at the bottom: a form with "Cancel / Save", a set-up step, a review before an
 * import. Switching tabs half-way through such a task would leave it, and a bar sliding in and out
 * under the screen's own buttons (as it does while the content scrolls) makes the screen look as if
 * it jumped. As Android's full-screen forms do, the screen gets the height down to the system
 * navigation bar and keeps it: nothing slides on scroll any more; the [DesktopShell]'s `bottomBar`
 * strip (a player) stays, still and above the system bar. The padding the shell reports
 * ([rememberShellContentPadding]) leaves the navigation bar out.
 *
 * No effect on the rail and sidebar layouts (tablet, DeX). Call it in the screen, as [CompactTopBar];
 * [enabled] false (or leaving composition) brings the bar back. A screen being left gives way at once.
 * Outside a [DesktopShell] nothing happens.
 */
@Composable
fun HideBottomNavigation(enabled: Boolean = true) {
    val host = LocalBottomNavigationHider.current ?: return
    val leaving = LocalScreenLeaving.current
    val entry = remember(host) { BottomNavigationHider.Entry(enabled, leaving) }
    SideEffect {
        entry.enabled = enabled
        entry.leaving = leaving
    }
    DisposableEffect(host, entry) {
        host.entries.add(entry)
        onDispose { host.entries.remove(entry) }
    }
}

/** The [HideBottomNavigation] calls in composition; any of a screen not being left hides the bar. */
@Stable
internal class BottomNavigationHider {
    class Entry(enabled: Boolean, leaving: () -> Boolean) {
        var enabled by mutableStateOf(enabled)
        var leaving by mutableStateOf(leaving)
    }

    val entries = mutableStateListOf<Entry>()

    val hidden: Boolean get() = entries.any { it.enabled && !it.leaving() }
}

internal val LocalBottomNavigationHider = staticCompositionLocalOf<BottomNavigationHider?> { null }
