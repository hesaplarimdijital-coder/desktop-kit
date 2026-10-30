package desktopkit.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * On a phone a tap on a cover or a table row opens it and selects nothing (the S24 showed "1 of 18
 * selected" and check circles on every cover after opening an album); picking items is for the
 * select mode, or goes on once some are selected.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class TouchSelectionTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var selection by mutableStateOf<Set<Any>>(emptySet())
    private var selectMode by mutableStateOf(false)
    private val opened = mutableListOf<String>()

    private val albums = listOf("Kış Bahçesi", "Mavi Hat", "Gece Treni")

    private fun grid() {
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Touch) {
                DeskGrid(
                    albums,
                    key = { it },
                    onItemClick = { album, _ -> opened += album },
                    selectedKeys = selection,
                    onSelectionChange = { selection = it },
                    toggleOnClick = selectMode,
                    itemMenu = { _, _ -> listOf(MenuEntry.Item("Sıraya ekle", {})) },
                ) { title ->
                    CoverCell(title) { Box(Modifier.fillMaxSize().background(Color.Gray)) }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun tap(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    @Test
    fun aTapOnACoverOpensItAndSelectsNothing() {
        grid()
        tap("Mavi Hat")
        assertEquals(listOf("Mavi Hat"), opened)
        assertEquals(emptySet<Any>(), selection)
    }

    @Test
    fun aLongPressOpensTheMenuAndSelectsNothing() {
        grid()
        compose.onNodeWithText("Mavi Hat").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Sıraya ekle").assertExists()
        assertEquals(emptySet<Any>(), selection)
    }

    @Test
    fun inTheSelectModeTapsPickAndDrop() {
        selectMode = true
        grid()
        tap("Mavi Hat")
        tap("Gece Treni")
        assertEquals(setOf<Any>("Mavi Hat", "Gece Treni"), selection)
        tap("Mavi Hat")
        assertEquals(setOf<Any>("Gece Treni"), selection)
        assertEquals(emptyList<String>(), opened)
    }

    @Test
    fun onceSomethingIsPickedATapPicksToo() {
        selection = setOf("Kış Bahçesi")
        grid()
        tap("Mavi Hat")
        assertEquals(setOf<Any>("Kış Bahçesi", "Mavi Hat"), selection)
        assertEquals(emptyList<String>(), opened)
    }

    @Test
    fun aTapOnATableRowOpensItAndSelectsNothing() {
        compose.setContent {
            DesktopKitTheme(inputMode = InputMode.Touch) {
                DenseTable(
                    columns = listOf(TableColumn<String>("Ad", ColumnWidth.Weight(1f)) { it }),
                    rows = listOf("Yedek.zip", "Kayıt 14.flac"),
                    key = { it },
                    onRowClick = { row, _ -> opened += row },
                    selectedKeys = selection,
                    onSelectionChange = { selection = it },
                )
            }
        }
        compose.waitForIdle()
        tap("Kayıt 14.flac")
        assertEquals(listOf("Kayıt 14.flac"), opened)
        assertEquals(emptySet<Any>(), selection)
    }
}
