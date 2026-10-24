package com.animecliplabs.clipmark.core.batch

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One finished batch. Stores only counts and links to the exports — never image copies.
 * Links to exports the user later deleted simply stop resolving.
 */
@Serializable
data class HistoryEntry(
    val id: String,
    val finishedAt: Long,
    val saved: Int,
    val failed: Int,
    val presetName: String,
    val outputUris: List<String> = emptyList(),
)

sealed interface DayLabel {
    data object Today : DayLabel
    data object Yesterday : DayLabel
    data class Date(val date: LocalDate) : DayLabel
}

data class DayGroup(val label: DayLabel, val savedImages: Int, val entries: List<HistoryEntry>)

object HistoryGrouping {
    const val MAX_ENTRIES = 60
    const val MAX_URIS_PER_ENTRY = 100

    fun group(entries: List<HistoryEntry>, today: LocalDate, zone: ZoneId): List<DayGroup> =
        entries
            .sortedByDescending { it.finishedAt }
            .groupBy { Instant.ofEpochMilli(it.finishedAt).atZone(zone).toLocalDate() }
            .map { (date, dayEntries) ->
                val label = when (date) {
                    today -> DayLabel.Today
                    today.minusDays(1) -> DayLabel.Yesterday
                    else -> DayLabel.Date(date)
                }
                DayGroup(label, dayEntries.sumOf { it.saved }, dayEntries)
            }

    /** Newest first, capped so history stays a few kilobytes forever. */
    fun append(existing: List<HistoryEntry>, entry: HistoryEntry): List<HistoryEntry> =
        (listOf(entry.copy(outputUris = entry.outputUris.take(MAX_URIS_PER_ENTRY))) + existing.filter { it.id != entry.id })
            .take(MAX_ENTRIES)
}
