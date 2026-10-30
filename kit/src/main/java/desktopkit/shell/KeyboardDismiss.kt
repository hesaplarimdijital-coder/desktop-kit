package desktopkit.shell

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager

/**
 * The kit's text fields on screen: where they are and whether one has the focus, so the shell can
 * tell a tap on a field from a tap elsewhere. Each field registers itself while it is shown.
 */
internal class TextFields {
    class Field {
        var coordinates: LayoutCoordinates? = null
        var focused = false
    }

    private val fields = mutableListOf<Field>()

    fun add(field: Field) {
        fields += field
    }

    fun remove(field: Field) {
        fields -= field
    }

    val anyFocused: Boolean get() = fields.any { it.focused }

    /** Whether [windowPosition] lies on one of the fields (their buttons included). */
    fun contains(windowPosition: Offset): Boolean = fields.any { field ->
        val coordinates = field.coordinates
        coordinates != null && coordinates.isAttached && coordinates.boundsInWindow().contains(windowPosition)
    }
}

/**
 * With a finger, the on-screen keyboard steps aside once the user moves on, as in the system's own
 * apps: scrolling the content, or a tap that lands outside every text field, takes the focus from
 * the kit's field that has it, and with it the keyboard. A scroll the field asks for itself (to
 * stay in sight above a rising keyboard) has no finger behind it and leaves the keyboard open, and
 * so does a mouse.
 */
@Composable
internal fun Modifier.dismissKeyboardOnTouch(fields: TextFields): Modifier {
    val focusManager = LocalFocusManager.current
    val latestFocusManager by rememberUpdatedState(focusManager)
    val gesture = remember { TouchGesture() }
    val connection = remember(fields) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (gesture.fingerDown && source == NestedScrollSource.UserInput && available.y != 0f && fields.anyFocused) {
                    latestFocusManager.clearFocus()
                }
                return Offset.Zero
            }
        }
    }
    return this
        .onGloballyPositioned { gesture.coordinates = it }
        .pointerInput(fields) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                // a mouse keeps the desktop's way (the field keeps the focus); fingers and pens move on
                if (down.type == PointerType.Mouse) return@awaitEachGesture
                gesture.fingerDown = true
                try {
                    var moved = false
                    var last = down.position
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                        last = change.position
                        if (!change.pressed) break
                    }
                    if (!moved && fields.anyFocused) {
                        val at = gesture.coordinates?.takeIf { it.isAttached }?.localToWindow(last)
                        if (at != null && !fields.contains(at)) latestFocusManager.clearFocus()
                    }
                } finally {
                    gesture.fingerDown = false
                }
            }
        }
        .nestedScroll(connection)
}

private class TouchGesture {
    var fingerDown = false
    var coordinates: LayoutCoordinates? = null
}
