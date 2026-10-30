package desktopkit.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * The kit's vibrations, one word per meaning, on the system's own haptic patterns (so they follow
 * the device's touch-feedback setting and strength). Only for fingers: with a mouse or touchpad
 * (DeX) nothing vibrates, as on a desktop.
 *
 * The kit's controls already use it: a switch, check box or filter chip turned on or off
 * ([toggle]), a segment or tab chosen ([tick]), a sheet dragged past the point where it closes
 * ([threshold]). Apps use the same words:
 *
 * ```
 * val haptics = rememberDeskHaptics()
 * DeskButton("Delete", onClick = { haptics.confirm(); delete() })
 * ```
 */
@Stable
class DeskHaptics internal constructor(private val feedback: HapticFeedback, private val active: () -> Boolean) {
    /** A switch, check box or filter chip turned on or off. */
    fun toggle(on: Boolean) = perform(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

    /** One of several choices picked: a segment, a tab. */
    fun tick() = perform(HapticFeedbackType.SegmentTick)

    /** A drag crossed the point where letting go acts (close a sheet, skip a track). */
    fun threshold() = perform(HapticFeedbackType.GestureThresholdActivate)

    /** Something was done for good: saved, sent, deleted. */
    fun confirm() = perform(HapticFeedbackType.Confirm)

    /** Something could not be done: a drop refused, a value out of range. */
    fun reject() = perform(HapticFeedbackType.Reject)

    /** A long press opened something (a menu, the selection mode). */
    fun longPress() = perform(HapticFeedbackType.LongPress)

    private fun perform(type: HapticFeedbackType) {
        if (active()) feedback.performHapticFeedback(type)
    }
}

/** [DeskHaptics] for the current input: silent while a mouse or touchpad drives the app. */
@Composable
fun rememberDeskHaptics(): DeskHaptics {
    val feedback = LocalHapticFeedback.current
    val touch by rememberUpdatedState(DeskTheme.inputMode == InputMode.Touch)
    return remember(feedback) { DeskHaptics(feedback) { touch } }
}
