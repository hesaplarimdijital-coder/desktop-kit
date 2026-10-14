package desktopkit.shell

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.SampleDetailScreen
import desktopkit.TestShell
import desktopkit.components.DeskIcons
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class CompactTopBarTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var backs = 0
    private var fallbackBacks = 0
    private val actions = mutableListOf<String>()

    private fun showDetail(widthClass: WidthClass) {
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                TestShell(widthClass, startRoute = "documents", content = { _, _ ->
                    // registered before the top bar's handler, so it only runs when that one is absent
                    BackHandler { fallbackBacks++ }
                    SampleDetailScreen(onBack = { backs++ }, onAction = { actions += it })
                })
            }
        }
        compose.waitForIdle()
    }

    private fun pressSystemBack() {
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun compactShowsBackTitleActionsAndOverflow() {
        showDetail(WidthClass.Compact)
        compose.onNodeWithText("Sunum taslağı.odp").assertIsDisplayed()
        // the route's label stays only in the bottom bar
        compose.onAllNodesWithText("Belgeler").assertCountEquals(1)
        compose.onNodeWithContentDescription("Aç").assertIsDisplayed()
        compose.onNodeWithContentDescription("Listeye ekle").assertIsDisplayed()
        compose.onNodeWithContentDescription("Özellikler").assertDoesNotExist()
        compose.onNodeWithContentDescription("Search or run a command").assertIsDisplayed()

        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)

        compose.onNodeWithContentDescription("Aç").performClick()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Sil").assertIsDisplayed()
        compose.onNodeWithText("Özellikler").performClick()
        compose.waitForIdle()
        assertEquals(listOf("open", "info"), actions)
        compose.onNodeWithText("Sil").assertDoesNotExist()
    }

    @Test
    fun compactSystemBackCallsOnBack() {
        showDetail(WidthClass.Compact)
        pressSystemBack()
        assertEquals(1, backs)
        assertEquals(0, fallbackBacks)
    }

    @Test
    fun expandedIsUnchanged() {
        showDetail(WidthClass.Expanded)
        compose.onNodeWithText("Sunum taslağı.odp").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
        compose.onNodeWithContentDescription("Aç").assertDoesNotExist()
        compose.onNodeWithContentDescription("More options").assertDoesNotExist()
        compose.onNodeWithContentDescription("Search or run a command").assertIsDisplayed()
        // the rail's label and the top bar's title
        compose.onAllNodesWithText("Belgeler").assertCountEquals(2)
        pressSystemBack()
        assertEquals(0, backs)
        assertEquals(1, fallbackBacks)
    }

    @Test
    fun lastComposedWinsAndLeavingRestores() {
        var outer by mutableStateOf(true)
        var inner by mutableStateOf(true)
        var outerTitle by mutableStateOf("Dış")
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Light, inputMode = InputMode.Touch) {
                TestShell(WidthClass.Compact, startRoute = "documents", content = { _, _ ->
                    if (outer) CompactTopBar(title = outerTitle, showSearch = false)
                    if (inner) CompactTopBar(title = "İç", onBack = { backs++ }, backIcon = TopBarBackIcon.Close)
                    Text("içerik")
                })
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("İç").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").assertIsDisplayed()
        compose.onNodeWithContentDescription("Search or run a command").assertIsDisplayed()

        inner = false
        compose.waitForIdle()
        compose.onNodeWithText("Dış").assertIsDisplayed()
        compose.onNodeWithText("İç").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close").assertDoesNotExist()
        compose.onNodeWithContentDescription("Search or run a command").assertDoesNotExist()

        outerTitle = "Dış 2"
        compose.waitForIdle()
        compose.onNodeWithText("Dış 2").assertIsDisplayed()

        inner = true
        compose.waitForIdle()
        compose.onNodeWithText("İç").assertIsDisplayed()

        inner = false
        outer = false
        compose.waitForIdle()
        compose.onNodeWithText("Dış 2").assertDoesNotExist()
        compose.onAllNodesWithText("Belgeler").assertCountEquals(2)
        compose.onNodeWithContentDescription("Search or run a command").assertIsDisplayed()
        pressSystemBack()
        assertEquals(0, backs)
    }

    @Test
    fun widthClassIsProvided() {
        var widthClass by mutableStateOf(WidthClass.Compact)
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                TestShell(widthClass, content = { _, _ -> Text("class-${currentWidthClass()}") })
            }
        }
        for (next in WidthClass.entries) {
            widthClass = next
            compose.waitForIdle()
            compose.onNodeWithText("class-$next").assertExists()
        }
    }

    /** The animated transitions keep the navigation graph: state survives width changes and screen switches. */
    @Test
    fun defaultTransitionsKeepState() {
        var widthClass by mutableStateOf(WidthClass.Compact)
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                TestShell(widthClass, startRoute = "music", transitions = ShellTransitions.Default, content = { route, _ -> Counter(route) })
            }
        }
        repeat(2) { compose.onNodeWithTag("counter-music").performClick() }
        for (next in listOf(WidthClass.Medium, WidthClass.Large, WidthClass.Compact)) {
            widthClass = next
            compose.waitForIdle()
            compose.onNodeWithTag("counter-music").assertTextEquals("music: 2")
        }
        compose.onNodeWithText("Ana sayfa").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("counter-home").assertIsDisplayed()
        compose.onNodeWithTag("counter-music").assertDoesNotExist()
        compose.onNodeWithText("Müzik").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("counter-music").assertTextEquals("music: 2")
    }

    @Test
    fun overflowKeepsPrimaryActionsInTheBar() {
        fun action(label: String, primary: Boolean = false) = TopBarAction(DeskIcons.Info, label, {}, primary = primary)
        val three = listOf(action("a"), action("b"), action("c"))
        assertEquals(three to emptyList<TopBarAction>(), splitTopBarActions(three))

        val five = listOf(action("a"), action("b"), action("c"), action("d", primary = true), action("e"))
        val (shown, overflow) = splitTopBarActions(five)
        assertEquals(listOf("a", "d"), shown.map { it.label })
        assertEquals(listOf("b", "c", "e"), overflow.map { it.label })
    }
}

@Composable
private fun Counter(route: String) {
    var count by rememberSaveable { mutableIntStateOf(0) }
    Text("$route: $count", Modifier.testTag("counter-$route").clickable { count++ })
}
