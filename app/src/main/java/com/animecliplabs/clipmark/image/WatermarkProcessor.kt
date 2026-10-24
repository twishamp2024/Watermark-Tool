package com.animecliplabs.clipmark.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.StatFs
import com.animecliplabs.clipmark.core.batch.BatchItem
import com.animecliplabs.clipmark.core.batch.BatchJob
import com.animecliplabs.clipmark.core.batch.FailureKind
import com.animecliplabs.clipmark.core.batch.ItemNote
import com.animecliplabs.clipmark.core.batch.ItemOutcome
import com.animecliplabs.clipmark.core.batch.ItemProcessor
import com.animecliplabs.clipmark.core.format.OutputFormat
import com.animecliplabs.clipmark.core.format.OutputFormatResolver
import com.animecliplabs.clipmark.core.naming.FileNamer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Pipeline for one image:
 *   decode full-res (upright) → decide position/readability → draw watermark → encode to a
 *   private temp file → copy whitelisted EXIF → publish to MediaStore under a unique name.
 * The original is only ever opened for reading.
 */
class WatermarkProcessor(
    private val loader: ImageLoader,
    private val renderer: WatermarkRenderer,
    private val analyzer: WatermarkAnalyzer,
    private val exif: ExifCopier,
    private val store: OutputStore,
    private val tempDir: File,
) : ItemProcessor {

    override suspend fun process(job: BatchJob, item: BatchItem, reservedNames: MutableSet<String>): ItemOutcome =
        withContext(Dispatchers.IO) { processBlocking(job, item, reservedNames) }

    fun cleanTempFiles() {
        tempDir.listFiles()?.forEach { it.delete() }
    }

    private fun processBlocking(job: BatchJob, item: BatchItem, reserved: MutableSet<String>): ItemOutcome {
        val uri = Uri.parse(item.sourceUri)
        val style = job.styleFor(item)
        val notes = mutableListOf<ItemNote>()

        val image = try {
            loader.decodeFull(uri)
        } catch (t: Throwable) {
            return ItemOutcome.Failed(classifyRead(t))
        }

        val bitmap = image.bitmap
        val width = bitmap.width
        val height = bitmap.height
        var temp: File? = null
        try {
            if (image.downscaled) notes += ItemNote.DOWNSCALED_FOR_MEMORY

            analyzer.decide(bitmap, style, item.resolvedAnchor)?.let { decision ->
                renderer.draw(Canvas(bitmap), decision.placement, style, decision.readability)
                if (decision.placement.shrunkToFit) notes += ItemNote.SHRUNK_TO_FIT
            }

            val format = OutputFormatResolver.resolve(image.mimeType, job.output.format, bitmap.hasAlpha())
            if (!hasRoomFor(width, height, format)) return ItemOutcome.Failed(FailureKind.STORAGE_FULL)

            tempDir.mkdirs()
            val file = File(tempDir, "${UUID.randomUUID()}.${format.extension}").also { temp = it }
            try {
                encode(bitmap, format, job.output.quality, file)
            } catch (e: IOException) {
                return ItemOutcome.Failed(classifyWrite(e))
            }
            bitmap.recycle() // free pixels before the metadata/copy steps

            if (!exif.copy(uri, file, job.output, width, height)) notes += ItemNote.METADATA_NOT_COPIED

            val sourceName = item.sourceName ?: loader.meta(uri).displayName
            val base = FileNamer.baseName(sourceName, fallback = "animecliplabs_" + timestamp())
            val name = FileNamer.uniqueName(base, format.extension) { candidate ->
                candidate.lowercase() in reserved || store.isTaken(candidate)
            }
            reserved += name.lowercase()

            val saved = try {
                store.save(file, name, format.mimeType)
            } catch (e: Exception) {
                return ItemOutcome.Failed(classifyWrite(e))
            }
            reserved += saved.displayName.lowercase()
            return ItemOutcome.Saved(saved.uri.toString(), saved.displayName, notes)
        } catch (oom: OutOfMemoryError) {
            return ItemOutcome.Failed(FailureKind.TOO_LARGE)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
            temp?.delete()
        }
    }

    private fun encode(bitmap: Bitmap, format: OutputFormat, quality: Int, file: File) {
        val compressFormat = when (format) {
            OutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
            OutputFormat.PNG -> Bitmap.CompressFormat.PNG
            OutputFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
            }
        }
        val ok = file.outputStream().buffered(BUFFER).use { bitmap.compress(compressFormat, quality, it) }
        if (!ok) throw IOException("Encoder rejected the image")
    }

    /** Temp file + MediaStore copy, with a safety margin. Pessimistic on purpose. */
    private fun hasRoomFor(width: Int, height: Int, format: OutputFormat): Boolean {
        val estimate = width.toLong() * height * (if (format == OutputFormat.PNG) 3 else 1)
        val available = runCatching { StatFs(tempDir.parentFile?.path ?: tempDir.path).availableBytes }.getOrDefault(Long.MAX_VALUE)
        return available > estimate * 2 + RESERVE_BYTES
    }

    private fun timestamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    companion object {
        private const val BUFFER = 256 * 1024
        private const val RESERVE_BYTES = 32L * 1024 * 1024

        fun classifyRead(t: Throwable): FailureKind = when (t) {
            is SecurityException -> FailureKind.ACCESS_LOST
            is FileNotFoundException -> FailureKind.NOT_FOUND
            is OutOfMemoryError -> FailureKind.TOO_LARGE
            is ImageDecoder.DecodeException -> FailureKind.UNREADABLE
            is IOException ->
                if (t.message?.contains("unimplemented", ignoreCase = true) == true) FailureKind.UNSUPPORTED
                else FailureKind.UNREADABLE
            else -> FailureKind.UNKNOWN
        }

        fun classifyWrite(t: Throwable): FailureKind {
            val msg = generateSequence(t) { it.cause }.mapNotNull { it.message }.joinToString(" ")
            return if (msg.contains("ENOSPC") || msg.contains("No space", ignoreCase = true)) FailureKind.STORAGE_FULL
            else FailureKind.WRITE_FAILED
        }
    }
}
