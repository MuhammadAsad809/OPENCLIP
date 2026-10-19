# Application Architecture Specification: Clean-Room 2D Animation Engine

## 1. Architectural Philosophy

Our application is built as an independent, Android-first, 100% free 2D animation studio.
We adhere to **Clean Architecture** principles with clear separation between:
1. **Domain Layer**: Pure business logic (projects, frames, layers, timeline, animation clocks).
2. **Drawing & Canvas Engine**: Dedicated interactive graphics surface, touch processing, stroke rasterization, and layer compositing.
3. **Storage & Data Layer**: Local project serialization, Room DB / JSON repository, frame bitmap cache.
4. **Media & Export Pipeline**: Native Android `MediaCodec` + `MediaMuxer` (MP4 video), Animated GIF encoder, and PNG sequence renderer.
5. **Presentation Layer**: Jetpack Compose modern UI combined with an optimized custom drawing view.

---

## 2. Layered Module Structure

```
com.vitre.animation/  (or custom clean package)
├── core/
│   ├── model/                  # Data classes: Project, Frame, Layer, Tool, BrushConfig
│   ├── engine/                 # Core drawing and rendering logic
│   │   ├── DrawingCanvasView   # Custom interactive Android View for low-latency drawing
│   │   ├── StrokeRenderer      # Quadratic Bézier stroke smoother & pressure curve
│   │   ├── LayerCompositor     # Multi-layer PorterDuff offscreen blending
│   │   ├── OnionSkinRenderer   # Tinted frame lookbehind/lookahead overlay
│   │   ├── FloodFillEngine     # 4-way scanline flood fill algorithm
│   │   ├── LassoTransformEngine# Path containment, bounding box, matrix transform
│   │   └── HistoryManager      # Command-based undo/redo snapshot ledger
│   └── cache/                  # Memory-conscious LRU Bitmap and disk frame cache
├── data/
│   ├── repository/             # ProjectRepository (CRUD operations, autosave)
│   ├── local/                  # Local project files, JSON parsing, disk I/O
│   └── export/                 # Video & image exporters
│       ├── Mp4VideoExporter    # Hardware-accelerated MediaCodec H.264/AAC pipeline
│       ├── GifAnimationExporter# Animated GIF encoder with color quantizer
│       └── PngSequenceExporter # Image sequence writer
├── domain/
│   ├── usecase/                # ProjectUseCases, FrameUseCases, ExportUseCases
│   └── playback/               # AnimationPlaybackController (Choreographer clock)
└── presentation/
    ├── gallery/                # Home screen, project list, new project modal
    ├── editor/                 # Main animation studio screen (Stage)
    │   ├── EditorScreen        # Compose container hosting toolbars & panels
    │   ├── EditorViewModel     # State holder for tools, active frame/layer, playback
    │   ├── components/
    │   │   ├── TopToolbar      # Back, title, undo/redo, zoom reset, menu
    │   │   ├── DrawingToolbar  # Tool rail (pen, eraser, lasso, fill, color)
    │   │   ├── FrameTimeline   # Bottom thumbnail carousel & add frame button
    │   │   ├── LayerSheet      # Bottom sheet for layer management & opacity
    │   │   ├── BrushDialog     # Brush type, size, opacity popup
    │   │   ├── OnionSkinDialog # Onion skin configuration dialog
    │   │   └── ColorPickerDialog# HSV wheel, alpha slider, swatches
    └── export/                 # Export modal, format selector, progress dialog
```

---

## 3. High-Performance Canvas Rendering Engine

### Why Custom Android View over pure Compose Canvas for Drawing:
While Jetpack Compose is great for UI widgets, high-frequency stylus/touch events (120Hz/240Hz touch sampling rate on modern Android screens) benefit immensely from a dedicated custom Android `View` (`DrawingCanvasView`) implementing `onTouchEvent` directly:
- **Zero GC overhead** during active stroke gestures.
- Direct access to `MotionEvent.getHistoricalX/Y/Pressure` for sub-frame stroke smoothing.
- Hardware-accelerated `Canvas` operations with offscreen `Bitmap` backing.
- Matrix-based Pan & Zoom transform independent of UI recomposition.

### Stroke Smoothing Algorithm:
```kotlin
// Midpoint Quadratic Bézier curve generation:
val midX = (prevX + currentX) / 2f
val midY = (prevY + currentY) / 2f
currentPath.quadTo(prevX, prevY, midX, midY)
```
Pressure sensitivity dynamically scales `Paint.strokeWidth`:
```kotlin
val dynamicWidth = baseWidth * (0.3f + 0.7f * pressure)
```

---

## 4. Hardware-Accelerated Video Export (No GPL / FFmpeg bloat)

Instead of requiring 20MB+ of external FFmpeg binaries with complex licensing restrictions, our app uses **Android Native `MediaCodec` + `MediaMuxer`**:
- Encodes video using device hardware H.264/AVC encoder (`video/avc`).
- Takes frames rendered from `LayerCompositor` via an input `Surface` (`createInputSurface()`).
- Renders at target FPS (e.g., 12, 24, 30 FPS).
- Muxes directly to `.mp4` container.
- Blazing fast hardware encoding (typically 10x-20x faster than software FFmpeg on mobile GPUs/SoCs).
