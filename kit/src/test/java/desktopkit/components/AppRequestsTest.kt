package desktopkit.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

/** What the apps on the kit asked of it (DeskMail's KIT-ISTEKLERI.md), one test per request. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class AppRequestsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun content(input: InputMode = InputMode.Touch, body: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = input) { Column { body() } }
        }
        compose.waitForIdle()
    }

    /** Request 6: "Remote images blocked · Show · Always show from this sender". */
    @Test
    fun aBannerHasASecondAction() {
        val used = mutableListOf<String>()
        content {
            StatusBanner(
                BannerTone.Info, "Uzak görseller engellendi",
                actionLabel = "Göster", onAction = { used += "show" },
                secondaryActionLabel = "Bu gönderenden hep göster", onSecondaryAction = { used += "always" },
            )
        }
        compose.onNodeWithText("Göster").performClick()
        compose.onNodeWithText("Bu gönderenden hep göster").performClick()
        assertEquals(listOf("show", "always"), used)
    }

    /** Request 8 and the audit's visible "⋯": a preview line under the subject, and a button for the row's menu. */
    @Test
    fun aListRowHasAPreviewAndAMoreButton() {
        content {
            DeskListRow(
                title = "Ayşe Yılmaz",
                subtitle = "Toplantı notları",
                preview = "Yarınki toplantı için notları ekte bulabilirsin, eksik bir şey varsa yaz.",
                menu = { listOf(MenuEntry.Item("Arşivle", {})) },
                moreButton = true,
            )
        }
        compose.onNodeWithText("Yarınki toplantı için notları ekte bulabilirsin, eksik bir şey varsa yaz.").assertIsDisplayed()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Arşivle").assertIsDisplayed()
    }

    /** Requests 16 and 17: a filled button on an empty screen, a whole settings line as a button, a danger button. */
    @Test
    fun accountsScreenPieces() {
        val used = mutableListOf<String>()
        content {
            SettingRow("ayse@example.com", subtitle = "IMAP · Gmail", onClick = { used += "account" })
            DeskButton("Hesabı kaldır", onClick = { used += "remove" }, danger = true)
            desktopkit.screens.MessageScreen("Henüz hesap yok", DeskIcons.Folder, actionLabel = "Hesap ekle",
                onAction = { used += "add" }, actionPrimary = true)
        }
        compose.onNodeWithText("ayse@example.com").performClick()
        compose.onNodeWithText("Hesabı kaldır").performClick()
        compose.onNodeWithText("Hesap ekle").performClick()
        assertEquals(listOf("account", "remove", "add"), used)
        compose.onNode(androidx.compose.ui.test.isHeading() and androidx.compose.ui.test.hasText("Henüz hesap yok")).assertExists()
    }
}
