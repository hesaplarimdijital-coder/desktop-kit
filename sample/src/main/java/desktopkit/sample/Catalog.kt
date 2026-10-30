package desktopkit.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import desktopkit.components.BannerTone
import desktopkit.components.CheckboxRow
import desktopkit.components.ChipOption
import desktopkit.components.CountBadge
import desktopkit.components.DeskButton
import desktopkit.components.DeskCard
import desktopkit.components.DeskCheckbox
import desktopkit.components.DeskChip
import desktopkit.components.DeskDragPayload
import desktopkit.components.DeskIconButton
import desktopkit.components.DeskIcons
import desktopkit.components.DeskListRow
import desktopkit.components.DeskProgressBar
import desktopkit.components.DeskSearchField
import desktopkit.components.DeskSlider
import desktopkit.components.DeskSwitch
import desktopkit.components.DeskTab
import desktopkit.components.DeskTextField
import desktopkit.components.ToastState
import desktopkit.components.FilterChips
import desktopkit.components.GlassBottomSheet
import desktopkit.components.HorizontalRule
import desktopkit.components.IconMenuButton
import desktopkit.components.KeyValueRow
import desktopkit.components.MenuButton
import desktopkit.components.MenuEntry
import desktopkit.components.MoreButton
import desktopkit.components.NumberField
import desktopkit.components.OptionalNumberField
import desktopkit.components.SegmentedChoice
import desktopkit.components.SelectionBar
import desktopkit.components.SettingRow
import desktopkit.components.SkeletonList
import desktopkit.components.StatusBanner
import desktopkit.components.SwatchChoice
import desktopkit.components.SwitchRow
import desktopkit.components.TabStrip
import desktopkit.components.ToastDuration
import desktopkit.components.TooltipArea
import desktopkit.components.dropZone
import desktopkit.screens.PageHeader
import desktopkit.settings.AppearanceSection
import desktopkit.settings.DeskPreferences
import desktopkit.settings.InputSection
import desktopkit.shell.rememberNewWindow
import desktopkit.shell.rememberShellContentPadding
import desktopkit.theme.Accent
import desktopkit.theme.DeskTheme
import desktopkit.theme.rememberDeskHaptics
import kotlinx.coroutines.launch

/**
 * Every component of the kit in each of its states, under the theme, accent and density chosen at
 * the top (three themes, touch and pointer sizes). Hover with a mouse, Tab with a keyboard and press
 * to see the interaction states; the disabled, error and selected states are shown side by side.
 */
@Composable
fun CatalogScreen(prefs: DeskPreferences, toasts: ToastState, onDialog: () -> Unit, accentLabel: @Composable (Accent) -> String) {
    val padding = rememberShellContentPadding()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item { PageHeader("Components", subtitle = "hover, Tab, press; disabled, error and selected side by side") }
        item {
            Column(Modifier.widthIn(max = 980.dp)) {
                AppearanceSection(prefs, accentLabel = accentLabel)
                InputSection(prefs)
            }
        }
        item { Section("Buttons") { Buttons() } }
        item { Section("Choices") { Choices() } }
        item { Section("Text") { TextInputs() } }
        item { Section("Check boxes and switches") { Toggles() } }
        item { Section("Progress, banners, badges, toasts") { Feedback(toasts) } }
        item { Section("Cards, rows, tabs") { Structure(toasts) } }
        item { Section("Loading") { Loading() } }
        item { Section("Drag and drop, windows") { DragAndWindows(toasts) } }
        item { Section("Sheets, dialogs, haptics") { Overlays(onDialog) } }
    }
}

/** A titled card, at most 980 dp wide. */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    DeskCard(
        Modifier
            .widthIn(max = 980.dp)
            .fillMaxWidth()
            .padding(horizontal = DeskTheme.dimens.pagePadding, vertical = DeskTheme.dimens.gapS),
        title = title,
        contentPadding = PaddingValues(DeskTheme.dimens.gapL),
        verticalArrangement = Arrangement.spacedBy(DeskTheme.dimens.gapL),
        content = content,
    )
}

/** A row of examples that wraps on narrow windows. */
@Composable
private fun Examples(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, style = DeskTheme.type.labelSmall, color = DeskTheme.colors.textTertiary, modifier = modifier)
}

// ------------------------------------------------------------------ buttons

@Composable
private fun Buttons() {
    Examples {
        DeskButton("Button", {})
        DeskButton("With icon", {}, icon = DeskIcons.Folder)
        DeskButton("Primary", {}, primary = true)
        DeskButton("Disabled", {}, enabled = false)
        DeskButton("Primary, disabled", {}, primary = true, enabled = false)
        MenuButton("Menu button", entries = {
            listOf(MenuEntry.Item("First", {}), MenuEntry.Item("Second", {}, shortcut = "Ctrl+2"), MenuEntry.Separator,
                MenuEntry.Item("Delete", {}, icon = DeskIcons.Delete, destructive = true))
        })
    }
    Examples {
        TooltipArea("Play", shortcut = "Space") { DeskIconButton(DeskIcons.Play, "Play", {}) }
        DeskIconButton(DeskIcons.Shuffle, "Shuffle (on)", {}, selected = true)
        DeskIconButton(DeskIcons.Delete, "Delete (disabled)", {}, enabled = false)
        IconMenuButton(DeskIcons.Sort, "Sort", entries = { listOf(MenuEntry.Item("Name", {}, checked = true), MenuEntry.Item("Size", {})) })
        MoreButton({ trackActions(tracks[0]) }, title = tracks[0].title)
    }
    Caption("Hover with a mouse, Tab with a keyboard, press with a finger; on touch the targets grow to 48 dp.")
}

// ------------------------------------------------------------------ choices

@Composable
private fun Choices() {
    var segment by remember { mutableIntStateOf(1) }
    var swatch by remember { mutableIntStateOf(1) }
    var kinds by remember { mutableStateOf(setOf("audio")) }
    SegmentedChoice(listOf(1 to "Grid", 2 to "List", 3 to "Columns"), selected = segment, onSelect = { segment = it })
    SwatchChoice(
        listOf(Triple(1, Accent.Blue.base, "Blue"), Triple(2, Accent.Amber.base, "Amber"), Triple(3, Accent.Green.base, "Green"),
            Triple(4, Accent.Coral.base, "Coral")),
        selected = swatch, onSelect = { swatch = it },
    )
    FilterChips(
        options = listOf(ChipOption("audio", "Audio", count = "42"), ChipOption("video", "Video", count = "7"),
            ChipOption("text", "Documents", icon = DeskIcons.Folder), ChipOption("archive", "Archives")),
        selected = kinds,
        onToggle = { kind -> kinds = if (kind in kinds) kinds - kind else kinds + kind },
        wrap = true,
        contentPadding = 0.dp,
    )
    Examples {
        DeskChip("Disabled", selected = false, onClick = {}, enabled = false)
        DeskChip("Selected, disabled", selected = true, onClick = {}, enabled = false)
    }
}

// ------------------------------------------------------------------ text

@Composable
private fun TextInputs() {
    var name by remember { mutableStateOf("Winter Garden") }
    var search by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("hunter2") }
    var folder by remember { mutableStateOf("/storage/2A1F-0C33/Music") }
    var notes by remember { mutableStateOf("") }
    var buffer by remember { mutableIntStateOf(9000) }
    var minSize by remember { mutableStateOf<Long?>(null) }
    DeskSearchField(search, { search = it }, Modifier.widthIn(max = 420.dp).fillMaxWidth())
    DeskTextField(name, { name = it }, Modifier.widthIn(max = 420.dp).fillMaxWidth(), label = "Album title", clearable = true,
        supportingText = "Shown in the library and on the device")
    DeskTextField(folder, { folder = it }, Modifier.fillMaxWidth(), label = "Music folder", leading = DeskIcons.Folder, mono = true,
        error = !folder.startsWith("/storage"), supportingText = if (folder.startsWith("/storage")) null else "Not a folder on this device")
    Examples {
        DeskTextField(password, { password = it }, Modifier.width(240.dp), label = "Password", password = true)
        DeskTextField("Read only", {}, Modifier.width(200.dp), label = "Read only", readOnly = true)
        DeskTextField("Disabled", {}, Modifier.width(200.dp), label = "Disabled", enabled = false)
    }
    Examples {
        NumberField(buffer, { buffer = it }, label = "Pre-buffer", min = 0, max = 5000, step = 250, suffix = "ms", steppers = true)
        OptionalNumberField(minSize, { minSize = it }, Modifier.width(180.dp), hint = "Any size", label = "At least", suffix = "kB")
    }
    DeskTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = "Notes", hint = "Several lines", singleLine = false, minLines = 3)
}

// ------------------------------------------------------------------ toggles

@Composable
private fun Toggles() {
    var on by remember { mutableStateOf(true) }
    val formats = remember { mutableStateListOf(true, false, true) }
    val all = when {
        formats.all { it } -> ToggleableState.On
        formats.none { it } -> ToggleableState.Off
        else -> ToggleableState.Indeterminate
    }
    Examples {
        DeskCheckbox(checked = true, onCheckedChange = {})
        DeskCheckbox(checked = false, onCheckedChange = {})
        DeskCheckbox(state = ToggleableState.Indeterminate, onClick = {})
        DeskCheckbox(checked = true, onCheckedChange = {}, enabled = false)
        Spacer(Modifier.width(16.dp))
        DeskSwitch(checked = on, onCheckedChange = { on = it })
        DeskSwitch(checked = !on, onCheckedChange = { on = !it })
        DeskSwitch(checked = true, onCheckedChange = {}, enabled = false)
    }
    Caption("A check box or switch on its own needs a name for TalkBack: use the rows below, or Modifier.semantics.")
    Column {
        SwitchRow("Send audio straight to the DAC", checked = on, onCheckedChange = { on = it },
            subtitle = "The DAC sets the volume; notification sounds are muted meanwhile")
        HorizontalRule()
        CheckboxRow("All formats", all, onClick = {
            val target = all != ToggleableState.On
            for (i in formats.indices) formats[i] = target
        })
        listOf("Lossless: FLAC, ALAC, WAV", "Lossy: MP3, AAC", "Audiobooks").forEachIndexed { i, label ->
            CheckboxRow(label, formats[i], { formats[i] = it }, Modifier.padding(start = 24.dp))
        }
        CheckboxRow("Disabled", checked = false, onCheckedChange = {}, enabled = false)
    }
}

// ------------------------------------------------------------------ feedback

@Composable
private fun Feedback(toasts: ToastState) {
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DeskProgressBar(0.64f, contentDescription = "Scanning")
        DeskProgressBar(null, contentDescription = "Connecting")
        DeskProgressBar(0.3f, color = DeskTheme.colors.success, contentDescription = "Copied")
    }
    StatusBanner(BannerTone.Success, title = "USB DAC connected, playing lossless", text = "24-bit / 192 kHz · Android's mixer is bypassed",
        actionLabel = "Open report", onAction = {})
    StatusBanner(BannerTone.Info, "The library is scanned again every night.")
    StatusBanner(BannerTone.Warning, "The shared folder was not found; the scan stopped half way.", actionLabel = "Retry", onAction = {},
        closeLabel = "Close", onClose = {})
    StatusBanner(BannerTone.Error, "Could not write to the SD card.", closeLabel = "Close", onClose = {})
    Examples {
        Text("Badges", style = DeskTheme.type.body, color = DeskTheme.colors.textSecondary)
        CountBadge(3)
        CountBadge(42)
        CountBadge(120)
        CountBadge(7, color = DeskTheme.colors.danger)
    }
    Examples {
        DeskButton("Toast", { scope.launch { toasts.show("Library updated") } })
        DeskButton("With undo", {
            scope.launch {
                toasts.show("“Night Drive” added to the queue · 9 tracks", actionLabel = "Undo", tone = BannerTone.Success,
                    shortcut = "Ctrl+Z")
            }
        })
        DeskButton("Error", {
            scope.launch { toasts.show("Could not reach the server", tone = BannerTone.Error, duration = ToastDuration.Long) }
        })
    }
}

// ------------------------------------------------------------------ structure

@Composable
private fun Structure(toasts: ToastState) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("albums") }
    val tabs = remember { mutableStateListOf("Artists", "Albums", "Tracks", "Search: winter") }
    var selecting by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(setOf(1)) }
    Examples {
        DeskCard(Modifier.width(240.dp), title = "Output") {
            KeyValueRow("Device", "USB DAC")
            KeyValueRow("Format", "FLAC 24/96", mono = true)
        }
        DeskCard(Modifier.width(240.dp), onClick = { scope.launch { toasts.show("Card clicked") } }) {
            Text("Clickable card", style = DeskTheme.type.bodyStrong, color = DeskTheme.colors.text)
            Text("Hover, focus and press it", style = DeskTheme.type.labelSmall, color = DeskTheme.colors.textTertiary)
        }
    }
    KeyValueRow("Location", "/storage/2A1F-0C33/Music/Mavi Hat/Winter Garden/01 Night Drive.flac", mono = true, selectable = true)
    TabStrip(contentPadding = PaddingValues(0.dp)) {
        tabs.forEachIndexed { i, label ->
            DeskTab(
                label,
                selected = tab == label.lowercase(),
                onClick = { tab = label.lowercase() },
                badge = if (i == 1) 12 else 0,
                busy = i == 2,
                onClose = if (i == 3) ({ tabs.remove(label) }) else null,
                drag = { DeskDragPayload(DragKinds.TAB, listOf(label), label) },
            )
        }
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // the caption takes what the button leaves (on a phone it wraps instead of pushing the button out)
            Caption("List rows (phones and any list that is not a table)", Modifier.weight(1f))
            DeskButton(if (selecting) "Done" else "Select", { selecting = !selecting })
        }
        albums.take(4).forEachIndexed { i, album ->
            DeskListRow(
                title = album.title,
                subtitle = "${album.artist} · ${album.year}",
                leadingContent = { Box(Modifier.size(40.dp).background(album.artwork, RoundedCornerShape(6.dp))) },
                trailing = "%d:%02d".format(38 + i, (i * 17) % 60),
                trailingSub = "${album.songs.size} songs",
                progress = if (i == 2) 0.4f else null,
                selectionMode = selecting,
                selected = selecting && i in picked,
                enabled = i != 3,
                onClick = { if (selecting) picked = if (i in picked) picked - i else picked + i },
                onLongClick = { selecting = true; picked = setOf(i) },
                menu = { listOf(MenuEntry.Item("Play", {}, icon = DeskIcons.Play), MenuEntry.Item("Add to playlist…", {})) },
            )
        }
        if (selecting) {
            SelectionBar("${picked.size} selected", onClose = { selecting = false }) {
                IconMenuButton(DeskIcons.PlaylistAdd, "Add to playlist", entries = { emptyList() })
                DeskIconButton(DeskIcons.Delete, "Delete", {})
            }
        }
    }
}

// ------------------------------------------------------------------ loading

@Composable
private fun Loading() {
    var loading by remember { mutableStateOf(true) }
    SwitchRow("Loading", checked = loading, onCheckedChange = { loading = it }, subtitle = "Breathing placeholders until the rows arrive")
    Box(Modifier.fillMaxWidth().height(220.dp)) {
        FilesTable(loading = loading)
    }
    if (loading) SkeletonList(count = 3)
}

// ------------------------------------------------------------------ drag and drop, windows

@Composable
private fun DragAndWindows(toasts: ToastState) {
    val scope = rememberCoroutineScope()
    var dropped by remember { mutableStateOf<String?>(null) }
    val newWindow = rememberNewWindow()
    Caption("With a mouse, drag a tab above, a row of the Files table or an album here, from this window or another one.")
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .background(DeskTheme.colors.background, RoundedCornerShape(DeskTheme.dimens.radiusCard))
            .dropZone(
                setOf(DragKinds.TAB, DragKinds.FILE, DragKinds.ALBUM, DragKinds.TRACK),
                onDrop = { payload ->
                    dropped = "${payload.kind}: ${payload.label}"
                    scope.launch { toasts.show("Dropped ${payload.label}") }
                    true
                },
                shape = RoundedCornerShape(DeskTheme.dimens.radiusCard),
            )
            .padding(DeskTheme.dimens.gapL),
        contentAlignment = Alignment.Center,
    ) {
        Text(dropped ?: "Drop here", style = DeskTheme.type.body, color = DeskTheme.colors.textSecondary)
    }
    if (newWindow != null) {
        SettingRow("Another window", subtitle = "Ctrl+N; on a tablet it opens next to this one") {
            DeskButton("New window", { newWindow("catalog") }, icon = DeskIcons.NewWindow)
        }
    }
}

// ------------------------------------------------------------------ overlays, motion, haptics

@Composable
private fun Overlays(onDialog: () -> Unit) {
    var sheet by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(0.6f) }
    val haptics = rememberDeskHaptics()
    Examples {
        DeskButton("Dialog", onDialog)
        DeskButton("Glass sheet", { sheet = true })
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Volume", style = DeskTheme.type.body, color = DeskTheme.colors.textSecondary, modifier = Modifier.width(80.dp))
        DeskSlider(volume, { volume = it }, Modifier.widthIn(max = 320.dp).weight(1f), label = "Volume",
            valueText = { "${(it * 100).toInt()} %" })
    }
    Caption("Haptics (touch only): the kit's switches, chips, sheets and drops use these.")
    Examples {
        DeskButton("Tick", { haptics.tick() })
        DeskButton("Threshold", { haptics.threshold() })
        DeskButton("Confirm", { haptics.confirm() })
        DeskButton("Reject", { haptics.reject() })
    }
    GlassBottomSheet(visible = sheet, onDismiss = { sheet = false }, title = "Glass sheet") {
        Text(
            "Swipe it down, press Back or Esc, or tap outside.",
            style = DeskTheme.type.body,
            color = DeskTheme.colors.textSecondary,
            modifier = Modifier.padding(horizontal = DeskTheme.dimens.pagePadding, vertical = DeskTheme.dimens.gapL),
        )
        Spacer(Modifier.height(24.dp))
    }
}
