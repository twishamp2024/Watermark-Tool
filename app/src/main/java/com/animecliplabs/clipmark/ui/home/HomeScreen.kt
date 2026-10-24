package com.animecliplabs.clipmark.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.animecliplabs.clipmark.R
import com.animecliplabs.clipmark.core.batch.DayGroup
import com.animecliplabs.clipmark.core.batch.DayLabel
import com.animecliplabs.clipmark.core.model.Preset
import com.animecliplabs.clipmark.data.BatchState
import com.animecliplabs.clipmark.ui.common.Intents
import com.animecliplabs.clipmark.ui.common.LocalAppContainer
import com.animecliplabs.clipmark.ui.common.SamplePreview
import com.animecliplabs.clipmark.ui.common.SectionHeader
import com.animecliplabs.clipmark.ui.common.rememberNotificationGate
import com.animecliplabs.clipmark.ui.common.styleSummary
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    openPickerRequested: Boolean,
    onPickerRequestConsumed: () -> Unit,
    onOpenEditor: () -> Unit,
    onEditPreset: (String) -> Unit,
    onNewPreset: () -> Unit,
    onOpenBatch: () -> Unit,
    onOpenPresets: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val gate = rememberNotificationGate()
    val busyMessage = stringResource(R.string.batch_busy)
    val imagesOnly = remember { PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly) }

    val quickPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) gate {
            viewModel.quickWatermark(uris, onStarted = onOpenBatch, onBusy = { scope.launch { snackbar.showSnackbar(busyMessage) } })
        }
    }
    val previewPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.offerSelection(uris)
            onOpenEditor()
        }
    }

    LaunchedEffect(openPickerRequested) {
        if (openPickerRequested) {
            onPickerRequestConsumed()
            previewPicker.launch(imagesOnly)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.home_tagline),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenHistory) { Icon(Icons.Outlined.History, stringResource(R.string.history_title)) }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings_title)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "banner") {
                AnimatedVisibility(
                    visible = state.batch.job != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    BatchBanner(
                        batch = state.batch,
                        onOpen = onOpenBatch,
                        onResume = viewModel::resume,
                        onDismiss = viewModel::dismissBatch,
                        onShare = { uris -> Intents.shareImages(context, uris) },
                    )
                }
            }
            item(key = "preset") {
                ActivePresetCard(state.active, onAdjust = { onEditPreset(state.active.id) })
            }
            item(key = "actions") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { quickPicker.launch(imagesOnly) },
                        enabled = !state.batch.running,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                    ) {
                        Icon(Icons.Filled.Bolt, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.quick_watermark), style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = if (state.batch.running) stringResource(R.string.home_quick_busy)
                        else stringResource(R.string.home_quick_caption, state.active.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = { previewPicker.launch(imagesOnly) },
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.select_and_preview))
                    }
                }
            }
            item(key = "presets") {
                Column {
                    SectionHeader(stringResource(R.string.presets_title), Modifier.semantics { heading() }) {
                        TextButton(onClick = onOpenPresets) { Text(stringResource(R.string.manage)) }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.presets, key = { it.id }) { preset ->
                            val selected = preset.id == state.active.id
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.setActive(preset.id) },
                                label = { Text(preset.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp)) },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                } else null,
                            )
                        }
                        item {
                            AssistChip(
                                onClick = onNewPreset,
                                label = { Text(stringResource(R.string.preset_new)) },
                                leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            )
                        }
                    }
                }
            }
            item(key = "recent") { RecentSection(state.recent, onOpenHistory) }
            item(key = "privacy") {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.privacy_footer),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivePresetCard(preset: Preset, onAdjust: () -> Unit) {
    val renderer = LocalAppContainer.current.renderer
    val family = remember(preset.style.font, preset.style.bold) {
        FontFamily(renderer.typeface(preset.style.font, preset.style.bold))
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SamplePreview(preset.style, Modifier.width(112.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.home_ready_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(preset.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                Text(
                    preset.style.text,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = family),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    styleSummary(preset.style),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onAdjust, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)) {
                    Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.adjust_preset))
                }
            }
        }
    }
}

@Composable
private fun BatchBanner(
    batch: BatchState,
    onOpen: () -> Unit,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
    onShare: (List<Uri>) -> Unit,
) {
    val job = batch.job ?: return
    when {
        batch.running -> Card(
            onClick = onOpen,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.batch_processing_count, (job.processedCount + 1).coerceAtMost(job.total), job.total),
                    style = MaterialTheme.typography.titleSmall,
                )
                LinearProgressIndicator(
                    progress = { job.processedCount / job.total.coerceAtLeast(1).toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        batch.interrupted -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
            Column(Modifier.padding(16.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.PauseCircle, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.batch_paused_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.batch_paused_body, job.savedCount, job.pendingCount),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.discard)) }
                    FilledTonalButton(onClick = onResume) { Text(stringResource(R.string.resume)) }
                }
            }
        }
        else -> Card(
            onClick = onOpen,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        pluralStringResource(R.plurals.photos_saved, job.savedCount, job.savedCount),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (job.failedCount > 0) {
                        Text(
                            pluralStringResource(R.plurals.photos_failed, job.failedCount, job.failedCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Text(stringResource(R.string.batch_banner_tap), style = MaterialTheme.typography.bodySmall)
                    }
                }
                val outputs = job.items.mapNotNull { it.outputUri?.let(Uri::parse) }
                if (outputs.isNotEmpty()) {
                    IconButton(onClick = { onShare(outputs) }) { Icon(Icons.Outlined.Share, stringResource(R.string.share)) }
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, stringResource(R.string.dismiss)) }
            }
        }
    }
}

@Composable
private fun RecentSection(recent: List<DayGroup>, onOpenHistory: () -> Unit) {
    Column {
        SectionHeader(stringResource(R.string.recent_title), Modifier.semantics { heading() }) {
            if (recent.isNotEmpty()) TextButton(onClick = onOpenHistory) { Text(stringResource(R.string.see_all)) }
        }
        if (recent.isEmpty()) {
            Text(
                stringResource(R.string.recent_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Card(onClick = onOpenHistory, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                recent.forEach { group ->
                    ListItem(
                        headlineContent = { Text(dayLabel(group.label)) },
                        supportingContent = {
                            Text(pluralStringResource(R.plurals.images_watermarked, group.savedImages, group.savedImages))
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    )
                }
            }
        }
    }
}

@Composable
fun dayLabel(label: DayLabel): String = when (label) {
    DayLabel.Today -> stringResource(R.string.today)
    DayLabel.Yesterday -> stringResource(R.string.yesterday)
    is DayLabel.Date -> remember(label.date) { label.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }
}
