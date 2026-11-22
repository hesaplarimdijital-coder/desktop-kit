package desktopkit.editor

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import desktopkit.theme.SyntaxRole

/** A script in a small made-up language: sections, choices, jumps, tags, logic in braces, comments. */
val sampleScript = """
    // Çay bahçesi: ilk sahne
    VAR cay = 2
    VAR sehir = "Kemer"

    == bahce ==
    Sabah erkenden bahçeye indim. Masaların üstü hâlâ ıslaktı; garson çocuk bezle siliyor, bir yandan da radyodaki türküye eşlik ediyordu. # sabah
    * "Bir çay alabilir miyim?" -> cay_soyle
    * Denize doğru yürüdüm. -> sahil
    + { cay > 0 } Bardağı bitirdim.
        ~ cay = cay - 1
        -> bahce

    == cay_soyle ==
    /* Garson burada iki kez
       gelip gidiyor. */
    Çay geldi (demli, { cay } şeker). -> bahce

    == sahil ==
    Dalgalar [taşlara] vuruyordu. -> END
""".trimIndent()

private val keywords = setOf("VAR", "CONST", "INCLUDE", "END", "DONE")
private const val IN_COMMENT = 1

/** The sample language's colours; a block comment reaches over lines through the state. */
val sampleTokenizer = LineTokenizer { line, state, tokens ->
    val length = line.length
    var i = 0
    var inComment = state == IN_COMMENT
    var first = 0
    while (first < length && line[first].isWhitespace()) first++
    if (!inComment && line.startsWith("==", first)) {
        tokens.add(first, length, SyntaxRole.Heading)
        return@LineTokenizer 0
    }
    while (i < length) {
        if (inComment) {
            val end = line.indexOf("*/", i)
            if (end < 0) {
                tokens.add(i, length, SyntaxRole.Comment)
                return@LineTokenizer IN_COMMENT
            }
            tokens.add(i, end + 2, SyntaxRole.Comment)
            i = end + 2
            inComment = false
            continue
        }
        val c = line[i]
        when {
            line.startsWith("//", i) -> {
                tokens.add(i, length, SyntaxRole.Comment)
                i = length
            }
            line.startsWith("/*", i) -> {
                val end = line.indexOf("*/", i + 2)
                if (end < 0) {
                    tokens.add(i, length, SyntaxRole.Comment)
                    return@LineTokenizer IN_COMMENT
                }
                tokens.add(i, end + 2, SyntaxRole.Comment)
                i = end + 2
            }
            line.startsWith("->", i) -> {
                tokens.add(i, i + 2, SyntaxRole.Operator)
                var from = i + 2
                while (from < length && line[from] == ' ') from++
                var to = from
                while (to < length && (line[to].isLetterOrDigit() || line[to] == '_' || line[to] == '.')) to++
                if (to > from) tokens.add(from, to, if (line.substring(from, to) in keywords) SyntaxRole.Keyword else SyntaxRole.Link)
                i = maxOf(to, i + 2)
            }
            c == '#' -> {
                tokens.add(i, length, SyntaxRole.Tag)
                i = length
            }
            c == '"' -> {
                val close = line.indexOf('"', i + 1)
                val end = if (close < 0) length else close + 1
                tokens.add(i, end, SyntaxRole.String)
                i = end
            }
            c.isDigit() -> {
                var end = i
                while (end < length && (line[end].isDigit() || line[end] == '.')) end++
                tokens.add(i, end, SyntaxRole.Number)
                i = end
            }
            (c == '*' || c == '+' || c == '~') && i == first -> {
                tokens.add(i, i + 1, SyntaxRole.Keyword)
                i++
            }
            c in "{}()[]|:," -> {
                tokens.add(i, i + 1, SyntaxRole.Punctuation)
                i++
            }
            c.isLetter() || c == '_' -> {
                var end = i
                while (end < length && (line[end].isLetterOrDigit() || line[end] == '_')) end++
                when (line.substring(i, end)) {
                    in keywords -> tokens.add(i, end, SyntaxRole.Keyword)
                    "true", "false" -> tokens.add(i, end, SyntaxRole.Constant)
                }
                i = end
            }
            else -> i++
        }
    }
    // an empty line inside a comment is still inside it
    if (inComment) IN_COMMENT else 0
}

/** The editor's view in [this] activity. */
internal fun <A : ComponentActivity> AndroidComposeTestRule<ActivityScenarioRule<A>, A>.editorView(): CodeEditorView {
    waitForIdle()
    return checkNotNull(find(activity.window.decorView)) { "no CodeEditorView in the window" }
}

private fun find(view: View): CodeEditorView? {
    if (view is CodeEditorView) return view
    if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
    return null
}

/** The connection an input method gets to the editor: what an on-screen keyboard types through. */
internal fun CodeEditorView.inputConnection(): InputConnection = checkNotNull(onCreateInputConnection(EditorInfo()))

/** A hardware key pressed and released on the editor. */
internal fun CodeEditorView.press(keyCode: Int, meta: Int = 0) {
    val now = android.os.SystemClock.uptimeMillis()
    dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
    dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta))
}
