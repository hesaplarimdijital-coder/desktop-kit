package desktopkit.input

import android.content.res.Resources
import android.view.KeyEvent as AndroidKeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.type
import desktopkit.R
import desktopkit.components.MenuEntry
import desktopkit.components.MenuSpec

/**
 * A key with its modifiers, e.g. Ctrl+K.
 *
 * A punctuation key (/ , . - = + [ ]) also matches by the character it types, whichever key types
 * it: on a Turkish Q keyboard "/" is Shift+7 and no key sends [Key.Slash], so Ctrl+/ never came
 * (DeskMail's request 10). Such a chord matches when the character typed with the keys held, Ctrl,
 * Alt and Meta left aside, is its character and Ctrl, Alt and Meta are as asked; Shift may be held
 * for the character. [char] sets the character for any other key (null: the key's own, if it is
 * one of these).
 */
@Immutable
data class KeyChord(
    val key: Key,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val meta: Boolean = false,
    val char: Char? = null,
) {
    val hasModifier: Boolean get() = ctrl || alt || meta

    /** F1–F12: safe to handle globally because they never type text. */
    val isFunctionKey: Boolean get() = key.nativeKeyCode in AndroidKeyEvent.KEYCODE_F1..AndroidKeyEvent.KEYCODE_F12

    /** The character this chord also matches by: [char], or the key's own punctuation. */
    val typedChar: Char? get() = char ?: punctuation(key)

    fun matches(event: KeyEvent): Boolean {
        if (event.key == key && event.isCtrlPressed == ctrl && event.isShiftPressed == shift && event.isAltPressed == alt &&
            event.isMetaPressed == meta) return true
        val wanted = typedChar ?: return false
        // the right Alt (AltGr) types characters, so only the left one counts as the chord's Alt here
        val leftAlt = event.nativeKeyEvent.metaState and AndroidKeyEvent.META_ALT_LEFT_ON != 0
        return event.isCtrlPressed == ctrl && leftAlt == alt && event.isMetaPressed == meta && typed(event) == wanted
    }

    /** "Ctrl+Shift+N", "Space", "Alt+→": the same text in menus, tooltips and the shortcut list. */
    fun label(resources: Resources? = null): String = buildString {
        if (ctrl) append("Ctrl+")
        if (alt) append("Alt+")
        if (shift) append("Shift+")
        if (meta) append("Meta+")
        append(keyName(key, resources))
    }

    /** Android meta state for [KeyboardShortcutInfo]. */
    internal val androidModifiers: Int
        get() = (if (ctrl) AndroidKeyEvent.META_CTRL_ON else 0) or
            (if (shift) AndroidKeyEvent.META_SHIFT_ON else 0) or
            (if (alt) AndroidKeyEvent.META_ALT_ON else 0) or
            (if (meta) AndroidKeyEvent.META_META_ON else 0)
}

/** The punctuation [key] stands for, when it is one that layouts put on other keys. */
private fun punctuation(key: Key): Char? = when (key) {
    Key.Slash -> '/'
    Key.Comma -> ','
    Key.Period -> '.'
    Key.Minus -> '-'
    Key.Equals -> '='
    Key.Plus -> '+'
    Key.LeftBracket -> '['
    Key.RightBracket -> ']'
    else -> null
}

/** Meta states that only make chords and type nothing themselves (AltGr, the right Alt, types). */
private const val CHORD_ONLY_META = AndroidKeyEvent.META_CTRL_MASK or AndroidKeyEvent.META_META_MASK or AndroidKeyEvent.META_ALT_LEFT_ON

/** The character [event] types with Shift, Caps Lock and AltGr as held, Ctrl, Meta and the left Alt left aside. */
private fun typed(event: KeyEvent): Char? {
    val native = event.nativeKeyEvent
    var meta = native.metaState and CHORD_ONLY_META.inv()
    // with only the left Alt gone, the plain Alt bit would stay and pick the layout's Alt row
    if (native.metaState and AndroidKeyEvent.META_ALT_RIGHT_ON == 0) meta = meta and AndroidKeyEvent.META_ALT_ON.inv()
    val code = runCatching { native.getUnicodeChar(meta) }.getOrDefault(0)
    return if (code == 0 || code and android.view.KeyCharacterMap.COMBINING_ACCENT != 0) null else code.toChar()
}

private fun keyName(key: Key, resources: Resources?): String = when (key) {
    Key.Spacebar -> resources?.getString(R.string.dk_key_space) ?: "Space"
    Key.DirectionLeft -> "←"
    Key.DirectionRight -> "→"
    Key.DirectionUp -> "↑"
    Key.DirectionDown -> "↓"
    Key.Enter -> "Enter"
    Key.Escape -> "Esc"
    Key.Delete -> "Del"
    Key.Backspace -> "Backspace"
    Key.Tab -> "Tab"
    Key.PageUp -> "PgUp"
    Key.PageDown -> "PgDn"
    Key.MoveHome -> "Home"
    Key.MoveEnd -> "End"
    Key.Comma -> ","
    Key.Period -> "."
    Key.Slash -> "/"
    Key.LeftBracket -> "["
    Key.RightBracket -> "]"
    Key.Equals -> "="
    Key.Minus -> "-"
    Key.Plus -> "+"
    else -> {
        val code = key.nativeKeyCode
        when (code) {
            in AndroidKeyEvent.KEYCODE_A..AndroidKeyEvent.KEYCODE_Z -> ('A' + (code - AndroidKeyEvent.KEYCODE_A)).toString()
            in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9 -> ('0' + (code - AndroidKeyEvent.KEYCODE_0)).toString()
            in AndroidKeyEvent.KEYCODE_F1..AndroidKeyEvent.KEYCODE_F12 -> "F" + (code - AndroidKeyEvent.KEYCODE_F1 + 1)
            else -> AndroidKeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
        }
    }
}

/** A section of the shortcut list and, usually, one menu of the menu bar ("File", "View", …). */
@Immutable
data class ShortcutGroup(val id: String, @StringRes val title: Int)

/**
 * Where a shortcut works. [Global] shortcuts work everywhere; define your own scopes for
 * screens that own single-key shortcuts (a player, an editor) and pass them to [ShortcutRegistry.match].
 */
@JvmInline
value class ShortcutScope(val name: String) {
    companion object {
        val Global = ShortcutScope("global")
    }
}

/** One keyboard shortcut. [id] is what your action handler switches on. */
@Immutable
data class Shortcut(
    val id: String,
    val group: ShortcutGroup,
    @StringRes val label: Int,
    val chord: KeyChord,
    val scope: ShortcutScope = ShortcutScope.Global,
)

/**
 * Single source of an app's keyboard shortcuts: the menu bar, tooltips, the Meta+/ system
 * list and the shortcut settings section all read the same table, so they never disagree.
 *
 * ```
 * val shortcuts = ShortcutRegistry(
 *     groups = listOf(FileGroup, CommonShortcuts.ViewGroup, CommonShortcuts.HelpGroup),
 *     shortcuts = listOf(Shortcut("open", FileGroup, R.string.open, KeyChord(Key.O, ctrl = true))) + CommonShortcuts.all,
 * )
 * ```
 */
class ShortcutRegistry(val groups: List<ShortcutGroup>, val shortcuts: List<Shortcut>) {

    private val byId = shortcuts.associateBy { it.id }

    operator fun get(id: String): Shortcut = byId.getValue(id)

    fun getOrNull(id: String): Shortcut? = byId[id]

    fun chordLabel(id: String, resources: Resources? = null): String? = byId[id]?.chord?.label(resources)

    fun inGroup(group: ShortcutGroup): List<Shortcut> = shortcuts.filter { it.group == group }

    /** Shortcut for a key-down [event] among those active in [scopes]; null when the key is not bound. */
    fun match(event: KeyEvent, scopes: Set<ShortcutScope> = setOf(ShortcutScope.Global)): Shortcut? {
        if (event.type != KeyEventType.KeyDown) return null
        return shortcuts.firstOrNull { it.scope in scopes && it.chord.matches(event) }
    }

    /**
     * Same as [match] for an Android key event, for routing keys from `Activity.dispatchKeyEvent`
     * (this works whatever has focus, also before any composable is focused):
     * ```
     * private val palette = CommandPaletteState()   // the activity holds it, so it can look here
     *
     * override fun dispatchKeyEvent(event: KeyEvent) =
     *     (!palette.isOpen && shortcuts.matchNative(event)?.let { actions.perform(it.id) } == true) ||
     *         super.dispatchKeyEvent(event)
     * ```
     * The activity sees the keys before Compose, so skip the routing while a layer that takes keys
     * itself is open: the [desktopkit.components.CommandPalette] (its field, Up and Down, Enter,
     * Ctrl+Enter, Tab, Esc), or the shortcuts act behind it. Give the palette the same state
     * (`CommandPalette(palette, …)`) instead of one from `rememberCommandPaletteState()`.
     *
     * Plain keys (Space, M, ←) are skipped unless [allowPlainKeys], so typing in a text field
     * never triggers a shortcut; handle them in the screen that owns them (e.g. the player). Function
     * keys (F1–F12) always count as chords.
     */
    fun matchNative(
        event: AndroidKeyEvent,
        scopes: Set<ShortcutScope> = setOf(ShortcutScope.Global),
        allowPlainKeys: Boolean = false,
    ): Shortcut? {
        val shortcut = match(KeyEvent(event), scopes) ?: return null
        return if (allowPlainKeys || shortcut.chord.hasModifier || shortcut.chord.isFunctionKey) shortcut else null
    }

    /**
     * Menu bar menus built from the table, one per group, so labels and keys match the
     * shortcut list. [perform] runs the shortcut's action; [extra] appends items without a shortcut.
     */
    fun menus(
        resources: Resources,
        perform: (String) -> Unit,
        enabled: (String) -> Boolean = { true },
        extra: (ShortcutGroup) -> List<MenuEntry> = { emptyList() },
    ): List<MenuSpec> = groups.map { group ->
        MenuSpec(resources.getString(group.title)) {
            inGroup(group).map { shortcut ->
                MenuEntry.Item(
                    label = resources.getString(shortcut.label),
                    onClick = { perform(shortcut.id) },
                    shortcut = shortcut.chord.label(resources),
                    enabled = enabled(shortcut.id),
                )
            } + extra(group)
        }
    }

    /** Pairs of shortcuts bound to the same chord in overlapping scopes; keep it empty in a unit test. */
    fun conflicts(): List<Pair<Shortcut, Shortcut>> =
        shortcuts.groupBy { it.chord }.values.filter { it.size > 1 }.flatMap { group ->
            group.indices.flatMap { i -> (i + 1 until group.size).map { j -> group[i] to group[j] } }
        }.filter { (a, b) -> a.scope == b.scope || a.scope == ShortcutScope.Global || b.scope == ShortcutScope.Global }

    /**
     * The keyboard shortcut helper list (Meta+/ or the system's keyboard shortcuts screen).
     * Return it from `Activity.onProvideKeyboardShortcuts`.
     */
    fun keyboardShortcutGroups(resources: Resources): List<KeyboardShortcutGroup> =
        groups.mapNotNull { group ->
            val items = inGroup(group).map { shortcut ->
                KeyboardShortcutInfo(resources.getString(shortcut.label), shortcut.chord.key.nativeKeyCode, shortcut.chord.androidModifiers)
            }
            if (items.isEmpty()) null else KeyboardShortcutGroup(resources.getString(group.title), items)
        }
}

/** Shortcuts every desktop-class app has, with the kit's own translated labels. */
object CommonShortcuts {
    val ViewGroup = ShortcutGroup("view", R.string.dk_menu_view)
    val HelpGroup = ShortcutGroup("help", R.string.dk_menu_help)

    const val SEARCH = "dk.search"
    const val SETTINGS = "dk.settings"
    const val TOGGLE_SIDEBAR = "dk.toggle_sidebar"
    const val SHORTCUT_LIST = "dk.shortcut_list"
    const val NEW_WINDOW = "dk.new_window"

    val Search = Shortcut(SEARCH, ViewGroup, R.string.dk_search, KeyChord(Key.K, ctrl = true))
    val Settings = Shortcut(SETTINGS, ViewGroup, R.string.dk_settings, KeyChord(Key.Comma, ctrl = true))
    val ToggleSidebar = Shortcut(TOGGLE_SIDEBAR, ViewGroup, R.string.dk_toggle_sidebar, KeyChord(Key.B, ctrl = true))
    val ShortcutList = Shortcut(SHORTCUT_LIST, HelpGroup, R.string.dk_shortcut_list, KeyChord(Key.Slash, ctrl = true))

    /**
     * Ctrl+N opens another window of the app ([desktopkit.shell.DeskWindows.openNewWindow]). Not part
     * of [all]: many apps use Ctrl+N for a new item; add it to the registry when the app has windows.
     */
    val NewWindow = Shortcut(NEW_WINDOW, ViewGroup, R.string.dk_new_window, KeyChord(Key.N, ctrl = true))

    val all: List<Shortcut> = listOf(Search, Settings, ToggleSidebar, ShortcutList)
}
