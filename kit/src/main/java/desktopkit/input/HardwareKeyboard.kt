package desktopkit.input

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import desktopkit.theme.DeskTheme
import desktopkit.theme.InputMode

/**
 * Whether a hardware keyboard is at hand: a keyboard cover, a Bluetooth keyboard or DeX's, not
 * folded away. Follows the system as keyboards come and go. To decide whether to show keyboard
 * shortcuts, use [showShortcutHints], which also counts the mouse.
 */
@Composable
@ReadOnlyComposable
fun hasHardwareKeyboard(): Boolean = LocalConfiguration.current.hasHardwareKeyboard()

internal fun Configuration.hasHardwareKeyboard(): Boolean =
    keyboard == Configuration.KEYBOARD_QWERTY && hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES

/**
 * Whether to show keyboard shortcuts: with a mouse (the pointer layout) or a [hardware
 * keyboard][hasHardwareKeyboard], not for a finger alone, where "Ctrl+K" is only noise. The kit's
 * menus, the "⋯" sheet, the command palette and the toasts follow it; use it for the app's own hints
 * too, a settings section listing the shortcuts say:
 * ```
 * if (showShortcutHints()) item { ShortcutListSection(shortcuts) }
 * ```
 */
@Composable
@ReadOnlyComposable
fun showShortcutHints(): Boolean = DeskTheme.inputMode == InputMode.Pointer || hasHardwareKeyboard()
