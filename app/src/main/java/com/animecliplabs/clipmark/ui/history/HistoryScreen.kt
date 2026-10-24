package com.animecliplabs.clipmark.ui.history

import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.animecliplabs.clipmark.AppContainer
import com.animecliplabs.clipmark.R
import com.animecliplabs.clipmark.core.batch.DayGroup
import com.animecliplabs.clipmark.core.batch.HistoryEntry
import com.animecliplabs.clipmark.core.batch.HistoryGrouping
import com.animecliplabs.clipmark.ui.common.Intents
import com.animecliplabs.clipmark.ui.common.Thumbnail
import com.animecliplabs.clipmark.ui.home.dayLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

class HistoryViewModel(private val container: AppContainer) : ViewModel() {
    val groups: StateFlow<List<DayGroup>?> = container.history.entries
        .map { HistoryGrouping.group(it, LocalDate.now(), ZoneId.systemDefault()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun clear() = viewModelScope.launch { container.history.clear() }

    /** Only share exports that still exist — the user may have deleted some since. */
    suspend fun existingOutputs(entry: HistoryEntry): List<Uri> = withContext(Dispatchers.IO) {
        entry.outputUris.map(Uri::parse).filter(container.outputStore::exists)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel, onBack: () -> Unit) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = { Text(stringResource(R.string.history_clear_body)) },
            confirmButton = { TextButton(onClick = { viewModel.clear(); confirmClear = false }) { Text(stringResource(R.string.clear)) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    if (!groups.isNullOrEmpty()) {
                        IconButton(onClick = { confirmClear = true }) { Icon(Icons.Outlined.DeleteSweep, stringResource(R.string.history_clear_title)) }
                    }
                },
            )
        },
    ) { padding ->
        val list = groups
        if (list != null && list.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.history_empty_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.recent_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            list.orEmpty().forEach { group ->
                item(key = "h-${group.entries.first().id}") {
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
                        Text(
                            dayLabel(group.label),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f).semantics { heading() },
                        )
                        Text(
                            pluralStringResource(R.plurals.images_watermarked, group.savedImages, group.savedImages),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                items(group.entries, key = { it.id }) { entry -> EntryCard(entry, viewModel) }
            }
        }
    }
}

@Composable
private fun EntryCard(entry: HistoryEntry, viewModel: HistoryViewModel) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val time = remember(entry.finishedAt) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(entry.finishedAt)) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        pluralStringResource(R.plurals.photos_saved, entry.saved, entry.saved) +
                            if (entry.failed > 0) " · " + pluralStringResource(R.plurals.photos_failed, entry.failed, entry.failed) else "",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text("$time · ${entry.presetName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (entry.outputUris.isNotEmpty()) {
                    IconButton(onClick = {
                        scope.launch { Intents.shareImages(context, viewModel.existingOutputs(entry)) }
                    }) { Icon(Icons.Outlined.Share, stringResource(R.string.share)) }
                }
            }
            if (entry.outputUris.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    entry.outputUris.take(5).forEach { uri ->
                        Thumbnail(Uri.parse(uri), size = 48.dp)
                    }
                    if (entry.outputUris.size > 5) {
                        Text(
                            "+${entry.outputUris.size - 5}",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.align(Alignment.CenterVertically).padding(start = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
