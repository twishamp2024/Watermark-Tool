package com.animecliplabs.clipmark.image

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException

data class SavedOutput(val uri: Uri, val displayName: String)

interface OutputStore {
    fun isTaken(displayName: String): Boolean
    fun save(file: File, displayName: String, mimeType: String): SavedOutput
}

/**
 * Writes into `Pictures/AnimeClips Watermarked/` through MediaStore: visible in every gallery,
 * no storage permission needed (API 29+), and never touches the originals.
 */
class MediaStoreOutputStore(context: Context) : OutputStore {

    private val resolver = context.applicationContext.contentResolver
    private val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    override fun isTaken(displayName: String): Boolean = runCatching {
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf(RELATIVE_PATH, displayName),
            null,
        )?.use { it.count > 0 } ?: false
    }.getOrDefault(false)

    override fun save(file: File, displayName: String, mimeType: String): SavedOutput {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
            // Hidden from other apps until fully written: no half-written images in the gallery.
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("Could not create the output file")
        try {
            val out = resolver.openOutputStream(uri, "w") ?: throw IOException("Could not open the output file")
            out.use { stream -> file.inputStream().use { it.copyTo(stream, BUFFER) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
        // MediaStore may have adjusted the name if something outside our view already used it.
        val finalName = runCatching {
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: displayName
        return SavedOutput(uri, finalName)
    }

    fun exists(uri: Uri): Boolean = runCatching {
        resolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { it.count > 0 } ?: false
    }.getOrDefault(false)

    companion object {
        const val FOLDER_NAME = "AnimeClips Watermarked"
        val RELATIVE_PATH = "${Environment.DIRECTORY_PICTURES}/$FOLDER_NAME/"
        private const val BUFFER = 256 * 1024
    }
}
