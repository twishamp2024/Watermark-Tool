package com.animecliplabs.clipmark.data

import com.animecliplabs.clipmark.core.batch.BatchItem
import com.animecliplabs.clipmark.core.batch.BatchJob
import com.animecliplabs.clipmark.core.model.Anchor
import com.animecliplabs.clipmark.core.model.NormalizedPoint
import com.animecliplabs.clipmark.core.model.OutputFormatPreference
import com.animecliplabs.clipmark.core.model.OutputSettings
import com.animecliplabs.clipmark.core.model.Preset
import com.animecliplabs.clipmark.core.model.WatermarkFont
import com.animecliplabs.clipmark.core.model.WatermarkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetCodecTest {

    @Test
    fun `the default preset is exactly the brief`() {
        val s = Preset.Default.style
        assertEquals("AnimeClips Default", Preset.Default.name)
        assertEquals("@animecliplabs", s.text)
        assertEquals(Anchor.BOTTOM_RIGHT, s.anchor)
        assertEquals(0.8f, s.opacity)
        assertEquals(0xFFFFFFFF.toInt(), s.colorArgb)
        assertTrue(s.shadow)
        assertEquals(com.animecliplabs.clipmark.core.model.SizeLevel.SMALL, s.sizeLevel)
    }

    @Test
    fun `presets round-trip losslessly`() {
        val presets = listOf(
            Preset.Default,
            Preset(
                id = "p2", name = "Story – top", createdAt = 42L,
                style = WatermarkStyle(
                    text = "@animecliplabs ✦", font = WatermarkFont.SCRIPT, sizePercent = 3.3f, bold = true,
                    colorArgb = 0xFFFF7AA2.toInt(), opacity = 0.55f, anchor = Anchor.TOP_CENTER,
                    customCenter = NormalizedPoint(0.4f, 0.1f), marginXPercent = 5f, marginYPercent = 7f,
                    shadow = false, outline = true, contrastAssist = false, smartCorner = true,
                ),
            ),
        )
        assertEquals(presets, PresetCodec.decode(PresetCodec.encode(presets)))
    }

    @Test
    fun `older saved data without new fields still loads with defaults`() {
        val json = """[{"id":"a","name":"Old","style":{"text":"@me","anchor":"TOP_LEFT"}}]"""
        val p = PresetCodec.decode(json).single()
        assertEquals("@me", p.style.text)
        assertEquals(Anchor.TOP_LEFT, p.style.anchor)
        assertEquals(WatermarkStyle().opacity, p.style.opacity)
        assertTrue(p.style.contrastAssist)
    }

    @Test
    fun `data from a newer version with unknown keys or values still loads`() {
        val json = """[{"id":"a","name":"New","future":1,"style":{"text":"@me","anchor":"SOMEWHERE_NEW","glow":true}}]"""
        val p = PresetCodec.decode(json).single()
        assertEquals(Anchor.BOTTOM_RIGHT, p.style.anchor) // unknown enum → default
    }

    @Test
    fun `corrupt or empty storage falls back to the default preset instead of crashing`() {
        assertEquals(listOf(Preset.Default), PresetCodec.decode("{not json"))
        assertEquals(listOf(Preset.Default), PresetCodec.decode("[]"))
        assertEquals(listOf(Preset.Default), PresetCodec.decode(null))
    }

    @Test
    fun `output settings default to faithful and private`() {
        val s = SettingsCodec.decode(null)
        assertEquals(OutputFormatPreference.MATCH_ORIGINAL, s.format)
        assertEquals(OutputSettings.QUALITY_HIGH, s.quality)
        assertTrue(s.keepMetadata)
        assertTrue(s.stripLocation)
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
    }

    @Test
    fun `batch jobs round-trip for resumption`() {
        val job = BatchJob(
            id = "j", createdAt = 1, presetName = "AnimeClips Default", style = WatermarkStyle(), output = OutputSettings(),
            items = listOf(BatchItem("content://a/1", "a.jpg", resolvedAnchor = Anchor.TOP_LEFT)),
        )
        val back = AppJson.decodeFromString(BatchJob.serializer(), AppJson.encodeToString(BatchJob.serializer(), job))
        assertEquals(job, back)
        assertFalse(back.isFinished)
    }
}
