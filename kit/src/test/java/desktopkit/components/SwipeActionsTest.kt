package desktopkit.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** DeskMail's request 7: a finger slides a list row sideways to archive it or mark it read. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-mdpi")
class SwipeActionsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val done = mutableListOf<String>()

    private fun row(inputMode: InputMode) {
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = inputMode) {
                Column {
                    DeskListRow(
                        "Toplantı notları",
                        subtitle = "Ayşe Yılmaz",
                        onClick = { done += "açıldı" },
                        swipeStart = SwipeAction("Arşivle", DeskIcons.Folder, { done += "arşiv" }),
                        swipeEnd = SwipeAction("Okundu", DeskIcons.Check, { done += "okundu" }, removesRow = false),
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private val node get() = compose.onNode(hasText("Toplantı notları"))

    @Test
    fun aLongSlideTowardTheEndArchives() {
        row(InputMode.Touch)
        node.performTouchInput { swipeRight(startX = left + 10f, endX = right - 10f, durationMillis = 300) }
        compose.waitForIdle()
        assertEquals(listOf("arşiv"), done)
    }

    @Test
    fun aShortSlideComesBackAndDoesNothing() {
        row(InputMode.Touch)
        node.performTouchInput { swipeLeft(startX = right - 10f, endX = right - 60f, durationMillis = 400) }
        compose.waitForIdle()
        assertEquals(emptyList<String>(), done)
        node.performTouchInput { swipeLeft(startX = right - 10f, endX = left + 10f, durationMillis = 300) }
        compose.waitForIdle()
        assertEquals(listOf("okundu"), done)
    }

    @Test
    fun theMouseDoesNotSwipeButTalkBackHasTheActions() {
        row(InputMode.Pointer)
        node.performTouchInput { swipeRight(startX = left + 10f, endX = right - 10f, durationMillis = 300) }
        compose.waitForIdle()
        // the row does not slide: no swipe action ran (a press that stays on the row is its click)
        assertEquals(emptyList<String>(), done.filter { it != "açıldı" })
        done.clear()
        val actions = node.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Arşivle", "Okundu"), actions.map { it.label })
        compose.runOnUiThread { actions.first().action() }
        assertEquals(listOf("arşiv"), done)
    }
}
