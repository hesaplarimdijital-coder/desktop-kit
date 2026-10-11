package desktopkit.input

import android.content.Context
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.R
import desktopkit.components.MenuEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShortcutRegistryTest {

    private val fileGroup = ShortcutGroup("file", R.string.dk_settings)
    private val player = ShortcutScope("player")

    private val registry = ShortcutRegistry(
        groups = listOf(fileGroup, CommonShortcuts.ViewGroup, CommonShortcuts.HelpGroup),
        shortcuts = listOf(
            Shortcut("open", fileGroup, R.string.dk_more, KeyChord(Key.O, ctrl = true)),
            Shortcut("play", fileGroup, R.string.dk_more, KeyChord(Key.Spacebar), player),
        ) + CommonShortcuts.all,
    )

    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun commonShortcutsDoNotConflict() {
        val conflicts = registry.conflicts()
        assertTrue(conflicts.joinToString { (a, b) -> "${a.id}/${b.id}" }, conflicts.isEmpty())
    }

    @Test
    fun conflictsRespectScopes() {
        val editorKey = Shortcut("delete-frame", fileGroup, R.string.dk_more, KeyChord(Key.Spacebar), ShortcutScope("editor"))
        assertTrue(ShortcutRegistry(registry.groups, registry.shortcuts + editorKey).conflicts().isEmpty())
        val globalKey = Shortcut("global-space", fileGroup, R.string.dk_more, KeyChord(Key.Spacebar))
        assertEquals(1, ShortcutRegistry(registry.groups, registry.shortcuts + globalKey).conflicts().size)
    }

    @Test
    fun labels() {
        assertEquals("Ctrl+K", KeyChord(Key.K, ctrl = true).label())
        assertEquals("Ctrl+Shift+N", KeyChord(Key.N, ctrl = true, shift = true).label())
        assertEquals("Alt+→", KeyChord(Key.DirectionRight, alt = true).label())
        assertEquals("Space", KeyChord(Key.Spacebar).label())
        assertEquals("Ctrl+,", KeyChord(Key.Comma, ctrl = true).label())
        assertEquals("F5", KeyChord(Key.F5).label())
    }

    @Test
    fun eventsMatchShortcuts() {
        val ctrlK = AndroidKeyEvent(0, 0, AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_K, 0, AndroidKeyEvent.META_CTRL_ON or AndroidKeyEvent.META_CTRL_LEFT_ON)
        assertEquals(CommonShortcuts.SEARCH, registry.matchNative(ctrlK)?.id)
        assertEquals(CommonShortcuts.SEARCH, registry.match(KeyEvent(ctrlK))?.id)
        // key up is ignored
        val ctrlKUp = AndroidKeyEvent(0, 0, AndroidKeyEvent.ACTION_UP, AndroidKeyEvent.KEYCODE_K, 0, AndroidKeyEvent.META_CTRL_ON)
        assertNull(registry.matchNative(ctrlKUp))
        // Space belongs to the player scope
        val space = AndroidKeyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_SPACE)
        assertNull(registry.matchNative(space))
        // a plain key from the activity only when asked for, so text fields keep their keys
        assertNull(registry.matchNative(space, setOf(ShortcutScope.Global, player)))
        assertEquals("play", registry.matchNative(space, setOf(ShortcutScope.Global, player), allowPlainKeys = true)?.id)
        assertEquals("play", registry.match(KeyEvent(space), setOf(ShortcutScope.Global, player))?.id)
    }

    @Test
    fun menusFollowTheTable() {
        val menus = registry.menus(resources, perform = {})
        assertEquals(3, menus.size)
        val view = menus[1].entries().filterIsInstance<MenuEntry.Item>()
        assertEquals(listOf("Ctrl+K", "Ctrl+,", "Ctrl+B"), view.map { it.shortcut })
    }

    @Test
    fun systemShortcutListCoversEveryShortcut() {
        val groups = registry.keyboardShortcutGroups(resources)
        assertEquals(3, groups.size)
        assertEquals(registry.shortcuts.size, groups.sumOf { it.items.size })
    }
}
