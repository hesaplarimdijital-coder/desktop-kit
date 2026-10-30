package desktopkit.accessibility

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.KeyEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import desktopkit.DesktopKitRoot
import desktopkit.SampleFile
import desktopkit.TestShell
import desktopkit.components.BannerTone
import desktopkit.components.CheckboxRow
import desktopkit.components.ChipOption
import desktopkit.components.ColumnWidth
import desktopkit.components.DenseTable
import desktopkit.components.DeskButton
import desktopkit.components.DeskCard
import desktopkit.components.DeskIconButton
import desktopkit.components.DeskIcons
import desktopkit.components.DeskListRow
import desktopkit.components.DeskSearchField
import desktopkit.components.DeskSlider
import desktopkit.components.DeskTab
import desktopkit.components.DeskTextField
import desktopkit.components.FilterChips
import desktopkit.components.IconMenuButton
import desktopkit.components.MenuButton
import desktopkit.components.NumberField
import desktopkit.components.SegmentedChoice
import desktopkit.components.SelectionBar
import desktopkit.components.StatusBanner
import desktopkit.components.SwatchChoice
import desktopkit.components.SwitchRow
import desktopkit.components.TabStrip
import desktopkit.components.TableColumn
import desktopkit.components.TableSort
import desktopkit.sampleFiles
import desktopkit.screenshots.ShellScreenshotTest
import desktopkit.settings.DeskPreferences
import desktopkit.settings.rememberDeskPreferences
import desktopkit.theme.Accent
import desktopkit.theme.DensityPreference
import desktopkit.theme.DeskTheme
import desktopkit.theme.DesktopKitTheme
import desktopkit.theme.InputMode
import desktopkit.theme.ThemeMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.input.InputMode as ComposeInputMode

/**
 * T4: twice the text size cuts no line, every control has a name for TalkBack, and the Tab key
 * walks a DeX window in reading order.
 */
@OptIn(ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccessibilityTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var view: View
    private lateinit var inputModeManager: InputModeManager

    // ------------------------------------------------------------------ content

    /** Every interactive component of the kit, labelled the way an app labels them. */
    @Composable
    private fun Controls() {
        Column(
            Modifier.fillMaxSize().background(DeskTheme.colors.background).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DeskIconButton(DeskIcons.Play, "Oynat", {})
                DeskButton("Klasör seç", {}, icon = DeskIcons.Folder)
                DeskButton("Dönüştür", {}, primary = true)
                MenuButton("USB DAC", entries = { emptyList() })
                IconMenuButton(DeskIcons.Sort, "Sırala", entries = { emptyList() })
            }
            SegmentedChoice(listOf(1 to "Açık", 2 to "Koyu", 3 to "OLED"), selected = 1, onSelect = {})
            FilterChips(
                options = listOf(ChipOption("ses", "Ses", count = "12"), ChipOption("video", "Video")),
                selected = setOf("ses"),
                onToggle = {},
                wrap = true,
                contentPadding = 0.dp,
            )
            SwatchChoice(listOf(Triple(1, Accent.Blue.base, "Mavi"), Triple(2, Accent.Amber.base, "Kehribar")), selected = 1, onSelect = {})
            DeskSearchField("Kış", {}, Modifier.width(320.dp))
            DeskTextField("/storage/2A1F-0C33/Müzik", {}, Modifier.fillMaxWidth(), label = "Müzik klasörü", clearable = true, mono = true)
            NumberField(120, {}, label = "Ön tampon", min = 0, max = 5000, suffix = "ms", steppers = true)
            SwitchRow("Sesi DAC'a doğrudan gönder", checked = true, onCheckedChange = {},
                subtitle = "Ses düzeyi DAC'tan ayarlanır; bildirim sesleri bu sırada duyulmaz")
            CheckboxRow("Kayıpsız: FLAC, ALAC, WAV", checked = true, onCheckedChange = {})
            CheckboxRow("Kayıplı (2 / 3)", ToggleableState.Indeterminate, onClick = {})
            DeskSlider(0.3f, {}, Modifier.width(320.dp), label = "Ses düzeyi")
            TabStrip(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                DeskTab("Albümler", selected = true, onClick = {}, badge = 12)
                DeskTab("Arama: Kış", selected = false, onClick = {}, onClose = {})
            }
            DeskListRow("Kış Bahçesi", subtitle = "Mavi Hat · 2021", trailing = "42:10", onClick = {}, menu = { emptyList() })
            DeskCard(title = "Çıkış", onClick = {}) {
                Text("USB DAC · 24-bit / 192 kHz", style = DeskTheme.type.body, color = DeskTheme.colors.textSecondary)
            }
            StatusBanner(BannerTone.Warning, "Paylaşım klasörü bulunamadı.", actionLabel = "Yeniden dene", onAction = {},
                closeLabel = "Kapat", onClose = {})
            Box(Modifier.fillMaxWidth().height(200.dp)) {
                DenseTable(
                    columns = listOf(
                        TableColumn<SampleFile>("Ad", ColumnWidth.Weight(1f)) { it.name },
                        TableColumn<SampleFile>("Boyut", ColumnWidth.Fixed(100.dp), alignEnd = true, mono = true) { it.size },
                    ),
                    rows = sampleFiles.take(3),
                    key = { it.name },
                    onRowClick = { _, _ -> },
                    sort = TableSort(0, ascending = true),
                    onSortChange = {},
                    rowMenu = { _, _ -> emptyList() },
                    rowMenuLabel = "Diğer işlemler",
                )
            }
            SelectionBar("1 seçili", onClose = {}) {
                IconMenuButton(DeskIcons.Delete, "Sil", entries = { emptyList() })
            }
        }
    }

    private fun controls(input: InputMode) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            view = LocalView.current
            DesktopKitTheme(themeMode = ThemeMode.Dark, inputMode = input) { Controls() }
        }
        compose.waitForIdle()
    }

    private fun shell(pointer: Boolean, content: @Composable () -> Unit = {}) {
        if (pointer) {
            compose.activity.getSharedPreferences(DeskPreferences.FILE, android.content.Context.MODE_PRIVATE).edit()
                .putString(DeskPreferences.KEY_DENSITY, DensityPreference.Pointer.name).commit()
        }
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            view = LocalView.current
            inputModeManager = LocalInputModeManager.current
            DesktopKitRoot(rememberDeskPreferences(defaultTheme = ThemeMode.Dark)) { widthClass ->
                TestShell(widthClass, content = { route, _ ->
                    if (route == "home") content() else desktopkit.SampleScreen(route)
                })
            }
        }
        compose.waitForIdle()
    }

    private fun screenshot(name: String) {
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    // ------------------------------------------------------------------ 200 % text

    private fun textLayouts(): List<TextLayoutResult> =
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .mapNotNull { node ->
                val results = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                results.firstOrNull()
            }

    /**
     * Text that is cut off: its box is lower than its lines (a fixed height), or it runs on past
     * its box or its last line with nothing to show it (no ellipsis).
     */
    private fun cutText(): List<String> = textLayouts().mapNotNull { result ->
        val lines = result.multiParagraph.height
        val text = result.layoutInput.text
        when {
            result.size.height + 1 < lines -> "“$text” ${result.size.height} px < $lines px"
            result.hasVisualOverflow && result.layoutInput.overflow == TextOverflow.Clip -> "“$text” clipped without an ellipsis"
            else -> null
        }
    }

    /** One-line text shortened with an ellipsis (a long title in a row): allowed, and listed in the test's output. */
    private fun shortenedText(): List<String> = textLayouts()
        .filter { it.hasVisualOverflow && it.layoutInput.overflow != TextOverflow.Clip && it.layoutInput.overflow != TextOverflow.Visible }
        .map { it.layoutInput.text.text }

    private fun checkText(name: String) {
        val cut = cutText()
        println("$name, shortened at 200 %: ${shortenedText()}")
        screenshot(name)
        assertTrue("cut off at 200 %: $cut", cut.isEmpty())
    }

    @Test
    @Config(qualifiers = "tr-w411dp-h891dp-hdpi", fontScale = 2f)
    fun phoneAtTwiceTheTextSize() {
        shell(pointer = false) { Controls() }
        checkText("a11y-phone-text-200")
    }

    @Test
    @Config(qualifiers = "tr-w1280dp-h800dp-mdpi", fontScale = 2f)
    fun dexAtTwiceTheTextSize() {
        shell(pointer = true) { Controls() }
        checkText("a11y-dex-text-200")
    }

    @Test
    @Config(qualifiers = "tr-w700dp-h1600dp-mdpi", fontScale = 2f)
    fun controlsAtTwiceTheTextSize() {
        controls(InputMode.Touch)
        checkText("a11y-controls-text-200")
    }

    // ------------------------------------------------------------------ names for TalkBack

    private fun SemanticsNode.name(): String {
        val config = config
        return listOfNotNull(
            config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
            config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text },
            config.getOrNull(SemanticsProperties.EditableText)?.text,
        ).joinToString(" ").trim()
    }

    /** Controls TalkBack can reach that would be read without a name. */
    private fun unnamed(): List<String> {
        val control = SemanticsMatcher("control") { node ->
            node.config.contains(SemanticsActions.OnClick) || node.config.contains(SemanticsActions.SetProgress) ||
                node.config.contains(SemanticsProperties.EditableText)
        }
        return compose.onAllNodes(control).fetchSemanticsNodes().filter { it.name().isEmpty() }.map { node ->
            "${node.config.getOrNull(SemanticsProperties.Role) ?: "?"} at ${node.boundsInRoot}"
        }
    }

    /** Check boxes and switches say whether they are on. */
    private fun togglesWithoutState(): List<String> =
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Role)).fetchSemanticsNodes().filter { node ->
            val role = node.config[SemanticsProperties.Role]
            (role == androidx.compose.ui.semantics.Role.Checkbox || role == androidx.compose.ui.semantics.Role.Switch) &&
                !node.config.contains(SemanticsProperties.ToggleableState)
        }.map { it.name() }

    @Test
    @Config(qualifiers = "tr-w900dp-h1600dp-mdpi")
    fun everyControlHasANameOnTouch() {
        controls(InputMode.Touch)
        assertTrue("without a name: ${unnamed()}", unnamed().isEmpty())
        assertTrue("toggles without a state: ${togglesWithoutState()}", togglesWithoutState().isEmpty())
    }

    @Test
    @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun everyControlOfThePhoneShellHasAName() {
        shell(pointer = false)
        assertTrue("without a name: ${unnamed()}", unnamed().isEmpty())
    }

    @Test
    @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun everyControlOfTheDexShellHasAName() {
        shell(pointer = true)
        assertTrue("without a name: ${unnamed()}", unnamed().isEmpty())
    }

    // ------------------------------------------------------------------ the keyboard

    private fun press(keyCode: Int, shift: Boolean = false) {
        val meta = if (shift) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
        compose.runOnUiThread {
            view.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
            view.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0, meta))
        }
        compose.waitForIdle()
    }

    private fun focusedName(): String? =
        compose.onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull()?.name()

    @Test
    @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun tabWalksADexWindowInReadingOrder() {
        shell(pointer = true) {
            Column(Modifier.padding(20.dp)) {
                DeskButton("İçerikteki düğme", {})
            }
        }
        compose.runOnIdle { inputModeManager.requestInputMode(ComposeInputMode.Keyboard) }
        // from the first menu on, as after a click on it
        compose.onNodeWithText("Dosya").requestFocus()
        val visited = mutableListOf<String>()
        focusedName()?.let { visited += it }
        repeat(30) {
            press(KeyEvent.KEYCODE_TAB)
            focusedName()?.let { visited += it }
        }
        val menu = visited.indexOfFirst { it == "Dosya" }
        val sidebar = visited.indexOfFirst { it.startsWith("Ana sayfa") }
        val content = visited.indexOfFirst { it == "İçerikteki düğme" }
        assertTrue("the menu bar, the sidebar, then the content: $visited", menu in 0 until sidebar && sidebar < content)
        assertTrue("every menu and the search field on the way: $visited",
            listOf("Düzen", "Görünüm", "Araçlar", "Yardım").all { it in visited.subList(menu, sidebar) })
        // Shift+Tab goes back the same way
        val before = focusedName()
        press(KeyEvent.KEYCODE_TAB)
        press(KeyEvent.KEYCODE_TAB, shift = true)
        assertTrue("Shift+Tab returns to $before, at ${focusedName()}", focusedName() == before)
    }
}
