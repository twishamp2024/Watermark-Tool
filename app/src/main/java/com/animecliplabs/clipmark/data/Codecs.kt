package com.animecliplabs.clipmark.data

import com.animecliplabs.clipmark.core.batch.HistoryEntry
import com.animecliplabs.clipmark.core.model.OutputSettings
import com.animecliplabs.clipmark.core.model.Preset
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Tolerant JSON: unknown keys (from a newer app version) are ignored, missing keys take their
 * defaults, and unknown enum values fall back to the default. Stored settings never crash the app.
 */
val AppJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
}

object PresetCodec {
    private val serializer = ListSerializer(Preset.serializer())

    fun encode(presets: List<Preset>): String = AppJson.encodeToString(serializer, presets)

    /** Always returns at least one preset. */
    fun decode(json: String?): List<Preset> {
        val decoded = json?.let { runCatching { AppJson.decodeFromString(serializer, it) }.getOrNull() }
        return decoded?.takeIf { it.isNotEmpty() } ?: listOf(Preset.Default)
    }
}

object SettingsCodec {
    fun encode(settings: OutputSettings): String = AppJson.encodeToString(OutputSettings.serializer(), settings)
    fun decode(json: String?): OutputSettings =
        json?.let { runCatching { AppJson.decodeFromString(OutputSettings.serializer(), it) }.getOrNull() } ?: OutputSettings()
}

object HistoryCodec {
    private val serializer = ListSerializer(HistoryEntry.serializer())
    fun encode(entries: List<HistoryEntry>): String = AppJson.encodeToString(serializer, entries)
    fun decode(json: String?): List<HistoryEntry> =
        json?.let { runCatching { AppJson.decodeFromString(serializer, it) }.getOrNull() } ?: emptyList()
}
