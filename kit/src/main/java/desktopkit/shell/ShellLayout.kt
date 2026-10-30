package desktopkit.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

private const val SLOT_TOP = "top"
private const val SLOT_NAV = "nav"
private const val SLOT_CONTENT = "content"
private const val SLOT_PLAYER = "player"
private const val SLOT_OVERLAY = "overlay"
private const val SLOT_BAND = "band"

// drawn in this order: the content, the chrome over it, the system band over the sliding bars, the
// overlay over everything. Within a layer, slots are drawn (and reached with Tab) in the order placed.
private const val CHROME = 1f
private const val BAND = 2f
private const val OVERLAY = 3f

/** A slot TalkBack reads as a whole, at [index] among the shell's slots. */
private fun Modifier.readingOrder(index: Float) = semantics {
    isTraversalGroup = true
    traversalIndex = index
}

/**
 * Places the shell's slots for a [NavKind]. The content slot is always the same child of the same
 * layout, so when the window is resized across width classes only the chrome around it changes:
 * the navigation back stack, scroll positions and text fields keep their state.
 *
 * - BottomBar: the content fills the whole window and the bars lie over it: the top bar along the
 *   top, the bottom navigation along the bottom and the player strip just above it. The shell
 *   tells the screens how much of them is covered ([rememberShellContentPadding]), so lists pad
 *   their ends and scroll beneath the glass. [navigationOffset] slides the navigation and the
 *   player that many pixels down; it is read while drawing (a layer translation), so hiding the
 *   bars while the content scrolls neither measures nor recomposes anything.
 * - Rail: rail on the start edge; top bar, content and player stacked beside it.
 * - Sidebar: menu bar across the top, player across the bottom, sidebar and content between.
 *
 * With [floatingPlayer] (Rail and Sidebar) the player is not stacked under the content but floats
 * over its bottom edge, [floatingMargin] from the edges and at most [floatingMaxWidth] wide.
 * [overlay] covers the whole window above everything else (the expanded player).
 *
 * [systemBottom] is the height (px, read while measuring) of the system navigation bar at the bottom
 * of the window: three-button navigation or the gesture handle. The layout keeps everything above it
 * and fills it with [systemBand] across the whole width: on a phone the bars' glass continues behind
 * the buttons and stays there while the bars slide away beneath it; on tablets the chrome colour.
 *
 * The keyboard's Tab key and TalkBack go through the slots in reading order: the top bar (menu bar),
 * the navigation, the content, the player; on a phone the bottom navigation comes last. The chrome
 * is still drawn over the content.
 */
@Composable
fun ShellLayout(
    navKind: NavKind,
    topBar: @Composable () -> Unit,
    navigation: @Composable () -> Unit,
    player: @Composable () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationOffset: () -> Int = { 0 },
    overlay: @Composable () -> Unit = {},
    floatingPlayer: Boolean = false,
    floatingMargin: androidx.compose.ui.unit.Dp = 12.dp,
    floatingMaxWidth: androidx.compose.ui.unit.Dp = 760.dp,
    systemBottom: () -> Int = { 0 },
    systemBand: @Composable () -> Unit = {},
) {
    val slides = navKind == NavKind.BottomBar
    // reading order for TalkBack; the Tab key follows the order the slots are placed in (below)
    val navOrder = if (slides) 3f else 0.5f
    Layout(
        modifier = modifier,
        content = {
            Box(Modifier.layoutId(SLOT_CONTENT).readingOrder(1f)) { content() }
            Box(Modifier.layoutId(SLOT_TOP).readingOrder(0f)) { topBar() }
            Box(
                Modifier
                    .layoutId(SLOT_NAV)
                    .readingOrder(navOrder)
                    .graphicsLayer { translationY = if (slides) navigationOffset().toFloat() else 0f },
            ) { navigation() }
            Box(
                Modifier
                    .layoutId(SLOT_PLAYER)
                    .readingOrder(2f)
                    .graphicsLayer { translationY = if (slides) navigationOffset().toFloat() else 0f },
            ) { player() }
            Box(Modifier.layoutId(SLOT_BAND)) { systemBand() }
            Box(Modifier.layoutId(SLOT_OVERLAY).readingOrder(4f)) { overlay() }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val fullHeight = constraints.maxHeight
        val topM = measurables.first { it.layoutId == SLOT_TOP }
        val navM = measurables.first { it.layoutId == SLOT_NAV }
        val playerM = measurables.first { it.layoutId == SLOT_PLAYER }
        val contentM = measurables.first { it.layoutId == SLOT_CONTENT }
        val overlay = measurables.first { it.layoutId == SLOT_OVERLAY }.measure(Constraints.fixed(width, fullHeight))
        val system = systemBottom().coerceIn(0, fullHeight)
        val band = measurables.first { it.layoutId == SLOT_BAND }.measure(Constraints.fixed(width, system))
        // everything but the content of the phone layout stays above the system navigation bar
        val height = fullHeight - system
        val margin = floatingMargin.roundToPx()

        when (navKind) {
            NavKind.BottomBar -> {
                val top = topM.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = height))
                val nav = navM.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = height))
                val player = playerM.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = height))
                // the content fills the window and never changes size while the bars move: they are drawn over it
                val content = contentM.measure(Constraints.fixed(width, fullHeight))
                layout(width, fullHeight) {
                    top.placeRelative(0, 0, CHROME)
                    content.placeRelative(0, 0)
                    player.placeRelative(0, height - nav.height - player.height, CHROME)
                    nav.placeRelative(0, height - nav.height, CHROME)
                    band.placeRelative(0, height, BAND)
                    overlay.placeRelative(0, 0, OVERLAY)
                }
            }

            NavKind.Rail -> {
                val nav = navM.measure(Constraints(maxWidth = width, minHeight = height, maxHeight = height))
                val rest = (width - nav.width).coerceAtLeast(0)
                val top = topM.measure(Constraints(minWidth = rest, maxWidth = rest, maxHeight = height))
                if (floatingPlayer) {
                    val playerWidth = (rest - 2 * margin).coerceAtMost(floatingMaxWidth.roundToPx()).coerceAtLeast(0)
                    val player = playerM.measure(Constraints(minWidth = playerWidth, maxWidth = playerWidth, maxHeight = height))
                    val contentHeight = (height - top.height).coerceAtLeast(0)
                    val content = contentM.measure(Constraints.fixed(rest, contentHeight))
                    layout(width, fullHeight) {
                        top.placeRelative(nav.width, 0, CHROME)
                        nav.placeRelative(0, 0, CHROME)
                        content.placeRelative(nav.width, top.height)
                        player.placeRelative(nav.width + (rest - playerWidth) / 2, height - margin - player.height, CHROME)
                        band.placeRelative(0, height, BAND)
                        overlay.placeRelative(0, 0, OVERLAY)
                    }
                } else {
                    val player = playerM.measure(Constraints(minWidth = rest, maxWidth = rest, maxHeight = height))
                    val contentHeight = (height - top.height - player.height).coerceAtLeast(0)
                    val content = contentM.measure(Constraints.fixed(rest, contentHeight))
                    layout(width, fullHeight) {
                        top.placeRelative(nav.width, 0, CHROME)
                        nav.placeRelative(0, 0, CHROME)
                        content.placeRelative(nav.width, top.height)
                        player.placeRelative(nav.width, height - player.height, CHROME)
                        band.placeRelative(0, height, BAND)
                        overlay.placeRelative(0, 0, OVERLAY)
                    }
                }
            }

            NavKind.Sidebar -> {
                val top = topM.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = height))
                if (floatingPlayer) {
                    val middle = (height - top.height).coerceAtLeast(0)
                    val nav = navM.measure(Constraints(maxWidth = width, minHeight = middle, maxHeight = middle))
                    val contentWidth = (width - nav.width).coerceAtLeast(0)
                    val content = contentM.measure(Constraints.fixed(contentWidth, middle))
                    val playerWidth = (contentWidth - 2 * margin).coerceAtMost(floatingMaxWidth.roundToPx()).coerceAtLeast(0)
                    val player = playerM.measure(Constraints(minWidth = playerWidth, maxWidth = playerWidth, maxHeight = height))
                    layout(width, fullHeight) {
                        top.placeRelative(0, 0, CHROME)
                        nav.placeRelative(0, top.height, CHROME)
                        content.placeRelative(nav.width, top.height)
                        player.placeRelative(nav.width + (contentWidth - playerWidth) / 2, height - margin - player.height, CHROME)
                        band.placeRelative(0, height, BAND)
                        overlay.placeRelative(0, 0, OVERLAY)
                    }
                } else {
                    val player = playerM.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = height))
                    val middle = (height - top.height - player.height).coerceAtLeast(0)
                    val nav = navM.measure(Constraints(maxWidth = width, minHeight = middle, maxHeight = middle))
                    val contentWidth = (width - nav.width).coerceAtLeast(0)
                    val content = contentM.measure(Constraints.fixed(contentWidth, middle))
                    layout(width, fullHeight) {
                        top.placeRelative(0, 0, CHROME)
                        nav.placeRelative(0, top.height, CHROME)
                        content.placeRelative(nav.width, top.height)
                        player.placeRelative(0, height - player.height, CHROME)
                        band.placeRelative(0, height, BAND)
                        overlay.placeRelative(0, 0, OVERLAY)
                    }
                }
            }
        }
    }
}
