package com.openclip.studio.ui.editor

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openclip.studio.core.engine.HistoryManager
import com.openclip.studio.core.export.ExportFormat
import com.openclip.studio.core.export.ExportPipeline
import com.openclip.studio.core.model.BackgroundType
import com.openclip.studio.core.model.BrushConfig
import com.openclip.studio.core.model.GridConfig
import com.openclip.studio.core.model.Layer
import com.openclip.studio.core.model.MirrorMode
import com.openclip.studio.core.model.OnionConfig
import com.openclip.studio.core.model.Project
import com.openclip.studio.core.model.RulerType
import com.openclip.studio.core.model.ToolType
import com.openclip.studio.data.ProjectRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class EditorViewModel(
    application: Application,
    val repository: ProjectRepository,
    projectId: String
) : AndroidViewModel(application) {

    private val _project = MutableStateFlow(repository.getProject(projectId) ?: Project(id = projectId))
    val project: StateFlow<Project> = _project.asStateFlow()

    private val _currentFrameIndex = MutableStateFlow(0)
    val currentFrameIndex: StateFlow<Int> = _currentFrameIndex.asStateFlow()

    private val _activeLayerId = MutableStateFlow(0)
    val activeLayerId: StateFlow<Int> = _activeLayerId.asStateFlow()

    private val _brushConfig = MutableStateFlow(BrushConfig())
    val brushConfig: StateFlow<BrushConfig> = _brushConfig.asStateFlow()

    private val _onionConfig = MutableStateFlow(OnionConfig())
    val onionConfig: StateFlow<OnionConfig> = _onionConfig.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLooping = MutableStateFlow(true)
    val isLooping: StateFlow<Boolean> = _isLooping.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    val historyManager = HistoryManager().apply {
        onHistoryChanged = { u, r ->
            _canUndo.value = u
            _canRedo.value = r
        }
    }

    private var playbackJob: Job? = null

    init {
        // Ensure active layer matches existing layers
        val proj = _project.value
        if (proj.layers.none { it.id == _activeLayerId.value }) {
            _activeLayerId.value = proj.layers.firstOrNull()?.id ?: 0
        }
    }

    fun selectFrame(index: Int) {
        if (index in 0 until _project.value.frameCount) {
            _currentFrameIndex.value = index
            historyManager.clear()
        }
    }

    fun selectLayer(id: Int) {
        _activeLayerId.value = id
        historyManager.clear()
    }

    fun setToolType(type: ToolType) {
        _brushConfig.value = _brushConfig.value.copy(toolType = type)
    }

    fun setBrushSize(size: Float) {
        _brushConfig.value = _brushConfig.value.copy(size = size)
    }

    fun setBrushColor(color: Int) {
        _brushConfig.value = _brushConfig.value.copy(color = color)
    }

    fun setBrushOpacity(opacity: Float) {
        _brushConfig.value = _brushConfig.value.copy(opacity = opacity)
    }

    fun setEraserSize(size: Float) {
        _brushConfig.value = _brushConfig.value.copy(eraserSize = size)
    }

    fun setRulerType(type: RulerType) {
        _brushConfig.value = _brushConfig.value.copy(
            rulerType = type,
            toolType = if (type != RulerType.NONE) ToolType.RULER else ToolType.PEN
        )
    }

    fun setOnionEnabled(enabled: Boolean) {
        _onionConfig.value = _onionConfig.value.copy(enabled = enabled)
    }

    fun updateOnionConfig(config: OnionConfig) {
        _onionConfig.value = config
    }

    // Playback Controller
    fun togglePlayback() {
        if (_isPlaying.value) {
            stopPlayback()
        } else {
            startPlayback()
        }
    }

    private fun startPlayback() {
        _isPlaying.value = true
        playbackJob = viewModelScope.launch {
            val fps = _project.value.fps.coerceIn(1, 60)
            val delayMs = (1000L / fps)
            while (_isPlaying.value) {
                delay(delayMs)
                val cur = _currentFrameIndex.value
                val total = _project.value.frameCount
                if (cur + 1 < total) {
                    _currentFrameIndex.value = cur + 1
                } else {
                    if (_isLooping.value) {
                        _currentFrameIndex.value = 0
                    } else {
                        _isPlaying.value = false
                        break
                    }
                }
            }
        }
    }

    fun stopPlayback() {
        _isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
    }

    fun toggleLoop() {
        _isLooping.value = !_isLooping.value
    }

    // Frame Actions
    fun addFrame() {
        stopPlayback()
        val cur = _currentFrameIndex.value
        val updated = repository.insertFrame(_project.value, cur + 1)
        _project.value = updated
        _currentFrameIndex.value = cur + 1
        historyManager.clear()
    }

    fun duplicateCurrentFrame() {
        stopPlayback()
        val cur = _currentFrameIndex.value
        val updated = repository.duplicateFrame(_project.value, cur)
        _project.value = updated
        _currentFrameIndex.value = cur + 1
        historyManager.clear()
    }

    fun deleteCurrentFrame() {
        stopPlayback()
        val cur = _currentFrameIndex.value
        val updated = repository.deleteFrame(_project.value, cur)
        _project.value = updated
        _currentFrameIndex.value = cur.coerceAtMost(updated.frameCount - 1)
        historyManager.clear()
    }

    // Layer Actions
    fun addLayer(name: String = "Layer ${_project.value.layers.size + 1}") {
        val currentLayers = _project.value.layers
        val nextId = (currentLayers.maxOfOrNull { it.id } ?: -1) + 1
        val newLayer = Layer(id = nextId, name = name, visible = true, locked = false, opacity = 1.0f)
        val updatedLayers = currentLayers + newLayer
        val updated = _project.value.copy(layers = updatedLayers, updatedAt = System.currentTimeMillis())
        _project.value = updated
        _activeLayerId.value = nextId
        repository.saveProject(updated)
    }

    fun toggleLayerVisibility(layerId: Int) {
        val updatedLayers = _project.value.layers.map {
            if (it.id == layerId) it.copy(visible = !it.visible) else it
        }
        val updated = _project.value.copy(layers = updatedLayers, updatedAt = System.currentTimeMillis())
        _project.value = updated
        repository.saveProject(updated)
    }

    fun setLayerOpacity(layerId: Int, opacity: Float) {
        val updatedLayers = _project.value.layers.map {
            if (it.id == layerId) it.copy(opacity = opacity.coerceIn(0f, 1f)) else it
        }
        val updated = _project.value.copy(layers = updatedLayers, updatedAt = System.currentTimeMillis())
        _project.value = updated
        repository.saveProject(updated)
    }

    fun deleteLayer(layerId: Int) {
        if (_project.value.layers.size <= 1) return // Must keep at least 1 layer
        val updatedLayers = _project.value.layers.filter { it.id != layerId }
        val updated = _project.value.copy(layers = updatedLayers, updatedAt = System.currentTimeMillis())
        _project.value = updated
        if (_activeLayerId.value == layerId) {
            _activeLayerId.value = updatedLayers.first().id
        }
        repository.saveProject(updated)
    }

    fun mergeLayerDown(layerId: Int) {
        val proj = _project.value
        val idx = proj.layers.indexOfFirst { it.id == layerId }
        if (idx <= 0) return
        val targetLayerId = proj.layers[idx - 1].id
        val updated = repository.mergeLayerDown(proj, layerId)
        _project.value = updated
        _activeLayerId.value = targetLayerId
    }

    fun setMirrorMode(mode: MirrorMode) {
        _brushConfig.value = _brushConfig.value.copy(mirrorMode = mode)
    }

    fun setStrokeSmoothing(smoothing: Float) {
        _brushConfig.value = _brushConfig.value.copy(strokeSmoothing = smoothing.coerceIn(0f, 1f))
    }

    fun setEraserHardness(hardness: Float) {
        _brushConfig.value = _brushConfig.value.copy(eraserHardness = hardness.coerceIn(0f, 1f))
    }

    fun setRulerLocked(locked: Boolean) {
        _brushConfig.value = _brushConfig.value.copy(rulerLockedToCanvas = locked)
    }

    fun setFrameHold(frameIndex: Int, holdDuration: Int) {
        val updated = repository.setFrameHold(_project.value, frameIndex, holdDuration)
        _project.value = updated
    }

    fun updateProjectTitle(newTitle: String) {
        val updated = _project.value.copy(title = newTitle, updatedAt = System.currentTimeMillis())
        _project.value = updated
        repository.saveProject(updated)
    }

    fun updateProjectSettings(
        title: String,
        fps: Int,
        canvasWidth: Int,
        canvasHeight: Int,
        bgType: BackgroundType,
        bgColor: Int,
        flipH: Boolean,
        rotation: Float
    ) {
        val updated = _project.value.copy(
            title = title,
            fps = fps.coerceIn(1, 60),
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            backgroundType = bgType,
            backgroundColor = bgColor,
            canvasFlipH = flipH,
            canvasRotation = rotation,
            updatedAt = System.currentTimeMillis()
        )
        _project.value = updated
        repository.saveProject(updated)
    }

    fun updateGridConfig(config: GridConfig) {
        val updated = _project.value.copy(
            gridConfig = config,
            updatedAt = System.currentTimeMillis()
        )
        _project.value = updated
        repository.saveProject(updated)
    }

    fun updateLayer(layer: Layer) {
        val updatedLayers = _project.value.layers.map {
            if (it.id == layer.id) layer else it
        }
        val updated = _project.value.copy(
            layers = updatedLayers,
            updatedAt = System.currentTimeMillis()
        )
        _project.value = updated
        repository.saveProject(updated)
    }

    fun addFrameAfter(index: Int) {
        stopPlayback()
        val insertAt = (index + 1).coerceIn(0, _project.value.frameCount)
        val updated = repository.insertFrame(_project.value, insertAt)
        _project.value = updated
        _currentFrameIndex.value = insertAt
        historyManager.clear()
    }

    fun addFrameBefore(index: Int) {
        stopPlayback()
        val insertAt = index.coerceIn(0, _project.value.frameCount)
        val updated = repository.insertFrame(_project.value, insertAt)
        _project.value = updated
        _currentFrameIndex.value = insertAt
        historyManager.clear()
    }

    fun addBetweenAllFrames() {
        stopPlayback()
        val updated = repository.addBetweenAllFrames(_project.value)
        _project.value = updated
        historyManager.clear()
    }

    fun duplicateFrames(indices: List<Int>) {
        stopPlayback()
        val updated = repository.duplicateFrames(_project.value, indices)
        _project.value = updated
        historyManager.clear()
    }

    fun deleteFrames(indices: List<Int>) {
        stopPlayback()
        val updated = repository.deleteFrames(_project.value, indices)
        _project.value = updated
        _currentFrameIndex.value = _currentFrameIndex.value.coerceIn(0, updated.frameCount - 1)
        historyManager.clear()
    }

    fun moveFrame(fromIndex: Int, toIndex: Int) {
        stopPlayback()
        val updated = repository.moveFrame(_project.value, fromIndex, toIndex)
        _project.value = updated
        _currentFrameIndex.value = toIndex
        historyManager.clear()
    }

    fun loadFrameThumbnails(): Map<Int, Bitmap> {
        val result = mutableMapOf<Int, Bitmap>()
        val proj = _project.value
        for (i in 0 until proj.frameCount) {
            val thumbFile = repository.getThumbnailFile(proj.id, i)
            if (thumbFile.exists() && thumbFile.length() > 0) {
                val bmp = BitmapFactory.decodeFile(thumbFile.absolutePath)
                if (bmp != null) result[i] = bmp
            }
        }
        return result
    }

    fun importRotoscopedBitmaps(bitmaps: List<Bitmap>) {
        stopPlayback()
        var cur = _project.value
        val curIndex = _currentFrameIndex.value
        val activeLayer = _activeLayerId.value
        for ((idx, bmp) in bitmaps.withIndex()) {
            val targetFrame = curIndex + idx
            if (targetFrame < cur.frameCount) {
                repository.saveLayerBitmap(cur.id, targetFrame, activeLayer, bmp)
                repository.updateFrameThumbnail(cur, targetFrame)
            } else {
                cur = repository.insertFrame(cur, targetFrame)
                repository.saveLayerBitmap(cur.id, targetFrame, activeLayer, bmp)
                repository.updateFrameThumbnail(cur, targetFrame)
            }
        }
        _project.value = cur
        historyManager.clear()
    }

    fun setProject(project: Project) {
        _project.value = project
        repository.saveProject(project)
    }

    fun reloadProject() {
        val proj = repository.getProject(_project.value.id)
        if (proj != null) {
            _project.value = proj
        }
    }
}
