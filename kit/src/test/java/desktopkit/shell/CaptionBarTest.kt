package desktopkit.shell

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.DesktopKitRoot
import desktopkit.TestShell
import desktopkit.screenshots.ShellScreenshotTest
import desktopkit.settings.DeskPreferences
import desktopkit.settings.rememberDeskPreferences
import desktopkit.theme.Accent
import desktopkit.theme.DeskPalette
import desktopkit.theme.DensityPreference
import desktopkit.theme.ThemeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * M1: in a desktop window the menu bar fills the caption bar, between the system's app chip and
 * window buttons, and the caption bar turns transparent.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class CaptionBarTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var view: View

    private fun px(dp: Dp): Int = with(compose.density) { dp.roundToPx() }

    @After
    fun noCaption() {
        CaptionElements.testCaption = null
    }

    private fun shell(theme: ThemeMode = ThemeMode.Dark) {
        // a mouse, as on DeX: the 40 dp pointer menu bar
        compose.activity.getSharedPreferences(DeskPreferences.FILE, Context.MODE_PRIVATE).edit()
            .putString(DeskPreferences.KEY_DENSITY, DensityPreference.Pointer.name).commit()
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            view = LocalView.current
            DesktopKitRoot(rememberDeskPreferences(defaultTheme = theme)) { widthClass ->
                TestShell(widthClass, content = { _, _ -> Box(Modifier.fillMaxSize().testTag("content")) })
            }
        }
        compose.waitForIdle()
    }

    private val chip = 120.dp
    private val buttons = 144.dp

    /**
     * What the system reports for a desktop window: a 40 dp caption bar with the app chip at its start
     * and the window buttons at its end; [reachesContent] false while the system still keeps an opaque
     * caption bar's insets from the content.
     */
    private fun captionBar(elements: Boolean = true, reachesContent: Boolean = true) {
        val width = compose.activity.window.decorView.width
        val height = px(40.dp)
        CaptionElements.testCaption = height to
            if (!elements) emptyList() else listOf(Rect(0, 0, px(chip), height), Rect(width - px(buttons), 0, width, height))
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.captionBar(), Insets.of(0, if (reachesContent) height else 0, 0, 0))
            .build()
        compose.runOnUiThread {
            ViewCompat.dispatchApplyWindowInsets(view, insets)
            // the window is laid out again, as the system does when its insets change
            view.requestLayout()
            view.measure(
                View.MeasureSpec.makeMeasureSpec(view.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(view.height, View.MeasureSpec.EXACTLY),
            )
            view.layout(view.left, view.top, view.right, view.bottom)
        }
        compose.waitForIdle()
    }

    private fun appearance(): Int {
        var value = 0
        compose.runOnIdle { value = compose.activity.window.insetsController!!.systemBarsAppearance }
        return value
    }

    @Test
    fun menusAndSearchLieBetweenTheSystemsElements() {
        shell()
        captionBar()
        val menu = compose.onNodeWithText("Dosya").getBoundsInRoot()
        assertTrue("the first menu clears the app chip: ${menu.left}", menu.left >= chip)
        assertTrue("the menus sit in the caption bar: ${menu.top}..${menu.bottom}", menu.bottom <= 40.dp)
        val search = compose.onNodeWithText("Ctrl+K").getBoundsInRoot()
        assertTrue("the search field ends before the window buttons: ${search.right}", search.right <= 1280.dp - buttons)
        // the app chip names the app; the menu bar does not repeat it
        compose.onNodeWithText("Örnek").assertDoesNotExist()
        // the content starts under the menu bar (40 dp and its rule), not under a second bar
        val content = compose.onNodeWithTag("content").getBoundsInRoot()
        assertEquals(41f, content.top.value, 1f)
        val flags = appearance()
        assertTrue("transparent caption bar", flags and WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND != 0)
        assertEquals("light window buttons on the dark theme", 0, flags and WindowInsetsController.APPEARANCE_LIGHT_CAPTION_BARS)
        screenshot("dex-caption-dark")
    }

    @Test
    fun darkButtonsOnTheLightTheme() {
        shell(ThemeMode.Light)
        captionBar()
        assertTrue(appearance() and WindowInsetsController.APPEARANCE_LIGHT_CAPTION_BARS != 0)
        screenshot("dex-caption-light")
    }

    @Test
    fun withoutElementsTheSystemKeepsItsCaptionBar() {
        shell()
        captionBar(elements = false)
        // the root keeps the content below the caption bar, which the system draws
        val menu = compose.onNodeWithText("Dosya").getBoundsInRoot()
        assertTrue("menu bar below the caption bar: ${menu.top}", menu.top >= 40.dp)
        compose.onNodeWithText("Örnek").assertExists()
        assertEquals(0, appearance() and WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND)
    }

    @Test
    fun anOpaqueCaptionBarIsNotFilledYet() {
        shell()
        // asked for transparency, but the system still keeps the caption bar from the content
        captionBar(reachesContent = false)
        assertTrue(appearance() and WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND != 0)
        val menu = compose.onNodeWithText("Dosya").getBoundsInRoot()
        assertTrue("no room left for elements that are not over the bar: ${menu.left}", menu.left < chip)
        compose.onNodeWithText("Örnek").assertExists()
    }

    @Test
    @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun theRailLeavesTheCaptionBarToTheSystem() {
        shell()
        captionBar()
        assertEquals(0, appearance() and WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND)
        val content = compose.onNodeWithTag("content").getBoundsInRoot()
        assertTrue("content below the caption bar: ${content.top}", content.top >= 40.dp)
    }

    /** The window, with the system's elements sketched where the system would draw them. */
    private fun screenshot(name: String) {
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        val canvas = Canvas(bitmap)
        val dark = name.contains("dark")
        val ink = (if (dark) DeskPalette.dark(Accent.Blue) else DeskPalette.light(Accent.Blue)).textSecondary.toArgb()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; strokeWidth = 1.5f; style = Paint.Style.STROKE; textSize = 13f }
        val mid = px(40.dp) / 2f
        // the app chip: icon, name, chevron
        canvas.drawCircle(22f, mid, 9f, paint)
        paint.style = Paint.Style.FILL
        canvas.drawText("Örnek  ⌄", 38f, mid + 4.5f, paint)
        // minimize, maximize, close
        val right = window.width.toFloat()
        paint.style = Paint.Style.STROKE
        canvas.drawLine(right - 124f, mid, right - 112f, mid, paint)
        canvas.drawRect(RectF(right - 80f, mid - 6f, right - 68f, mid + 6f), paint)
        canvas.drawLine(right - 36f, mid - 6f, right - 24f, mid + 6f, paint)
        canvas.drawLine(right - 36f, mid + 6f, right - 24f, mid - 6f, paint)
        ShellScreenshotTest.save(name, bitmap)
    }
}
