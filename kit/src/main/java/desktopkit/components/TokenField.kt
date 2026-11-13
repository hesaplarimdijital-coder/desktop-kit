package desktopkit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import desktopkit.R
import desktopkit.theme.DeskGlass
import desktopkit.theme.DeskTheme
import desktopkit.theme.GlassThickness
import desktopkit.theme.glass

/** One suggestion of a [TokenField]: what becomes the token ([value]), and how it is shown. */
@Immutable
data class TokenSuggestion(val value: String, val title: String, val subtitle: String? = null)

/**
 * A field that holds a list of short values as chips: e-mail addresses, tags, keywords.
 *
 * - Typing a separator ([separators]: comma and semicolon by default), Enter or the keyboard's action
 *   key turns what was typed into a chip; a pasted list ("a@x.org, b@y.org") becomes chips at once.
 * - Backspace in an empty field removes the last chip; each chip has its own × too.
 * - A chip that [isValid] rejects is outlined in the danger colour and read out as invalid.
 * - [suggestions] gives what to offer for the text being typed (past addresses, contacts): a list
 *   opens under the field; ↑ and ↓ move in it, Enter or a tap takes one, Esc closes it, and typing
 *   goes on in the field all the while.
 * - [tokenLabel] is what a chip shows and TalkBack reads (a name for an address).
 *
 * The app keeps [tokens] and stores what [onTokensChange] hands it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TokenField(
    tokens: List<String>,
    onTokensChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    hint: String = "",
    separators: Set<Char> = setOf(',', ';'),
    isValid: (String) -> Boolean = { true },
    tokenLabel: (String) -> String = { it },
    suggestions: (String) -> List<TokenSuggestion> = { emptyList() },
    keyboardType: KeyboardType = KeyboardType.Email,
    enabled: Boolean = true,
) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val touch = isTouchDensity()
    val density = LocalDensity.current
    val latestTokens by rememberUpdatedState(tokens)
    var text by remember { mutableStateOf(TextFieldValue("")) }
    var highlighted by remember { mutableIntStateOf(0) }
    var dismissed by remember { mutableStateOf(false) }
    var fieldWidth by remember { mutableIntStateOf(0) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    val invalidLabel = stringResource(R.string.dk_token_invalid)
    val removeLabel = stringResource(R.string.dk_token_remove)

    val offered = if (text.text.isBlank() || dismissed || !focused) emptyList() else suggestions(text.text.trim())
    val shownIndex = highlighted.coerceIn(0, (offered.size - 1).coerceAtLeast(0))

    fun commit(values: List<String>, rest: String = "") {
        val added = values.map { it.trim() }.filter { it.isNotEmpty() && it !in latestTokens }
        if (added.isNotEmpty()) onTokensChange(latestTokens + added)
        text = TextFieldValue(rest, TextRange(rest.length))
        highlighted = 0
        dismissed = false
    }

    fun take(suggestion: TokenSuggestion) = commit(listOf(suggestion.value))

    fun onText(value: TextFieldValue) {
        val typed = value.text
        if (typed.any { it in separators || it == '\n' }) {
            // a separator typed or a list pasted: everything before the last separator becomes chips
            val parts = typed.split(*(separators + '\n').toCharArray())
            commit(parts.dropLast(1), parts.last().trimStart())
        } else {
            if (typed != text.text) {
                highlighted = 0
                dismissed = false
            }
            text = value
        }
    }

    Column(modifier) {
        if (label != null) {
            Text(label, style = DeskTheme.type.label, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(dimens.gapXs))
        }
        FlowRow(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { fieldWidth = it.width }
                .heightIn(min = dimens.controlHeight.coerceAtLeast(dimens.minTarget))
                .background(if (enabled) colors.surface else androidx.compose.ui.graphics.Color.Transparent, shape)
                .border(if (focused) 2.dp else 1.dp, if (focused) colors.accent else colors.controlBorder, shape)
                .padding(horizontal = if (touch) 8.dp else 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(dimens.gapXs),
            verticalArrangement = Arrangement.spacedBy(dimens.gapXs, Alignment.CenterVertically),
        ) {
            tokens.forEach { token ->
                TokenChip(
                    label = tokenLabel(token),
                    valid = isValid(token),
                    invalidLabel = invalidLabel,
                    removeLabel = removeLabel,
                    enabled = enabled,
                    onRemove = { onTokensChange(latestTokens - token) },
                )
            }
            BasicTextField(
                value = text,
                onValueChange = ::onText,
                enabled = enabled,
                singleLine = true,
                textStyle = DeskTheme.type.body.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                interactionSource = interaction,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
                // the keyboard's action key makes a chip of what was typed; with nothing typed it moves on
                keyboardActions = KeyboardActions(onNext = {
                    if (text.text.isNotBlank()) {
                        if (offered.isNotEmpty()) take(offered[shownIndex]) else commit(listOf(text.text))
                    } else {
                        defaultKeyboardAction(ImeAction.Next)
                    }
                }),
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 96.dp)
                    .heightIn(min = if (touch) 36.dp else 24.dp)
                    .padding(horizontal = 4.dp)
                    .focusMemory()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when {
                            event.key == Key.DirectionDown && offered.isNotEmpty() -> {
                                highlighted = (shownIndex + 1) % offered.size
                                true
                            }
                            event.key == Key.DirectionUp && offered.isNotEmpty() -> {
                                highlighted = (shownIndex - 1 + offered.size) % offered.size
                                true
                            }
                            (event.key == Key.Enter || event.key == Key.NumPadEnter) && text.text.isNotBlank() -> {
                                if (offered.isNotEmpty()) take(offered[shownIndex]) else commit(listOf(text.text))
                                true
                            }
                            event.key == Key.Escape && offered.isNotEmpty() -> {
                                dismissed = true
                                true
                            }
                            event.key == Key.Backspace && text.text.isEmpty() && tokens.isNotEmpty() -> {
                                onTokensChange(tokens.dropLast(1))
                                true
                            }
                            else -> false
                        }
                    },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (text.text.isEmpty() && tokens.isEmpty() && hint.isNotEmpty()) {
                            Text(hint, style = DeskTheme.type.body, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        inner()
                    }
                },
            )
        }
        if (offered.isNotEmpty()) {
            Popup(
                popupPositionProvider = remember { BelowStartPositionProvider() },
                // the typing stays in the field
                properties = PopupProperties(focusable = false),
                onDismissRequest = { dismissed = true },
            ) {
                val menuShape = RoundedCornerShape(dimens.radiusCard)
                Column(
                    Modifier
                        .width(with(density) { fieldWidth.toDp() })
                        .glass(DeskGlass.raised(colors, GlassThickness.Regular), menuShape)
                        .border(1.dp, colors.borderStrong, menuShape)
                        .padding(vertical = 4.dp),
                ) {
                    offered.take(MAX_SUGGESTIONS).forEachIndexed { index, suggestion ->
                        SuggestionRow(suggestion, highlighted = index == shownIndex) { take(suggestion) }
                    }
                }
            }
        }
    }
}

/** How many suggestions show at once. */
private const val MAX_SUGGESTIONS = 6

@Composable
private fun TokenChip(label: String, valid: Boolean, invalidLabel: String, removeLabel: String, enabled: Boolean, onRemove: () -> Unit) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val touch = isTouchDensity()
    val shape = RoundedCornerShape(dimens.radiusPill)
    val remove = "$removeLabel: $label"
    Row(
        Modifier
            .heightIn(min = if (touch) 32.dp else 24.dp)
            .background(colors.surfaceRaised, shape)
            .border(1.dp, if (valid) colors.borderStrong else colors.danger, shape)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                if (!valid) error(invalidLabel)
                customActions = listOf(CustomAccessibilityAction(remove) { onRemove(); true })
            }
            .padding(start = if (touch) 12.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = DeskTheme.type.label, color = if (valid) colors.text else colors.danger, maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis, modifier = Modifier.widthIn(max = 240.dp))
        DeskIconButton(DeskIcons.Close, remove, onRemove, enabled = enabled, size = if (touch) 32.dp else 24.dp,
            iconSize = if (touch) 16.dp else 12.dp, tooltip = false)
    }
}

@Composable
private fun SuggestionRow(suggestion: TokenSuggestion, highlighted: Boolean, onClick: () -> Unit) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.rowHeight.coerceAtLeast(dimens.minTarget))
            .background(if (highlighted) colors.selection else stateBackground(state))
            .deskClickable(interaction, onClick)
            .padding(horizontal = dimens.gapM, vertical = dimens.gapXs),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(suggestion.title, style = DeskTheme.type.body, color = if (highlighted) colors.onSelection else colors.text,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (suggestion.subtitle != null) {
            Text(suggestion.subtitle, style = DeskTheme.type.labelSmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Under the anchor, at its start edge; above it when there is no room below. */
private class BelowStartPositionProvider : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val gap = 4
        val x = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left else anchorBounds.right - popupContentSize.width
        var y = anchorBounds.bottom + gap
        if (y + popupContentSize.height > windowSize.height) y = anchorBounds.top - gap - popupContentSize.height
        return IntOffset(x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)), y.coerceAtLeast(0))
    }
}
