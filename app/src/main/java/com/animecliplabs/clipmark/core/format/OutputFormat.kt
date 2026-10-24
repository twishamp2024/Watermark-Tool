package com.animecliplabs.clipmark.core.format

import com.animecliplabs.clipmark.core.model.OutputFormatPreference

enum class OutputFormat(val mimeType: String, val extension: String, val lossless: Boolean) {
    JPEG("image/jpeg", "jpg", lossless = false),
    PNG("image/png", "png", lossless = true),
    WEBP("image/webp", "webp", lossless = false),
}

object OutputFormatResolver {
    /**
     * Keep the original container whenever Android can encode it:
     *  - JPEG → JPEG, PNG → PNG (lossless), WebP → WebP.
     *  - GIF/BMP (and anything with transparency) → PNG, so nothing is flattened.
     *  - HEIC/AVIF/unknown → JPEG (Android has no general-purpose encoder for those).
     * A forced JPEG preference still falls back to PNG for transparent images, because JPEG
     * would silently turn transparency black.
     */
    fun resolve(sourceMime: String?, preference: OutputFormatPreference, hasAlpha: Boolean): OutputFormat {
        val mime = sourceMime?.lowercase()
        return when (preference) {
            OutputFormatPreference.PNG -> OutputFormat.PNG
            OutputFormatPreference.JPEG -> if (hasAlpha) OutputFormat.PNG else OutputFormat.JPEG
            OutputFormatPreference.MATCH_ORIGINAL -> when (mime) {
                "image/jpeg", "image/jpg", "image/pjpeg" -> OutputFormat.JPEG
                "image/png", "image/gif", "image/bmp", "image/x-ms-bmp" -> OutputFormat.PNG
                "image/webp" -> OutputFormat.WEBP
                else -> if (hasAlpha) OutputFormat.PNG else OutputFormat.JPEG
            }
        }
    }
}
