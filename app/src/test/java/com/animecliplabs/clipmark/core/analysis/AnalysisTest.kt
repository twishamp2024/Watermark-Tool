package com.animecliplabs.clipmark.core.analysis

import com.animecliplabs.clipmark.core.model.Anchor
import com.animecliplabs.clipmark.core.model.WatermarkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AnalysisTest {

    private fun flat(value: Float) = RegionStats.from(FloatArray(24 * 8) { value }, 24, 8)
    private fun noisy(seed: Int) = Random(seed).let { r -> RegionStats.from(FloatArray(24 * 8) { r.nextFloat() }, 24, 8) }

    @Test
    fun `luminance and contrast follow WCAG`() {
        assertEquals(1f, ColorMath.relativeLuminance(0xFFFFFFFF.toInt()), 1e-4f)
        assertEquals(0f, ColorMath.relativeLuminance(0xFF000000.toInt()), 1e-4f)
        assertEquals(21f, ColorMath.contrastRatio(1f, 0f), 1e-3f)
        assertEquals(1f, ColorMath.contrastRatio(0.4f, 0.4f), 1e-4f)
    }

    @Test
    fun `region stats separate calm from busy areas`() {
        val calm = flat(0.2f)
        val busy = noisy(1)
        assertEquals(0f, calm.stdDev, 1e-6f)
        assertEquals(0f, calm.edgeEnergy, 1e-6f)
        assertTrue(busy.stdDev > 0.2f)
        assertTrue(busy.edgeEnergy > 0.2f)
    }

    @Test
    fun `smart corner moves off a busy corner to a calm one`() {
        val stats = mapOf(
            Anchor.BOTTOM_RIGHT to noisy(1), // detailed art — e.g. a character
            Anchor.BOTTOM_LEFT to flat(0.12f), // dark calm ground: white text reads well
            Anchor.TOP_LEFT to noisy(2),
            Anchor.TOP_RIGHT to noisy(3),
        )
        assertEquals(Anchor.BOTTOM_LEFT, SmartCorner.choose(stats, Anchor.BOTTOM_RIGHT, textLuminance = 1f))
    }

    @Test
    fun `smart corner keeps the preferred corner unless another is clearly better`() {
        val stats = mapOf(
            Anchor.BOTTOM_RIGHT to flat(0.15f),
            Anchor.BOTTOM_LEFT to flat(0.10f),
            Anchor.TOP_LEFT to flat(0.14f),
            Anchor.TOP_RIGHT to flat(0.13f),
        )
        assertEquals(Anchor.BOTTOM_RIGHT, SmartCorner.choose(stats, Anchor.BOTTOM_RIGHT, textLuminance = 1f))
    }

    @Test
    fun `smart corner considers text contrast - white text avoids white sky`() {
        val stats = mapOf(
            Anchor.TOP_RIGHT to flat(0.95f), // bright sky
            Anchor.BOTTOM_RIGHT to flat(0.05f), // dark ground
            Anchor.TOP_LEFT to flat(0.95f),
            Anchor.BOTTOM_LEFT to flat(0.06f),
        )
        assertEquals(Anchor.BOTTOM_RIGHT, SmartCorner.choose(stats, Anchor.TOP_RIGHT, textLuminance = 1f))
    }

    @Test
    fun `non-corner preference is still a candidate`() {
        assertTrue(Anchor.BOTTOM_CENTER in SmartCorner.candidatesFor(Anchor.BOTTOM_CENTER))
        assertEquals(4, SmartCorner.candidatesFor(Anchor.TOP_LEFT).size)
    }

    @Test
    fun `contrast assist adds a dark shadow to white text on a bright area`() {
        val style = WatermarkStyle(shadow = false, outline = false, contrastAssist = true)
        val r = ReadabilityResolver.resolve(style, backgroundLuminance = 0.85f)
        assertTrue(r.shadow)
        assertTrue(r.assisted)
        assertEquals(0xFF000000.toInt(), r.effectColor)
    }

    @Test
    fun `contrast assist adds an outline when text nearly vanishes`() {
        val style = WatermarkStyle(shadow = false, outline = false, contrastAssist = true)
        assertTrue(ReadabilityResolver.resolve(style, backgroundLuminance = 0.98f).outline)
    }

    @Test
    fun `dark text gets a light effect - the reverse case`() {
        val style = WatermarkStyle(colorArgb = 0xFF111111.toInt(), shadow = false, contrastAssist = true)
        val r = ReadabilityResolver.resolve(style, backgroundLuminance = 0.02f)
        assertTrue(r.shadow)
        assertEquals(0xFFFFFFFF.toInt(), r.effectColor)
    }

    @Test
    fun `contrast assist stays out of the way when text is already readable`() {
        val style = WatermarkStyle(shadow = false, contrastAssist = true)
        val r = ReadabilityResolver.resolve(style, backgroundLuminance = 0.03f)
        assertFalse(r.shadow)
        assertFalse(r.assisted)
    }

    @Test
    fun `assist off means only the user's own choices apply`() {
        val style = WatermarkStyle(shadow = false, outline = false, contrastAssist = false)
        val r = ReadabilityResolver.resolve(style, backgroundLuminance = 0.99f)
        assertFalse(r.shadow)
        assertFalse(r.outline)
    }
}
