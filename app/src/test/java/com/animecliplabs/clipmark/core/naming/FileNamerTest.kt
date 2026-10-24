package com.animecliplabs.clipmark.core.naming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNamerTest {

    @Test
    fun `strips the extension and adds the suffix`() {
        val base = FileNamer.baseName("image.jpg", "fallback")
        assertEquals("image", base)
        assertEquals("image_watermarked.jpg", FileNamer.candidate(base, "jpg", 1))
    }

    @Test
    fun `duplicates get a clean counter and never overwrite`() {
        val taken = mutableSetOf("image_watermarked.jpg", "image_watermarked_2.jpg")
        val name = FileNamer.uniqueName("image", "jpg") { it in taken }
        assertEquals("image_watermarked_3.jpg", name)
    }

    @Test
    fun `first free name is used when nothing collides`() {
        assertEquals("frame_watermarked.png", FileNamer.uniqueName("frame", "png") { false })
    }

    @Test
    fun `extension follows the output format, not the source`() {
        val base = FileNamer.baseName("IMG_0042.HEIC", "x")
        assertEquals("IMG_0042_watermarked.jpg", FileNamer.candidate(base, "jpg", 1))
    }

    @Test
    fun `unsafe characters are replaced`() {
        val base = FileNamer.baseName("ep 3: \"final\" <cut>?.png", "x")
        assertFalse(base.any { it in "\\/:*?\"<>|" })
        assertTrue(base.startsWith("ep 3_"))
    }

    @Test
    fun `missing or empty names fall back`() {
        assertEquals("animecliplabs_1", FileNamer.baseName(null, "animecliplabs_1"))
        assertEquals("animecliplabs_1", FileNamer.baseName(".jpg", "animecliplabs_1"))
        assertEquals("animecliplabs_1", FileNamer.baseName("   ", "animecliplabs_1"))
    }

    @Test
    fun `opaque photo-picker ids are not used as names, real numeric file names are`() {
        assertEquals("animecliplabs_20260924_2113", FileNamer.baseName("47", "animecliplabs_20260924_2113"))
        assertEquals("20260101", FileNamer.baseName("20260101.jpg", "x"))
    }

    @Test
    fun `dots inside names survive, only a known extension is removed`() {
        assertEquals("v1.2 final", FileNamer.baseName("v1.2 final.webp", "x"))
        assertEquals("archive.tar", FileNamer.baseName("archive.tar", "x"))
    }

    @Test
    fun `very long names are truncated`() {
        assertEquals(80, FileNamer.baseName("a".repeat(300) + ".jpg", "x").length)
    }
}
