package desktopkit.input

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Punctuation chords match by the character typed: on a Turkish Q keyboard "/" is Shift+7 and no
 * key sends Key.Slash, so the kit's own Ctrl+/ never came (DeskMail's request 10). The test
 * machine's keyboard is a US one: there Shift+7 types "&", which stands in for the Turkish "/".
 */
@RunWith(AndroidJUnit4::class)
class KeyChordCharTest {

    private fun event(code: Int, meta: Int) = KeyEvent(AndroidKeyEvent(0L, 0L, AndroidKeyEvent.ACTION_DOWN, code, 0, meta))

    private val ctrl = AndroidKeyEvent.META_CTRL_ON or AndroidKeyEvent.META_CTRL_LEFT_ON
    private val shift = AndroidKeyEvent.META_SHIFT_ON or AndroidKeyEvent.META_SHIFT_LEFT_ON

    @Test
    fun theKeyItselfStillMatches() {
        val chord = KeyChord(Key.Slash, ctrl = true)
        assertTrue(chord.matches(event(AndroidKeyEvent.KEYCODE_SLASH, ctrl)))
        assertFalse("Ctrl alone is asked for", chord.matches(event(AndroidKeyEvent.KEYCODE_SLASH, 0)))
    }

    @Test
    fun theCharacterTypedWithShiftMatches() {
        val chord = KeyChord(Key.Slash, ctrl = true, char = '&')
        assertTrue("Ctrl+Shift+7 types & here", chord.matches(event(AndroidKeyEvent.KEYCODE_7, ctrl or shift)))
        assertFalse("without Ctrl", chord.matches(event(AndroidKeyEvent.KEYCODE_7, shift)))
        assertFalse("7 alone types 7", chord.matches(event(AndroidKeyEvent.KEYCODE_7, ctrl)))
    }

    @Test
    fun aShiftedSlashIsNotASlash() {
        // Shift+/ types "?" on this keyboard: Ctrl+Shift+/ is not Ctrl+/
        assertFalse(KeyChord(Key.Slash, ctrl = true).matches(event(AndroidKeyEvent.KEYCODE_SLASH, ctrl or shift)))
    }

    @Test
    fun lettersKeepMatchingByKeyOnly() {
        assertTrue(KeyChord(Key.K, ctrl = true).matches(event(AndroidKeyEvent.KEYCODE_K, ctrl)))
        assertFalse(KeyChord(Key.K, ctrl = true).matches(event(AndroidKeyEvent.KEYCODE_K, ctrl or shift)))
    }
}
