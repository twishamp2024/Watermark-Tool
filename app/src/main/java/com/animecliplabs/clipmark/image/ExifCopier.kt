package com.animecliplabs.clipmark.image

import android.content.ContentResolver
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.animecliplabs.clipmark.core.metadata.MetadataPolicy
import com.animecliplabs.clipmark.core.model.OutputSettings
import java.io.File

/**
 * Copies a whitelist of EXIF tags (see [MetadataPolicy]) from the original into the encoded
 * export. `Bitmap.compress` writes no EXIF of its own, so anything not copied here is gone.
 */
class ExifCopier(private val resolver: ContentResolver) {

    /** @return false if metadata could not be written (the image itself is still fine). */
    fun copy(source: Uri, target: File, settings: OutputSettings, width: Int, height: Int): Boolean {
        val tags = MetadataPolicy.tagsToCopy(settings)
        if (tags.isEmpty()) return true
        return try {
            val src = resolver.openInputStream(source)?.use { ExifInterface(it) } ?: return true
            val values = tags.mapNotNull { tag -> src.getAttribute(tag)?.let { tag to it } }
            if (values.isEmpty()) return true // e.g. screenshots: nothing worth copying

            val dst = ExifInterface(target)
            values.forEach { (tag, value) -> dst.setAttribute(tag, value) }
            dst.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            dst.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, width.toString())
            dst.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, height.toString())
            dst.saveAttributes()
            true
        } catch (e: Exception) {
            false
        }
    }
}
