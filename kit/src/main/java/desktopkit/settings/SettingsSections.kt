package desktopkit.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import desktopkit.R
import desktopkit.components.HorizontalRule
import desktopkit.components.SegmentedChoice
import desktopkit.components.SettingRow
import desktopkit.components.SwatchChoice
import desktopkit.input.ShortcutRegistry
import desktopkit.input.ShortcutScope
import desktopkit.shell.KeyCap
import desktopkit.theme.Accent
import desktopkit.theme.DensityPreference
import desktopkit.theme.DeskTheme
import desktopkit.theme.ThemeMode

/** A titled card of a settings page, at most 760 dp wide so lines stay readable on big screens. */
@Composable
fun SettingsSection(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = DeskTheme.colors
    val dimens = DeskTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusCard)
    Box(modifier.fillMaxWidth().padding(horizontal = dimens.pagePadding, vertical = dimens.gapS)) {
        Column(
            Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .background(colors.surface, shape)
                .border(1.dp, colors.border, shape)
                .padding(dimens.gapL),
        ) {
            Text(title, style = DeskTheme.type.title, color = colors.text, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(dimens.gapS))
            HorizontalRule()
            Spacer(Modifier.height(dimens.gapS))
            content()
        }
    }
}

/** Theme and accent choices; [accentLabel] names an accent (presets have translated names). */
@Composable
fun AppearanceSection(prefs: DeskPreferences, accentLabel: @Composable (Accent) -> String = { presetAccentLabel(it) }) {
    SettingsSection(stringResource(R.string.dk_settings_appearance)) {
        SettingRow(stringResource(R.string.dk_settings_theme)) {
            SegmentedChoice(
                options = listOf(
                    ThemeMode.System to stringResource(R.string.dk_theme_system),
                    ThemeMode.Light to stringResource(R.string.dk_theme_light),
                    ThemeMode.Dark to stringResource(R.string.dk_theme_dark),
                    ThemeMode.Oled to stringResource(R.string.dk_theme_oled),
                ),
                selected = prefs.themeMode,
                onSelect = prefs::updateThemeMode,
            )
        }
        if (prefs.accents.size > 1) {
            SettingRow(stringResource(R.string.dk_settings_accent)) {
                SwatchChoice(
                    options = prefs.accents.map { Triple(it, it.base, accentLabel(it)) },
                    selected = prefs.accent,
                    onSelect = prefs::updateAccent,
                )
            }
        }
    }
}

/** Density choice: automatic, touch, or mouse and keyboard. */
@Composable
fun InputSection(prefs: DeskPreferences) {
    SettingsSection(stringResource(R.string.dk_settings_input)) {
        SettingRow(stringResource(R.string.dk_settings_density)) {
            SegmentedChoice(
                options = listOf(
                    DensityPreference.Auto to stringResource(R.string.dk_theme_system),
                    DensityPreference.Touch to stringResource(R.string.dk_density_touch),
                    DensityPreference.Pointer to stringResource(R.string.dk_density_pointer),
                ),
                selected = prefs.density,
                onSelect = prefs::updateDensity,
            )
        }
        Text(stringResource(R.string.dk_density_auto), style = DeskTheme.type.labelSmall, color = DeskTheme.colors.textTertiary)
    }
}

/**
 * Every shortcut of [registry], grouped. Shortcuts outside the global scope are marked with
 * [scopeLabel] (e.g. "In the player").
 */
@Composable
fun ShortcutListSection(registry: ShortcutRegistry, scopeLabel: @Composable (ShortcutScope) -> String? = { null }) {
    val colors = DeskTheme.colors
    val resources = LocalContext.current.resources
    SettingsSection(stringResource(R.string.dk_shortcut_list)) {
        registry.groups.forEach { group ->
            val shortcuts = registry.inGroup(group)
            if (shortcuts.isEmpty()) return@forEach
            Text(
                stringResource(group.title),
                style = DeskTheme.type.label,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = DeskTheme.dimens.gapM, bottom = 4.dp),
            )
            shortcuts.forEach { shortcut ->
                Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(shortcut.label), style = DeskTheme.type.body, color = colors.text, modifier = Modifier.weight(1f))
                    if (shortcut.scope != ShortcutScope.Global) {
                        scopeLabel(shortcut.scope)?.let {
                            Text(it, style = DeskTheme.type.caption, color = colors.textTertiary, modifier = Modifier.padding(end = 8.dp))
                        }
                    }
                    KeyCap(shortcut.chord.label(resources))
                }
            }
        }
    }
}

@Composable
private fun presetAccentLabel(accent: Accent): String = when (accent) {
    Accent.Blue -> stringResource(R.string.dk_accent_blue)
    Accent.Amber -> stringResource(R.string.dk_accent_amber)
    Accent.Green -> stringResource(R.string.dk_accent_green)
    Accent.Coral -> stringResource(R.string.dk_accent_coral)
    else -> accent.name
}
