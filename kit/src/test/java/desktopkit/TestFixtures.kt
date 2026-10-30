package desktopkit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import desktopkit.components.ActionGroup
import desktopkit.components.ChipOption
import desktopkit.components.ColumnWidth
import desktopkit.components.DenseTable
import desktopkit.components.DeskIconButton
import desktopkit.components.DeskIcons
import desktopkit.components.DeskSlider
import desktopkit.components.FilterChips
import desktopkit.components.HorizontalRule
import desktopkit.components.MenuEntry
import desktopkit.components.MenuSpec
import desktopkit.components.TableColumn
import desktopkit.components.TableSort
import desktopkit.screens.MessageScreen
import desktopkit.screens.PageHeader
import desktopkit.shell.DesktopShell
import desktopkit.shell.MoreStyle
import desktopkit.shell.rememberShellContentPadding
import desktopkit.shell.NavItem
import desktopkit.shell.NavKind
import desktopkit.shell.NavSection
import desktopkit.shell.ShellNavigator
import desktopkit.shell.ShellSpec
import desktopkit.shell.ShellTransitions
import desktopkit.shell.CompactTopBar
import desktopkit.shell.TopBarAction
import desktopkit.shell.WidthClass
import desktopkit.theme.DeskTheme
import desktopkit.theme.GlassMode
import desktopkit.theme.LocalGlassMode

/** A file manager-like app with more screens than the bottom bar and the rail can show. */
val sampleSpec = ShellSpec(
    appTitle = "Örnek",
    sections = listOf(
        NavSection(
            "Kitaplık",
            listOf(
                NavItem("home", "Ana sayfa", DeskIcons.Home),
                NavItem("documents", "Belgeler", DeskIcons.Folder),
                NavItem("music", "Müzik", DeskIcons.Music),
                NavItem("videos", "Videolar", DeskIcons.Movies),
                NavItem("recent", "Son kullanılanlar", DeskIcons.History, shortLabel = "Son"),
            ),
        ),
        NavSection(
            "Kaynaklar",
            listOf(
                NavItem("device", "Bu cihaz", DeskIcons.Device, shortLabel = "Cihaz"),
                NavItem("network", "Ağ", DeskIcons.Network),
            ),
        ),
        NavSection(
            "Araçlar",
            listOf(
                NavItem("convert", "Dönüştür", DeskIcons.Convert),
                NavItem("downloads", "İndirilenler", DeskIcons.Downloads, shortLabel = "İndirme"),
            ),
        ),
    ),
    footer = NavItem("settings", "Ayarlar", DeskIcons.Settings),
    bottomBar = listOf("home", "documents", "music", "videos"),
)

data class SampleFile(val name: String, val kind: String, val size: String)

val sampleFiles = listOf(
    SampleFile("Bütçe 2026.ods", "Tablo", "84 KB"),
    SampleFile("Tatil fotoğrafları", "Klasör", "1,2 GB"),
    SampleFile("Sunum taslağı.odp", "Sunum", "3,4 MB"),
    SampleFile("Notlar.md", "Metin", "12 KB"),
    SampleFile("Kayıt 14.flac", "Ses", "38 MB"),
    SampleFile("Yedek.zip", "Arşiv", "640 MB"),
)

/** The phone layout of a media app: three tabs and "⋯" opening the glass sheet. */
val mediaSpec = ShellSpec(
    appTitle = "Nağme",
    sections = listOf(
        NavSection(
            "Dinle",
            listOf(
                NavItem("music", "Müzik", DeskIcons.Music),
                NavItem("podcasts", "Podcast'ler", DeskIcons.Podcasts, shortLabel = "Podcast"),
                NavItem("radio", "Radyo", DeskIcons.Radio),
            ),
        ),
        NavSection(
            "Kitaplık",
            listOf(
                NavItem("albums", "Albümler", DeskIcons.Albums),
                NavItem("artists", "Sanatçılar", DeskIcons.Artists),
                NavItem("history", "Geçmiş", DeskIcons.History),
            ),
        ),
    ),
    footer = NavItem("settings", "Ayarlar", DeskIcons.Settings),
    bottomBar = listOf("music", "podcasts", "radio"),
    moreStyle = MoreStyle.Sheet,
)

/** App actions after the screens in the "⋯" sheet. */
val sampleMoreActions = listOf(
    ActionGroup("Oynatma", listOf(
        MenuEntry.Item("Ekolayzır", {}, icon = DeskIcons.Equalizer),
        MenuEntry.Item("Uyku zamanlayıcısı", {}, icon = DeskIcons.History),
    )),
)

/**
 * The real shell with sample screens and a sample status bar; no screen transitions unless asked
 * for. Glass is pinned to [glassMode] (the translucent look of Android 11 and older by default), so
 * the pictures do not depend on the test machine's renderer.
 */
@Composable
fun TestShell(
    widthClass: WidthClass,
    startRoute: String = "home",
    sidebarCollapsed: Boolean = false,
    transitions: ShellTransitions = ShellTransitions.None,
    spec: ShellSpec = sampleSpec,
    bottomBar: @Composable (NavKind) -> Unit = { kind -> SampleStatusBar(kind) },
    floatingPlayer: Boolean = false,
    glassMode: GlassMode? = GlassMode.Translucent,
    moreActions: List<ActionGroup> = emptyList(),
    toasts: desktopkit.components.DeskToastState? = null,
    overlays: @Composable () -> Unit = {},
    content: @Composable (String, ShellNavigator) -> Unit = { route, _ -> SampleScreen(route) },
) {
    val navController = rememberNavController()
    LaunchedEffect(startRoute) {
        if (startRoute != spec.startRoute) ShellNavigator(navController).navigate(startRoute)
    }
    CompositionLocalProvider(LocalGlassMode provides glassMode) {
        DesktopShell(
            spec = spec,
            widthClass = widthClass,
            navController = navController,
            sidebarCollapsed = sidebarCollapsed,
            badges = mapOf("documents" to "128", "music" to "4 812", "downloads" to "3 yeni"),
            menus = listOf("Dosya", "Düzen", "Görünüm", "Araçlar", "Yardım").map { title ->
                MenuSpec(title) { listOf(MenuEntry.Item("$title…", {})) }
            },
            onSearch = {},
            bottomBar = bottomBar,
            transitions = transitions,
            floatingPlayer = floatingPlayer,
            moreActions = moreActions,
            toasts = toasts,
            overlays = overlays,
            content = content,
        )
    }
}

/** A long list laid out under the phone's bars: it pads its ends with the shell's padding. */
@Composable
fun SampleTrackList(state: LazyListState = rememberLazyListState(), count: Int = 40) {
    val padding = rememberShellContentPadding()
    LazyColumn(Modifier.fillMaxSize().testTag("tracks"), state = state, contentPadding = padding) {
        items(count) { i ->
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.foundation.layout.Box(
                    Modifier.size(40.dp).background(sampleArtwork[i % sampleArtwork.size], androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(sampleTracks[i % sampleTracks.size].first, style = DeskTheme.type.bodyStrong, color = DeskTheme.colors.text, maxLines = 1)
                    Text(sampleTracks[i % sampleTracks.size].second, style = DeskTheme.type.labelSmall, color = DeskTheme.colors.textTertiary, maxLines = 1)
                }
                Text("%d:%02d".format(3 + i % 4, (17 + i * 7) % 60), style = DeskTheme.type.mono, color = DeskTheme.colors.textTertiary)
            }
        }
    }
}

val sampleTracks = listOf(
    "Uzun İnce Bir Yoldayım" to "Âşık Veysel",
    "Gesi Bağları" to "Kayseri türküsü",
    "Kâtibim" to "İstanbul türküsü",
    "Dere Geliyor Dere" to "Karadeniz",
    "Sarı Gelin" to "Erzurum",
    "Çanakkale İçinde" to "Türkü",
)

/** "Artwork" colours of the sample tracks. */
val sampleArtwork = listOf(
    Color(0xFFE0574B), Color(0xFFF2B84B), Color(0xFF59D49A), Color(0xFF8AB0FF), Color(0xFFB07AFF), Color(0xFFFF8FB1),
)

/** The mini player of the samples: artwork, title, artist, play button. */
@Composable
fun SampleMiniPlayer(track: Int = 0) {
    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(44.dp).background(sampleArtwork[track % sampleArtwork.size], androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(sampleTracks[track % sampleTracks.size].first, style = DeskTheme.type.bodyStrong, color = DeskTheme.colors.text, maxLines = 1)
            Text(sampleTracks[track % sampleTracks.size].second, style = DeskTheme.type.labelSmall, color = DeskTheme.colors.textSecondary, maxLines = 1)
        }
        DeskIconButton(DeskIcons.Pause, "Duraklat", {})
        DeskIconButton(DeskIcons.Next, "Sonraki", {})
    }
}

/** The full-screen player of the samples. */
@Composable
fun SampleFullPlayer(track: Int = 0) {
    Column(Modifier.fillMaxSize().padding(24.dp).testTag("full-player"), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(48.dp))
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxWidth().aspectRatio(1f).background(sampleArtwork[track % sampleArtwork.size], androidx.compose.foundation.shape.RoundedCornerShape(16.dp)),
        )
        Spacer(Modifier.height(24.dp))
        Text(sampleTracks[track % sampleTracks.size].first, style = DeskTheme.type.headline, color = DeskTheme.colors.text)
        Text(sampleTracks[track % sampleTracks.size].second, style = DeskTheme.type.body, color = DeskTheme.colors.textSecondary)
        Spacer(Modifier.height(24.dp))
        // a plain progress line: a focusable slider would take the test window's first (keyboard) focus
        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(4.dp).background(DeskTheme.colors.borderStrong)) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(0.35f).height(4.dp).background(DeskTheme.colors.accent))
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            DeskIconButton(DeskIcons.Previous, "Önceki", {})
            DeskIconButton(DeskIcons.Pause, "Duraklat", {}, size = 64.dp, iconSize = 28.dp)
            DeskIconButton(DeskIcons.Next, "Sonraki", {})
        }
    }
}

/** The tablet and DeX transport bar for a floating player: transparent, the shell draws the glass. */
@Composable
fun SampleTransportBar(track: Int = 0) {
    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(40.dp).background(sampleArtwork[track % sampleArtwork.size], androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.width(220.dp)) {
            Text(sampleTracks[track % sampleTracks.size].first, style = DeskTheme.type.bodyStrong, color = DeskTheme.colors.text, maxLines = 1)
            Text(sampleTracks[track % sampleTracks.size].second, style = DeskTheme.type.labelSmall, color = DeskTheme.colors.textSecondary, maxLines = 1)
        }
        DeskIconButton(DeskIcons.Previous, "Önceki", {})
        DeskIconButton(DeskIcons.Pause, "Duraklat", {})
        DeskIconButton(DeskIcons.Next, "Sonraki", {})
        Spacer(Modifier.width(12.dp))
        DeskSlider(value = 0.35f, onValueChange = {}, modifier = Modifier.weight(1f), label = "Konum")
    }
}

@Composable
fun SampleScreen(route: String) {
    when (route) {
        "documents" -> Column(Modifier.fillMaxSize()) {
            PageHeader(title = "Belgeler", subtitle = "128 öğe") {
                DeskIconButton(DeskIcons.Grid, "Izgara görünümü", {})
                DeskIconButton(DeskIcons.ListView, "Liste görünümü", {}, selected = true)
            }
            // one sideways-scrolling line on the phone, wrapped lines from tablet width up
            FilterChips(
                options = sampleFiles.map { ChipOption(it.kind, it.kind) } + ChipOption("Görsel", "Görseller") + ChipOption("Çizim", "Çizimler"),
                selected = setOf("Sunum"),
                onToggle = {},
                modifier = Modifier.padding(bottom = 4.dp),
            )
            DenseTable(
                columns = listOf(
                    TableColumn<SampleFile>("Ad", ColumnWidth.Weight(1f)) { it.name },
                    TableColumn<SampleFile>("Tür", ColumnWidth.Fixed(120.dp), hideBelow = WidthClass.Medium) { it.kind },
                    TableColumn<SampleFile>("Boyut", ColumnWidth.Fixed(90.dp), alignEnd = true, mono = true) { it.size },
                ),
                rows = sampleFiles,
                key = { it.name },
                selectedKey = "Sunum taslağı.odp",
                sort = TableSort(0, true),
                onSortChange = {},
                onRowClick = { _, _ -> },
            )
        }
        "home" -> Column(Modifier.fillMaxSize()) {
            PageHeader(title = "Ana sayfa")
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                sampleFiles.take(3).forEach { file ->
                    Column(Modifier.width(200.dp)) {
                        androidx.compose.foundation.layout.Box(
                            Modifier.fillMaxWidth().height(112.dp)
                                .background(DeskTheme.colors.surfaceRaised, androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(file.name, style = DeskTheme.type.bodyStrong, color = DeskTheme.colors.text)
                        Text(file.size, style = DeskTheme.type.labelSmall, color = DeskTheme.colors.textTertiary)
                    }
                }
            }
        }
        else -> MessageScreen(
            title = "Bu ekran yakında",
            icon = sampleSpec.item(route)?.icon ?: DeskIcons.Info,
            pageTitle = sampleSpec.item(route)?.label,
            description = "Örnek uygulamada bu bölüm boş bırakıldı.",
            actionLabel = "Ana sayfaya dön",
            onAction = {},
        )
    }
}

@Composable
fun SampleStatusBar(kind: NavKind) {
    val colors = DeskTheme.colors
    Column(Modifier.fillMaxWidth().background(colors.chrome)) {
        HorizontalRule()
        Row(
            // grows with large text instead of cutting it
            Modifier.fillMaxWidth().heightIn(min = if (kind == NavKind.Sidebar) 40.dp else 48.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Kopyalanıyor: Yedek.zip", style = DeskTheme.type.labelSmall, color = colors.textSecondary, modifier = Modifier.weight(1f))
            DeskSlider(value = 0.42f, onValueChange = {}, modifier = Modifier.width(160.dp), label = "Kopyalama")
            DeskIconButton(DeskIcons.Close, "İptal", {})
        }
    }
}

/** Four actions: the primary one and the next stay in the compact top bar, two go to its "more" menu. */
fun sampleTopBarActions(onAction: (String) -> Unit = {}) = listOf(
    TopBarAction(DeskIcons.OpenWindow, "Aç", { onAction("open") }, primary = true),
    TopBarAction(DeskIcons.PlaylistAdd, "Listeye ekle", { onAction("add") }),
    TopBarAction(DeskIcons.Info, "Özellikler", { onAction("info") }),
    TopBarAction(DeskIcons.Delete, "Sil", { onAction("delete") }),
)

/** A detail screen: on a phone its title, a back button and its actions move into the top bar. */
@Composable
fun SampleDetailScreen(onBack: () -> Unit = {}, onAction: (String) -> Unit = {}) {
    CompactTopBar(title = "Sunum taslağı.odp", onBack = onBack, actions = sampleTopBarActions(onAction))
    Column(Modifier.fillMaxSize()) {
        PageHeader(title = "Sunum taslağı.odp", subtitle = "Sunum · 3,4 MB")
        listOf("Tür" to "Sunum", "Boyut" to "3,4 MB", "Konum" to "Belgeler / İş", "Değiştirilme" to "14.10.2026 18:22").forEach { (label, value) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(label, style = DeskTheme.type.body, color = DeskTheme.colors.textSecondary, modifier = Modifier.width(120.dp))
                Text(value, style = DeskTheme.type.body, color = DeskTheme.colors.text)
            }
        }
    }
}
