package desktopkit.editor

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import desktopkit.R
import desktopkit.appResources
import desktopkit.components.EndOfAnchorPositionProvider
import desktopkit.components.MenuEntry
import desktopkit.components.MenuPopup
import desktopkit.components.ScrollbarAdapter
import desktopkit.components.TooltipBubble
import desktopkit.components.TooltipPlacement
import desktopkit.components.TooltipPopup
import desktopkit.components.deskClickable
import desktopkit.components.focusMemory
import desktopkit.components.rememberMenuState
import desktopkit.components.rememberTooltipState
import desktopkit.components.scrollbar
import desktopkit.components.tooltipTrigger
import desktopkit.input.showShortcutHints
import desktopkit.shell.LocalShellOverlayHost
import desktopkit.shell.TextFields
import desktopkit.theme.DeskTheme
import desktopkit.theme.SyntaxRole
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * A source editor: a multi-line editor for code or a script language that fills the space it is
 * given and scrolls its text, with syntax colours, a gutter of line numbers and markers, marked
 * ranges, links and a context menu. The language is the app's: a [tokenizer] gives the colours,
 * [markers] the compiler's findings, [links] what leads elsewhere.
 *
 * The document is [state], owned by the app, one for each open document; giving the editor another
 * state shows that document as it was left (text, cursor, scroll position, undo history).
 *
 * Typing is the platform's own (input method, autocorrect and swipe typing when [autoCorrect] asks
 * for them, selection handles, the magnifier, the clipboard, TalkBack), and its cost does not grow
 * with the document. On top of it:
 * - Tab indents by [indent] (the lines of a selection that spans several), Shift+Tab moves them out,
 *   Enter keeps the line's indentation. Since Tab types, the keyboard leaves the editor with Esc and
 *   then Tab (or Shift+Tab), as in desktop editors; Esc twice gives up the focus, the platform's way;
 * - Ctrl+Z, Ctrl+Y and Ctrl+Shift+Z go through [state]'s history, Ctrl+A, C, X and V do what they do
 *   everywhere, pasted text comes without its styles;
 * - a right click, a two-finger tap on a touchpad, the Menu key or Shift+F10 open the kit's menu with
 *   Undo, Redo, Cut, Copy, Paste and Select all, followed by [contextMenu]'s entries for the offset
 *   that was clicked. A long press with a finger selects a word and shows the platform's selection
 *   toolbar, with the same entries of the app after its own;
 * - with Ctrl or Alt held, a link under the mouse is underlined and the pointer becomes a hand; a
 *   click calls [onLinkClick] with the offset;
 * - with a [zoom], Ctrl with the wheel, a pinch, and Ctrl with +, − and 0 change the text's size.
 *
 * [onPreviewKeyEvent] sees a hardware key before the editor does and takes it by returning true.
 *
 * With a [search] the editor searches its document and marks the matches while the search is open (see
 * [CodeSearchBar]); Ctrl+F opens it with the selection as the query, Ctrl+H with the replace row, F3 and
 * Shift+F3 step through the matches, Esc closes it.
 *
 * [softWrap] wraps long lines (the default; a wrapped line has its number on its first row); without
 * it the text scrolls sideways. [proportional] sets the text in the theme's text face instead of the
 * monospace one, for languages that are mostly prose. [contentPadding] is room at the top and the
 * bottom the text scrolls through: on a phone pass `rememberShellContentPadding()`, and the text runs
 * under the glass bars as a list does while its first and last lines can rest clear of them. Inside a
 * [desktopkit.shell.DesktopShell] a tap in the editor or a scroll of its text leaves the on-screen
 * keyboard open, and the line with the cursor stays in sight above it.
 *
 * [label] is what TalkBack calls the editor ("Editor" when null); [placeholder] shows in an empty
 * document.
 */
@Composable
fun CodeEditor(
    state: CodeEditorState,
    modifier: Modifier = Modifier,
    tokenizer: LineTokenizer? = null,
    markers: List<LineMarker> = emptyList(),
    onMarkerClick: ((LineMarker) -> Unit)? = null,
    decorations: List<CodeDecoration> = emptyList(),
    links: CodeLinks? = null,
    onLinkClick: ((offset: Int) -> Unit)? = null,
    contextMenu: ((offset: Int) -> List<MenuEntry>)? = null,
    zoom: CodeEditorZoom? = null,
    softWrap: Boolean = true,
    readOnly: Boolean = false,
    lineNumbers: Boolean = true,
    proportional: Boolean = false,
    autoCorrect: Boolean = false,
    capitalizeSentences: Boolean = false,
    indent: CodeIndent = CodeIndent.Tab,
    placeholder: String = "",
    contentPadding: PaddingValues = PaddingValues(0.dp),
    highlightCurrentLine: Boolean = true,
    matchBrackets: Boolean = true,
    tintMarkedLines: Boolean = true,
    label: String? = null,
    onPreviewKeyEvent: ((KeyEvent) -> Boolean)? = null,
    search: CodeSearchState? = null,
) {
    val colors = DeskTheme.colors
    val syntax = DeskTheme.syntax
    val dimens = DeskTheme.dimens
    val type = DeskTheme.type
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val selectionColors = LocalTextSelectionColors.current
    val fontResolver = LocalFontFamilyResolver.current
    val focusManager = LocalFocusManager.current
    val touch = dimens.minTarget >= 48.dp
    val scale = zoom?.scale ?: 1f

    val codeStyle = type.code
    val textTypeface = fontResolver.resolve(if (proportional) type.body.fontFamily else codeStyle.fontFamily,
        codeStyle.fontWeight ?: FontWeight.Normal, codeStyle.fontStyle ?: FontStyle.Normal).value as? Typeface ?: Typeface.MONOSPACE
    val numberTypeface = fontResolver.resolve(type.mono.fontFamily, FontWeight.Normal).value as? Typeface ?: Typeface.MONOSPACE
    val markerSize = if (touch) 16.dp else 12.dp
    val metrics = remember(density, codeStyle, scale, touch, textTypeface, numberTypeface, dimens) {
        with(density) {
            val textSize = codeStyle.fontSize.toPx() * scale
            EditorMetrics(
                textSize = textSize,
                lineHeight = (codeStyle.lineHeight.toPx() * scale).roundToInt(),
                typeface = textTypeface,
                numberTypeface = numberTypeface,
                numberSize = textSize * LINE_NUMBER_SCALE,
                markerSize = markerSize.roundToPx(),
                gutterStart = dimens.gapS.roundToPx(),
                gutterGap = dimens.gapXs.roundToPx(),
                textStart = dimens.gapM.roundToPx(),
                textEnd = (if (touch) dimens.gapL else dimens.gapM).roundToPx(),
                edge = dimens.gapS.roundToPx(),
                cursorWidth = 2.dp.roundToPx().coerceAtLeast(1),
                stroke = 1.dp.toPx().coerceAtLeast(1f),
                corner = 2.dp.toPx(),
            )
        }
    }
    val look = remember(colors, syntax, selectionColors) {
        EditorLook(
            text = syntax.plain.toArgb(),
            hint = colors.textTertiary.toArgb(),
            currentLine = syntax.currentLine.toArgb(),
            lineNumber = syntax.lineNumber.toArgb(),
            lineNumberCurrent = syntax.plain.toArgb(),
            selection = selectionColors.backgroundColor.toArgb(),
            cursor = colors.accent.toArgb(),
            bracket = colors.controlBorder.toArgb(),
            tones = IntArray(DecorationTone.entries.size) { index ->
                when (DecorationTone.entries[index]) {
                    DecorationTone.Accent -> colors.accentText
                    DecorationTone.Danger -> colors.danger
                    DecorationTone.Warning -> colors.warning
                    DecorationTone.Success -> colors.success
                    DecorationTone.Neutral -> colors.textTertiary
                }.toArgb()
            },
            syntaxColors = IntArray(SyntaxRole.entries.size) { syntax[SyntaxRole.entries[it]].color.toArgb() },
            syntaxBold = BooleanArray(SyntaxRole.entries.size) { syntax[SyntaxRole.entries[it]].bold },
            syntaxItalic = BooleanArray(SyntaxRole.entries.size) { syntax[SyntaxRole.entries[it]].italic },
        )
    }
    val topPadding = with(density) { contentPadding.calculateTopPadding().roundToPx() }
    val bottomPadding = with(density) { contentPadding.calculateBottomPadding().roundToPx() }

    val editor = remember { EditorHandle() }
    val menu = rememberMenuState()
    var menuOffset by remember { mutableIntStateOf(0) }
    var shownMarkers by remember { mutableStateOf(emptyList<ShownMarker>()) }
    val currentMarkers by rememberUpdatedState(markers)
    val currentOnMarkerClick by rememberUpdatedState(onMarkerClick)
    val currentOnLinkClick by rememberUpdatedState(onLinkClick)
    val currentContextMenu by rememberUpdatedState(contextMenu)
    val currentZoom by rememberUpdatedState(zoom)
    val currentKeyHook by rememberUpdatedState(onPreviewKeyEvent)
    val currentSearch by rememberUpdatedState(search)
    val editorLabel = label ?: stringResource(R.string.dk_editor)
    val zoomLabels = if (zoom == null) null else stringResource(R.string.dk_editor_larger) to stringResource(R.string.dk_editor_smaller)

    // the search runs on the document shown, its matches marked in the text
    if (search != null) {
        DisposableEffect(search, state) {
            search.attach(state)
            onDispose { search.detach(state) }
        }
        LaunchedEffect(search, state) { search.run(state) }
    }

    // known to the shell like the kit's text fields: a tap in the editor or a scroll that starts in it keeps the keyboard
    val shell = LocalShellOverlayHost.current
    val field = remember { TextFields.Field() }
    if (shell != null) {
        DisposableEffect(shell, field) {
            shell.textFields.add(field)
            onDispose { shell.textFields.remove(field) }
        }
    }

    Box(
        modifier
            .background(syntax.background)
            .clipToBounds()
            .onGloballyPositioned { field.coordinates = it }
            .scrollbar(editor.scrollbar),
    ) {
        AndroidView(
            factory = { context ->
                CodeEditorView(context).also { view ->
                    editor.view = view
                    view.onMarkersShown = { shownMarkers = it }
                    view.onViewportChanged = { editor.viewportChanged() }
                    view.onFocusChange = { focused -> field.focused = focused }
                    view.onLeave = { forward -> focusManager.moveFocus(if (forward) FocusDirection.Next else FocusDirection.Previous) }
                    view.onContextMenu = { x, y, offset ->
                        menuOffset = offset
                        menu.open(IntOffset(x, y))
                    }
                    view.onLink = { offset -> currentOnLinkClick?.invoke(offset) }
                    view.onZoomStep = { step ->
                        when (step) {
                            1 -> currentZoom?.zoomIn()
                            -1 -> currentZoom?.zoomOut()
                            else -> currentZoom?.reset()
                        }
                    }
                    view.onPinch = { factor, done ->
                        val target = currentZoom
                        if (target != null) {
                            if (editor.pinchStart.isNaN()) editor.pinchStart = target.scale
                            target.follow(editor.pinchStart, factor)
                            if (done) {
                                target.commit()
                                editor.pinchStart = Float.NaN
                            }
                        }
                    }
                    view.keyInterceptor = { event -> currentSearch?.let { searchKey(it, event) } == true }
                    view.selectionActions = {
                        currentContextMenu?.invoke(view.selectionEnd.coerceAtLeast(0)).orEmpty()
                            .filterIsInstance<MenuEntry.Item>().filter { it.enabled }.map { SelectionAction(it.label, it.onClick) }
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .focusMemory()
                .onPreviewKeyEvent { event -> currentKeyHook?.invoke(event) == true },
            update = { view ->
                view.layoutDirection = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) android.view.View.LAYOUT_DIRECTION_RTL else android.view.View.LAYOUT_DIRECTION_LTR
                view.applyMetrics(metrics)
                view.applyLook(look)
                view.applyInputType(autoCorrect, capitalizeSentences)
                view.softWrap = softWrap
                view.readOnly = readOnly
                view.showLineNumbers = lineNumbers
                view.indent = indent
                view.highlightCurrentLine = highlightCurrentLine
                view.matchBrackets = matchBrackets
                view.tintMarkedLines = tintMarkedLines
                view.accessibilityLabel = editorLabel
                view.links = links
                view.applyPlaceholder(placeholder)
                view.applyZoomActions(zoomLabels)
                view.applyContentPadding(topPadding, bottomPadding)
                if (state.tokens.tokenizer !== tokenizer) {
                    state.tokens.tokenizer = tokenizer
                    if (view.document === state) view.tokenizerChanged()
                }
                view.bind(state)
                view.applyMarkers(markers)
                view.applyDecorations(decorations)
                view.applySearch(if (search != null && search.isOpen) search.matches else CodeSearchState.NO_MATCHES, search?.currentMatch ?: -1)
            },
            onRelease = { view ->
                view.release()
                editor.view = null
            },
        )

        // the markers in view, as elements of their own over the gutter: TalkBack reads them, the mouse rests on
        // them for the message, a finger taps them
        val rowTarget = dimens.minTarget
        shownMarkers.forEach { shown ->
            key(shown.line) {
                val onLine = currentMarkers.filter { it.line == shown.line }
                if (onLine.isNotEmpty()) {
                    MarkerTarget(
                        markers = onLine,
                        top = shown.top,
                        bottom = shown.bottom,
                        width = dimens.gapS + markerSize + dimens.gapXs,
                        minHeight = rowTarget,
                        onClick = { currentOnMarkerClick?.invoke(onLine.first()) },
                    )
                }
            }
        }

        // a new pop-up for every opening, as ContextMenuArea does
        key(menu.generation) {
            MenuPopup(
                visible = menu.isOpen,
                onDismiss = menu::close,
                entries = if (menu.isOpen) editMenu(editor, state, readOnly) + appEntries(contextMenu, menuOffset) else emptyList(),
                offset = menu.offset ?: IntOffset.Zero,
            )
        }
    }
}

/**
 * The editor's keys for its [search]: Ctrl+F opens the bar (the selection becomes the query), Ctrl+H opens it
 * with the replace row, F3 and Shift+F3 step to the next and the previous match, Esc closes an open bar.
 */
private fun searchKey(search: CodeSearchState, event: android.view.KeyEvent): Boolean {
    val ctrl = event.isCtrlPressed || event.isMetaPressed
    val plain = !ctrl && !event.isAltPressed && !event.isShiftPressed
    return when {
        ctrl && !event.isAltPressed && event.keyCode == android.view.KeyEvent.KEYCODE_F -> {
            search.open()
            true
        }
        ctrl && !event.isAltPressed && event.keyCode == android.view.KeyEvent.KEYCODE_H -> {
            search.open(replace = true)
            true
        }
        event.keyCode == android.view.KeyEvent.KEYCODE_F3 && !ctrl && !event.isAltPressed -> {
            if (!search.isOpen) search.open()
            if (event.isShiftPressed) search.previous() else search.next()
            true
        }
        event.keyCode == android.view.KeyEvent.KEYCODE_ESCAPE && plain && search.isOpen -> {
            search.close()
            true
        }
        else -> false
    }
}

/** The text's size against the line numbers'. */
private const val LINE_NUMBER_SCALE = 0.85f

/** How long a marker's message stays after a tap. */
private const val MARKER_MESSAGE_MS = 4000L

/** What [CodeEditor] keeps of its view between compositions. */
@Stable
private class EditorHandle {
    var view: CodeEditorView? = null
    var pinchStart = Float.NaN

    /** Counts scrolls and layouts of the view, for what is drawn from them (the scrollbar). */
    private var viewport by mutableIntStateOf(0)

    fun viewportChanged() {
        viewport++
    }

    val scrollbar = object : ScrollbarAdapter {
        override val contentLength: Float
            get() {
                viewport
                return view?.scrollRangeY?.toFloat() ?: 0f
            }
        override val viewportLength: Float
            get() {
                viewport
                return view?.height?.toFloat() ?: 0f
            }
        override val offset: Float
            get() {
                viewport
                return view?.scrollOffsetY?.toFloat() ?: 0f
            }
        override val insetStart: Int
            get() {
                viewport
                return view?.contentTopInset ?: 0
            }
        override val insetEnd: Int
            get() {
                viewport
                return view?.contentBottomInset ?: 0
            }
        override val scrolling: Boolean
            get() {
                viewport
                return view?.isScrolling == true
            }

        override fun scrollTo(to: Float) {
            view?.scrollToFraction(to)
        }
    }
}

@Composable
private fun editMenu(editor: EditorHandle, state: CodeEditorState, readOnly: Boolean): List<MenuEntry> {
    val hints = showShortcutHints()
    val selected = !state.selection.collapsed
    fun run(id: Int): () -> Unit = { editor.view?.onTextContextMenuItem(id) }
    return listOf(
        MenuEntry.Item(stringResource(R.string.dk_undo), { state.undo() }, shortcut = "Ctrl+Z".takeIf { hints }, enabled = state.canUndo && !readOnly),
        MenuEntry.Item(stringResource(R.string.dk_redo), { state.redo() }, shortcut = "Ctrl+Y".takeIf { hints }, enabled = state.canRedo && !readOnly),
        MenuEntry.Separator,
        MenuEntry.Item(stringResource(R.string.dk_cut), run(android.R.id.cut), shortcut = "Ctrl+X".takeIf { hints }, enabled = selected && !readOnly),
        MenuEntry.Item(stringResource(R.string.dk_copy), run(android.R.id.copy), shortcut = "Ctrl+C".takeIf { hints }, enabled = selected),
        MenuEntry.Item(stringResource(R.string.dk_paste), run(android.R.id.paste), shortcut = "Ctrl+V".takeIf { hints }, enabled = !readOnly),
        MenuEntry.Separator,
        MenuEntry.Item(stringResource(R.string.dk_select_all), run(android.R.id.selectAll), shortcut = "Ctrl+A".takeIf { hints }),
    )
}

private fun appEntries(contextMenu: ((Int) -> List<MenuEntry>)?, offset: Int): List<MenuEntry> {
    val entries = contextMenu?.invoke(offset).orEmpty()
    return if (entries.isEmpty()) entries else listOf(MenuEntry.Separator) + entries
}

/**
 * A marker's place in the gutter as an element: it carries the marker's name for TalkBack, shows the
 * message beside it while the mouse rests on it or the keyboard focus is in it, and for a few seconds
 * after a tap or a click, which also tells the app.
 */
@Composable
private fun MarkerTarget(markers: List<LineMarker>, top: Int, bottom: Int, width: Dp, minHeight: Dp, onClick: () -> Unit) {
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val tooltip = rememberTooltipState()
    var pinned by remember { mutableStateOf(false) }
    val message = markers.joinToString("\n") { it.message }
    val resources = appResources()
    val description = markers.joinToString("\n") { marker ->
        resources.getString(
            when (marker.severity) {
                MarkerSeverity.Error -> R.string.dk_editor_marker_error
                MarkerSeverity.Warning -> R.string.dk_editor_marker_warning
                MarkerSeverity.Info -> R.string.dk_editor_marker_info
            },
            marker.line + 1, marker.message,
        )
    }
    LaunchedEffect(pinned) {
        if (pinned) {
            delay(MARKER_MESSAGE_MS)
            pinned = false
        }
    }
    val rowHeight = with(density) { (bottom - top).toDp() }
    val height = if (rowHeight < minHeight) minHeight else rowHeight
    // a row lower than a target: the target reaches evenly above and below it
    val y = top - with(density) { ((height - rowHeight) / 2).roundToPx() }
    Box(
        Modifier
            .offset { IntOffset(0, y) }
            .size(width.coerceAtLeast(minHeight), height)
            .tooltipTrigger(tooltip)
            .semantics { contentDescription = description }
            .deskClickable(interaction, onClick = {
                pinned = !pinned
                onClick()
            }, role = Role.Button),
    ) {
        TooltipPopup(tooltip, message, shortcut = null, placement = TooltipPlacement.End)
        if (pinned && !tooltip.visible) {
            Popup(popupPositionProvider = remember { EndOfAnchorPositionProvider() }, properties = PopupProperties(focusable = false)) {
                TooltipBubble(message, shortcut = null)
            }
        }
    }
}
