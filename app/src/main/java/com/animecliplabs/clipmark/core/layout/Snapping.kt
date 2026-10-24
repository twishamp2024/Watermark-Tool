package com.animecliplabs.clipmark.core.layout

import com.animecliplabs.clipmark.core.model.Anchor
import com.animecliplabs.clipmark.core.model.NormalizedPoint
import com.animecliplabs.clipmark.core.model.WatermarkStyle
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Drag-to-position with magnetic snapping. Snapping to one of the nine anchors is preferred —
 * an anchored watermark lands in the *same* spot on every aspect ratio, which is what keeps a
 * batch consistent. Free positions still snap to the centre lines.
 */
object Snapping {

    data class Result(
        val style: WatermarkStyle,
        /** Non-null when the drag snapped onto a quick position. */
        val snappedAnchor: Anchor?,
        /** Guide lines to draw (surface coordinates), or null. */
        val guideX: Float?,
        val guideY: Float?,
    )

    /**
     * @param centerX proposed centre of the watermark box, in surface pixels.
     * @param threshold snap distance in surface pixels (callers convert from dp).
     */
    fun drag(
        style: WatermarkStyle,
        surfaceWidth: Float,
        surfaceHeight: Float,
        measurer: TextMeasurer,
        centerX: Float,
        centerY: Float,
        threshold: Float,
    ): Result {
        val nearest = Anchor.entries
            .mapNotNull { a -> WatermarkLayout.place(surfaceWidth, surfaceHeight, style, measurer, a)?.let { a to it } }
            .minByOrNull { (_, p) -> hypot(p.centerX - centerX, p.centerY - centerY) }

        if (nearest != null) {
            val (anchor, p) = nearest
            if (hypot(p.centerX - centerX, p.centerY - centerY) <= threshold) {
                return Result(
                    style = style.copy(anchor = anchor, customCenter = null, smartCorner = false),
                    snappedAnchor = anchor,
                    guideX = p.centerX,
                    guideY = p.centerY,
                )
            }
        }

        var cx = centerX
        var cy = centerY
        var guideX: Float? = null
        var guideY: Float? = null
        if (abs(cx - surfaceWidth / 2f) <= threshold) {
            cx = surfaceWidth / 2f; guideX = cx
        }
        if (abs(cy - surfaceHeight / 2f) <= threshold) {
            cy = surfaceHeight / 2f; guideY = cy
        }

        val free = style.copy(customCenter = NormalizedPoint(cx / surfaceWidth, cy / surfaceHeight), smartCorner = false)
        // Store the centre the safe area actually allows, so what is saved is what is shown.
        val clamped = WatermarkLayout.place(surfaceWidth, surfaceHeight, free, measurer)
            ?: return Result(free, null, guideX, guideY)
        return Result(
            style = free.copy(customCenter = NormalizedPoint(clamped.centerX / surfaceWidth, clamped.centerY / surfaceHeight)),
            snappedAnchor = null,
            guideX = guideX,
            guideY = guideY,
        )
    }
}
