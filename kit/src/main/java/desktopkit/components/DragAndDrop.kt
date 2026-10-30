package desktopkit.components

import android.content.ClipData
import android.content.ClipDescription
import android.os.PersistableBundle
import android.view.DragEvent
import android.view.View
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import desktopkit.theme.DeskTheme
import desktopkit.theme.InputMode
import desktopkit.theme.rememberDeskHaptics
import java.util.UUID

/**
 * What one of the kit's drags carries: a [kind] the drop targets choose by ("track", "file"), the
 * app's [keys] of the dragged items and a [label] ("3 tracks"). Other apps receive only the label,
 * as plain text; the app's own windows (DeX, split screen) receive all of it.
 */
@Immutable
data class DeskDragPayload(val kind: String, val keys: List<String>, val label: String) {

    internal fun toTransferData(acrossWindows: Boolean): DragAndDropTransferData {
        // what the system shows every window is the label and a token; the app's own windows,
        // which run in this process, look the payload up by the token
        val description = ClipDescription(label, arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN))
        description.extras = PersistableBundle().apply { putString(EXTRA_TOKEN, DragsInFlight.add(this@DeskDragPayload)) }
        return DragAndDropTransferData(
            clipData = ClipData(description, ClipData.Item(label)),
            localState = this,
            flags = if (acrossWindows) View.DRAG_FLAG_GLOBAL else 0,
        )
    }

    companion object {
        private const val EXTRA_TOKEN = "desktopkit.drag"

        /** The kind of a drag, as soon as it starts; null for drags that are not this app's kit drags. */
        fun kindOf(event: DragEvent): String? = from(event)?.kind

        /** The payload of a drag or a drop, also when it comes from another window of the app. */
        fun from(event: DragEvent): DeskDragPayload? = from(event.localState, event.clipDescription)

        internal fun from(localState: Any?, description: ClipDescription?): DeskDragPayload? {
            (localState as? DeskDragPayload)?.let { return it }
            val token = description?.extras?.getString(EXTRA_TOKEN) ?: return null
            return DragsInFlight[token]
        }
    }
}

/**
 * The drags this process started, by token. The app's other windows (another activity: the
 * drag's local state does not reach it) find the payload here; another app, even one built on the
 * kit, finds nothing and does not take the drop.
 */
internal object DragsInFlight {
    private val drags = object : LinkedHashMap<String, DeskDragPayload>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DeskDragPayload>?): Boolean = size > 8
    }

    @Synchronized
    fun add(payload: DeskDragPayload): String = UUID.randomUUID().toString().also { drags[it] = payload }

    @Synchronized
    operator fun get(token: String): DeskDragPayload? = drags[token]
}

/**
 * Lets this element be dragged to a [dropZone] (of this window or, with [acrossWindows], of the
 * app's other windows; other apps get the label as text). With a mouse or touchpad a drag starts
 * once the pointer moves with the button held; on touch only when [touch] is true, on a long press
 * (otherwise a long press keeps opening the element's menu). [payload] is asked when the drag
 * starts; null cancels it.
 */
@Composable
fun Modifier.dragSource(
    enabled: Boolean = true,
    acrossWindows: Boolean = true,
    touch: Boolean = false,
    payload: () -> DeskDragPayload?,
): Modifier {
    val pointer = DeskTheme.inputMode == InputMode.Pointer
    val latest by rememberUpdatedState(payload)
    if (!enabled || (!pointer && !touch)) return this
    return dragAndDropSource { _ -> latest()?.toTransferData(acrossWindows) }
}

/**
 * Makes this element a drop target for the kit's drags of [kinds]. While such a drag is in the air
 * the element is tinted faintly; under the pointer it takes the selection colour and an accent
 * ring ([shape]); [highlight] false leaves the drawing to the caller. [onDrop] returns whether the
 * drop was taken (a short confirm vibration on touch, or a reject one).
 */
@Composable
fun Modifier.dropZone(
    kinds: Set<String>,
    onDrop: (DeskDragPayload) -> Boolean,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(DeskTheme.dimens.radiusControl),
    highlight: Boolean = true,
    onActiveChange: ((active: Boolean, over: Boolean) -> Unit)? = null,
): Modifier {
    var active by remember { mutableStateOf(false) }
    var over by remember { mutableStateOf(false) }
    val latestDrop by rememberUpdatedState(onDrop)
    val latestKinds by rememberUpdatedState(kinds)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestChange by rememberUpdatedState(onActiveChange)
    val haptics = rememberDeskHaptics()
    val target = remember {
        object : DragAndDropTarget {
            private fun set(isActive: Boolean, isOver: Boolean) {
                active = isActive
                over = isOver
                latestChange?.invoke(isActive, isOver)
            }

            override fun onStarted(event: DragAndDropEvent) = set(true, false)
            override fun onEntered(event: DragAndDropEvent) {
                set(true, true)
                haptics.tick()
            }
            override fun onExited(event: DragAndDropEvent) = set(true, false)
            override fun onEnded(event: DragAndDropEvent) = set(false, false)
            override fun onDrop(event: DragAndDropEvent): Boolean {
                set(false, false)
                val payload = DeskDragPayload.from(event.toAndroidDragEvent()) ?: return false
                val taken = latestDrop(payload)
                if (taken) haptics.confirm() else haptics.reject()
                return taken
            }
        }
    }
    val colors = DeskTheme.colors
    return this
        .dragAndDropTarget(
            shouldStartDragAndDrop = { event -> latestEnabled && DeskDragPayload.kindOf(event.toAndroidDragEvent()) in latestKinds },
            target = target,
        )
        .then(
            if (!highlight) Modifier else Modifier.drawWithContent {
                if (over) {
                    drawOutline(shape.createOutline(size, layoutDirection, this), colors.selection)
                }
                drawContent()
                if (active) {
                    val ring = colors.accent.copy(alpha = if (over) 1f else 0.35f)
                    val width = (if (over) 2.dp else 1.dp).toPx()
                    // the ring sits inside the bounds
                    val inner = Size(size.width - width, size.height - width)
                    translate(width / 2, width / 2) {
                        drawOutline(shape.createOutline(inner, layoutDirection, this), ring, style = Stroke(width))
                    }
                }
            },
        )
}
