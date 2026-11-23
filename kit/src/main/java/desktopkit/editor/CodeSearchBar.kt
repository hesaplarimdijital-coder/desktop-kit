package desktopkit.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import desktopkit.R
import desktopkit.components.DeskButton
import desktopkit.components.DeskIconButton
import desktopkit.components.DeskIcons
import desktopkit.components.DeskTextField
import desktopkit.pluralString
import desktopkit.shell.WidthClass
import desktopkit.shell.currentWidthClass
import desktopkit.theme.DeskTheme

/**
 * The find and replace bar of a [CodeEditor] given the same [search]; it shows while [CodeSearchState.isOpen].
 * The app places it, normally docked above the editor in a column (on a phone below the shell's top bar).
 *
 * A find field with the count ("3 / 17", "No results", or "Invalid expression" in the danger colour while
 * a regular expression does not compile), the options match case, whole word and regular expression
 * (Alt+C, Alt+W, Alt+R), previous and next (Shift+Enter and Enter, Shift+F3 and F3, or the keyboard's search
 * key), and close (Esc, which leaves the editor with the focus at the current match). With [allowReplace] a
 * chevron shows the replace row (Ctrl+H): a replace field (Enter replaces the current match, Ctrl+Alt+Enter
 * all of them), Replace and Replace all. In a phone-width window the options and the replace buttons take
 * rows of their own.
 */
@Composable
fun CodeSearchBar(search: CodeSearchState, modifier: Modifier = Modifier, allowReplace: Boolean = true) {
    if (!search.isOpen) return
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val compact = currentWidthClass() == WidthClass.Compact
    val touch = dimens.minTarget >= 48.dp
    val findFocus = remember { FocusRequester() }
    val replaceFocus = remember { FocusRequester() }
    // the field's own value keeps the keyboard's composing region; a query set from outside replaces it
    var find by remember { mutableStateOf(TextFieldValue(search.query)) }
    val findValue = if (find.text == search.query) find else TextFieldValue(search.query, TextRange(search.query.length))
    LaunchedEffect(search.focusRequests) {
        // opened (again): the whole query selected, so typing replaces it
        find = TextFieldValue(search.query, TextRange(0, search.query.length))
        findFocus.requestFocus()
    }
    var focusReplace by remember { mutableStateOf(0) }
    LaunchedEffect(focusReplace) { if (focusReplace > 0) replaceFocus.requestFocus() }

    fun commonKey(event: KeyEvent): Boolean {
        val plain = !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed
        return when {
            event.key == Key.Escape && plain -> {
                search.close()
                true
            }
            event.key == Key.F3 && plain -> {
                if (event.isShiftPressed) search.previous() else search.next()
                true
            }
            event.isAltPressed && !event.isCtrlPressed && event.key == Key.C -> { search.matchCase = !search.matchCase; true }
            event.isAltPressed && !event.isCtrlPressed && event.key == Key.W -> { search.wholeWord = !search.wholeWord; true }
            event.isAltPressed && !event.isCtrlPressed && event.key == Key.R -> { search.regex = !search.regex; true }
            (event.isCtrlPressed || event.isMetaPressed) && event.key == Key.H && allowReplace -> {
                search.replaceVisible = true
                focusReplace++
                true
            }
            (event.isCtrlPressed || event.isMetaPressed) && event.key == Key.F -> {
                search.open()
                true
            }
            else -> false
        }
    }

    val findKeys = Modifier.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent event.key == Key.Enter || event.key == Key.NumPadEnter
        when {
            event.key == Key.Enter || event.key == Key.NumPadEnter -> {
                if (event.isShiftPressed) search.previous() else search.next()
                true
            }
            else -> commonKey(event)
        }
    }
    val replaceKeys = Modifier.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent event.key == Key.Enter || event.key == Key.NumPadEnter
        when {
            (event.key == Key.Enter || event.key == Key.NumPadEnter) && (event.isCtrlPressed || event.isMetaPressed) && event.isAltPressed -> {
                search.replaceAll()
                true
            }
            event.key == Key.Enter || event.key == Key.NumPadEnter -> {
                search.replaceCurrent()
                true
            }
            else -> commonKey(event)
        }
    }

    val border = colors.border
    // the rows below the find row start where its field does, past the replace chevron
    val indent: @Composable () -> Unit = { if (allowReplace) Spacer(Modifier.width(dimens.minTarget.coerceAtLeast(dimens.controlHeight))) }
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface)
            .drawBehind {
                // a hairline between the bar and the text below it
                val line = 1.dp.toPx()
                drawLine(border, Offset(0f, size.height - line / 2), Offset(size.width, size.height - line / 2), strokeWidth = line)
            }
            .padding(horizontal = dimens.gapS, vertical = dimens.gapXs),
        verticalArrangement = Arrangement.spacedBy(dimens.gapXs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.gapXs)) {
            if (allowReplace) {
                DeskIconButton(
                    if (search.replaceVisible) DeskIcons.ChevronDown else DeskIcons.ChevronRight,
                    stringResource(if (search.replaceVisible) R.string.dk_search_hide_replace else R.string.dk_search_show_replace),
                    { search.replaceVisible = !search.replaceVisible },
                    shortcut = "Ctrl+H",
                )
            }
            DeskTextField(
                value = findValue,
                onValueChange = {
                    find = it
                    search.query = it.text
                },
                modifier = Modifier.weight(1f).focusRequester(findFocus).then(findKeys),
                hint = stringResource(R.string.dk_search_find),
                leading = DeskIcons.Search,
                error = search.invalidPattern,
                imeAction = ImeAction.Search,
                onImeAction = search::next,
                trailingContent = {
                    MatchCount(search)
                    if (!compact) Options(search, touch)
                },
            )
            val anyMatch = search.matchCount > 0
            DeskIconButton(DeskIcons.ChevronUp, stringResource(R.string.dk_search_previous), search::previous, enabled = anyMatch, shortcut = "Shift+F3")
            DeskIconButton(DeskIcons.ChevronDown, stringResource(R.string.dk_search_next), search::next, enabled = anyMatch, shortcut = "F3")
            DeskIconButton(DeskIcons.Close, stringResource(R.string.dk_search_close), search::close, shortcut = "Esc")
        }
        if (compact) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.gapXs)) {
                indent()
                Options(search, touch)
            }
        }
        if (allowReplace && search.replaceVisible) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.gapXs)) {
                indent()
                DeskTextField(
                    value = search.replacement,
                    onValueChange = { search.replacement = it },
                    modifier = Modifier.weight(1f).focusRequester(replaceFocus).then(replaceKeys),
                    hint = stringResource(R.string.dk_search_replace_with),
                    imeAction = ImeAction.Done,
                )
                if (!compact) {
                    Row(Modifier.padding(start = dimens.gapXs), horizontalArrangement = Arrangement.spacedBy(dimens.gapS)) { ReplaceButtons(search) }
                }
            }
            if (compact) {
                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(dimens.gapS)) { ReplaceButtons(search) }
            }
        }
    }
}

/** "3 / 17", "17 matches", "No results", or what is wrong with the expression. Read out as it changes. */
@Composable
private fun MatchCount(search: CodeSearchState) {
    val colors = DeskTheme.colors
    val count = search.matchCount
    val current = search.currentMatch
    val text: String
    val description: String
    when {
        search.query.isEmpty() -> return
        search.invalidPattern -> {
            text = stringResource(R.string.dk_search_invalid)
            description = text
        }
        count == 0 -> {
            text = stringResource(R.string.dk_search_no_results)
            description = text
        }
        current >= 0 -> {
            text = stringResource(R.string.dk_search_position, current + 1, count)
            description = stringResource(R.string.dk_search_position_description, current + 1, count)
        }
        else -> {
            text = pluralString(R.plurals.dk_search_matches, count, count)
            description = text
        }
    }
    Text(
        text,
        style = DeskTheme.type.label,
        color = if (search.invalidPattern) colors.danger else colors.textTertiary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.semantics {
            contentDescription = description
            liveRegion = LiveRegionMode.Polite
        },
    )
}

/** Match case, whole word, regular expression: buttons that stay pressed while their option is on. */
@Composable
private fun Options(search: CodeSearchState, touch: Boolean) {
    OptionButton(DeskIcons.MatchCase, stringResource(R.string.dk_search_match_case), search.matchCase, "Alt+C", touch) { search.matchCase = it }
    OptionButton(DeskIcons.WholeWord, stringResource(R.string.dk_search_whole_word), search.wholeWord, "Alt+W", touch) { search.wholeWord = it }
    OptionButton(DeskIcons.Regex, stringResource(R.string.dk_search_regex), search.regex, "Alt+R", touch) { search.regex = it }
}

@Composable
private fun OptionButton(icon: ImageVector, label: String, on: Boolean, shortcut: String, touch: Boolean, onChange: (Boolean) -> Unit) {
    DeskIconButton(
        icon, label, { onChange(!on) },
        modifier = Modifier.semantics { toggleableState = ToggleableState(on) },
        selected = on,
        size = if (touch) 32.dp else 24.dp,
        iconSize = if (touch) 16.dp else 12.dp,
        shortcut = shortcut,
    )
}

@Composable
private fun ReplaceButtons(search: CodeSearchState) {
    // not while the query as it is now is still searched: the matches would be the old query's
    DeskButton(stringResource(R.string.dk_search_replace), search::replaceCurrent, enabled = search.canReplace)
    DeskButton(stringResource(R.string.dk_search_replace_all), search::replaceAll, enabled = search.canReplace)
}
