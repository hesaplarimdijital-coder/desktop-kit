package desktopkit.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import desktopkit.R
import desktopkit.shell.KeyCap
import desktopkit.theme.DeskTheme

/** How a dialog shows that it can be closed: an "Esc" key cap with a hardware keyboard, a × button otherwise. */
enum class DismissStyle {
    /** A hardware keyboard is attached (DeX, a keyboard cover): the "Esc" key cap, which can be clicked too. */
    EscKey,

    /** Touch only: a 48 dp × button. */
    CloseButton,
}

/** The close affordance of the surrounding dialog frame; read by [DialogDismissButton]. */
@Immutable
class DialogDismiss(val style: DismissStyle, val onDismiss: () -> Unit)

/** Provided by [DeskDialog] (and [DialogFrame]); null outside a dialog. */
val LocalDialogDismiss = compositionLocalOf<DialogDismiss?> { null }

/**
 * [DismissStyle.EscKey] when [configuration] reports a hardware QWERTY keyboard that is not
 * hidden (`Configuration.KEYBOARD_QWERTY`), [DismissStyle.CloseButton] otherwise.
 */
fun dismissStyleFor(configuration: Configuration): DismissStyle =
    if (configuration.keyboard == Configuration.KEYBOARD_QWERTY &&
        configuration.hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES
    ) DismissStyle.EscKey else DismissStyle.CloseButton

/**
 * The close affordance of the dialog it is in: the "Esc" key cap when a hardware keyboard is
 * present, a × button otherwise; both call the dialog's dismiss. Nothing outside a dialog frame.
 */
@Composable
fun DialogDismissButton(modifier: Modifier = Modifier) {
    val dismiss = LocalDialogDismiss.current ?: return
    val label = stringResource(R.string.dk_close)
    when (dismiss.style) {
        DismissStyle.CloseButton -> DeskIconButton(DeskIcons.Close, label, dismiss.onDismiss, modifier, size = 48.dp)
        DismissStyle.EscKey -> {
            val interaction = remember { MutableInteractionSource() }
            val state = interaction.collectState()
            val shape = RoundedCornerShape(6.dp)
            Box(
                modifier
                    .focusRing(state, shape)
                    .background(stateBackground(state), shape)
                    .deskClickable(interaction, dismiss.onDismiss, role = Role.Button, onClickLabel = label)
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) { KeyCap(stringResource(R.string.dk_key_esc)) }
        }
    }
}

/**
 * A desktop-class dialog: a raised card with [title], the close affordance ([DialogDismissButton]:
 * "Esc" with a hardware keyboard, × on touch), [content] and a row of [actions] at the end.
 * Esc, Back and a tap outside call [onDismiss].
 */
@Composable
fun DeskDialog(
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 560.dp,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogFrame(onDismiss, title, modifier.padding(24.dp), maxWidth, actions, content)
    }
}

/**
 * The card of [DeskDialog] without the dialog window, for showing it inline (a settings pane, a
 * screenshot). Provides [LocalDialogDismiss] for [dismissStyle] (by default from the configuration).
 */
@Composable
fun DialogFrame(
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 560.dp,
    actions: @Composable RowScope.() -> Unit = {},
    dismissStyle: DismissStyle = dismissStyleFor(LocalConfiguration.current),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusCard.coerceAtLeast(12.dp))
    val dismiss = remember(dismissStyle, onDismiss) { DialogDismiss(dismissStyle, onDismiss) }
    CompositionLocalProvider(LocalDialogDismiss provides dismiss) {
        Column(
            modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .shadow(16.dp, shape)
                .background(colors.surfaceRaised, shape)
                .border(1.dp, colors.borderStrong, shape)
                .semantics { paneTitle = title }
                .onPreviewKeyEvent { event ->
                    val esc = event.key == Key.Escape && event.type == KeyEventType.KeyDown
                    if (esc) onDismiss()
                    esc
                }
                .padding(bottom = dimens.gapL),
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = dimens.gapXl, end = dimens.gapS, top = dimens.gapS),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = DeskTheme.type.title, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                Spacer(Modifier.width(dimens.gapS))
                DialogDismissButton()
            }
            Column(Modifier.padding(horizontal = dimens.gapXl), content = content)
            Row(
                Modifier.fillMaxWidth().padding(start = dimens.gapXl, end = dimens.gapXl, top = dimens.gapL),
                horizontalArrangement = Arrangement.spacedBy(dimens.gapS, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}
