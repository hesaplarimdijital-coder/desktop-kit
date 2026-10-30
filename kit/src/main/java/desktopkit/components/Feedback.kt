package desktopkit.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import desktopkit.R
import desktopkit.shell.KeyCap
import desktopkit.theme.DeskGlass
import desktopkit.theme.DeskMotion
import desktopkit.theme.DeskTheme
import desktopkit.theme.GlassMode
import desktopkit.theme.GlassThickness
import desktopkit.theme.InputMode
import desktopkit.theme.LocalMotionTier
import desktopkit.theme.MotionTier
import desktopkit.theme.glass
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.math.abs

// ---------------------------------------------------------------- progress

/**
 * A progress bar: [fraction] from 0 to 1, or null while the amount is unknown (a segment then
 * sweeps along the track; with reduced motion it rests as a faint full bar). Changes of [fraction]
 * ease over 300 ms. 6 dp tall on touch, 4 dp with a mouse; [height] overrides it (3 dp under a list
 * row's subtitle).
 */
@Composable
fun DeskProgressBar(
    fraction: Float?,
    modifier: Modifier = Modifier,
    color: Color = DeskTheme.colors.accent,
    trackColor: Color = DeskTheme.colors.border,
    height: Dp = if (isTouchDensity()) 6.dp else 4.dp,
    contentDescription: String? = null,
) {
    val tier = LocalMotionTier.current
    val shown by animateFloatAsState((fraction ?: 0f).coerceIn(0f, 1f), DeskMotion.fade(tier, tween(DeskMotion.LONG_MS, easing = DeskMotion.Standard)),
        label = "progress")
    val loading = stringResource(R.string.dk_loading)
    val semantics = Modifier.semantics {
        this.contentDescription = contentDescription ?: loading
        progressBarRangeInfo = if (fraction == null) ProgressBarRangeInfo.Indeterminate else ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f)
    }
    val bar = modifier.fillMaxWidth().height(height).then(semantics)
    if (fraction != null) {
        Canvas(bar) {
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(trackColor, cornerRadius = radius)
            if (shown > 0f) drawRoundRect(color, size = Size(size.width * shown, size.height), cornerRadius = radius)
        }
    } else if (tier == MotionTier.Reduced) {
        Canvas(bar) {
            drawRoundRect(color.copy(alpha = color.alpha * 0.4f), cornerRadius = CornerRadius(size.height / 2))
        }
    } else {
        val sweep = rememberInfiniteTransition(label = "indeterminate")
        val position by sweep.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1400, easing = DeskMotion.Standard), RepeatMode.Restart),
            label = "sweep",
        )
        Canvas(bar) {
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(trackColor, cornerRadius = radius)
            // a 40 % segment entering from the start edge and leaving at the end
            val segment = size.width * 0.4f
            val start = (position * (size.width + segment) - segment).coerceAtLeast(0f)
            val end = (position * (size.width + segment)).coerceAtMost(size.width)
            if (end > start) drawRoundRect(color, topLeft = Offset(start, 0f), size = Size(end - start, size.height), cornerRadius = radius)
        }
    }
}

// ---------------------------------------------------------------- banner and badge

/** The four tones of a [StatusBanner] and a toast. */
enum class BannerTone { Info, Warning, Error, Success }

/** The colour of [tone] in the current theme. */
@Composable
fun BannerTone.color(): Color {
    val colors = DeskTheme.colors
    return when (this) {
        BannerTone.Info -> colors.accentText
        BannerTone.Warning -> colors.warning
        BannerTone.Error -> colors.danger
        BannerTone.Success -> colors.success
    }
}

/** The icon of [tone]. */
val BannerTone.icon: ImageVector
    get() = when (this) {
        BannerTone.Info -> DeskIcons.Info
        BannerTone.Warning -> DeskIcons.Warning
        BannerTone.Error -> DeskIcons.Error
        BannerTone.Success -> DeskIcons.CheckCircle
    }

/**
 * A message strip in one of four tones: a tinted background, a border and an icon in the tone's
 * colour, the [text] (under a bold [title] when given), and optionally an action button and a close
 * button. TalkBack announces it when it appears.
 */
@Composable
fun StatusBanner(
    tone: BannerTone,
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector = tone.icon,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    closeLabel: String? = null,
    onClose: (() -> Unit)? = null,
) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val color = tone.color()
    val shape = RoundedCornerShape(if (title != null) dimens.radiusCard else dimens.radiusControl)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(color.copy(alpha = if (colors.isDark) 0.12f else 0.10f), shape)
            .border(1.dp, color.copy(alpha = 0.45f), shape)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(
                start = dimens.gapM + 2.dp,
                end = if (actionLabel != null || closeLabel != null) dimens.gapS + 2.dp else dimens.gapM + 2.dp,
                top = if (title != null) dimens.gapM else dimens.gapS,
                bottom = if (title != null) dimens.gapM else dimens.gapS,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.gapM),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(dimens.iconLarge))
        Column(Modifier.weight(1f).padding(vertical = 2.dp)) {
            if (title != null) Text(title, style = DeskTheme.type.bodyStrong, color = colors.text)
            Text(text, style = if (title != null) DeskTheme.type.label else DeskTheme.type.body,
                color = if (title != null) colors.textSecondary else colors.text)
        }
        if (actionLabel != null && onAction != null) DeskButton(actionLabel, onAction)
        if (closeLabel != null && onClose != null) DeskIconButton(DeskIcons.Close, closeLabel, onClose)
    }
}

/** A small count in a pill ("8", "99+"); nothing for zero. */
@Composable
fun CountBadge(
    count: Int,
    modifier: Modifier = Modifier,
    color: Color = DeskTheme.colors.accent,
    onColor: Color = DeskTheme.colors.onAccent,
    max: Int = 99,
) {
    if (count <= 0) return
    val touch = isTouchDensity()
    val size = if (touch) 22.dp else 18.dp
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = size, minHeight = size)
            .background(color, RoundedCornerShape(size / 2))
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > max) "$max+" else count.toString(), style = DeskTheme.type.mono.copy(fontWeight = FontWeight.Medium),
            color = onColor, maxLines = 1)
    }
}

// ---------------------------------------------------------------- toasts

/** How long a toast stays: about 4 s, 10 s, or until it is closed. TalkBack users get longer. */
enum class ToastDuration { Short, Long, Indefinite }

/** How a toast ended. */
enum class ToastResult { Dismissed, ActionPerformed }

/** One toast on screen; see [DeskToastState.show]. */
@Stable
class DeskToast internal constructor(
    val message: String,
    val actionLabel: String?,
    val duration: ToastDuration,
    val tone: BannerTone?,
    val icon: ImageVector?,
    /** A keyboard shortcut shown next to the action on DeX ("Ctrl+Z"); the app handles the key itself. */
    val shortcut: String?,
    private val continuation: CancellableContinuation<ToastResult>,
) {
    /** The action button was used. */
    fun performAction() {
        if (continuation.isActive) continuation.resume(ToastResult.ActionPerformed)
    }

    /** Closed, timed out or swiped away. */
    fun dismiss() {
        if (continuation.isActive) continuation.resume(ToastResult.Dismissed)
    }
}

/**
 * Short messages at the bottom of the window, one at a time, optionally with an action ("Undo"):
 *
 * ```
 * val toasts = rememberToastState()
 * DesktopShell(…, toasts = toasts) { … }
 * …
 * scope.launch {
 *     if (toasts.show("Moved to the trash", actionLabel = "Undo") == ToastResult.ActionPerformed) restore()
 * }
 * ```
 *
 * [show] waits until the toast is gone and says how it ended; toasts shown meanwhile wait their turn.
 */
@Stable
class DeskToastState {
    private val mutex = Mutex()

    /** The toast on screen, if any. */
    var current: DeskToast? by mutableStateOf(null)
        private set

    /**
     * Shows [message] and suspends until the toast is dismissed or its action used. [duration]
     * defaults to [ToastDuration.Long] for a toast with an action, [ToastDuration.Short] otherwise.
     */
    suspend fun show(
        message: String,
        actionLabel: String? = null,
        duration: ToastDuration = if (actionLabel != null) ToastDuration.Long else ToastDuration.Short,
        tone: BannerTone? = null,
        icon: ImageVector? = tone?.icon,
        shortcut: String? = null,
    ): ToastResult = mutex.withLock {
        try {
            suspendCancellableCoroutine { continuation ->
                current = DeskToast(message, actionLabel, duration, tone, icon, shortcut, continuation)
            }
        } finally {
            current = null
        }
    }

    /** Closes the toast on screen. */
    fun dismissCurrent() {
        current?.dismiss()
    }
}

@Composable
fun rememberToastState(): DeskToastState = remember { DeskToastState() }

/**
 * Draws the toasts of [state]: a glass card (solid where glass is off) with the message, the action
 * in the accent colour and a close button; it slides up into place and fades out. On touch a
 * sideways swipe dismisses it. [desktopkit.shell.DesktopShell] places one above its bars when given
 * `toasts`; elsewhere put it at the bottom of the window.
 */
@Composable
fun ToastHost(state: DeskToastState, modifier: Modifier = Modifier) {
    val current = state.current
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(current) {
        if (current == null) return@LaunchedEffect
        val base = when (current.duration) {
            ToastDuration.Short -> 4000L
            ToastDuration.Long -> 10000L
            ToastDuration.Indefinite -> Long.MAX_VALUE
        }
        val millis = accessibility?.calculateRecommendedTimeoutMillis(base, containsIcons = true, containsText = true,
            containsControls = current.actionLabel != null) ?: base
        if (millis == Long.MAX_VALUE) return@LaunchedEffect
        delay(millis)
        current.dismiss()
    }
    // the last toast stays drawn while it fades out (remembered after each composition, not during it)
    val last = remember { mutableStateOf<DeskToast?>(null) }
    SideEffect { if (current != null) last.value = current }
    val tier = LocalMotionTier.current
    AnimatedVisibility(
        visible = current != null,
        modifier = modifier,
        enter = if (tier == MotionTier.Reduced) fadeIn(tween(0)) else
            fadeIn(tween(DeskMotion.MEDIUM_MS)) + slideInVertically(DeskMotion.spatial(tier, tween(DeskMotion.LONG_MS, easing = DeskMotion.EmphasizedDecelerate))) { it / 2 },
        exit = if (tier == MotionTier.Reduced) fadeOut(tween(0)) else
            fadeOut(tween(DeskMotion.MEDIUM_MS, easing = DeskMotion.EmphasizedAccelerate)) + slideOutVertically(tween(DeskMotion.MEDIUM_MS)) { it / 4 },
    ) {
        (current ?: last.value)?.let { ToastCard(it) }
    }
}

@Composable
private fun ToastCard(toast: DeskToast) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val touch = DeskTheme.inputMode == InputMode.Touch
    val shape = RoundedCornerShape(dimens.radiusCard)
    val scope = rememberCoroutineScope()
    val swipe = remember(toast) { Animatable(0f) }
    var width by remember { mutableStateOf(1) }
    val glassy = DeskGlass.source != null && DeskGlass.mode != GlassMode.Solid
    val label = stringResource(R.string.dk_notification)
    Row(
        modifier = Modifier
            .padding(horizontal = dimens.pagePadding, vertical = dimens.gapM)
            .widthIn(max = 560.dp)
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .graphicsLayer {
                translationX = swipe.value
                alpha = 1f - (abs(swipe.value) / width).coerceIn(0f, 1f)
            }
            // a shadow would show through glass; a solid toast keeps it
            .then(if (glassy) Modifier else Modifier.shadow(12.dp, shape, clip = false))
            .glass(DeskGlass.raised(colors, GlassThickness.Thin), shape)
            .border(1.dp, colors.borderStrong, shape)
            .semantics {
                paneTitle = label
                liveRegion = LiveRegionMode.Polite
            }
            .then(
                if (!touch) Modifier else Modifier.draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta -> scope.launch { swipe.snapTo(swipe.value + delta) } },
                    onDragStopped = { velocity ->
                        if (abs(swipe.value) > width * 0.3f || abs(velocity) > 1200f) {
                            toast.dismiss()
                        } else {
                            swipe.animateTo(0f, DeskMotion.gesture())
                        }
                    },
                ),
            )
            .heightIn(min = if (touch) 52.dp else 40.dp)
            .padding(start = dimens.gapM + 2.dp, end = dimens.gapXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.gapM),
    ) {
        if (toast.icon != null) {
            Icon(toast.icon, contentDescription = null, tint = toast.tone?.color() ?: colors.textSecondary, modifier = Modifier.size(dimens.icon))
        }
        Text(toast.message, style = DeskTheme.type.body, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).padding(vertical = dimens.gapS))
        if (toast.actionLabel != null) {
            TextAction(toast.actionLabel, onClick = { toast.performAction() })
            if (toast.shortcut != null && !touch) KeyCap(toast.shortcut)
        }
        DeskIconButton(DeskIcons.Close, stringResource(R.string.dk_close), onClick = { toast.dismiss() },
            size = if (touch) 40.dp else 28.dp, iconSize = if (touch) 18.dp else 14.dp)
    }
}

/** A text-only button in the accent colour: the action of a toast. */
@Composable
internal fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    val background by animatedStateBackground(state)
    Box(
        modifier
            .heightIn(min = dimens.minTarget.coerceAtLeast(28.dp))
            .focusRing(state, shape)
            .background(background, shape)
            .deskClickable(interaction, onClick)
            .padding(horizontal = dimens.gapS),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = DeskTheme.type.label.copy(fontWeight = FontWeight.Medium), color = colors.accentText, maxLines = 1)
    }
}
