package desktopkit.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.SampleFile
import desktopkit.SampleFullPlayer
import desktopkit.SampleMiniPlayer
import desktopkit.SampleTrackList
import desktopkit.SampleTransportBar
import desktopkit.TestShell
import desktopkit.components.ColumnWidth
import desktopkit.components.DenseTable
import desktopkit.components.DeskButton
import desktopkit.components.DialogFrame
import desktopkit.components.DismissStyle
import desktopkit.components.ExpandablePlayer
import desktopkit.components.InspectorPane
import desktopkit.components.InspectorState
import desktopkit.components.InspectorTab
import desktopkit.components.PlayerValue
import desktopkit.components.TableColumn
import desktopkit.components.TableSort
import desktopkit.components.WorkstationLayout
import desktopkit.components.rememberExpandablePlayerState
import desktopkit.mediaSpec
import desktopkit.sampleFiles
import desktopkit.sampleMoreActions
import desktopkit.shell.WidthClass
import desktopkit.shell.plus
import desktopkit.shell.rememberShellContentPadding
import desktopkit.theme.DeskTheme
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.GlassMode
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The 1.0 surfaces: the phone with content under its glass bars, the expandable player and the
 * "⋯" sheet; the tablet and DeX workstation with the inspector, the floating player and the glass
 * table header; the dialog's close affordance. Glass is drawn in its translucent form: the test
 * renderer draws into software canvases, which cannot draw a blur.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KitScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun window(name: String) {
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    private fun phone(
        name: String,
        theme: ThemeMode,
        expanded: Boolean = false,
        glassMode: GlassMode = GlassMode.Translucent,
        list: LazyListState? = null,
        after: () -> Unit = {},
    ) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = theme, inputMode = InputMode.Touch) {
                val player = rememberExpandablePlayerState(if (expanded) PlayerValue.Expanded else PlayerValue.Collapsed)
                TestShell(
                    WidthClass.Compact,
                    startRoute = "music",
                    spec = mediaSpec,
                    glassMode = glassMode,
                    moreActions = sampleMoreActions,
                    bottomBar = { ExpandablePlayer(player, mini = { SampleMiniPlayer(1) }, full = { SampleFullPlayer(1) }) },
                ) { _, _ ->
                    val state = list ?: rememberLazyListState()
                    // scrolled, so rows pass under the top bar and the bottom glass
                    LaunchedEffect(Unit) { state.scrollToItem(3, 20) }
                    SampleTrackList(state)
                }
            }
        }
        compose.waitForIdle()
        after()
        window(name)
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phoneGlassDark() = phone("phone-glass-dark", ThemeMode.Dark)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phoneGlassLight() = phone("phone-glass-light", ThemeMode.Light)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phoneGlassOled() = phone("phone-glass-oled", ThemeMode.Oled)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phonePlayerExpanded() = phone("phone-player-expanded-dark", ThemeMode.Dark, expanded = true)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phoneMoreSheet() = phone("phone-more-sheet-dark", ThemeMode.Dark) {
        compose.onNodeWithText("Daha fazla").performClick()
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phoneMoreSheetLight() = phone("phone-more-sheet-light", ThemeMode.Light) {
        compose.onNodeWithText("Daha fazla").performClick()
    }

    private fun workstation(name: String, widthClass: WidthClass, theme: ThemeMode, input: InputMode) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = theme, inputMode = input) {
                Box(Modifier.fillMaxSize().background(DeskTheme.colors.background)) {
                    TestShell(
                        widthClass,
                        startRoute = "documents",
                        floatingPlayer = true,
                        bottomBar = { SampleTransportBar(2) },
                    ) { _, _ -> WorkstationScreen() }
                }
            }
        }
        window(name)
    }

    @Composable
    private fun WorkstationScreen() {
        val inspector = androidx.compose.runtime.remember { InspectorState(320f) }
        val tabs = listOf(
            InspectorTab("tags", "Etiketler"),
            InspectorTab("signal", "Sinyal yolu"),
            InspectorTab("lyrics", "Sözler"),
            InspectorTab("queue", "Sıra"),
        )
        WorkstationLayout(
            state = inspector,
            main = {
                val state = rememberLazyListState()
                LaunchedEffect(Unit) { state.scrollToItem(2, 10) }
                DenseTable(
                    columns = listOf(
                        TableColumn<SampleFile>("Ad", ColumnWidth.Weight(1f)) { it.name },
                        TableColumn<SampleFile>("Tür", ColumnWidth.Fixed(120.dp), hideBelow = WidthClass.Medium) { it.kind },
                        TableColumn<SampleFile>("Boyut", ColumnWidth.Fixed(90.dp), alignEnd = true, mono = true) { it.size },
                    ),
                    rows = sampleFiles + sampleFiles.map { it.copy(name = "Kopya – " + it.name) } + sampleFiles.map { it.copy(name = "Arşiv – " + it.name) },
                    key = { it.name },
                    state = state,
                    selectedKeys = setOf("Kopya – Notlar.md", "Kopya – Kayıt 14.flac"),
                    onSelectionChange = {},
                    sort = TableSort(0, true),
                    onSortChange = {},
                    onRowClick = { _, _ -> },
                    contentPadding = rememberShellContentPadding() + androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp),
                )
            },
            inspector = {
                InspectorPane(tabs, selected = 0, onSelect = {}, onCollapse = {}) { tab ->
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Biçim" to "FLAC 24/96", "Kanal" to "Stereo", "ReplayGain" to "−6,2 dB", "Tepe" to "0,98").forEach { (k, v) ->
                            Row(Modifier.fillMaxWidth()) {
                                Text(k, style = DeskTheme.type.body, color = DeskTheme.colors.textSecondary, modifier = Modifier.weight(1f))
                                Text(v, style = DeskTheme.type.mono, color = DeskTheme.colors.text)
                            }
                        }
                        Text(tab.label, style = DeskTheme.type.caption, color = DeskTheme.colors.textTertiary)
                    }
                }
            },
        )
    }

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun dexWorkstationDark() = workstation("dex-workstation-dark", WidthClass.ExtraLarge, ThemeMode.Dark, InputMode.Pointer)

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun dexWorkstationLight() = workstation("dex-workstation-light", WidthClass.ExtraLarge, ThemeMode.Light, InputMode.Pointer)

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun tabletWorkstationDark() = workstation("tablet-workstation-dark", WidthClass.Expanded, ThemeMode.Dark, InputMode.Touch)

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun tabletWorkstationLight() = workstation("tablet-workstation-light", WidthClass.Expanded, ThemeMode.Light, InputMode.Touch)

    private fun dialogs(name: String, theme: ThemeMode) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = theme, inputMode = InputMode.Pointer) {
                Column(
                    Modifier.fillMaxSize().background(DeskTheme.colors.background).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    for (style in DismissStyle.entries) {
                        DialogFrame(
                            onDismiss = {},
                            title = if (style == DismissStyle.EscKey) "Klavye bağlı: Esc" else "Dokunmatik: ×",
                            dismissStyle = style,
                            actions = {
                                DeskButton("Vazgeç", {})
                                DeskButton("Kaydet", {}, primary = true)
                            },
                        ) {
                            Text("Çalma listesi \"Akşam\" kaydedilsin mi?", style = DeskTheme.type.body, color = DeskTheme.colors.textSecondary,
                                modifier = Modifier.height(40.dp))
                        }
                    }
                }
            }
        }
        window(name)
    }

    @Test @Config(qualifiers = "tr-w700dp-h700dp-mdpi")
    fun dialogsDark() = dialogs("dialogs-dark", ThemeMode.Dark)

    @Test @Config(qualifiers = "tr-w700dp-h700dp-mdpi")
    fun dialogsLight() = dialogs("dialogs-light", ThemeMode.Light)
}
