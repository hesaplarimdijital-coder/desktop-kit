package desktopkit.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import desktopkit.components.ActionGroup
import desktopkit.components.DeskIconButton
import desktopkit.components.DeskIcons
import desktopkit.components.DeskSlider
import desktopkit.components.MenuEntry
import desktopkit.components.MoreButton
import desktopkit.components.playerSharedElement
import desktopkit.theme.DeskTheme
import kotlin.math.roundToInt

/** The "⋯" actions of a track: a glass sheet on the phone, a menu on wider windows. */
fun trackActions(track: Track) = listOf(
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

/** "2:58 of 3:41", for TalkBack and the tooltip of the position slider. */
private fun positionText(track: Track, fraction: Float): String {
    val (minutes, seconds) = track.length.split(":").map { it.toInt() }
    val at = ((minutes * 60 + seconds) * fraction).roundToInt()
    return "%d:%02d of %s".format(at / 60, at % 60, track.length)
}

/**
 * The phone's mini player: swipe sideways to skip, drag up (or tap) to open the full player. The
 * artwork and the title are shared with [FullPlayer]: they fly between the two as the player opens.
 */
@Composable
fun MiniPlayer(index: Int) {
    val track = tracks[index]
    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.playerSharedElement("cover", RoundedCornerShape(8.dp)).size(44.dp).background(track.artwork, RoundedCornerShape(8.dp)))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track.title, Modifier.playerSharedElement("title"), style = DeskTheme.type.bodyStrong, color = DeskTheme.colors.text, maxLines = 1)
            Text(track.artist, style = DeskTheme.type.labelSmall, color = DeskTheme.colors.textSecondary, maxLines = 1)
        }
        DeskIconButton(DeskIcons.Pause, "Pause", {})
    }
}

@Composable
fun FullPlayer(index: Int, onCollapse: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit) {
    val track = tracks[index]
    var position by remember { mutableFloatStateOf(0.3f) }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth()) {
            DeskIconButton(DeskIcons.ChevronDown, stringResource(desktopkit.R.string.dk_player_collapse), onCollapse)
            Spacer(Modifier.weight(1f))
            MoreButton({ trackActions(track) }, title = track.title)
        }
        Spacer(Modifier.height(24.dp))
        Box(Modifier.playerSharedElement("cover", RoundedCornerShape(16.dp)).fillMaxWidth().aspectRatio(1f).background(track.artwork, RoundedCornerShape(16.dp)))
        Spacer(Modifier.height(24.dp))
        Text(track.title, Modifier.playerSharedElement("title"), style = DeskTheme.type.headline, color = DeskTheme.colors.text)
        Text(track.artist, style = DeskTheme.type.body, color = DeskTheme.colors.textSecondary)
        Spacer(Modifier.height(16.dp))
        DeskSlider(position, { position = it }, Modifier.fillMaxWidth(), label = "Position", valueText = { positionText(track, it) })
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            DeskIconButton(DeskIcons.Previous, "Previous", onPrevious)
            DeskIconButton(DeskIcons.Pause, "Pause", {}, size = 64.dp, iconSize = 28.dp)
            DeskIconButton(DeskIcons.Next, "Next", onNext)
        }
    }
}

/** Tablet and DeX: the transport bar inside the shell's floating glass bar (no background of its own). */
@Composable
fun TransportBar(index: Int, onPrevious: () -> Unit, onNext: () -> Unit) {
    val track = tracks[index]
    var position by remember { mutableFloatStateOf(0.3f) }
    // grows with large text instead of cutting the title
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(track.artwork, RoundedCornerShape(6.dp)))
        Column(Modifier.width(200.dp).padding(horizontal = 12.dp)) {
            Text(track.title, style = DeskTheme.type.bodyStrong, color = DeskTheme.colors.text, maxLines = 1)
            Text(track.artist, style = DeskTheme.type.labelSmall, color = DeskTheme.colors.textSecondary, maxLines = 1)
        }
        DeskIconButton(DeskIcons.Previous, "Previous", onPrevious)
        DeskIconButton(DeskIcons.Pause, "Pause", {})
        DeskIconButton(DeskIcons.Next, "Next", onNext)
        DeskSlider(position, { position = it }, Modifier.weight(1f).padding(start = 12.dp), label = "Position",
            valueText = { positionText(track, it) })
    }
}
