package desktopkit.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import desktopkit.components.DeskButton
import desktopkit.components.collectState
import desktopkit.components.deskClickable
import desktopkit.components.focusRing
import desktopkit.components.stateBackground
import desktopkit.shell.LocalNavKind
import desktopkit.shell.NavItem
import desktopkit.shell.NavKind
import desktopkit.theme.DeskTheme

/**
 * Title row of a screen. In the sidebar layout (large windows, DeX) the menu bar carries no page
 * title, so the screen shows it here. On smaller windows the top bar already shows a title, so
 * [title] is not repeated: only [subtitle] (small) and [actions] remain. On compact windows
 * (phones) a header with neither of them takes no space at all; to show this screen's title in
 * the phone's top bar instead of the route's label, call [desktopkit.shell.CompactTopBar].
 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val dimens = DeskTheme.dimens
    val navKind = LocalNavKind.current
    val showTitle = navKind == NavKind.Sidebar
    val vertical = if (navKind == NavKind.BottomBar && subtitle == null) {
        Modifier.verticalPaddingUnlessEmpty(dimens.gapS)
    } else {
        Modifier.padding(top = if (showTitle) dimens.gapL else dimens.gapS, bottom = dimens.gapS)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = dimens.pagePadding, end = dimens.pagePadding)
            .then(vertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showTitle) {
            Text(title, style = DeskTheme.type.display, color = DeskTheme.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() })
            if (subtitle != null) {
                Spacer(Modifier.width(dimens.gapS))
                Text(subtitle, style = DeskTheme.type.body, color = DeskTheme.colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else if (subtitle != null) {
            Text(subtitle, style = DeskTheme.type.labelSmall, color = DeskTheme.colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.gapXs), content = actions)
    }
}

/** [padding] above and below, or no height at all when the content has none (a header without actions). */
private fun Modifier.verticalPaddingUnlessEmpty(padding: Dp): Modifier = layout { measurable, constraints ->
    val pad = padding.roundToPx()
    val inner = constraints.copy(
        minHeight = (constraints.minHeight - 2 * pad).coerceAtLeast(0),
        maxHeight = if (constraints.hasBoundedHeight) (constraints.maxHeight - 2 * pad).coerceAtLeast(0) else constraints.maxHeight,
    )
    val placeable = measurable.measure(inner)
    val height = if (placeable.height == 0) 0 else placeable.height + 2 * pad
    layout(placeable.width, height.coerceIn(constraints.minHeight, constraints.maxHeight)) { placeable.place(0, pad) }
}

/**
 * Centred icon, title, description and an optional button: for empty states, screens that
 * are not built yet and errors.
 */
@Composable
fun MessageScreen(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    pageTitle: String? = null,
    description: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    Column(modifier.fillMaxSize()) {
        if (pageTitle != null) PageHeader(title = pageTitle)
        Box(Modifier.fillMaxSize().padding(dimens.pagePadding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 420.dp)) {
                Box(
                    Modifier.size(64.dp).background(colors.surfaceRaised, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(dimens.gapL))
                Text(title, style = DeskTheme.type.title, color = colors.text, textAlign = TextAlign.Center)
                if (description != null) {
                    Spacer(Modifier.height(dimens.gapS))
                    Text(description, style = DeskTheme.type.body, color = colors.textSecondary, textAlign = TextAlign.Center)
                }
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.height(dimens.gapL))
                    DeskButton(text = actionLabel, onClick = onAction)
                }
            }
        }
    }
}

/** The "More" screen: every item that the current navigation chrome does not show. */
@Composable
fun OverflowScreen(entries: List<NavItem>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val dimens = DeskTheme.dimens
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = dimens.gapS)) {
        items(entries, key = { it.route }) { item -> ItemRow(item) { onSelect(item.route) } }
    }
}

@Composable
private fun ItemRow(item: NavItem, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.gapS)
            .heightIn(min = dimens.rowHeight.coerceAtLeast(dimens.minTarget))
            .focusRing(state, shape)
            .background(stateBackground(state), shape)
            .deskClickable(interaction, onClick)
            .padding(horizontal = dimens.gapM, vertical = dimens.gapS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(item.icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(dimens.iconLarge))
        Spacer(Modifier.width(dimens.gapM))
        Text(item.label, style = DeskTheme.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
