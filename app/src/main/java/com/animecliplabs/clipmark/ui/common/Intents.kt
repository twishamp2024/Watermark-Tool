package com.animecliplabs.clipmark.ui.common

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.animecliplabs.clipmark.R

object Intents {
    /** Opens the system share sheet with every exported image (Instagram accepts multiple for a carousel). */
    fun shareImages(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }.apply {
            type = "image/*"
            clipData = ClipData.newRawUri(null, uris.first()).also { clip -> uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        start(context, Intent.createChooser(send, context.getString(R.string.share_title)))
    }

    fun viewImage(context: Context, uri: Uri) {
        start(
            context,
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }

    private fun start(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.error_no_app, Toast.LENGTH_SHORT).show()
        }
    }
}
