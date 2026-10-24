package com.animecliplabs.clipmark

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.IntentCompat
import com.animecliplabs.clipmark.ui.AppEvent
import com.animecliplabs.clipmark.ui.ClipMarkNavHost
import com.animecliplabs.clipmark.ui.common.LocalAppContainer
import com.animecliplabs.clipmark.ui.theme.ClipMarkTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class MainActivity : ComponentActivity() {

    private val events = Channel<AppEvent>(Channel.BUFFERED)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ClipMarkApplication).container
        if (savedInstanceState == null) handle(intent)
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                ClipMarkTheme {
                    ClipMarkNavHost(events.receiveAsFlow())
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent == null) return
        val shared = sharedImages(intent)
        when {
            shared.isNotEmpty() -> {
                (application as ClipMarkApplication).container.selection.offer(shared)
                events.trySend(AppEvent.OpenEditor)
            }
            intent.getBooleanExtra(EXTRA_OPEN_BATCH, false) -> events.trySend(AppEvent.OpenBatch)
        }
    }

    private fun sharedImages(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND ->
            listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        Intent.ACTION_SEND_MULTIPLE ->
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else -> emptyList()
    }

    companion object {
        const val EXTRA_OPEN_BATCH = "open_batch"
    }
}
