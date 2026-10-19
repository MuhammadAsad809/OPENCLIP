# Implementation Roadmap: 100% Free 2D Animation App

## Phase 0: Project Setup & Baseline Build Verification
- [x] Analyze FlipaClip 5.0.2 APK structure, DEX classes, native libraries, and assets.
- [x] Create comprehensive reverse-engineering documentation under `analysis/`.
- [ ] Configure Android build scripts (`build.gradle.kts`, `app/build.gradle.kts`) with our custom application ID and clean dependencies.
- [ ] Verify clean compilation and build a debug APK with `./gradlew assembleDebug`.

## Phase 1: Core Domain Models & Storage Engine
- [ ] Implement data models: `Project`, `Frame`, `Layer`, `BlendMode`, `CanvasPreset`, `OnionConfig`, `ToolType`.
- [ ] Implement `ProjectRepository` with JSON metadata serialization and atomic file writing.
- [ ] Implement `FrameCacheManager` (LRU memory caching for active Bitmaps, disk persistence for inactive frames).
- [ ] Implement project creation, rename, duplicate, delete, and list operations.
- [ ] Unit tests for Project creation, serialization, deserialization, and autosave.

## Phase 2: Drawing Canvas Engine & Tools
- [ ] Build `DrawingCanvasView`: Interactive touch surface with Pan & Zoom matrix support.
- [ ] Implement `StrokeRenderer`: Quadratic Bézier curve smoothing, pressure dynamics, velocity interpolation.
- [ ] Implement Brush types: Pen (solid crisp), Pencil (textured sketch), Marker (semi-transparent), Airbrush (soft radial gradient), Eraser (`CLEAR` PorterDuff mode).
- [ ] Implement Ruler / Shape guides: Straight line, circle/oval, square/rectangle.
- [ ] Implement Scanline Flood Fill (`FloodFillEngine`) with configurable color distance tolerance.
- [ ] Implement Color Picker dialog (HSV wheel, hex, alpha, and color palette presets).
- [ ] Implement Command-based Undo / Redo history system.
- [ ] Unit tests for Drawing, Erasing, and Undo/Redo stack.

## Phase 3: Multi-Layer & Frame Animation Timeline
- [ ] Implement multi-layer compositing engine (`LayerCompositor`):
  - Add, delete, reorder, rename layers.
  - Layer visibility and opacity sliders (0% to 100%).
  - Independent drawing on the active layer.
- [ ] Implement frame animation timeline:
  - Add frame, duplicate frame, delete frame, reorder frames.
  - Horizontal timeline thumbnail strip with active frame indicator.
  - Background thumbnail generator.
- [ ] Implement Onion Skinning:
  - Configurable frames before & after (1-5 frames).
  - Opacity falloff.
  - Colored onion mode (Red for past, Green for future).
- [ ] Implement real-time animation playback controller:
  - Configurable FPS (1 to 30 FPS).
  - Loop toggle, play/pause, scrub slider.
- [ ] Unit tests for Frame manipulation, layer compositing, and onion skinning.

## Phase 4: Selection / Transform & Audio Support
- [ ] Implement Lasso selection tool:
  - Freehand selection loop, bounding box computation.
  - Translation, scaling, rotation, horizontal/vertical flip.
  - Stamp / Paste into current layer.
- [ ] Implement audio track preview:
  - Import audio file (MP3 / WAV).
  - Synchronized audio playback with animation frame clock.

## Phase 5: Complete Export Pipeline (Zero Watermarks)
- [ ] Implement Single Frame PNG export (`saveToGallery`).
- [ ] Implement PNG Image Sequence zip export.
- [ ] Implement Animated GIF export with LZW encoding.
- [ ] Implement Hardware-accelerated MP4 Video export using Android `MediaCodec` + `MediaMuxer`.
- [ ] Export settings dialog: Format, resolution, FPS, background toggle.
- [ ] Progress reporting and notification upon completion.

## Phase 6: Final Integration, Packaging & APK Delivery
- [ ] Assemble complete application UI (Home Project Gallery + Full Animation Studio).
- [ ] Build release/debug APK.
- [ ] Verify installation and run verification tests on Android.
- [ ] Deliver APK path and comprehensive walkthrough report.
