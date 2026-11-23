package desktopkit.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.text.DynamicLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Typing in a long document costs what it costs in a short one: the platform's text layout lays out the
 * edited paragraph only, the syntax spans change on the lines in view. One character typed in the middle of
 * a 5,000-line, 350,000-character script, the window's layout pass and the editor drawn, against the same in
 * 50 lines. The times are printed for the record; the test asserts the incremental layout and bounds the
 * time loosely.
 *
 * The drawing here is software drawing on the build machine's JVM, whose JNI copies a text's whole character
 * array for every run of text drawn, so it grows with the document (about 12 ms for 50 lines, 35 ms for 5,000);
 * Android's runtime hands native code the array itself, and a phone draws text from display lists on its GPU.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w800dp-h600dp-mdpi")
class CodeEditorSizeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var document by mutableStateOf(CodeEditorState())

    /** What one keystroke and the frame after it cost: the edit itself, the layout pass and the drawing, in ms. */
    private class Cost(val edit: Double, val layout: Double, val draw: Double) {
        val total get() = edit + layout + draw
        override fun toString() = "%.1f ms (edit %.1f, layout %.1f, draw %.1f)".format(total, edit, layout, draw)
    }

    /** The median cost of a keystroke in the middle of [text], and the cheapest one. */
    private fun keystroke(text: String): Pair<Cost, Cost> {
        document = CodeEditorState(text)
        compose.waitForIdle()
        val view = compose.editorView()
        val middle = document.lineCount / 2
        compose.runOnUiThread {
            view.requestFocus()
            document.revealLine(middle)
        }
        compose.waitForIdle()
        assertTrue("the platform lays out the edited paragraph only", view.layout is DynamicLayout)
        val window = compose.activity.window.decorView
        val canvas = Canvas(Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888))
        val costs = ArrayList<Cost>()
        compose.runOnUiThread {
            val keyboard = view.inputConnection()
            val exactly = android.view.View.MeasureSpec.EXACTLY
            repeat(WARM_UP + MEASURED) { round ->
                val start = System.nanoTime()
                keyboard.commitText(if (round % 2 == 0) "x" else " ", 1)
                val edited = System.nanoTime()
                window.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(window.width, exactly),
                    android.view.View.MeasureSpec.makeMeasureSpec(window.height, exactly),
                )
                window.layout(0, 0, window.width, window.height)
                val laidOut = System.nanoTime()
                // what a frame draws of the editor: the rows in view, the gutter, the marks
                canvas.save()
                canvas.translate(-view.scrollX.toFloat(), -view.scrollY.toFloat())
                view.draw(canvas)
                canvas.restore()
                val drawn = System.nanoTime()
                if (round >= WARM_UP) costs += Cost((edited - start) / 1e6, (laidOut - edited) / 1e6, (drawn - laidOut) / 1e6)
            }
        }
        compose.waitForIdle()
        assertEquals("the view and the document hold the same text", view.text.toString(), document.text)
        val sorted = costs.sortedBy { it.total }
        return sorted[sorted.size / 2] to sorted.first()
    }

    @Test
    fun typingInALongDocumentCostsWhatItCostsInAShortOne() {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                CodeEditor(document, Modifier.fillMaxSize(), tokenizer = sampleTokenizer)
            }
        }
        val (short, shortBest) = keystroke(DocumentSizeTest.largeScript().lineSequence().take(50).joinToString("\n"))
        val (long, longBest) = keystroke(DocumentSizeTest.largeScript())
        println("CodeEditorSizeTest: keystroke and frame, 50 lines: median $short, fastest $shortBest")
        println("CodeEditorSizeTest: keystroke and frame, 5,000 lines (${document.length} characters): median $long, fastest $longBest")
        // an editor that lays out the whole text takes hundreds of milliseconds here
        assertTrue("a keystroke in 5,000 lines took $long", longBest.total < 120.0)
        assertTrue("the edit itself does not grow with the document: $shortBest, $longBest", longBest.edit < shortBest.edit * 3 + 5.0)
    }

    /**
     * What is not incremental, for the record (the review of PR #19 measured the same in this harness): a new text
     * size, a new width and another document each lay out every paragraph. At 5,000 lines that is a fraction of a
     * second here, so a pinch and quick zoom steps scale the drawing and lay out once (CodeEditorTest), and a
     * window resized by dragging re-lays out as the platform's text fields do. Bounds are loose; the numbers go to
     * the API file and the README.
     */
    @Test
    fun aNewSizeANewWidthAndAnotherDocumentAt5000Lines() {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                CodeEditor(document, Modifier.fillMaxSize(), tokenizer = sampleTokenizer)
            }
        }
        document = CodeEditorState(DocumentSizeTest.largeScript())
        compose.waitForIdle()
        val view = compose.editorView()
        val window = compose.activity.window.decorView
        val exactly = android.view.View.MeasureSpec.EXACTLY
        fun spec(size: Int) = android.view.View.MeasureSpec.makeMeasureSpec(size, exactly)
        fun layoutWindowAt(width: Int): Double {
            val start = System.nanoTime()
            window.measure(spec(width), spec(window.height))
            window.layout(0, 0, width, window.height)
            return (System.nanoTime() - start) / 1e6
        }
        fun layoutEditor(): Double {
            val start = System.nanoTime()
            view.measure(spec(view.width), spec(view.height))
            view.layout(view.left, view.top, view.right, view.bottom)
            return (System.nanoTime() - start) / 1e6
        }
        val sizes = ArrayList<Double>()
        val widths = ArrayList<Double>()
        compose.runOnUiThread {
            document.revealLine(2500)
            val width = window.width
            layoutWindowAt(width)
            layoutEditor()
            repeat(10) { round ->
                val size = view.textSize
                val start = System.nanoTime()
                view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, size * (if (round % 2 == 0) 1.1f else 1f / 1.1f))
                sizes += (System.nanoTime() - start) / 1e6 + layoutEditor()
            }
            repeat(6) { round -> widths += layoutWindowAt(if (round % 2 == 0) width - 16 else width) }
            layoutWindowAt(width)
        }
        compose.waitForIdle()
        val other = CodeEditorState(DocumentSizeTest.largeScript().replace("dalgalar", "rüzgâr"))
        val switchStart = System.nanoTime()
        document = other
        compose.waitForIdle()
        val switch = (System.nanoTime() - switchStart) / 1e6
        fun median(list: List<Double>) = list.sorted()[list.size / 2]
        println("CodeEditorSizeTest: 5,000 lines (${document.length} characters): a new text size %.0f ms, a new width %.0f ms, another document %.0f ms (medians; the document once)"
            .format(median(sizes), median(widths), switch))
        assertTrue(median(sizes) < 2_000.0 && median(widths) < 2_000.0 && switch < 5_000.0)
    }

    private companion object {
        const val WARM_UP = 5
        const val MEASURED = 30
    }
}
