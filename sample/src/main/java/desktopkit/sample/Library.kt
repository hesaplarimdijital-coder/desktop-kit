package desktopkit.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import desktopkit.components.BannerTone
import desktopkit.components.CoverCell
import desktopkit.components.DeskButton
import desktopkit.components.DeskDragPayload
import desktopkit.components.DeskGrid
import desktopkit.components.DeskIcons
import desktopkit.components.DeskListRow
import desktopkit.components.MenuEntry
import desktopkit.components.MoreButton
import desktopkit.components.StatusBanner
import desktopkit.components.dropZone
import desktopkit.screens.PageHeader
import desktopkit.shell.CompactTopBar
import desktopkit.shell.DetailTransition
import desktopkit.shell.TopBarAction
import desktopkit.shell.WidthClass
import desktopkit.shell.currentWidthClass
import desktopkit.shell.detailSharedBounds
import desktopkit.shell.detailSharedElement
import desktopkit.shell.rememberShellContentPadding
import desktopkit.theme.DeskTheme
import desktopkit.theme.InputMode
import kotlinx.coroutines.delay

/** What a drop of [payload] adds to a playlist: a whole album or single tracks. */
fun songsIn(payload: DeskDragPayload): Int = when (payload.kind) {
    DragKinds.ALBUM -> payload.keys.size * 8
    else -> payload.keys.size
}

/**
 * The cover grid (K5): click opens an album, Ctrl/Shift+click and Shift+arrows select, right click
 * or "⋯" opens its menu. Covers can be dragged with a mouse onto "Playlists" in the sidebar (K6).
 * Opening an album flies its cover and title into the album's page (M3).
 */
@Composable
fun AlbumsScreen(opened: String?, onOpen: (String?) -> Unit, onPlay: (Int) -> Unit) {
    var selected by remember { mutableStateOf<Set<Any>>(emptySet()) }
    // the grid first shows breathing placeholder covers, as while a library loads
    var loading by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(900)
        loading = false
    }
    DetailTransition(
        detail = albums.firstOrNull { it.id == opened },
        onBack = { onOpen(null) },
        detailKey = { it.id },
        list = {
            Column(Modifier.fillMaxSize()) {
                val subtitle = if (selected.isEmpty()) "${albums.size} albums" else "${selected.size} of ${albums.size} selected"
                PageHeader("Albums", subtitle = subtitle)
                DeskGrid(
                    items = if (loading) emptyList() else albums,
                    key = { it.id },
                    onItemClick = { album, _ -> onOpen(album.id) },
                    selectedKeys = selected,
                    onSelectionChange = { selected = it },
                    itemMenu = { album, _ ->
                        listOf(
                            MenuEntry.Item("Open", { onOpen(album.id) }, shortcut = "Enter", icon = DeskIcons.Albums),
                            MenuEntry.Item("Play", { onPlay(albums.indexOf(album) % tracks.size) }, icon = DeskIcons.Play),
                            MenuEntry.Item("Add to queue", {}, icon = DeskIcons.Queue),
                        )
                    },
                    loading = loading,
                    itemDrag = { album, _ ->
                        val ids = if (album.id in selected) selected.map { it as String } else listOf(album.id)
                        DeskDragPayload(DragKinds.ALBUM, ids, if (ids.size == 1) album.title else "${ids.size} albums")
                    },
                ) { album ->
                    CoverCell(
                        album.title,
                        subtitle = "${album.artist} · ${album.year}",
                        onPlay = { onPlay(albums.indexOf(album) % tracks.size) },
                        playLabel = "Play ${album.title}",
                        titleModifier = Modifier.detailSharedBounds("title-${album.id}"),
                    ) {
                        Box(Modifier.detailSharedElement("cover-${album.id}").fillMaxSize().background(album.artwork))
                    }
                }
            }
        },
    ) { album ->
        AlbumPage(album, onBack = { onOpen(null) }, onPlay = onPlay)
    }
}

/** An album's page: the cover and the title that flew in, the tracks (draggable onto "Playlists"). */
@Composable
private fun AlbumPage(album: Album, onBack: () -> Unit, onPlay: (Int) -> Unit) {
    val compact = currentWidthClass() == WidthClass.Compact
    CompactTopBar(
        title = album.title,
        onBack = onBack,
        actions = listOf(TopBarAction(DeskIcons.Play, "Play", { onPlay(0) }, primary = true)),
    )
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            val cover = @Composable { size: androidx.compose.ui.unit.Dp ->
                Box(Modifier.detailSharedElement("cover-${album.id}", shape = RoundedCornerShape(12.dp)).size(size).background(album.artwork))
            }
            val about = @Composable {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(album.title, style = DeskTheme.type.headline, color = DeskTheme.colors.text,
                        modifier = Modifier.detailSharedBounds("title-${album.id}"))
                    Text("${album.artist} · ${album.year} · ${album.songs.size} songs", style = DeskTheme.type.body,
                        color = DeskTheme.colors.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DeskButton("Play", { onPlay(0) }, icon = DeskIcons.Play, primary = true)
                        DeskButton("Shuffle", {}, icon = DeskIcons.Shuffle)
                        if (!compact) DeskButton("Back", onBack, icon = DeskIcons.ArrowLeft)
                    }
                }
            }
            if (compact) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    cover(232.dp)
                    Spacer(Modifier.height(16.dp))
                    about()
                }
            } else {
                Row(Modifier.padding(DeskTheme.dimens.pagePadding), verticalAlignment = Alignment.Bottom) {
                    cover(232.dp)
                    Spacer(Modifier.size(24.dp))
                    about()
                }
            }
        }
        itemsIndexed(album.songs) { i, song ->
            DeskListRow(
                title = "${i + 1}. ${song.title}",
                subtitle = song.artist,
                trailing = song.length,
                onClick = { onPlay(tracks.indexOfFirst { it.title == song.title }.coerceAtLeast(0)) },
                trailingContent = { MoreButton({ trackActions(song) }, title = song.title) },
                drag = { DeskDragPayload(DragKinds.TRACK, listOf("${album.id}/$i"), song.title) },
            )
        }
    }
}

/**
 * A list laid out under the phone's glass bars: it asks the shell for the bars' heights and pads
 * its ends with them, so the rows scroll beneath the glass and the last one can rest above the bar.
 */
@Composable
fun TracksScreen(playing: Int, onPlay: (Int) -> Unit) {
    val padding = rememberShellContentPadding()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item { PageHeader("Tracks", subtitle = "${tracks.size * 8} tracks") }
        itemsIndexed(List(tracks.size * 8) { tracks[it % tracks.size] }) { i, track ->
            DeskListRow(
                title = track.title,
                subtitle = track.artist,
                selected = i % tracks.size == playing,
                leadingContent = { Box(Modifier.size(40.dp).background(track.artwork, RoundedCornerShape(6.dp))) },
                trailing = track.length,
                trailingContent = { MoreButton({ trackActions(track) }, title = track.title) },
                onClick = { onPlay(i % tracks.size) },
                drag = { DeskDragPayload(DragKinds.TRACK, listOf("track-$i"), track.title) },
            )
        }
    }
}

/**
 * Playlists take albums and tracks dropped on them here, or on "Playlists" in the sidebar (with a
 * mouse: the hint shows only then). A tap opens a playlist's songs.
 */
@Composable
fun PlaylistsScreen(library: LibraryState, onPlay: (Int) -> Unit) {
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    DetailTransition(
        detail = library.playlists.firstOrNull { it.name == opened },
        onBack = { opened = null },
        detailKey = { it.name },
        sharedElements = false,
        list = {
            Column(Modifier.fillMaxSize()) {
                PageHeader("Playlists", subtitle = "${library.playlists.size} playlists")
                if (DeskTheme.inputMode == InputMode.Pointer) {
                    StatusBanner(
                        BannerTone.Info,
                        "With a mouse, drag albums or tracks onto a playlist, or onto “Playlists” in the sidebar.",
                        Modifier.padding(horizontal = DeskTheme.dimens.pagePadding).padding(bottom = DeskTheme.dimens.gapS),
                    )
                }
                library.playlists.forEachIndexed { index, playlist ->
                    DeskListRow(
                        title = playlist.name,
                        subtitle = "${playlist.count} songs",
                        leading = DeskIcons.Queue,
                        onClick = { opened = playlist.name },
                        modifier = Modifier.dropZone(
                            setOf(DragKinds.ALBUM, DragKinds.TRACK),
                            onDrop = { payload ->
                                library.addToPlaylist(index, songsIn(payload))
                                true
                            },
                        ),
                    )
                }
            }
        },
    ) { playlist ->
        PlaylistPage(playlist, onBack = { opened = null }, onPlay = onPlay)
    }
}

/** A playlist's songs (the sample's tracks over and over, as many as the playlist holds). */
@Composable
private fun PlaylistPage(playlist: Playlist, onBack: () -> Unit, onPlay: (Int) -> Unit) {
    CompactTopBar(
        title = playlist.name,
        onBack = onBack,
        actions = listOf(TopBarAction(DeskIcons.Play, "Play", { onPlay(0) }, primary = true)),
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = rememberShellContentPadding()) {
        item {
            PageHeader(playlist.name, subtitle = "${playlist.count} songs") {
                DeskButton("Play", { onPlay(0) }, icon = DeskIcons.Play, primary = true)
                if (currentWidthClass() != WidthClass.Compact) DeskButton("Back", onBack, icon = DeskIcons.ArrowLeft)
            }
        }
        itemsIndexed(List(playlist.count) { tracks[it % tracks.size] }) { i, track ->
            DeskListRow(
                title = track.title,
                subtitle = track.artist,
                leadingContent = { Box(Modifier.size(40.dp).background(track.artwork, RoundedCornerShape(6.dp))) },
                trailing = track.length,
                onClick = { onPlay(i % tracks.size) },
            )
        }
    }
}

/** Files dropped on "Bin" in the sidebar (the hint shows with a mouse), with a way back. */
@Composable
fun BinScreen(library: LibraryState) {
    Column(Modifier.fillMaxSize()) {
        PageHeader("Bin", subtitle = if (library.binned.isEmpty()) "Empty" else "${library.binned.size} files") {
            if (library.binned.isNotEmpty()) DeskButton("Restore all", { library.binned.clear() }, icon = DeskIcons.Undo)
        }
        if (library.binned.isEmpty() && DeskTheme.inputMode == InputMode.Pointer) {
            StatusBanner(
                BannerTone.Info,
                "With a mouse, drag files from “Files” onto “Bin” in the sidebar.",
                Modifier.padding(horizontal = DeskTheme.dimens.pagePadding),
            )
        }
        library.binned.toList().forEach { name ->
            DeskListRow(
                title = name,
                leading = DeskIcons.Delete,
                trailingContent = { DeskButton("Restore", { library.binned.remove(name) }) },
            )
        }
    }
}
