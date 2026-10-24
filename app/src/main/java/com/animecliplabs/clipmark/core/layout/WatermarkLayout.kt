package com.animecliplabs.clipmark.core.layout

import com.animecliplabs.clipmark.core.model.Anchor
import com.animecliplabs.clipmark.core.model.WatermarkStyle
import kotlin.math.min

/** Font metrics for one line of text at a given size. [ascent] and [descent] are positive distances. */
data class TextMetrics(val width: Float, val ascent: Float, val descent: Float)

/** Supplied by the renderer so the layout math stays platform-free and testable. */
fun interface TextMeasurer {
    fun measure(text: String, textSize: Float): TextMetrics
}

/**
 * Where the watermark box sits, in the coordinate space of whatever surface it is drawn on
 * (full-resolution bitmap for export, on-screen image rect for preview).
 */
data class Placement(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val textSize: Float,
    val padding: Float,
    val ascent: Float,
    /** The anchor used, or null for a dragged (custom) position. */
    val anchor: Anchor?,
    /** True when the text had to shrink to fit a very small image. */
    val shrunkToFit: Boolean,
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f
    val textX: Float get() = left + padding
    val baseline: Float get() = top + padding + ascent
}

/** The inner rectangle the watermark box must stay inside. */
data class SafeArea(val left: Float, val top: Float, val right: Float, val bottom: Float)

object WatermarkLayout {
    /**
     * Breathing room around the glyphs, as a fraction of text size. It is part of the box so a
     * shadow or outline never bleeds past the safe margin, and it is constant so toggling
     * effects never makes the watermark jump.
     */
    const val PADDING_RATIO = 0.2f

    fun safeArea(imageWidth: Float, imageHeight: Float, style: WatermarkStyle): SafeArea {
        val short = min(imageWidth, imageHeight)
        val mx = marginPx(style.marginXPercent, short)
        val my = marginPx(style.marginYPercent, short)
        return SafeArea(mx, my, imageWidth - mx, imageHeight - my)
    }

    /**
     * Computes the watermark box. Returns null when there is nothing to draw.
     *
     * @param anchorOverride forces an anchor (Smart Corner, snapping), ignoring any custom centre.
     */
    fun place(
        imageWidth: Float,
        imageHeight: Float,
        style: WatermarkStyle,
        measurer: TextMeasurer,
        anchorOverride: Anchor? = null,
    ): Placement? {
        if (style.text.isBlank() || imageWidth <= 0f || imageHeight <= 0f) return null

        val short = min(imageWidth, imageHeight)
        val safe = safeArea(imageWidth, imageHeight, style)
        val availableW = (safe.right - safe.left).coerceAtLeast(1f)
        val availableH = (safe.bottom - safe.top).coerceAtLeast(1f)

        var textSize = (style.sizePercent.coerceIn(WatermarkStyle.MIN_SIZE, WatermarkStyle.MAX_SIZE) / 100f * short)
            .coerceAtLeast(1f)
        var box = measureBox(style.text, textSize, measurer)
        var shrunk = false
        if (box.width > availableW || box.height > availableH) {
            textSize *= min(availableW / box.width, availableH / box.height)
            box = measureBox(style.text, textSize, measurer)
            shrunk = true
        }

        val anchor = anchorOverride ?: style.anchor.takeIf { style.customCenter == null }
        val rawLeft: Float
        val rawTop: Float
        if (anchor != null) {
            rawLeft = when (anchor.col) {
                0 -> safe.left
                1 -> (imageWidth - box.width) / 2f
                else -> safe.right - box.width
            }
            rawTop = when (anchor.row) {
                0 -> safe.top
                1 -> (imageHeight - box.height) / 2f
                else -> safe.bottom - box.height
            }
        } else {
            val center = requireNotNull(style.customCenter)
            rawLeft = center.x * imageWidth - box.width / 2f
            rawTop = center.y * imageHeight - box.height / 2f
        }

        return Placement(
            left = clampInto(rawLeft, safe.left, safe.right - box.width),
            top = clampInto(rawTop, safe.top, safe.bottom - box.height),
            width = box.width,
            height = box.height,
            textSize = textSize,
            padding = box.padding,
            ascent = box.ascent,
            anchor = anchor,
            shrunkToFit = shrunk,
        )
    }

    private data class Box(val width: Float, val height: Float, val padding: Float, val ascent: Float)

    private fun measureBox(text: String, textSize: Float, measurer: TextMeasurer): Box {
        val m = measurer.measure(text, textSize)
        val pad = textSize * PADDING_RATIO
        return Box(m.width + 2 * pad, m.ascent + m.descent + 2 * pad, pad, m.ascent)
    }

    private fun marginPx(percent: Float, shortEdge: Float): Float =
        percent.coerceIn(WatermarkStyle.MIN_MARGIN, WatermarkStyle.MAX_MARGIN) / 100f * shortEdge

    /** Clamp that degrades gracefully to "centred" if the box is wider than the allowed range. */
    private fun clampInto(value: Float, lo: Float, hi: Float): Float =
        if (hi < lo) (lo + hi) / 2f else value.coerceIn(lo, hi)
}
