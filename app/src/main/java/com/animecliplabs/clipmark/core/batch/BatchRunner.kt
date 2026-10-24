package com.animecliplabs.clipmark.core.batch

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Processes one image. Implementations must not throw for per-image problems; return [ItemOutcome.Failed]. */
fun interface ItemProcessor {
    /**
     * @param reservedNames output names already claimed in this batch (lower-case), so two
     *   originals called `IMG_0001.jpg` from different folders never collide.
     */
    suspend fun process(job: BatchJob, item: BatchItem, reservedNames: MutableSet<String>): ItemOutcome
}

fun interface JobSink {
    suspend fun save(job: BatchJob)
}

/**
 * Sequential batch loop. One image in memory at a time; state persisted after each image.
 *  - A failure affects only that image.
 *  - A full disk stops the batch early (every later write would fail too); remaining items are SKIPPED
 *    so they can be retried after freeing space.
 *  - A user cancel keeps everything already saved and marks the rest CANCELLED.
 *  - Any other stop (process death, system quota) leaves the job unfinished so it resumes later.
 */
class BatchRunner(
    private val processor: ItemProcessor,
    private val sink: JobSink,
    private val isUserCancel: () -> Boolean = { true },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(job: BatchJob, onProgress: suspend (BatchProgress) -> Unit = {}): BatchJob {
        var current = job
        val reserved = job.items.mapNotNullTo(mutableSetOf()) { it.outputName?.lowercase() }
        try {
            for (index in current.items.indices) {
                val item = current.items[index]
                if (item.status != ItemStatus.PENDING) continue

                onProgress(BatchProgress(current.processedCount, current.total, current.failedCount, item.sourceName))

                val outcome = try {
                    processor.process(current, item, reserved)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    ItemOutcome.Failed(FailureKind.UNKNOWN)
                }

                current = current.withItem(index, item.applying(outcome))
                if (outcome is ItemOutcome.Failed && outcome.kind == FailureKind.STORAGE_FULL) {
                    current = current.markPending(ItemStatus.SKIPPED)
                }
                sink.save(current)
                if (current.stoppedForStorage) break
            }
            current = current.copy(finishedAt = clock())
            sink.save(current)
            onProgress(BatchProgress(current.processedCount, current.total, current.failedCount, null))
            return current
        } catch (e: CancellationException) {
            val snapshot = if (isUserCancel()) {
                current.markPending(ItemStatus.CANCELLED).copy(finishedAt = clock(), cancelled = true)
            } else {
                current
            }
            withContext(NonCancellable) { sink.save(snapshot) }
            throw e
        }
    }

    private fun BatchItem.applying(outcome: ItemOutcome): BatchItem = when (outcome) {
        is ItemOutcome.Saved -> copy(
            status = ItemStatus.DONE, outputUri = outcome.uri, outputName = outcome.name,
            failure = null, notes = outcome.notes,
        )
        is ItemOutcome.Failed -> copy(status = ItemStatus.FAILED, failure = outcome.kind)
    }
}
