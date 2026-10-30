package desktopkit

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * The app's resources for code that reads them directly (`getQuantityString`, arrays, formats),
 * with the same promise as `stringResource`: when the configuration changes (the in-app language,
 * the font size, the theme) the caller is composed again and reads the new values.
 *
 * Reading `LocalContext.current.resources` alone does not do that: the context stays the same
 * object, so the caller keeps the old language until something else recomposes it.
 */
@Composable
@ReadOnlyComposable
fun appResources(): Resources {
    // read for its invalidation: a new configuration recomposes the caller
    LocalConfiguration.current
    return LocalContext.current.resources
}

/**
 * A quantity string ("1 file", "3 files") that follows a language change like [appResources].
 * Without [formatArgs] the count fills the string's placeholder ("%d files"), as most plurals
 * expect; with them, they do (the count then only picks the form).
 */
@Composable
@ReadOnlyComposable
fun pluralString(@PluralsRes id: Int, count: Int, vararg formatArgs: Any): String {
    val resources = appResources()
    return if (formatArgs.isEmpty()) resources.getQuantityString(id, count, count) else resources.getQuantityString(id, count, *formatArgs)
}
