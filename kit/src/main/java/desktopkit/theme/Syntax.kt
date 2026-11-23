package desktopkit.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * What a piece of source text is, for the code editor's colours ([desktopkit.editor.CodeEditor]). A
 * tokenizer names roles, never colours; the theme's [DeskSyntax] gives each role its look. The set is
 * meant for any language: a script language's section title is a [Heading], its jump target a [Link],
 * a markup language's `*bold*` a [Strong].
 */
enum class SyntaxRole {
    /** Notes for the reader the language ignores. */
    Comment,

    /** Words of the language itself (`if`, `return`, `VAR`). */
    Keyword,

    /** Quoted text. */
    String,
    Number,

    /** `true`, `null`, a named constant. */
    Constant,

    /** A name where it is defined (a variable, a label). */
    Name,

    /** A function or command, defined or called. */
    Function,
    Type,
    Operator,

    /** Brackets, commas, separators. */
    Punctuation,

    /** A tag or annotation attached to something (`#tag`, `@Annotation`). */
    Tag,

    /** A reference that leads somewhere else (a jump target, an include, a URL). */
    Link,

    /** A section title. */
    Heading,
    Emphasis,
    Strong,

    /** Directives to the tool rather than the program (a preprocessor line, a pragma). */
    Meta,

    /** Text the language cannot accept. */
    Invalid,
}

/**
 * How one [SyntaxRole] is drawn. [bold] and [italic] are drawn on the same glyph widths as plain
 * text (a thicker stroke, a slant), so a style never moves the text around it.
 */
@Immutable
data class SyntaxStyle(val color: Color, val bold: Boolean = false, val italic: Boolean = false)

/**
 * The code editor's colours: one [SyntaxStyle] for each [SyntaxRole], and the editor's own surfaces.
 * Made from the theme's colours by [DeskSyntax.from] and read through `DeskTheme.syntax`.
 *
 * The look is the kit's: tone before saturation. Comments, operators and punctuation are the theme's
 * greys, headings and emphasis the text colour with weight or slant, keywords and links the accent;
 * four quiet hues carry the rest (green for strings, amber for numbers and constants, teal for names
 * and functions, violet for types, tags and directives). Every role reaches 4.5:1 on [background],
 * on [currentLine] and on every tint the editor lays under text (search matches, marked lines and
 * ranges, the selection) in the light, dark and OLED themes with any accent (SyntaxContrastTest).
 *
 * Change single roles for one app through the theme's `syntax` hook:
 * `DesktopKitRoot(prefs, syntax = { it.with(SyntaxRole.Tag, SyntaxStyle(it.plain, italic = true)) })`.
 */
@Immutable
class DeskSyntax internal constructor(
    /** Text that has no role. */
    val plain: Color,
    /** The editor's background: the page colour. */
    val background: Color,
    /** The tint of the line the cursor is on. */
    val currentLine: Color,
    /** Line numbers in the gutter; the current line's number is [plain]. */
    val lineNumber: Color,
    private val styles: Array<SyntaxStyle>,
) {
    /** The style of [role]. */
    operator fun get(role: SyntaxRole): SyntaxStyle = styles[role.ordinal]

    /** These colours with [role] drawn as [style]. */
    fun with(role: SyntaxRole, style: SyntaxStyle): DeskSyntax =
        DeskSyntax(plain, background, currentLine, lineNumber, styles.copyOf().also { it[role.ordinal] = style })

    override fun equals(other: Any?): Boolean = other is DeskSyntax && plain == other.plain && background == other.background &&
        currentLine == other.currentLine && lineNumber == other.lineNumber && styles.contentEquals(other.styles)

    override fun hashCode(): Int = (plain.hashCode() * 31 + background.hashCode()) * 31 + styles.contentHashCode()

    override fun toString(): String = "DeskSyntax(${SyntaxRole.entries.joinToString { "${it.name}=${this[it]}" }})"

    companion object {
        /** The syntax colours that belong to [colors]: the light set on a light theme, the dark set otherwise. */
        fun from(colors: DeskColors): DeskSyntax {
            val dark = colors.isDark
            val background = colors.background
            // light: the raised surface is the page's white, so the hover tone marks the line
            val currentLine = if (dark) colors.surfaceRaised else colors.hover
            // readable on the page, on the current line and on every tint the editor lays under text (SyntaxTints),
            // whatever a colour hook or a custom accent made of them
            val surfaces = tintedSurfaces(colors, background, currentLine)
            fun fit(color: Color): Color = surfaces.fold(color) { fitted, surface -> ensureContrast(fitted, surface, lighten = dark) }
            val green = fit(if (dark) Color(0xFF8FD3A8) else Color(0xFF175E3D))
            val amber = fit(if (dark) Color(0xFFE2BB73) else Color(0xFF724A0D))
            val teal = fit(if (dark) Color(0xFF7CCBD6) else Color(0xFF0A5C67))
            val violet = fit(if (dark) Color(0xFFC3A9F4) else Color(0xFF603EAF))
            val text = fit(colors.text)
            val secondary = fit(colors.textSecondary)
            val tertiary = fit(colors.textTertiary)
            val accent = fit(colors.accentText)
            val styles = Array(SyntaxRole.entries.size) { index ->
                when (SyntaxRole.entries[index]) {
                    SyntaxRole.Comment -> SyntaxStyle(tertiary, italic = true)
                    SyntaxRole.Keyword -> SyntaxStyle(accent, bold = true)
                    SyntaxRole.String -> SyntaxStyle(green)
                    SyntaxRole.Number -> SyntaxStyle(amber)
                    SyntaxRole.Constant -> SyntaxStyle(amber)
                    SyntaxRole.Name -> SyntaxStyle(teal)
                    SyntaxRole.Function -> SyntaxStyle(teal)
                    SyntaxRole.Type -> SyntaxStyle(violet)
                    SyntaxRole.Operator -> SyntaxStyle(secondary)
                    SyntaxRole.Punctuation -> SyntaxStyle(secondary)
                    SyntaxRole.Tag -> SyntaxStyle(violet)
                    SyntaxRole.Link -> SyntaxStyle(accent)
                    SyntaxRole.Heading -> SyntaxStyle(text, bold = true)
                    SyntaxRole.Emphasis -> SyntaxStyle(text, italic = true)
                    SyntaxRole.Strong -> SyntaxStyle(text, bold = true)
                    SyntaxRole.Meta -> SyntaxStyle(violet, italic = true)
                    SyntaxRole.Invalid -> SyntaxStyle(fit(colors.danger))
                }
            }
            return DeskSyntax(plain = text, background = background, currentLine = currentLine, lineNumber = tertiary, styles = styles)
        }

        /**
         * Every surface text can lie on in the editor: the page and the current line, each alone and under a marked
         * or decorated line's tint; on each of those a search match or a range an app marks; and the selection
         * (which hides the current line's tint) on the page and on a marked line.
         */
        internal fun tintedSurfaces(colors: DeskColors, background: Color, currentLine: Color): List<Color> {
            val tones = listOf(colors.accentText, colors.danger, colors.warning, colors.success, colors.textTertiary)
            fun Color.tint(alpha: Float, under: Color) = copy(alpha = alpha).compositeOver(under)
            return buildList {
                for (page in listOf(background, currentLine)) {
                    for (under in listOf(page) + tones.map { it.tint(SyntaxTints.LINE, page) }) {
                        add(under)
                        add(colors.accentText.tint(SyntaxTints.MATCH, under))
                        for (tone in tones) add(tone.tint(SyntaxTints.RANGE, under))
                    }
                }
                for (under in listOf(background) + tones.map { it.tint(SyntaxTints.LINE, background) }) {
                    add(colors.accent.tint(SyntaxTints.SELECTION, under))
                }
            }
        }
    }
}

/**
 * How strongly the code editor's tints lie under its text, as the alpha of their tone. [DeskSyntax.from] makes
 * every colour readable (4.5:1) on each of them, alone and laid over one another as they can be (SyntaxContrastTest).
 */
internal object SyntaxTints {
    /** A marked or decorated line, across the gutter. */
    const val LINE = 0.08f

    /** A range an app marks with a background. */
    const val RANGE = 0.14f

    /** A search match, the current one too: that one is told apart by a frame, not by a stronger fill under its text. */
    const val MATCH = 0.14f

    /**
     * The selection: the theme's accent, lighter than in the kit's text fields (0.35), whose text is one colour;
     * under coloured tokens that tint would take them below 4.5:1.
     */
    const val SELECTION = 0.20f
}
