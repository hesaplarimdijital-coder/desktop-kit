package desktopkit.sample

import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.Menu
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.rememberNavController
import desktopkit.DesktopKitRoot
import desktopkit.components.ActionGroup
import desktopkit.components.CommandPalette
import desktopkit.components.DeskButton
import desktopkit.components.DeskDialog
import desktopkit.components.DeskIcons
import desktopkit.components.ExpandablePlayer
import desktopkit.components.MenuEntry
import desktopkit.components.PaletteItem
import desktopkit.components.ToastResult
import desktopkit.components.paletteItems
import desktopkit.components.rememberCommandPaletteState
import desktopkit.components.rememberExpandablePlayerState
import desktopkit.components.rememberToastState
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
import desktopkit.shell.DeskWindows
import desktopkit.shell.DesktopShell
import desktopkit.shell.MoreStyle
import desktopkit.shell.NavDrop
import desktopkit.shell.NavItem
import desktopkit.shell.NavKind
import desktopkit.shell.NavSection
import desktopkit.shell.ShellNavigator
import desktopkit.shell.ShellSpec
import desktopkit.shell.WidthClass
import desktopkit.theme.Accent
import desktopkit.theme.DeskTheme
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ 1. keyboard shortcuts

private val FileGroup = ShortcutGroup("file", R.string.menu_file)

/**
 * The app's own shortcuts plus the common ones (Ctrl+K search, Ctrl+, settings, Ctrl+B sidebar,
 * Ctrl+/ list) and Ctrl+N for another window.
 */
val shortcuts = ShortcutRegistry(
    groups = listOf(FileGroup, CommonShortcuts.ViewGroup, CommonShortcuts.HelpGroup),
    shortcuts = listOf(
        Shortcut("open", FileGroup, R.string.action_open, KeyChord(Key.O, ctrl = true)),
        Shortcut("new_folder", FileGroup, R.string.action_new_folder, KeyChord(Key.N, ctrl = true, shift = true)),
        Shortcut("refresh", FileGroup, R.string.action_refresh, KeyChord(Key.F5)),
    ) + CommonShortcuts.all + CommonShortcuts.NewWindow,
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
            // the wallpaper's colour is offered after the presets and the brand colour
            val prefs = rememberDeskPreferences(accents = Accent.presets + BrandAccent, dynamicAccent = true)
            val navController = rememberNavController()
            val navigator = remember(navController) { ShellNavigator(navController) }
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            // Ctrl+K: the command palette over the screens, the albums and the shortcut table
            val palette = rememberCommandPaletteState()
            val toasts = rememberToastState()
            val library = remember { LibraryState() }
            var openAlbum by rememberSaveable { mutableStateOf<String?>(null) }
            // a window opened with Ctrl+N or from the palette starts at its screen
            val appSpec = spec(startRoute = DeskWindows.startRoute(intent))
            perform = { id ->
                when (id) {
                    CommonShortcuts.SEARCH -> palette.open()
                    CommonShortcuts.NEW_WINDOW -> DeskWindows.openNewWindow(this@MainActivity)
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
            val accent = if (followArtwork) Accent.custom("Artwork", tracks[track].artwork) else prefs.accent
            val previous = { track = (track + tracks.size - 1) % tracks.size }
            val next = { track = (track + 1) % tracks.size }
            val accentLabel: @Composable (Accent) -> String = {
                when {
                    it == BrandAccent -> "Brand"
                    it.name == Accent.DYNAMIC -> stringResource(desktopkit.R.string.dk_accent_system)
                    else -> it.name
                }
            }

            // drops on the sidebar: albums and tracks onto "Playlists", files onto "Bin"
            val navDrop = remember(library) {
                NavDrop(mapOf("playlists" to setOf(DragKinds.ALBUM, DragKinds.TRACK), "bin" to setOf(DragKinds.FILE))) { route, payload ->
                    when (route) {
                        "playlists" -> {
                            val songs = songsIn(payload)
                            library.addToPlaylist(0, songs)
                            scope.launch {
                                val result = toasts.show("${payload.label} added to “${library.playlists[0].name}”", actionLabel = "Undo",
                                    shortcut = "Ctrl+Z")
                                if (result == ToastResult.ActionPerformed) library.addToPlaylist(0, -songs)
                            }
                            true
                        }
                        "bin" -> {
                            val names = payload.keys.filter { it !in library.binned }
                            library.binned.addAll(names)
                            scope.launch {
                                val result = toasts.show("${payload.label} moved to the bin", actionLabel = "Undo", shortcut = "Ctrl+Z")
                                if (result == ToastResult.ActionPerformed) library.binned.removeAll(names)
                            }
                            true
                        }
                        else -> false
                    }
                }
            }

            // the shell already sets text one sp larger on tablet and DeX windows (largeWindowTextStep = 1f)
            DesktopKitRoot(prefs, accent = accent) { widthClass ->
                val windows = widthClass != WidthClass.Compact
                DesktopShell(
                    spec = appSpec,
                    widthClass = widthClass,
                    navController = navController,
                    sidebarCollapsed = prefs.sidebarCollapsed,
                    onSidebarCollapsedChange = prefs::updateSidebarCollapsed,
                    badges = buildMap {
                        put("files", (sampleFiles.size - library.binned.size).toString())
                        if (library.binned.isNotEmpty()) put("bin", library.binned.size.toString())
                    },
                    menus = shortcuts.menus(context.resources, perform = { perform(it) }),
                    onSearch = { perform(CommonShortcuts.SEARCH) },
                    toasts = toasts,
                    navDrop = navDrop,
                    overlays = {
                        CommandPalette(
                            state = palette,
                            items = {
                                appSpec.items.map { item ->
                                    PaletteItem(
                                        item.route, item.label, "Screens", { navigator.navigate(item.route) }, icon = item.icon,
                                        // Ctrl+Enter: the screen in a window of its own (tablet and DeX)
                                        onOpenInNewWindow = if (!windows) null else {
                                            { DeskWindows.openNewWindow(this@MainActivity, route = item.route) }
                                        },
                                    )
                                } + albums.map { album ->
                                    PaletteItem(
                                        album.id, album.title, "Albums",
                                        onSelect = {
                                            navigator.navigate("albums")
                                            openAlbum = album.id
                                        },
                                        subtitle = album.artist,
                                        icon = DeskIcons.Albums,
                                        keywords = listOf(album.artist, album.year.toString()),
                                    )
                                } + shortcuts.paletteItems(context.resources) { id -> perform(id) }
                            },
                        )
                    },
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
                        "catalog" -> CatalogScreen(prefs, toasts, onDialog = { about = true }, accentLabel = accentLabel)
                        "albums" -> AlbumsScreen(openAlbum, onOpen = { openAlbum = it }, onPlay = { track = it })
                        "tracks" -> TracksScreen(playing = track, onPlay = { track = it })
                        "playlists" -> PlaylistsScreen(library)
                        "files" -> FilesScreen(prefs, library)
                        "bin" -> BinScreen(library)
                        "settings" -> LazyColumn(Modifier.fillMaxSize()) {
                            item { PageHeader(stringResource(desktopkit.R.string.dk_settings)) }
                            item { AppearanceSection(prefs, accentLabel = accentLabel) }
                            item { InputSection(prefs) }
                            item { ShortcutListSection(shortcuts) }
                        }
                        else -> MessageScreen(
                            title = "Coming soon",
                            icon = appSpec.item(route)?.icon ?: DeskIcons.Info,
                            pageTitle = appSpec.item(route)?.label,
                            description = "Each app fills its screens in here.",
                        )
                    }
                }
                if (about) {
                    // "Esc" with a hardware keyboard (DeX), a × button on touch
                    DeskDialog(onDismiss = { about = false }, title = "Desktop Kit 2.0", actions = {
                        DeskButton("Close", { about = false }, primary = true)
                    }) {
                        Text(
                            "Glass bars, springs on the phone, the workstation on tablet and DeX, the menu bar in the " +
                                "window's title bar, more than one window.",
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
private fun spec(startRoute: String? = null) = remember {
    ShellSpec(
        appTitle = "Desktop Kit",
        sections = listOf(
            NavSection("Kit", listOf(NavItem("catalog", "Components", DeskIcons.Grid))),
            NavSection(
                "Library",
                listOf(
                    NavItem("albums", "Albums", DeskIcons.Albums),
                    NavItem("tracks", "Tracks", DeskIcons.Music),
                    NavItem("playlists", "Playlists", DeskIcons.Queue),
                ),
            ),
            NavSection(
                "Files",
                listOf(
                    NavItem("files", "Files", DeskIcons.Folder),
                    NavItem("bin", "Bin", DeskIcons.Delete),
                    NavItem("network", "Network", DeskIcons.Network),
                ),
            ),
        ),
        footer = NavItem("settings", "Settings", DeskIcons.Settings),
        // phone: three tabs and "⋯" (the glass sheet)
        bottomBar = listOf("catalog", "albums", "files"),
        startRoute = startRoute,
        moreStyle = MoreStyle.Sheet,
    )
}
