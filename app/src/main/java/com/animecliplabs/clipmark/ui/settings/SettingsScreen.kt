package com.animecliplabs.clipmark.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.animecliplabs.clipmark.AppContainer
import com.animecliplabs.clipmark.BuildConfig
import com.animecliplabs.clipmark.R
import com.animecliplabs.clipmark.core.model.OutputFormatPreference
import com.animecliplabs.clipmark.core.model.OutputSettings
import com.animecliplabs.clipmark.image.MediaStoreOutputStore
import com.animecliplabs.clipmark.ui.common.SectionHeader
import com.animecliplabs.clipmark.ui.common.SwitchRow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<OutputSettings> =
        container.settings.output.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OutputSettings())

    fun update(transform: (OutputSettings) -> OutputSettings) {
        viewModelScope.launch { container.settings.update(transform) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionHeader(stringResource(R.string.settings_output), Modifier.padding(horizontal = 16.dp))
            Column(Modifier.selectableGroup()) {
                FormatOption(stringResource(R.string.format_match), stringResource(R.string.format_match_desc), s.format == OutputFormatPreference.MATCH_ORIGINAL) {
                    viewModel.update { it.copy(format = OutputFormatPreference.MATCH_ORIGINAL) }
                }
                FormatOption(stringResource(R.string.format_jpeg), stringResource(R.string.format_jpeg_desc), s.format == OutputFormatPreference.JPEG) {
                    viewModel.update { it.copy(format = OutputFormatPreference.JPEG) }
                }
                FormatOption(stringResource(R.string.format_png), stringResource(R.string.format_png_desc), s.format == OutputFormatPreference.PNG) {
                    viewModel.update { it.copy(format = OutputFormatPreference.PNG) }
                }
            }
            Text(
                stringResource(R.string.quality),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
            Text(
                stringResource(R.string.quality_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
                listOf(OutputSettings.QUALITY_HIGH to R.string.quality_high, OutputSettings.QUALITY_MAX to R.string.quality_max)
                    .forEachIndexed { i, (q, label) ->
                        SegmentedButton(
                            selected = s.quality == q,
                            onClick = { viewModel.update { it.copy(quality = q) } },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(stringResource(label)) }
                    }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionHeader(stringResource(R.string.settings_metadata), Modifier.padding(horizontal = 16.dp))
            SwitchRow(
                title = stringResource(R.string.keep_metadata),
                description = stringResource(R.string.keep_metadata_desc),
                icon = Icons.Outlined.CameraAlt,
                checked = s.keepMetadata,
                onCheckedChange = { on -> viewModel.update { it.copy(keepMetadata = on) } },
            )
            SwitchRow(
                title = stringResource(R.string.strip_location),
                description = stringResource(R.string.strip_location_desc),
                icon = Icons.Outlined.LocationOff,
                checked = s.stripLocation || !s.keepMetadata,
                enabled = s.keepMetadata,
                onCheckedChange = { on -> viewModel.update { it.copy(stripLocation = on) } },
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionHeader(stringResource(R.string.settings_about), Modifier.padding(horizontal = 16.dp))
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.save_location)) },
                supportingContent = { Text(MediaStoreOutputStore.RELATIVE_PATH.trimEnd('/')) },
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.privacy_title)) },
                supportingContent = { Text(stringResource(R.string.privacy_body)) },
            )
            Text(
                stringResource(R.string.version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun FormatOption(title: String, description: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
