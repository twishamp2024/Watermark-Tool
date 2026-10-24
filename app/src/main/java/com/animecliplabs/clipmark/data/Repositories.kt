package com.animecliplabs.clipmark.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.animecliplabs.clipmark.core.batch.BatchJob
import com.animecliplabs.clipmark.core.batch.HistoryEntry
import com.animecliplabs.clipmark.core.batch.HistoryGrouping
import com.animecliplabs.clipmark.core.model.OutputSettings
import com.animecliplabs.clipmark.core.model.Preset
import com.animecliplabs.clipmark.core.model.WatermarkStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private object Keys {
    val PRESETS = stringPreferencesKey("presets")
    val ACTIVE_PRESET = stringPreferencesKey("active_preset")
    val OUTPUT = stringPreferencesKey("output_settings")
    val HISTORY = stringPreferencesKey("history")
    val ASKED_NOTIFICATIONS = booleanPreferencesKey("asked_notifications")
}

class PresetRepository(private val store: DataStore<Preferences>) {

    val presets: Flow<List<Preset>> = store.data.map { PresetCodec.decode(it[Keys.PRESETS]) }.distinctUntilChanged()

    /** The last-used preset — what the app opens with. */
    val activePreset: Flow<Preset> = store.data.map { prefs ->
        val list = PresetCodec.decode(prefs[Keys.PRESETS])
        list.firstOrNull { it.id == prefs[Keys.ACTIVE_PRESET] } ?: list.first()
    }.distinctUntilChanged()

    suspend fun active(): Preset = activePreset.first()

    suspend fun setActive(id: String) {
        store.edit { it[Keys.ACTIVE_PRESET] = id }
    }

    suspend fun create(name: String, style: WatermarkStyle): Preset {
        val preset = Preset(UUID.randomUUID().toString(), name.trim().ifEmpty { "Preset" }, style, System.currentTimeMillis())
        store.edit { prefs ->
            prefs[Keys.PRESETS] = PresetCodec.encode(PresetCodec.decode(prefs[Keys.PRESETS]) + preset)
            prefs[Keys.ACTIVE_PRESET] = preset.id
        }
        return preset
    }

    suspend fun update(preset: Preset) = mutate { list -> list.map { if (it.id == preset.id) preset else it } }

    suspend fun rename(id: String, name: String) =
        mutate { list -> list.map { if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }) else it } }

    suspend fun duplicate(id: String) = mutate { list ->
        val source = list.firstOrNull { it.id == id } ?: return@mutate list
        list + source.copy(id = UUID.randomUUID().toString(), name = "${source.name} copy", createdAt = System.currentTimeMillis())
    }

    /** The last remaining preset cannot be deleted — the app always has something ready. */
    suspend fun delete(id: String) = mutate { list -> if (list.size <= 1) list else list.filterNot { it.id == id } }

    private suspend fun mutate(transform: (List<Preset>) -> List<Preset>) {
        store.edit { prefs -> prefs[Keys.PRESETS] = PresetCodec.encode(transform(PresetCodec.decode(prefs[Keys.PRESETS]))) }
    }
}

class SettingsRepository(private val store: DataStore<Preferences>) {
    val output: Flow<OutputSettings> = store.data.map { SettingsCodec.decode(it[Keys.OUTPUT]) }.distinctUntilChanged()

    suspend fun current(): OutputSettings = output.first()

    suspend fun update(transform: (OutputSettings) -> OutputSettings) {
        store.edit { it[Keys.OUTPUT] = SettingsCodec.encode(transform(SettingsCodec.decode(it[Keys.OUTPUT]))) }
    }

    val askedForNotifications: Flow<Boolean> = store.data.map { it[Keys.ASKED_NOTIFICATIONS] ?: false }

    suspend fun markAskedForNotifications() {
        store.edit { it[Keys.ASKED_NOTIFICATIONS] = true }
    }
}

class HistoryRepository(private val store: DataStore<Preferences>) {
    val entries: Flow<List<HistoryEntry>> = store.data.map { HistoryCodec.decode(it[Keys.HISTORY]) }.distinctUntilChanged()

    suspend fun record(job: BatchJob) {
        if (job.savedCount == 0 && job.failedCount == 0) return
        val entry = HistoryEntry(
            id = job.id,
            finishedAt = job.finishedAt ?: System.currentTimeMillis(),
            saved = job.savedCount,
            failed = job.failedCount,
            presetName = job.presetName,
            outputUris = job.items.mapNotNull { it.outputUri },
        )
        store.edit { it[Keys.HISTORY] = HistoryCodec.encode(HistoryGrouping.append(HistoryCodec.decode(it[Keys.HISTORY]), entry)) }
    }

    suspend fun clear() {
        store.edit { it.remove(Keys.HISTORY) }
    }
}
