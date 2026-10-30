package desktopkit.components

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import java.lang.ref.WeakReference
import desktopkit.shell.LocalShellOverlayHost
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.focus.requestFocus
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.FocusRequesterModifierNode
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import desktopkit.theme.DeskMotion
import desktopkit.theme.DeskTheme
import desktopkit.theme.InputMode

/** Visual state of an interactive element: hover, press and keyboard focus. */
data class InteractionState(val hovered: Boolean, val pressed: Boolean, val focused: Boolean)

@Composable
fun InteractionSource.collectState(): InteractionState {
    val hovered by collectIsHoveredAsState()
    val pressed by collectIsPressedAsState()
    val focused by collectIsFocusedAsState()
    return InteractionState(hovered, pressed, focused)
}

/** Background for a desktop-style control: pressed > selected > hovered > [rest]. */
@Composable
fun stateBackground(state: InteractionState, selected: Boolean = false, rest: Color = Color.Transparent): Color {
    val colors = DeskTheme.colors
    return when {
        state.pressed -> colors.pressed
        selected -> colors.selection
        state.hovered -> colors.hover
        else -> rest
    }
}

/**
 * Click handling shared by all kit controls: hand cursor, hover tracking, no ripple
 * (the background states above replace it), keyboard focus with a visible ring.
 */
fun Modifier.deskClickable(
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
): Modifier = this
    .pointerHoverIcon(PointerIcon.Hand)
    .hoverable(interactionSource, enabled)
    .focusMemory()
    .clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onClick = onClick,
    )

/**
 * Remembers which of the kit's elements has the keyboard focus, so that a layer drawn over the
 * shell (the command palette, a sheet) can give the focus back where it was when it closes. Kept
 * by the shell's overlay host; elements outside a shell remember nothing.
 */
internal class FocusMemory {
    private var last: WeakReference<FocusMemoryNode>? = null

    fun focused(node: FocusMemoryNode) {
        last = WeakReference(node)
    }

    /** The element that has the keyboard focus now, if it is one of the kit's. */
    fun current(): FocusMemoryNode? = last?.get()?.takeIf { it.isAttached && it.isFocused }
}

internal class FocusMemoryNode : Modifier.Node(), FocusEventModifierNode, FocusRequesterModifierNode, CompositionLocalConsumerModifierNode {
    var isFocused = false
        private set

    override fun onFocusEvent(focusState: FocusState) {
        isFocused = focusState.isFocused
        if (isFocused) currentValueOf(LocalShellOverlayHost)?.focusMemory?.focused(this)
    }

    /** Gives the keyboard focus back to the element; false when it has gone. */
    fun restore(): Boolean = isAttached && requestFocus()
}

private object FocusMemoryElement : ModifierNodeElement<FocusMemoryNode>() {
    override fun create() = FocusMemoryNode()
    override fun update(node: FocusMemoryNode) = Unit
    override fun hashCode(): Int = 0x6d656d6f
    override fun equals(other: Any?): Boolean = other === this
}

/** Lets the shell give the keyboard focus back to the focus target after this modifier. */
internal fun Modifier.focusMemory(): Modifier = this then FocusMemoryElement

/** 2 dp focus ring in the accent colour, drawn only while the element has keyboard focus. */
@Composable
fun Modifier.focusRing(state: InteractionState, shape: Shape): Modifier =
    if (state.focused) this.border(2.dp, DeskTheme.colors.focusRing, shape) else this

/**
 * [stateBackground] with touch feedback that does not lag behind a mouse.
 *
 * With a mouse ([InputMode.Pointer]) hover and press change the colour in the same frame, as on a
 * desktop: only the element under the pointer is ever lit, nothing trails behind it. A change of
 * [selected] (a click, a key) still eases in [DeskMotion.color]. With touch every change eases.
 */
@Composable
fun animatedStateBackground(state: InteractionState, selected: Boolean = false, rest: Color = Color.Transparent): State<Color> =
    animateInteractionColor(stateBackground(state, selected, rest), selected)

/**
 * The colour behind [animatedStateBackground], for kit controls that compute their own hover and
 * press colour: jumps to [target] in pointer mode unless [selected] changed, eases otherwise. A fade
 * to or from a transparent colour changes only the opacity (an Oklab fade toward transparent black
 * would pass through grey).
 */
@Composable
internal fun animateInteractionColor(
    target: Color,
    selected: Boolean = false,
    spec: AnimationSpec<Color> = DeskMotion.color(),
): State<Color> {
    val fade = remember { InteractionFade(target, selected) }
    fade.retarget(target, selected, instant = DeskTheme.inputMode == InputMode.Pointer)
    val current = rememberUpdatedState(target)
    LaunchedEffect(fade, target) { fade.run(target, spec) }
    return if (fade.easing) fade.color.asState() else current
}

private class InteractionFade(initial: Color, private var selected: Boolean) {
    val color = Animatable(initial)
    private var target = initial

    /** Whether the latest change eases; when false the caller gets the target itself, with no frame in between. */
    var easing = false
        private set

    fun retarget(newTarget: Color, newSelected: Boolean, instant: Boolean) {
        if (newTarget == target && newSelected == selected) return
        easing = !instant || newSelected != selected
        target = newTarget
        selected = newSelected
    }

    suspend fun run(to: Color, spec: AnimationSpec<Color>) {
        if (!easing) {
            color.snapTo(to)
            return
        }
        val from = color.value
        if (from.alpha == 0f && to.alpha > 0f) color.snapTo(to.copy(alpha = 0f))
        color.animateTo(if (to.alpha == 0f) from.copy(alpha = 0f) else to, spec)
        color.snapTo(to)
    }
}

/**
 * Touch feedback in place of a ripple: the element shrinks slightly while a finger holds it and
 * springs back on release. Only in touch mode; with a mouse (DeX) nothing moves, as on a desktop.
 */
@Composable
fun Modifier.pressScale(pressed: Boolean, scale: Float = 0.96f): Modifier {
    val touch = DeskTheme.inputMode == InputMode.Touch
    val value by animateFloatAsState(if (pressed && touch) scale else 1f, DeskMotion.fastSpatial(), label = "pressScale")
    return this.graphicsLayer {
        scaleX = value
        scaleY = value
    }
}
