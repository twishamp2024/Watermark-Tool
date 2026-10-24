package com.animecliplabs.clipmark.core.layout

import com.animecliplabs.clipmark.EPS
import com.animecliplabs.clipmark.FakeMeasurer
import com.animecliplabs.clipmark.core.model.Anchor
import com.animecliplabs.clipmark.core.model.NormalizedPoint
import com.animecliplabs.clipmark.core.model.WatermarkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatermarkLayoutTest {

    private val style = WatermarkStyle() // @animecliplabs, bottom-right, 2.6 %, 3.5 % margins

    private fun place(w: Float, h: Float, s: WatermarkStyle = style, anchor: Anchor? = null) =
        requireNotNull(WatermarkLayout.place(w, h, s, FakeMeasurer, anchor))

    @Test
    fun `default is bottom right, exactly on the safe margins`() {
        val p = place(1080f, 1350f)
        val margin = 1080f * 0.035f
        assertEquals(1080f - margin, p.right, EPS)
        assertEquals(1350f - margin, p.bottom, EPS)
        assertEquals(Anchor.BOTTOM_RIGHT, p.anchor)
    }

    @Test
    fun `text size is a percentage of the short edge`() {
        assertEquals(1080f * 0.026f, place(1080f, 1350f).textSize, EPS)
        assertEquals(1080f * 0.026f, place(1920f, 1080f).textSize, EPS) // landscape: short edge is height
    }

    @Test
    fun `every quick position stays inside the safe area on every aspect ratio`() {
        val sizes = listOf(1080f to 1350f, 1080f to 1920f, 1920f to 1080f, 4000f to 3000f, 1000f to 1000f, 300f to 2400f)
        for ((w, h) in sizes) for (anchor in Anchor.entries) {
            val p = place(w, h, anchor = anchor)
            val safe = WatermarkLayout.safeArea(w, h, style)
            assertTrue("$anchor @ ${w}x$h left", p.left >= safe.left - EPS)
            assertTrue("$anchor @ ${w}x$h top", p.top >= safe.top - EPS)
            assertTrue("$anchor @ ${w}x$h right", p.right <= safe.right + EPS)
            assertTrue("$anchor @ ${w}x$h bottom", p.bottom <= safe.bottom + EPS)
        }
    }

    @Test
    fun `centre anchors are truly centred`() {
        val p = place(1200f, 800f, anchor = Anchor.CENTER)
        assertEquals(600f, p.centerX, EPS)
        assertEquals(400f, p.centerY, EPS)
        val top = place(1200f, 800f, anchor = Anchor.TOP_CENTER)
        assertEquals(600f, top.centerX, EPS)
    }

    @Test
    fun `placement scales exactly with resolution, so batches look identical`() {
        val small = place(1080f, 1350f)
        val large = place(4320f, 5400f)
        assertEquals(4f, large.textSize / small.textSize, 1e-3f)
        assertEquals(small.left / 1080f, large.left / 4320f, 1e-4f)
        assertEquals(small.top / 1350f, large.top / 5400f, 1e-4f)
        assertEquals(small.width / 1080f, large.width / 4320f, 1e-4f)
    }

    @Test
    fun `preview size and export size give the same relative placement`() {
        // Preview is drawn on a ~340 dp surface; export on the full image.
        val preview = place(1080f / 3.2f, 1350f / 3.2f)
        val export = place(1080f, 1350f)
        assertEquals(export.left / 1080f, preview.left / (1080f / 3.2f), 1e-4f)
        assertEquals(export.bottom / 1350f, preview.bottom / (1350f / 3.2f), 1e-4f)
    }

    @Test
    fun `dragged position outside the image is clamped into the safe area`() {
        val p = place(1000f, 1000f, style.copy(customCenter = NormalizedPoint(1.2f, -0.3f)))
        val safe = WatermarkLayout.safeArea(1000f, 1000f, style)
        assertEquals(safe.right, p.right, EPS)
        assertEquals(safe.top, p.top, EPS)
        assertNull(p.anchor)
    }

    @Test
    fun `custom centre is honoured when it fits`() {
        val p = place(1000f, 1000f, style.copy(customCenter = NormalizedPoint(0.5f, 0.3f)))
        assertEquals(500f, p.centerX, EPS)
        assertEquals(300f, p.centerY, EPS)
    }

    @Test
    fun `anchor override beats a custom centre`() {
        val p = place(1000f, 1000f, style.copy(customCenter = NormalizedPoint(0.5f, 0.5f)), anchor = Anchor.TOP_LEFT)
        val safe = WatermarkLayout.safeArea(1000f, 1000f, style)
        assertEquals(safe.left, p.left, EPS)
        assertEquals(safe.top, p.top, EPS)
    }

    @Test
    fun `text shrinks to fit a tiny image instead of spilling over the edge`() {
        val big = style.copy(sizePercent = 8f, text = "@animecliplabs — follow for more clips")
        val p = place(120f, 90f, big)
        val safe = WatermarkLayout.safeArea(120f, 90f, big)
        assertTrue(p.shrunkToFit)
        assertTrue(p.left >= safe.left - EPS && p.right <= safe.right + EPS)
    }

    @Test
    fun `normal images never shrink`() {
        assertFalse(place(1080f, 1350f).shrunkToFit)
    }

    @Test
    fun `toggling shadow or outline never moves the watermark`() {
        val a = place(1080f, 1350f, style.copy(shadow = false, outline = false))
        val b = place(1080f, 1350f, style.copy(shadow = true, outline = true))
        assertEquals(a, b)
    }

    @Test
    fun `out-of-range values are coerced to safe limits`() {
        val p = place(1000f, 1000f, style.copy(marginXPercent = 0f, marginYPercent = -5f, sizePercent = 99f))
        val safe = WatermarkLayout.safeArea(1000f, 1000f, style.copy(marginXPercent = 0f, marginYPercent = -5f))
        assertEquals(1000f * WatermarkStyle.MIN_MARGIN / 100f, safe.left, EPS) // never touches the edge
        assertEquals(1000f * WatermarkStyle.MAX_SIZE / 100f, p.textSize, EPS)
    }

    @Test
    fun `blank text or empty image draws nothing`() {
        assertNull(WatermarkLayout.place(1000f, 1000f, style.copy(text = "   "), FakeMeasurer))
        assertNull(WatermarkLayout.place(0f, 1000f, style, FakeMeasurer))
    }
}
