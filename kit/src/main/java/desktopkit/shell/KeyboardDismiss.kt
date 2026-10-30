package desktopkit.shell

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
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
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalFocusManager

/**
 * The kit's text fields on screen: where they are and whether one has the focus, so the shell can
 * tell a tap on a field from a tap elsewhere. Each field registers itself while it is shown, and so
 * does each element marked with [keepsKeyboardOpen] (focused while the focus is inside it).
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
 * apps: scrolling the content, or a tap that lands outside every text field (and every element
 * marked with [keepsKeyboardOpen]), takes the focus from the field that has it, and with it the
 * keyboard. Only while [keyboardUp] (the on-screen keyboard is shown and no hardware keyboard is
 * typing): with a keyboard cover the field keeps the focus, so typing goes on where it was.
 *
 * A gesture that starts on a field is the field's own and leaves the keyboard open: scrolling a long
 * text inside it, or the scroll that brings the field into sight while the finger is still down. So
 * do a mouse wheel and a scroll with no finger behind it. A mouse click is left to Compose, which by
 * default takes the focus from the field on a click outside it (the ComposeView's
 * `autoClearFocusBehavior`).
 */
@Composable
internal fun Modifier.dismissKeyboardOnTouch(fields: TextFields, keyboardUp: () -> Boolean): Modifier {
    val focusManager = LocalFocusManager.current
    val latestFocusManager by rememberUpdatedState(focusManager)
    val latestKeyboardUp by rememberUpdatedState(keyboardUp)
    val gesture = remember { TouchGesture() }
    val connection = remember(fields) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (gesture.fingerDown && !gesture.onField && source == NestedScrollSource.UserInput && available.y != 0f &&
                    fields.anyFocused && latestKeyboardUp()
                ) {
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
                // a mouse is left to Compose; fingers and pens move on
                if (down.type == PointerType.Mouse) return@awaitEachGesture
                gesture.fingerDown = true
                // a gesture that starts on a field (or on a control that keeps the keyboard) is its own
                gesture.onField = gesture.window(down.position)?.let(fields::contains) == true
                try {
                    var moved = false
                    var last = down.position
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                        last = change.position
                        if (!change.pressed) break
                    }
                    if (!moved && !gesture.onField && fields.anyFocused && latestKeyboardUp()) {
                        val at = gesture.window(last)
                        if (at != null && !fields.contains(at)) latestFocusManager.clearFocus()
                    }
                } finally {
                    gesture.fingerDown = false
                    gesture.onField = false
                }
            }
        }
        .nestedScroll(connection)
}

private class TouchGesture {
    var fingerDown = false
    var onField = false
    var coordinates: LayoutCoordinates? = null

    /** [local], a position in the shell's content, in the window. */
    fun window(local: Offset): Offset? = coordinates?.takeIf { it.isAttached }?.localToWindow(local)
}

/**
 * A tap with a finger on this element leaves the on-screen keyboard open, as a tap on the kit's text
 * fields does: a Send button next to a message field, a formatting bar above it, or a text field of
 * the app's own. Elsewhere a tap closes the keyboard inside a [DesktopShell]; outside a shell this
 * does nothing. The element's own click still runs. An app's own text field marked with it counts as
 * one of the kit's: while it has the focus, a scroll or a tap elsewhere closes the keyboard too.
 */
fun Modifier.keepsKeyboardOpen(): Modifier = this then KeepsKeyboardOpenElement

private object KeepsKeyboardOpenElement : ModifierNodeElement<KeepsKeyboardOpenNode>() {
    override fun create() = KeepsKeyboardOpenNode()
    override fun update(node: KeepsKeyboardOpenNode) = Unit
    override fun hashCode(): Int = 0x6b656570
    override fun equals(other: Any?): Boolean = other === this

    override fun InspectorInfo.inspectableProperties() {
        name = "keepsKeyboardOpen"
    }
}

private class KeepsKeyboardOpenNode :
    Modifier.Node(), CompositionLocalConsumerModifierNode, GlobalPositionAwareModifierNode, FocusEventModifierNode {
    private val area = TextFields.Field()
    private var fields: TextFields? = null

    override fun onAttach() {
        fields = currentValueOf(LocalShellOverlayHost)?.textFields?.also { it.add(area) }
    }

    override fun onDetach() {
        fields?.remove(area)
        fields = null
        area.coordinates = null
        area.focused = false
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        area.coordinates = coordinates
    }

    override fun onFocusEvent(focusState: FocusState) {
        area.focused = focusState.hasFocus
    }
}
