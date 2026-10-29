package desktopkit.sample

import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.Menu
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import desktopkit.DesktopKitRoot
import desktopkit.components.ActionGroup
import desktopkit.components.ChipOption
import desktopkit.components.ColumnWidth
import desktopkit.components.DenseTable
import desktopkit.components.DeskButton
import desktopkit.components.DeskDialog
import desktopkit.components.ExpandablePlayer
import desktopkit.components.InspectorPane
import desktopkit.components.InspectorTab
import desktopkit.components.InspectorToggleButton
import desktopkit.components.MoreButton
import desktopkit.components.WorkstationLayout
import desktopkit.components.rememberExpandablePlayerState
import desktopkit.components.rememberInspectorState
import desktopkit.components.rememberTableColumnsState
import desktopkit.components.DeskIconButton
import desktopkit.components.DeskIcons
import desktopkit.components.DeskSlider
import desktopkit.components.FilterChips
import desktopkit.components.HorizontalSplitPane
import desktopkit.components.MenuEntry
import desktopkit.components.SegmentedChoice
import desktopkit.components.TableColumn
import desktopkit.components.TableSort
import desktopkit.components.TooltipArea
import desktopkit.components.rememberSplitState
import desktopkit.input.CommonShortcuts
import desktopkit.input.KeyChord
import desktopkit.input.Shortcut
import desktopkit.input.ShortcutGroup
import desktopkit.input.ShortcutRegistry
import desktopkit.screens.MessageScreen
import desktopkit.screens.PageHeader
import desktopkit.settings.AppearanceSection
import desktopkit.settings.InputSection
import desktopkit.settings.ShortcutListSection
import desktopkit.settings.DeskPreferences
import desktopkit.settings.rememberDeskPreferences
import desktopkit.shell.CompactTopBar
import desktopkit.shell.DetailTransition
import desktopkit.shell.DesktopShell
import desktopkit.shell.MoreStyle
import desktopkit.shell.NavKind
import desktopkit.shell.rememberShellContentPadding
import desktopkit.shell.NavItem
import desktopkit.shell.NavSection
import desktopkit.shell.ShellNavigator
import desktopkit.shell.ShellSpec
import desktopkit.shell.TopBarAction
import desktopkit.shell.WidthClass
import desktopkit.shell.currentWidthClass
import desktopkit.theme.Accent
import kotlinx.coroutines.launch
import desktopkit.theme.DeskTheme

// ------------------------------------------------------------------ 1. keyboard shortcuts

private val FileGroup = ShortcutGroup("file", R.string.menu_file)

/** The app's own shortcuts plus the common ones (Ctrl+K search, Ctrl+, settings, Ctrl+B sidebar, Ctrl+/ list). */
val shortcuts = ShortcutRegistry(
    groups = listOf(FileGroup, CommonShortcuts.ViewGroup, CommonShortcuts.HelpGroup),
    shortcuts = listOf(
        Shortcut("open", FileGroup, R.string.action_open, KeyChord(Key.O, ctrl = true)),
        Shortcut("new_folder", FileGroup, R.string.action_new_folder, KeyChord(Key.N, ctrl = true, shift = true)),
        Shortcut("refresh", FileGroup, R.string.action_refresh, KeyChord(Key.F5)),
    ) + CommonShortcuts.all,
)

/** A brand colour offered next to the four presets. */
private val BrandAccent = Accent.custom("Brand", Color(0xFF00897B))

class MainActivity : ComponentActivity() {

    /** Set by the composition; runs a shortcut's action. */
    private var perform: (String) -> Boolean = { false }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs = rememberDeskPreferences(accents = Accent.presets + BrandAccent)
            val navController = rememberNavController()
            val navigator = remember(navController) { ShellNavigator(navController) }
            val context = LocalContext.current
            perform = { id ->
                when (id) {
                    CommonShortcuts.SETTINGS -> navigator.navigate("settings")
                    CommonShortcuts.SHORTCUT_LIST -> navigator.navigate("settings")
                    CommonShortcuts.TOGGLE_SIDEBAR -> prefs.updateSidebarCollapsed(!prefs.sidebarCollapsed)
                    else -> Toast.makeText(context, id, Toast.LENGTH_SHORT).show()
                }
                true
            }

            // the player: the playing track's artwork colour becomes the accent and cross-fades (150 ms)
            var track by remember { mutableIntStateOf(0) }
            var followArtwork by remember { mutableStateOf(true) }
            var about by remember { mutableStateOf(false) }
            val player = rememberExpandablePlayerState()
            val scope = rememberCoroutineScope()
            val accent = if (followArtwork) Accent.custom("Artwork", tracks[track].artwork) else prefs.accent
            val previous = { track = (track + tracks.size - 1) % tracks.size }
            val next = { track = (track + 1) % tracks.size }

            // the shell already sets text one sp larger on tablet and DeX windows (largeWindowTextStep = 1f)
            DesktopKitRoot(prefs, accent = accent) { widthClass ->
                DesktopShell(
                    spec = spec(),
                    widthClass = widthClass,
                    navController = navController,
                    sidebarCollapsed = prefs.sidebarCollapsed,
                    onSidebarCollapsedChange = prefs::updateSidebarCollapsed,
                    badges = mapOf("files" to "128"),
                    menus = shortcuts.menus(context.resources, perform = { perform(it) }),
                    onSearch = { perform(CommonShortcuts.SEARCH) },
                    // phone: three tabs and "⋯"; the sheet lists the other screens, then these
                    moreActions = listOf(
                        ActionGroup("App", listOf(
                            MenuEntry.Item("Follow artwork colour", { followArtwork = !followArtwork }, checked = followArtwork),
                            MenuEntry.Item("About", { about = true }, icon = DeskIcons.Info),
                        )),
                    ),
                    // tablet and DeX: the transport bar floats over the content as glass
                    floatingPlayer = true,
                    bottomBar = { kind ->
                        if (kind == NavKind.BottomBar) {
                            ExpandablePlayer(
                                state = player,
                                mini = { MiniPlayer(track) },
                                full = { FullPlayer(track, onCollapse = { scope.launch { player.collapse() } }, onPrevious = previous, onNext = next) },
                                onPrevious = previous,
                                onNext = next,
                            )
                        } else {
                            TransportBar(track, onPrevious = previous, onNext = next)
                        }
                    },
                ) { route, _ ->
                    when (route) {
                        "home" -> GalleryScreen(onAbout = { about = true })
                        "files" -> FilesScreen(prefs)
                        "music" -> MusicScreen(playing = track, onPlay = { track = it })
                        "settings" -> LazyColumn(Modifier.fillMaxSize()) {
                            item { PageHeader(stringResource(desktopkit.R.string.dk_settings)) }
                            item { AppearanceSection(prefs, accentLabel = { if (it == BrandAccent) "Marka" else it.name }) }
                            item { InputSection(prefs) }
                            item { ShortcutListSection(shortcuts) }
                        }
                        else -> MessageScreen(
                            title = "Coming soon",
                            icon = spec().item(route)?.icon ?: DeskIcons.Info,
                            pageTitle = spec().item(route)?.label,
                            description = "Each app fills its screens in here.",
                        )
                    }
                }
                if (about) {
                    // "Esc" with a hardware keyboard (DeX), a × button on touch
                    DeskDialog(onDismiss = { about = false }, title = "Desktop Kit 1.0", actions = {
                        DeskButton("Close", { about = false }, primary = true)
                    }) {
                        Text(
                            "Glass bars, springs on the phone, the workstation on tablet and DeX.",
                            style = DeskTheme.type.body,
                            color = DeskTheme.colors.textSecondary,
                        )
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ 2. route keys to the table

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        shortcuts.matchNative(event)?.let { perform(it.id) } == true || super.dispatchKeyEvent(event)

    /** Meta+/ (or the system's keyboard shortcuts screen) lists the same table. */
    override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>, menu: Menu?, deviceId: Int) {
        data.addAll(shortcuts.keyboardShortcutGroups(resources))
    }
}

// ------------------------------------------------------------------ 3. navigation

@Composable
private fun spec() = remember {
    ShellSpec(
        appTitle = "Desktop Kit",
        sections = listOf(
            NavSection(
                "Library",
                listOf(
                    NavItem("home", "Components", DeskIcons.Grid),
                    NavItem("files", "Files", DeskIcons.Folder),
                    NavItem("music", "Music", DeskIcons.Music),
                ),
            ),
            NavSection(
                "Sources",
                listOf(
                    NavItem("device", "This device", DeskIcons.Device, shortLabel = "Device"),
                    NavItem("network", "Network", DeskIcons.Network),
                ),
            ),
        ),
        footer = NavItem("settings", "Settings", DeskIcons.Settings),
        // phone: three tabs and "⋯" (the glass sheet)
        bottomBar = listOf("home", "files", "music"),
        moreStyle = MoreStyle.Sheet,
    )
}

// ------------------------------------------------------------------ 4. screens

@Composable
private fun GalleryScreen(onAbout: () -> Unit) {
    var choice by remember { mutableStateOf(1) }
    var volume by remember { mutableFloatStateOf(0.6f) }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Components", subtitle = "hover, focus, right click, keys")
        Column(Modifier.padding(horizontal = DeskTheme.dimens.pagePadding), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DeskButton("Primary", {}, primary = true)
                DeskButton("With icon", {}, icon = DeskIcons.Folder)
                TooltipArea("Play", shortcut = "Space") { DeskIconButton(DeskIcons.Play, "Play", {}) }
                DeskIconButton(DeskIcons.Shuffle, "Shuffle", {}, selected = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DeskButton("Dialog", onAbout)
                // a sheet on the phone, a menu on wider windows
                MoreButton({ trackActions(tracks[0]) }, title = tracks[0].title)
            }
            SegmentedChoice(listOf(1 to "Grid", 2 to "List", 3 to "Columns"), selected = choice, onSelect = { choice = it })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Volume", style = DeskTheme.type.body, color = DeskTheme.colors.textSecondary, modifier = Modifier.width(80.dp))
                DeskSlider(volume, { volume = it }, Modifier.width(280.dp))
            }
        }
        Box(Modifier.weight(1f).padding(top = 16.dp)) {
            HorizontalSplitPane(
                state = rememberSplitState(360.dp),
                start = { FilesTable() },
                end = {
                    MessageScreen(
                        title = "Details",
                        icon = DeskIcons.Info,
                        description = "Drag the divider, or focus it and use the arrow keys.",
                    )
                },
                dividerLabel = "Resize panes",
            )
        }
    }
}

/**
 * Multi-selection (click, Ctrl+click, Shift+click, Ctrl+A, Shift+arrows; "Select" toggles rows
 * with each tap), a filter field and filter chips: on a phone the chips stay on one line that
 * scrolls sideways, and the bottom bar steps aside while the keyboard is open or the list scrolls.
 * "Open" (a tap on a phone) slides a file's details in; Back (with the predictive gesture) slides them out.
 */
@Composable
private fun FilesScreen(prefs: DeskPreferences) {
    // tablet and DeX: files | inspector with four tabs; the inspector's width is kept in the preferences
    val inspector = rememberInspectorState(prefs)
    WorkstationLayout(
        state = inspector,
        main = { FilesList(prefs, inspectorToggle = { InspectorToggleButton(inspector) }) },
        inspector = {
            InspectorPane(
                tabs = inspectorTabs,
                selected = inspector.selectedTab,
                onSelect = { inspector.selectedTab = it },
                onCollapse = inspector::toggle,
            ) { tab -> InspectorTabContent(tab) }
        },
    )
}

private val inspectorTabs = listOf(
    InspectorTab("tags", "Tags", DeskIcons.Info),
    InspectorTab("signal", "Signal path", DeskIcons.Waveform),
    InspectorTab("lyrics", "Lyrics", DeskIcons.Lyrics),
    InspectorTab("queue", "Queue", DeskIcons.Queue),
)

@Composable
private fun InspectorTabContent(tab: InspectorTab) {
    val rows = when (tab.id) {
        "tags" -> listOf("Format" to "FLAC 24/96", "Channels" to "Stereo", "ReplayGain" to "−6.2 dB", "Peak" to "0.98")
        "signal" -> listOf("Decoder" to "FLAC", "Resampler" to "Off", "Output" to "AAudio · 96 kHz", "Volume" to "Bit-perfect")
        "lyrics" -> listOf("Source" to "Embedded", "Lines" to "32")
        else -> tracks.mapIndexed { i, t -> "${i + 1}" to t.title }
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = DeskTheme.dimens.gapL, vertical = DeskTheme.dimens.gapS)) {
        itemsIndexed(rows) { _, (label, value) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(label, style = DeskTheme.type.body, color = DeskTheme.colors.textSecondary, modifier = Modifier.width(110.dp))
                Text(value, style = DeskTheme.type.body, color = DeskTheme.colors.text)
            }
        }
    }
}

@Composable
private fun FilesList(prefs: DeskPreferences, inspectorToggle: @Composable () -> Unit) {
    var filter by remember { mutableStateOf("") }
    var kinds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selected by remember { mutableStateOf<Set<Any>>(emptySet()) }
    var selectMode by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<String?>(null) }
    val compact = currentWidthClass() == WidthClass.Compact
    DetailTransition(
        detail = sampleFiles.firstOrNull { it.name == opened },
        onBack = { opened = null },
        detailKey = { it.name },
        list = {
            Column(Modifier.fillMaxSize()) {
                val subtitle = if (selected.isEmpty()) "${sampleFiles.size} items" else "${selected.size} of ${sampleFiles.size} selected"
                PageHeader("Files", subtitle = subtitle) {
                    DeskButton(if (selectMode) "Done" else "Select", { selectMode = !selectMode })
                    DeskIconButton(DeskIcons.Grid, "Grid", {})
                    DeskIconButton(DeskIcons.ListView, "List", {}, selected = true)
                    if (!compact) inspectorToggle()
                }
                FilterField(filter, { filter = it }, Modifier.padding(horizontal = DeskTheme.dimens.pagePadding).fillMaxWidth())
                FilterChips(
                    options = sampleFiles.map { it.kind }.distinct().map { kind ->
                        ChipOption(kind, kind, count = sampleFiles.count { it.kind == kind }.toString())
                    },
                    selected = kinds,
                    onToggle = { kind -> kinds = if (kind in kinds) kinds - kind else kinds + kind },
                    modifier = Modifier.padding(bottom = DeskTheme.dimens.gapS),
                )
                FilesTable(
                    prefs = prefs,
                    filter = filter,
                    kinds = kinds,
                    selectedKeys = selected,
                    onSelectionChange = { selected = it },
                    toggleOnClick = selectMode,
                    onOpen = { opened = it.name },
                    openOnClick = compact,
                )
            }
        },
    ) { file ->
        FileDetails(file, onClose = { opened = null })
    }
}

/**
 * On a phone the file name, a back arrow and the actions move into the top bar (CompactTopBar);
 * the system Back gesture closes the details too. Wider windows keep their own header.
 */
@Composable
private fun FileDetails(file: SampleFile, onClose: () -> Unit) {
    val context = LocalContext.current
    val toast = { text: String -> Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    // DetailTransition takes the Back gesture (with its predictive animation) on every window size
    CompactTopBar(
        title = file.name,
        onBack = onClose,
        actions = listOf(
            TopBarAction(DeskIcons.OpenWindow, "Open", { toast("Open") }, primary = true),
            TopBarAction(DeskIcons.PlaylistAdd, "Add to list", { toast("Add to list") }),
            TopBarAction(DeskIcons.Info, "Properties", { toast("Properties") }),
            TopBarAction(DeskIcons.Delete, "Delete", { toast("Delete") }),
        ),
    )
    Column(Modifier.fillMaxSize()) {
        PageHeader(file.name, subtitle = "${file.kind} · ${file.size}") {
            if (currentWidthClass() != WidthClass.Compact) DeskButton("Back", onClose, icon = DeskIcons.ArrowLeft)
        }
        listOf("Kind" to file.kind, "Size" to file.size, "Location" to "Files").forEach { (label, value) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = DeskTheme.dimens.pagePadding, vertical = 10.dp)) {
                Text(label, style = DeskTheme.type.body, color = DeskTheme.colors.textSecondary, modifier = Modifier.width(120.dp))
                Text(value, style = DeskTheme.type.body, color = DeskTheme.colors.text)
            }
        }
    }
}

@Composable
private fun FilterField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = DeskTheme.colors
    val shape = RoundedCornerShape(DeskTheme.dimens.radiusControl)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = DeskTheme.type.body.copy(color = colors.text),
        cursorBrush = SolidColor(colors.accent),
        modifier = modifier.padding(bottom = DeskTheme.dimens.gapS),
        decorationBox = { field ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(colors.surface, shape)
                    .border(1.dp, colors.borderStrong, shape)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                if (value.isEmpty()) Text("Filter files", style = DeskTheme.type.body, color = colors.textTertiary)
                field()
            }
        },
    )
}

private data class SampleFile(val name: String, val kind: String, val size: String)

private val sampleFiles = listOf(
    SampleFile("Budget 2026.ods", "Spreadsheet", "84 KB"),
    SampleFile("Holiday photos", "Folder", "1.2 GB"),
    SampleFile("Talk draft.odp", "Presentation", "3.4 MB"),
    SampleFile("Notes.md", "Text", "12 KB"),
    SampleFile("Recording 14.flac", "Audio", "38 MB"),
    SampleFile("Backup.zip", "Archive", "640 MB"),
)

/**
 * Single selection unless [onSelectionChange] is given. Esc clears a selection; with [prefs] the
 * header's context menu chooses, orders and remembers the columns.
 */
@Composable
private fun FilesTable(
    prefs: DeskPreferences? = null,
    filter: String = "",
    kinds: Set<String> = emptySet(),
    selectedKeys: Set<Any>? = null,
    onSelectionChange: ((Set<Any>) -> Unit)? = null,
    toggleOnClick: Boolean = false,
    onOpen: (SampleFile) -> Unit = {},
    openOnClick: Boolean = false,
) {
    var selected by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(TableSort(0, true)) }
    val rows = remember(sort, filter, kinds) {
        val sorted = when (sort.column) {
            1 -> sampleFiles.sortedBy { it.kind }
            2 -> sampleFiles.sortedBy { it.size }
            else -> sampleFiles.sortedBy { it.name }
        }.filter { filter.isBlank() || it.name.contains(filter.trim(), ignoreCase = true) }
            .filter { kinds.isEmpty() || it.kind in kinds }
        if (sort.ascending) sorted else sorted.reversed()
    }
    DenseTable(
        columns = listOf(
            TableColumn<SampleFile>("Name", ColumnWidth.Weight(1f), id = "name") { it.name },
            // phones show name and size; the kind returns from tablet width up
            TableColumn<SampleFile>("Kind", ColumnWidth.Fixed(120.dp), hideBelow = WidthClass.Medium, id = "kind") { it.kind },
            TableColumn<SampleFile>("Size", ColumnWidth.Fixed(90.dp), alignEnd = true, mono = true, id = "size") { it.size },
        ),
        rows = rows,
        key = { it.name },
        selectedKey = if (onSelectionChange == null) selected else null,
        selectedKeys = selectedKeys,
        onSelectionChange = onSelectionChange,
        toggleOnClick = toggleOnClick,
        sort = sort,
        onSortChange = { sort = it },
        onRowClick = { file, _ ->
            selected = file.name
            if (openOnClick) onOpen(file)
        },
        rowMenu = { file, _ ->
            // the table has already selected the row (or kept a selection that contains it)
            val count = selectedKeys?.size?.coerceAtLeast(1) ?: 1
            listOf(
                MenuEntry.Item("Open", { onOpen(file) }, shortcut = "Enter", icon = DeskIcons.OpenWindow),
                MenuEntry.Item("Rename", {}, shortcut = "F2", enabled = count == 1),
                MenuEntry.Separator,
                MenuEntry.Item(if (count > 1) "Delete $count items" else "Delete", {}, shortcut = "Del", icon = DeskIcons.Delete,
                    destructive = true),
            )
        },
        rowMenuLabel = "More actions",
        columnsState = if (prefs == null) null else rememberTableColumnsState("files", prefs),
        modifier = Modifier.background(DeskTheme.colors.background),
    )
}

// ------------------------------------------------------------------ 5. music and the player

private class Track(val title: String, val artist: String, val length: String, val artwork: Color)

private val tracks = listOf(
    Track("Night Drive", "Ada Ekin", "3:41", Color(0xFFE0574B)),
    Track("Paper Boats", "The Quiet Rooms", "4:05", Color(0xFFF2B84B)),
    Track("Green Line", "Lumen", "2:58", Color(0xFF59D49A)),
    Track("Blue Hour", "Sea of Glass", "5:12", Color(0xFF8AB0FF)),
    Track("Violet Static", "Kaan & The Tapes", "3:27", Color(0xFFB07AFF)),
)

/** The "⋯" actions of a track: a glass sheet on the phone, a menu on wider windows. */
private fun trackActions(track: Track) = listOf(
    ActionGroup(null, listOf(
        MenuEntry.Item("Play next", {}, icon = DeskIcons.Play),
        MenuEntry.Item("Add to queue", {}, icon = DeskIcons.Queue),
        MenuEntry.Item("Add to playlist…", {}, icon = DeskIcons.PlaylistAdd),
    )),
    ActionGroup("Go to", listOf(
        MenuEntry.Item("Artist: ${track.artist}", {}, icon = DeskIcons.Artists),
        MenuEntry.Item("Album", {}, icon = DeskIcons.Albums),
    )),
    ActionGroup(null, listOf(MenuEntry.Item("Delete from device", {}, icon = DeskIcons.Delete, destructive = true))),
)

/**
 * A list laid out under the phone's glass bars: it asks the shell for the bars' heights and pads
 * its ends with them, so the rows scroll beneath the glass and the last one can rest above the bar.
 */
@Composable
private fun MusicScreen(playing: Int, onPlay: (Int) -> Unit) {
    val padding = rememberShellContentPadding()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item { PageHeader("Music", subtitle = "${tracks.size * 8} tracks") }
        itemsIndexed(List(tracks.size * 8) { tracks[it % tracks.size] }) { i, track ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPlay(i % tracks.size) }
                    .padding(start = DeskTheme.dimens.pagePadding, end = DeskTheme.dimens.gapS)
                    .height(DeskTheme.dimens.rowHeight.coerceAtLeast(56.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).background(track.artwork, RoundedCornerShape(6.dp)))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(track.title, style = DeskTheme.type.bodyStrong,
                        color = if (i % tracks.size == playing) DeskTheme.colors.accentText else DeskTheme.colors.text, maxLines = 1)
                    Text(track.artist, style = DeskTheme.type.labelSmall, color = DeskTheme.colors.textTertiary, maxLines = 1)
                }
                Text(track.length, style = DeskTheme.type.mono, color = DeskTheme.colors.textTertiary)
                MoreButton({ trackActions(track) }, title = track.title)
            }
        }
    }
}

/** The phone's mini player: swipe sideways to skip, drag up (or tap) to open the full player. */
@Composable
private fun MiniPlayer(index: Int) {
    val track = tracks[index]
    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).background(track.artwork, RoundedCornerShape(8.dp)))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track.title, style = DeskTheme.type.bodyStrong, color = DeskTheme.colors.text, maxLines = 1)
            Text(track.artist, style = DeskTheme.type.labelSmall, color = DeskTheme.colors.textSecondary, maxLines = 1)
        }
        DeskIconButton(DeskIcons.Pause, "Pause", {})
    }
}

@Composable
private fun FullPlayer(index: Int, onCollapse: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit) {
    val track = tracks[index]
    var position by remember { mutableFloatStateOf(0.3f) }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth()) {
            DeskIconButton(DeskIcons.ChevronDown, stringResource(desktopkit.R.string.dk_player_collapse), onCollapse)
            Spacer(Modifier.weight(1f))
            MoreButton({ trackActions(track) }, title = track.title)
        }
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(track.artwork, RoundedCornerShape(16.dp)))
        Spacer(Modifier.height(24.dp))
        Text(track.title, style = DeskTheme.type.headline, color = DeskTheme.colors.text)
        Text(track.artist, style = DeskTheme.type.body, color = DeskTheme.colors.textSecondary)
        Spacer(Modifier.height(16.dp))
        DeskSlider(position, { position = it }, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            DeskIconButton(DeskIcons.Previous, "Previous", onPrevious)
            DeskIconButton(DeskIcons.Pause, "Pause", {}, size = 64.dp, iconSize = 28.dp)
            DeskIconButton(DeskIcons.Next, "Next", onNext)
        }
    }
}

/** Tablet and DeX: the transport bar inside the shell's floating glass bar (no background of its own). */
@Composable
private fun TransportBar(index: Int, onPrevious: () -> Unit, onNext: () -> Unit) {
    val track = tracks[index]
    var position by remember { mutableFloatStateOf(0.3f) }
    Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(track.artwork, RoundedCornerShape(6.dp)))
        Column(Modifier.width(200.dp).padding(horizontal = 12.dp)) {
            Text(track.title, style = DeskTheme.type.bodyStrong, color = DeskTheme.colors.text, maxLines = 1)
            Text(track.artist, style = DeskTheme.type.labelSmall, color = DeskTheme.colors.textSecondary, maxLines = 1)
        }
        DeskIconButton(DeskIcons.Previous, "Previous", onPrevious)
        DeskIconButton(DeskIcons.Pause, "Pause", {})
        DeskIconButton(DeskIcons.Next, "Next", onNext)
        DeskSlider(position, { position = it }, Modifier.weight(1f).padding(start = 12.dp))
    }
}
