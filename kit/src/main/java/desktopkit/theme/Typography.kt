package desktopkit.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import desktopkit.R

/**
 * Font families of the interface: [sans] for text, [mono] for timecodes, formats and key caps.
 * The default is IBM Plex (SIL OFL 1.1, bundled); pass your own families to change the look.
 */
@Immutable
data class DeskFonts(
    val sans: FontFamily,
    val mono: FontFamily,
    /** Display, headline and title styles; the same as [sans] unless an app wants, say, a serif for headings. */
    val heading: FontFamily = sans,
) {
    companion object {
        val Plex = DeskFonts(
            sans = FontFamily(
                Font(R.font.dk_ibm_plex_sans_regular, FontWeight.Normal),
                Font(R.font.dk_ibm_plex_sans_medium, FontWeight.Medium),
                Font(R.font.dk_ibm_plex_sans_semibold, FontWeight.SemiBold),
            ),
            mono = FontFamily(
                Font(R.font.dk_ibm_plex_mono_regular, FontWeight.Normal),
                Font(R.font.dk_ibm_plex_mono_medium, FontWeight.Medium),
            ),
        )

        /** The device's system fonts (Roboto / Samsung One UI font). */
        val System = DeskFonts(sans = FontFamily.Default, mono = FontFamily.Monospace)
    }
}

/**
 * Text styles. Two sets: pointer (DeX, mouse) is denser, touch is larger. Nothing is below 11 sp.
 * Apps change the scale through the theme's `type` hook, e.g. one sp larger with a pointer only:
 * `DesktopKitRoot(prefs, type = { mode, fonts -> DeskType.forMode(mode, fonts, pointerStep = 1f) })`.
 */
@Immutable
data class DeskType(
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val label: TextStyle,
    val labelSmall: TextStyle,
    val caption: TextStyle,
    val mono: TextStyle,
) {
    companion object {
        /**
         * The dense set for mouse and touchpad. [step] adds that many sp to every style (line
         * heights grow in proportion), e.g. `pointer(fonts, step = 1f)` for a slightly larger desktop.
         */
        fun pointer(fonts: DeskFonts = DeskFonts.Plex, step: Float = 0f) =
            build(fonts, step, display = 22, headline = 18, title = 16, body = 13, label = 12.5f, labelSmall = 11.5f, caption = 11, mono = 11)

        /** The larger set for fingers. [step] works as in [pointer]. */
        fun touch(fonts: DeskFonts = DeskFonts.Plex, step: Float = 0f) =
            build(fonts, step, display = 26, headline = 20, title = 17, body = 15, label = 14, labelSmall = 13, caption = 12, mono = 12)

        val Pointer = pointer()
        val Touch = touch()

        /**
         * The set for [mode]; [pointerStep] and [touchStep] add sp to every style of that set only.
         * The theme's `type` hook uses this: `type = { mode, fonts -> DeskType.forMode(mode, fonts, pointerStep = 1f) }`.
         */
        fun forMode(mode: InputMode, fonts: DeskFonts = DeskFonts.Plex, pointerStep: Float = 0f, touchStep: Float = 0f) =
            if (mode == InputMode.Pointer) pointer(fonts, pointerStep) else touch(fonts, touchStep)

        private fun build(fonts: DeskFonts, step: Float, display: Number, headline: Number, title: Number, body: Number, label: Number,
                          labelSmall: Number, caption: Number, mono: Number): DeskType {
            // the step is added to the size first, so the line height keeps its ratio to the size
            fun sans(size: Number, weight: FontWeight, ratio: Float, family: FontFamily = fonts.sans): TextStyle {
                val value = size.toFloat() + step
                return TextStyle(
                    fontFamily = family,
                    fontWeight = weight,
                    fontSize = value.sp,
                    lineHeight = (value * ratio).sp,
                    letterSpacing = TextUnit.Unspecified,
                )
            }
            val monoSize = mono.toFloat() + step
            return DeskType(
                display = sans(display, FontWeight.SemiBold, ratio = 1.25f, family = fonts.heading),
                headline = sans(headline, FontWeight.SemiBold, ratio = 1.3f, family = fonts.heading),
                title = sans(title, FontWeight.SemiBold, ratio = 1.35f, family = fonts.heading),
                body = sans(body, FontWeight.Normal, ratio = 1.4f),
                bodyStrong = sans(body, FontWeight.Medium, ratio = 1.4f),
                label = sans(label, FontWeight.Medium, ratio = 1.3f),
                labelSmall = sans(labelSmall, FontWeight.Normal, ratio = 1.3f),
                caption = sans(caption, FontWeight.Normal, ratio = 1.3f),
                mono = TextStyle(fontFamily = fonts.mono, fontWeight = FontWeight.Normal, fontSize = monoSize.sp,
                    lineHeight = (monoSize * 1.3f).sp),
            )
        }
    }
}

/**
 * The same styles [step] sp larger (smaller when negative); line heights keep their ratio to the
 * size. The shell uses it to set text one sp larger on tablet and DeX windows
 * (`DesktopShell(largeWindowTextStep = 1f)`), whatever the theme's `type` hook built.
 */
fun DeskType.grow(step: Float): DeskType {
    if (step == 0f) return this
    fun TextStyle.grown(): TextStyle {
        val size = fontSize.value
        val ratio = if (lineHeight.isSp && size > 0f) lineHeight.value / size else Float.NaN
        val next = size + step
        return copy(fontSize = next.sp, lineHeight = if (ratio.isNaN()) lineHeight else (next * ratio).sp)
    }
    return DeskType(
        display = display.grown(),
        headline = headline.grown(),
        title = title.grown(),
        body = body.grown(),
        bodyStrong = bodyStrong.grown(),
        label = label.grown(),
        labelSmall = labelSmall.grown(),
        caption = caption.grown(),
        mono = mono.grown(),
    )
}
