package desktopkit.sample

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// ------------------------------------------------------------------ music

class Track(val title: String, val artist: String, val length: String, val artwork: Color)

val tracks = listOf(
    Track("Night Drive", "Ada Ekin", "3:41", Color(0xFFE0574B)),
    Track("Paper Boats", "The Quiet Rooms", "4:05", Color(0xFFF2B84B)),
    Track("Green Line", "Lumen", "2:58", Color(0xFF59D49A)),
    Track("Blue Hour", "Sea of Glass", "5:12", Color(0xFF8AB0FF)),
    Track("Violet Static", "Kaan & The Tapes", "3:27", Color(0xFFB07AFF)),
)

class Album(val id: String, val title: String, val artist: String, val year: Int, val artwork: Color) {
    /** Eight tracks, named after the sample tracks. */
    val songs: List<Track> = List(8) { i -> tracks[(i + year) % tracks.size].let { Track(it.title, artist, it.length, artwork) } }
}

private val artists = listOf("Ada Ekin", "The Quiet Rooms", "Lumen", "Sea of Glass", "Kaan & The Tapes", "Mavi Hat")
private val albumTitles = listOf(
    "Winter Garden", "Paper Boats", "Green Line", "Blue Hour", "Violet Static", "Night Drive",
    "Coastline", "Glass Houses", "Low Tide", "Northern Lights", "Signal Path", "Late Trains",
    "Small Hours", "Field Notes", "Open Water", "Long Way Home", "Neon Rain", "Quiet Room",
)

val albums: List<Album> = albumTitles.mapIndexed { i, title ->
    Album("album-$i", title, artists[i % artists.size], 2014 + (i * 3) % 12, tracks[i % tracks.size].artwork.copy(alpha = 1f - (i / 6) * 0.18f))
}

// ------------------------------------------------------------------ files

data class SampleFile(val name: String, val kind: String, val size: String)

val sampleFiles = listOf(
    SampleFile("Budget 2026.ods", "Spreadsheet", "84 KB"),
    SampleFile("Holiday photos", "Folder", "1.2 GB"),
    SampleFile("Talk draft.odp", "Presentation", "3.4 MB"),
    SampleFile("Notes.md", "Text", "12 KB"),
    SampleFile("Recording 14.flac", "Audio", "38 MB"),
    SampleFile("Backup.zip", "Archive", "640 MB"),
)

// ------------------------------------------------------------------ what drags and drops change

/** The kinds of the sample's drags (DeskDragPayload.kind). */
object DragKinds {
    const val TRACK = "track"
    const val ALBUM = "album"
    const val FILE = "file"
    const val TAB = "tab"
}

class Playlist(val name: String, count: Int) {
    var count by mutableIntStateOf(count)
}

/** Playlists that take dropped albums and tracks, and a bin that takes dropped files. */
@Stable
class LibraryState {
    val playlists = mutableStateListOf(Playlist("Morning", 12), Playlist("Focus", 30), Playlist("Road trip", 21))
    val binned = mutableStateListOf<String>()

    fun addToPlaylist(index: Int, songs: Int) {
        playlists.getOrNull(index)?.let { it.count += songs }
    }
}
