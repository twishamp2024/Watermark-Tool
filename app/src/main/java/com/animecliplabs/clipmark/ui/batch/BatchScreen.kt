package com.animecliplabs.clipmark.ui.batch

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.animecliplabs.clipmark.R
import com.animecliplabs.clipmark.core.batch.BatchJob
import com.animecliplabs.clipmark.core.batch.FailureKind
import com.animecliplabs.clipmark.core.batch.ItemNote
import com.animecliplabs.clipmark.core.batch.ItemStatus
import com.animecliplabs.clipmark.data.BatchState
import com.animecliplabs.clipmark.image.MediaStoreOutputStore
import com.animecliplabs.clipmark.ui.common.Intents
import com.animecliplabs.clipmark.ui.common.Thumbnail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchScreen(viewModel: BatchViewModel, onClose: () -> Unit, onProcessMore: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        val s = state
        val job = s?.job
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                s == null -> Unit
                job == null -> EmptyBatch(onClose)
                else -> AnimatedContent(
                    targetState = if (s.running) Phase.RUNNING else if (s.interrupted) Phase.PAUSED else Phase.DONE,
                    transitionSpec = { (fadeIn(tween(300)) + scaleIn(initialScale = 0.96f)) togetherWith fadeOut(tween(150)) },
                    label = "phase",
                ) { phase ->
                    when (phase) {
                        Phase.RUNNING -> Running(job, onCancel = viewModel::cancel)
                        Phase.PAUSED -> Paused(job, onResume = viewModel::resume, onDiscard = { viewModel.dismiss(onClose) })
                        Phase.DONE -> Done(
                            job = job,
                            onRetry = viewModel::retryUnsaved,
                            onProcessMore = { viewModel.dismiss(onProcessMore) },
                            onDone = { viewModel.dismiss(onClose) },
                        )
                    }
                }
            }
        }
    }
}

private enum class Phase { RUNNING, PAUSED, DONE }

@Composable
private fun Running(job: BatchJob, onCancel: () -> Unit) {
    val fraction by animateFloatAsState(
        targetValue = job.processedCount / job.total.coerceAtLeast(1).toFloat(),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "progress",
    )
    val current = job.items.firstOrNull { it.status == ItemStatus.PENDING }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(progress = { fraction }, modifier = Modifier.size(180.dp), strokeWidth = 10.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${(job.processedCount + 1).coerceAtMost(job.total)} / ${job.total}",
                    style = MaterialTheme.typography.displaySmall,
                )
                if (job.failedCount > 0) {
                    Text(
                        pluralStringResource(R.plurals.photos_failed, job.failedCount, job.failedCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(R.string.batch_processing_count, (job.processedCount + 1).coerceAtMost(job.total), job.total),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Text(
            current?.sourceName ?: stringResource(R.string.batch_with_preset, job.presetName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.batch_leave_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        OutlinedButton(onClick = onCancel, modifier = Modifier.height(48.dp)) { Text(stringResource(R.string.action_cancel)) }
    }
}

@Composable
private fun Paused(job: BatchJob, onResume: () -> Unit, onDiscard: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.batch_paused_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.batch_paused_body, job.savedCount, job.pendingCount),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        LinearProgressIndicator(progress = { job.processedCount / job.total.coerceAtLeast(1).toFloat() }, modifier = Modifier.fillMaxWidth(0.6f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onDiscard) { Text(stringResource(R.string.discard)) }
            Button(onClick = onResume) { Text(stringResource(R.string.resume)) }
        }
    }
}

@Composable
private fun Done(job: BatchJob, onRetry: () -> Unit, onProcessMore: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val outputs = remember(job) { job.items.mapNotNull { it.outputUri?.let(Uri::parse) } }
    val problems = remember(job) { job.items.filter { it.status != ItemStatus.DONE } }
    val notes = remember(job) { job.items.flatMap { it.notes }.toSet() }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 96.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                if (job.savedCount > 0) SuccessCheck() else Icon(
                    Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    when {
                        job.cancelled -> stringResource(R.string.batch_cancelled_title)
                        job.savedCount == 0 -> stringResource(R.string.batch_none_saved)
                        else -> stringResource(R.string.batch_done_title)
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Text(
                    pluralStringResource(R.plurals.photos_saved_to, job.savedCount, job.savedCount, MediaStoreOutputStore.RELATIVE_PATH.trimEnd('/')),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                if (outputs.isNotEmpty()) {
                    Button(
                        onClick = { Intents.shareImages(context, outputs) },
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(pluralStringResource(R.plurals.share_n, outputs.size, outputs.size), style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    if (outputs.isNotEmpty()) {
                        FilledTonalButton(onClick = { Intents.viewImage(context, outputs.first()) }, modifier = Modifier.weight(1f).height(48.dp)) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.open))
                        }
                    }
                    FilledTonalButton(onClick = onProcessMore, modifier = Modifier.weight(1f).height(48.dp)) {
                        Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.process_more), maxLines = 1)
                    }
                }
                TextButton(onClick = onDone, modifier = Modifier.padding(top = 4.dp)) { Text(stringResource(R.string.done)) }
            }
        }

        if (problems.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { ProblemsCard(job, problems.size, onRetry) }
        }
        if (notes.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { NotesCard(notes) }
        }
        items(outputs, key = { it.toString() }) { uri ->
            Thumbnail(
                uri = uri,
                size = 112.dp,
                contentDescription = stringResource(R.string.open),
                modifier = Modifier.aspectRatio(1f).clickable { Intents.viewImage(context, uri) },
            )
        }
    }
}

@Composable
private fun ProblemsCard(job: BatchJob, count: Int, onRetry: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                pluralStringResource(R.plurals.photos_not_saved, count, count),
                style = MaterialTheme.typography.titleSmall,
            )
            job.items.filter { it.status != ItemStatus.DONE }.take(6).forEach { item ->
                Text(
                    "• ${item.sourceName ?: stringResource(R.string.unnamed_photo)} — " + when (item.status) {
                        ItemStatus.FAILED -> failureText(item.failure ?: FailureKind.UNKNOWN)
                        ItemStatus.SKIPPED -> stringResource(R.string.skipped_storage)
                        else -> stringResource(R.string.skipped_cancelled)
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (count > 6) Text(stringResource(R.string.and_n_more, count - 6), style = MaterialTheme.typography.bodySmall)
            Button(onClick = onRetry, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(pluralStringResource(R.plurals.retry_n, count, count))
            }
        }
    }
}

@Composable
private fun NotesCard(notes: Set<ItemNote>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.padding(16.dp)) {
            Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                notes.forEach { note ->
                    Text(
                        stringResource(
                            when (note) {
                                ItemNote.DOWNSCALED_FOR_MEMORY -> R.string.note_downscaled
                                ItemNote.METADATA_NOT_COPIED -> R.string.note_metadata
                                ItemNote.SHRUNK_TO_FIT -> R.string.note_shrunk
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/** A circle that pops in, then a check that draws itself. */
@Composable
private fun SuccessCheck() {
    val scale = remember { Animatable(0.6f) }
    val draw = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(Unit) { draw.animateTo(1f, tween(durationMillis = 450, delayMillis = 150)) }
    val circle = MaterialTheme.colorScheme.primaryContainer
    val tick = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(88.dp).scale(scale.value)) {
        drawCircle(circle)
        val path = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.52f)
            lineTo(size.width * 0.44f, size.height * 0.67f)
            lineTo(size.width * 0.73f, size.height * 0.36f)
        }
        val measure = PathMeasure().apply { setPath(path, false) }
        val partial = Path()
        measure.getSegment(0f, measure.length * draw.value, partial, true)
        drawPath(partial, tick, style = Stroke(width = size.width * 0.08f, cap = StrokeCap.Round))
    }
}

@Composable
private fun EmptyBatch(onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.batch_empty), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onClose) { Text(stringResource(R.string.back)) }
    }
}

@Composable
fun failureText(kind: FailureKind): String = stringResource(
    when (kind) {
        FailureKind.UNREADABLE -> R.string.fail_unreadable
        FailureKind.UNSUPPORTED -> R.string.fail_unsupported
        FailureKind.ACCESS_LOST -> R.string.fail_access
        FailureKind.NOT_FOUND -> R.string.fail_not_found
        FailureKind.TOO_LARGE -> R.string.fail_too_large
        FailureKind.STORAGE_FULL -> R.string.fail_storage
        FailureKind.WRITE_FAILED -> R.string.fail_write
        FailureKind.UNKNOWN -> R.string.fail_unknown
    },
)

