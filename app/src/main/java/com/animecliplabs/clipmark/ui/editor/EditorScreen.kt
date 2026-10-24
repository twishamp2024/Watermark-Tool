package com.animecliplabs.clipmark.ui.editor

import android.content.res.Configuration
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.animecliplabs.clipmark.R
import com.animecliplabs.clipmark.ui.common.LocalAppContainer
import com.animecliplabs.clipmark.ui.common.Thumbnail
import com.animecliplabs.clipmark.ui.common.rememberNotificationGate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: EditorViewModel, onBack: () -> Unit, onBatchStarted: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val renderer = LocalAppContainer.current.renderer
    val snackbar = remember { SnackbarHostState() }
    val gate = rememberNotificationGate()
    var nameDialog by remember { mutableStateOf(false) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val messages = mapOf(
        EditorMessage.PresetUpdated::class to stringResource(R.string.msg_preset_updated),
        EditorMessage.BatchBusy::class to stringResource(R.string.batch_busy),
        EditorMessage.EmptyText::class to stringResource(R.string.error_empty_text),
        EditorMessage.OverridesCleared::class to stringResource(R.string.msg_overrides_cleared),
    )
    val createdTemplate = stringResource(R.string.msg_preset_created)
    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        val text = if (msg is EditorMessage.PresetCreated) createdTemplate.format(msg.name) else messages[msg::class].orEmpty()
        viewModel.consumeMessage()
        snackbar.showSnackbar(text)
    }

    if (nameDialog) {
        PresetNameDialog(
            initial = if (state.mode == EditorMode.NEW_PRESET) "" else stringResource(R.string.preset_copy_name, state.presetName),
            onDismiss = { nameDialog = false },
            onConfirm = { name ->
                nameDialog = false
                viewModel.saveAsPreset(name, onDone = { if (state.mode != EditorMode.PHOTOS) onBack() })
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
                },
                title = {
                    Column {
                        Text(
                            stringResource(
                                when (state.mode) {
                                    EditorMode.PHOTOS -> R.string.editor_title
                                    EditorMode.EDIT_PRESET -> R.string.editor_title_preset
                                    EditorMode.NEW_PRESET -> R.string.editor_title_new_preset
                                },
                            ),
                            maxLines = 1,
                        )
                        if (state.count > 1) {
                            Text(
                                stringResource(R.string.photo_x_of_y, state.index + 1, state.count),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconToggleButton(checked = state.showOriginal, onCheckedChange = viewModel::setShowOriginal) {
                        Icon(Icons.Outlined.Compare, stringResource(R.string.show_original))
                    }
                    if (state.mode == EditorMode.PHOTOS) {
                        PresetMenu(state, viewModel, onSaveAsNew = { nameDialog = true })
                    }
                },
            )
        },
        bottomBar = {
            BottomActions(
                state = state,
                onSaveImages = { gate { viewModel.saveImages(onBatchStarted) } },
                onUpdatePreset = { viewModel.updatePreset(onDone = onBack) },
                onSaveAsNew = { nameDialog = true },
            )
        },
    ) { padding ->
        val preview = @Composable { modifier: Modifier ->
            EditorPreview(
                preview = state.preview,
                style = state.currentStyle,
                smartAnchor = state.smartAnchor,
                readability = state.readability,
                showOriginal = state.showOriginal,
                guides = state.guides,
                renderer = renderer,
                onDrag = viewModel::drag,
                onDragEnd = viewModel::endDrag,
                onHoldOriginal = viewModel::setShowOriginal,
                modifier = modifier,
            )
        }
        if (landscape) {
            Row(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.weight(1.1f).fillMaxHeight()) {
                    preview(Modifier.weight(1f).fillMaxWidth())
                    ThumbnailStrip(state, viewModel::select)
                }
                ControlsPanel(state, viewModel, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                val panelMax = maxHeight * 0.5f
                Column(Modifier.fillMaxSize()) {
                    preview(Modifier.weight(1f).fillMaxWidth())
                    ThumbnailStrip(state, viewModel::select)
                    ControlsPanel(state, viewModel, Modifier.fillMaxWidth().heightIn(max = panelMax))
                }
            }
        }
    }
}

@Composable
private fun ThumbnailStrip(state: EditorUiState, onSelect: (Int) -> Unit) {
    if (state.count <= 1) return
    val listState = rememberLazyListState()
    LaunchedEffect(state.index) { listState.animateScrollToItem((state.index - 2).coerceAtLeast(0)) }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(state.uris) { i, uri ->
            val selected = i == state.index
            val custom = !state.lockAll && state.overrides.containsKey(i)
            val description = stringResource(R.string.photo_x_of_y, i + 1, state.count)
            Box(
                Modifier
                    .selectable(selected = selected, onClick = { onSelect(i) })
                    .semantics { contentDescription = description },
            ) {
                Thumbnail(
                    uri = uri,
                    size = 56.dp,
                    modifier = if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small) else Modifier,
                )
                if (custom) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.align(Alignment.TopEnd).padding(3.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Tune, stringResource(R.string.a11y_custom_adjusted),
                            tint = MaterialTheme.colorScheme.onTertiary, modifier = Modifier.size(14.dp).padding(1.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ControlsPanel(state: EditorUiState, viewModel: EditorViewModel, modifier: Modifier) {
    val renderer = LocalAppContainer.current.renderer
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large.copy(
        bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp),
        bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp),
    )) {
        Column {
            if (state.count > 1) {
                LockRow(state.lockAll, state.count, viewModel::setLockAll)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                listOf(R.string.tab_position, R.string.tab_style, R.string.tab_readability).forEachIndexed { i, res ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(res), maxLines = 1) })
                }
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
                when (tab) {
                    0 -> PositionControls(state.currentStyle, state.smartAnchor, viewModel::updateStyle)
                    1 -> StyleControls(state.currentStyle, renderer, viewModel::updateStyle)
                    else -> ReadabilityControls(state.currentStyle, state.readability, viewModel::updateStyle)
                }
            }
        }
    }
}

/** Batch Preset Lock: one switch that guarantees identical branding across the whole selection. */
@Composable
private fun LockRow(locked: Boolean, count: Int, onChange: (Boolean) -> Unit) {
    com.animecliplabs.clipmark.ui.common.SwitchRow(
        title = pluralStringResource(R.plurals.lock_all_title, count, count),
        description = stringResource(if (locked) R.string.lock_all_on else R.string.lock_all_off),
        icon = if (locked) Icons.Filled.Lock else Icons.Outlined.LockOpen,
        checked = locked,
        onCheckedChange = onChange,
    )
}

@Composable
private fun PresetMenu(state: EditorUiState, viewModel: EditorViewModel, onSaveAsNew: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text(
                if (state.isModified) stringResource(R.string.preset_edited, state.presetName) else state.presetName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp),
            )
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = stringResource(R.string.choose_preset))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            state.presets.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.name) },
                    leadingIcon = if (preset.id == state.presetId) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else null,
                    onClick = { open = false; viewModel.selectPreset(preset) },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.preset_update, state.presetName)) },
                enabled = state.isModified,
                onClick = { open = false; viewModel.updatePreset() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.preset_save_as_new)) },
                onClick = { open = false; onSaveAsNew() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.preset_revert)) },
                enabled = state.isModified || state.overrides.isNotEmpty(),
                onClick = { open = false; viewModel.revertToPreset() },
            )
        }
    }
}

@Composable
private fun BottomActions(
    state: EditorUiState,
    onSaveImages: () -> Unit,
    onUpdatePreset: () -> Unit,
    onSaveAsNew: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (state.mode) {
                EditorMode.PHOTOS -> Button(
                    onClick = onSaveImages,
                    enabled = state.ready && !state.busy,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(pluralStringResource(R.plurals.save_photos, state.count, state.count), style = MaterialTheme.typography.titleMedium)
                }
                EditorMode.EDIT_PRESET -> {
                    OutlinedButton(onClick = onSaveAsNew, modifier = Modifier.height(56.dp)) {
                        Text(stringResource(R.string.preset_save_as_new_short))
                    }
                    Button(
                        onClick = onUpdatePreset,
                        enabled = state.isModified,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) { Text(stringResource(R.string.preset_save)) }
                }
                EditorMode.NEW_PRESET -> Button(
                    onClick = onSaveAsNew,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text(stringResource(R.string.preset_create)) }
            }
        }
    }
}

@Composable
fun PresetNameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit, title: String = stringResource(R.string.preset_name_title)) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = { Text(stringResource(R.string.preset_name_label)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
