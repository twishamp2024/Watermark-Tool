package com.animecliplabs.clipmark.image

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.animecliplabs.clipmark.core.analysis.Readability
import com.animecliplabs.clipmark.core.layout.Placement
import com.animecliplabs.clipmark.core.layout.TextMeasurer
import com.animecliplabs.clipmark.core.layout.TextMetrics
import com.animecliplabs.clipmark.core.layout.WatermarkLayout
import com.animecliplabs.clipmark.core.model.Anchor
import com.animecliplabs.clipmark.core.model.WatermarkFont
import com.animecliplabs.clipmark.core.model.WatermarkStyle
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * The single drawing routine used by BOTH the live preview (Compose canvas, display scale) and
 * the export (full-resolution bitmap). Because layout is relative to the surface size, the
 * preview is an exact scaled rendition of the saved file.
 */
class WatermarkRenderer {

    private val typefaces = ConcurrentHashMap<Pair<WatermarkFont, Boolean>, Typeface>()

    fun typeface(font: WatermarkFont, bold: Boolean): Typeface = typefaces.getOrPut(font to bold) {
        Typeface.create(Typeface.create(font.familyName, Typeface.NORMAL), if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun textPaint(style: WatermarkStyle, textSize: Float) =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
            typeface = typeface(style.font, style.bold)
            this.textSize = textSize
        }

    fun measurer(style: WatermarkStyle): TextMeasurer = TextMeasurer { text, size ->
        val paint = textPaint(style, size)
        val fm = paint.fontMetrics
        TextMetrics(width = paint.measureText(text), ascent = -fm.ascent, descent = fm.descent)
    }

    fun place(width: Float, height: Float, style: WatermarkStyle, anchorOverride: Anchor? = null): Placement? =
        WatermarkLayout.place(width, height, style, measurer(style), anchorOverride)

    /**
     * Draws the watermark into an isolated layer so opacity applies to the watermark as a whole
     * (fill, outline and shadow together) instead of stacking translucent strokes.
     * The layer is bounded to the watermark, so memory stays tiny even on 50 MP images.
     */
    fun draw(canvas: Canvas, placement: Placement, style: WatermarkStyle, readability: Readability) {
        val ts = placement.textSize
        val bleed = ts * 0.4f
        val layerPaint = Paint().apply { alpha = (style.opacity.coerceIn(0f, 1f) * 255).roundToInt() }
        val checkpoint = canvas.saveLayer(
            placement.left - bleed, placement.top - bleed,
            placement.right + bleed, placement.bottom + bleed,
            layerPaint,
        )

        if (readability.outline) {
            val stroke = textPaint(style, ts).apply {
                this.style = Paint.Style.STROKE
                strokeWidth = ts * 0.09f
                strokeJoin = Paint.Join.ROUND
                color = withAlpha(readability.effectColor, 0.85f)
            }
            canvas.drawText(style.text, placement.textX, placement.baseline, stroke)
        }

        val fill = textPaint(style, ts).apply {
            color = style.colorArgb or OPAQUE
            if (readability.shadow) {
                setShadowLayer(ts * 0.12f, 0f, ts * 0.04f, withAlpha(readability.effectColor, 0.55f))
            }
        }
        canvas.drawText(style.text, placement.textX, placement.baseline, fill)
        canvas.restoreToCount(checkpoint)
    }

    private fun withAlpha(color: Int, alpha: Float): Int =
        ((alpha * 255).roundToInt() shl 24) or (color and 0x00FFFFFF)

    private companion object {
        const val OPAQUE = 0xFF000000.toInt()
    }
}
