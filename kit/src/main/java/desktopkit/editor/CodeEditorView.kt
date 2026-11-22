package desktopkit.editor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.Layout
import android.text.Selection
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.TextWatcher
import android.text.method.ArrowKeyMovementMethod
import android.text.style.CharacterStyle
import android.text.style.MetricAffectingSpan
import android.text.style.ParagraphStyle
import android.text.style.SuggestionSpan
import android.text.style.UpdateAppearance
import android.util.SparseArray
import android.util.TypedValue
import android.view.ActionMode
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.OverScroller
import android.widget.TextView
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The colours of a [CodeEditorView], as ARGB values; made from the theme by [CodeEditor]. */
internal class EditorLook(
    val text: Int,
    val hint: Int,
    val currentLine: Int,
    val lineNumber: Int,
    val lineNumberCurrent: Int,
    val selection: Int,
    val cursor: Int,
    val bracket: Int,
    /** Indexed by [DecorationTone]. */
    val tones: IntArray,
    /** Indexed by [desktopkit.theme.SyntaxRole]. */
    val syntaxColors: IntArray,
    val syntaxBold: BooleanArray,
    val syntaxItalic: BooleanArray,
) {
    override fun equals(other: Any?): Boolean = other is EditorLook && text == other.text && hint == other.hint &&
        currentLine == other.currentLine && lineNumber == other.lineNumber && lineNumberCurrent == other.lineNumberCurrent &&
        selection == other.selection && cursor == other.cursor && bracket == other.bracket && tones.contentEquals(other.tones) &&
        syntaxColors.contentEquals(other.syntaxColors) && syntaxBold.contentEquals(other.syntaxBold) && syntaxItalic.contentEquals(other.syntaxItalic)

    override fun hashCode(): Int = (text * 31 + cursor) * 31 + syntaxColors.contentHashCode()
}

/** The sizes of a [CodeEditorView] in pixels; made from the theme, the density and the zoom by [CodeEditor]. */
internal data class EditorMetrics(
    val textSize: Float,
    val lineHeight: Int,
    val typeface: Typeface,
    /** Line numbers are set in the monospace face whatever the text's is, so their column keeps its width. */
    val numberTypeface: Typeface,
    val numberSize: Float,
    /** The side of a marker's icon in the gutter. */
    val markerSize: Int,
    /** From the editor's start edge to the marker. */
    val gutterStart: Int,
    /** Between the marker and the line numbers. */
    val gutterGap: Int,
    /** Between the gutter and the text. */
    val textStart: Int,
    val textEnd: Int,
    /** Room above the first and below the last line. */
    val edge: Int,
    val cursorWidth: Int,
    /** The stroke of an underline decoration and of the bracket frame. */
    val stroke: Float,
    val corner: Float,
)

/** A marker whose line is in view: [top] and [bottom] of the line's first row, in the view. */
internal data class ShownMarker(val line: Int, val top: Int, val bottom: Int)

/** One of the app's entries in the selection toolbar. */
internal class SelectionAction(val label: String, val run: () -> Unit)

/**
 * The view inside [CodeEditor]: the platform's own [EditText], for everything typing needs (the
 * input method, autocorrect and swipe typing, selection handles and the magnifier, the clipboard,
 * the hardware keyboard, TalkBack) and for its incremental layout: an edit lays out the edited
 * paragraph again, not the document, so typing costs the same in a long document as in a short one.
 *
 * Around it this class adds what an editor needs:
 * - the document lives in a [CodeEditorState]; the view's text mirrors it and reports every change;
 * - scrolling of its own (the platform's moves the cursor along and cannot go under the shell's
 *   glass bars): a finger drags and flings the text, nested scrolling tells the shell;
 * - the gutter, the current line, markers, decorations, search matches and matching brackets, drawn
 *   for the rows in view only;
 * - syntax colours as spans on the lines in and around the view, set in the same call that changes
 *   the text, so a frame never shows new text in old colours;
 * - Tab and Shift+Tab, Enter that keeps the indentation, undo and redo through the state's history.
 */
@SuppressLint("ViewConstructor", "AppCompatCustomView", "ClickableViewAccessibility")
internal class CodeEditorView(context: Context) : EditText(context), EditorHost {

    /** False while the superclass constructor runs: it calls methods overridden here before this class's fields exist. */
    private var ready = false

    var document: CodeEditorState? = null
        private set

    /** The whole text is being replaced: the watcher stays out of it. */
    private var loading = false

    /** An edit the state asked for, not the user's. */
    private var programmatic = false
    private var removedText = ""
    private var selectionBefore = 0L

    // ---- options, set by CodeEditor
    var softWrap = true
        set(value) {
            if (field == value) return
            // the lines wrap anew: the row at the top stays at the top
            saveAnchor()
            field = value
            setHorizontallyScrolling(!value)
            if (document != null && layout != null) restoreScroll = true
            scrollTo(0, scrollY)
        }
    var readOnly = false
        set(value) {
            if (field == value) return
            field = value
            showSoftInputOnFocus = !value
            isCursorVisible = true
        }
    var showLineNumbers = true
        set(value) {
            if (field == value) return
            field = value
            applyPadding()
        }
    var indent: CodeIndent = CodeIndent.Tab
    var highlightCurrentLine = true
        set(value) {
            field = value
            invalidate()
        }
    var matchBrackets = true
        set(value) {
            field = value
            updateBrackets()
        }
    var tintMarkedLines = true
        set(value) {
            field = value
            invalidate()
        }
    var accessibilityLabel: String? = null

    // ---- what the app marks
    private var markerList: List<LineMarker> = emptyList()
    private var markers = SparseArray<LineMarker>()
    private var decorations: List<CodeDecoration> = emptyList()
    private var searchRanges = EMPTY_INTS
    private var searchCurrent = -1
    var links: CodeLinks? = null

    // ---- what CodeEditor wants to hear
    var onContextMenu: ((x: Int, y: Int, offset: Int) -> Unit)? = null
    var onLink: ((offset: Int) -> Unit)? = null
    var onMarkersShown: ((List<ShownMarker>) -> Unit)? = null

    /** Sees a key before the editor does (the completion list's arrows, Enter, Tab and Esc). */
    var keyInterceptor: ((KeyEvent) -> Boolean)? = null

    /** The text scrolled or was laid out again: whatever floats at the cursor moves. */
    var onViewportChanged: (() -> Unit)? = null

    /** The user changed the text ([typed]) or the cursor moved. */
    var onCursorActivity: ((typed: Boolean) -> Unit)? = null

    /** Ctrl with the wheel or with + and −: 1 larger, -1 smaller, 0 back to the default. */
    var onZoomStep: ((Int) -> Unit)? = null

    /** Two fingers: the factor since the pinch began, and whether the fingers have let go. */
    var onPinch: ((factor: Float, done: Boolean) -> Unit)? = null
    var selectionActions: (() -> List<SelectionAction>)? = null
    var onFocusChange: ((Boolean) -> Unit)? = null

    /** Esc and then Tab: the keyboard focus is to move on to the next element (forward) or the one before. */
    var onLeave: ((forward: Boolean) -> Unit)? = null

    // ---- look
    private var look: EditorLook? = null
    private var metrics: EditorMetrics? = null
    private val syntaxLook = SyntaxLook()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val numberPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
    private val path = Path()
    private val rect = RectF()
    private var gutterWidth = 0
    private var digits = 2
    private var digitWidth = 0f

    /** The room the app asked to keep clear at the top and the bottom (the shell's glass bars): text scrolls under it. */
    private var contentTop = 0
    private var contentBottom = 0

    // ---- scrolling
    private val scroller = OverScroller(context)
    private val nested = NestedScrollingChildHelper(this).apply { isNestedScrollingEnabled = true }
    private val viewConfiguration = ViewConfiguration.get(context)
    private var velocityTracker: VelocityTracker? = null
    private var dragging = false
    private var swallowGesture = false
    private var longPressed = false
    private var secondTap = false
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var lastUpTime = 0L
    private var lastUpX = 0f
    private var lastUpY = 0f
    private var flingLastY = 0
    private var flinging = false
    private var scaling = false
    private var wheelGathered = 0f
    private val scrollConsumed = IntArray(2)
    private val scrollOffset = IntArray(2)

    /** The widest line seen so far without wrapping: how far the text scrolls sideways. */
    private var widestLine = 0f

    /** The cursor moved or the text changed: the next layout pass brings the cursor into view. A scroll alone never does. */
    private var followCursor = false
    private var restoreScroll = false
    private var pendingReveal: Long = NO_REVEAL

    // ---- state of the pointer and the keys
    private var escapeArmed = false
    private var swallowedKey = KeyEvent.KEYCODE_UNKNOWN
    private var pressedLink = -1
    private var hoverX = Float.NaN
    private var hoverY = Float.NaN
    private var hoverLinkStart = -1
    private var hoverLinkEnd = -1
    private var bracketA = -1
    private var bracketB = -1
    private var shownMarkers: List<ShownMarker> = emptyList()

    // ---- syntax spans on the lines around the view
    private class AppliedLine(val spans: Array<RoleSpan>, var pass: Int)
    private val applied = IdentityHashMap<IntArray, AppliedLine>()
    private var syntaxPass = 0
    private var syntaxFirst = 0
    private var syntaxLast = -1

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var factor = 1f
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            if (onPinch == null) return false
            factor = 1f
            scaling = true
            endDrag(fling = false)
            cancelSuperGesture()
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            factor *= detector.scaleFactor
            onPinch?.invoke(factor, false)
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            onPinch?.invoke(factor, true)
        }
    })

    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            if (loading) return
            removedText = if (count == 0) "" else s.subSequence(start, start + count).toString()
            selectionBefore = CodeEditorState.pack(Selection.getSelectionStart(s).coerceAtLeast(0), Selection.getSelectionEnd(s).coerceAtLeast(0))
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            if (loading) return
            document?.hostEdited(start, removedText, s.subSequence(start, start + count), typed = !programmatic, selectionBefore = selectionBefore)
            removedText = ""
        }

        override fun afterTextChanged(s: Editable) {
            if (loading) return
            val state = document ?: return
            // the view's text is what the user sees: should the two ever differ, the document follows it
            if (s.length != state.lines.length) state.resyncFrom(s)
            followSoon()
            updateDigits()
            syncSyntax()
            updateBrackets()
            publishViewport()
            onCursorActivity?.invoke(!programmatic)
        }
    }

    private val inputFilter = InputFilter { source, start, end, dest, dstart, _ ->
        if (programmatic || loading || readOnly) null else filtered(source, start, end, dest, dstart)
    }

    init {
        background = null
        setPadding(0, 0, 0, 0)
        gravity = Gravity.TOP or Gravity.START
        includeFontPadding = false
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        // a document does not belong in the saved instance state, and is nothing to autofill
        isSaveEnabled = false
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_ACTION_NONE
        applyInputType(autoCorrect = false, capitalize = false)
        movementMethod = EditorMovement
        filters = arrayOf(ReadOnlyGate(), inputFilter)
        addTextChangedListener(watcher)
        val toolbar = ToolbarCallback()
        customSelectionActionModeCallback = toolbar
        customInsertionActionModeCallback = toolbar
        ready = true
    }

    /** Refuses the user's edits while the editor is read-only; first in line, so nothing else sees them. */
    private inner class ReadOnlyGate : InputFilter {
        override fun filter(source: CharSequence, start: Int, end: Int, dest: Spanned, dstart: Int, dend: Int): CharSequence? {
            return if (readOnly && !programmatic && !loading) dest.subSequence(dstart, dend) else null
        }
    }

    // ------------------------------------------------------------------ the document

    /** Shows [state]'s document; the one shown before keeps its scroll position for its next time. */
    fun bind(state: CodeEditorState?) {
        if (state === document) return
        release()
        document = state
        if (state == null) return
        // an editor still showing this document lets go of it: one editor at a time
        (state.host as? CodeEditorView)?.release()
        state.host = this
        load(state)
        Selection.setSelection(editableText, state.selection.start.coerceIn(0, length()), state.selection.end.coerceIn(0, length()))
        restoreScroll = true
        state.pendingReveal?.let { pendingReveal = CodeEditorState.pack(it.start, it.end) }
        state.pendingReveal = null
        if (state.pendingFocus) {
            state.pendingFocus = false
            focus()
        }
        requestLayout()
    }

    /** Lets go of the document: it remembers where the view was. */
    fun release() {
        val state = document ?: return
        stopFling()
        removeCallbacks(tokenizeMore)
        if (state.host === this) {
            saveAnchor()
            state.host = null
        }
        document = null
    }

    private fun load(state: CodeEditorState) {
        loading = true
        try {
            applied.clear()
            syntaxLast = -1
            widestLine = 0f
            setText(state.content, BufferType.EDITABLE)
        } finally {
            loading = false
        }
        updateDigits()
        bracketA = -1
        bracketB = -1
    }

    override fun replace(start: Int, end: Int, text: String) {
        finishComposition()
        programmatic = true
        try {
            editableText.replace(start, end, text)
        } finally {
            programmatic = false
        }
    }

    override fun select(start: Int, end: Int) {
        Selection.setSelection(editableText, start.coerceIn(0, length()), end.coerceIn(0, length()))
    }

    override fun showText() {
        val state = document ?: return
        finishComposition()
        load(state)
        restoreScroll = true
        requestLayout()
    }

    override fun scrollToAnchor() {
        restoreScroll = true
        stopFling()
        val state = document
        if (state != null && layout != null && width > 0 && !isLayoutRequested) applyRestore(state) else requestLayout()
    }

    override fun reveal(start: Int, end: Int) {
        pendingReveal = CodeEditorState.pack(start, end)
        if (layout != null && width > 0 && !isLayoutRequested) applyPendingReveal() else requestLayout()
    }

    override fun focus() {
        if (!isAttachedToWindow) {
            post { focus() }
            return
        }
        requestFocus()
        if (!readOnly) windowInsetsController?.show(WindowInsets.Type.ime())
    }

    override fun batch(begin: Boolean) {
        if (begin) {
            finishComposition()
            beginBatchEdit()
        } else {
            endBatchEdit()
        }
    }

    /**
     * Ends the word the on-screen keyboard is composing, as it stands, before the text changes under
     * it: the keyboard starts afresh instead of writing its old word over the new text.
     */
    private fun finishComposition() {
        val editable = editableText ?: return
        if (BaseInputConnection.getComposingSpanStart(editable) < 0) return
        BaseInputConnection.removeComposingSpans(editable)
        context.getSystemService(InputMethodManager::class.java)?.restartInput(this)
    }

    // ------------------------------------------------------------------ options and look

    /** The keyboard's help for prose ([autoCorrect], [capitalize] sentences) or none of it, for code. */
    fun applyInputType(autoCorrect: Boolean, capitalize: Boolean) {
        var type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        type = type or if (autoCorrect) InputType.TYPE_TEXT_FLAG_AUTO_CORRECT else InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        if (capitalize) type = type or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        if (inputType == type) return
        inputType = type
        // setting the input type puts the wrapping back on
        setHorizontallyScrolling(!softWrap)
    }

    fun applyLook(next: EditorLook) {
        if (next == look) return
        look = next
        setTextColor(next.text)
        setHintTextColor(next.hint)
        highlightColor = next.selection
        syntaxLook.set(next)
        applyCursor()
        for (handle in arrayOf(textSelectHandle, textSelectHandleLeft, textSelectHandleRight)) handle?.mutate()?.setTint(next.cursor)
        // the spans read their colours when drawn, but the platform keeps drawn rows: set again, they are drawn again
        resetSyntax()
        invalidate()
    }

    fun applyMetrics(next: EditorMetrics) {
        if (next == metrics) return
        // a new text size keeps the row that was at the top there
        val state = document
        if (metrics != null && state != null && layout != null) {
            saveAnchor()
            state.setScrollAnchor(state.scrollAnchor, 0)
            restoreScroll = true
        }
        metrics = next
        typeface = next.typeface
        setTextSize(TypedValue.COMPLEX_UNIT_PX, next.textSize)
        setLineHeight(next.lineHeight)
        numberPaint.typeface = next.numberTypeface
        numberPaint.textSize = next.numberSize
        digitWidth = numberPaint.measureText("0")
        strokePaint.strokeWidth = next.stroke
        widestLine = 0f
        applyCursor()
        applyPadding()
    }

    private fun applyCursor() {
        val look = look ?: return
        val metrics = metrics ?: return
        textCursorDrawable = GradientDrawable().apply {
            setColor(look.cursor)
            setSize(metrics.cursorWidth, 0)
        }
    }

    fun applyContentPadding(top: Int, bottom: Int) {
        if (top == contentTop && bottom == contentBottom) return
        val atTop = scrollY <= minScrollY()
        contentTop = top
        contentBottom = bottom
        if (layout != null) scrollTo(scrollX, if (atTop) minScrollY() else scrollY)
        saveAnchor()
        publishViewport()
    }

    fun applyPlaceholder(text: String) {
        if (hint?.toString() != text) hint = text
    }

    private var zoomLabels: Pair<String, String>? = null
    private var zoomActionIds = IntArray(0)

    /** TalkBack's "Larger text" and "Smaller text" on the editor while it has a zoom ([labels]); null takes them away. */
    fun applyZoomActions(labels: Pair<String, String>?) {
        if (labels == zoomLabels) return
        zoomLabels = labels
        for (id in zoomActionIds) ViewCompat.removeAccessibilityAction(this, id)
        zoomActionIds = if (labels == null) IntArray(0) else intArrayOf(
            ViewCompat.addAccessibilityAction(this, labels.first) { _, _ -> zoom(1); true },
            ViewCompat.addAccessibilityAction(this, labels.second) { _, _ -> zoom(-1); true },
        )
    }

    fun applyMarkers(list: List<LineMarker>) {
        if (list == markerList) return
        markerList = list
        val next = SparseArray<LineMarker>(list.size)
        // the gravest marker of a line is the one shown
        for (marker in list) {
            val known = next[marker.line]
            if (known == null || marker.severity.ordinal < known.severity.ordinal) next.put(marker.line, marker)
        }
        markers = next
        publishViewport()
        invalidate()
    }

    fun applyDecorations(list: List<CodeDecoration>) {
        if (list == decorations) return
        decorations = list
        invalidate()
    }

    /** How many search matches are marked. */
    val searchMatchCount: Int get() = searchRanges.size / 2

    /** The search's matches as `start, end` pairs in order, and which of them is the current one. */
    fun applySearch(ranges: IntArray, current: Int) {
        if (ranges === searchRanges && current == searchCurrent) return
        searchRanges = ranges
        searchCurrent = current
        invalidate()
    }

    private fun applyPadding() {
        val metrics = metrics ?: return
        val numbers = if (showLineNumbers) metrics.gutterGap + ceil(digits * digitWidth).toInt() else 0
        gutterWidth = metrics.gutterStart + metrics.markerSize + numbers + metrics.textStart
        setPaddingRelative(gutterWidth, 0, metrics.textEnd, 0)
    }

    /** The gutter is as wide as the last line's number, and at least two digits. */
    private fun updateDigits() {
        val count = document?.lines?.count ?: 1
        var needed = 2
        var limit = 100
        while (count >= limit && needed < 9) {
            needed++
            limit *= 10
        }
        if (needed != digits) {
            digits = needed
            applyPadding()
        }
    }

    // ------------------------------------------------------------------ syntax colours

    /** The colours and emphasis the spans read while the text is drawn. */
    private class SyntaxLook {
        var colors = EMPTY_INTS
        var bold = BooleanArray(0)
        var italic = BooleanArray(0)

        fun set(look: EditorLook) {
            colors = look.syntaxColors
            bold = look.syntaxBold
            italic = look.syntaxItalic
        }
    }

    /**
     * Draws a token in its role's style. Not a metric span: bold is a thicker stroke and italic a
     * slant on the same glyphs, so the layout never depends on which lines carry spans.
     */
    private class RoleSpan(private val look: SyntaxLook, private val role: Int) : CharacterStyle(), UpdateAppearance {
        override fun updateDrawState(paint: TextPaint) {
            if (role >= look.colors.size) return
            paint.color = look.colors[role]
            if (look.bold[role]) {
                paint.style = Paint.Style.FILL_AND_STROKE
                paint.strokeWidth = paint.textSize * BOLD_STROKE
            }
            if (look.italic[role]) paint.textSkewX = ITALIC_SKEW
        }
    }

    private fun resetSyntax() {
        val editable = editableText ?: return
        for (line in applied.values) for (span in line.spans) editable.removeSpan(span)
        applied.clear()
        syntaxLast = -1
        syncSyntax()
    }

    /** The tokenizer changed: every line is coloured anew. */
    fun tokenizerChanged() = resetSyntax()

    /**
     * Makes the spans of the lines in and around the view match the document's tokens. Called in the
     * same pass that changed the text, and when scrolling comes near the edge of the lines done.
     */
    private fun syncSyntax() {
        val state = document ?: return
        val layout = layout
        val editable = editableText ?: return
        if (state.tokens.tokenizer == null) {
            if (applied.isNotEmpty()) {
                for (line in applied.values) for (span in line.spans) editable.removeSpan(span)
                applied.clear()
            }
            return
        }
        val lines = state.lines
        val firstVisible: Int
        val lastVisible: Int
        if (layout == null || height == 0) {
            firstVisible = 0
            lastVisible = SYNTAX_MARGIN
        } else {
            firstVisible = lines.lineOf(layout.getLineStart(layout.getLineForVertical(scrollY)))
            lastVisible = lines.lineOf(layout.getLineStart(layout.getLineForVertical(scrollY + height)))
        }
        val first = (firstVisible - SYNTAX_MARGIN).coerceAtLeast(0)
        val last = (lastVisible + SYNTAX_MARGIN).coerceAtMost(lines.count - 1)
        state.tokens.ensure(last) { line -> state.content.subSequence(lines.start(line), lines.end(line)) }
        val pass = ++syntaxPass
        val length = editable.length
        for (line in first..last) {
            val tokens = state.tokens.tokensOf(line)
            if (tokens.isEmpty()) continue
            val known = applied[tokens]
            if (known != null) {
                known.pass = pass
                continue
            }
            val lineStart = lines.start(line)
            val spans = Array(tokens.size / 3) { index ->
                val span = RoleSpan(syntaxLook, tokens[index * 3 + 2])
                val from = (lineStart + tokens[index * 3]).coerceAtMost(length)
                editable.setSpan(span, from, (lineStart + tokens[index * 3 + 1]).coerceIn(from, length), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                span
            }
            applied[tokens] = AppliedLine(spans, pass)
        }
        val stale = applied.entries.iterator()
        while (stale.hasNext()) {
            val entry = stale.next()
            if (entry.value.pass != pass) {
                for (span in entry.value.spans) editable.removeSpan(span)
                stale.remove()
            }
        }
        syntaxFirst = first
        syntaxLast = last
        // the lines beyond the view are done between frames, starting once the typing pauses
        removeCallbacks(tokenizeMore)
        if (state.tokens.pending) postDelayed(tokenizeMore, TOKENIZE_IDLE_MS)
    }

    /**
     * Tokenizes on beyond the view in short slices, each a message of its own so input and frames come
     * between them. An edit starts the wait again ([syncSyntax]).
     */
    private val tokenizeMore: Runnable = object : Runnable {
        override fun run() {
            val state = document ?: return
            val lines = state.lines
            val more = state.tokens.advance(System.nanoTime() + TOKENIZE_SLICE_NANOS) { line -> state.content.subSequence(lines.start(line), lines.end(line)) }
            if (more) post(this)
        }
    }

    /** Scrolling: the spans are set again once the view comes within a few lines of where they end. */
    private fun syncSyntaxIfNear() {
        val state = document ?: return
        val layout = layout ?: return
        if (state.tokens.tokenizer == null) return
        val lines = state.lines
        val firstVisible = lines.lineOf(layout.getLineStart(layout.getLineForVertical(scrollY)))
        val lastVisible = lines.lineOf(layout.getLineStart(layout.getLineForVertical(scrollY + height)))
        val slack = SYNTAX_MARGIN / 2
        val needFirst = (firstVisible - slack).coerceAtLeast(0)
        val needLast = (lastVisible + slack).coerceAtMost(lines.count - 1)
        if (syntaxLast < 0 || needFirst < syntaxFirst || needLast > syntaxLast) syncSyntax()
    }

    // ------------------------------------------------------------------ what is typed

    /** What goes into the text in place of what the user's edit brought: indentation after Enter, plain text only. */
    private fun filtered(source: CharSequence, start: Int, end: Int, dest: Spanned, dstart: Int): CharSequence? {
        if (end - start == 1 && source[start] == '\n') {
            // Enter keeps the indentation of the line it leaves
            var lineStart = dstart
            while (lineStart > 0 && dest[lineStart - 1] != '\n') lineStart--
            var indentEnd = lineStart
            while (indentEnd < dstart && (dest[indentEnd] == ' ' || dest[indentEnd] == '\t')) indentEnd++
            return if (indentEnd == lineStart) null else "\n" + dest.subSequence(lineStart, indentEnd)
        }
        var carriageReturn = false
        for (i in start until end) if (source[i] == '\r') { carriageReturn = true; break }
        val styled = source is Spanned && source.getSpans(start, end, Any::class.java).any { span -> foreign(source, span) }
        if (!carriageReturn && !styled) return null
        val plain = CodeEditorState.normalizeLineBreaks(source.subSequence(start, end).toString())
        if (carriageReturn || source !is Spanned) return plain
        // the word being composed keeps the keyboard's own marks (its underline, its suggestions)
        val kept = SpannableString(plain)
        for (span in source.getSpans(start, end, Any::class.java)) {
            if (foreign(source, span)) continue
            val from = (source.getSpanStart(span) - start).coerceIn(0, plain.length)
            val to = (source.getSpanEnd(span) - start).coerceIn(from, plain.length)
            kept.setSpan(span, from, to, source.getSpanFlags(span))
        }
        return kept
    }

    /** Styling that came with pasted or dropped text (a web page's bold, a colour): the document is plain text. */
    private fun foreign(source: Spanned, span: Any): Boolean {
        if (source.getSpanFlags(span) and Spanned.SPAN_COMPOSING != 0 || span is SuggestionSpan) return false
        return span is MetricAffectingSpan || span is ParagraphStyle || span is CharacterStyle
    }

    override fun onTextContextMenuItem(id: Int): Boolean = when (id) {
        android.R.id.undo -> document?.undo() == true
        android.R.id.redo -> document?.redo() == true
        // pasted text comes without its styles
        android.R.id.paste -> super.onTextContextMenuItem(android.R.id.pasteAsPlainText)
        else -> super.onTextContextMenuItem(id)
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (!ready || loading) return
        document?.hostSelected(selStart, selEnd)
        followSoon()
        updateBrackets()
        invalidate()
        onCursorActivity?.invoke(false)
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        if (!ready) return
        escapeArmed = false
        onFocusChange?.invoke(focused)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        val label = accessibilityLabel
        if (ready && label != null && info.hintText.isNullOrEmpty()) info.hintText = label
    }

    // ------------------------------------------------------------------ keys

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!ready) return super.onKeyDown(keyCode, event)
        if (keyInterceptor?.invoke(event) == true) {
            swallowedKey = keyCode
            return true
        }
        val wasArmed = escapeArmed
        if (!KeyEvent.isModifierKey(keyCode)) escapeArmed = false
        if (isLinkModifier(keyCode)) updateHoverLink(event.metaState)
        val ctrl = event.isCtrlPressed || event.isMetaPressed
        val state = document
        when {
            keyCode == KeyEvent.KEYCODE_ESCAPE && event.hasNoModifiers() -> {
                // Esc, then Tab: the way out of an editor in which Tab types. The key stops here (an Esc nobody
                // takes is the system's Back); a second Esc is the platform's, which gives up the focus.
                if (wasArmed) return super.onKeyDown(keyCode, event)
                escapeArmed = true
                swallowedKey = keyCode
                return true
            }
            keyCode == KeyEvent.KEYCODE_TAB && !ctrl && !event.isAltPressed -> {
                if (wasArmed || readOnly) {
                    onLeave?.invoke(!event.isShiftPressed)
                } else if (state != null) {
                    if (event.isShiftPressed) state.outdentSelection(indent) else state.indentSelection(indent, typed = true)
                }
                return true
            }
            keyCode == KeyEvent.KEYCODE_MENU || (keyCode == KeyEvent.KEYCODE_F10 && event.isShiftPressed) -> {
                openContextMenuAtCursor()
                return true
            }
            ctrl && !event.isAltPressed -> if (shortcut(keyCode, event)) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (ready && keyCode == swallowedKey) {
            swallowedKey = KeyEvent.KEYCODE_UNKNOWN
            return true
        }
        if (ready && isLinkModifier(keyCode)) updateHoverLink(event.metaState)
        return super.onKeyUp(keyCode, event)
    }

    /** The platform asks again for a Ctrl key nobody took: the answer is the same, never its own undo. */
    override fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean =
        (ready && (event.isCtrlPressed || event.isMetaPressed) && !event.isAltPressed && shortcut(keyCode, event)) || super.onKeyShortcut(keyCode, event)

    private fun shortcut(keyCode: Int, event: KeyEvent): Boolean {
        val state = document ?: return false
        when (keyCode) {
            KeyEvent.KEYCODE_Z -> if (!readOnly) { if (event.isShiftPressed) state.redo() else state.undo() }
            KeyEvent.KEYCODE_Y -> if (!readOnly) state.redo()
            KeyEvent.KEYCODE_A -> onTextContextMenuItem(android.R.id.selectAll)
            KeyEvent.KEYCODE_C -> onTextContextMenuItem(android.R.id.copy)
            KeyEvent.KEYCODE_X -> onTextContextMenuItem(if (readOnly) android.R.id.copy else android.R.id.cut)
            KeyEvent.KEYCODE_V -> if (!readOnly) onTextContextMenuItem(android.R.id.paste)
            else -> return zoomKey(keyCode, event)
        }
        return true
    }

    /** Ctrl with + or = (larger), − (smaller) and 0 (the default), by the character the key types as a browser's zoom does. */
    private fun zoomKey(keyCode: Int, event: KeyEvent): Boolean {
        val zoom = onZoomStep ?: return false
        val step = when (keyCode) {
            KeyEvent.KEYCODE_NUMPAD_ADD -> 1
            KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> -1
            KeyEvent.KEYCODE_NUMPAD_0 -> 0
            else -> when (event.getUnicodeChar(event.metaState and (KeyEvent.META_CTRL_MASK or KeyEvent.META_META_MASK).inv()).toChar()) {
                '+', '=' -> 1
                '-' -> -1
                '0' -> 0
                else -> return false
            }
        }
        zoom(step)
        return true
    }

    private fun isLinkModifier(keyCode: Int) = keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT ||
        keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT

    // ------------------------------------------------------------------ pointer

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!ready) return super.onTouchEvent(event)
        val action = event.actionMasked
        val mouse = event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE
        if (!mouse) scaleDetector.onTouchEvent(event)
        if (action == MotionEvent.ACTION_DOWN) {
            stopFling()
            escapeArmed = false
            swallowGesture = false
            scaling = false
            pressedLink = -1
            val x = event.x
            val y = event.y
            if (event.buttonState and (MotionEvent.BUTTON_SECONDARY or MotionEvent.BUTTON_STYLUS_SECONDARY) != 0) {
                openContextMenuAt(x, y)
                swallowGesture = true
                return true
            }
            if (mouse && event.metaState and (KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON) != 0) {
                val offset = textOffsetAt(x, y)
                if (offset >= 0 && linkRange(offset) != NO_REVEAL) {
                    pressedLink = offset
                    swallowGesture = true
                    return true
                }
            }
            if (mouse && event.metaState and KeyEvent.META_SHIFT_ON != 0 && hasFocus()) {
                // Shift and a click: the selection reaches from where it began to the click
                Selection.setSelection(editableText, selectionStart.coerceAtLeast(0), getOffsetForPosition(x, y))
                swallowGesture = true
                return true
            }
            if (!mouse) beginTouch(event)
        } else if (swallowGesture) {
            if (action == MotionEvent.ACTION_UP && pressedLink >= 0) {
                val offset = textOffsetAt(event.x, event.y)
                val range = linkRange(pressedLink)
                if (offset >= CodeEditorState.unpackStart(range) && offset <= CodeEditorState.unpackEnd(range)) onLink?.invoke(pressedLink)
                pressedLink = -1
            }
            return true
        } else if (!mouse && trackTouch(event)) {
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun beginTouch(event: MotionEvent) {
        dragging = false
        longPressed = false
        downX = event.x
        downY = event.y
        lastX = downX
        lastY = downY
        // the second tap of a double tap selects a word and drags on by words: that gesture is the platform's
        secondTap = event.eventTime - lastUpTime < ViewConfiguration.getDoubleTapTimeout() &&
            abs(downX - lastUpX) < viewConfiguration.scaledDoubleTapSlop && abs(downY - lastUpY) < viewConfiguration.scaledDoubleTapSlop
        velocityTracker?.recycle()
        velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
        nested.startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL or ViewCompat.SCROLL_AXIS_HORIZONTAL, ViewCompat.TYPE_TOUCH)
    }

    /** A finger on the text: past the slop it scrolls, and the platform's tap and long press are called off. True while it scrolls. */
    private fun trackTouch(event: MotionEvent): Boolean {
        velocityTracker?.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (scaling) return true
                val x = event.x
                val y = event.y
                if (!dragging && !longPressed && !secondTap && event.pointerCount == 1) {
                    val slop = viewConfiguration.scaledTouchSlop
                    if (abs(y - downY) > slop || (!softWrap && abs(x - downX) > slop)) {
                        dragging = true
                        cancelSuperGesture()
                        parent?.requestDisallowInterceptTouchEvent(true)
                        lastX = x
                        lastY = y
                    }
                }
                if (dragging) {
                    scrollByUser((lastX - x).roundToInt(), (lastY - y).roundToInt(), ViewCompat.TYPE_TOUCH)
                    lastX = x
                    lastY = y
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                lastUpTime = event.eventTime
                lastUpX = event.x
                lastUpY = event.y
                if (scaling) {
                    scaling = false
                    nested.stopNestedScroll(ViewCompat.TYPE_TOUCH)
                    return true
                }
                if (dragging) {
                    endDrag(fling = event.actionMasked == MotionEvent.ACTION_UP)
                    return true
                }
                nested.stopNestedScroll(ViewCompat.TYPE_TOUCH)
            }
        }
        return false
    }

    private fun endDrag(fling: Boolean) {
        if (!dragging) return
        dragging = false
        val tracker = velocityTracker
        var velocityX = 0f
        var velocityY = 0f
        if (fling && tracker != null) {
            tracker.computeCurrentVelocity(1000, viewConfiguration.scaledMaximumFlingVelocity.toFloat())
            velocityX = if (softWrap) 0f else -tracker.xVelocity
            velocityY = -tracker.yVelocity
        }
        tracker?.recycle()
        velocityTracker = null
        val minimum = viewConfiguration.scaledMinimumFlingVelocity
        if (abs(velocityY) < minimum) velocityY = 0f
        if (abs(velocityX) < minimum) velocityX = 0f
        if ((velocityX != 0f || velocityY != 0f) && !nested.dispatchNestedPreFling(velocityX, velocityY)) {
            nested.stopNestedScroll(ViewCompat.TYPE_TOUCH)
            nested.startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_NON_TOUCH)
            flinging = true
            flingLastY = scrollY
            scroller.fling(scrollX, scrollY, velocityX.toInt(), velocityY.toInt(), 0, maxScrollX(), minScrollY(), maxScrollY())
            postInvalidateOnAnimation()
        } else {
            // the shell settles its bars when the content comes to rest
            nested.dispatchNestedFling(0f, 0f, true)
            nested.stopNestedScroll(ViewCompat.TYPE_TOUCH)
        }
    }

    private fun stopFling() {
        if (!flinging) return
        flinging = false
        scroller.abortAnimation()
        nested.stopNestedScroll(ViewCompat.TYPE_NON_TOUCH)
        settleNestedScroll()
    }

    /** Tells the nested scrolling parent (the shell) that the content has come to rest, so it settles its bars. */
    private fun settleNestedScroll() {
        if (nested.startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)) {
            nested.dispatchNestedFling(0f, 0f, true)
            nested.stopNestedScroll(ViewCompat.TYPE_TOUCH)
        }
    }

    override fun computeScroll() {
        if (!ready || !flinging) return
        if (scroller.computeScrollOffset()) {
            val before = scrollY
            scrollTo(scroller.currX, scroller.currY)
            val moved = scrollY - before
            if (moved != 0) nested.dispatchNestedScroll(0, moved, 0, 0, null, ViewCompat.TYPE_NON_TOUCH)
            postInvalidateOnAnimation()
        } else {
            stopFling()
        }
    }

    /** Ends the platform's handling of the gesture in progress: no tap, no long press, no cursor move on release. */
    private fun cancelSuperGesture() {
        cancelLongPress()
        val now = android.os.SystemClock.uptimeMillis()
        val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, lastX, lastY, 0)
        super.onTouchEvent(cancel)
        cancel.recycle()
    }

    override fun performLongClick(): Boolean {
        if (ready) longPressed = true
        return super.performLongClick()
    }

    override fun performLongClick(x: Float, y: Float): Boolean {
        if (ready) longPressed = true
        return super.performLongClick(x, y)
    }

    /** Scrolls by a finger's or a wheel's delta, the nested scrolling parent before and after; returns what the text took of dy. */
    private fun scrollByUser(dx: Int, dy: Int, type: Int): Int {
        var remainingX = dx
        var remainingY = dy
        scrollConsumed[0] = 0
        scrollConsumed[1] = 0
        if (nested.dispatchNestedPreScroll(remainingX, remainingY, scrollConsumed, scrollOffset, type)) {
            remainingX -= scrollConsumed[0]
            remainingY -= scrollConsumed[1]
        }
        val beforeX = scrollX
        val beforeY = scrollY
        scrollTo(beforeX + remainingX, beforeY + remainingY)
        val takenX = scrollX - beforeX
        val takenY = scrollY - beforeY
        nested.dispatchNestedScroll(takenX, takenY, remainingX - takenX, remainingY - takenY, scrollOffset, type)
        return takenY
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (ready && event.actionMasked == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) {
            val vertical = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            val horizontal = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
            if (event.metaState and (KeyEvent.META_CTRL_ON or KeyEvent.META_META_ON) != 0 && onZoomStep != null) {
                // a step for each notch; a fine wheel's small turns add up to one
                wheelGathered += vertical
                while (abs(wheelGathered) >= 1f) {
                    zoom(if (wheelGathered > 0f) 1 else -1)
                    wheelGathered -= if (wheelGathered > 0f) 1f else -1f
                }
                return true
            }
            wheelGathered = 0f
            stopFling()
            val step = viewConfiguration.scaledVerticalScrollFactor
            val sideways = event.metaState and KeyEvent.META_SHIFT_ON != 0
            val dx = if (sideways) -vertical * step else horizontal * step
            val dy = if (sideways) 0f else -vertical * step
            nested.startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_NON_TOUCH)
            scrollByUser(dx.roundToInt(), dy.roundToInt(), ViewCompat.TYPE_NON_TOUCH)
            nested.stopNestedScroll(ViewCompat.TYPE_NON_TOUCH)
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    private fun zoom(step: Int) {
        onZoomStep?.invoke(step)
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (ready) {
            if (event.actionMasked == MotionEvent.ACTION_HOVER_EXIT) {
                hoverX = Float.NaN
                hoverY = Float.NaN
            } else {
                hoverX = event.x
                hoverY = event.y
            }
            updateHoverLink(event.metaState)
        }
        return super.onHoverEvent(event)
    }

    override fun onResolvePointerIcon(event: MotionEvent, pointerIndex: Int): PointerIcon? {
        if (ready) {
            if (inGutter(event.getX(pointerIndex))) return PointerIcon.getSystemIcon(context, PointerIcon.TYPE_ARROW)
            if (hoverLinkStart >= 0) return PointerIcon.getSystemIcon(context, PointerIcon.TYPE_HAND)
        }
        return super.onResolvePointerIcon(event, pointerIndex)
    }

    /** The link under the resting pointer while Ctrl or Alt is held: it is underlined and the pointer becomes a hand. */
    private fun updateHoverLink(metaState: Int) {
        var start = -1
        var end = -1
        if (!hoverX.isNaN() && metaState and (KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON) != 0) {
            val offset = textOffsetAt(hoverX, hoverY)
            if (offset >= 0) {
                val range = linkRange(offset)
                if (range != NO_REVEAL) {
                    start = CodeEditorState.unpackStart(range)
                    end = CodeEditorState.unpackEnd(range)
                }
            }
        }
        if (start != hoverLinkStart || end != hoverLinkEnd) {
            hoverLinkStart = start
            hoverLinkEnd = end
            invalidate()
        }
    }

    private fun linkRange(offset: Int): Long {
        val state = document ?: return NO_REVEAL
        val range = links?.linkAt(state, offset) ?: return NO_REVEAL
        return CodeEditorState.pack(range.min.coerceIn(0, length()), range.max.coerceIn(0, length()))
    }

    private fun inGutter(x: Float): Boolean = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) x >= width - gutterWidth else x < gutterWidth

    /** The offset of the character at a point of the view, or -1 when the point is not on text (the gutter, past a line's end). */
    private fun textOffsetAt(x: Float, y: Float): Int {
        val layout = layout ?: return -1
        if (inGutter(x)) return -1
        val row = layout.getLineForVertical((y + scrollY).toInt())
        val inLayout = x - compoundPaddingLeft + scrollX
        if (inLayout < layout.getLineLeft(row) || inLayout > layout.getLineRight(row)) return -1
        if (y + scrollY > layout.getLineBottom(layout.lineCount - 1)) return -1
        return getOffsetForPosition(x, y)
    }

    // ------------------------------------------------------------------ context menu

    private fun openContextMenuAt(x: Float, y: Float) {
        val offset = getOffsetForPosition(x, y)
        // a click on the selection keeps it; elsewhere the cursor goes to the click, as in desktop editors
        if (offset < min(selectionStart, selectionEnd) || offset > max(selectionStart, selectionEnd) || !hasSelection()) {
            Selection.setSelection(editableText, offset)
        }
        if (!hasFocus()) requestFocus()
        onContextMenu?.invoke(x.roundToInt(), y.roundToInt(), offset)
    }

    private fun openContextMenuAtCursor() {
        val layout = layout ?: return
        val offset = selectionEnd.coerceAtLeast(0)
        val row = layout.getLineForOffset(offset)
        val x = compoundPaddingLeft + layout.getPrimaryHorizontal(offset) - scrollX
        onContextMenu?.invoke(x.roundToInt(), layout.getLineBottom(row) - scrollY, offset)
    }

    override fun showContextMenu(): Boolean {
        if (!ready) return super.showContextMenu()
        openContextMenuAtCursor()
        return true
    }

    override fun showContextMenu(x: Float, y: Float): Boolean {
        if (!ready) return super.showContextMenu(x, y)
        openContextMenuAt(x, y)
        return true
    }

    override fun performContextClick(x: Float, y: Float): Boolean {
        if (!ready) return super.performContextClick(x, y)
        openContextMenuAt(x, y)
        return true
    }

    /** The app's entries in the platform's selection toolbar, after its own; in a read-only editor nothing that edits. */
    private inner class ToolbarCallback : ActionMode.Callback {
        private var actions: List<SelectionAction> = emptyList()

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            actions = selectionActions?.invoke().orEmpty()
            actions.forEachIndexed { index, action -> menu.add(Menu.NONE, FIRST_ACTION_ID + index, Menu.CATEGORY_SECONDARY + index, action.label) }
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.removeItem(android.R.id.autofill)
            if (readOnly) for (id in EDITING_ITEMS) menu.removeItem(id)
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val action = actions.getOrNull(item.itemId - FIRST_ACTION_ID) ?: return false
            mode.finish()
            action.run()
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) = Unit
    }

    // ------------------------------------------------------------------ scrolling and layout

    private fun minScrollY(): Int = -(contentTop + (metrics?.edge ?: 0))

    private fun maxScrollY(): Int {
        val layout = layout ?: return minScrollY()
        return max(minScrollY(), layout.height - height + contentBottom + (metrics?.edge ?: 0))
    }

    private fun maxScrollX(): Int {
        if (softWrap) return 0
        return max(0, ceil(widestLine).toInt() - (width - compoundPaddingLeft - compoundPaddingRight) + (metrics?.textEnd ?: 0))
    }

    override fun scrollTo(x: Int, y: Int) {
        if (!ready) {
            super.scrollTo(x, y)
            return
        }
        super.scrollTo(x.coerceIn(0, maxScrollX()), y.coerceIn(minScrollY(), maxScrollY()))
    }

    override fun onScrollChanged(horiz: Int, vert: Int, oldHoriz: Int, oldVert: Int) {
        super.onScrollChanged(horiz, vert, oldHoriz, oldVert)
        if (!ready) return
        saveAnchor()
        syncSyntaxIfNear()
        publishViewport()
        onViewportChanged?.invoke()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // a new width wraps the lines anew: the row at the top stays at the top
        if (ready && layout != null && measuredWidth > 0 && MeasureSpec.getSize(widthMeasureSpec) != measuredWidth && document != null) {
            saveAnchor()
            restoreScroll = true
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (!ready || layout == null) return
        val state = document
        if (restoreScroll && state != null) applyRestore(state) else scrollTo(scrollX, scrollY)
        if (pendingReveal != NO_REVEAL) applyPendingReveal()
        if (followCursor) bringPointIntoView(selectionEnd.coerceAtLeast(0))
        saveAnchor()
        syncSyntaxIfNear()
        publishViewport()
        onViewportChanged?.invoke()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!ready) return
        // the on-screen keyboard took a part of the height: the line with the cursor stays in sight above it
        if (h < oldh && hasFocus()) {
            followCursor = true
            post { bringPointIntoView(selectionEnd.coerceAtLeast(0)) }
        }
    }

    /**
     * The platform asks for the cursor to be in view after every layout pass. Here that happens only
     * when the cursor moved or the text changed, and within the room the bars leave: a finger scrolling
     * the text away from the cursor must not be pulled back.
     */
    override fun bringPointIntoView(offset: Int): Boolean {
        if (!ready) return super.bringPointIntoView(offset)
        if (!followCursor) return false
        followCursor = false
        return scrollIntoView(offset, offset, far = false)
    }

    /**
     * The cursor moved or the text changed: its line is brought into view once the change is through,
     * without waiting for the next frame's draw (the platform's own moment for it).
     */
    private fun followSoon() {
        followCursor = true
        removeCallbacks(follow)
        post(follow)
    }

    private val follow = Runnable {
        // a layout pass to come does it with the new layout (see onLayout)
        if (followCursor && layout != null && !isLayoutRequested) bringPointIntoView(selectionEnd.coerceAtLeast(0))
    }

    private fun applyPendingReveal() {
        val range = pendingReveal
        pendingReveal = NO_REVEAL
        followCursor = false
        scrollIntoView(CodeEditorState.unpackStart(range), CodeEditorState.unpackEnd(range), far = true)
    }

    /**
     * Scrolls as little as needed to show the text from [start] to [end]. With [far], a range more
     * than a view's height away lands a third of the way down instead of at the edge, with its
     * surroundings in sight.
     */
    private fun scrollIntoView(start: Int, end: Int, far: Boolean): Boolean {
        val layout = layout ?: return false
        if (height == 0) return false
        val length = length()
        val from = min(start, end).coerceIn(0, length)
        val to = max(start, end).coerceIn(0, length)
        val top = layout.getLineTop(layout.getLineForOffset(from))
        val bottom = layout.getLineBottom(layout.getLineForOffset(to))
        val edge = metrics?.edge ?: 0
        val viewTop = scrollY + contentTop + edge
        val viewBottom = scrollY + height - contentBottom - edge
        val room = viewBottom - viewTop
        var targetY = scrollY
        if (top < viewTop || bottom > viewBottom) {
            targetY = if (far && (bottom < viewTop - room || top > viewBottom + room)) {
                top - contentTop - edge - room / 3
            } else if (top < viewTop || bottom - top > room) {
                top - contentTop - edge
            } else {
                bottom - height + contentBottom + edge
            }
        }
        var targetX = scrollX
        if (!softWrap) {
            val x = layout.getPrimaryHorizontal(to)
            widestLine = max(widestLine, layout.getLineWidth(layout.getLineForOffset(to)))
            val available = width - compoundPaddingLeft - compoundPaddingRight
            val margin = (metrics?.textEnd ?: 0) * 2
            if (x - margin < scrollX) targetX = (x - margin).toInt() else if (x + margin > scrollX + available) targetX = (x + margin - available).toInt()
        }
        if (targetX == scrollX && targetY == scrollY) return false
        stopFling()
        scrollTo(targetX, targetY)
        return true
    }

    /**
     * Tells the document which row is at the top of the text and how much of it is scrolled past, so the
     * document comes back there ([CodeEditorState.firstVisibleLine]). Not while a scroll to it is pending.
     */
    private fun saveAnchor() {
        val state = document ?: return
        val layout = layout ?: return
        if (restoreScroll || loading || state.host !== this || state.lines.length != length()) return
        val top = (scrollY + contentTop + (metrics?.edge ?: 0)).coerceIn(0, layout.height)
        val row = layout.getLineForVertical(top)
        state.scrollX = scrollX
        state.setScrollAnchor(layout.getLineStart(row), top - layout.getLineTop(row))
    }

    /** Scrolls to where the document says it was: its anchor row at the top of the text, as far as the end allows. */
    private fun applyRestore(state: CodeEditorState) {
        restoreScroll = false
        followCursor = false
        val layout = layout ?: return
        val row = layout.getLineForOffset(state.scrollAnchor.coerceIn(0, length()))
        // after the lines wrapped anew, the row may be lower than the pixels scrolled past it
        val delta = state.scrollAnchorDelta.coerceAtMost((layout.getLineBottom(row) - layout.getLineTop(row) - 1).coerceAtLeast(0))
        scrollTo(state.scrollX, layout.getLineTop(row) + delta - contentTop - (metrics?.edge ?: 0))
        saveAnchor()
    }

    /** The cursor's place in the view, for what floats beside it (the completion list): left, top, bottom. */
    fun cursorAnchor(out: IntArray): Boolean {
        val layout = layout ?: return false
        val offset = selectionEnd.coerceIn(0, length())
        val row = layout.getLineForOffset(offset)
        out[0] = (compoundPaddingLeft + layout.getPrimaryHorizontal(offset) - scrollX).roundToInt()
        out[1] = layout.getLineTop(row) - scrollY
        out[2] = layout.getLineBottom(row) - scrollY
        return true
    }

    /** How far the text has scrolled, how long it is and how much of it shows, for the scrollbar. */
    val scrollOffsetY: Int get() = scrollY - minScrollY()
    val scrollRangeY: Int get() = maxScrollY() - minScrollY() + height
    fun scrollToFraction(position: Float) {
        stopFling()
        scrollTo(scrollX, minScrollY() + position.roundToInt())
    }

    val isScrolling: Boolean get() = dragging || flinging
    val contentTopInset: Int get() = contentTop
    val contentBottomInset: Int get() = contentBottom

    /** Tells [CodeEditor] which markers are in view and where, when that changed. */
    private fun publishViewport() {
        val callback = onMarkersShown ?: return
        val layout = layout
        val state = document
        var shown: List<ShownMarker> = emptyList()
        if (layout != null && state != null && markers.size() > 0 && height > 0) {
            val lines = state.lines
            val firstLine = lines.lineOf(layout.getLineStart(layout.getLineForVertical(scrollY)))
            val lastLine = lines.lineOf(layout.getLineStart(layout.getLineForVertical(scrollY + height)))
            val list = ArrayList<ShownMarker>()
            for (i in 0 until markers.size()) {
                val line = markers.keyAt(i)
                if (line < firstLine || line > lastLine || line >= lines.count) continue
                val row = layout.getLineForOffset(lines.start(line).coerceAtMost(length()))
                list += ShownMarker(line, layout.getLineTop(row) - scrollY, layout.getLineBottom(row) - scrollY)
            }
            shown = list
        }
        if (shown != shownMarkers) {
            shownMarkers = shown
            callback(shown)
        }
    }

    // ------------------------------------------------------------------ brackets

    private fun updateBrackets() {
        val state = document
        var a = -1
        var b = -1
        if (matchBrackets && state != null && selectionStart == selectionEnd && selectionEnd >= 0 && state.lines.length == length()) {
            val found = Brackets.match(state, selectionEnd)
            a = CodeEditorState.unpackStart(found)
            b = CodeEditorState.unpackEnd(found)
        }
        if (a != bracketA || b != bracketB) {
            bracketA = a
            bracketB = b
            invalidate()
        }
    }

    // ------------------------------------------------------------------ drawing

    override fun onDraw(canvas: Canvas) {
        val layout = layout
        val look = look
        val state = document
        val metrics = metrics
        if (!ready || layout == null || look == null || state == null || metrics == null || state.lines.length != length()) {
            super.onDraw(canvas)
            return
        }
        val firstRow = layout.getLineForVertical(scrollY)
        val lastRow = layout.getLineForVertical(scrollY + height)
        if (!softWrap) for (row in firstRow..lastRow) widestLine = max(widestLine, layout.getLineWidth(row))
        drawLineTints(canvas, layout, look, state, firstRow, lastRow)
        // what marks characters stays in the text's own area: scrolled sideways, it goes under the gutter like the text
        canvas.save()
        canvas.clipRect(scrollX + compoundPaddingLeft, scrollY, scrollX + width - compoundPaddingRight, scrollY + height)
        drawBehindText(canvas, layout, look, metrics, firstRow, lastRow)
        canvas.restore()
        super.onDraw(canvas)
        canvas.save()
        canvas.clipRect(scrollX + compoundPaddingLeft, scrollY, scrollX + width - compoundPaddingRight, scrollY + height)
        drawOverText(canvas, layout, look, metrics, firstRow, lastRow)
        canvas.restore()
        drawGutter(canvas, layout, look, state, metrics, firstRow, lastRow)
    }

    /** The tints that reach across the whole width, the gutter included: the cursor's line, marked lines, line decorations. */
    private fun drawLineTints(canvas: Canvas, layout: Layout, look: EditorLook, state: CodeEditorState, firstRow: Int, lastRow: Int) {
        val lines = state.lines
        val left = scrollX.toFloat()
        val right = left + width
        val visibleStart = layout.getLineStart(firstRow)
        val visibleEnd = layout.getLineEnd(lastRow)
        fillPaint.style = Paint.Style.FILL
        // the line with the cursor, all its rows
        if (highlightCurrentLine && selectionStart == selectionEnd && selectionEnd >= 0) {
            val line = lines.lineOf(selectionEnd)
            fillPaint.color = look.currentLine
            canvas.drawRect(left, layout.getLineTop(layout.getLineForOffset(lines.start(line))).toFloat(), right,
                layout.getLineBottom(layout.getLineForOffset(lines.end(line))).toFloat(), fillPaint)
        }
        val firstLine = lines.lineOf(visibleStart)
        val lastLine = lines.lineOf(visibleEnd)
        if (tintMarkedLines) {
            for (i in 0 until markers.size()) {
                val line = markers.keyAt(i)
                if (line < firstLine || line > lastLine || line >= lines.count) continue
                fillPaint.color = withAlpha(look.tones[markerTone(markers.valueAt(i).severity).ordinal], LINE_TINT)
                canvas.drawRect(left, layout.getLineTop(layout.getLineForOffset(lines.start(line))).toFloat(), right,
                    layout.getLineBottom(layout.getLineForOffset(lines.end(line))).toFloat(), fillPaint)
            }
        }
        val length = length()
        for (decoration in decorations) {
            if (decoration.kind != DecorationKind.Line || decoration.end < visibleStart || decoration.start > visibleEnd) continue
            val from = decoration.start.coerceIn(0, length)
            val to = decoration.end.coerceIn(from, length)
            fillPaint.color = withAlpha(look.tones[decoration.tone.ordinal], LINE_TINT)
            canvas.drawRect(left, layout.getLineTop(layout.getLineForOffset(lines.start(lines.lineOf(from)))).toFloat(), right,
                layout.getLineBottom(layout.getLineForOffset(lines.end(lines.lineOf(to)))).toFloat(), fillPaint)
        }
    }

    /** What lies behind single characters: tinted ranges, the search's matches, the frame of a matched bracket. */
    private fun drawBehindText(canvas: Canvas, layout: Layout, look: EditorLook, metrics: EditorMetrics, firstRow: Int, lastRow: Int) {
        val textLeft = compoundPaddingLeft.toFloat()
        val visibleStart = layout.getLineStart(firstRow)
        val visibleEnd = layout.getLineEnd(lastRow)
        val length = length()
        fillPaint.style = Paint.Style.FILL
        for (decoration in decorations) {
            if (decoration.kind != DecorationKind.Background || decoration.end < visibleStart || decoration.start > visibleEnd) continue
            val from = decoration.start.coerceIn(0, length)
            val to = decoration.end.coerceIn(from, length)
            if (to > from) {
                fillPaint.color = withAlpha(look.tones[decoration.tone.ordinal], RANGE_TINT)
                drawRange(canvas, layout, from, to, textLeft, fillPaint)
            }
        }
        // search matches: the first that ends in view, then on until one starts past it
        val ranges = searchRanges
        if (ranges.isNotEmpty()) {
            var low = 0
            var high = ranges.size / 2
            while (low < high) {
                val mid = (low + high) ushr 1
                if (ranges[mid * 2 + 1] < visibleStart) low = mid + 1 else high = mid
            }
            val accent = look.tones[DecorationTone.Accent.ordinal]
            var index = low
            while (index < ranges.size / 2 && ranges[index * 2] <= visibleEnd) {
                val from = ranges[index * 2].coerceIn(0, length)
                val to = ranges[index * 2 + 1].coerceIn(from, length)
                if (to > from) {
                    fillPaint.color = withAlpha(accent, if (index == searchCurrent) CURRENT_MATCH_TINT else RANGE_TINT)
                    drawRange(canvas, layout, from, to, textLeft, fillPaint)
                }
                index++
            }
        }
        if (bracketA >= 0 && bracketB >= 0) {
            strokePaint.color = look.bracket
            for (at in intArrayOf(bracketA, bracketB)) {
                if (at < visibleStart || at >= visibleEnd || at >= length) continue
                val row = layout.getLineForOffset(at)
                val x = textLeft + layout.getPrimaryHorizontal(at)
                val next = textLeft + layout.getPrimaryHorizontal(at + 1)
                rect.set(min(x, next), layout.getLineTop(row) + metrics.stroke, max(x, next), layout.getLineBottom(row) - metrics.stroke)
                canvas.drawRoundRect(rect, metrics.corner, metrics.corner, strokePaint)
            }
        }
    }

    private fun drawRange(canvas: Canvas, layout: Layout, from: Int, to: Int, textLeft: Float, paint: Paint) {
        path.rewind()
        layout.getSelectionPath(from, to, path)
        canvas.save()
        canvas.translate(textLeft, 0f)
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun drawOverText(canvas: Canvas, layout: Layout, look: EditorLook, metrics: EditorMetrics, firstRow: Int, lastRow: Int) {
        val visibleStart = layout.getLineStart(firstRow)
        val visibleEnd = layout.getLineEnd(lastRow)
        val length = length()
        for (decoration in decorations) {
            if (decoration.kind != DecorationKind.Underline || decoration.end < visibleStart || decoration.start > visibleEnd) continue
            val from = decoration.start.coerceIn(0, length)
            strokePaint.color = look.tones[decoration.tone.ordinal]
            drawUnderline(canvas, layout, from, decoration.end.coerceIn(from, length), metrics)
        }
        if (hoverLinkStart >= 0 && hoverLinkEnd > hoverLinkStart) {
            strokePaint.color = look.tones[DecorationTone.Accent.ordinal]
            drawUnderline(canvas, layout, hoverLinkStart.coerceAtMost(length), hoverLinkEnd.coerceAtMost(length), metrics)
        }
    }

    /** A line under the text from [from] to [to], row by row. */
    private fun drawUnderline(canvas: Canvas, layout: Layout, from: Int, to: Int, metrics: EditorMetrics) {
        if (to <= from) return
        val textLeft = compoundPaddingLeft.toFloat()
        for (row in layout.getLineForOffset(from)..layout.getLineForOffset(to)) {
            val rowStart = max(from, layout.getLineStart(row))
            val rowEnd = min(to, layout.getLineVisibleEnd(row))
            if (rowEnd <= rowStart) continue
            val a = layout.getPrimaryHorizontal(rowStart)
            val b = if (rowEnd == layout.getLineVisibleEnd(row)) layout.getLineRight(row).takeIf { layout.getParagraphDirection(row) > 0 } ?: layout.getPrimaryHorizontal(rowEnd)
            else layout.getPrimaryHorizontal(rowEnd)
            val y = layout.getLineBaseline(row) + metrics.stroke * 2
            canvas.drawLine(textLeft + min(a, b), y, textLeft + max(a, b), y, strokePaint)
        }
    }

    private fun drawGutter(canvas: Canvas, layout: Layout, look: EditorLook, state: CodeEditorState, metrics: EditorMetrics, firstRow: Int, lastRow: Int) {
        val lines = state.lines
        val rtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
        val edge = if (rtl) scrollX + width - gutterWidth else scrollX
        // the marker at the start edge, the numbers after it, right-aligned in their column
        val markerX = if (rtl) edge + gutterWidth - metrics.gutterStart - metrics.markerSize else edge + metrics.gutterStart
        val numberRight = if (rtl) edge + metrics.textStart + digits * digitWidth else edge + gutterWidth - metrics.textStart.toFloat()
        val cursorLine = if (selectionEnd >= 0) lines.lineOf(selectionEnd) else -1
        var line = lines.lineOf(layout.getLineStart(firstRow))
        for (row in firstRow..lastRow) {
            val rowStart = layout.getLineStart(row)
            // only a line's first row carries its number and its marker
            if (rowStart != lines.start(line)) {
                if (rowStart > lines.end(line)) line = lines.lineOf(rowStart)
                if (rowStart != lines.start(line)) continue
            }
            if (showLineNumbers) {
                numberPaint.color = if (line == cursorLine) look.lineNumberCurrent else look.lineNumber
                canvas.drawText(lineNumberText(line + 1), numberRight, layout.getLineBaseline(row).toFloat(), numberPaint)
            }
            val marker = markers[line]
            if (marker != null) {
                val top = layout.getLineTop(row) + (layout.getLineBottom(row) - layout.getLineTop(row) - metrics.markerSize) / 2f
                MarkerIcons.draw(canvas, marker.severity, markerX.toFloat(), top, metrics.markerSize.toFloat(),
                    look.tones[markerTone(marker.severity).ordinal], strokePaint)
                strokePaint.strokeWidth = metrics.stroke
            }
            line++
            if (line >= lines.count) break
        }
    }

    private val numberTexts = arrayOfNulls<String>(256)

    /** The numbers drawn on every frame are made into text once. */
    private fun lineNumberText(number: Int): String {
        val slot = number and 0xFF
        val known = numberTexts[slot]
        if (known != null && known.length == digitCount(number) && known.toInt() == number) return known
        return number.toString().also { numberTexts[slot] = it }
    }

    private fun digitCount(number: Int): Int {
        var count = 1
        var rest = number
        while (rest >= 10) {
            rest /= 10
            count++
        }
        return count
    }

    private object EditorMovement : ArrowKeyMovementMethod() {
        /**
         * The platform's method scrolls the text under a dragging finger and then moves the cursor into
         * what is in view. The view scrolls itself (see [trackTouch]), and scrolling leaves the cursor alone.
         */
        override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean = false
    }

    companion object {
        private val EMPTY_INTS = IntArray(0)
        private const val NO_REVEAL = Long.MIN_VALUE

        /** Lines coloured above and below the view, so a short scroll finds them done. */
        private const val SYNTAX_MARGIN = 40

        /** How long after the last edit the lines beyond the view are tokenized on, and for how long at a time. */
        private const val TOKENIZE_IDLE_MS = 80L
        private const val TOKENIZE_SLICE_NANOS = 4_000_000L
        private const val BOLD_STROKE = 0.035f
        private const val ITALIC_SKEW = -0.2f
        private const val LINE_TINT = 0.10f
        private const val RANGE_TINT = 0.24f
        private const val CURRENT_MATCH_TINT = 0.55f
        private const val FIRST_ACTION_ID = 0x00dc0000
        private val EDITING_ITEMS = intArrayOf(android.R.id.cut, android.R.id.paste, android.R.id.pasteAsPlainText, android.R.id.replaceText, android.R.id.undo, android.R.id.redo)

        fun markerTone(severity: MarkerSeverity): DecorationTone = when (severity) {
            MarkerSeverity.Error -> DecorationTone.Danger
            MarkerSeverity.Warning -> DecorationTone.Warning
            MarkerSeverity.Info -> DecorationTone.Accent
        }

        fun withAlpha(color: Int, alpha: Float): Int = (color and 0x00FFFFFF) or ((alpha * 255).roundToInt() shl 24)
    }
}
