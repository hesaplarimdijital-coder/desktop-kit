package desktopkit.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.TestShell
import desktopkit.editor.CodeDecoration
import desktopkit.editor.CodeEditor
import desktopkit.editor.CodeEditorState
import desktopkit.editor.CodeSearchBar
import desktopkit.editor.CodeSearchState
import desktopkit.editor.DecorationKind
import desktopkit.editor.DecorationTone
import desktopkit.editor.LineMarker
import desktopkit.editor.MarkerSeverity
import desktopkit.editor.editorView
import desktopkit.editor.sampleScript
import desktopkit.editor.sampleTokenizer
import desktopkit.shell.WidthClass
import desktopkit.shell.rememberShellContentPadding
import desktopkit.theme.DeskTheme
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The code editor with a script in the sample language: syntax colours in the three themes, the
 * gutter with an error and a warning, a selection, marked ranges, a matched bracket; with a mouse
 * on a wide window and with a finger on a phone, where the text runs under the shell's bars.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val markers = listOf(
        LineMarker(6, MarkerSeverity.Error, "“cay_soyle” diye bir bölüm yok"),
        LineMarker(8, MarkerSeverity.Warning, "Bu seçenek hiç görünmez"),
        LineMarker(15, MarkerSeverity.Info, "Burada bitiyor"),
    )

    private fun capture(
        name: String,
        theme: ThemeMode,
        input: InputMode,
        prepare: (CodeEditorState) -> Unit = {},
        ready: () -> Boolean = { true },
        content: @Composable BoxScope.(CodeEditorState) -> Unit,
    ) {
        val state = CodeEditorState(sampleScript)
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = theme, inputMode = input) {
                Box(Modifier.fillMaxSize().background(DeskTheme.colors.background)) { content(state) }
            }
        }
        val view = compose.editorView()
        compose.runOnUiThread {
            view.requestFocus()
            prepare(state)
        }
        awaitIdle(ready)
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    /**
     * Lets the main thread and the work it waits for (the search runs on another thread) go on until [ready].
     * Robolectric runs the main thread's messages only when the test lets it idle.
     */
    private fun awaitIdle(ready: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        compose.waitForIdle()
        while (!ready()) {
            check(System.currentTimeMillis() < until) { "not ready after 5 s" }
            Thread.sleep(10)
            compose.waitForIdle()
        }
    }

    /** The exact place of the error, underlined; the occurrences of a name, tinted. */
    private fun decorations(state: CodeEditorState): List<CodeDecoration> {
        val target = state.text.indexOf("cay_soyle")
        val names = Regex("\\bcay\\b").findAll(state.text).map { CodeDecoration(it.range.first, it.range.last + 1, DecorationTone.Neutral) }.toList()
        return names + CodeDecoration(target, target + "cay_soyle".length, DecorationTone.Danger, DecorationKind.Underline)
    }

    @Composable
    private fun Editor(state: CodeEditorState, modifier: Modifier = Modifier.fillMaxSize(), softWrap: Boolean = true) {
        CodeEditor(state, modifier, tokenizer = sampleTokenizer, markers = markers, decorations = decorations(state), softWrap = softWrap)
    }

    /** The cursor after the closing brace of line 9: the brace and its partner are framed, the line is tinted. */
    private fun atBrace(state: CodeEditorState) = state.select(state.text.indexOf("} Bardağı") + 1)

    @Test @Config(qualifiers = "tr-w1000dp-h640dp-mdpi")
    fun darkPointer() = capture("editor-dark-pointer", ThemeMode.Dark, InputMode.Pointer, ::atBrace) { Editor(it) }

    @Test @Config(qualifiers = "tr-w1000dp-h640dp-mdpi")
    fun lightPointer() = capture("editor-light-pointer", ThemeMode.Light, InputMode.Pointer, ::atBrace) { Editor(it) }

    @Test @Config(qualifiers = "tr-w1000dp-h640dp-mdpi")
    fun oledPointer() = capture("editor-oled-pointer", ThemeMode.Oled, InputMode.Pointer, ::atBrace) { Editor(it) }

    /** A selection over three lines, with a finger's sizes on a tablet. */
    @Test @Config(qualifiers = "tr-w900dp-h700dp-mdpi")
    fun darkTouchSelection() = capture("editor-dark-touch-selection", ThemeMode.Dark, InputMode.Touch,
        prepare = { it.select(it.lineStart(5) + 6, it.lineStart(7) + 8) }) { Editor(it) }

    /** Long lines left unwrapped: the text scrolls sideways under the gutter. */
    @Test @Config(qualifiers = "tr-w1000dp-h640dp-mdpi")
    fun lightPointerUnwrapped() = capture("editor-light-pointer-nowrap", ThemeMode.Light, InputMode.Pointer,
        prepare = { it.select(it.lineEnd(5)) }) { Editor(it, softWrap = false) }

    /** A phone: the editor fills the shell's content and its text runs under the glass bars. */
    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phoneDark() = capture("editor-phone-dark", ThemeMode.Dark, InputMode.Touch, ::atBrace) { state ->
        TestShell(WidthClass.Compact, content = { _, _ ->
            CodeEditor(state, Modifier.fillMaxSize(), tokenizer = sampleTokenizer, markers = markers, decorations = decorations(state),
                contentPadding = rememberShellContentPadding())
        })
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phoneLight() = capture("editor-phone-light", ThemeMode.Light, InputMode.Touch, ::atBrace) { state ->
        TestShell(WidthClass.Compact, content = { _, _ ->
            CodeEditor(state, Modifier.fillMaxSize(), tokenizer = sampleTokenizer, markers = markers, decorations = decorations(state),
                contentPadding = rememberShellContentPadding())
        })
    }

    private val search = CodeSearchState()

    /** The bar above the editor, the replace row open, "cay" found: every match marked, the current one stronger. */
    private fun findCay(state: CodeEditorState) {
        val first = state.text.indexOf("cay")
        state.select(first, first + 3)
        search.open(replace = true)
        search.replacement = "çay"
    }

    @Composable
    private fun SearchedEditor(state: CodeEditorState, padding: PaddingValues = PaddingValues(0.dp)) {
        Column(Modifier.fillMaxSize()) {
            CodeSearchBar(search, Modifier.padding(top = padding.calculateTopPadding()))
            CodeEditor(state, Modifier.weight(1f), tokenizer = sampleTokenizer, markers = markers, search = search,
                contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()))
        }
    }

    @Test @Config(qualifiers = "tr-w1000dp-h640dp-mdpi")
    fun searchDarkPointer() = capture("editor-search-dark-pointer", ThemeMode.Dark, InputMode.Pointer, ::findCay,
        ready = { search.matchCount > 0 }) { SearchedEditor(it) }

    /** A phone: the bar below the shell's top bar, its options and replace buttons on rows of their own. */
    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun searchPhoneLight() = capture("editor-search-phone-light", ThemeMode.Light, InputMode.Touch, ::findCay,
        ready = { search.matchCount > 0 }) { state ->
        TestShell(WidthClass.Compact, content = { _, _ -> SearchedEditor(state, rememberShellContentPadding()) })
    }
}

