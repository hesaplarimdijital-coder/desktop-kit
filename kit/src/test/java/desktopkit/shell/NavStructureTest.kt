package desktopkit.shell

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.rightClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.components.DeskIcons
import desktopkit.components.MenuEntry
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DeskMail's request 3: the navigation of a mail app, whose folders come and go and sit in each other — a new
 * folder keeps the open screen and its state, folders only in the sidebar and "Folders" only on the phone and
 * the rail, a folder's tab marked while it is open, indented folders, an account that shuts, an entry's menu.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp")
class NavStructureTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val phoneAndRail = setOf(NavKind.BottomBar, NavKind.Rail)
    private val sidebar = setOf(NavKind.Sidebar)

    private fun mail(archive: Boolean = false, drafts: Boolean = true) = ShellSpec(
        appTitle = "Posta",
        sections = listOf(
            NavSection(
                null,
                listOf(
                    NavItem("inbox", "Gelen", DeskIcons.Home, parent = "folders"),
                    NavItem("folders", "Klasörler", DeskIcons.Folder, showIn = phoneAndRail),
                    NavItem("search", "Ara", DeskIcons.Search, showIn = phoneAndRail),
                ),
            ),
            NavSection(
                "ayse@ornek.org",
                listOfNotNull(
                    NavItem("sent", "Gönderilmiş", DeskIcons.Folder, parent = "folders", showIn = sidebar),
                    NavItem("drafts", "Taslaklar", DeskIcons.Folder, parent = "folders", showIn = sidebar).takeIf { drafts },
                    NavItem("projects", "Projeler", DeskIcons.Folder, parent = "folders", showIn = sidebar),
                    NavItem("p2026", "2026", DeskIcons.Folder, parent = "projects", showIn = sidebar),
                    NavItem("archive", "Arşiv", DeskIcons.Folder, parent = "folders", showIn = sidebar).takeIf { archive },
                ),
                collapsible = true,
            ),
        ),
        footer = NavItem("settings", "Ayarlar", DeskIcons.Settings),
    )

    @Test
    fun whatEachChromeShowsAndMarks() {
        val spec = mail()
        assertEquals(listOf("inbox", "folders", "search"), spec.bottomBarRoutes)
        assertEquals(listOf("inbox", "sent", "drafts", "projects", "p2026", "settings"), spec.shown(NavKind.Sidebar).map { it.route })
        // the folders are on no phone's "More": the "Folders" screen lists them
        assertEquals(listOf("settings"), spec.overflow(NavKind.BottomBar).map { it.route })
        assertEquals(emptyList<String>(), spec.overflow(NavKind.Sidebar).map { it.route })
        // a folder open on a phone marks "Folders", a folder in a folder too; the sidebar marks the folder
        assertEquals("folders", spec.marked(NavKind.BottomBar, "sent"))
        assertEquals("folders", spec.marked(NavKind.BottomBar, "p2026"))
        assertEquals("p2026", spec.marked(NavKind.Sidebar, "p2026"))
        assertEquals(ShellSpec.MORE_ROUTE, spec.marked(NavKind.BottomBar, "settings"))
        assertNull("the sidebar has no entry for the phone's Folders screen", spec.marked(NavKind.Sidebar, "folders"))
        assertEquals(0, spec.depth(spec.item("sent")!!))
        assertEquals(1, spec.depth(spec.item("p2026")!!))
    }

    @Test
    fun aParentMustBeThereAndNotGoRound() {
        assertThrows(IllegalArgumentException::class.java) {
            ShellSpec("App", listOf(NavSection(null, listOf(NavItem("a", "A", DeskIcons.Home, parent = "missing")))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShellSpec("App", listOf(NavSection(null, listOf(
                NavItem("a", "A", DeskIcons.Home, parent = "b"),
                NavItem("b", "B", DeskIcons.Home, parent = "a"),
            ))))
        }
    }

    private var spec by mutableStateOf(mail())
    private var widthClass by mutableStateOf(WidthClass.Compact)
    private lateinit var navigator: ShellNavigator
    private lateinit var navController: NavHostController
    private val menuOpened = mutableListOf<String>()

    private fun shell() {
        compose.setContent {
            navController = rememberNavController()
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = if (widthClass == WidthClass.Compact) InputMode.Touch else InputMode.Pointer) {
                DesktopShell(
                    spec = spec,
                    widthClass = widthClass,
                    navController = navController,
                    transitions = ShellTransitions.None,
                    navMenu = { route ->
                        if (route == "sent") listOf(MenuEntry.Item("Tümünü okundu say", { menuOpened += route })) else emptyList()
                    },
                ) { route, nav ->
                    navigator = nav
                    var count by rememberSaveable { mutableIntStateOf(0) }
                    Text("ekran $route: $count", Modifier.clickable { count++ })
                }
            }
        }
        compose.waitForIdle()
    }

    private fun entry(label: String) = compose.onNode(hasText(label) and hasClickAction() or (hasContentDescription(label) and hasClickAction()))

    @Test
    fun aNewFolderKeepsTheOpenScreenAndItsState() {
        widthClass = WidthClass.Large
        shell()
        compose.onNodeWithText("Gönderilmiş").performClick()
        compose.waitForIdle()
        repeat(3) { compose.onNodeWithText("ekran sent: ${it}").performClick() }
        compose.onNodeWithText("ekran sent: 3").assertExists()
        // the server has a new folder
        spec = mail(archive = true)
        compose.waitForIdle()
        compose.onNodeWithText("ekran sent: 3").assertExists()
        // and it opens
        compose.onNodeWithText("Arşiv").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("ekran archive: 0").assertExists()
        // back to the folder before: its state is still there
        compose.onNodeWithText("Gönderilmiş").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("ekran sent: 3").assertExists()
    }

    @Test
    fun aFolderThatIsGoneSendsItsScreenToTheStart() {
        widthClass = WidthClass.Large
        shell()
        compose.onNodeWithText("Taslaklar").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("ekran drafts: 0").assertExists()
        spec = mail(drafts = false)
        compose.waitForIdle()
        compose.onNodeWithText("ekran inbox: 0").assertExists()
        assertTrue(navController.graph.findNode("drafts") == null)
    }

    @Test
    fun aFolderOpenedFromFoldersMarksFoldersAndBackComesBack() {
        shell()
        compose.onNodeWithText("Klasörler").performClick()
        compose.waitForIdle()
        compose.runOnUiThread { navigator.open("sent") }
        compose.waitForIdle()
        compose.onNodeWithText("ekran sent: 0").assertExists()
        entry("Klasörler").assertIsSelected()
        entry("Gelen").assertIsNotSelected()
        // the page has its Back button; it comes back to the Folders screen
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("ekran folders: 0").assertExists()
        // the tab's entry again from a folder: back to the Folders screen too
        compose.runOnUiThread { navigator.open("p2026") }
        compose.waitForIdle()
        entry("Klasörler").assertIsSelected()
        entry("Klasörler").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("ekran folders: 0").assertExists()
    }

    @Test
    fun anAccountShutsAndItsFoldersStepIn() {
        widthClass = WidthClass.Large
        shell()
        compose.onNodeWithText("Gönderilmiş").performClick()
        compose.waitForIdle()
        val header = compose.onNode(hasText("ayse@ornek.org") and hasClickAction())
        header.performSemanticsAction(SemanticsActions.Collapse)
        compose.waitForIdle()
        compose.onNodeWithText("Projeler").assertDoesNotExist()
        // the open folder stays, so the sidebar still says where the user is
        compose.onNodeWithText("Gönderilmiş").assertExists()
        header.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))
        header.performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Projeler").assertExists()
        // a folder in a folder is indented a step
        val projects = compose.onNodeWithText("Projeler", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left
        val year = compose.onNodeWithText("2026", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left
        assertTrue("2026 sits under Projeler: $year > $projects", year > projects)
    }

    @Test
    fun anEntrysMenuOpensWithARightClickAndForTalkBack() {
        widthClass = WidthClass.Large
        shell()
        entry("Gönderilmiş").performMouseInput { rightClick(center) }
        compose.waitForIdle()
        compose.onNodeWithText("Tümünü okundu say").performClick()
        compose.waitForIdle()
        assertEquals(listOf("sent"), menuOpened)
        // an entry without entries has no menu action
        entry("Projeler").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.CustomActions))
        entry("Gönderilmiş").assert(SemanticsMatcher("has the menu action") { node ->
            node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().isNotEmpty()
        })
    }
}
