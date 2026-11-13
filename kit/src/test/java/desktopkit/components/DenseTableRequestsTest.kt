package desktopkit.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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

/** DeskMail's request 2 and the design audit's table findings: a mail list built from DenseTable. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1000dp-h700dp-mdpi")
class DenseTableRequestsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private data class Mail(val id: Int, val from: String, val subject: String, val unread: Boolean, var starred: Boolean)

    private var mails by mutableStateOf(
        listOf(
            Mail(1, "Ayşe Yılmaz", "Toplantı notları", unread = true, starred = false),
            Mail(2, "Mehmet Kaya", "Fatura", unread = false, starred = true),
        ),
    )
    private val opened = mutableListOf<Int>()
    private val starredClicks = mutableListOf<Int>()
    private var sort by mutableStateOf<TableSort?>(TableSort(1, ascending = true))

    private fun table() {
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                DenseTable(
                    columns = listOf(
                        TableColumn("Yıldız", ColumnWidth.Fixed(40.dp), sortable = false,
                            cell = { mail -> Icon(DeskIcons.Check, contentDescription = if (mail.starred) "Yıldızlı" else "Yıldızsız") },
                            onCellClick = { mail -> starredClicks += mail.id },
                            cellClickLabel = { "Yıldızı değiştir" },
                        ) { if (it.starred) "★" else "" },
                        TableColumn("Gönderen", ColumnWidth.Weight(1f)) { it.from },
                        TableColumn("Konu", ColumnWidth.Weight(2f), ellipsis = androidx.compose.ui.text.style.TextOverflow.MiddleEllipsis) { it.subject },
                    ),
                    rows = mails,
                    key = { it.id },
                    onRowClick = { mail, _ -> opened += mail.id },
                    sort = sort,
                    onSortChange = { sort = it },
                    rowEmphasis = { it.unread },
                    emptyContent = { Text("Bu klasörde ileti yok") },
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aCellButtonDoesNotOpenTheRow() {
        table()
        compose.onNodeWithContentDescription("Yıldızsız").performClick()
        assertEquals(listOf(1), starredClicks)
        assertEquals(emptyList<Int>(), opened)
        compose.onNodeWithText("Toplantı notları").performClick()
        assertEquals(listOf(1), opened)
    }

    @Test
    fun theSortedColumnSaysWhichWay() {
        table()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Sorted ascending")).assertExists()
        compose.onNodeWithText("Gönderen").performClick()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Sorted descending")).assertExists()
    }

    @Test
    fun anEmptyTableSaysWhy() {
        mails = emptyList()
        table()
        compose.onNodeWithText("Bu klasörde ileti yok").assertExists()
    }

    @Test
    fun anEmptyGridSaysWhyToo() {
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                DeskGrid(items = emptyList<Mail>(), key = { it.id }, onItemClick = { _, _ -> },
                    emptyContent = { Text("Albüm bulunamadı") }) { mail -> Text(mail.subject) }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Albüm bulunamadı").assertExists()
    }
}
