package com.animecliplabs.clipmark.data

import com.animecliplabs.clipmark.core.batch.BatchJob
import com.animecliplabs.clipmark.core.batch.JobSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The current batch as a JSON file, written atomically (temp file + rename) after every image.
 * The UI observes [job]; the worker writes through [save]. Both live in the same process.
 */
class BatchJobStore(private val dir: File) : JobSink {

    private val file get() = File(dir, "current_batch.json")
    private val mutex = Mutex()
    private val _job = MutableStateFlow<BatchJob?>(null)
    val job: StateFlow<BatchJob?> = _job.asStateFlow()
    @Volatile private var loaded = false

    suspend fun load(): BatchJob? = mutex.withLock {
        if (!loaded) {
            _job.value = withContext(Dispatchers.IO) {
                runCatching { AppJson.decodeFromString(BatchJob.serializer(), file.readText()) }.getOrNull()
            }
            loaded = true
        }
        _job.value
    }

    override suspend fun save(job: BatchJob) = mutex.withLock {
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            val tmp = File(dir, "current_batch.json.tmp")
            tmp.writeText(AppJson.encodeToString(BatchJob.serializer(), job))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
        loaded = true
        _job.value = job
    }

    suspend fun clear() = mutex.withLock {
        withContext(Dispatchers.IO) { file.delete() }
        loaded = true
        _job.value = null
    }
}
