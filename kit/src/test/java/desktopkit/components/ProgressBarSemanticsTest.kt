package desktopkit.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What TalkBack hears from a progress bar: a bar with an amount says its percentage (named only
 * when the app names it), a bar without one says "Loading" unless the app says something better.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class ProgressBarSemanticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun bars() {
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Touch) {
                Column {
                    DeskProgressBar(0.4f, Modifier.testTag("amount"))
                    DeskProgressBar(null, Modifier.testTag("unknown"))
                    DeskProgressBar(0.4f, Modifier.testTag("named amount"), contentDescription = "Kopyalanıyor")
                    DeskProgressBar(null, Modifier.testTag("named unknown"), contentDescription = "Bağlanıyor")
                    DeskListRow(title = "Yedek.zip", subtitle = "Kopyalanıyor", progress = 0.5f)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun name(tag: String): String? =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")

    @Test
    fun aBarWithAnAmountSaysItsPercentageOnly() {
        bars()
        assertNull(name("amount"))
        compose.onNodeWithTag("amount").assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(0.4f, 0f..1f)))
    }

    @Test
    fun aBarWithoutAnAmountSaysLoading() {
        bars()
        assertEquals("Yükleniyor", name("unknown"))
        compose.onNodeWithTag("unknown").assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate))
    }

    @Test
    fun theAppsNameWinsEitherWay() {
        bars()
        assertEquals("Kopyalanıyor", name("named amount"))
        assertEquals("Bağlanıyor", name("named unknown"))
    }

    @Test
    fun aListRowsCopyIsNotCalledLoading() {
        bars()
        // only the one bar without an amount and without a name says it
        assertEquals(1, compose.onAllNodesWithContentDescription("Yükleniyor", useUnmergedTree = true).fetchSemanticsNodes().size)
    }
}
