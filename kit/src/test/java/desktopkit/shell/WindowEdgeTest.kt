package desktopkit.shell

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.DesktopKitRoot
import desktopkit.TestShell
import desktopkit.settings.rememberDeskPreferences
import desktopkit.theme.GlassMode
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.max

/**
 * Nothing of the content shows below the phone's bars: the bottom glass and the band behind the
 * system navigation reach the window's bottom edge, with three-button and gesture navigation, with
 * the bars shown and slid away by a scroll, under a screen that hides the bottom navigation, and
 * when the system's bottom insets do not agree with each other. Kitap Bulucu on a Galaxy S24
 * (1080 × 2340, density 3, three buttons) showed the list's next row, sharp, in the last pixel row
 * under the system's buttons.
 *
 * The window is drawn with the hardware renderer (PixelCopy) and the glass blurs as on a phone, so
 * the blur's own drawing is what is checked. The content is one green, which the chrome never is:
 * through the glass it is blurred and tinted to a dark tone, a pixel that is still clearly green
 * is content drawn without the chrome over it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WindowEdgeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var view: View

    /** The system bars: a status bar of [status] px, a navigation bar of [navigation] px, a bottom cutout of [cutout] px. */
    private fun systemBars(status: Int, navigation: Int, threeButtons: Boolean, cutout: Int = 0) {
        val builder = WindowInsetsCompat.Builder()
            .setInsets(Type.statusBars(), Insets.of(0, status, 0, 0))
            .setVisible(Type.statusBars(), true)
            .setInsets(Type.navigationBars(), Insets.of(0, 0, 0, navigation))
            .setVisible(Type.navigationBars(), true)
            .setInsets(Type.tappableElement(), Insets.of(0, status, 0, if (threeButtons) navigation else 0))
            .setInsets(Type.mandatorySystemGestures(), Insets.of(0, status, 0, navigation))
            .setInsets(Type.systemGestures(), Insets.of(if (threeButtons) 0 else 60, status, if (threeButtons) 0 else 60, navigation))
        if (cutout > 0) builder.setInsets(Type.displayCutout(), Insets.of(0, 0, 0, cutout))
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(view, builder.build()) }
        settle()
    }

    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    /** The phone shell in the dark palette, blurring, over a list that is green from end to end. */
    private fun phone(hideNavigation: Boolean = false) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            view = LocalView.current
            DesktopKitRoot(rememberDeskPreferences(defaultTheme = ThemeMode.Dark)) { widthClass ->
                TestShell(widthClass, bottomBar = {}, glassMode = GlassMode.Blur) { _, _ ->
                    if (hideNavigation) HideBottomNavigation()
                    val padding = rememberShellContentPadding()
                    LazyColumn(Modifier.fillMaxSize().testTag("list"), rememberLazyListState(10), padding) {
                        items(120) { Box(Modifier.fillMaxWidth().height(56.dp).background(CONTENT)) }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** The window as the hardware renderer draws it, from the content view's top left. */
    private fun capture(): Bitmap {
        val window = compose.activity.window
        val decor = window.decorView
        val full = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { PixelCopy.request(window, full, {}, Handler(Looper.getMainLooper())) }
        val at = IntArray(2).also { view.getLocationInWindow(it) }
        return Bitmap.createBitmap(full, at[0], at[1], view.width, view.height)
    }

    /** A pixel of the content seen without the chrome (or through too little of it). */
    private fun isContent(argb: Int): Boolean {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return g - max(r, b) > 90
    }

    private fun hex(argb: Int) = "#%06X".format(argb and 0xFFFFFF)

    /** No content pixel in rows [top] until the window's bottom, across the whole width. */
    private fun assertCoveredFrom(bitmap: Bitmap, top: Int, what: String) {
        val leaks = (top until bitmap.height).mapNotNull { y ->
            val xs = (0 until bitmap.width).filter { isContent(bitmap.getPixel(it, y)) }
            if (xs.isEmpty()) null else "row $y: ${xs.size} px from x ${xs.first()} (${hex(bitmap.getPixel(xs.first(), y))})"
        }
        val middle = bitmap.getPixel(bitmap.width / 2, (top + bitmap.height) / 2)
        println("$what: rows $top..${bitmap.height - 1}, chrome ${hex(middle)}, last row ${hex(bitmap.getPixel(bitmap.width / 2, bitmap.height - 1))}, right column ${hex(bitmap.getPixel(bitmap.width - 1, bitmap.height - 2))}")
        assertTrue("$what: the content shows under the chrome (${leaks.size} rows): ${leaks.take(4)}", leaks.isEmpty())
    }

    /** Above the shell: the status bar shows the root's background, never the content. */
    private fun assertStatusBarClear(bitmap: Bitmap, status: Int) {
        val leaks = (0 until status).count { y -> (0 until bitmap.width).any { isContent(bitmap.getPixel(it, y)) } }
        assertTrue("the content shows under the status bar in $leaks rows", leaks == 0)
    }

    private fun glassBottomReachesTheWindow(what: String) {
        val window = compose.onRoot().getBoundsInRoot()
        val glass = compose.onNodeWithTag(BOTTOM_GLASS_TAG, useUnmergedTree = true).getBoundsInRoot()
        assertTrue("$what: the bottom glass ends at ${glass.bottom}, the window at ${window.bottom}", glass.bottom >= window.bottom - 0.5.dp)
    }

    private fun glassTopPx(): Int = with(compose.density) {
        compose.onNodeWithTag(BOTTOM_GLASS_TAG, useUnmergedTree = true).getBoundsInRoot().top.roundToPx()
    }

    private fun scrollTheBarsAway() {
        compose.onNodeWithTag("list").performTouchInput { swipeUp(startY = centerY + 300f, endY = top + 200f, durationMillis = 800) }
        settle()
    }

    /** The S24 of the report: 1080 × 2340 at density 3, three-button navigation (48 dp). */
    @Test @Config(qualifiers = "tr-w360dp-h780dp-xxhdpi")
    fun threeButtonNavigation() {
        phone()
        systemBars(status = STATUS_PX, navigation = 144, threeButtons = true)
        glassBottomReachesTheWindow("bars shown")
        val shown = capture()
        assertStatusBarClear(shown, STATUS_PX)
        assertCoveredFrom(shown, glassTopPx() + RULE_PX, "three buttons, bars shown")

        scrollTheBarsAway()
        glassBottomReachesTheWindow("bars slid away")
        val hidden = capture()
        assertStatusBarClear(hidden, STATUS_PX)
        assertCoveredFrom(hidden, hidden.height - 144 + RULE_PX, "three buttons, bars slid away")
    }

    /** Gesture navigation: a 16 dp handle. */
    @Test @Config(qualifiers = "tr-w360dp-h780dp-xxhdpi")
    fun gestureNavigation() {
        phone()
        systemBars(status = STATUS_PX, navigation = 48, threeButtons = false)
        glassBottomReachesTheWindow("bars shown")
        assertCoveredFrom(capture(), glassTopPx() + RULE_PX, "gesture handle, bars shown")

        scrollTheBarsAway()
        glassBottomReachesTheWindow("bars slid away")
        val hidden = capture()
        assertCoveredFrom(hidden, hidden.height - 48 + RULE_PX, "gesture handle, bars slid away")
    }

    /** A wider phone (1440 px): the glass also reaches the window's right edge. */
    @Test @Config(qualifiers = "tr-w480dp-h1040dp-xxhdpi")
    fun widerPhone() {
        phone()
        systemBars(status = STATUS_PX, navigation = 144, threeButtons = true)
        glassBottomReachesTheWindow("bars shown")
        assertCoveredFrom(capture(), glassTopPx() + RULE_PX, "1440 px wide, bars shown")
    }

    /** A screen that hides the bottom navigation (a form): the band alone, down to the edge. */
    @Test @Config(qualifiers = "tr-w360dp-h780dp-xxhdpi")
    fun bottomNavigationHidden() {
        phone(hideNavigation = true)
        systemBars(status = STATUS_PX, navigation = 144, threeButtons = true)
        glassBottomReachesTheWindow("navigation hidden")
        val bitmap = capture()
        assertCoveredFrom(bitmap, bitmap.height - 144 + RULE_PX, "navigation hidden")
    }

    /** The system's bottom insets disagree (a bottom cutout taller than the navigation bar): the chrome covers the taller. */
    @Test @Config(qualifiers = "tr-w360dp-h780dp-xxhdpi")
    fun bottomInsetsThatDisagree() {
        phone()
        systemBars(status = STATUS_PX, navigation = 144, threeButtons = true, cutout = 150)
        glassBottomReachesTheWindow("cutout taller than the navigation bar")
        val bitmap = capture()
        assertCoveredFrom(bitmap, bitmap.height - 150, "cutout taller than the navigation bar")
    }

    private companion object {
        val CONTENT = Color(0xFF00FF00)
        const val STATUS_PX = 117 // 39 dp
        const val RULE_PX = 6 // the bar's top rule (1 dp) and a pixel either side
    }
}

