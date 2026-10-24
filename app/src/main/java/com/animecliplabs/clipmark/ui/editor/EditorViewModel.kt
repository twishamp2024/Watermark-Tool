package com.animecliplabs.clipmark.ui.editor

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.animecliplabs.clipmark.AppContainer
import com.animecliplabs.clipmark.core.analysis.Readability
import com.animecliplabs.clipmark.core.analysis.ReadabilityResolver
import com.animecliplabs.clipmark.core.batch.FailureKind
import com.animecliplabs.clipmark.core.layout.Snapping
import com.animecliplabs.clipmark.core.model.Anchor
import com.animecliplabs.clipmark.core.model.Preset
import com.animecliplabs.clipmark.core.model.WatermarkStyle
import com.animecliplabs.clipmark.data.BatchRequest
import com.animecliplabs.clipmark.image.WatermarkProcessor
import com.animecliplabs.clipmark.ui.EditorRoute
import com.animecliplabs.clipmark.ui.common.SampleArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class EditorMode { PHOTOS, EDIT_PRESET, NEW_PRESET }

sealed interface PreviewState {
    data object Loading : PreviewState
    data class Ready(val bitmap: Bitmap) : PreviewState
    data class Failed(val kind: FailureKind) : PreviewState
}

/** Smart Corner / Contrast Assist result for one image, tied to the style it was computed for. */
data class Analysis(val style: WatermarkStyle, val smartAnchor: Anchor?, val backgroundLuminance: Float?)

data class Guides(val x: Float?, val y: Float?, val snapped: Anchor?)

sealed interface EditorMessage {
    data object PresetUpdated : EditorMessage
    data class PresetCreated(val name: String) : EditorMessage
    data object BatchBusy : EditorMessage
    data object EmptyText : EditorMessage
    data object OverridesCleared : EditorMessage
}

data class EditorUiState(
    val mode: EditorMode,
    val uris: List<Uri>,
    val index: Int = 0,
    val ready: Boolean = false,
    val presets: List<Preset> = emptyList(),
    val presetId: String = "",
    val presetName: String = "",
    val presetStyle: WatermarkStyle = WatermarkStyle(),
    val sharedStyle: WatermarkStyle = WatermarkStyle(),
    val overrides: Map<Int, WatermarkStyle> = emptyMap(),
    val lockAll: Boolean = true,
    val preview: PreviewState = PreviewState.Loading,
    val analysis: Map<Int, Analysis> = emptyMap(),
    val showOriginal: Boolean = false,
    val guides: Guides? = null,
    val busy: Boolean = false,
    val message: EditorMessage? = null,
) {
    /** Sample-image modes show one generated picture. */
    val count: Int get() = if (mode == EditorMode.PHOTOS) uris.size else 1
    val isSample: Boolean get() = mode != EditorMode.PHOTOS
    fun styleFor(i: Int): WatermarkStyle = if (lockAll) sharedStyle else overrides[i] ?: sharedStyle
    val currentStyle: WatermarkStyle get() = styleFor(index)
    val isModified: Boolean get() = sharedStyle != presetStyle
    val currentUri: Uri? get() = uris.getOrNull(index)

    val currentAnalysis: Analysis? get() = analysis[index]
    val smartAnchor: Anchor?
        get() = currentStyle.takeIf { it.smartCorner && it.customCenter == null }?.let { currentAnalysis?.smartAnchor }
    /** Recomputed instantly from the last sampled background, so toggles never lag or flicker. */
    val readability: Readability
        get() = ReadabilityResolver.resolve(currentStyle, currentAnalysis?.backgroundLuminance)
}

class EditorViewModel(private val container: AppContainer, handle: SavedStateHandle) : ViewModel() {

    private val route = handle.toRoute<EditorRoute>()
    private val mode = when {
        route.presetId != null -> EditorMode.EDIT_PRESET
        route.newPreset -> EditorMode.NEW_PRESET
        else -> EditorMode.PHOTOS
    }

    /** Selection survives process death through SavedStateHandle. */
    private val uris: List<Uri> = if (mode == EditorMode.PHOTOS) {
        handle.get<ArrayList<Uri>>(KEY_URIS) ?: ArrayList(container.selection.take()).also { handle[KEY_URIS] = it }
    } else {
        emptyList()
    }

    private val _state = MutableStateFlow(EditorUiState(mode = mode, uris = uris))
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val previewCache = LruCache<Int, Bitmap>(4)

    init {
        viewModelScope.launch {
            val presets = container.presets.presets.first()
            val preset = when (mode) {
                EditorMode.EDIT_PRESET -> presets.firstOrNull { it.id == route.presetId } ?: container.presets.active()
                else -> container.presets.active()
            }
            _state.update {
                it.copy(
                    ready = true, presets = presets,
                    presetId = preset.id, presetName = preset.name,
                    presetStyle = preset.style, sharedStyle = preset.style,
                )
            }
        }
        viewModelScope.launch {
            container.presets.presets.collect { list -> _state.update { it.copy(presets = list) } }
        }
        viewModelScope.launch {
            _state.map { it.index }.distinctUntilChanged().collectLatest { loadPreview(it) }
        }
        viewModelScope.launch {
            _state.map { Triple(it.index, (it.preview as? PreviewState.Ready)?.bitmap, it.currentStyle) }
                .distinctUntilChanged()
                .collectLatest { (index, bitmap, style) ->
                    if (bitmap == null || !_state.value.ready) return@collectLatest
                    delay(ANALYSIS_DEBOUNCE_MS)
                    val decision = withContext(Dispatchers.Default) { container.analyzer.decide(bitmap, style) }
                        ?: return@collectLatest
                    _state.update {
                        it.copy(analysis = it.analysis + (index to Analysis(style, decision.smartAnchor, decision.backgroundLuminance)))
                    }
                }
        }
    }

    private suspend fun loadPreview(index: Int) {
        if (mode != EditorMode.PHOTOS) {
            _state.update { it.copy(preview = PreviewState.Ready(SampleArt.bitmap)) }
            return
        }
        val uri = uris.getOrNull(index) ?: return
        previewCache.get(index)?.let { cached ->
            _state.update { it.copy(preview = PreviewState.Ready(cached)) }
            return
        }
        _state.update { it.copy(preview = PreviewState.Loading) }
        val result = withContext(Dispatchers.IO) { runCatching { container.imageLoader.decodePreview(uri) } }
        _state.update { s ->
            if (s.index != index) return@update s
            result.fold(
                onSuccess = { bmp -> previewCache.put(index, bmp); s.copy(preview = PreviewState.Ready(bmp)) },
                onFailure = { s.copy(preview = PreviewState.Failed(WatermarkProcessor.classifyRead(it))) },
            )
        }
    }

    fun select(index: Int) {
        if (index in 0 until _state.value.count) _state.update { it.copy(index = index, showOriginal = false) }
    }

    /** Edits go to every image when locked (the default) or to the current image only when unlocked. */
    fun updateStyle(transform: (WatermarkStyle) -> WatermarkStyle) {
        _state.update { s ->
            if (s.lockAll || s.count == 1) s.copy(sharedStyle = transform(s.sharedStyle))
            else s.copy(overrides = s.overrides + (s.index to transform(s.currentStyle)))
        }
    }

    fun setLockAll(locked: Boolean) {
        _state.update { s ->
            val hadOverrides = locked && s.overrides.isNotEmpty()
            s.copy(
                lockAll = locked,
                overrides = if (locked) emptyMap() else s.overrides,
                message = if (hadOverrides) EditorMessage.OverridesCleared else s.message,
            )
        }
    }

    fun drag(centerX: Float, centerY: Float, width: Float, height: Float, threshold: Float) {
        val style = _state.value.currentStyle
        val result = Snapping.drag(style, width, height, container.renderer.measurer(style), centerX, centerY, threshold)
        updateStyle { result.style }
        _state.update { it.copy(guides = Guides(result.guideX, result.guideY, result.snappedAnchor)) }
    }

    fun endDrag() = _state.update { it.copy(guides = null) }

    fun setShowOriginal(show: Boolean) = _state.update { it.copy(showOriginal = show) }

    fun selectPreset(preset: Preset) {
        _state.update {
            it.copy(
                presetId = preset.id, presetName = preset.name, presetStyle = preset.style,
                sharedStyle = preset.style, overrides = emptyMap(),
            )
        }
        viewModelScope.launch { container.presets.setActive(preset.id) }
    }

    fun revertToPreset() = _state.update { it.copy(sharedStyle = it.presetStyle, overrides = emptyMap()) }

    fun updatePreset(onDone: () -> Unit = {}) {
        val s = _state.value
        val preset = s.presets.firstOrNull { it.id == s.presetId } ?: return
        viewModelScope.launch {
            container.presets.update(preset.copy(style = s.sharedStyle))
            _state.update { it.copy(presetStyle = s.sharedStyle, message = EditorMessage.PresetUpdated) }
            onDone()
        }
    }

    fun saveAsPreset(name: String, onDone: () -> Unit = {}) {
        val style = _state.value.sharedStyle
        viewModelScope.launch {
            val created = container.presets.create(name, style)
            _state.update {
                it.copy(
                    presetId = created.id, presetName = created.name, presetStyle = created.style,
                    message = EditorMessage.PresetCreated(created.name),
                )
            }
            onDone()
        }
    }

    fun saveImages(onStarted: () -> Unit) {
        val s = _state.value
        if ((0 until s.count).any { s.styleFor(it).text.isBlank() }) {
            _state.update { it.copy(message = EditorMessage.EmptyText) }
            return
        }
        if (s.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            container.presets.setActive(s.presetId)
            // Reuse Smart Corner decisions the user has already seen, so the export matches the preview.
            val anchors = s.analysis
                .filter { (i, a) -> a.smartAnchor != null && a.style == s.styleFor(i) && a.style.smartCorner }
                .mapValues { (_, a) -> requireNotNull(a.smartAnchor) }
            val started = container.batches.start(
                BatchRequest(
                    uris = s.uris,
                    presetName = s.presetName,
                    style = s.sharedStyle,
                    overrides = if (s.lockAll) emptyMap() else s.overrides,
                    resolvedAnchors = anchors,
                ),
            )
            _state.update { it.copy(busy = false, message = if (started) it.message else EditorMessage.BatchBusy) }
            if (started) onStarted()
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private companion object {
        const val KEY_URIS = "uris"
        const val ANALYSIS_DEBOUNCE_MS = 60L
    }
}
