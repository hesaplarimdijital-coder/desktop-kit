package desktopkit.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import desktopkit.components.HorizontalSplitPane
import desktopkit.components.SplitState
import desktopkit.components.rememberSplitState
import desktopkit.settings.DeskPreferences
import desktopkit.theme.DeskMotion
import desktopkit.theme.DeskTheme
import desktopkit.theme.LocalMotionTier
import desktopkit.theme.MotionTier

/**
 * A list and the detail of what is chosen in it, laid out for the window (Android's list–detail):
 *
 * - narrower than [twoPanesFrom] (phones, a tablet's narrow window): one pane at a time, the detail
 *   opening over the list with [DetailTransition]'s motion and Back closing it;
 * - from [twoPanesFrom] up (Expanded by default: a tablet held either way, DeX): the list and the detail
 *   side by side. The list is [listWidth] wide (360 dp, 412 dp from Large up) and the divider between
 *   them can be dragged; with [prefs] and [splitKey] its place is kept. With nothing chosen the detail
 *   pane shows [placeholder] (a [desktopkit.screens.MessageScreen] saying what to pick); a new choice
 *   cross-fades the detail. Back is the app's there ([handleBackInTwoPanes] true makes it close the
 *   detail as on a phone).
 *
 * The list and the detail keep their state when the window crosses [twoPanesFrom] (resized on DeX,
 * a tablet turned): a detail open on a phone stays open beside the list once the window widens, and
 * a detail open beside the list stays open, alone, once it narrows. The app keeps [detail] and marks
 * the chosen row in its list itself.
 */
@Composable
fun <T : Any> ListDetailLayout(
    detail: T?,
    onBack: () -> Unit,
    list: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    detailKey: (T) -> Any = { it },
    placeholder: @Composable () -> Unit = {},
    twoPanesFrom: WidthClass = WidthClass.Expanded,
    listWidth: Dp = if (currentWidthClass() >= WidthClass.Large) 412.dp else 360.dp,
    minListWidth: Dp = 280.dp,
    minDetailWidth: Dp = 360.dp,
    prefs: DeskPreferences? = null,
    splitKey: String? = null,
    handleBackInTwoPanes: Boolean = false,
    sharedElements: Boolean = true,
    detailContent: @Composable (T) -> Unit,
) {
    val latestList by rememberUpdatedState(list)
    val latestDetail by rememberUpdatedState(detailContent)
    // the same list and detail in either layout: their state moves with them
    val movableList = remember { movableContentOf { latestList() } }
    // one movable detail per chosen item: a detail moves between the layouts with its state, and another
    // item's detail starts afresh; the one on its way out keeps its own (the last two are kept)
    val details = remember {
        object : LinkedHashMap<Any, @Composable (T) -> Unit>(4, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, @Composable (T) -> Unit>?) = size > 2
        }
    }
    val detailOf: @Composable (T) -> Unit = { item ->
        details.getOrPut(detailKey(item)) { movableContentOf { shown: T -> latestDetail(shown) } }(item)
    }
    val twoPanes = currentWidthClass() >= twoPanesFrom
    if (!twoPanes) {
        DetailTransition(
            detail = detail,
            onBack = onBack,
            modifier = modifier,
            detailKey = detailKey,
            list = movableList,
            sharedElements = sharedElements,
        ) { shown -> detailOf(shown) }
        return
    }
    val split: SplitState = if (prefs != null && splitKey != null) rememberSplitState(listWidth, prefs, splitKey) else rememberSplitState(listWidth)
    val tier = LocalMotionTier.current
    BackHandler(enabled = handleBackInTwoPanes && detail != null) { onBack() }
    HorizontalSplitPane(
        state = split,
        modifier = modifier,
        minStart = minListWidth,
        minEnd = minDetailWidth,
        start = { Box(Modifier.fillMaxSize()) { movableList() } },
        end = {
            AnimatedContent(
                targetState = detail,
                contentKey = { it?.let(detailKey) },
                transitionSpec = {
                    // a new choice: the old detail is gone before the new one comes, as Android's own panes do
                    if (tier == MotionTier.Reduced) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    else fadeIn(tween(DeskMotion.MEDIUM_MS, delayMillis = DeskMotion.SHORT_MS)) togetherWith fadeOut(tween(DeskMotion.SHORT_MS))
                },
                label = "listDetailPane",
                modifier = Modifier.fillMaxSize().background(DeskTheme.colors.background),
            ) { shown ->
                Box(Modifier.fillMaxSize()) {
                    if (shown == null) placeholder() else detailOf(shown)
                }
            }
        },
    )
}
