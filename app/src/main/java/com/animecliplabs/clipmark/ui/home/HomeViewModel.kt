package com.animecliplabs.clipmark.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animecliplabs.clipmark.AppContainer
import com.animecliplabs.clipmark.core.batch.DayGroup
import com.animecliplabs.clipmark.core.batch.HistoryGrouping
import com.animecliplabs.clipmark.core.model.Preset
import com.animecliplabs.clipmark.data.BatchRequest
import com.animecliplabs.clipmark.data.BatchState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class HomeUiState(
    val loaded: Boolean = false,
    val active: Preset = Preset.Default,
    val presets: List<Preset> = emptyList(),
    val batch: BatchState = BatchState(null, false),
    val recent: List<DayGroup> = emptyList(),
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        container.presets.presets,
        container.presets.activePreset,
        container.batches.state,
        container.history.entries,
    ) { presets, active, batch, history ->
        HomeUiState(
            loaded = true,
            active = active,
            presets = presets,
            batch = batch,
            recent = HistoryGrouping.group(history, LocalDate.now(), ZoneId.systemDefault()).take(2),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setActive(id: String) {
        viewModelScope.launch { container.presets.setActive(id) }
    }

    /** Pick → process with the active preset. No preview, no questions. */
    fun quickWatermark(uris: List<Uri>, onStarted: () -> Unit, onBusy: () -> Unit) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val preset = container.presets.active()
            val started = container.batches.start(BatchRequest(uris, preset.name, preset.style))
            if (started) onStarted() else onBusy()
        }
    }

    fun offerSelection(uris: List<Uri>) = container.selection.offer(uris)

    fun resume() {
        viewModelScope.launch { container.batches.resume() }
    }

    fun dismissBatch() {
        viewModelScope.launch { container.batches.dismiss() }
    }
}
