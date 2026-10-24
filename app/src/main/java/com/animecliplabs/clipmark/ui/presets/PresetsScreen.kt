package com.animecliplabs.clipmark.ui.presets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.animecliplabs.clipmark.AppContainer
import com.animecliplabs.clipmark.R
import com.animecliplabs.clipmark.core.model.Preset
import com.animecliplabs.clipmark.ui.common.SamplePreview
import com.animecliplabs.clipmark.ui.common.styleSummary
import com.animecliplabs.clipmark.ui.editor.PresetNameDialog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PresetsUiState(val presets: List<Preset> = emptyList(), val activeId: String = "")

class PresetsViewModel(private val container: AppContainer) : ViewModel() {
    val state: StateFlow<PresetsUiState> =
        combine(container.presets.presets, container.presets.activePreset) { list, active -> PresetsUiState(list, active.id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PresetsUiState())

    fun use(id: String) = viewModelScope.launch { container.presets.setActive(id) }
    fun rename(id: String, name: String) = viewModelScope.launch { container.presets.rename(id, name) }
    fun duplicate(id: String) = viewModelScope.launch { container.presets.duplicate(id) }
    fun delete(id: String) = viewModelScope.launch { container.presets.delete(id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetsScreen(
    viewModel: PresetsViewModel,
    onBack: () -> Unit,
    onEditPreset: (String) -> Unit,
    onNewPreset: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<Preset?>(null) }
    var deleting by remember { mutableStateOf<Preset?>(null) }

    renaming?.let { preset ->
        PresetNameDialog(
            initial = preset.name,
            title = stringResource(R.string.preset_rename),
            onDismiss = { renaming = null },
            onConfirm = { viewModel.rename(preset.id, it); renaming = null },
        )
    }
    deleting?.let { preset ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.preset_delete_title, preset.name)) },
            text = { Text(stringResource(R.string.preset_delete_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(preset.id); deleting = null }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.presets_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewPreset,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.preset_new)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.presets, key = { it.id }) { preset ->
                PresetCard(
                    preset = preset,
                    active = preset.id == state.activeId,
                    canDelete = state.presets.size > 1,
                    onUse = { viewModel.use(preset.id) },
                    onEdit = { onEditPreset(preset.id) },
                    onRename = { renaming = preset },
                    onDuplicate = { viewModel.duplicate(preset.id) },
                    onDelete = { deleting = preset },
                )
            }
        }
    }
}

@Composable
private fun PresetCard(
    preset: Preset,
    active: Boolean,
    canDelete: Boolean,
    onUse: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = onEdit,
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SamplePreview(preset.style, Modifier.width(72.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(preset.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (active) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Filled.CheckCircle, stringResource(R.string.preset_active), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
                Text(preset.style.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(styleSummary(preset.style), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                if (!active) {
                    FilledTonalButton(onClick = onUse, modifier = Modifier.height(40.dp)) { Text(stringResource(R.string.preset_use)) }
                } else {
                    Text(stringResource(R.string.preset_active_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more_options)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.preset_rename)) }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.preset_duplicate)) }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        enabled = canDelete,
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}
