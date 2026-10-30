package desktopkit.components

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.withKeyDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.sampleArtwork
import desktopkit.screenshots.ShellScreenshotTest
import desktopkit.theme.DeskTheme
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The cover grid (K5): the table's selection rules in two dimensions, and its pictures. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1000dp-h700dp-mdpi")
class GridTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val albums = (1..24).map { "Albüm $it" }

    @Test
    fun shiftDownSelectsARowFurther() {
        var selection: Set<Any> = emptySet()
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Pointer) {
                var selected by remember { mutableStateOf<Set<Any>>(emptySet()) }
                DeskGrid(albums, key = { it }, onItemClick = { _, _ -> }, selectedKeys = selected,
                    onSelectionChange = { selected = it; selection = it }) { title ->
                    CoverCell(title, subtitle = "Sanatçı") { Box(Modifier.fillMaxSize().background(sampleArtwork[0])) }
                }
            }
        }
        compose.onNodeWithText("Albüm 1").requestFocus()
        compose.onNodeWithText("Albüm 1").performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionDown) } }
        compose.waitForIdle()
        // 960 dp of content fit six 136 dp columns: Shift+Down reaches the cell under the first one
        assertEquals(albums.take(7).toSet(), selection)
        compose.onNodeWithText("Albüm 7").performKeyInput { pressKey(Key.Escape) }
        compose.waitForIdle()
        assertEquals(emptySet<Any>(), selection)
    }

    private fun capture(name: String, input: InputMode, hover: String? = null, selecting: Boolean = false) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = input) {
                Box(Modifier.fillMaxSize().background(DeskTheme.colors.background)) {
                    DeskGrid(
                        albums, key = { it }, onItemClick = { _, _ -> },
                        selectedKeys = if (selecting) setOf("Albüm 2", "Albüm 5") else null,
                        onSelectionChange = if (selecting) ({ }) else null,
                        toggleOnClick = selecting,
                        itemMenu = { _, _ -> emptyList() },
                    ) { title ->
                        val i = albums.indexOf(title)
                        CoverCell(title, subtitle = "Deniz Aras · ${2015 + i % 10}", onPlay = {}, playLabel = "Oynat") {
                            Box(Modifier.fillMaxSize().background(sampleArtwork[i % sampleArtwork.size].copy(alpha = 0.55f)))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        if (hover != null) compose.onNodeWithText(hover).performMouseInput { moveTo(center) }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Test
    fun gridOnDex() = capture("grid-dark-pointer", InputMode.Pointer, hover = "Albüm 3")

    @Test
    @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun gridSelectingOnPhone() = capture("grid-dark-phone-selecting", InputMode.Touch, selecting = true)
}
