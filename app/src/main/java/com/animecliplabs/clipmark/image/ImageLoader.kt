package com.animecliplabs.clipmark.image

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.util.LruCache
import android.util.Size
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * All decoding goes through [ImageDecoder], which applies EXIF orientation while decoding. The
 * pixels we draw on are therefore already upright — exactly what a gallery shows — and the
 * export is written with orientation "normal", so nothing can end up double-rotated.
 */
class ImageLoader(context: Context) {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    data class SourceMeta(val displayName: String?, val sizeBytes: Long?)

    data class FullImage(
        val bitmap: Bitmap,
        /** MIME type detected from the file header (more trustworthy than the file name). */
        val mimeType: String?,
        /** Upright size of the original. Equal to the bitmap size unless [downscaled]. */
        val originalWidth: Int,
        val originalHeight: Int,
        val downscaled: Boolean,
    )

    /**
     * Original file name, used to name the export. Providers differ in which columns they accept
     * (the Photo Picker rejects some projections), so ask for everything and read what exists.
     * Never falls back to the URI's last segment — that is usually a numeric id, not a name.
     */
    fun meta(uri: Uri): SourceMeta {
        if (uri.scheme == "file") return SourceMeta(uri.lastPathSegment, null)
        // Photo Picker URIs deliberately hide the real file name (it reports e.g. "41.jpg", its own
        // item id). Using it would produce meaningless "41_watermarked.jpg" names, so treat it as unknown.
        val opaqueName = uri.authority == MediaStore.AUTHORITY && "picker" in uri.pathSegments
        return try {
            resolver.query(uri, null, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                SourceMeta(
                    displayName = if (!opaqueName && nameIdx >= 0 && !c.isNull(nameIdx)) c.getString(nameIdx) else null,
                    sizeBytes = if (sizeIdx >= 0 && !c.isNull(sizeIdx)) c.getLong(sizeIdx) else null,
                )
            } ?: SourceMeta(null, null)
        } catch (e: Exception) {
            Log.w(TAG, "No display name for $uri", e)
            SourceMeta(null, null)
        }
    }

    /** A screen-sized, software bitmap for the editor (software so it can be analysed). */
    fun decodePreview(uri: Uri, maxDimension: Int = PREVIEW_MAX_DIMENSION): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val scale = min(1f, maxDimension.toFloat() / max(w, h))
            if (scale < 1f) decoder.setTargetSize((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1))
        }

    /**
     * Full-resolution decode for export. Only if the image cannot fit in the memory budget is it
     * reduced (reported to the user as a note — never silently).
     */
    fun decodeFull(uri: Uri, maxPixels: Long = pixelBudget()): FullImage {
        return try {
            decodeFullOnce(uri, maxPixels)
        } catch (oom: OutOfMemoryError) {
            System.gc()
            decodeFullOnce(uri, maxPixels / 4)
        }
    }

    private fun decodeFullOnce(uri: Uri, maxPixels: Long): FullImage {
        var mime: String? = null
        var originalW = 0
        var originalH = 0
        var downscaled = false
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            mime = info.mimeType
            originalW = info.size.width
            originalH = info.size.height
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true // we draw directly onto the decoded pixels
            val pixels = originalW.toLong() * originalH
            if (pixels > maxPixels) {
                val s = sqrt(maxPixels.toDouble() / pixels)
                decoder.setTargetSize((originalW * s).toInt().coerceAtLeast(1), (originalH * s).toInt().coerceAtLeast(1))
                downscaled = true
            }
        }
        return FullImage(bitmap, mime, originalW, originalH, downscaled)
    }

    /** How many ARGB pixels we allow in memory at once — ~35 % of currently available RAM, capped at 512 MB. */
    fun pixelBudget(): Long {
        val am = appContext.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val bytes = min((info.availMem * 0.35).toLong(), 512L * 1024 * 1024)
        return max(bytes / 4, MIN_BUDGET_PIXELS)
    }

    private val thumbnails = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    fun cachedThumbnail(uri: Uri, sizePx: Int): Bitmap? = thumbnails.get("$uri@$sizePx")

    /** Small, cached thumbnail. Uses the provider's own thumbnail when it has one. */
    fun thumbnail(uri: Uri, sizePx: Int): Bitmap? {
        val key = "$uri@$sizePx"
        thumbnails.get(key)?.let { return it }
        val bmp = runCatching { resolver.loadThumbnail(uri, Size(sizePx, sizePx), null) }
            .recoverCatching { decodePreview(uri, sizePx) }
            .getOrNull() ?: return null
        thumbnails.put(key, bmp)
        return bmp
    }

    companion object {
        private const val TAG = "ImageLoader"
        const val PREVIEW_MAX_DIMENSION = 2048
        private const val MIN_BUDGET_PIXELS = 12_000_000L
    }
}
