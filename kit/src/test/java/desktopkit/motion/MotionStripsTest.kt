package desktopkit.motion

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.SampleFullPlayer
import desktopkit.SampleMiniPlayer
import desktopkit.SampleTrackList
import desktopkit.TestShell
import desktopkit.components.ActionGroupList
import desktopkit.components.CoverCell
import desktopkit.components.DeskGrid
import desktopkit.components.DeskIconButton
import desktopkit.components.DeskIcons
import desktopkit.components.DeskListRow
import desktopkit.components.ExpandablePlayer
import desktopkit.components.ExpandablePlayerState
import desktopkit.components.GlassBottomSheet
import desktopkit.components.listAppear
import desktopkit.components.rememberExpandablePlayerState
import desktopkit.components.rememberListAppear
import desktopkit.mediaSpec
import desktopkit.sampleArtwork
import desktopkit.sampleMoreActions
import desktopkit.sampleTracks
import desktopkit.screens.PageHeader
import desktopkit.shell.DetailTransition
import desktopkit.shell.ShellNavigator
import desktopkit.shell.ShellTransitions
import desktopkit.shell.WidthClass
import desktopkit.shell.detailSharedBounds
import desktopkit.shell.detailSharedCard
import desktopkit.shell.detailSharedElement
import desktopkit.theme.DeskTheme
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.LocalMotionTier
import desktopkit.theme.MotionTier
import desktopkit.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * Frame strips of the phone's transitions as the kit plays them: each test plays a transition with
 * the clock advanced frame by frame and saves the frames side by side as
 * build/outputs/screenshots/motion/<name>.png (CI pushes them to the ci/screenshots-pr-<n> branch).
 * Nothing is asserted beyond the strip existing; they are there to be looked at.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class MotionStripsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val times = listOf(16L, 48L, 96L, 144L, 208L, 304L, 448L)

    private fun phoneContent(content: @Composable () -> Unit) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                CompositionLocalProvider(LocalMotionTier provides MotionTier.Gesture) {
                    Box(Modifier.fillMaxSize().background(DeskTheme.colors.background)) { content() }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun shot(): Bitmap {
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        return bitmap
    }

    /** Frame by frame (a run of frames without an idle in between leaves the recomposer asleep) up to [t] ms. */
    private fun advanceTo(t: Long, now: Long): Long {
        var at = now
        while (at < t) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            at += 16
        }
        return at
    }

    /**
     * The window before [change] and [at] ms after it, as one strip. [change] runs on the UI thread
     * unless it drives the test's own input ([onUiThread] false), which synchronises itself.
     */
    private fun strip(name: String, at: List<Long> = times, onUiThread: Boolean = true, change: () -> Unit) {
        compose.mainClock.autoAdvance = false
        val frames = mutableListOf("önce" to shot())
        if (onUiThread) compose.runOnIdle { change() } else change()
        var now = 0L
        for (t in at) {
            now = advanceTo(t, now)
            frames += "$now ms" to shot()
        }
        compose.mainClock.autoAdvance = true
        saveStrip(name, frames)
    }

    // ---------------------------------------------------------------- screens (tab switch)

    @Test
    fun tabSwitch() {
        var navigator: ShellNavigator? = null
        phoneContent {
            TestShell(WidthClass.Compact, startRoute = "music", spec = mediaSpec, transitions = ShellTransitions.Default) { route, nav ->
                navigator = nav
                if (route == "podcasts") PodcastList() else SampleTrackList()
            }
        }
        strip("tab-switch") { navigator?.navigate("podcasts") }
    }

    // ---------------------------------------------------------------- list → detail

    @Test
    fun detailOpen() {
        var detail by mutableStateOf<Album?>(null)
        phoneContent { Albums(detail) { detail = it } }
        strip("detail-open") { detail = albums[2] }
    }

    /** Back a few frames into the opening: the card turns around where it is. */
    @Test
    fun detailTurnAround() {
        var detail by mutableStateOf<Album?>(null)
        phoneContent { Albums(detail) { detail = it } }
        compose.mainClock.autoAdvance = false
        val frames = mutableListOf("önce" to shot())
        compose.runOnIdle { detail = albums[2] }
        var now = advanceTo(96, 0)
        frames += "açılırken $now ms" to shot()
        compose.runOnIdle { detail = null }
        for (t in listOf(16L, 48L, 96L, 160L, 240L, 320L)) {
            now = advanceTo(96 + t, now)
            frames += "geri +$t ms" to shot()
        }
        compose.mainClock.autoAdvance = true
        saveStrip("detail-turn-around", frames)
    }

    // ---------------------------------------------------------------- predictive back on a detail

    @Test
    fun detailBack() {
        var detail by mutableStateOf<Album?>(albums[2])
        phoneContent { Albums(detail) { detail = it } }
        val dispatcher = compose.activity.onBackPressedDispatcher
        fun back(progress: Float) = BackEventCompat(40f, 600f, progress, BackEventCompat.EDGE_LEFT)
        compose.mainClock.autoAdvance = false
        val frames = mutableListOf("önce" to shot())
        compose.runOnUiThread { dispatcher.dispatchOnBackStarted(back(0f)) }
        for (p in listOf(0.25f, 0.5f, 0.8f)) {
            compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(back(p)) }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            frames += "parmak $p" to shot()
        }
        compose.runOnUiThread { dispatcher.onBackPressed() }
        var now = 0L
        for (t in listOf(16L, 64L, 144L, 304L)) {
            now = advanceTo(t, now)
            frames += "bırakınca +$now ms" to shot()
        }
        compose.mainClock.autoAdvance = true
        saveStrip("detail-back", frames)
    }

    /** A Back gesture let go of half way: the detail springs back into place. */
    @Test
    fun detailBackCancelled() {
        var detail by mutableStateOf<Album?>(albums[2])
        phoneContent { Albums(detail) { detail = it } }
        val dispatcher = compose.activity.onBackPressedDispatcher
        fun back(progress: Float) = BackEventCompat(40f, 600f, progress, BackEventCompat.EDGE_LEFT)
        compose.mainClock.autoAdvance = false
        val frames = mutableListOf("önce" to shot())
        compose.runOnUiThread { dispatcher.dispatchOnBackStarted(back(0f)) }
        compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(back(0.5f)) }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        frames += "parmak 0.5" to shot()
        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        var now = 0L
        for (t in listOf(16L, 64L, 144L, 304L)) {
            now = advanceTo(t, now)
            frames += "vazgeçince +$now ms" to shot()
        }
        compose.mainClock.autoAdvance = true
        saveStrip("detail-back-cancelled", frames)
    }

    // ---------------------------------------------------------------- player

    private var playerScope: CoroutineScope? = null

    private fun playerShell(): ExpandablePlayerState {
        var player: ExpandablePlayerState? = null
        phoneContent {
            player = rememberExpandablePlayerState()
            playerScope = rememberCoroutineScope()
            TestShell(
                WidthClass.Compact,
                startRoute = "music",
                spec = mediaSpec,
                bottomBar = {
                    ExpandablePlayer(player!!, mini = { SampleMiniPlayer(2) }, full = { SampleFullPlayer(2) }, modifier = Modifier.testTag("mini"))
                },
            ) { _, _ -> SampleTrackList() }
        }
        return player!!
    }

    @Test
    fun playerExpand() {
        val player = playerShell()
        strip("player-expand") { playerScope?.launch { player.expand() } }
    }

    @Test
    fun playerCollapse() {
        val player = playerShell()
        compose.onNodeWithTag("mini").performClick()
        compose.waitForIdle()
        strip("player-collapse") { playerScope?.launch { player.collapse() } }
    }

    /** The predictive Back gesture on the open player: the shrink rides down with the sheet once let go. */
    @Test
    fun playerBack() {
        playerShell()
        compose.onNodeWithTag("mini").performClick()
        compose.waitForIdle()
        val dispatcher = compose.activity.onBackPressedDispatcher
        fun back(progress: Float) = BackEventCompat(40f, 600f, progress, BackEventCompat.EDGE_LEFT)
        compose.mainClock.autoAdvance = false
        val frames = mutableListOf("önce" to shot())
        compose.runOnUiThread { dispatcher.dispatchOnBackStarted(back(0f)) }
        for (p in listOf(0.4f, 0.8f)) {
            compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(back(p)) }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            frames += "parmak $p" to shot()
        }
        compose.runOnUiThread { dispatcher.onBackPressed() }
        var now = 0L
        for (t in listOf(16L, 64L, 144L, 240L, 400L)) {
            now = advanceTo(t, now)
            frames += "bırakınca +$now ms" to shot()
        }
        compose.mainClock.autoAdvance = true
        saveStrip("player-back", frames)
    }

    /** The bars were hidden by a scroll, so the mini player sits at the bottom edge: the sheet opens from there. */
    @Test
    fun playerTapWithBarsHidden() {
        playerWithBarsHidden()
        strip("player-tap-bars-hidden", onUiThread = false) { compose.onNodeWithTag("mini").performClick() }
    }

    /** The way back with the bars hidden: the sheet lands on the strip at the bottom edge. */
    @Test
    fun playerCollapseWithBarsHidden() {
        val player = playerWithBarsHidden()
        compose.onNodeWithTag("mini").performClick()
        compose.waitForIdle()
        strip("player-collapse-bars-hidden", at = listOf(16L, 96L, 208L, 304L, 448L, 608L)) {
            playerScope?.launch { player.collapse() }
        }
    }

    /** The phone shell with the player, after a long swipe up that hides the bars. */
    private fun playerWithBarsHidden(): ExpandablePlayerState {
        val player = playerShell()
        compose.onNodeWithTag("tracks").performTouchInput { swipeUp(startY = centerY + 300f, endY = top + 200f, durationMillis = 800) }
        compose.waitForIdle()
        return player
    }

    // ---------------------------------------------------------------- the "⋯" sheet

    private fun sheetShell(open: () -> Boolean, close: () -> Unit) {
        phoneContent {
            TestShell(
                WidthClass.Compact,
                startRoute = "music",
                spec = mediaSpec,
                overlays = {
                    GlassBottomSheet(visible = open(), onDismiss = close, title = "Daha fazla") {
                        ActionGroupList(sampleMoreActions, onDismiss = close)
                    }
                },
            ) { _, _ -> SampleTrackList() }
        }
    }

    @Test
    fun sheetOpen() {
        var open by mutableStateOf(false)
        sheetShell({ open }) { open = false }
        strip("sheet-open") { open = true }
    }

    @Test
    fun sheetClose() {
        var open by mutableStateOf(true)
        sheetShell({ open }) { open = false }
        strip("sheet-close") { open = false }
    }

    // ---------------------------------------------------------------- a list's rows arriving

    @Test
    fun listAppear() {
        var shown by mutableStateOf(false)
        phoneContent {
            if (shown) {
                val appear = rememberListAppear()
                Column(Modifier.fillMaxSize()) {
                    PageHeader("Parçalar", subtitle = "${sampleTracks.size} parça")
                    sampleTracks.forEachIndexed { i, (title, artist) ->
                        DeskListRow(
                            title = title,
                            modifier = Modifier.listAppear(appear, i),
                            subtitle = artist,
                            leadingContent = { Box(Modifier.size(40.dp).background(sampleArtwork[i % sampleArtwork.size], RoundedCornerShape(6.dp))) },
                            trailing = "3:${20 + i}",
                        )
                    }
                }
            }
        }
        strip("list-appear", at = listOf(16L, 64L, 112L, 160L, 208L, 256L, 320L)) { shown = true }
    }

    // ---------------------------------------------------------------- the pieces

    private data class Album(val id: Int, val title: String, val artist: String, val color: Color)

    private val albums = sampleTracks.mapIndexed { i, (title, artist) -> Album(i, title, artist, sampleArtwork[i % sampleArtwork.size]) }

    @Composable
    private fun PodcastList() {
        Column(Modifier.fillMaxSize()) {
            PageHeader("Podcast'ler", subtitle = "6 abonelik")
            repeat(8) { i ->
                DeskListRow(
                    title = "Bölüm ${24 - i}: ${sampleTracks[i % sampleTracks.size].first}",
                    subtitle = "${38 + i * 3} dk",
                    leading = DeskIcons.Podcasts,
                    trailing = if (i == 0) "Yeni" else null,
                )
            }
        }
    }

    private val cardShape = RoundedCornerShape(12.dp)

    /** The albums grid and the album's page in a [DetailTransition], as the sample has them: the card grows into the header. */
    @Composable
    private fun Albums(detail: Album?, onDetail: (Album?) -> Unit) {
        DetailTransition(detail = detail, onBack = { onDetail(null) }, detailKey = { it.id }, list = { AlbumList() }) { album ->
            AlbumDetail(album)
        }
    }

    @Composable
    private fun AlbumList() {
        val colors = DeskTheme.colors
        Column(Modifier.fillMaxSize().background(colors.background)) {
            PageHeader("Albümler", subtitle = "${albums.size} albüm")
            DeskGrid(items = albums, key = { it.id }, onItemClick = { _, _ -> }) { album ->
                CoverCell(
                    album.title,
                    modifier = Modifier.detailSharedCard("card-${album.id}"),
                    subtitle = album.artist,
                    titleModifier = Modifier.detailSharedBounds("title-${album.id}"),
                ) {
                    Box(Modifier.detailSharedElement("cover-${album.id}").fillMaxSize().background(album.color))
                }
            }
        }
    }

    /** The album's page: a top bar look-alike, the header the card grows into, the tracks arriving in turn. */
    @Composable
    private fun AlbumDetail(album: Album) {
        val colors = DeskTheme.colors
        val appear = rememberListAppear(album.id)
        Column(Modifier.fillMaxSize().background(colors.background)) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                DeskIconButton(DeskIcons.ArrowLeft, "Geri", {})
                Spacer(Modifier.width(4.dp))
                Text(album.title, style = DeskTheme.type.headline, color = colors.text, maxLines = 1)
            }
            Column(
                Modifier.detailSharedCard("card-${album.id}").fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.detailSharedElement("cover-${album.id}", shape = cardShape).size(232.dp).background(album.color))
                Spacer(Modifier.height(16.dp))
                Text(album.title, style = DeskTheme.type.headline, color = colors.text, modifier = Modifier.detailSharedBounds("title-${album.id}"))
                Text("${album.artist} · 2026 · 9 şarkı", style = DeskTheme.type.body, color = colors.textSecondary)
            }
            repeat(5) { i ->
                DeskListRow(
                    title = "${i + 1}. ${sampleTracks[(i + 1) % sampleTracks.size].first}",
                    modifier = Modifier.listAppear(appear, i),
                    subtitle = album.artist,
                    trailing = "3:${20 + i}",
                )
            }
        }
    }

    private fun saveStrip(name: String, frames: List<Pair<String, Bitmap>>) {
        val scale = 0.5f
        val width = (frames.first().second.width * scale).roundToInt()
        val height = (frames.first().second.height * scale).roundToInt()
        val label = 28
        val gap = 6
        val strip = Bitmap.createBitmap(frames.size * (width + gap) - gap, height + label, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(strip)
        canvas.drawColor(android.graphics.Color.rgb(32, 32, 36))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 18f
        }
        frames.forEachIndexed { index, (text, bitmap) ->
            val x = index * (width + gap)
            canvas.drawText(text, x + 6f, 20f, paint)
            canvas.drawBitmap(bitmap, null, Rect(x, label, x + width, label + height), paint)
        }
        val dir = File(File(System.getProperty("desktopkit.screenshotDir") ?: "build/outputs/screenshots"), "motion")
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { strip.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
