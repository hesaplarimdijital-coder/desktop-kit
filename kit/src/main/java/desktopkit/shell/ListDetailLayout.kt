package desktopkit.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
 * A list and the detail of what is chosen in it, laid out for the room it has (Android's list–detail):
 *
 * - narrower than [twoPanesFrom], or with less room than [minListWidth] and [minDetailWidth] together (a phone, a
 *   tablet's narrow window, a main pane beside an inspector): one pane at a time, the detail opening over the list with
 *   [DetailTransition]'s motion and Back closing it;
 * - from [twoPanesFrom] up (Expanded by default: a tablet held either way, DeX) with that room: the list and the detail
 *   side by side. The list is [listWidth] wide (360 dp, 412 dp from Large up) and the divider between them can be
 *   dragged; with [prefs] and [splitKey] its place is kept. With nothing chosen the detail pane shows [placeholder] (a
 *   [desktopkit.screens.MessageScreen] saying what to pick); a new choice cross-fades the detail. Back is the app's
 *   there ([handleBackInTwoPanes] true makes it close the detail as on a phone).
 *
 * The list and the detail keep their state when the layout changes (a window resized on DeX, a tablet turned): a detail
 * open on a phone stays open beside the list once the window widens, and a detail open beside the list stays open,
 * alone, once it narrows; the list keeps its scroll position and what was typed in it either way. The app keeps [detail]
 * and marks the chosen row in its list itself.
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
    // The list's saved state lives here, not in the layout of the moment: when a layout change leaves the list out
    // of the composition for a while (a detail covering it on a phone), it comes back with its scroll position.
    val saved = rememberSaveableStateHolder()
    // the same list in either layout: its state moves with it
    val movableList = remember { movableContentOf { saved.SaveableStateProvider(LIST_KEY) { latestList() } } }
    // one movable detail per chosen item: a detail moves between the layouts with its state, another item's detail
    // starts afresh; every detail still on screen (a cross-fade of several quick choices) keeps its own
    val details = remember { DetailSlots<T>() }
    val currentKey = detail?.let(detailKey)
    SideEffect { details.prune(keep = currentKey) }
    val detailOf: @Composable (T) -> Unit = { item ->
        val key = detailKey(item)
        val slot = details.slots.getOrPut(key) { movableContentOf { shown: T -> latestDetail(shown) } }
        DisposableEffect(key) {
            details.users[key] = (details.users[key] ?: 0) + 1
            onDispose { details.users[key] = (details.users[key] ?: 1) - 1 }
        }
        slot(item)
    }
    val wide = currentWidthClass() >= twoPanesFrom
    BoxWithConstraints(modifier) {
        // side by side only with room for both panes: a main pane beside an inspector can be narrower than its window
        val twoPanes = wide && maxWidth >= minListWidth + minDetailWidth
        if (!twoPanes) {
            DetailTransition(
                detail = detail,
                onBack = onBack,
                modifier = Modifier.fillMaxSize(),
                detailKey = detailKey,
                list = movableList,
                sharedElements = sharedElements,
            ) { shown -> detailOf(shown) }
            return@BoxWithConstraints
        }
        val split: SplitState = if (prefs != null && splitKey != null) rememberSplitState(listWidth, prefs, splitKey) else rememberSplitState(listWidth)
        val tier = LocalMotionTier.current
        BackHandler(enabled = handleBackInTwoPanes && detail != null) { onBack() }
        HorizontalSplitPane(
            state = split,
            modifier = Modifier.fillMaxSize(),
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
}

private const val LIST_KEY = "desktopkit.list"

/**
 * The details' movable contents by key, and how many places compose each one. A detail is let go once nothing composes
 * it and it is not the chosen one; while it is on screen (still fading out) it keeps its content and its state.
 */
private class DetailSlots<T> {
    val slots = HashMap<Any, @Composable (T) -> Unit>()
    val users = HashMap<Any, Int>()

    fun prune(keep: Any?) {
        val gone = slots.keys.filter { it != keep && (users[it] ?: 0) <= 0 }
        gone.forEach {
            slots.remove(it)
            users.remove(it)
        }
    }
}
