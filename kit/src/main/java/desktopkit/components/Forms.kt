package desktopkit.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import desktopkit.R
import desktopkit.shell.LocalShellOverlayHost
import desktopkit.shell.TextFields
import desktopkit.theme.DeskMotion
import desktopkit.theme.DeskTheme
import desktopkit.theme.LocalMotionTier
import desktopkit.theme.rememberDeskHaptics
import java.text.NumberFormat

// ---------------------------------------------------------------- text fields

/** Touch sizes are in use (48 dp targets), as opposed to the pointer sizes of DeX and a mouse. */
@Composable
internal fun isTouchDensity(): Boolean = DeskTheme.dimens.minTarget >= 48.dp

/**
 * The kit's text field, drawn like its buttons: the surface colour, a 1 dp border that turns into a
 * 2 dp accent ring while the field has the focus, and a 2 dp danger ring with [error]. One line by
 * default; [singleLine] false grows from [minLines] to [maxLines] lines.
 *
 * - [label] sits above the field, [supportingText] below it (in the danger colour with [error]);
 *   both belong to the field, so TalkBack reads them with it;
 * - [leading] is an icon before the text, [suffix] a unit after it ("ms", "MB");
 * - [clearable] adds a clear button while there is text; [password] hides the text and adds a
 *   button that shows it;
 * - [mono] sets the text in the monospace face (paths, numbers);
 * - [onImeAction] runs on the keyboard's action key ([imeAction]) and on Enter in a single line,
 *   in place of the key's own action. Without it the key does what the system does: Done closes
 *   the keyboard, Next moves to the next field.
 */
@Composable
fun DeskTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    label: String? = null,
    leading: ImageVector? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    suffix: String? = null,
    clearable: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    password: Boolean = false,
    error: Boolean = false,
    supportingText: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    mono: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
) = TextFieldImpl(
    value, onValueChange, modifier, hint, label, leading, trailingContent, suffix, clearable, singleLine, minLines, maxLines, password,
    error, supportingText, supportingText, enabled, readOnly, mono, keyboardType, imeAction, onImeAction, interactionSource,
)

/**
 * [DeskTextField] with [errorDescription], what TalkBack says about an error, and [endMessage], one
 * line under the field aligned with its end that may reach past the field's start edge instead of
 * widening it (TalkBack hears it as [errorDescription]).
 */
@Composable
internal fun TextFieldImpl(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    hint: String,
    label: String?,
    leading: ImageVector?,
    trailing: (@Composable () -> Unit)?,
    suffix: String?,
    clearable: Boolean,
    singleLine: Boolean,
    minLines: Int,
    maxLines: Int,
    password: Boolean,
    error: Boolean,
    supportingText: String?,
    errorDescription: String?,
    enabled: Boolean,
    readOnly: Boolean,
    mono: Boolean,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onImeAction: (() -> Unit)?,
    interactionSource: MutableInteractionSource?,
    endMessage: String? = null,
) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val touch = isTouchDensity()
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    var revealed by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(dimens.radiusControl)
    val ring by animateInteractionColor(
        when {
            error -> colors.danger
            focused -> colors.accent
            (hovered || pressed) && enabled && !readOnly -> colors.textTertiary
            else -> colors.borderStrong
        },
    )
    val ringWidth = if ((focused || error) && enabled) 2.dp else 1.dp
    val base = DeskTheme.type.body
    val textStyle = (if (mono) base.copy(fontFamily = DeskTheme.type.mono.fontFamily) else base)
        .copy(color = if (enabled) colors.text else colors.textTertiary)
    val clearLabel = stringResource(R.string.dk_clear)
    val hasEnd = suffix != null || (clearable && value.isNotEmpty() && enabled && !readOnly) || password || trailing != null
    // known to the shell, which closes the keyboard on a tap elsewhere or a scroll (touch)
    val host = LocalShellOverlayHost.current
    val field = remember { TextFields.Field() }
    if (host != null) {
        DisposableEffect(host, field) {
            host.textFields.add(field)
            onDispose { host.textFields.remove(field) }
        }
    }
    SideEffect { field.focused = focused }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .onGloballyPositioned { field.coordinates = it }
            .focusMemory()
            .hoverable(interaction, enabled)
            .then(
                // a hardware Enter in one line runs the action itself: the field would run its
                // imeAction, which is often Default and so nothing
                if (!singleLine || onImeAction == null) Modifier else Modifier.onPreviewKeyEvent { event ->
                    val enter = (event.key == Key.Enter || event.key == Key.NumPadEnter) &&
                        !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed
                    if (enter && event.type == KeyEventType.KeyDown) onImeAction()
                    enter
                },
            )
            .semantics { if (error) this.error(errorDescription ?: "") },
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType, imeAction = imeAction),
        // only a given action replaces the key's own: Done closes the keyboard, Next moves on
        keyboardActions = if (onImeAction == null) KeyboardActions.Default else KeyboardActions(onAny = { onImeAction() }),
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        visualTransformation = if (password && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
        interactionSource = interaction,
        cursorBrush = SolidColor(colors.accent),
        decorationBox = { field ->
            Column(Modifier.alpha(if (enabled) 1f else 0.6f)) {
                if (label != null) {
                    Text(label, style = DeskTheme.type.label, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = dimens.controlHeight.coerceAtLeast(dimens.minTarget))
                        .background(if (enabled) colors.surface else Color.Transparent, shape)
                        .border(ringWidth, ring, shape)
                        .padding(
                            start = if (touch) 14.dp else 8.dp,
                            // the field's buttons bring their own padding
                            end = if (hasEnd) (if (touch) 6.dp else 4.dp) else (if (touch) 14.dp else 8.dp),
                            top = if (singleLine) 0.dp else dimens.gapS,
                            bottom = if (singleLine) 0.dp else dimens.gapS,
                        ),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(dimens.gapS),
                ) {
                    if (leading != null) {
                        Icon(leading, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(dimens.icon))
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && hint.isNotEmpty()) {
                            Text(hint, style = textStyle.copy(color = colors.textTertiary), maxLines = if (singleLine) 1 else maxLines,
                                overflow = TextOverflow.Ellipsis)
                        }
                        field()
                    }
                    if (suffix != null) Text(suffix, style = DeskTheme.type.label, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (clearable && value.isNotEmpty() && enabled && !readOnly) {
                        FieldButton(DeskIcons.Close, clearLabel) { onValueChange("") }
                    }
                    if (password) {
                        FieldButton(
                            if (revealed) DeskIcons.EyeOff else DeskIcons.Eye,
                            stringResource(if (revealed) R.string.dk_password_hide else R.string.dk_password_show),
                        ) { revealed = !revealed }
                    }
                    trailing?.invoke()
                }
                if (supportingText != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(supportingText, style = DeskTheme.type.labelSmall, color = if (error) colors.danger else colors.textTertiary)
                }
                if (endMessage != null) {
                    Spacer(Modifier.height(4.dp))
                    EndAligned {
                        Text(endMessage, style = DeskTheme.type.labelSmall, color = colors.danger, maxLines = 1, softWrap = false,
                            modifier = Modifier.clearAndSetSemantics {})
                    }
                }
            }
        },
    )
}

/**
 * [content] at the end of the available width, as wide as it needs: wider than the space, it
 * reaches past the start edge instead of widening its parent.
 */
@Composable
private fun EndAligned(content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
        layout(width, placeable.height) { placeable.placeRelative(width - placeable.width, 0) }
    }
}

/**
 * A small icon button inside a field (clear, show password, steppers): 32 dp on touch, where a
 * finger reaches it 8 dp around it as well (Compose widens a touch target to 48 dp), and 24 dp,
 * the kit's smallest pointer target, with a mouse.
 */
@Composable
private fun FieldButton(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val touch = isTouchDensity()
    DeskIconButton(icon, label, onClick, enabled = enabled, size = if (touch) 32.dp else 24.dp, iconSize = if (touch) 16.dp else 12.dp)
}

/**
 * A search field you type into (the menu bar's [desktopkit.shell.SearchField] only opens a search):
 * a magnifier before the text, a clear button while there is text, Esc clears it, and the
 * keyboard's search key (or Enter) calls [onSearch].
 */
@Composable
fun DeskSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = stringResource(R.string.dk_search_hint),
    onSearch: ((String) -> Unit)? = null,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    DeskTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.onPreviewKeyEvent { event ->
            val esc = event.key == Key.Escape && event.type == KeyEventType.KeyDown && value.isNotEmpty()
            if (esc) onValueChange("")
            esc
        },
        hint = hint,
        leading = DeskIcons.Search,
        clearable = true,
        enabled = enabled,
        imeAction = ImeAction.Search,
        onImeAction = onSearch?.let { search -> { search(value) } },
        interactionSource = interactionSource,
    )
}

/**
 * What a number field shows: the text as typed. A value set from outside (a reset, a step made
 * elsewhere) replaces the text; the field's own reports coming back do not, even when they come
 * late, after further keystrokes (state passed through a Flow arrives a frame or more later).
 */
private class NumberText<T>(initial: T, private val format: (T) -> String, private val parse: (String) -> T?) {
    var text by mutableStateOf(format(initial))
    private var seen: T = initial
    private val reported = ArrayDeque<T>()

    /** Reports [value] to the caller and remembers it, to know it again when it comes back. */
    fun report(value: T, onValueChange: (T) -> Unit) {
        reported.addLast(value)
        if (reported.size > 64) reported.removeFirst()
        onValueChange(value)
    }

    /** After every composition, with the caller's value. */
    fun sync(value: T) {
        if (value == seen) return
        seen = value
        val echo = reported.indexOf(value)
        if (echo >= 0) {
            repeat(echo + 1) { reported.removeFirst() }
        } else {
            reported.clear()
            if (parse(text) != value) text = format(value)
        }
    }
}

/**
 * A whole number between [min] and [max]. Only digits (and a minus sign when [min] is negative) can
 * be typed; a number outside the range turns the field red and says the range under it, and
 * [onValueChange] is called only with valid numbers. The arrow keys step by [step]; [steppers] adds
 * − and + buttons. [suffix] is the unit ("ms").
 *
 * The field keeps its width while the range shows: the line reaches toward the field's start
 * instead. [value] may come back late (through a Flow): what is being typed stays as typed.
 */
@Composable
fun NumberField(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 0,
    max: Int = Int.MAX_VALUE,
    step: Int = 1,
    suffix: String? = null,
    label: String? = null,
    steppers: Boolean = false,
    enabled: Boolean = true,
) {
    val field = remember { NumberText(value, { it.toString() }, { it.toIntOrNull() }) }
    SideEffect { field.sync(value) }
    val text = field.text
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed in min..max
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val rangeText = when {
        max == Int.MAX_VALUE -> stringResource(R.string.dk_number_min, format.format(min))
        min == Int.MIN_VALUE -> stringResource(R.string.dk_number_max, format.format(max))
        else -> stringResource(R.string.dk_number_range, format.format(min), format.format(max))
    }
    val stepBy = { delta: Int ->
        val next = ((parsed ?: value).toLong() + delta).coerceIn(min.toLong(), max.toLong()).toInt()
        field.text = next.toString()
        field.report(next, onValueChange)
    }
    val touch = isTouchDensity()
    TextFieldImpl(
        value = text,
        onValueChange = { input ->
            val filtered = input.filterIndexed { i, c -> c.isDigit() || (c == '-' && i == 0 && min < 0) }.take(11)
            field.text = filtered
            filtered.toIntOrNull()?.takeIf { it in min..max }?.let { field.report(it, onValueChange) }
        },
        // the default width goes after the caller's modifier, so a width given there wins
        modifier = modifier
            .width(
                when {
                    steppers && touch -> 214.dp
                    steppers -> 156.dp
                    touch -> 150.dp
                    else -> 112.dp
                },
            )
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || !enabled) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> { stepBy(step); true }
                    Key.DirectionDown -> { stepBy(-step); true }
                    else -> false
                }
            },
        hint = "",
        label = label,
        leading = null,
        suffix = suffix,
        clearable = false,
        singleLine = true,
        minLines = 1,
        maxLines = 1,
        password = false,
        error = !valid,
        supportingText = null,
        errorDescription = rangeText,
        enabled = enabled,
        readOnly = false,
        mono = true,
        keyboardType = KeyboardType.Number,
        imeAction = ImeAction.Default,
        onImeAction = null,
        interactionSource = null,
        trailing = if (!steppers) null else {
            {
                Row {
                    FieldButton(DeskIcons.Minus, stringResource(R.string.dk_number_decrease), enabled = enabled && (parsed ?: value) > min) { stepBy(-step) }
                    FieldButton(DeskIcons.Plus, stringResource(R.string.dk_number_increase), enabled = enabled && (parsed ?: value) < max) { stepBy(step) }
                }
            }
        },
        // the range goes under the field, aligned with its end, as wide as it needs
        endMessage = if (valid) null else rangeText,
    )
}

/**
 * A number that may be left empty: blank is null. Only digits can be typed. For optional filters
 * ("at least … kB"). A [value] set from outside (a reset) replaces what was typed; the field's own
 * reports coming back late do not.
 */
@Composable
fun OptionalNumberField(
    value: Long?,
    onValueChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    suffix: String? = null,
    label: String? = null,
    enabled: Boolean = true,
) {
    val field = remember { NumberText(value, { it?.toString().orEmpty() }, { it.toLongOrNull() }) }
    SideEffect { field.sync(value) }
    DeskTextField(
        value = field.text,
        onValueChange = { input ->
            val filtered = input.filter { it.isDigit() }.take(18)
            field.text = filtered
            field.report(filtered.toLongOrNull(), onValueChange)
        },
        modifier = modifier,
        hint = hint,
        label = label,
        suffix = suffix,
        enabled = enabled,
        mono = true,
        keyboardType = KeyboardType.Number,
    )
}

// ---------------------------------------------------------------- check box and switch

/**
 * The kit's check box: a rounded square (22 dp on touch, 16 dp with a mouse) that fills with the
 * accent colour while the check mark draws itself in. A null [onCheckedChange] draws it without
 * handling clicks (a row around it toggles it).
 */
@Composable
fun DeskCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    DeskCheckbox(
        state = ToggleableState(checked),
        onClick = onCheckedChange?.let { change -> { change(!checked) } },
        modifier = modifier,
        enabled = enabled,
        interactionSource = interactionSource,
    )
}

/**
 * A three-state check box: on, off, or [ToggleableState.Indeterminate] (a dash), for a group whose
 * items are partly checked. [onClick] decides the next state.
 */
@Composable
fun DeskCheckbox(
    state: ToggleableState,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val touch = isTouchDensity()
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val interactionState = interaction.collectState()
    val on = state != ToggleableState.Off
    val boxSize = if (touch) 22.dp else 16.dp
    val radius = if (touch) 6.dp else 4.dp
    val tier = LocalMotionTier.current
    val fill by animateInteractionColor(if (on) colors.accent else Color.Transparent, selected = on)
    val outline by animateInteractionColor(if (on) Color.Transparent else colors.textTertiary, selected = on)
    val mark by animateFloatAsState(if (on) 1f else 0f, DeskMotion.fade(tier, tween(DeskMotion.MEDIUM_MS, easing = DeskMotion.EmphasizedDecelerate)),
        label = "checkMark")
    val halo = if (enabled) stateBackground(interactionState) else Color.Transparent
    val target = if (onClick != null) dimens.minTarget.coerceAtLeast(boxSize + 8.dp) else boxSize + 4.dp
    val haloShape = RoundedCornerShape(radius + 4.dp)
    val onAccent = colors.onAccent
    val dash = state == ToggleableState.Indeterminate
    Box(
        modifier
            .size(target)
            .then(
                if (onClick == null) Modifier else Modifier
                    .pointerHoverIcon(PointerIcon.Hand)
                    .hoverable(interaction, enabled)
                    .triStateToggleable(state, interaction, indication = null, enabled = enabled, role = Role.Checkbox, onClick = onClick),
            )
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(boxSize + 8.dp).focusRing(interactionState, haloShape).background(halo, haloShape))
        Canvas(Modifier.size(boxSize)) {
            val corner = CornerRadius(radius.toPx())
            drawRoundRect(fill, cornerRadius = corner)
            if (outline.alpha > 0f) {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    outline,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = corner,
                    style = Stroke(stroke),
                )
            }
            if (mark > 0f) {
                val unit = size.width / 16f
                val path = Path().apply {
                    if (dash) {
                        moveTo(4f * unit, 8f * unit)
                        lineTo(12f * unit, 8f * unit)
                    } else {
                        moveTo(3.5f * unit, 8.5f * unit)
                        lineTo(6.5f * unit, 11.5f * unit)
                        lineTo(12.5f * unit, 4.5f * unit)
                    }
                }
                val measure = PathMeasure().apply { setPath(path, false) }
                val drawn = Path()
                measure.getSegment(0f, measure.length * mark, drawn, true)
                drawPath(drawn, onAccent, style = Stroke(width = 2f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/**
 * The kit's switch: a pill track and a round thumb that slides across on the kit's spring; the
 * track fills with the accent colour when on. 52 × 32 dp on touch, 36 × 20 dp with a mouse. A null
 * [onCheckedChange] draws it without handling clicks (a row around it toggles it).
 */
@Composable
fun DeskSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val touch = isTouchDensity()
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val tier = LocalMotionTier.current
    val trackWidth = if (touch) 52.dp else 36.dp
    val trackHeight = if (touch) 32.dp else 20.dp
    val inset = if (touch) 4.dp else 3.dp
    val position by animateFloatAsState(if (checked) 1f else 0f,
        DeskMotion.spatial(tier, tween(DeskMotion.MEDIUM_MS, easing = DeskMotion.Emphasized), 0.001f), label = "switchThumb")
    val pressedGrowth by animateFloatAsState(if (state.pressed && touch) 1f else 0f, DeskMotion.fastSpatial(), label = "switchPress")
    val track by animateInteractionColor(if (checked) colors.accent else colors.borderStrong, selected = checked)
    val thumb by animateInteractionColor(
        when {
            checked -> colors.onAccent
            colors.isDark -> colors.textSecondary
            else -> Color.White
        },
        selected = checked,
    )
    val halo = if (enabled) stateBackground(state) else Color.Transparent
    val shadow = if (colors.isDark) Color.Transparent else colors.text.copy(alpha = 0.18f)
    val trackShape = RoundedCornerShape(trackHeight / 2)
    val haptics = rememberDeskHaptics()
    Box(
        modifier
            .heightIn(min = dimens.minTarget)
            .width(trackWidth + if (touch) 0.dp else 8.dp)
            .then(
                if (onCheckedChange == null) Modifier else Modifier
                    .pointerHoverIcon(PointerIcon.Hand)
                    .hoverable(interaction, enabled)
                    .toggleable(checked, interaction, indication = null, enabled = enabled, role = Role.Switch) { on ->
                        haptics.toggle(on)
                        onCheckedChange(on)
                    },
            )
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(trackWidth, trackHeight).focusRing(state, trackShape)) {
            val h = size.height
            drawRoundRect(track, cornerRadius = CornerRadius(h / 2))
            val thumbRadius = (h / 2 - inset.toPx()) + pressedGrowth * 2.dp.toPx()
            val start = h / 2
            val end = size.width - h / 2
            val center = Offset(start + (end - start) * position, h / 2)
            if (halo.alpha > 0f) drawCircle(halo, radius = h / 2 + 4.dp.toPx(), center = center)
            if (shadow.alpha > 0f) drawCircle(shadow, radius = thumbRadius + 0.5.dp.toPx(), center = center + Offset(0f, 1.dp.toPx()))
            drawCircle(thumb, radius = thumbRadius, center = center)
        }
    }
}

/**
 * A settings line with a switch: the title (and [subtitle]) on the start side, the switch at the end.
 * The whole line toggles, and TalkBack reads it as one switch.
 */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberDeskHaptics()
    ToggleRow(
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        modifier = modifier
            .hoverable(interaction, enabled)
            .toggleable(checked, interaction, indication = null, enabled = enabled, role = Role.Switch) { on ->
                haptics.toggle(on)
                onCheckedChange(on)
            },
        end = { DeskSwitch(checked, onCheckedChange = null, enabled = enabled, interactionSource = interaction) },
    )
}

/** A check box with its [title]; the whole line toggles. [subtitle] adds a second line. */
@Composable
fun CheckboxRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    ToggleRow(
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        modifier = modifier
            .hoverable(interaction, enabled)
            .toggleable(checked, interaction, indication = null, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange),
        start = { DeskCheckbox(checked, onCheckedChange = null, enabled = enabled, interactionSource = interaction) },
        strong = false,
    )
}

/** [CheckboxRow] for a three-state check box ("Lossy (2 of 3)"). */
@Composable
fun CheckboxRow(
    title: String,
    state: ToggleableState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    ToggleRow(
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        modifier = modifier
            .hoverable(interaction, enabled)
            .triStateToggleable(state, interaction, indication = null, enabled = enabled, role = Role.Checkbox, onClick = onClick),
        start = { DeskCheckbox(state, onClick = null, enabled = enabled, interactionSource = interaction) },
        strong = false,
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String?,
    enabled: Boolean,
    modifier: Modifier,
    start: (@Composable () -> Unit)? = null,
    end: (@Composable () -> Unit)? = null,
    strong: Boolean = true,
) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val touch = isTouchDensity()
    Row(
        modifier
            .fillMaxWidth()
            .pointerHoverIcon(PointerIcon.Hand)
            .heightIn(min = if (start != null) dimens.minTarget else if (touch) 64.dp else 40.dp)
            .padding(vertical = if (start != null) 0.dp else dimens.gapXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (start != null) dimens.gapXs else dimens.gapL),
    ) {
        start?.invoke()
        Column(Modifier.weight(1f).alpha(if (enabled) 1f else 0.6f)) {
            Text(title, style = if (strong) DeskTheme.type.bodyStrong else DeskTheme.type.body, color = colors.text)
            if (subtitle != null) {
                Text(subtitle, style = DeskTheme.type.labelSmall, color = colors.textTertiary, modifier = Modifier.padding(top = 2.dp))
            }
        }
        end?.invoke()
    }
}

