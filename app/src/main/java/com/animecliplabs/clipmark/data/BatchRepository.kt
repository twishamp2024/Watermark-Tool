package com.animecliplabs.clipmark.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.animecliplabs.clipmark.core.batch.BatchItem
import com.animecliplabs.clipmark.core.batch.BatchJob
import com.animecliplabs.clipmark.core.batch.ItemStatus
import com.animecliplabs.clipmark.core.model.Anchor
import com.animecliplabs.clipmark.core.model.WatermarkStyle
import com.animecliplabs.clipmark.image.ImageLoader
import com.animecliplabs.clipmark.work.BatchWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

data class BatchRequest(
    val uris: List<Uri>,
    val presetName: String,
    val style: WatermarkStyle,
    val overrides: Map<Int, WatermarkStyle> = emptyMap(),
    val resolvedAnchors: Map<Int, Anchor> = emptyMap(),
)

data class BatchState(val job: BatchJob?, val running: Boolean) {
    /** A batch that stopped without finishing (process killed and not rescheduled). */
    val interrupted: Boolean get() = job != null && !job.isFinished && !running
}

class BatchRepository(
    context: Context,
    private val jobStore: BatchJobStore,
    private val settings: SettingsRepository,
    private val loader: ImageLoader,
) {
    private val appContext = context.applicationContext
    private val workManager get() = WorkManager.getInstance(appContext)

    /** Jobs enqueued by this process — covers the short gap before WorkManager reports ENQUEUED. */
    private val launched = MutableStateFlow<Set<String>>(emptySet())

    private val workActive: Flow<Boolean> =
        workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_WORK).map { infos -> infos.any { !it.state.isFinished } }

    val state: Flow<BatchState> = combine(jobStore.job, workActive, launched) { job, active, launchedIds ->
        BatchState(job, running = job != null && !job.isFinished && (active || job.id in launchedIds))
    }

    suspend fun restore() {
        jobStore.load()
    }

    /** @return false if another batch is still running. */
    suspend fun start(request: BatchRequest): Boolean {
        if (state.first().running) return false
        request.uris.forEach(::persistReadAccess)
        val names = withContext(Dispatchers.IO) { request.uris.map { loader.meta(it).displayName } }
        val job = BatchJob(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            presetName = request.presetName,
            style = request.style,
            output = settings.current(),
            items = request.uris.mapIndexed { i, uri ->
                BatchItem(
                    sourceUri = uri.toString(),
                    sourceName = names[i],
                    styleOverride = request.overrides[i],
                    resolvedAnchor = request.resolvedAnchors[i],
                )
            },
        )
        jobStore.save(job)
        enqueue(job.id)
        return true
    }

    /** Re-runs everything that did not save (failed, skipped for storage, or cancelled). */
    suspend fun retryUnsaved(): Boolean {
        val previous = jobStore.load() ?: return false
        val retry = previous.items.filter { it.status != ItemStatus.DONE }
        if (retry.isEmpty() || state.first().running) return false
        val job = previous.copy(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            items = retry.map { it.copy(status = ItemStatus.PENDING, failure = null, notes = emptyList(), outputUri = null, outputName = null) },
            finishedAt = null,
            cancelled = false,
        )
        jobStore.save(job)
        enqueue(job.id)
        return true
    }

    /** Continues an interrupted batch from where it stopped. Already-saved images are skipped. */
    suspend fun resume() {
        val job = jobStore.load() ?: return
        if (!job.isFinished) enqueue(job.id)
    }

    /** Read by the worker to tell a user cancel apart from the system stopping it. */
    @Volatile
    var cancelRequested = false
        private set

    fun cancel() {
        cancelRequested = true
        workManager.cancelUniqueWork(UNIQUE_WORK)
    }

    suspend fun dismiss() {
        if (!state.first().running) jobStore.clear()
    }

    private fun enqueue(jobId: String) {
        cancelRequested = false
        launched.value = launched.value + jobId
        val request = OneTimeWorkRequestBuilder<BatchWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.KEEP, request)
    }

    /**
     * Photo-picker grants normally end with the process. Persisting them lets a batch resume
     * after the app is killed. Not every source supports it (e.g. some share intents) — fine.
     */
    private fun persistReadAccess(uri: Uri) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Give the grants back once a batch is done; Android caps how many an app may hold. */
    fun releaseReadAccess(job: BatchJob) {
        val persisted = appContext.contentResolver.persistedUriPermissions.map { it.uri.toString() }.toSet()
        job.items.filter { it.sourceUri in persisted }.forEach {
            runCatching {
                appContext.contentResolver.releasePersistableUriPermission(Uri.parse(it.sourceUri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    companion object {
        const val UNIQUE_WORK = "watermark-batch"
    }
}
