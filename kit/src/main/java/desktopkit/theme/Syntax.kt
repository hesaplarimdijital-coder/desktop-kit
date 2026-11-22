package desktopkit.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

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
 * and functions, violet for types, tags and directives). Every role reaches 4.5:1 on [background]
 * and on [currentLine] in the light, dark and OLED themes with any accent (SyntaxContrastTest).
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
            // readable on the page and on the current line, whatever a colour hook or a custom accent made of them
            fun fit(color: Color): Color = ensureContrast(ensureContrast(color, background, lighten = dark), currentLine, lighten = dark)
            val green = fit(if (dark) Color(0xFF8FD3A8) else Color(0xFF1B6E47))
            val amber = fit(if (dark) Color(0xFFE2BB73) else Color(0xFF85560F))
            val teal = fit(if (dark) Color(0xFF7CCBD6) else Color(0xFF0B6672))
            val violet = fit(if (dark) Color(0xFFC3A9F4) else Color(0xFF6541B8))
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
    }
}
