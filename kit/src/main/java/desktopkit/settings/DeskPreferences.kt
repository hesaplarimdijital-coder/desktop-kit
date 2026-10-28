package desktopkit.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import desktopkit.theme.Accent
import desktopkit.theme.DensityPreference
import desktopkit.theme.ThemeMode

/**
 * Appearance and shell preferences (theme, accent, density, sidebar, inspector width, table
 * columns), stored in shared preferences and observable from Compose. [accents] lists the accents
 * the app offers; the stored name is looked up there.
 */
@Stable
class DeskPreferences(
    private val prefs: SharedPreferences,
    val accents: List<Accent> = Accent.presets,
    defaultTheme: ThemeMode = ThemeMode.System,
    defaultAccent: Accent = accents.first(),
) {
    var themeMode by mutableStateOf(prefs.enumValue(KEY_THEME, defaultTheme))
        private set
    var accent by mutableStateOf(accents.firstOrNull { it.name == prefs.getString(KEY_ACCENT, null) } ?: defaultAccent)
        private set
    var density by mutableStateOf(prefs.enumValue(KEY_DENSITY, DensityPreference.Auto))
        private set
    var sidebarCollapsed by mutableStateOf(prefs.getBoolean(KEY_SIDEBAR_COLLAPSED, false))
        private set

    /** Width of the workstation's inspector pane in dp; NaN until the user resizes it. */
    var inspectorWidth by mutableFloatStateOf(prefs.getFloat(KEY_INSPECTOR_WIDTH, Float.NaN))
        private set
    var inspectorCollapsed by mutableStateOf(prefs.getBoolean(KEY_INSPECTOR_COLLAPSED, false))
        private set

    fun updateThemeMode(value: ThemeMode) {
        themeMode = value
        prefs.edit().putString(KEY_THEME, value.name).apply()
    }

    fun updateAccent(value: Accent) {
        accent = value
        prefs.edit().putString(KEY_ACCENT, value.name).apply()
    }

    fun updateDensity(value: DensityPreference) {
        density = value
        prefs.edit().putString(KEY_DENSITY, value.name).apply()
    }

    fun updateSidebarCollapsed(value: Boolean) {
        sidebarCollapsed = value
        prefs.edit().putBoolean(KEY_SIDEBAR_COLLAPSED, value).apply()
    }

    fun updateInspectorWidth(value: Float) {
        inspectorWidth = value
        prefs.edit().putFloat(KEY_INSPECTOR_WIDTH, value).apply()
    }

    fun updateInspectorCollapsed(value: Boolean) {
        inspectorCollapsed = value
        prefs.edit().putBoolean(KEY_INSPECTOR_COLLAPSED, value).apply()
    }

    /** The column order and hidden columns stored for the table [key], or null when never changed. */
    fun tableColumns(key: String): Pair<List<String>, Set<String>>? {
        val order = prefs.getString(KEY_TABLE_PREFIX + key + "_order", null) ?: return null
        val hidden = prefs.getString(KEY_TABLE_PREFIX + key + "_hidden", "").orEmpty()
        return order.split(SEPARATOR).filter { it.isNotEmpty() } to hidden.split(SEPARATOR).filter { it.isNotEmpty() }.toSet()
    }

    fun updateTableColumns(key: String, order: List<String>, hidden: Set<String>) {
        prefs.edit()
            .putString(KEY_TABLE_PREFIX + key + "_order", order.joinToString(SEPARATOR))
            .putString(KEY_TABLE_PREFIX + key + "_hidden", hidden.joinToString(SEPARATOR))
            .apply()
    }

    companion object {
        const val FILE = "desktopkit"
        const val KEY_INSPECTOR_WIDTH = "dk_inspector_width"
        const val KEY_INSPECTOR_COLLAPSED = "dk_inspector_collapsed"
        const val KEY_TABLE_PREFIX = "dk_table_"
        private const val SEPARATOR = "\u001F"
        const val KEY_THEME = "dk_theme"
        const val KEY_ACCENT = "dk_accent"
        const val KEY_DENSITY = "dk_density"
        const val KEY_SIDEBAR_COLLAPSED = "dk_sidebar_collapsed"
    }
}

/** Preferences kept in their own file ("desktopkit"), so they never clash with the app's keys. */
@Composable
fun rememberDeskPreferences(
    accents: List<Accent> = Accent.presets,
    defaultTheme: ThemeMode = ThemeMode.System,
): DeskPreferences {
    val context = LocalContext.current.applicationContext
    return remember(accents, defaultTheme) {
        DeskPreferences(context.getSharedPreferences(DeskPreferences.FILE, Context.MODE_PRIVATE), accents, defaultTheme)
    }
}

private inline fun <reified E : Enum<E>> SharedPreferences.enumValue(key: String, default: E): E {
    val stored = getString(key, null) ?: return default
    return enumValues<E>().firstOrNull { it.name == stored } ?: default
}
