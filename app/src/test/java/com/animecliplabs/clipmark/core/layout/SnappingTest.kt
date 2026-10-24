package com.animecliplabs.clipmark.core.layout

import com.animecliplabs.clipmark.EPS
import com.animecliplabs.clipmark.FakeMeasurer
import com.animecliplabs.clipmark.core.model.Anchor
import com.animecliplabs.clipmark.core.model.WatermarkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SnappingTest {
    private val w = 1000f
    private val h = 1250f
    private val style = WatermarkStyle(anchor = Anchor.BOTTOM_RIGHT, smartCorner = true)

    private fun centerOf(anchor: Anchor) = requireNotNull(WatermarkLayout.place(w, h, style, FakeMeasurer, anchor))

    @Test
    fun `dropping near a quick position snaps onto it`() {
        val target = centerOf(Anchor.TOP_LEFT)
        val r = Snapping.drag(style, w, h, FakeMeasurer, target.centerX + 8f, target.centerY - 6f, threshold = 20f)
        assertEquals(Anchor.TOP_LEFT, r.snappedAnchor)
        assertEquals(Anchor.TOP_LEFT, r.style.anchor)
        assertNull(r.style.customCenter)
    }

    @Test
    fun `dragging turns Smart Corner off - the user chose a spot`() {
        val r = Snapping.drag(style, w, h, FakeMeasurer, 300f, 400f, threshold = 20f)
        assertFalse(r.style.smartCorner)
    }

    @Test
    fun `free drag near the vertical centre line snaps horizontally only`() {
        val r = Snapping.drag(style, w, h, FakeMeasurer, w / 2 + 10f, 400f, threshold = 20f)
        assertNull(r.snappedAnchor)
        assertNotNull(r.guideX)
        assertNull(r.guideY)
        assertEquals(0.5f, requireNotNull(r.style.customCenter).x, 1e-4f)
        assertEquals(400f / h, requireNotNull(r.style.customCenter).y, 1e-4f)
    }

    @Test
    fun `free position is stored where the safe area actually allows it`() {
        val r = Snapping.drag(style, w, h, FakeMeasurer, w - 1f, 500f, threshold = 5f)
        val c = requireNotNull(r.style.customCenter)
        val placed = requireNotNull(WatermarkLayout.place(w, h, r.style, FakeMeasurer))
        assertEquals(placed.centerX / w, c.x, 1e-4f)
        assertEquals(WatermarkLayout.safeArea(w, h, style).right, placed.right, EPS)
    }
}
