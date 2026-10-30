package desktopkit.sample

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import desktopkit.components.ChipOption
import desktopkit.components.ColumnWidth
import desktopkit.components.CoverCell
import desktopkit.components.DenseTable
import desktopkit.components.DeskButton
import desktopkit.components.DeskDragPayload
import desktopkit.components.DeskGrid
import desktopkit.components.DeskIconButton
import desktopkit.components.DeskIcons
import desktopkit.components.DeskSearchField
import desktopkit.components.FilterChips
import desktopkit.components.InspectorPane
import desktopkit.components.InspectorTab
import desktopkit.components.InspectorToggleButton
import desktopkit.components.KeyValueRow
import desktopkit.components.MenuEntry
import desktopkit.components.TableColumn
import desktopkit.components.TableSort
import desktopkit.components.WorkstationLayout
import desktopkit.components.rememberInspectorState
import desktopkit.components.rememberTableColumnsState
import desktopkit.screens.PageHeader
import desktopkit.settings.DeskPreferences
import desktopkit.shell.CompactTopBar
import desktopkit.shell.DetailTransition
import desktopkit.shell.TopBarAction
import desktopkit.shell.WidthClass
import desktopkit.shell.currentWidthClass
import desktopkit.theme.DeskTheme
import desktopkit.theme.InputMode

/**
 * Multi-selection (click, Ctrl+click, Shift+click, Ctrl+A, Shift+arrows; "Select" toggles rows
 * with each tap), a search field and filter chips: on a phone the chips stay on one line that
 * scrolls sideways, and the bottom bar steps aside while the keyboard is open or the list scrolls.
 * "Open" (a tap on a phone) slides a file's details in; Back (with the predictive gesture) slides them
 * out. With a mouse, rows can be dragged onto "Bin" in the sidebar.
 */
@Composable
fun FilesScreen(prefs: DeskPreferences, library: LibraryState) {
    // tablet and DeX: files | inspector with four tabs; the inspector's width is kept in the preferences
    val inspector = rememberInspectorState(prefs)
    WorkstationLayout(
        state = inspector,
        main = { FilesList(prefs, library, inspectorToggle = { InspectorToggleButton(inspector) }) },
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
            KeyValueRow(label, value, Modifier.padding(vertical = 6.dp), mono = tab.id == "signal")
        }
    }
}

@Composable
private fun FilesList(prefs: DeskPreferences, library: LibraryState, inspectorToggle: @Composable () -> Unit) {
    var filter by remember { mutableStateOf("") }
    var kinds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selected by remember { mutableStateOf<Set<Any>>(emptySet()) }
    var selectMode by remember { mutableStateOf(false) }
    var grid by rememberSaveable { mutableStateOf(false) }
    var opened by remember { mutableStateOf<String?>(null) }
    val compact = currentWidthClass() == WidthClass.Compact
    // a finger opens a file with a tap (the kit leaves the selection alone); a mouse click selects,
    // and "Open" is in the menu
    val openOnTap = compact || DeskTheme.inputMode == InputMode.Touch
    val files = sampleFiles.filter { it.name !in library.binned }
    DetailTransition(
        detail = files.firstOrNull { it.name == opened },
        onBack = { opened = null },
        detailKey = { it.name },
        // nothing flies between the table and the details
        sharedElements = false,
        list = {
            Column(Modifier.fillMaxSize()) {
                val subtitle = if (selected.isEmpty()) "${files.size} items" else "${selected.size} of ${files.size} selected"
                PageHeader("Files", subtitle = subtitle) {
                    DeskButton(if (selectMode) "Done" else "Select", { selectMode = !selectMode })
                    DeskIconButton(DeskIcons.Grid, "Grid", { grid = true }, selected = grid)
                    DeskIconButton(DeskIcons.ListView, "List", { grid = false }, selected = !grid)
                    if (!compact) inspectorToggle()
                }
                DeskSearchField(filter, { filter = it },
                    Modifier.padding(horizontal = DeskTheme.dimens.pagePadding).padding(bottom = DeskTheme.dimens.gapS).fillMaxWidth(),
                    hint = "Filter files")
                FilterChips(
                    options = files.map { it.kind }.distinct().map { kind ->
                        ChipOption(kind, kind, count = files.count { it.kind == kind }.toString())
                    },
                    selected = kinds,
                    onToggle = { kind -> kinds = if (kind in kinds) kinds - kind else kinds + kind },
                    modifier = Modifier.padding(bottom = DeskTheme.dimens.gapS),
                )
                if (grid) {
                    FilesGrid(
                        files = files,
                        filter = filter,
                        kinds = kinds,
                        selectedKeys = selected,
                        onSelectionChange = { selected = it },
                        toggleOnClick = selectMode,
                        onOpen = { opened = it.name },
                        openOnClick = openOnTap,
                    )
                } else {
                    FilesTable(
                        files = files,
                        prefs = prefs,
                        filter = filter,
                        kinds = kinds,
                        selectedKeys = selected,
                        onSelectionChange = { selected = it },
                        toggleOnClick = selectMode,
                        onOpen = { opened = it.name },
                        openOnClick = openOnTap,
                    )
                }
            }
        },
    ) { file ->
        FileDetails(file, onClose = { opened = null })
    }
}

/** The files as tiles, by kind: the table's selection, menu and drag (with a mouse, onto "Bin"). */
@Composable
private fun FilesGrid(
    files: List<SampleFile>,
    filter: String,
    kinds: Set<String>,
    selectedKeys: Set<Any>,
    onSelectionChange: (Set<Any>) -> Unit,
    toggleOnClick: Boolean,
    onOpen: (SampleFile) -> Unit,
    openOnClick: Boolean,
) {
    val shown = files
        .filter { filter.isBlank() || it.name.contains(filter.trim(), ignoreCase = true) }
        .filter { kinds.isEmpty() || it.kind in kinds }
        .sortedBy { it.name }
    DeskGrid(
        items = shown,
        key = { it.name },
        onItemClick = { file, _ -> if (openOnClick) onOpen(file) },
        minCellWidth = 112.dp,
        selectedKeys = selectedKeys,
        onSelectionChange = onSelectionChange,
        toggleOnClick = toggleOnClick,
        itemMenu = { file, _ -> listOf(MenuEntry.Item("Open", { onOpen(file) }, shortcut = "Enter", icon = DeskIcons.OpenWindow)) },
        itemMenuLabel = "More actions",
        itemDrag = { file, _ ->
            val names = if (file.name in selectedKeys) selectedKeys.map { it as String } else listOf(file.name)
            DeskDragPayload(DragKinds.FILE, names, if (names.size == 1) names[0] else "${names.size} files")
        },
    ) { file ->
        CoverCell(file.name, subtitle = "${file.kind} · ${file.size}") {
            Box(Modifier.fillMaxSize().background(DeskTheme.colors.surface), contentAlignment = Alignment.Center) {
                Icon(kindIcon(file.kind), contentDescription = null, tint = DeskTheme.colors.textSecondary, modifier = Modifier.size(36.dp))
            }
        }
    }
}

private fun kindIcon(kind: String) = when (kind) {
    "Folder" -> DeskIcons.Folder
    "Audio" -> DeskIcons.Music
    "Text" -> DeskIcons.Lyrics
    "Spreadsheet" -> DeskIcons.Grid
    "Presentation" -> DeskIcons.Screenshot
    "Archive" -> DeskIcons.Downloads
    else -> DeskIcons.Info
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
        Column(Modifier.padding(horizontal = DeskTheme.dimens.pagePadding)) {
            listOf("Kind" to file.kind, "Size" to file.size, "Location" to "/storage/emulated/0/Files/${file.name}").forEach { (label, value) ->
                KeyValueRow(label, value, Modifier.padding(vertical = 8.dp), mono = label == "Location", selectable = label == "Location")
            }
        }
    }
}

/**
 * Single selection unless [onSelectionChange] is given. Esc clears a selection; with [prefs] the
 * header's context menu chooses, orders and remembers the columns. With a mouse a row (or the
 * selection it is in) can be dragged to "Bin" in the sidebar.
 */
@Composable
fun FilesTable(
    files: List<SampleFile> = sampleFiles,
    prefs: DeskPreferences? = null,
    filter: String = "",
    kinds: Set<String> = emptySet(),
    selectedKeys: Set<Any>? = null,
    onSelectionChange: ((Set<Any>) -> Unit)? = null,
    toggleOnClick: Boolean = false,
    onOpen: (SampleFile) -> Unit = {},
    openOnClick: Boolean = false,
    loading: Boolean = false,
) {
    var selected by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(TableSort(0, true)) }
    val rows = remember(files, sort, filter, kinds) {
        val sorted = when (sort.column) {
            1 -> files.sortedBy { it.kind }
            2 -> files.sortedBy { it.size }
            else -> files.sortedBy { it.name }
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
        rows = if (loading) emptyList() else rows,
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
        rowDrag = { file, _ ->
            // a row inside the selection drags the whole selection
            val names = if (selectedKeys != null && file.name in selectedKeys) selectedKeys.map { it as String } else listOf(file.name)
            DeskDragPayload(DragKinds.FILE, names, if (names.size == 1) names[0] else "${names.size} files")
        },
        loading = loading,
        columnsState = if (prefs == null) null else rememberTableColumnsState("files", prefs),
        modifier = Modifier.background(DeskTheme.colors.background),
    )
}
