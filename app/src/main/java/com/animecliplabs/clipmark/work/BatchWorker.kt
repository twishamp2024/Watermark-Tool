package com.animecliplabs.clipmark.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.animecliplabs.clipmark.ClipMarkApplication
import com.animecliplabs.clipmark.core.batch.BatchJob
import com.animecliplabs.clipmark.core.batch.BatchProgress
import com.animecliplabs.clipmark.core.batch.BatchRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Runs the current batch as a foreground job, so it keeps going while the user switches to
 * Instagram. The job file makes it restartable: WorkManager re-runs this worker after a crash
 * or process death and the runner skips images that are already saved.
 */
class BatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val container = (context.applicationContext as ClipMarkApplication).container
    private val notifications = container.notifications

    override suspend fun getForegroundInfo(): ForegroundInfo =
        notifications.foregroundInfo(id, BatchProgress(0, 0, 0, null))

    override suspend fun doWork(): Result {
        val job = container.jobStore.load() ?: return Result.success()
        if (job.isFinished) return Result.success()

        container.processor.cleanTempFiles()
        promoteToForeground(BatchProgress(job.processedCount, job.total, job.failedCount, null))

        val runner = BatchRunner(
            processor = container.processor,
            sink = container.jobStore,
            isUserCancel = { container.batches.cancelRequested || stopReason == WorkInfo.STOP_REASON_CANCELLED_BY_APP },
        )
        val finished = try {
            runner.run(job) { progress ->
                setProgress(workDataOf(KEY_PROCESSED to progress.processed, KEY_TOTAL to progress.total))
                promoteToForeground(progress)
            }
        } catch (e: CancellationException) {
            // Only a user cancel (not a system stop) leaves a finished job behind; record it.
            withContext(NonCancellable) {
                container.jobStore.job.value?.takeIf { it.isFinished }?.let { wrapUp(it) }
            }
            throw e
        }
        wrapUp(finished)
        return Result.success()
    }

    private suspend fun wrapUp(job: BatchJob) {
        container.history.record(job)
        container.batches.releaseReadAccess(job)
        notifications.showFinished(job)
    }

    private suspend fun promoteToForeground(progress: BatchProgress) {
        try {
            setForeground(notifications.foregroundInfo(id, progress))
        } catch (e: IllegalStateException) {
            // Background start not allowed right now (Android 12+). The work still runs; it just
            // has no ongoing notification.
        }
    }

    companion object {
        const val KEY_PROCESSED = "processed"
        const val KEY_TOTAL = "total"
    }
}
