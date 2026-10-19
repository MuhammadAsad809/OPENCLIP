package com.openclip.studio.ui.editor

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openclip.studio.core.engine.HistoryManager
import com.openclip.studio.core.export.ExportFormat
import com.openclip.studio.core.export.ExportPipeline
import com.openclip.studio.core.model.BrushConfig
import com.openclip.studio.core.model.Layer
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

    fun updateProjectTitle(newTitle: String) {
        val updated = _project.value.copy(title = newTitle, updatedAt = System.currentTimeMillis())
        _project.value = updated
        repository.saveProject(updated)
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
