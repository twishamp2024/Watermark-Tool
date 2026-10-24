package com.animecliplabs.clipmark.core.batch

import com.animecliplabs.clipmark.core.model.OutputSettings
import com.animecliplabs.clipmark.core.model.WatermarkStyle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchRunnerTest {

    private fun job(n: Int) = BatchJob(
        id = "job", createdAt = 0, presetName = "AnimeClips Default", style = WatermarkStyle(), output = OutputSettings(),
        items = (1..n).map { BatchItem("content://photos/$it", "img$it.jpg") },
    )

    private class RecordingSink : JobSink {
        val saves = mutableListOf<BatchJob>()
        override suspend fun save(job: BatchJob) { saves += job }
    }

    private fun processor(fail: Map<Int, FailureKind> = emptyMap(), seen: MutableList<String> = mutableListOf()) =
        ItemProcessor { _, item, reserved ->
            seen += item.sourceUri
            val n = item.sourceUri.substringAfterLast('/').toInt()
            fail[n]?.let { return@ItemProcessor ItemOutcome.Failed(it) }
            val name = "img${n}_watermarked.jpg"
            reserved += name
            ItemOutcome.Saved("content://out/$n", name)
        }

    @Test
    fun `fifteen photos, one bad - the other fourteen are saved`() = runTest {
        val sink = RecordingSink()
        val result = BatchRunner(processor(mapOf(7 to FailureKind.UNREADABLE)), sink, clock = { 99 }).run(job(15))
        assertEquals(14, result.savedCount)
        assertEquals(1, result.failedCount)
        assertEquals(FailureKind.UNREADABLE, result.items[6].failure)
        assertEquals(99L, result.finishedAt)
        assertFalse(result.cancelled)
    }

    @Test
    fun `progress is persisted after every image, so nothing is lost on a crash`() = runTest {
        val sink = RecordingSink()
        BatchRunner(processor(), sink).run(job(5))
        // one save per item + the final "finished" save
        assertEquals(6, sink.saves.size)
        assertEquals((1..5).toList(), sink.saves.take(5).map { it.savedCount })
    }

    @Test
    fun `a processor exception becomes a per-item failure, not a crashed batch`() = runTest {
        val throwing = ItemProcessor { _, item, _ ->
            if (item.sourceUri.endsWith("/2")) error("boom") else ItemOutcome.Saved("u", "n")
        }
        val result = BatchRunner(throwing, RecordingSink()).run(job(3))
        assertEquals(2, result.savedCount)
        assertEquals(FailureKind.UNKNOWN, result.items[1].failure)
    }

    @Test
    fun `resuming skips images that are already done`() = runTest {
        val seen = mutableListOf<String>()
        val half = job(4).let { j ->
            j.withItem(0, j.items[0].copy(status = ItemStatus.DONE, outputUri = "x", outputName = "img1_watermarked.jpg"))
                .let { it.withItem(1, it.items[1].copy(status = ItemStatus.DONE, outputUri = "y", outputName = "img2_watermarked.jpg")) }
        }
        val result = BatchRunner(processor(seen = seen), RecordingSink()).run(half)
        assertEquals(listOf("content://photos/3", "content://photos/4"), seen)
        assertEquals(4, result.savedCount)
    }

    @Test
    fun `names already used by the batch are reserved on resume`() = runTest {
        var reservedAtStart: Set<String> = emptySet()
        val half = job(2).let { it.withItem(0, it.items[0].copy(status = ItemStatus.DONE, outputName = "Img1_Watermarked.jpg")) }
        val p = ItemProcessor { _, _, reserved -> reservedAtStart = reserved.toSet(); ItemOutcome.Saved("u", "n") }
        BatchRunner(p, RecordingSink()).run(half)
        assertTrue("img1_watermarked.jpg" in reservedAtStart)
    }

    @Test
    fun `a full disk stops early and leaves the rest retryable`() = runTest {
        val seen = mutableListOf<String>()
        val result = BatchRunner(processor(mapOf(3 to FailureKind.STORAGE_FULL), seen), RecordingSink()).run(job(6))
        assertEquals(3, seen.size)
        assertEquals(2, result.savedCount)
        assertEquals(ItemStatus.FAILED, result.items[2].status)
        assertTrue(result.items.drop(3).all { it.status == ItemStatus.SKIPPED })
        assertTrue(result.stoppedForStorage)
    }

    @Test
    fun `user cancel keeps saved photos and marks the rest cancelled`() = runTest {
        val sink = RecordingSink()
        val gate = CompletableDeferred<Unit>()
        val p = ItemProcessor { _, item, _ ->
            if (item.sourceUri.endsWith("/3")) gate.await() // hang on the 3rd image
            ItemOutcome.Saved(item.sourceUri, "n")
        }
        val running = async { BatchRunner(p, sink, isUserCancel = { true }).run(job(5)) }
        testScheduler.advanceUntilIdle()
        running.cancel()
        testScheduler.advanceUntilIdle()

        val last = sink.saves.last()
        assertTrue(last.cancelled)
        assertTrue(last.isFinished)
        assertEquals(2, last.savedCount)
        assertEquals(3, last.items.count { it.status == ItemStatus.CANCELLED })
    }

    @Test
    fun `a system stop leaves the batch unfinished so it can resume`() = runTest {
        val sink = RecordingSink()
        val gate = CompletableDeferred<Unit>()
        val p = ItemProcessor { _, item, _ ->
            if (item.sourceUri.endsWith("/2")) gate.await()
            ItemOutcome.Saved(item.sourceUri, "n")
        }
        val running = async { BatchRunner(p, sink, isUserCancel = { false }).run(job(4)) }
        testScheduler.advanceUntilIdle()
        running.cancel()
        testScheduler.advanceUntilIdle()

        val last = sink.saves.last()
        assertFalse(last.cancelled)
        assertNull(last.finishedAt)
        assertEquals(3, last.pendingCount)
    }

    @Test
    fun `progress reports the current file`() = runTest {
        val progress = mutableListOf<BatchProgress>()
        BatchRunner(processor(), RecordingSink()).run(job(2)) { progress += it }
        assertEquals("img1.jpg", progress[0].currentName)
        assertEquals(0, progress[0].processed)
        assertEquals(BatchProgress(2, 2, 0, null), progress.last())
    }
}
