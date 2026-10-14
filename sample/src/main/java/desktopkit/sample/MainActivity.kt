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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import desktopkit.components.ColumnWidth
import desktopkit.components.DenseTable
import desktopkit.components.DeskButton
import desktopkit.components.DeskIconButton
import desktopkit.components.DeskIcons
import desktopkit.components.DeskSlider
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
import desktopkit.settings.rememberDeskPreferences
import desktopkit.shell.DesktopShell
import desktopkit.shell.NavItem
import desktopkit.shell.NavSection
import desktopkit.shell.ShellNavigator
import desktopkit.shell.ShellSpec
import desktopkit.theme.Accent
import desktopkit.theme.DeskTheme
import desktopkit.theme.DeskType

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

            // every text one sp larger with a mouse or touchpad; touch sizes stay as they are
            DesktopKitRoot(prefs, type = { mode, fonts -> DeskType.forMode(mode, fonts, pointerStep = 1f) }) { widthClass ->
                DesktopShell(
                    spec = spec(),
                    widthClass = widthClass,
                    navController = navController,
                    sidebarCollapsed = prefs.sidebarCollapsed,
                    onSidebarCollapsedChange = prefs::updateSidebarCollapsed,
                    badges = mapOf("files" to "128"),
                    menus = shortcuts.menus(context.resources, perform = { perform(it) }),
                    onSearch = { perform(CommonShortcuts.SEARCH) },
                ) { route, _ ->
                    when (route) {
                        "home" -> GalleryScreen()
                        "files" -> FilesScreen()
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
            }
        }
    }

    // ------------------------------------------------------------------ 2. route keys to the table

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        shortcuts.matchNative(event)?.let { perform(it.id) } == true || super.dispatchKeyEvent(event)

    /** Meta+/ (or the system's keyboard shortcuts screen) lists the same table. */
    override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>, menu: Menu?, deviceId: Int) {
        if (android.os.Build.VERSION.SDK_INT >= 24) data.addAll(shortcuts.keyboardShortcutGroups(resources))
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
    )
}

// ------------------------------------------------------------------ 4. screens

@Composable
private fun GalleryScreen() {
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
 * with each tap) and a filter field: on a phone the bottom bar steps aside while the keyboard is open.
 */
@Composable
private fun FilesScreen() {
    var filter by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Set<Any>>(emptySet()) }
    var selectMode by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        val subtitle = if (selected.isEmpty()) "${sampleFiles.size} items" else "${selected.size} of ${sampleFiles.size} selected"
        PageHeader("Files", subtitle = subtitle) {
            DeskButton(if (selectMode) "Done" else "Select", { selectMode = !selectMode })
            DeskIconButton(DeskIcons.Grid, "Grid", {})
            DeskIconButton(DeskIcons.ListView, "List", {}, selected = true)
        }
        FilterField(filter, { filter = it }, Modifier.padding(horizontal = DeskTheme.dimens.pagePadding).fillMaxWidth())
        FilesTable(
            filter = filter,
            selectedKeys = selected,
            onSelectionChange = { selected = it },
            toggleOnClick = selectMode,
        )
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

/** Single selection unless [onSelectionChange] is given. */
@Composable
private fun FilesTable(
    filter: String = "",
    selectedKeys: Set<Any>? = null,
    onSelectionChange: ((Set<Any>) -> Unit)? = null,
    toggleOnClick: Boolean = false,
) {
    var selected by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(TableSort(0, true)) }
    val rows = remember(sort, filter) {
        val sorted = when (sort.column) {
            1 -> sampleFiles.sortedBy { it.kind }
            2 -> sampleFiles.sortedBy { it.size }
            else -> sampleFiles.sortedBy { it.name }
        }.filter { filter.isBlank() || it.name.contains(filter.trim(), ignoreCase = true) }
        if (sort.ascending) sorted else sorted.reversed()
    }
    DenseTable(
        columns = listOf(
            TableColumn<SampleFile>("Name", ColumnWidth.Weight(1f)) { it.name },
            TableColumn<SampleFile>("Kind", ColumnWidth.Fixed(120.dp)) { it.kind },
            TableColumn<SampleFile>("Size", ColumnWidth.Fixed(90.dp), alignEnd = true, mono = true) { it.size },
        ),
        rows = rows,
        key = { it.name },
        selectedKey = if (onSelectionChange == null) selected else null,
        selectedKeys = selectedKeys,
        onSelectionChange = onSelectionChange,
        toggleOnClick = toggleOnClick,
        sort = sort,
        onSortChange = { sort = it },
        onRowClick = { file, _ -> selected = file.name },
        rowMenu = { file, _ ->
            // the table has already selected the row (or kept a selection that contains it)
            val count = selectedKeys?.size?.coerceAtLeast(1) ?: 1
            listOf(
                MenuEntry.Item("Open", { selected = file.name }, shortcut = "Enter", icon = DeskIcons.OpenWindow),
                MenuEntry.Item("Rename", {}, shortcut = "F2", enabled = count == 1),
                MenuEntry.Separator,
                MenuEntry.Item(if (count > 1) "Delete $count items" else "Delete", {}, shortcut = "Del", icon = DeskIcons.Delete,
                    destructive = true),
            )
        },
        rowMenuLabel = "More actions",
        modifier = Modifier.background(DeskTheme.colors.background),
    )
}
