package com.openclip.studio.core.model

import java.util.UUID

enum class ToolType {
    PEN,
    PENCIL,
    BRUSH,
    AIRBRUSH,
    MARKER,
    CRAYON,
    PIXEL,
    CALLIGRAPHY,
    LEAF_SCATTER,
    CHARCOAL,
    ERASER,
    LASSO,
    FILL,
    TEXT,
    SMUDGE,
    BLUR,
    RULER,
    MIRROR,
    EYEDROPPER
}

enum class MirrorMode {
    NONE,
    VERTICAL,
    HORIZONTAL,
    QUAD
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

enum class BlendMode {
    NORMAL,
    MULTIPLY,
    SCREEN,
    OVERLAY,
    DARKEN,
    LIGHTEN,
    COLOR_DODGE,
    COLOR_BURN,
    SOFT_LIGHT,
    HARD_LIGHT,
    DIFFERENCE,
    EXCLUSION
}

enum class TextAlignment {
    LEFT,
    CENTER,
    RIGHT
}

data class GridConfig(
    val enabled: Boolean = false,
    val hSpacing: Int = 40,
    val vSpacing: Int = 40,
    val opacity: Float = 0.35f,
    val color: Int = 0xFF888888.toInt(),
    val isIsometric: Boolean = false
)

data class TextConfig(
    val text: String = "Text",
    val fontName: String = "SansSerif",
    val fontSize: Float = 48f,
    val color: Int = 0xFF000000.toInt(),
    val alignment: TextAlignment = TextAlignment.CENTER,
    val letterSpacing: Float = 0f
)

data class Layer(
    val id: Int,
    val name: String,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val opacity: Float = 1.0f,
    val blendMode: BlendMode = BlendMode.NORMAL,
    val alphaLock: Boolean = false,
    val isClippingMask: Boolean = false,
    val glowEnabled: Boolean = false,
    val glowColor: Int = 0xFFFFFF00.toInt(),
    val glowRadius: Float = 15f,
    val glowAlpha: Float = 0.8f,
    val pixelatorEnabled: Boolean = false,
    val pixelSize: Int = 8,
    val shadowEnabled: Boolean = false,
    val shadowColor: Int = 0x88000000.toInt(),
    val shadowDx: Float = 5f,
    val shadowDy: Float = 5f,
    val shadowRadius: Float = 10f
)

data class OnionConfig(
    val enabled: Boolean = true,
    val framesBefore: Int = 1,
    val framesAfter: Int = 0,
    val startOpacity: Float = 0.45f,
    val endOpacity: Float = 0.15f,
    val coloredOnion: Boolean = true, // Red for previous, Green for next
    val prevColor: Int = 0xFFFF3B30.toInt(), // Red
    val nextColor: Int = 0xFF34C759.toInt(), // Green
    val loop: Boolean = false // Loop across first/last frames
)

data class BrushConfig(
    val toolType: ToolType = ToolType.PEN,
    val size: Float = 12f,
    val color: Int = 0xFF000000.toInt(), // Black ARGB
    val opacity: Float = 1.0f,
    val eraserSize: Float = 30f,
    val eraserOpacity: Float = 1.0f,
    val eraserHardness: Float = 1.0f,
    val strokeSmoothing: Float = 0.5f,
    val rulerType: RulerType = RulerType.NONE,
    val rulerLockedToCanvas: Boolean = true,
    val mirrorMode: MirrorMode = MirrorMode.NONE,
    val mirrorAxisX: Float = 0.5f,
    val mirrorAxisY: Float = 0.5f,
    val fillTolerance: Int = 32,
    val fillBleed: Int = 2,
    val stylusPressureEnabled: Boolean = true,
    val stylusEraserOverride: Boolean = false,
    val hoverPreviewEnabled: Boolean = true
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


data class SpriteSheetConfig(
    val columns: Int = 4,
    val padding: Int = 2
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
    val frameHolds: Map<Int, Int> = emptyMap(), // Frame index to hold duration in exposures (1 = normal)
    val pasteboardMargin: Int = 300,
    val gridConfig: GridConfig = GridConfig(),
    val onionConfig: OnionConfig = OnionConfig(),
    val textConfig: TextConfig = TextConfig(),
    val canvasRotation: Float = 0f,
    val canvasFlipH: Boolean = false,
    val isTimelapseRecording: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
