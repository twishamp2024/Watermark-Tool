package com.animecliplabs.clipmark.core.analysis

import com.animecliplabs.clipmark.core.model.Anchor
import kotlin.math.abs
import kotlin.math.sqrt

/** Luminance statistics of the area a watermark would cover. All values are 0..1. */
data class RegionStats(val meanLuminance: Float, val stdDev: Float, val edgeEnergy: Float) {
    companion object {
        /**
         * @param samples relative luminance values laid out row-major in a [cols]×[rows] grid.
         */
        fun from(samples: FloatArray, cols: Int, rows: Int): RegionStats {
            require(cols > 0 && rows > 0 && samples.size == cols * rows) { "grid size mismatch" }
            val mean = samples.average().toFloat()
            var variance = 0.0
            for (s in samples) variance += (s - mean) * (s - mean)
            val std = sqrt(variance / samples.size).toFloat()

            var edgeSum = 0f
            var edgeCount = 0
            for (r in 0 until rows) for (c in 0 until cols) {
                val v = samples[r * cols + c]
                if (c + 1 < cols) { edgeSum += abs(v - samples[r * cols + c + 1]); edgeCount++ }
                if (r + 1 < rows) { edgeSum += abs(v - samples[(r + 1) * cols + c]); edgeCount++ }
            }
            return RegionStats(mean, std, if (edgeCount == 0) 0f else edgeSum / edgeCount)
        }
    }
}

/**
 * Chooses the least intrusive position: the calmest area (low texture, few edges — so the
 * watermark is less likely to sit on a face or detailed art) where the text colour also reads
 * well. Only the position changes; the photo is only ever *read*.
 */
object SmartCorner {
    /** A candidate must beat the user's preferred corner by this much to win — avoids flip-flopping. */
    const val PREFERENCE_MARGIN = 0.06f
    const val READABLE_CONTRAST = 4.5f

    fun score(stats: RegionStats, textLuminance: Float): Float {
        val busyness = (stats.stdDev * 2.2f + stats.edgeEnergy * 4f).coerceIn(0f, 1f)
        val contrast = ColorMath.contrastRatio(textLuminance, stats.meanLuminance)
        // Saturates at WCAG AA (4.5:1): beyond "clearly readable", more contrast is no reason to move.
        val contrastScore = ((contrast - 1f) / (READABLE_CONTRAST - 1f)).coerceIn(0f, 1f)
        return busyness * 0.65f + (1f - contrastScore) * 0.35f // lower is better
    }

    fun candidatesFor(preferred: Anchor): List<Anchor> =
        if (preferred.isCorner) Anchor.corners else Anchor.corners + preferred

    fun choose(stats: Map<Anchor, RegionStats>, preferred: Anchor, textLuminance: Float): Anchor {
        if (stats.isEmpty()) return preferred
        val scored = stats.mapValues { (_, s) -> score(s, textLuminance) }
        val best = scored.minBy { it.value }
        val preferredScore = scored[preferred] ?: return best.key
        return if (preferredScore - best.value < PREFERENCE_MARGIN) preferred else best.key
    }
}
