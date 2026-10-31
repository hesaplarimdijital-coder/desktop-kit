package desktopkit.motion

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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
import desktopkit.components.rememberExpandablePlayerState
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
import desktopkit.shell.detailSharedElement
import desktopkit.theme.DeskMotion
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
 * Frame strips of the phone's transitions, for choosing between motion proposals: each test plays a
 * transition with the clock advanced by hand and saves the frames side by side as
 * build/outputs/screenshots/motion/<name>.png (CI pushes them to the ci/screenshots-pr-<n> branch).
 * The "current" strips use the kit as it is; the "proposal" strips are sketches drawn in this file
 * with the proposed durations and springs, not kit code. Nothing is asserted beyond the strip existing.
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

    /** The window before [change] and [times] ms after it, as one strip. */
    private fun strip(name: String, change: () -> Unit) {
        compose.mainClock.autoAdvance = false
        val frames = mutableListOf("önce" to shot())
        compose.runOnIdle { change() }
        var now = 0L
        for (t in times) {
            compose.mainClock.advanceTimeBy(t - now)
            now = t
            compose.waitForIdle()
            frames += "$t ms" to shot()
        }
        compose.mainClock.autoAdvance = true
        saveStrip(name, frames)
    }

    // ---------------------------------------------------------------- screens (tab switch)

    @Test
    fun tabSwitchCurrent() {
        var navigator: ShellNavigator? = null
        phoneContent {
            TestShell(WidthClass.Compact, startRoute = "music", spec = mediaSpec, transitions = ShellTransitions.Default) { route, nav ->
                navigator = nav
                if (route == "podcasts") PodcastList() else SampleTrackList()
            }
        }
        strip("tab-switch-current") { navigator?.navigate("podcasts") }
    }

    @Test
    fun tabSwitchProposals() {
        tabProposal("tab-switch-a-aligned") {
            // A: the scale starts with the fade, as Material's fade-through does
            (fadeIn(tween(210, delayMillis = 90, easing = DeskMotion.EmphasizedDecelerate)) +
                scaleIn(tween(210, delayMillis = 90, easing = DeskMotion.EmphasizedDecelerate), initialScale = 0.96f))
                .togetherWith(fadeOut(tween(90, easing = DeskMotion.EmphasizedAccelerate)))
        }
        tabProposal("tab-switch-b-crossfade") {
            // B: a calm cross-fade, no movement
            fadeIn(tween(180, easing = DeskMotion.StandardDecelerate)) togetherWith fadeOut(tween(120, easing = DeskMotion.StandardAccelerate))
        }
        tabProposal("tab-switch-c-depth") {
            // C: the old screen sinks (shrinks and fades), the new one rises from 94 % on a spring
            (fadeIn(tween(200, delayMillis = 40, easing = DeskMotion.EmphasizedDecelerate)) +
                scaleIn(spring(dampingRatio = 0.9f, stiffness = 600f), initialScale = 0.94f))
                .togetherWith(fadeOut(tween(110, easing = DeskMotion.EmphasizedAccelerate)) + scaleOut(tween(110, easing = DeskMotion.EmphasizedAccelerate), targetScale = 0.97f))
        }
    }

    private fun tabProposal(name: String, transform: () -> ContentTransform) {
        var screen by mutableStateOf("music")
        phoneContent {
            AnimatedContent(targetState = screen, transitionSpec = { transform() }, label = name) { route ->
                Box(Modifier.fillMaxSize().background(DeskTheme.colors.background)) {
                    if (route == "podcasts") PodcastList() else SampleTrackList()
                }
            }
        }
        strip(name) { screen = "podcasts" }
    }

    // ---------------------------------------------------------------- list → detail

    @Test
    fun detailOpenCurrent() {
        var detail by mutableStateOf<Album?>(null)
        phoneContent {
            DetailTransition(detail = detail, onBack = { detail = null }, detailKey = { it.id }, list = { AlbumList(kit = true) }) { album ->
                AlbumDetail(album, kit = true)
            }
        }
        strip("detail-open-current") { detail = albums[2] }
    }

    @Test
    fun detailOpenProposals() {
        val offsetSpring = spring(dampingRatio = 0.9f, stiffness = 700f, visibilityThreshold = IntOffset(1, 1))
        detailProposal("detail-open-a-depth", card = false) {
            // A: depth. The detail slides in 12 % and fades; the list sinks to 95 % under it.
            (slideInHorizontally(offsetSpring) { it * 12 / 100 } + fadeIn(tween(200, delayMillis = 40, easing = DeskMotion.StandardDecelerate)))
                .togetherWith(scaleOut(tween(300, easing = DeskMotion.Emphasized), targetScale = 0.95f) + fadeOut(tween(160, easing = DeskMotion.StandardAccelerate)))
        }
        detailProposal("detail-open-b-card", card = true) {
            // B: the card grows into the detail's header; the rest of the detail fades in after it
            fadeIn(tween(220, delayMillis = 60, easing = DeskMotion.StandardDecelerate)) togetherWith fadeOut(tween(120, easing = DeskMotion.StandardAccelerate))
        }
        detailProposal("detail-open-c-axis", card = false) {
            // C: shared axis with a longer way: 30 % in from the end, the list 30 % out
            (slideInHorizontally(offsetSpring) { it * 3 / 10 } + fadeIn(tween(200, delayMillis = 40, easing = DeskMotion.StandardDecelerate)))
                .togetherWith(slideOutHorizontally(offsetSpring) { -it * 3 / 10 } + fadeOut(tween(120, easing = DeskMotion.StandardAccelerate)))
        }
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    private fun detailProposal(name: String, card: Boolean, transform: () -> ContentTransform) {
        var detail by mutableStateOf<Album?>(null)
        phoneContent {
            SharedTransitionLayout(Modifier.fillMaxSize()) {
                val shared = this
                AnimatedContent(targetState = detail, transitionSpec = { transform() }, contentKey = { it?.id }, label = name) { target ->
                    val visibility = this
                    if (target == null) AlbumList(kit = false, shared = shared, visibility = visibility, card = card)
                    else AlbumDetail(target, kit = false, shared = shared, visibility = visibility, card = card)
                }
            }
        }
        strip(name) { detail = albums[2] }
    }

    // ---------------------------------------------------------------- predictive back on a detail

    @Test
    fun detailBackCurrent() {
        var detail by mutableStateOf<Album?>(albums[2])
        phoneContent {
            DetailTransition(detail = detail, onBack = { detail = null }, detailKey = { it.id }, list = { AlbumList(kit = true) }) { album ->
                AlbumDetail(album, kit = true)
            }
        }
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
            compose.mainClock.advanceTimeBy(t - now)
            now = t
            compose.waitForIdle()
            frames += "bırakınca +$t ms" to shot()
        }
        compose.mainClock.autoAdvance = true
        saveStrip("detail-back-current", frames)
    }

    @Test
    fun detailBackProposal() {
        val state = SeekableTransitionState<Album?>(albums[2])
        var scope: CoroutineScope? = null
        phoneContent {
            scope = rememberCoroutineScope()
            SeekableDetail(state)
        }
        compose.mainClock.autoAdvance = false
        val frames = mutableListOf("önce" to shot())
        for (p in listOf(0.25f, 0.5f, 0.8f)) {
            compose.runOnIdle { scope?.launch { state.seekTo(p, null) } }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            frames += "parmak $p" to shot()
        }
        compose.runOnIdle { scope?.launch { state.animateTo(null) } }
        var now = 0L
        for (t in listOf(16L, 64L, 144L, 304L)) {
            compose.mainClock.advanceTimeBy(t - now)
            now = t
            compose.waitForIdle()
            frames += "bırakınca +$t ms" to shot()
        }
        compose.mainClock.autoAdvance = true
        saveStrip("detail-back-proposal", frames)
    }

    /**
     * Proposal: the Back gesture seeks the closing transition, so the list is really behind the
     * shrinking detail; letting go plays the rest from where the finger left it.
     */
    @Composable
    private fun SeekableDetail(state: SeekableTransitionState<Album?>) {
        val transition = rememberTransition(state, label = "seekableDetail")
        val colors = DeskTheme.colors
        transition.AnimatedContent(
            transitionSpec = {
                val closing = targetState == null
                val spec = (fadeIn(tween(300, easing = DeskMotion.StandardDecelerate)) + scaleIn(tween(300, easing = DeskMotion.Emphasized), initialScale = 0.94f))
                    .togetherWith(
                        slideOutHorizontally(tween(300, easing = DeskMotion.EmphasizedAccelerate)) { it / 4 } +
                            fadeOut(tween(300, easing = CubicBezierEasing(0.8f, 0f, 1f, 0.2f))),
                    )
                // the shrinking detail stays above the list that shows behind it
                spec.apply { targetContentZIndex = if (closing) -1f else 1f }
            },
            contentKey = { it?.id },
        ) { target ->
            if (target == null) {
                AlbumList(kit = false)
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = state.fraction.coerceIn(0f, 1f)
                            val scale = 1f - 0.1f * p
                            scaleX = scale
                            scaleY = scale
                            translationX = 24.dp.toPx() * p
                            shape = RoundedCornerShape((28 * p).dp)
                            clip = true
                        }
                        .background(colors.background),
                ) { AlbumDetail(target, kit = false) }
            }
        }
    }

    // ---------------------------------------------------------------- player

    @Test
    fun playerExpandCurrent() {
        var player: ExpandablePlayerState? = null
        var scope: CoroutineScope? = null
        phoneContent {
            player = rememberExpandablePlayerState()
            scope = rememberCoroutineScope()
            TestShell(
                WidthClass.Compact,
                startRoute = "music",
                spec = mediaSpec,
                bottomBar = { ExpandablePlayer(player!!, mini = { SampleMiniPlayer(2) }, full = { SampleFullPlayer(2) }) },
            ) { _, _ -> SampleTrackList() }
        }
        strip("player-expand-current") { scope?.launch { player?.expand() } }
    }

    @Test
    fun playerExpandProposal() {
        val progress = Animatable(0f)
        var scope: CoroutineScope? = null
        phoneContent {
            scope = rememberCoroutineScope()
            ProtoPlayer(progress)
        }
        strip("player-expand-proposal") { scope?.launch { progress.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 500f, visibilityThreshold = 0.001f)) } }
    }

    /**
     * Proposal: one surface grows from the mini player's strip to the full screen; the cover and
     * the title fly to their places, the mini controls fade out early, the full controls come late.
     */
    @Composable
    private fun ProtoPlayer(progress: Animatable<Float, AnimationVector1D>) {
        val colors = DeskTheme.colors
        val density = LocalDensity.current
        val track = sampleTracks[2]
        val artwork = sampleArtwork[2]
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val width = maxWidth
            val height = maxHeight
            val navHeight = 64.dp
            val miniHeight = 64.dp
            SampleTrackList()
            // the bottom bar look-alike, pushed down as the player opens
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(navHeight)
                    .graphicsLayer { translationY = progress.value * size.height }
                    .background(colors.chrome),
            )
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress.value }.background(colors.scrim))
            val collapsedTop = with(density) { (height - navHeight - miniHeight).toPx() }
            val stripColor = colors.chrome.copy(alpha = 0.96f)
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = progress.value
                        translationY = collapsedTop * (1f - p)
                        // rounded while it travels, square at both ends
                        shape = RoundedCornerShape((20f * 4f * p * (1f - p)).dp)
                        clip = true
                    }
                    .background(stripColor),
            ) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress.value }.background(colors.background))
                // the cover: 44 dp beside the strip's edge → 232 dp centred near the top
                val coverStart = 12.dp
                val coverEnd = (width - 232.dp) / 2
                Box(
                    Modifier
                        .graphicsLayer {
                            val p = progress.value
                            val scale = (44f + (232f - 44f) * p) / 232f
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = scale
                            scaleY = scale
                            translationX = lerpDp(coverStart, coverEnd, p).toPx()
                            translationY = lerpDp(10.dp, 72.dp, p).toPx()
                            shape = RoundedCornerShape((8f + 8f * p).dp)
                            clip = true
                        }
                        .size(232.dp)
                        .background(artwork),
                )
                // the title and artist: beside the cover → under it, growing
                Column(
                    Modifier.graphicsLayer {
                        val p = progress.value
                        val scale = 1f + 0.35f * p
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = scale
                        scaleY = scale
                        translationX = lerpDp(68.dp, 24.dp, p).toPx()
                        translationY = lerpDp(14.dp, 72.dp + 232.dp + 20.dp, p).toPx()
                    },
                ) {
                    Text(track.first, style = DeskTheme.type.bodyStrong, color = colors.text, maxLines = 1)
                    Text(track.second, style = DeskTheme.type.labelSmall, color = colors.textSecondary, maxLines = 1)
                }
                // mini controls go first, the full controls come last
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 12.dp, top = 8.dp)
                        .graphicsLayer { alpha = (1f - progress.value * 4f).coerceIn(0f, 1f) },
                ) {
                    DeskIconButton(DeskIcons.Pause, "Duraklat", {})
                    DeskIconButton(DeskIcons.Next, "Sonraki", {})
                }
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 72.dp + 232.dp + 20.dp + 64.dp)
                        .graphicsLayer { alpha = ((progress.value - 0.6f) / 0.4f).coerceIn(0f, 1f) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DeskIconButton(DeskIcons.Previous, "Önceki", {})
                    Spacer(Modifier.width(16.dp))
                    DeskIconButton(DeskIcons.Pause, "Duraklat", {}, size = 64.dp, iconSize = 28.dp)
                    Spacer(Modifier.width(16.dp))
                    DeskIconButton(DeskIcons.Next, "Sonraki", {})
                }
            }
        }
    }

    // ---------------------------------------------------------------- the "⋯" sheet

    @Test
    fun sheetOpenCurrent() {
        var open by mutableStateOf(false)
        phoneContent {
            TestShell(
                WidthClass.Compact,
                startRoute = "music",
                spec = mediaSpec,
                overlays = {
                    GlassBottomSheet(visible = open, onDismiss = { open = false }, title = "Daha fazla") {
                        ActionGroupList(sampleMoreActions, onDismiss = { open = false })
                    }
                },
            ) { _, _ -> SampleTrackList() }
        }
        strip("sheet-open-current") { open = true }
    }

    @Test
    fun sheetOpenProposals() {
        sheetProposal("sheet-open-a-scale", scale = true, dampingRatio = 0.9f, stiffness = 600f)
        sheetProposal("sheet-open-b-quick", scale = false, dampingRatio = 0.92f, stiffness = 800f)
    }

    private fun sheetProposal(name: String, scale: Boolean, dampingRatio: Float, stiffness: Float) {
        val progress = Animatable(0f)
        var scope: CoroutineScope? = null
        phoneContent {
            scope = rememberCoroutineScope()
            ProtoSheet(progress, scale)
        }
        strip(name) { scope?.launch { progress.animateTo(1f, spring(dampingRatio = dampingRatio, stiffness = stiffness, visibilityThreshold = 0.001f)) } }
    }

    /** Proposal: the sheet rises on a quicker spring, with the scrim; [scale] grows it from 96 % at its bottom edge too. */
    @Composable
    private fun ProtoSheet(progress: Animatable<Float, AnimationVector1D>, scale: Boolean) {
        val colors = DeskTheme.colors
        Box(Modifier.fillMaxSize()) {
            SampleTrackList()
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress.value }.background(colors.scrim))
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val p = progress.value
                        translationY = (1f - p) * size.height
                        if (scale) {
                            val s = 0.96f + 0.04f * p
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            scaleX = s
                            scaleY = s
                        }
                    }
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(colors.surfaceRaised.copy(alpha = 0.96f))
                    .padding(bottom = 12.dp),
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 36.dp, height = 4.dp).background(colors.borderStrong, RoundedCornerShape(2.dp)))
                }
                Text("Daha fazla", style = DeskTheme.type.title, color = colors.text, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                ActionGroupList(sampleMoreActions, onDismiss = {})
            }
        }
    }

    // ---------------------------------------------------------------- list items appearing

    @Test
    fun listAppearProposal() {
        var shown by mutableStateOf(false)
        phoneContent {
            if (shown) ProtoAppearList()
        }
        strip("list-appear-proposal") { shown = true }
    }

    /** Proposal: rows come in with a short staggered rise and fade, in the draw phase only. */
    @Composable
    private fun ProtoAppearList() {
        LazyColumn(Modifier.fillMaxSize()) {
            items(12) { i ->
                val appear = remember { Animatable(0f) }
                LaunchedEffect(Unit) { appear.animateTo(1f, tween(180, delayMillis = i * 20, easing = DeskMotion.EmphasizedDecelerate)) }
                Box(
                    Modifier.graphicsLayer {
                        alpha = appear.value
                        translationY = (1f - appear.value) * 12.dp.toPx()
                    },
                ) {
                    val track = sampleTracks[i % sampleTracks.size]
                    DeskListRow(
                        title = track.first,
                        subtitle = track.second,
                        leadingContent = { Box(Modifier.size(40.dp).background(sampleArtwork[i % sampleArtwork.size], RoundedCornerShape(6.dp))) },
                        trailing = "3:${10 + i}",
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------------- sample content

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

    @OptIn(ExperimentalSharedTransitionApi::class)
    private val bounds = BoundsTransform { _, _ -> spring(dampingRatio = 0.9f, stiffness = 700f) }

    /** The albums grid: with [kit] the kit's shared elements, otherwise [shared]'s own (and the whole card with [card]). */
    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    private fun AlbumList(
        kit: Boolean,
        shared: SharedTransitionScope? = null,
        visibility: AnimatedVisibilityScope? = null,
        card: Boolean = false,
    ) {
        val colors = DeskTheme.colors
        Column(Modifier.fillMaxSize().background(colors.background)) {
            PageHeader("Albümler", subtitle = "${albums.size} albüm")
            DeskGrid(items = albums, key = { it.id }, onItemClick = { _, _ -> }) { album ->
                val coverModifier = when {
                    kit -> Modifier.detailSharedElement("cover-${album.id}")
                    shared != null && visibility != null && !card -> with(shared) {
                        Modifier.sharedElement(sharedContentState = rememberSharedContentState("cover-${album.id}"), animatedVisibilityScope = visibility, boundsTransform = bounds)
                    }
                    else -> Modifier
                }
                val titleModifier = when {
                    kit -> Modifier.detailSharedBounds("title-${album.id}")
                    shared != null && visibility != null && !card -> with(shared) {
                        Modifier.sharedBounds(
                            sharedContentState = rememberSharedContentState("title-${album.id}"), animatedVisibilityScope = visibility, boundsTransform = bounds,
                            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.Fit, Alignment.CenterStart),
                        )
                    }
                    else -> Modifier
                }
                val cardModifier = if (card && shared != null && visibility != null) with(shared) {
                    Modifier
                        .sharedBounds(
                            sharedContentState = rememberSharedContentState("card-${album.id}"), animatedVisibilityScope = visibility, boundsTransform = bounds,
                            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopStart),
                            clipInOverlayDuringTransition = OverlayClip(cardShape),
                        )
                        .clip(cardShape)
                        .background(colors.surface)
                        .padding(6.dp)
                } else Modifier
                CoverCell(album.title, modifier = cardModifier, subtitle = album.artist, titleModifier = titleModifier) {
                    Box(coverModifier.fillMaxSize().background(album.color))
                }
            }
        }
    }

    /** The album's page: a top bar look-alike, the cover and the title that flew in, the tracks. */
    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    private fun AlbumDetail(
        album: Album,
        kit: Boolean,
        shared: SharedTransitionScope? = null,
        visibility: AnimatedVisibilityScope? = null,
        card: Boolean = false,
    ) {
        val colors = DeskTheme.colors
        val coverModifier = when {
            kit -> Modifier.detailSharedElement("cover-${album.id}", shape = cardShape)
            shared != null && visibility != null && !card -> with(shared) {
                Modifier.sharedElement(sharedContentState = rememberSharedContentState("cover-${album.id}"), animatedVisibilityScope = visibility, boundsTransform = bounds).clip(cardShape)
            }
            else -> Modifier.clip(cardShape)
        }
        val titleModifier = when {
            kit -> Modifier.detailSharedBounds("title-${album.id}")
            shared != null && visibility != null && !card -> with(shared) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState("title-${album.id}"), animatedVisibilityScope = visibility, boundsTransform = bounds,
                    resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.Fit, Alignment.CenterStart),
                )
            }
            else -> Modifier
        }
        val headerModifier = if (card && shared != null && visibility != null) with(shared) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState("card-${album.id}"), animatedVisibilityScope = visibility, boundsTransform = bounds,
                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopStart),
                clipInOverlayDuringTransition = OverlayClip(cardShape),
            )
        } else Modifier
        Column(Modifier.fillMaxSize().background(colors.background)) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                DeskIconButton(DeskIcons.ArrowLeft, "Geri", {})
                Spacer(Modifier.width(4.dp))
                Text(album.title, style = DeskTheme.type.headline, color = colors.text, maxLines = 1)
            }
            Column(headerModifier.fillMaxWidth().background(colors.background).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(coverModifier.size(232.dp).background(album.color))
                Spacer(Modifier.height(16.dp))
                Text(album.title, style = DeskTheme.type.headline, color = colors.text, modifier = titleModifier)
                Text("${album.artist} · 2026 · 9 şarkı", style = DeskTheme.type.body, color = colors.textSecondary)
            }
            repeat(5) { i ->
                DeskListRow(title = "${i + 1}. ${sampleTracks[(i + 1) % sampleTracks.size].first}", subtitle = album.artist, trailing = "3:${20 + i}")
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

    private fun lerpDp(start: Dp, stop: Dp, fraction: Float): Dp = androidx.compose.ui.unit.lerp(start, stop, fraction)
}
