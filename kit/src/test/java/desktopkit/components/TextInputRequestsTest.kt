package desktopkit.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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

/** DeskMail's requests 23 (the cursor and the selection of a field) and 4 (addresses as chips, with suggestions). */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1000dp-h700dp-mdpi")
class TextInputRequestsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun aFieldTellsWhereTheCursorIsAndTakesOne() {
        // the app puts the cursor before "İmza"; what is typed goes there
        var value by mutableStateOf(TextFieldValue("Merhaba\nİmza", selection = TextRange(8)))
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                Column { DeskTextField(value, { value = it }, singleLine = false) }
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("Şablon ")
        compose.waitForIdle()
        assertEquals("Merhaba\nŞablon İmza", value.text)
        assertEquals(TextRange(15), value.selection)
    }

    private var tokens by mutableStateOf(listOf<String>())

    private fun tokenField() {
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                Column {
                    TokenField(
                        tokens = tokens,
                        onTokensChange = { tokens = it },
                        label = "Kime",
                        isValid = { "@" in it },
                        suggestions = { typed ->
                            listOf(TokenSuggestion("ayse@ornek.org", "Ayşe Yılmaz", "ayse@ornek.org"))
                                .filter { it.title.startsWith(typed, ignoreCase = true) || it.value.startsWith(typed) }
                        },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun separatorsMakeChipsAndAPastedListSplits() {
        tokenField()
        val field = compose.onNode(hasSetTextAction())
        field.performTextInput("mehmet@ornek.org,")
        compose.waitForIdle()
        assertEquals(listOf("mehmet@ornek.org"), tokens)
        field.performTextInput("a@x.org; b@y.org, yarım")
        compose.waitForIdle()
        assertEquals(listOf("mehmet@ornek.org", "a@x.org", "b@y.org"), tokens)
        compose.onNodeWithText("yarım").assertExists()
    }

    @Test
    fun backspaceInAnEmptyFieldRemovesTheLastChip() {
        tokens = listOf("a@x.org", "geçersiz")
        tokenField()
        compose.onNode(hasSetTextAction()).performClick()
        compose.onNode(hasSetTextAction()).performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Backspace) }
        compose.waitForIdle()
        assertEquals(listOf("a@x.org"), tokens)
    }

    @Test
    fun aSuggestionIsTakenWithTheKeyboardOrATap() {
        tokenField()
        val field = compose.onNode(hasSetTextAction())
        field.performClick()
        field.performTextInput("Ay")
        compose.waitForIdle()
        compose.onNodeWithText("Ayşe Yılmaz").assertExists()
        field.performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Enter) }
        compose.waitForIdle()
        assertEquals(listOf("ayse@ornek.org"), tokens)
        // the chip's own × removes it
        compose.onNodeWithContentDescription("Remove: ayse@ornek.org").performClick()
        compose.waitForIdle()
        assertEquals(emptyList<String>(), tokens)
    }
}
