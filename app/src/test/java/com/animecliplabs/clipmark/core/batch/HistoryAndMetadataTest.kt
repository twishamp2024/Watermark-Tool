package com.animecliplabs.clipmark.core.batch

import com.animecliplabs.clipmark.core.metadata.MetadataPolicy
import com.animecliplabs.clipmark.core.model.OutputSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class HistoryAndMetadataTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 9, 24)
    private fun at(date: LocalDate, hour: Int) = date.atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private fun entry(id: String, time: Long, saved: Int) = HistoryEntry(id, time, saved, 0, "AnimeClips Default")

    @Test
    fun `history groups by day with totals`() {
        val groups = HistoryGrouping.group(
            listOf(
                entry("a", at(today, 9), 5),
                entry("b", at(today, 18), 7),
                entry("c", at(today.minusDays(1), 12), 8),
                entry("d", at(today.minusDays(5), 12), 3),
            ),
            today, zone,
        )
        assertEquals(DayLabel.Today, groups[0].label)
        assertEquals(12, groups[0].savedImages)
        assertEquals("b", groups[0].entries.first().id) // newest first
        assertEquals(DayLabel.Yesterday, groups[1].label)
        assertEquals(8, groups[1].savedImages)
        assertEquals(DayLabel.Date(today.minusDays(5)), groups[2].label)
    }

    @Test
    fun `history stays small - capped entries and links, no image copies`() {
        var list = emptyList<HistoryEntry>()
        repeat(100) { i ->
            list = HistoryGrouping.append(list, entry("$i", i.toLong(), 1).copy(outputUris = List(500) { "content://x/$it" }))
        }
        assertEquals(HistoryGrouping.MAX_ENTRIES, list.size)
        assertEquals("99", list.first().id)
        assertTrue(list.all { it.outputUris.size <= HistoryGrouping.MAX_URIS_PER_ENTRY })
    }

    @Test
    fun `location is stripped by default but dates and camera are kept`() {
        val tags = MetadataPolicy.tagsToCopy(OutputSettings())
        assertTrue("DateTimeOriginal" in tags)
        assertTrue("Model" in tags)
        assertFalse(tags.any { it.startsWith("GPS") })
        assertFalse("Orientation" in tags) // always written as normal, never copied
    }

    @Test
    fun `location is only copied when explicitly allowed`() {
        assertTrue("GPSLatitude" in MetadataPolicy.tagsToCopy(OutputSettings(stripLocation = false)))
        assertTrue(MetadataPolicy.tagsToCopy(OutputSettings(keepMetadata = false)).isEmpty())
    }
}
