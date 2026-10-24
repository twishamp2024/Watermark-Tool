package com.animecliplabs.clipmark.core.analysis

import com.animecliplabs.clipmark.core.model.WatermarkStyle
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object ColorMath {
    /** WCAG relative luminance (0 = black, 1 = white) of an ARGB colour; alpha is ignored. */
    fun relativeLuminance(argb: Int): Float {
        fun channel(c: Int): Float {
            val s = c / 255f
            return if (s <= 0.03928f) s / 12.92f else ((s + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
        }
        val r = channel((argb shr 16) and 0xFF)
        val g = channel((argb shr 8) and 0xFF)
        val b = channel(argb and 0xFF)
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    /** WCAG contrast ratio, 1..21. */
    fun contrastRatio(luminanceA: Float, luminanceB: Float): Float =
        (max(luminanceA, luminanceB) + 0.05f) / (min(luminanceA, luminanceB) + 0.05f)
}

/** How the watermark glyphs are drawn once the background is known. Never touches the photo. */
data class Readability(
    val shadow: Boolean,
    val outline: Boolean,
    /** Colour of the shadow/outline: the opposite of the text colour. */
    val effectColor: Int,
    /** True when Contrast Assist added an effect the user did not ask for. */
    val assisted: Boolean,
)

object ReadabilityResolver {
    /** Below this ratio text starts to get hard to read; Contrast Assist adds a soft shadow. */
    const val SHADOW_BELOW = 3.0f
    /** Below this the text nearly vanishes; add an outline as well. */
    const val OUTLINE_BELOW = 1.6f

    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    /**
     * @param backgroundLuminance mean relative luminance under the watermark, or null if unknown
     *   (in which case only the user's explicit choices apply).
     */
    fun resolve(style: WatermarkStyle, backgroundLuminance: Float?): Readability {
        val textLum = ColorMath.relativeLuminance(style.colorArgb)
        val effectColor = if (textLum > 0.4f) BLACK else WHITE
        var shadow = style.shadow
        var outline = style.outline
        var assisted = false
        if (style.contrastAssist && backgroundLuminance != null) {
            val ratio = ColorMath.contrastRatio(textLum, backgroundLuminance)
            if (ratio < SHADOW_BELOW && !shadow) {
                shadow = true; assisted = true
            }
            if (ratio < OUTLINE_BELOW && !outline) {
                outline = true; assisted = true
            }
        }
        return Readability(shadow, outline, effectColor, assisted)
    }
}
