package com.animecliplabs.clipmark.core.model

import kotlinx.serialization.Serializable

/** The nine quick positions. [col]/[row] are 0..2, left→right and top→bottom. */
@Serializable
enum class Anchor(val col: Int, val row: Int) {
    TOP_LEFT(0, 0), TOP_CENTER(1, 0), TOP_RIGHT(2, 0),
    CENTER_LEFT(0, 1), CENTER(1, 1), CENTER_RIGHT(2, 1),
    BOTTOM_LEFT(0, 2), BOTTOM_CENTER(1, 2), BOTTOM_RIGHT(2, 2);

    val isCorner: Boolean get() = col != 1 && row != 1

    companion object {
        val corners = listOf(TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT)
        fun at(col: Int, row: Int): Anchor = entries.first { it.col == col && it.row == row }
    }
}

/**
 * Fonts are Android system families: zero APK weight, and the preview and the export resolve
 * to exactly the same Typeface, which keeps the preview truthful.
 */
@Serializable
enum class WatermarkFont(val familyName: String) {
    CLEAN("sans-serif"),
    MEDIUM("sans-serif-medium"),
    CONDENSED("sans-serif-condensed"),
    SERIF("serif"),
    MONO("monospace"),
    SCRIPT("cursive"),
    CASUAL("casual"),
}

/** A point expressed as a fraction of the image (0..1 on both axes). */
@Serializable
data class NormalizedPoint(val x: Float, val y: Float)

/**
 * Everything needed to render a watermark, independent of any particular image.
 *
 * Size and margins are percentages of the image's *short edge*, so the same preset produces
 * visually identical branding on a 1080×1350 post and on a 4K screenshot.
 */
@Serializable
data class WatermarkStyle(
    val text: String = DEFAULT_TEXT,
    val font: WatermarkFont = WatermarkFont.MEDIUM,
    val sizePercent: Float = SizeLevel.SMALL.percent,
    val bold: Boolean = false,
    /** Opaque ARGB; transparency is controlled only by [opacity]. */
    val colorArgb: Int = WHITE,
    val opacity: Float = 0.8f,
    val anchor: Anchor = Anchor.BOTTOM_RIGHT,
    /** When set, the user dragged the watermark; this is its centre. Overrides [anchor]. */
    val customCenter: NormalizedPoint? = null,
    val marginXPercent: Float = DEFAULT_MARGIN,
    val marginYPercent: Float = DEFAULT_MARGIN,
    val shadow: Boolean = true,
    val outline: Boolean = false,
    /** Adds a shadow/outline automatically when the background makes the text hard to read. */
    val contrastAssist: Boolean = true,
    /** Picks the calmest corner per image instead of always using [anchor]. */
    val smartCorner: Boolean = false,
) {
    val sizeLevel: SizeLevel get() = SizeLevel.nearest(sizePercent)

    companion object {
        const val DEFAULT_TEXT = "@animecliplabs"
        const val WHITE = 0xFFFFFFFF.toInt()
        const val DEFAULT_MARGIN = 3.5f
        const val MIN_SIZE = 1.2f
        const val MAX_SIZE = 8f
        const val MIN_MARGIN = 1f
        const val MAX_MARGIN = 15f
        const val MIN_OPACITY = 0.15f
    }
}

enum class SizeLevel(val percent: Float) {
    EXTRA_SMALL(1.8f), SMALL(2.6f), MEDIUM(3.6f), LARGE(5f);

    companion object {
        fun nearest(percent: Float): SizeLevel = entries.minBy { kotlin.math.abs(it.percent - percent) }
    }
}
