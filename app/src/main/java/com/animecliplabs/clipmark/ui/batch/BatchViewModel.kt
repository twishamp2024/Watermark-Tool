package com.animecliplabs.clipmark.ui.batch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animecliplabs.clipmark.AppContainer
import com.animecliplabs.clipmark.data.BatchState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BatchViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<BatchState?> = container.batches.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun cancel() = container.batches.cancel()

    fun retryUnsaved() {
        viewModelScope.launch { container.batches.retryUnsaved() }
    }

    fun resume() {
        viewModelScope.launch { container.batches.resume() }
    }

    fun dismiss(then: () -> Unit) {
        viewModelScope.launch {
            container.batches.dismiss()
            then()
        }
    }
}
