package desktopkit.components

import android.content.ClipData
import android.content.ClipDescription
import android.os.PersistableBundle
import android.view.DragEvent
import android.view.View
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropSourceModifierNode
import androidx.compose.ui.draganddrop.DragAndDropStartTransferScope
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import desktopkit.theme.DeskColors
import desktopkit.theme.DeskTheme
import desktopkit.theme.DeskType
import desktopkit.theme.LocalDeskColors
import desktopkit.theme.LocalDeskType
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
 * app's other windows; other apps get the label as text). As in Samsung's My Files, a drag starts
 * once the pointer has been held still on the element for the system's touch and hold delay
 * (Settings › Accessibility, half a second by default): with a mouse or touchpad always, with a
 * finger only when [touch] is true (otherwise a long press keeps opening the element's menu).
 * Moving earlier starts nothing, so a quick drag across a list never picks up a row by accident.
 *
 * Under the pointer the drag shows a small card with [DeskDragPayload.label] and, for several items,
 * their number. The element itself keeps drawing as usual while it can be dragged (its hover and
 * selection change as they always do). [payload] is asked when the drag starts; null cancels it.
 */
@Composable
fun Modifier.dragSource(
    enabled: Boolean = true,
    acrossWindows: Boolean = true,
    touch: Boolean = false,
    payload: () -> DeskDragPayload?,
): Modifier {
    val latest = rememberUpdatedState(payload)
    if (!enabled) return this
    return this then DragSourceElement(acrossWindows, touch, latest)
}

private data class DragSourceElement(
    val acrossWindows: Boolean,
    val touch: Boolean,
    val payload: State<() -> DeskDragPayload?>,
) : ModifierNodeElement<DragSourceNode>() {
    override fun create() = DragSourceNode(acrossWindows, touch, payload)

    override fun update(node: DragSourceNode) {
        node.acrossWindows = acrossWindows
        node.touch = touch
        node.payload = payload
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "dragSource"
        properties["acrossWindows"] = acrossWindows
        properties["touch"] = touch
    }
}

/**
 * Waits for the pointer to rest on the element (see [dragSource]) and then starts the platform drag
 * with a [DragCard]. Unlike Compose's ready-made drag source it does not draw the element through
 * a picture taken once (that kept rows showing their first look: no hover, a stale selection).
 */
private class DragSourceNode(
    var acrossWindows: Boolean,
    var touch: Boolean,
    var payload: State<() -> DeskDragPayload?>,
) : DelegatingNode(), CompositionLocalConsumerModifierNode {

    private val source = delegate(DragAndDropSourceModifierNode { _ -> startTransfer() })

    init {
        delegate(SuspendingPointerInputModifierNode { awaitHold() })
    }

    private suspend fun PointerInputScope.awaitHold() {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val mouse = down.type == PointerType.Mouse
            // with a finger only where asked; with a mouse only the main button (the other opens menus)
            if (!mouse && !touch) return@awaitEachGesture
            if (mouse && !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
            val slop = viewConfiguration.touchSlop
            val ledEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                // lifted or moved away before the delay: no drag this time
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed || (change.position - down.position).getDistance() > slop) break
                }
                true
            }
            if (ledEarly == null) source.requestDragAndDropTransfer(down.position)
        }
    }

    private fun DragAndDropStartTransferScope.startTransfer() {
        val data = payload.value() ?: return
        val card = DragCard(
            payload = data,
            colors = currentValueOf(LocalDeskColors),
            type = currentValueOf(LocalDeskType),
            density = currentValueOf(LocalDensity),
            fonts = currentValueOf(LocalFontFamilyResolver),
            direction = currentValueOf(LocalLayoutDirection),
        )
        startDragAndDropTransfer(data.toTransferData(acrossWindows), card.pictureSize) { with(card) { draw() } }
    }
}

/**
 * The card a kit drag carries: the payload's label on the raised surface colour with a soft shadow,
 * and for several items their number in an accent badge on its corner (like My Files). The platform
 * puts the middle of the picture under the pointer; the card sits above that point, so the place
 * the pointer is over stays in sight.
 */
private class DragCard(
    payload: DeskDragPayload,
    private val colors: DeskColors,
    type: DeskType,
    private val density: Density,
    fonts: FontFamily.Resolver,
    direction: LayoutDirection,
) {
    private val measurer = TextMeasurer(fonts, density, direction, cacheSize = 0)
    private val label = with(density) {
        measurer.measure(
            AnnotatedString(payload.label),
            style = type.bodyStrong.copy(color = colors.text),
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            maxLines = 1,
            constraints = Constraints(maxWidth = LABEL_MAX.roundToPx()),
        )
    }
    private val count = payload.keys.size
    private val badge = if (count < 2) null else measurer.measure(
        AnnotatedString(if (count > 99) "99+" else count.toString()),
        style = type.labelSmall.copy(color = colors.onAccent, fontWeight = FontWeight.SemiBold),
        maxLines = 1,
    )
    private val px = with(density) {
        Metrics(
            padding = PADDING.toPx(),
            height = HEIGHT.toPx(),
            minWidth = MIN_WIDTH.toPx(),
            radius = RADIUS.toPx(),
            margin = MARGIN.toPx(),
            gap = GAP.toPx(),
            badge = BADGE.toPx(),
            shadow = 2.dp.toPx(),
            line = 1.dp.toPx(),
        )
    }
    private val cardWidth = maxOf(px.minWidth, label.size.width + 2 * px.padding)

    /** Twice the card's height plus the gap, so the card ends [GAP] above the middle (the pointer). */
    val pictureSize = Size(cardWidth + 2 * px.margin, 2 * (px.height + px.gap + px.margin))

    fun DrawScope.draw() {
        val topLeft = Offset(px.margin, size.height / 2 - px.gap - px.height)
        val corner = CornerRadius(px.radius)
        // a soft shadow: a few widening, fainter outlines under the card
        for (step in 3 downTo 1) {
            val grow = step * px.shadow
            drawRoundRect(
                Color.Black.copy(alpha = if (colors.isDark) 0.16f / step else 0.08f / step),
                topLeft = topLeft + Offset(-grow / 2, grow / 2),
                size = Size(cardWidth + grow, px.height + grow / 2),
                cornerRadius = CornerRadius(px.radius + grow / 2),
            )
        }
        drawRoundRect(colors.surfaceRaised, topLeft = topLeft, size = Size(cardWidth, px.height), cornerRadius = corner)
        drawRoundRect(colors.border, topLeft = topLeft, size = Size(cardWidth, px.height), cornerRadius = corner, style = Stroke(px.line))
        drawText(label, topLeft = topLeft + Offset((cardWidth - label.size.width) / 2, (px.height - label.size.height) / 2))
        if (badge != null) {
            val diameter = maxOf(px.badge, badge.size.width + px.padding)
            val center = topLeft + Offset(cardWidth - diameter / 4, diameter / 4)
            drawRoundRect(
                colors.accent,
                topLeft = center - Offset(diameter / 2, px.badge / 2),
                size = Size(diameter, px.badge),
                cornerRadius = CornerRadius(px.badge / 2),
            )
            drawText(badge, topLeft = center - Offset(badge.size.width / 2f, badge.size.height / 2f))
        }
    }

    private class Metrics(
        val padding: Float,
        val height: Float,
        val minWidth: Float,
        val radius: Float,
        val margin: Float,
        val gap: Float,
        val badge: Float,
        val shadow: Float,
        val line: Float,
    )

    private companion object {
        val PADDING = 14.dp
        val HEIGHT = 40.dp
        val MIN_WIDTH = 96.dp
        val LABEL_MAX = 240.dp
        val RADIUS = 10.dp
        val MARGIN = 12.dp
        val GAP = 14.dp
        val BADGE = 22.dp
    }
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
