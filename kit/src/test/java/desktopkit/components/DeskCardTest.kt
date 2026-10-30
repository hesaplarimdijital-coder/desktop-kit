package desktopkit.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * [DeskCard]'s verticalArrangement arranges the card's column as any Column does: the items are
 * spaced, the title's gap grows by the same amount, and alignments still reach the card's edges.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "tr-w600dp-h800dp-mdpi")
class DeskCardTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theArrangementSpacesTheItemsAndKeepsTheCardsEdges() {
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Pointer) {
                Column {
                    DeskCard(Modifier.fillMaxWidth(), title = "Aralıklı", verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(20.dp).testTag("a"))
                        Box(Modifier.size(20.dp).testTag("b"))
                        Box(Modifier.size(20.dp).align(Alignment.End).testTag("end a"))
                    }
                    DeskCard(Modifier.fillMaxWidth(), title = "Bitişik") {
                        Box(Modifier.size(20.dp).testTag("c"))
                        Box(Modifier.size(20.dp).testTag("d"))
                        Box(Modifier.size(20.dp).align(Alignment.End).testTag("end c"))
                    }
                }
            }
        }
        compose.waitForIdle()
        val a = compose.onNodeWithTag("a").getBoundsInRoot()
        val b = compose.onNodeWithTag("b").getBoundsInRoot()
        val c = compose.onNodeWithTag("c").getBoundsInRoot()
        val d = compose.onNodeWithTag("d").getBoundsInRoot()
        assertEquals("spaced items", 10f, (b.top - a.bottom).value, 0.5f)
        assertEquals("items without an arrangement touch", 0f, (d.top - c.bottom).value, 0.5f)
        val spacedTitle = compose.onNodeWithText("Aralıklı").getBoundsInRoot()
        val plainTitle = compose.onNodeWithText("Bitişik").getBoundsInRoot()
        assertEquals("the title's gap grows by the spacing", (c.top - plainTitle.bottom).value + 10f, (a.top - spacedTitle.bottom).value, 0.5f)
        assertEquals("an item aligned to the end reaches the card's edge in both",
            compose.onNodeWithTag("end c").getBoundsInRoot().right.value, compose.onNodeWithTag("end a").getBoundsInRoot().right.value, 0.5f)
    }
}
