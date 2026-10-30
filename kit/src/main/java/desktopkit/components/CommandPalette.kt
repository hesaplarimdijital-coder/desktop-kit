package desktopkit.components

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import desktopkit.R
import desktopkit.input.ShortcutRegistry
import desktopkit.shell.KeyCap
import desktopkit.shell.LocalShellOverlayHost
import desktopkit.shell.WidthClass
import desktopkit.shell.bottomInsetPadding
import desktopkit.shell.currentWidthClass
import desktopkit.theme.DeskGlass
import desktopkit.theme.DeskMotion
import desktopkit.theme.DeskTheme
import desktopkit.theme.GlassThickness
import desktopkit.input.showShortcutHints
import desktopkit.theme.InputMode
import desktopkit.theme.LocalMotionTier
import desktopkit.theme.MotionTier
import desktopkit.theme.glass

// ---------------------------------------------------------------- matching

/**
 * How the command palette matches what is typed, with Turkish in mind: case and the letters
 * ı/i, ş/s, ğ/g, ü/u, ö/o, ç/c (and â, î, û) count as the same, so "indir" finds "İndirilenler" and
 * "gec" finds "Gece". A query matches the start of the text best, then the start of a word, then
 * anywhere, then its letters in order ("gyt" in "Gece Yarısı Treni").
 */
object PaletteMatcher {
    /** A match: a higher [score] is better; [ranges] are the matched characters of the text. */
    @Immutable
    data class Match(val score: Int, val ranges: List<IntRange>)

    /** [text] in lower case with the Turkish letters folded; same length as [text], so positions carry over. */
    fun fold(text: String): String = buildString(text.length) {
        for (c in text) append(foldChar(c))
    }

    private fun foldChar(c: Char): Char = when (c) {
        'İ', 'I', 'ı', 'Î', 'î' -> 'i'
        'Ş', 'ş' -> 's'
        'Ğ', 'ğ' -> 'g'
        'Ü', 'ü', 'Û', 'û' -> 'u'
        'Ö', 'ö' -> 'o'
        'Ç', 'ç' -> 'c'
        'Â', 'â' -> 'a'
        else -> c.lowercaseChar()
    }

    private fun isWordStart(text: String, index: Int): Boolean =
        index == 0 || !text[index - 1].isLetterOrDigit()

    /** How well [query] matches [text], or null when it does not. An empty query matches everything with 0. */
    fun match(query: String, text: String): Match? {
        val q = fold(query.trim())
        if (q.isEmpty()) return Match(0, emptyList())
        val t = fold(text)
        if (t.startsWith(q)) return Match(1000 - (t.length - q.length).coerceAtMost(200), listOf(q.indices))
        var index = t.indexOf(q)
        while (index >= 0) {
            if (isWordStart(t, index)) return Match(800 - index.coerceAtMost(200), listOf(index until index + q.length))
            index = t.indexOf(q, index + 1)
        }
        val inside = t.indexOf(q)
        if (inside >= 0) return Match(600 - inside.coerceAtMost(200), listOf(inside until inside + q.length))
        // several words, each found somewhere: "treni gece"
        val words = q.split(' ').filter { it.isNotEmpty() }
        if (words.size > 1) {
            val ranges = mutableListOf<IntRange>()
            for (word in words) {
                val at = t.indexOf(word)
                if (at < 0) return subsequence(q.replace(" ", ""), t)
                ranges += at until at + word.length
            }
            return Match(500 - ranges.sumOf { it.first }.coerceAtMost(200), ranges.sortedBy { it.first })
        }
        return subsequence(q, t)
    }

    /** The query's letters in order, preferring word starts and runs; null when they are not all there. */
    private fun subsequence(q: String, t: String): Match? {
        if (q.isEmpty()) return null
        val ranges = mutableListOf<IntRange>()
        var from = 0
        var gaps = 0
        var wordStarts = 0
        var runStart = -1
        var last = -2
        for (c in q) {
            // prefer the next word start holding the letter, else the next occurrence
            var at = -1
            var i = t.indexOf(c, from)
            while (i >= 0) {
                if (isWordStart(t, i)) { at = i; break }
                i = t.indexOf(c, i + 1)
            }
            if (at < 0 || (last >= 0 && t.indexOf(c, from) == last + 1)) at = t.indexOf(c, from)
            if (at < 0) return null
            if (isWordStart(t, at)) wordStarts++
            if (at != last + 1) {
                if (runStart >= 0) ranges += runStart..last
                runStart = at
                if (last >= 0) gaps++
            }
            last = at
            from = at + 1
        }
        if (runStart >= 0) ranges += runStart..last
        val score = 300 + wordStarts * 20 - gaps * 10 - (ranges.firstOrNull()?.first ?: 0).coerceAtMost(100)
        return Match(score.coerceIn(1, 499), ranges)
    }
}

// ---------------------------------------------------------------- items and ranking

/**
 * One entry of the command palette: a command (from the shortcut table or the app) or a thing to
 * open (an album, a file). [group] is the heading it is listed under and the filter chip it belongs
 * to. [keywords] also match but are not shown. [shortcut] is shown as a key cap ("Ctrl+B"),
 * [hint] as grey text at the end ("Resume at 1:12:05"). [actionLabel] names what Enter does
 * ("play"); [onOpenInNewWindow] makes Ctrl+Enter open it in a new window.
 */
@Stable
class PaletteItem(
    val id: String,
    val title: String,
    val group: String,
    val onSelect: () -> Unit,
    val subtitle: String? = null,
    val icon: ImageVector? = null,
    val leading: (@Composable () -> Unit)? = null,
    val shortcut: String? = null,
    val hint: String? = null,
    val keywords: List<String> = emptyList(),
    val actionLabel: String? = null,
    val onOpenInNewWindow: (() -> Unit)? = null,
)

/** A line of the palette's results: a group heading or an item with what matched in its title. */
@Immutable
sealed interface PaletteRow {
    data class Heading(val title: String) : PaletteRow
    data class Result(val item: PaletteItem, val match: PaletteMatcher.Match, val best: Boolean = false) : PaletteRow
}

/** Ranking of the palette, free of Compose so it can be tested. */
object PaletteSearch {
    /**
     * The rows for [query]: when something is typed, the best match first under [bestHeading],
     * then every group with its matches (title matches before keyword matches), at most [perGroup]
     * each unless [filter] keeps a single group. Groups keep the order of their first item.
     */
    fun rows(
        items: List<PaletteItem>,
        query: String,
        filter: String? = null,
        bestHeading: String = "",
        perGroup: Int = 6,
    ): List<PaletteRow> {
        val scored = items.filter { filter == null || it.group == filter }.mapNotNull { item ->
            val title = PaletteMatcher.match(query, item.title)
            if (title != null) return@mapNotNull item to title
            val other = (listOfNotNull(item.subtitle) + item.keywords).firstNotNullOfOrNull { PaletteMatcher.match(query, it) }
                ?: return@mapNotNull null
            item to PaletteMatcher.Match(other.score - 200, emptyList())
        }
        if (scored.isEmpty()) return emptyList()
        val groups = scored.map { it.first.group }.distinct()
        val rows = mutableListOf<PaletteRow>()
        var best: PaletteItem? = null
        if (query.isNotBlank()) {
            val (item, match) = scored.maxBy { it.second.score }
            best = item
            rows += PaletteRow.Heading(bestHeading)
            rows += PaletteRow.Result(item, match, best = true)
        }
        val limit = if (filter != null) Int.MAX_VALUE else perGroup
        for (group in groups) {
            val inGroup = scored.filter { it.first.group == group && it.first !== best }
                .sortedByDescending { it.second.score }
                .take(limit)
            if (inGroup.isEmpty()) continue
            rows += PaletteRow.Heading(group)
            inGroup.forEach { (item, match) -> rows += PaletteRow.Result(item, match) }
        }
        return rows
    }
}

/** The shortcut table as palette commands, listed under their menu groups with their key caps. */
fun ShortcutRegistry.paletteItems(resources: Resources, perform: (String) -> Unit): List<PaletteItem> =
    groups.flatMap { group ->
        inGroup(group).map { shortcut ->
            PaletteItem(
                id = shortcut.id,
                title = resources.getString(shortcut.label),
                group = resources.getString(group.title),
                onSelect = { perform(shortcut.id) },
                shortcut = shortcut.chord.label(resources),
                icon = DeskIcons.Command,
            )
        }
    }

// ---------------------------------------------------------------- state and UI

/** Whether the palette is open, and what is typed and filtered in it. */
@Stable
class CommandPaletteState {
    var isOpen by mutableStateOf(false)
        private set
    var query by mutableStateOf("")
    /** The group chosen with a filter chip; null shows every group. */
    var filter by mutableStateOf<String?>(null)

    /** Opens the palette, empty or with [initialQuery]. */
    fun open(initialQuery: String = "") {
        query = initialQuery
        filter = null
        isOpen = true
    }

    fun close() {
        isOpen = false
    }
}

@Composable
fun rememberCommandPaletteState(): CommandPaletteState = remember { CommandPaletteState() }

/**
 * The command palette (Ctrl+K): a search field over the commands of the shortcut table and the
 * app's own entries ([items], asked again for every query), grouped, with the best match first
 * and the matched letters marked.
 *
 * - On tablets and DeX a glass panel near the top over a scrim; on a phone the whole window, with
 *   a back arrow.
 * - The keyboard focus stays in the field: Up and Down choose, Enter runs, Ctrl+Enter opens in a
 *   new window (when the item can), Tab and Shift+Tab move between the [filters] (group names; "All"
 *   comes first), Esc, Back or a click on the scrim closes. The mouse chooses by hovering. Inside
 *   the shell, the focus goes back where it was when the palette closes.
 * - [hint] is the grey text of the empty field.
 *
 * Inside [desktopkit.shell.DesktopShell] put it in the shell's `overlays`, so it is drawn over the
 * bars and blurs them; elsewhere it opens in a pop-up window.
 */
@Composable
fun CommandPalette(
    state: CommandPaletteState,
    items: (query: String) -> List<PaletteItem>,
    modifier: Modifier = Modifier,
    filters: List<String> = emptyList(),
    hint: String = stringResource(R.string.dk_search_hint),
) {
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = state.isOpen
    if (!visible.currentState && !visible.targetState) return
    val compact = currentWidthClass() == WidthClass.Compact
    val content: @Composable () -> Unit = { PaletteFrame(state, visible, compact, items, filters, hint, modifier) }
    val host = LocalShellOverlayHost.current
    if (host != null) {
        OverlayLayer(host, content)
        ReturnFocusOnClose(host, state.isOpen)
    } else {
        Popup(onDismissRequest = { state.close() }, properties = PopupProperties(focusable = true)) { content() }
    }
    BackHandler(enabled = state.isOpen) { state.close() }
}

@Composable
private fun PaletteFrame(
    state: CommandPaletteState,
    visible: MutableTransitionState<Boolean>,
    compact: Boolean,
    items: (String) -> List<PaletteItem>,
    filters: List<String>,
    hint: String,
    modifier: Modifier,
) {
    val colors = DeskTheme.colors
    val tier = LocalMotionTier.current
    val bottomInset = LocalShellOverlayHost.current?.systemBottomPx ?: { 0 }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(DeskMotion.SHORT_MS)), exit = fadeOut(tween(DeskMotion.SHORT_MS))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.scrim)
                    .pointerInput(state) { detectTapGestures { state.close() } },
            )
        }
        val enter = when {
            tier == MotionTier.Reduced -> fadeIn(tween(0))
            compact -> fadeIn(tween(DeskMotion.MEDIUM_MS)) + slideInVertically(DeskMotion.gesture()) { it / 8 }
            else -> fadeIn(tween(DeskMotion.MEDIUM_MS)) +
                scaleIn(tween(DeskMotion.MEDIUM_MS, easing = DeskMotion.EmphasizedDecelerate), initialScale = 0.97f)
        }
        val exit = when {
            tier == MotionTier.Reduced -> fadeOut(tween(0))
            compact -> fadeOut(tween(DeskMotion.SHORT_MS)) + slideOutVertically(tween(DeskMotion.SHORT_MS)) { it / 12 }
            else -> fadeOut(tween(DeskMotion.SHORT_MS)) + scaleOut(tween(DeskMotion.SHORT_MS), targetScale = 0.98f)
        }
        AnimatedVisibility(
            visible,
            modifier = if (compact) Modifier.fillMaxSize().bottomInsetPadding(bottomInset) else Modifier.align(Alignment.TopCenter),
            enter = enter,
            exit = exit,
        ) {
            PalettePanel(state, compact, items, filters, hint, modifier)
        }
    }
}

@Composable
private fun PalettePanel(
    state: CommandPaletteState,
    compact: Boolean,
    items: (String) -> List<PaletteItem>,
    filters: List<String>,
    hint: String,
    modifier: Modifier,
) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val touch = DeskTheme.inputMode == InputMode.Touch
    val shape = RoundedCornerShape(if (compact) 0.dp else 12.dp)
    val bestHeading = stringResource(R.string.dk_palette_best)
    val allLabel = stringResource(R.string.dk_palette_all)
    val label = stringResource(R.string.dk_palette)
    val currentItems by rememberUpdatedState(items)
    val rows = remember(state.query, state.filter) {
        PaletteSearch.rows(currentItems(state.query), state.query, state.filter, bestHeading)
    }
    val results = rows.filterIsInstance<PaletteRow.Result>()
    var selected by remember(rows) { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // keeps the chosen row in view while the keyboard moves through the list
    LaunchedEffect(selected, rows) {
        val target = results.getOrNull(selected) ?: return@LaunchedEffect
        val index = rows.indexOf(target)
        val info = listState.layoutInfo
        val shown = info.visibleItemsInfo
        if (shown.none { it.index == index && it.offset >= info.viewportStartOffset && it.offset + it.size <= info.viewportEndOffset }) {
            listState.animateScrollToItem((index - 1).coerceAtLeast(0))
        }
    }
    val run = { newWindow: Boolean ->
        val item = results.getOrNull(selected)
        if (item != null) {
            state.close()
            if (newWindow && item.item.onOpenInNewWindow != null) item.item.onOpenInNewWindow.invoke() else item.item.onSelect()
        }
    }
    val chips = if (filters.isEmpty()) emptyList() else listOf<String?>(null) + filters
    Column(
        modifier
            .then(if (compact) Modifier.fillMaxSize() else Modifier.padding(top = 48.dp).widthIn(max = 680.dp).fillMaxWidth(0.9f))
            // on a phone the palette is the whole window: a plain page, nothing shows through
            .then(
                if (compact) Modifier.background(colors.background)
                else Modifier.glass(DeskGlass.raised(colors, GlassThickness.Regular), shape).border(1.dp, colors.borderStrong, shape),
            )
            .semantics { paneTitle = label }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> { if (results.isNotEmpty()) selected = (selected + 1) % results.size; true }
                    Key.DirectionUp -> { if (results.isNotEmpty()) selected = (selected - 1 + results.size) % results.size; true }
                    Key.Enter, Key.NumPadEnter -> { run(event.isCtrlPressed || event.isMetaPressed); true }
                    Key.Escape -> { state.close(); true }
                    // the focus stays in the field: Tab goes through the filters, if any
                    Key.Tab -> {
                        if (chips.isNotEmpty()) {
                            val at = chips.indexOf(state.filter)
                            state.filter = chips[(at + if (event.isShiftPressed) chips.size - 1 else 1) % chips.size]
                        }
                        true
                    }
                    else -> false
                }
            },
    ) {
        // the field
        Row(
            Modifier.fillMaxWidth().heightIn(min = if (touch) 56.dp else 52.dp).padding(horizontal = if (compact) 4.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (compact) {
                DeskIconButton(DeskIcons.ArrowLeft, stringResource(R.string.dk_back), onClick = { state.close() }, size = 48.dp)
            } else {
                Icon(DeskIcons.Search, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(dimens.icon))
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                val style = DeskTheme.type.title.copy(fontWeight = FontWeight.Normal, fontSize = if (compact) DeskTheme.type.body.fontSize else 17.sp)
                if (state.query.isEmpty()) Text(hint, style = style, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // the typed text with the cursor; a query set from outside (open("…"), clear) puts the cursor at its end
                var field by remember { mutableStateOf(TextFieldValue(state.query, TextRange(state.query.length))) }
                val shown = if (field.text == state.query) field else TextFieldValue(state.query, TextRange(state.query.length))
                BasicTextField(
                    value = shown,
                    onValueChange = {
                        field = it
                        state.query = it.text
                    },
                    singleLine = true,
                    textStyle = style.copy(color = colors.text),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { run(false) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (state.query.isNotEmpty() && compact) {
                DeskIconButton(DeskIcons.Close, stringResource(R.string.dk_clear), onClick = { state.query = "" })
            }
            if (showShortcutHints()) KeyCap(stringResource(R.string.dk_key_esc))
        }
        HorizontalRule()
        if (chips.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                chips.forEach { chip ->
                    DeskChip(chip ?: allLabel, selected = state.filter == chip, onClick = { state.filter = chip })
                }
            }
        }
        LazyColumn(
            state = listState,
            modifier = (if (compact) Modifier.weight(1f) else Modifier.heightIn(max = 440.dp)).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 6.dp, end = 6.dp, bottom = 8.dp),
        ) {
            itemsIndexed(rows) { _, row ->
                when (row) {
                    is PaletteRow.Heading -> Text(row.title, style = DeskTheme.type.labelSmall, color = colors.textTertiary,
                        modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 4.dp))
                    is PaletteRow.Result -> {
                        val index = results.indexOf(row)
                        PaletteResultRow(row, chosen = index == selected, onHover = { selected = index }, onClick = {
                            selected = index
                            run(false)
                        })
                    }
                }
            }
        }
        if (rows.isEmpty() && state.query.isNotBlank()) {
            Text(stringResource(R.string.dk_palette_none), style = DeskTheme.type.body, color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp))
        }
    }
}

@Composable
private fun PaletteResultRow(row: PaletteRow.Result, chosen: Boolean, onHover: () -> Unit, onClick: () -> Unit) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val touch = DeskTheme.inputMode == InputMode.Touch
    // keys and shortcuts only where a keyboard or a mouse can use them
    val keys = showShortcutHints()
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val latestHover by rememberUpdatedState(onHover)
    LaunchedEffect(state.hovered) { if (state.hovered) latestHover() }
    val item = row.item
    val shape = RoundedCornerShape(dimens.radiusControl)
    val textColor = if (chosen) colors.onSelection else colors.text
    val title = remember(item.title, row.match, chosen, colors) {
        highlight(item.title, row.match.ranges, SpanStyle(color = if (chosen) colors.onSelection else colors.accentText, fontWeight = FontWeight.SemiBold,
            textDecoration = if (chosen) TextDecoration.Underline else null))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (touch) 52.dp else 44.dp)
            .background(if (chosen) colors.selection else if (state.pressed) colors.pressed else androidx.compose.ui.graphics.Color.Transparent, shape)
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interaction)
            // the keyboard stays in the field and chooses with the arrows: rows never take the focus
            .focusProperties { canFocus = false }
            .deskClickable(interaction, onClick, role = Role.Button)
            .semantics { selected = chosen }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            item.leading != null -> item.leading.invoke()
            item.icon != null -> Icon(item.icon, contentDescription = null, tint = if (chosen) colors.onSelection else colors.textSecondary,
                modifier = Modifier.size(dimens.icon))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = DeskTheme.type.body.copy(fontWeight = FontWeight.Medium), color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.subtitle != null) {
                Text(item.subtitle, style = DeskTheme.type.labelSmall, color = if (chosen) colors.onSelection else colors.textTertiary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (item.hint != null) Text(item.hint, style = DeskTheme.type.labelSmall, color = if (chosen) colors.onSelection else colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (chosen && keys && item.actionLabel != null) {
            KeyCap("Enter")
            Text(item.actionLabel, style = DeskTheme.type.mono, color = colors.onSelection, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (chosen && keys && item.onOpenInNewWindow != null) {
            KeyCap("Ctrl+Enter")
            Text(stringResource(R.string.dk_palette_new_window), style = DeskTheme.type.mono, color = colors.onSelection, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (item.shortcut != null && keys) KeyCap(item.shortcut)
        Spacer(Modifier.width(0.dp))
    }
}

/** [text] with [ranges] in [style]. */
internal fun highlight(text: String, ranges: List<IntRange>, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    append(text)
    for (range in ranges) {
        val start = range.first.coerceIn(0, text.length)
        val end = (range.last + 1).coerceIn(start, text.length)
        if (end > start) addStyle(style, start, end)
    }
}
