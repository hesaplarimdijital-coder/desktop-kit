package desktopkit.input

import desktopkit.theme.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** M4: only a mouse or touchpad switched on or off changes the density, not any change of a device. */
class PointerDevicesTest {

    // 3 is the keyboard cover's keyboard, 7 its touchpad, 9 a mouse
    private val switchedOn = mutableSetOf(7)
    private val devices = PointerDevices(intArrayOf(3, 7)) { it in switchedOn }

    @Test
    fun aKeyboardCoversOtherChangesLeaveTheDensityAlone() {
        // a new layout, a reconfigured key map: the cover reports changes of its keyboard and touchpad
        assertNull(devices.changed(3))
        assertNull(devices.changed(7))
        assertNull(devices.changed(7))
    }

    @Test
    fun theTouchpadSwitchedOffAndOnAgain() {
        switchedOn -= 7
        assertEquals(InputMode.Touch, devices.changed(7))
        assertNull("still off", devices.changed(7))
        switchedOn += 7
        assertEquals(InputMode.Pointer, devices.changed(7))
    }

    @Test
    fun aMouseComesAndGoes() {
        switchedOn += 9
        assertEquals(InputMode.Pointer, devices.added(9))
        // the touchpad is still on: removing the mouse changes nothing
        assertNull(devices.removed(9))
        assertEquals(InputMode.Touch, devices.removed(7))
        assertNull("a keyboard leaving says nothing", devices.removed(3))
    }
}
