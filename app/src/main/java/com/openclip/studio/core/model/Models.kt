package com.openclip.studio.core.model

import java.util.UUID

enum class ToolType {
    PEN,
    PENCIL,
    BRUSH,
    AIRBRUSH,
    MARKER,
    CRAYON,
    ERASER,
    LASSO,
    FILL,
    TEXT,
    SMUDGE,
    BLUR,
    RULER,
    EYEDROPPER
}

enum class RulerType {
    NONE,
    LINE,
    CIRCLE,
    RECTANGLE
}

enum class BackgroundType {
    SOLID,
    TRANSPARENT,
    PAPER_WHITE,
    PAPER_VINTAGE,
    PAPER_GRID
}

data class CanvasPreset(
    val name: String,
    val width: Int,
    val height: Int,
    val aspectRatioLabel: String
) {
    companion object {
        val ALL = listOf(
            CanvasPreset("YouTube 1080p", 1920, 1080, "16:9"),
            CanvasPreset("YouTube 720p", 1280, 720, "16:9"),
            CanvasPreset("TikTok / Shorts 1080p", 1080, 1920, "9:16"),
            CanvasPreset("TikTok / Shorts 720p", 720, 1280, "9:16"),
            CanvasPreset("Instagram Square", 1080, 1080, "1:1"),
            CanvasPreset("Instagram Portrait", 1080, 1350, "4:5"),
            CanvasPreset("Standard 4:3", 1024, 768, "4:3")
        )
        val DEFAULT = ALL[1] // YouTube 720p (1280x720) - fast, crisp, standard
    }
}

data class Layer(
    val id: Int,
    val name: String,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val opacity: Float = 1.0f
)

data class OnionConfig(
    val enabled: Boolean = true,
    val framesBefore: Int = 1,
    val framesAfter: Int = 0,
    val startOpacity: Float = 0.45f,
    val endOpacity: Float = 0.15f,
    val coloredOnion: Boolean = true // Red for previous, Green for next
)

data class BrushConfig(
    val toolType: ToolType = ToolType.PEN,
    val size: Float = 12f,
    val color: Int = 0xFF000000.toInt(), // Black ARGB
    val opacity: Float = 1.0f,
    val eraserSize: Float = 30f,
    val eraserOpacity: Float = 1.0f,
    val rulerType: RulerType = RulerType.NONE,
    val fillTolerance: Int = 32
)

data class AudioClip(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val filePath: String,
    val startFrame: Int = 0,
    val durationFrames: Int = 24,
    val volume: Float = 1.0f,
    val muted: Boolean = false
)

data class AudioTrack(
    val id: Int,
    val name: String = "Track 1",
    val clips: List<AudioClip> = emptyList(),
    val volume: Float = 1.0f,
    val muted: Boolean = false
)

data class Project(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "My Animation",
    val canvasWidth: Int = 1280,
    val canvasHeight: Int = 720,
    val fps: Int = 12,
    val frameCount: Int = 1,
    val backgroundColor: Int = 0xFFFFFFFF.toInt(),
    val backgroundType: BackgroundType = BackgroundType.SOLID,
    val layers: List<Layer> = listOf(
        Layer(id = 0, name = "Layer 1", visible = true, locked = false, opacity = 1.0f)
    ),
    val audioTracks: List<AudioTrack> = listOf(
        AudioTrack(id = 0, name = "Track 1")
    ),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
