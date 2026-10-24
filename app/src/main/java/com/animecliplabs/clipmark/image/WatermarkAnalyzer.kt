package com.animecliplabs.clipmark.image

import android.graphics.Bitmap
import com.animecliplabs.clipmark.core.analysis.ColorMath
import com.animecliplabs.clipmark.core.analysis.Readability
import com.animecliplabs.clipmark.core.analysis.ReadabilityResolver
import com.animecliplabs.clipmark.core.analysis.RegionStats
import com.animecliplabs.clipmark.core.analysis.SmartCorner
import com.animecliplabs.clipmark.core.layout.Placement
import com.animecliplabs.clipmark.core.model.Anchor
import com.animecliplabs.clipmark.core.model.WatermarkStyle

/** Everything decided about one image before drawing: where, and how readable. */
data class WatermarkDecision(
    /** Anchor chosen by Smart Corner, or null when the style's own position applies. */
    val smartAnchor: Anchor?,
    val readability: Readability,
    val placement: Placement,
    /** Mean luminance under the watermark (only sampled when Contrast Assist is on). */
    val backgroundLuminance: Float?,
)

/**
 * Reads (never writes) a bitmap to support Smart Corner and Contrast Assist. Works on any
 * resolution: the preview bitmap in the editor, the full-resolution one during export.
 */
class WatermarkAnalyzer(private val renderer: WatermarkRenderer) {

    fun decide(bitmap: Bitmap, style: WatermarkStyle, resolvedAnchor: Anchor? = null): WatermarkDecision? {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val smartAnchor = resolvedAnchor ?: if (style.smartCorner && style.customCenter == null) {
            val textLum = ColorMath.relativeLuminance(style.colorArgb)
            val stats = SmartCorner.candidatesFor(style.anchor).mapNotNull { anchor ->
                renderer.place(w, h, style, anchor)?.let { anchor to regionStats(bitmap, it) }
            }.toMap()
            SmartCorner.choose(stats, style.anchor, textLum)
        } else {
            null
        }
        val placement = renderer.place(w, h, style, smartAnchor) ?: return null
        val background = if (style.contrastAssist) regionStats(bitmap, placement).meanLuminance else null
        return WatermarkDecision(smartAnchor, ReadabilityResolver.resolve(style, background), placement, background)
    }

    /** Samples a coarse grid inside the watermark box. Cheap: a few hundred pixel reads. */
    fun regionStats(bitmap: Bitmap, placement: Placement, cols: Int = 24, rows: Int = 8): RegionStats {
        val maxX = bitmap.width - 1
        val maxY = bitmap.height - 1
        val samples = FloatArray(cols * rows)
        for (r in 0 until rows) {
            val y = (placement.top + (r + 0.5f) * placement.height / rows).toInt().coerceIn(0, maxY)
            for (c in 0 until cols) {
                val x = (placement.left + (c + 0.5f) * placement.width / cols).toInt().coerceIn(0, maxX)
                samples[r * cols + c] = ColorMath.relativeLuminance(bitmap.getPixel(x, y))
            }
        }
        return RegionStats.from(samples, cols, rows)
    }
}
