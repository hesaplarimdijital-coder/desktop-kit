package desktopkit.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.components.BannerTone
import desktopkit.components.CheckboxRow
import desktopkit.components.CountBadge
import desktopkit.components.DeskButton
import desktopkit.components.DeskCard
import desktopkit.components.DeskIcons
import desktopkit.components.DeskListRow
import desktopkit.components.DeskProgressBar
import desktopkit.components.DeskSearchField
import desktopkit.components.DeskTab
import desktopkit.components.DeskTextField
import desktopkit.components.ToastState
import desktopkit.components.HorizontalRule
import desktopkit.components.IconMenuButton
import desktopkit.components.KeyValueRow
import desktopkit.components.MenuButton
import desktopkit.components.NumberField
import desktopkit.components.SelectionBar
import desktopkit.components.SettingRow
import desktopkit.components.StatusBanner
import desktopkit.components.SwitchRow
import desktopkit.components.TabStrip
import desktopkit.components.ToastDuration
import desktopkit.components.ToastHost
import desktopkit.sampleArtwork
import desktopkit.sampleTracks
import desktopkit.theme.DeskTheme
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The 2.0 form, feedback and structure components (K1–K3) on a settings page as in the design
 * study: a status strip, cards of switches, a number field out of range, a text field with a
 * label, three check boxes and a progress bar; tabs and a toast on DeX; list rows on a phone.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FormsScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String, theme: ThemeMode, input: InputMode, content: @Composable BoxScope.() -> Unit) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = theme, inputMode = input) {
                Box(Modifier.fillMaxSize().background(DeskTheme.colors.background)) { content() }
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Composable
    private fun SettingsPage() {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StatusBanner(
                BannerTone.Success,
                title = "USB DAC bağlı, kayıpsız çalıyor",
                text = "24-bit / 192 kHz · Android'in ses karıştırıcısı atlanıyor",
                actionLabel = "Raporu aç",
                onAction = {},
            )
            DeskCard(title = "Çıkış") {
                SettingRow("Çıkış aygıtı", subtitle = "Bağlanınca kendiliğinden seçilir") {
                    MenuButton("USB DAC", entries = { emptyList() })
                }
                HorizontalRule()
                SwitchRow("Sesi DAC'a doğrudan gönder", checked = true, onCheckedChange = {},
                    subtitle = "Ses düzeyi DAC'tan ayarlanır; bildirim sesleri bu sırada duyulmaz")
                HorizontalRule()
                SwitchRow("Ses düzeyini eşitle", checked = false, onCheckedChange = {}, subtitle = "ReplayGain etiketleri okunur")
                HorizontalRule()
                SettingRow("Ön tampon", subtitle = "Ağ paylaşımından çalarken takılmayı önler") {
                    NumberField(9000, {}, min = 0, max = 5000, suffix = "ms")
                }
            }
            DeskCard(title = "Kütüphane taraması") {
                DeskTextField("/storage/2A1F-0C33/Müzik", {}, Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    label = "Müzik klasörü", leading = DeskIcons.Folder, clearable = true, mono = true)
                CheckboxRow("Kayıpsız: FLAC, ALAC, WAV", checked = true, onCheckedChange = {})
                CheckboxRow("Kayıplı (2 / 3)", ToggleableState.Indeterminate, onClick = {})
                CheckboxRow("Ses kitapları", checked = false, onCheckedChange = {})
                Column(Modifier.padding(vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text("Taranıyor: Mavi Hat / Kış Bahçesi", style = DeskTheme.type.bodyStrong, color = DeskTheme.colors.text,
                            modifier = Modifier.weight(1f))
                        Text("3 090 / 4 812", style = DeskTheme.type.mono, color = DeskTheme.colors.textSecondary)
                    }
                    Spacer(Modifier.height(10.dp))
                    DeskProgressBar(0.64f)
                }
            }
            StatusBanner(BannerTone.Warning, "Paylaşım klasörü bulunamadı; tarama yarıda kaldı.", closeLabel = "Kapat", onClose = {})
        }
    }

    @Test @Config(qualifiers = "tr-w900dp-h1200dp-mdpi")
    fun settingsLightTouch() = capture("forms-light-touch", ThemeMode.Light, InputMode.Touch) { SettingsPage() }

    @Test @Config(qualifiers = "tr-w900dp-h1200dp-mdpi")
    fun settingsDarkTouch() = capture("forms-dark-touch", ThemeMode.Dark, InputMode.Touch) { SettingsPage() }

    @Test @Config(qualifiers = "tr-w1000dp-h760dp-mdpi")
    fun settingsDarkPointer() = capture("forms-dark-pointer", ThemeMode.Dark, InputMode.Pointer) { SettingsPage() }

    /** DeX: tabs over a list, a search field, a count badge, an "undo" toast at the bottom. */
    @Test @Config(qualifiers = "tr-w1000dp-h640dp-mdpi")
    fun tabsAndToastDarkPointer() = capture("tabs-toast-dark-pointer", ThemeMode.Dark, InputMode.Pointer) {
        val toasts = remember { ToastState() }
        LaunchedEffect(Unit) {
            toasts.show("“Pencere” sıranın sonuna eklendi · 9 parça", actionLabel = "Geri al", tone = BannerTone.Success,
                shortcut = "Ctrl+Z", duration = ToastDuration.Indefinite)
        }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Kitaplık", style = DeskTheme.type.headline, color = DeskTheme.colors.text, modifier = Modifier.weight(1f))
                DeskSearchField("Kış", {}, Modifier.width(260.dp))
                Spacer(Modifier.width(8.dp))
                IconMenuButton(DeskIcons.Sort, "Sırala", entries = { emptyList() })
                CountBadge(8)
            }
            TabStrip {
                DeskTab("Sanatçılar", selected = false, onClick = {})
                DeskTab("Albümler", selected = true, onClick = {}, badge = 12)
                DeskTab("Parçalar", selected = false, onClick = {}, busy = true)
                DeskTab("Arama: Kış", selected = false, onClick = {}, onClose = {})
            }
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                KeyValueRow("Biçim", "FLAC 24/96", mono = true)
                KeyValueRow("Konum", "/storage/2A1F-0C33/Müzik/Mavi Hat/Kış Bahçesi", selectable = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeskButton("Çalma listesine ekle", {}, icon = DeskIcons.PlaylistAdd)
                    DeskButton("Devre dışı", {}, enabled = false)
                }
                DeskProgressBar(null, Modifier.padding(top = 12.dp))
            }
        }
        ToastHost(toasts, Modifier.align(Alignment.BottomCenter))
    }

    /** A phone: list rows with artwork, one selected in selection mode, the selection bar at the bottom. */
    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun listRowsDarkTouch() = capture("list-rows-dark-touch", ThemeMode.Dark, InputMode.Touch) {
        Column(Modifier.fillMaxSize()) {
            sampleTracks.take(5).forEachIndexed { i, (title, artist) ->
                DeskListRow(
                    title = title,
                    subtitle = artist,
                    selectionMode = true,
                    selected = i == 1,
                    leadingContent = { Box(Modifier.size(40.dp).background(sampleArtwork[i], RoundedCornerShape(6.dp))) },
                    trailing = "%d:%02d".format(3 + i, 7 * i % 60),
                    progress = if (i == 3) 0.4f else null,
                    menu = { emptyList() },
                )
            }
            Spacer(Modifier.weight(1f))
            SelectionBar("1 seçili", onClose = {}) {
                IconMenuButton(DeskIcons.PlaylistAdd, "Listeye ekle", entries = { emptyList() })
                IconMenuButton(DeskIcons.Delete, "Sil", entries = { emptyList() })
            }
        }
    }
}
