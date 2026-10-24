package com.animecliplabs.clipmark

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.preferencesDataStore
import com.animecliplabs.clipmark.data.BatchJobStore
import com.animecliplabs.clipmark.data.BatchRepository
import com.animecliplabs.clipmark.data.HistoryRepository
import com.animecliplabs.clipmark.data.PresetRepository
import com.animecliplabs.clipmark.data.SettingsRepository
import com.animecliplabs.clipmark.image.ExifCopier
import com.animecliplabs.clipmark.image.ImageLoader
import com.animecliplabs.clipmark.image.MediaStoreOutputStore
import com.animecliplabs.clipmark.image.WatermarkAnalyzer
import com.animecliplabs.clipmark.image.WatermarkProcessor
import com.animecliplabs.clipmark.image.WatermarkRenderer
import com.animecliplabs.clipmark.work.BatchNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

private val Context.dataStore by preferencesDataStore(name = "clipmark")

class ClipMarkApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifications.createChannels()
        container.appScope.launch { container.batches.restore() }
    }
}

/**
 * Hand-rolled dependency graph. The app is small enough that a DI framework would add more
 * ceremony than it removes.
 */
class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val presets = PresetRepository(app.dataStore)
    val settings = SettingsRepository(app.dataStore)
    val history = HistoryRepository(app.dataStore)

    val renderer = WatermarkRenderer()
    val analyzer = WatermarkAnalyzer(renderer)
    val imageLoader = ImageLoader(app)
    val outputStore = MediaStoreOutputStore(app)
    val processor = WatermarkProcessor(
        loader = imageLoader,
        renderer = renderer,
        analyzer = analyzer,
        exif = ExifCopier(app.contentResolver),
        store = outputStore,
        tempDir = File(app.cacheDir, "export"),
    )

    val jobStore = BatchJobStore(File(app.filesDir, "batch"))
    val batches = BatchRepository(app, jobStore, settings, imageLoader)
    val notifications = BatchNotifications(app)

    /** Photos picked (or shared in) waiting to be opened by the editor. */
    val selection = SelectionHolder()
}

class SelectionHolder {
    @Volatile private var pending: List<Uri> = emptyList()

    fun offer(uris: List<Uri>) {
        pending = uris
    }

    fun take(): List<Uri> = pending.also { pending = emptyList() }
}
