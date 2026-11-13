package desktopkit.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.shell.LocalWidthClass
import desktopkit.shell.WidthClass
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The design audit's medium finding 11: below the width that docks it, the inspector comes up as a sheet. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class InspectorSheetTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var widthClass by mutableStateOf(WidthClass.Compact)
    private lateinit var state: InspectorState

    private fun layout() {
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                CompositionLocalProvider(LocalWidthClass provides widthClass) {
                    state = rememberInspectorState()
                    WorkstationLayout(
                        state = state,
                        main = {
                            Column {
                                Text("liste")
                                InspectorToggleButton(state)
                            }
                        },
                        inspector = { Text("etiketler") },
                        sheetTitle = "Ayrıntılar",
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun narrowTheButtonBringsTheInspectorUpAsASheet() {
        layout()
        compose.onNodeWithText("etiketler").assertDoesNotExist()
        compose.onNodeWithContentDescription("Hide the inspector").assertDoesNotExist()
        compose.onNodeWithContentDescription("Show the inspector").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("etiketler").assertExists()
        compose.onNodeWithText("Ayrıntılar").assertExists()
        assertTrue(state.sheetOpen)
        // the window widens: the inspector is beside the main pane, the sheet gone
        widthClass = WidthClass.Expanded
        compose.waitForIdle()
        assertFalse(state.sheetOpen)
        assertFalse(state.collapsed)
        compose.onNodeWithText("Ayrıntılar").assertDoesNotExist()
        compose.onNodeWithText("etiketler").assertExists()
    }
}
