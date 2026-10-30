package desktopkit.input

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import desktopkit.theme.DeskTheme
import desktopkit.theme.InputMode

/**
 * Whether a hardware keyboard is at hand: a keyboard cover, a Bluetooth keyboard or DeX's, not
 * folded away. Follows the system as keyboards come and go. Show keyboard shortcuts (a settings
 * card listing them, say) only when this or a mouse is in use.
 */
@Composable
@ReadOnlyComposable
fun hasHardwareKeyboard(): Boolean = LocalConfiguration.current.hasHardwareKeyboard()

internal fun Configuration.hasHardwareKeyboard(): Boolean =
    keyboard == Configuration.KEYBOARD_QWERTY && hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES

/** Keyboard shortcuts next to menu items: with a mouse or a hardware keyboard, not for a finger alone. */
@Composable
@ReadOnlyComposable
internal fun showShortcutHints(): Boolean = DeskTheme.inputMode == InputMode.Pointer || hasHardwareKeyboard()
